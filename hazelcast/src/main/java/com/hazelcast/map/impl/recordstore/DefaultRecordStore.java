/*
 * Copyright (c) 2008-2025, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.map.impl.recordstore;

import com.hazelcast.cluster.Address;
import com.hazelcast.config.InMemoryFormat;
import com.hazelcast.config.MetadataPolicy;
import com.hazelcast.config.NativeMemoryConfig;
import com.hazelcast.core.EntryEventType;
import com.hazelcast.internal.iteration.IterationPointer;
import com.hazelcast.internal.locksupport.LockSupportService;
import com.hazelcast.internal.partition.IPartition;
import com.hazelcast.internal.partition.IPartitionService;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.internal.services.ObjectNamespace;
import com.hazelcast.internal.util.BiTuple;
import com.hazelcast.internal.util.Clock;
import com.hazelcast.internal.util.CollectionUtil;
import com.hazelcast.internal.util.ExceptionUtil;
import com.hazelcast.internal.util.FutureUtil;
import com.hazelcast.internal.util.counters.SwCounter;
import com.hazelcast.logging.ILogger;
import com.hazelcast.map.EntryLoader.MetadataAwareValue;
import com.hazelcast.map.impl.InterceptorRegistry;
import com.hazelcast.map.impl.MapContainer;
import com.hazelcast.map.impl.MapEntries;
import com.hazelcast.map.impl.MapKeyLoader;
import com.hazelcast.map.impl.MapService;
import com.hazelcast.map.impl.event.EntryEventData;
import com.hazelcast.map.impl.iterator.MapEntriesWithCursor;
import com.hazelcast.map.impl.iterator.MapKeysWithCursor;
import com.hazelcast.map.impl.mapstore.MapDataStore;
import com.hazelcast.map.impl.mapstore.writebehind.WriteBehindQueue;
import com.hazelcast.map.impl.mapstore.writebehind.WriteBehindStore;
import com.hazelcast.map.impl.mapstore.writebehind.entry.DelayedEntry;
import com.hazelcast.map.impl.operation.MapOperation;
import com.hazelcast.map.impl.querycache.QueryCacheContext;
import com.hazelcast.map.impl.querycache.publisher.MapPublisherRegistry;
import com.hazelcast.map.impl.querycache.publisher.PublisherContext;
import com.hazelcast.map.impl.querycache.publisher.PublisherRegistry;
import com.hazelcast.map.impl.record.Record;
import com.hazelcast.map.impl.record.Records;
import com.hazelcast.map.impl.recordstore.expiry.ExpiryMetadata;
import com.hazelcast.map.impl.recordstore.expiry.ExpiryReason;
import com.hazelcast.spi.exception.RetryableHazelcastException;
import com.hazelcast.spi.impl.NodeEngine;
import com.hazelcast.spi.merge.SplitBrainMergePolicy;
import com.hazelcast.spi.merge.SplitBrainMergeTypes.MapMergeTypes;
import com.hazelcast.spi.properties.ClusterProperty;
import com.hazelcast.wan.impl.CallerProvenance;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

import static com.hazelcast.config.NativeMemoryConfig.MemoryAllocatorType.POOLED;
import static com.hazelcast.core.EntryEventType.ADDED;
import static com.hazelcast.core.EntryEventType.LOADED;
import static com.hazelcast.core.EntryEventType.UPDATED;
import static com.hazelcast.internal.util.ConcurrencyUtil.CALLER_RUNS;
import static com.hazelcast.internal.util.ExceptionUtil.rethrow;
import static com.hazelcast.internal.util.MapUtil.createHashMap;
import static com.hazelcast.internal.util.ToHeapDataConverter.toHeapData;
import static com.hazelcast.internal.util.counters.SwCounter.newSwCounter;
import static com.hazelcast.map.impl.mapstore.MapDataStores.EMPTY_MAP_DATA_STORE;
import static com.hazelcast.map.impl.record.Record.UNSET;
import static com.hazelcast.map.impl.recordstore.StaticParams.PUT_BACKUP_FOR_ENTRY_PROCESSOR_PARAMS;
import static com.hazelcast.map.impl.recordstore.StaticParams.PUT_BACKUP_PARAMS;
import static com.hazelcast.spi.impl.merge.MergingValueFactory.createMergingEntry;
import static java.util.Collections.emptyList;

/**
 * Default implementation of a record store.
 */
@SuppressWarnings({"checkstyle:methodcount", "checkstyle:classfanoutcomplexity", "rawtypes"})
public class DefaultRecordStore extends AbstractEvictableRecordStore {

    protected final ILogger logger;
    protected final RecordStoreLoader recordStoreLoader;
    @Nullable
    protected final MapKeyLoader keyLoader;
    /**
     * A collection of futures representing pending completion of the key and
     * value loading tasks.
     * The loadingFutures are modified by partition threads and can be accessed
     * by query threads.
     *
     * @see #loadAll(boolean)
     * @see #loadAllFromStore(List, boolean)
     */
    protected final Collection<Future<?>> loadingFutures = new ConcurrentLinkedQueue<>();

    /**
     * Defined by {@link com.hazelcast.spi.properties.ClusterProperty#WAN_REPLICATE_IMAP_EVICTIONS},
     * if set to true then eviction operations by this RecordStore will be WAN replicated
     */
    protected boolean wanReplicateEvictions;

    /**
     * A reference to the Json Metadata store. It is initialized lazily only if the
     * store is needed.
     */
    private JsonMetadataStore metadataStore;

    /**
     * The record store may be created with or without triggering the load.
     * This flag guards that the loading on create is invoked not more than
     * once should the record store be migrated.
     */
    private boolean loadedOnCreate;
    /**
     * Records if the record store on the migration source has been loaded.
     * If the record store has already been loaded, the migration target should
     * NOT trigger loading again on migration commit, otherwise it may trigger
     * key loading.
     */
    private boolean loadedOnPreMigration;
    private final IPartitionService partitionService;
    private final InterceptorRegistry interceptorRegistry;
    // offloadedOperations is only accessed by single thread
    private final Set<MapOperation> offloadedOperations = new LinkedHashSet<>();
    // mapStoreOffloadedOperationsCount is for accessed by single thread
    private final SwCounter mapStoreOffloadedOperationsCount = newSwCounter();

    public DefaultRecordStore(MapContainer mapContainer,
                              int partitionId,
                              @Nullable MapKeyLoader keyLoader,
                              ILogger logger) {
        super(mapContainer, partitionId);

        this.logger = logger;
        this.keyLoader = keyLoader;
        this.recordStoreLoader = createRecordStoreLoader(mapStoreContext);
        this.partitionService = mapServiceContext.getNodeEngine().getPartitionService();
        this.interceptorRegistry = mapContainer.getInterceptorRegistry();
        this.wanReplicateEvictions = mapContainer.getWanContext().isWanReplicationEnabled()
                && mapServiceContext.getNodeEngine().getProperties().getBoolean(ClusterProperty.WAN_REPLICATE_IMAP_EVICTIONS);
        initJsonMetadataStore();
    }

    @Override
    public void incMapStoreOffloadedOperationsCount() {
        mapStoreOffloadedOperationsCount.inc();
    }

    @Override
    public void decMapStoreOffloadedOperationsCount() {
        mapStoreOffloadedOperationsCount.inc(-1);
    }

    @Override
    public long getMapStoreOffloadedOperationsCount() {
        return mapStoreOffloadedOperationsCount.get();
    }

    // Overridden in EE
    protected void initJsonMetadataStore() {
        // Forcibly initialize on-heap Json Metadata Store to avoid
        // lazy initialization and potential race condition.
        getOrCreateMetadataStore();
    }

    @Override
    public MapDataStore<Data, Object> getMapDataStore() {
        return mapDataStore;
    }

    protected JsonMetadataStore createMetadataStore() {
        return new JsonMetadataStoreImpl();
    }

    @Override
    public long softFlush() {
        updateStoreStats();
        return mapDataStore.softFlush();
    }

    /**
     * Flushes evicted records to map store.
     */
    private void flush(ArrayList<Data> dataKeys,
                       ArrayList<Record> records, boolean backup) {
        if (mapDataStore == EMPTY_MAP_DATA_STORE) {
            return;
        }

        for (int i = 0; i < dataKeys.size(); i++) {
            mapDataStore.flush(dataKeys.get(i), records.get(i).getValue(), backup);
        }
    }

    @Override
    public JsonMetadataStore getOrCreateMetadataStore() {
        if (mapContainer.getMapConfig().getMetadataPolicy() == MetadataPolicy.OFF) {
            return JsonMetadataStore.NULL;
        }
        if (metadataStore == null) {
            metadataStore = createMetadataStore();
        }
        return metadataStore;
    }

    private void destroyMetadataStore() {
        if (metadataStore != null) {
            metadataStore.destroy();
        }
    }

    @Override
    public Record getRecord(Data key) {
        return storage.get(key);
    }

    public Record getRecordSafe(Data key) {
        return storage.getSafe(key);
    }

    @Override
    public Record putOrUpdateReplicatedRecord(Data dataKey, Record replicatedRecord,
                                              ExpiryMetadata expiryMetadata,
                                              boolean indexesMustBePopulated, long now) {
        Record newRecord = storage.get(dataKey);
        if (newRecord == null) {
            newRecord = createRecord(dataKey, replicatedRecord != null
                    ? replicatedRecord.getValue() : null, now);
            storage.put(dataKey, newRecord);
        } else {
            storage.updateRecordValue(dataKey, newRecord, replicatedRecord.getValue());
        }

        Records.copyMetadataFrom(replicatedRecord, newRecord);
        expirySystem.add(dataKey, expiryMetadata, now);
        mutationObserver.onReplicationPutRecord(dataKey, newRecord, indexesMustBePopulated);

        return newRecord;
    }

    @Override
    public void removeReplicatedRecord(Data dataKey, boolean backup) {
        Record record = storage.get(dataKey);
        if (record != null) {
            removeRecord0(dataKey, record, backup);
        }
    }

    @Override
    public Record putBackup(Data dataKey, Record newRecord, ExpiryMetadata expiryMetadata,
                            boolean putTransient, CallerProvenance provenance) {
        return putBackupInternal(dataKey, newRecord.getValue(), true,
                expiryMetadata.getTtl(), expiryMetadata.getMaxIdle(),
                expiryMetadata.getExpirationTime(), putTransient, provenance, null, PUT_BACKUP_PARAMS);
    }

    @Override
    public Record putBackup(Data dataKey, Record record, long ttl,
                            long maxIdle, long nowOrExpiryTime, CallerProvenance provenance) {
        return putBackupInternal(dataKey, record.getValue(), true,
                ttl, maxIdle, nowOrExpiryTime, false, provenance, null, PUT_BACKUP_PARAMS);
    }

    @Override
    public Record putBackupTxn(Data dataKey, Record newRecord, ExpiryMetadata expiryMetadata,
                               boolean putTransient, CallerProvenance provenance, UUID transactionId) {
        return putBackupInternal(dataKey, newRecord.getValue(), true,
                expiryMetadata.getTtl(), expiryMetadata.getMaxIdle(),
                expiryMetadata.getExpirationTime(), putTransient, provenance, transactionId, PUT_BACKUP_PARAMS);
    }

    @Override
    public Record putBackupForEntryProcessor(Data key, Object value, boolean changeExpiryOnUpdate, long ttl, long maxIdle,
                                             long nowOrExpiryTime, CallerProvenance provenance) {
        return putBackupInternal(key, value, changeExpiryOnUpdate, ttl, maxIdle,
                nowOrExpiryTime, false, provenance, null, PUT_BACKUP_FOR_ENTRY_PROCESSOR_PARAMS);
    }

    @SuppressWarnings("checkstyle:parameternumber")
    private Record putBackupInternal(Data key, Object value, boolean changeExpiryOnUpdate,
                                     long ttl, long maxIdle, long expiryTime,
                                     boolean putTransient, CallerProvenance provenance,
                                     UUID transactionId, StaticParams staticParams) {
        long now = getNow();
        putInternal(key, value, changeExpiryOnUpdate, ttl, maxIdle, expiryTime,
                now, null, null, null, staticParams);

        Record record = getRecord(key);

        if (persistenceEnabledFor(provenance)) {
            if (putTransient) {
                mapDataStore.addTransient(key, now);
            } else {
                mapDataStore.addBackup(key, value,
                        expirySystem.getExpiryMetadata(key).getExpirationTime(),
                        now, transactionId);
            }
        }
        return record;
    }

    @Override
    public void forEach(BiConsumer<Data, Record> consumer, boolean backup) {
        forEach(consumer, backup, false);
    }

    @Override
    public void forEach(BiConsumer<Data, Record> consumer,
                        boolean backup, boolean includeExpiredRecords) {
        forEach(consumer, backup, includeExpiredRecords, true);
    }

    @Override
    public void forEach(BiConsumer<Data, Record> consumer,
                        boolean backup, boolean includeExpiredRecords, boolean noCaching) {

        long now = getNow();
        Iterator<Map.Entry<Data, Record>> entries = storage.mutationTolerantIterator();
        while (entries.hasNext()) {
            Map.Entry<Data, Record> entry = entries.next();

            Data key = entry.getKey();
            Record record = entry.getValue();

            if (includeExpiredRecords
                    || hasExpired(key, now, backup) == ExpiryReason.NOT_EXPIRED) {
                consumer.accept(key, record);
            }
        }
    }

    @Override
    public Iterator<Map.Entry<Data, Record>> iterator() {
        return storage.mutationTolerantIterator();
    }

    @Override
    public void forEachAfterLoad(BiConsumer<Data, Record> consumer, boolean backup) {
        checkIfLoaded();
        forEach(consumer, backup);
    }

    @Override
    public MapKeysWithCursor fetchKeys(IterationPointer[] pointers, int size) {
        return storage.fetchKeys(pointers, size);
    }

    @Override
    public MapEntriesWithCursor fetchEntries(IterationPointer[] pointers, int size) {
        return storage.fetchEntries(pointers, size);
    }

    /**
     * Size may not give precise size at a specific moment
     * due to the expiration logic. But eventually, it should be correct.
     *
     * @return record store size.
     */
    @Override
    public int size() {
        // do not add checkIfLoaded(), size() is also used internally
        return storage.size();
    }

    @Override
    public boolean isEmpty() {
        checkIfLoaded();
        return storage.isEmpty();
    }

    @Override
    public boolean containsValue(Object value) {
        checkIfLoaded();
        long now = getNow();

        if (storage.isEmpty()) {
            return false;
        }

        // optimisation to skip serialisation/de-serialisation
        // in each call to RecordComparator.isEqual()
        value = inMemoryFormat == InMemoryFormat.OBJECT
                ? serializationService.toObject(value)
                : serializationService.toData(value);

        Iterator<Map.Entry<Data, Record>> entryIterator = iterator();
        while (entryIterator.hasNext()) {
            Map.Entry<Data, Record> entry = entryIterator.next();

            Data key = entry.getKey();
            Record record = entry.getValue();

            if (evictIfExpired(key, now, false)) {
                continue;
            }
            if (valueComparator.isEqual(value, record.getValue(), serializationService)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean txnLock(Data key, UUID caller, long threadId, long referenceId, long ttl, boolean blockReads) {
        checkIfLoaded();
        return lockStore != null && lockStore.txnLock(key, caller, threadId, referenceId, ttl, blockReads);
    }

    @Override
    public boolean extendLock(Data key, UUID caller, long threadId, long ttl) {
        checkIfLoaded();
        return lockStore != null && lockStore.extendLeaseTime(key, caller, threadId, ttl);
    }

    @Override
    public boolean localLock(Data key, UUID caller, long threadId, long referenceId, long ttl) {
        checkIfLoaded();
        return lockStore != null && lockStore.localLock(key, caller, threadId, referenceId, ttl);
    }

    @Override
    public boolean unlock(Data key, UUID caller, long threadId, long referenceId) {
        checkIfLoaded();
        return lockStore != null && lockStore.unlock(key, caller, threadId, referenceId);
    }

    @Override
    public boolean lock(Data key, UUID caller, long threadId, long referenceId, long ttl) {
        checkIfLoaded();
        return lockStore != null && lockStore.lock(key, caller, threadId, referenceId, ttl);
    }

    @Override
    public boolean forceUnlock(Data dataKey) {
        return lockStore != null && lockStore.forceUnlock(dataKey);
    }

    @Override
    public boolean isLocked(Data dataKey) {
        return lockStore != null && lockStore.isLocked(dataKey);
    }

    @Override
    public boolean isTransactionallyLocked(Data key) {
        return lockStore != null && lockStore.shouldBlockReads(key);
    }

    @Override
    public boolean canAcquireLock(Data key, UUID caller, long threadId) {
        return lockStore == null || lockStore.canAcquireLock(key, caller, threadId);
    }

    @Override
    public boolean isLockedBy(Data key, UUID caller, long threadId) {
        return lockStore != null && lockStore.isLockedBy(key, caller, threadId);
    }

    @Override
    public String getLockOwnerInfo(Data key) {
        return lockStore != null ? lockStore.getOwnerInfo(key) : null;
    }

    /**
     * Loads value of key. If necessary loads it by
     * extracting from {@link MetadataAwareValue}
     *
     * @return loaded value from map-store as a BiTuple which
     * holds value and expirationTime together, when no value or expirationTime is
     * found, associated fields in BiTuple is set to {@link Record#UNSET}
     */
    public BiTuple<Object, Long> loadValueWithTtl(Data key, long now) {
        Object value = mapDataStore.load(key);
        return getOldValueWithTtlTupleOrNull(value, now);
    }

    public Object loadValueOfKey(Data key, long now) {
        Object value = mapDataStore.load(key);
        BiTuple<Object, Long> valueWithTtl = getOldValueWithTtlTupleOrNull(value, now);
        return valueWithTtl == null ? null : valueWithTtl.element1;
    }

    @Nullable
    public BiTuple<Object, Long> getOldValueWithTtlTupleOrNull(Object loadedValue, long now) {
        Object value = loadedValue;
        if (value == null) {
            return null;
        }

        if (mapDataStore.isWithExpirationTime()) {
            MetadataAwareValue loaderEntry = (MetadataAwareValue) value;
            long expirationTime = loaderEntry.getExpirationTime();
            long ttlFromExpiryTime = toTtlFromExpiryTime(expirationTime, now);
            if (ttlFromExpiryTime <= 0) {
                return null;
            }
            value = loaderEntry.getValue();
            if (value == null) {
                return null;
            }
            return BiTuple.of(value, ttlFromExpiryTime);
        }

        return BiTuple.of(value, (long) UNSET);
    }

    @Override
    public Record loadRecordOrNull(Data key, boolean backup, Address callerAddress, long now) {
        BiTuple<Object, Long> biTuple = loadValueWithTtl(key, now);
        if (biTuple == null) {
            return null;
        }

        return onLoadRecord(key, biTuple, backup, callerAddress, now);
    }

    public Record onLoadRecord(Data key, BiTuple<Object, Long> valueWithTtl,
                               boolean backup, Address callerAddress, long now) {
        assert key != null;
        assert valueWithTtl != null;

        Object loadedValue = valueWithTtl.element1;
        Long ttl = valueWithTtl.element2;

        Record record = putNewRecord(key, null, loadedValue, ttl, UNSET, UNSET, now,
                null, LOADED, false, backup);
        if (!backup && mapEventPublisher.hasEventListener(name)) {
            mapEventPublisher.publishEvent(callerAddress, name, EntryEventType.LOADED,
                    key, null, record.getValue(), null);
        }
        evictEntries(key);
        // here, we are only publishing events for loaded
        // entries. This is required for notifying query-caches
        // otherwise query-caches cannot see loaded entries
        if (!backup && hasQueryCache()) {
            addEventToQueryCache(key, record);
        }
        return record;
    }

    protected long toTtlFromExpiryTime(long expirationTime, long now) {
        if (expirationTime == MetadataAwareValue.NO_TIME_SET) {
            // return Long.MAX_VALUE to indicate infinite ttl
            return Long.MAX_VALUE;
        }
        return expirationTime - now;
    }

    public int removeBulk(List<Data> dataKeys, List<Record> records, boolean backup) {
        return removeOrEvictEntries(dataKeys, records, false, backup);
    }

    public int evictBulk(List<Data> dataKeys, List<Record> records, boolean backup) {
        return removeOrEvictEntries(dataKeys, records, true, backup);
    }

    private int removeOrEvictEntries(List<Data> dataKeys, List<Record> records, boolean eviction,
                                     boolean backup) {
        for (int i = 0; i < dataKeys.size(); i++) {
            Data dataKey = dataKeys.get(i);
            Record record = records.get(i);
            removeOrEvictEntry(dataKey, record, eviction, backup);
        }

        return dataKeys.size();
    }

    public void removeOrEvictEntry(Data dataKey, Record record, boolean eviction, boolean backup) {
        if (eviction) {
            mutationObserver.onEvictRecord(dataKey, record, backup);
        } else {
            mutationObserver.onRemoveRecord(dataKey, record, backup);
        }
        removeKeyFromExpirySystem(dataKey);
        storage.removeRecord(dataKey, record);

        if (wanReplicateEvictions && eviction) {
            mapEventPublisher.publishWanRemove(name, toHeapData(dataKey));
        }
    }

    @Override
    public Object evict(Data key, boolean backup) {
        Throwable throwable = null;
        Record record = storage.get(key);
        Object value = null;
        if (record != null) {
            value = copyToHeapWhenNeeded(record.getValue());
            mapDataStore.flush(key, value, backup);
            try {
                mutationObserver.onEvictRecord(key, record, backup);
            } catch (Throwable t) {
                throwable = t;
            }
            removeKeyFromExpirySystem(key);
            storage.removeRecord(key, record);
            if (!backup) {
                mapServiceContext.interceptRemove(interceptorRegistry, value);
            }
            if (wanReplicateEvictions) {
                mapEventPublisher.publishWanRemove(name, toHeapData(key));
            }
        }
        if (throwable != null) {
            throw rethrow(throwable);
        }
        return value;
    }

    public Object copyToHeapWhenNeeded(Object value) {
        // by default, no need to copy
        return value;
    }

    protected void removeKeyFromExpirySystem(Data key) {
        expirySystem.removeKeyFromExpirySystem(key);
    }

    @Override
    public void removeBackup(Data key, CallerProvenance provenance) {
        removeBackupInternal(key, provenance, null);
    }

    @Override
    public void removeBackupTxn(Data key, CallerProvenance provenance, UUID transactionId) {
        removeBackupInternal(key, provenance, transactionId);
    }

    private void removeBackupInternal(Data key, CallerProvenance provenance, UUID transactionId) {
        long now = getNow();

        Record record = getRecordOrNull(key, now, true);
        if (record == null) {
            return;
        }
        removeRecord0(key, record, true);
        if (persistenceEnabledFor(provenance)) {
            mapDataStore.removeBackup(key, now, transactionId);
        }
    }

    @Override
    public boolean delete(Data key, CallerProvenance provenance) {
        checkIfLoaded();
        long now = getNow();

        Record record = getRecordOrNull(key, now, false);
        if (record == null) {
            if (persistenceEnabledFor(provenance)) {
                mapDataStore.remove(key, now, null);
            }
        } else {
            Object oldValue = removeRecord(key, record, now, provenance, null);
            updateStatsOnRemove(now);
            return oldValue != null;
        }
        return false;
    }

    @Override
    public Object removeTxn(Data dataKey, CallerProvenance callerProvenance, UUID transactionId) {
        return removeInternal(dataKey, callerProvenance, transactionId);
    }

    @Override
    public Object remove(Data key, CallerProvenance callerProvenance) {
        return removeInternal(key, callerProvenance, null);
    }

    private Object removeInternal(Data key, CallerProvenance provenance,
                                  UUID transactionId) {
        checkIfLoaded();

        long now = getNow();
        Record record = getRecordOrNull(key, now, false);
        Object oldValue;
        if (record == null) {
            oldValue = loadValueOfKey(key, now);
            if (oldValue != null && persistenceEnabledFor(provenance)) {
                mapDataStore.remove(key, now, transactionId);
                updateStatsOnRemove(now);
            }
        } else {
            oldValue = removeRecord(key, record, now, provenance, transactionId);
            updateStatsOnRemove(now);
        }
        return oldValue;
    }

    @Override
    public boolean remove(Data key, Object expect) {
        checkIfLoaded();
        long now = getNow();

        Record record = getRecordOrNull(key, now, false);
        Object oldValue;
        boolean removed = false;
        if (record == null) {
            oldValue = loadValueOfKey(key, now);
            if (oldValue == null) {
                return false;
            }
        } else {
            oldValue = record.getValue();
        }

        if (valueComparator.isEqual(expect, oldValue, serializationService)) {
            mapServiceContext.interceptRemove(interceptorRegistry, oldValue);
            mapDataStore.remove(key, now, null);
            if (record != null) {
                onStore(record);
                removeRecord0(key, record, false);
                updateStatsOnRemove(now);
            }
            removed = true;
        }
        return removed;
    }

    @Override
    public Object get(Data key, boolean backup, Address callerAddress, boolean touch) {
        checkIfLoaded();
        long now = getNow();

        Record record = getRecordOrNull(key, now, backup);
        if (record != null && touch) {
            accessRecord(key, record, now);
        } else if (record == null && mapDataStore != EMPTY_MAP_DATA_STORE) {
            record = loadRecordOrNull(key, backup, callerAddress, now);
            record = evictIfExpired(key, now, backup) ? null : record;
        }
        Object value = record == null ? null : record.getValue();
        value = mapServiceContext.interceptGet(interceptorRegistry, value);

        return value;
    }

    /**
     * This method is called directly by user threads, in other words
     * it is called outside of the partition threads.
     */
    @Override
    public Data readBackupData(Data key) {
        Record record = getRecord(key);

        if (record == null) {
            return null;
        } else {
            if (partitionService.isPartitionOwner(partitionId)) {
                // set last access time to prevent
                // premature removal of the entry because
                // of idleness based expiry
                record.setLastAccessTime(getNow());
            }
        }

        Object value = mapServiceContext.interceptGet(interceptorRegistry, record.getValue());
        mapServiceContext.interceptAfterGet(interceptorRegistry, value);
        // this serialization step is needed not to expose the object, see issue 1292
        return mapServiceContext.toData(value);
    }

    @Override
    public MapEntries getAll(Set<Data> keys, Address callerAddress) {
        checkIfLoaded();
        long now = getNow();

        MapEntries mapEntries = getInMemoryEntries(keys, now);

        // then try to load missing keys from map-store
        if (mapDataStore != EMPTY_MAP_DATA_STORE && !keys.isEmpty()) {
            List keyBiTupleList = loadMultipleKeys(keys);
            Map<Data, Object> loadedEntries = putAndGetLoadedEntries(keyBiTupleList, callerAddress);
            addToMapEntrySet(mapEntries, loadedEntries);
        }

        return mapEntries;
    }

    public MapEntries getInMemoryEntries(Set<Data> keys, long now) {
        MapEntries mapEntries = new MapEntries(keys.size());

        // first search in memory
        Iterator<Data> iterator = keys.iterator();
        while (iterator.hasNext()) {
            Data key = iterator.next();
            Record record = getRecordOrNull(key, now, false);
            if (record != null) {
                addToMapEntrySet(key, record.getValue(), mapEntries);
                accessRecord(key, record, now);
                iterator.remove();
            }
        }
        return mapEntries;
    }

    public List loadMultipleKeys(Collection keysToLoad) {
        long now = getNow();
        Map loadedKeyValuePairs = mapDataStore.loadAll(keysToLoad);
        List keyBiTupleList = new ArrayList<>();
        Set<Map.Entry> set = loadedKeyValuePairs.entrySet();
        for (Map.Entry entry : set) {
            Object loadedKey = entry.getKey();
            Object loadedValue = entry.getValue();
            Data key = toData(loadedKey);

            if (key == null) {
                String msg = String.format("Key cannot be null.[mapName: %s]", name);
                throw new NullPointerException(msg);
            }

            if (loadedValue == null) {
                // we haven't found any matching value to load, this
                // is a legitimate case when loading not-existing keys
                // from a map-loader, so we can continue with the rest.
                continue;
            }

            BiTuple<Object, Long> biTuple = getOldValueWithTtlTupleOrNull(loadedValue, now);
            if (biTuple != null) {
                if (biTuple.element1 == null) {
                    // EntryLoader#MetadataAwareValue can hold value as null,
                    // here we check that possibility.
                    continue;
                }

                keyBiTupleList.add(key);
                keyBiTupleList.add(biTuple);
            }
        }
        return keyBiTupleList;
    }

    public Map<Data, Object> putAndGetLoadedEntries(List loadedKeyAndOldValueWithTtlPairs,
                                                    Address callerAddress) {
        if (CollectionUtil.isEmpty(loadedKeyAndOldValueWithTtlPairs)) {
            return Collections.emptyMap();
        }

        // holds serialized keys and if values are
        // serialized, also holds them in serialized format.
        Map<Data, Object> resultMap = createHashMap(loadedKeyAndOldValueWithTtlPairs.size() / 2);

        // add loaded key-value pairs to this record-store.
        for (int i = 0; i < loadedKeyAndOldValueWithTtlPairs.size(); i += 2) {
            Object loadedKey = loadedKeyAndOldValueWithTtlPairs.get(i);
            BiTuple<Object, Long> valueAndTtl = (BiTuple<Object, Long>) loadedKeyAndOldValueWithTtlPairs.get(i + 1);
            Data key = toData(loadedKey);
            Object value = valueAndTtl.element1;
            Long ttl = valueAndTtl.element2;
            putFromLoadInternal(key, value, ttl, UNSET, callerAddress, StaticParams.PUT_FROM_LOAD_PARAMS);

            resultMap.put(key, value);
        }

        if (hasQueryCache()) {
            for (Data key : resultMap.keySet()) {
                Record record = storage.get(key);
                // here we are only publishing events for loaded
                // entries. This is required for notifying query-caches
                // otherwise query-caches cannot see loaded entries
                addEventToQueryCache(key, record);
            }
        }
        return resultMap;
    }

    protected void addToMapEntrySet(Object key, Object value, MapEntries mapEntries) {
        if (key == null || value == null) {
            return;
        }
        value = mapServiceContext.interceptGet(interceptorRegistry, value);
        Data dataKey = mapServiceContext.toData(key);
        Data dataValue = mapServiceContext.toData(value);
        mapEntries.add(dataKey, dataValue);
    }

    public void addToMapEntrySet(MapEntries mapEntries, Map<?, ?> entries) {
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            addToMapEntrySet(entry.getKey(), entry.getValue(), mapEntries);
        }
    }

    @Override
    public boolean existInMemory(Data key) {
        return storage.containsKey(key);
    }

    @Override
    public boolean containsKey(Data key, Address callerAddress) {
        checkIfLoaded();
        long now = getNow();

        Record record = getRecordOrNull(key, now, false);
        if (record == null) {
            record = loadRecordOrNull(key, false, callerAddress, now);
        }
        boolean contains = record != null;
        if (contains) {
            accessRecord(key, record, now);
        }

        return contains;
    }

    /**
     * @return {@code true} if this IMap has any query-cache, otherwise return {@code false}
     */
    @Override
    public boolean hasQueryCache() {
        QueryCacheContext queryCacheContext = mapServiceContext.getQueryCacheContext();
        PublisherContext publisherContext = queryCacheContext.getPublisherContext();
        MapPublisherRegistry mapPublisherRegistry = publisherContext.getMapPublisherRegistry();
        PublisherRegistry publisherRegistry = mapPublisherRegistry.getOrNull(name);
        return publisherRegistry != null;
    }

    private void addEventToQueryCache(Data dataKey, Record record) {
        EntryEventData eventData = new EntryEventData(thisAddress.toString(), name, thisAddress,
                dataKey, mapServiceContext.toData(record.getValue()),
                null, null, ADDED.getType());

        mapEventPublisher.addEventToQueryCache(eventData);
    }

    @Override
    public boolean setTtl(Data key, long ttl) {
        long now = getNow();
        Object oldValue = putInternal(key, null, true, ttl, UNSET, UNSET,
                now, null, null, null, StaticParams.SET_TTL_PARAMS);
        return oldValue != null;
    }

    @Override
    public boolean setTtlBackup(Data key, long ttl) {
        long now = getNow();
        Object oldValue = putInternal(key, null, true, ttl, UNSET, UNSET,
                now, null, null, null, StaticParams.SET_TTL_BACKUP_PARAMS);
        return oldValue != null;
    }

    @Override
    public Object set(Data dataKey, Object value, long ttl, long maxIdle) {
        long now = getNow();
        return putInternal(dataKey, value, true, ttl, maxIdle, UNSET,
                now, null, null, null, StaticParams.SET_PARAMS);
    }

    @Override
    public Object setTxn(Data dataKey, Object value, long ttl, long maxIdle, UUID transactionId) {
        long now = getNow();
        return putInternal(dataKey, value, true, ttl, maxIdle, UNSET,
                now, null, transactionId, null, StaticParams.SET_PARAMS);
    }

    @Override
    public Object put(Data key, Object value, long ttl, long maxIdle) {
        long now = getNow();
        return putInternal(key, value, true, ttl, maxIdle, UNSET,
                now, null, null, null, StaticParams.PUT_PARAMS);
    }

    /**
     * Core put method for all variants of puts/updates.
     *
     * @return old value if this is an update operation, otherwise returns null
     */
    @SuppressWarnings({"checkstyle:npathcomplexity",
            "checkstyle:parameternumber", "checkstyle:cyclomaticcomplexity"})
    private Object putInternal(Data key, Object newValue, boolean changeExpiryOnUpdate, long ttl,
                               long maxIdle, long expiryTime, long now, Object expectedValue,
                               @Nullable UUID transactionId, Address callerAddress,
                               StaticParams staticParams) {
        // If this method has to wait end of map loading.
        if (staticParams.isCheckIfLoaded()) {
            checkIfLoaded();
        }

        Object oldValue = null;

        // Get record by checking expiry, if expired, evict record.
        Record record = getRecordOrNull(key, now, staticParams.isBackup());

        // Variants of loading oldValue
        if (staticParams.isPutVanilla()) {
            oldValue = record == null
                    ? (staticParams.isLoad() ? loadValueOfKey(key, now) : null) : record.getValue();
        } else if (staticParams.isPutIfAbsent()) {
            record = getOrLoadRecord(record, key, now, callerAddress, staticParams.isBackup());
            // if this is an existing record, return existing value.
            if (record != null) {
                return record.getValue();
            }
        } else if (staticParams.isPutIfExists()) {
            // For methods like setTtl and replace,
            // when no matching record, just return.
            record = getOrLoadRecord(record, key, now, callerAddress, staticParams.isBackup());
            if (record == null) {
                return null;
            }
            oldValue = record.getValue();
            newValue = staticParams.isSetTtl() ? oldValue : newValue;
        }

        // For method replace, if current value is not expected one, return.
        if (staticParams.isPutIfEqual()
                && !valueComparator.isEqual(expectedValue, oldValue, serializationService)) {
            return null;
        }

        // Intercept put on owner partition.
        //
        // As an exception to the above statement, we allow the
        // interceptor to run on backup replicas when an EntryProcessor
        // (EP) is used. This is because the EP is executed on backup
        // replicas as well (instead of simply copying the result
        // from the owner replica to the backup), and we need to
        // ensure consistency between replicas in this scenario.
        if (!staticParams.isBackup() || staticParams.isBackupEntryProcessor()) {
            newValue = mapServiceContext.interceptPut(interceptorRegistry, oldValue, newValue);
        }

        // Put new record or update existing one.
        if (record == null) {
            putNewRecord(key, oldValue, newValue, ttl, maxIdle, expiryTime, now,
                    transactionId, staticParams.isPutFromLoad() ? LOADED : ADDED,
                    staticParams.isStore(), staticParams.isBackup());
        } else {
            oldValue = updateRecord(record, key, oldValue, newValue, changeExpiryOnUpdate,
                    ttl, maxIdle, expiryTime, now, transactionId,
                    staticParams.isStore(), staticParams.isCountAsAccess(), staticParams.isBackup());
        }
        return oldValue;
    }

    @SuppressWarnings("checkstyle:parameternumber")
    protected Record putNewRecord(Data key, Object oldValue, Object newValue, long ttl,
                                  long maxIdle, long expiryTime, long now, UUID transactionId,
                                  EntryEventType entryEventType, boolean store,
                                  boolean backup) {
        Record record = createRecord(key, newValue, now);
        if (mapDataStore != EMPTY_MAP_DATA_STORE && store) {
            putIntoMapStore(record, key, newValue, ttl, maxIdle, now, transactionId);
        }
        putMemory(record, key, oldValue, ttl, maxIdle, expiryTime, now, entryEventType, backup);
        return record;
    }

    @SuppressWarnings("checkstyle:parameternumber")
    public Record putMemory(Record record, Data key, Object oldValue, long ttl, long maxIdle,
                            long expiryTime, long now, EntryEventType entryEventType,
                            boolean backup) {
        storage.put(key, record);
        expirySystem.add(key, ttl, maxIdle, expiryTime, now, now);

        if (entryEventType == EntryEventType.LOADED) {
            mutationObserver.onLoadRecord(key, record, backup);
        } else {
            mutationObserver.onPutRecord(key, record, oldValue, backup);
        }

        return record;
    }

    @SuppressWarnings("checkstyle:parameternumber")
    protected Object updateRecord(Record record, Data key, Object oldValue, Object newValue,
                                  boolean changeExpiryOnUpdate, long ttl, long maxIdle,
                                  long expiryTime, long now, UUID transactionId,
                                  boolean store, boolean countAsAccess, boolean backup) {
        updateRecord0(record, now, countAsAccess);

        if (mapDataStore != EMPTY_MAP_DATA_STORE && store) {
            newValue = putIntoMapStore(record, key, newValue,
                    ttl, maxIdle, now, transactionId);
        }

        return updateMemory(record, key, oldValue, newValue, changeExpiryOnUpdate,
                ttl, maxIdle, expiryTime, now, backup);
    }

    public void updateRecord0(Record record, long now, boolean countAsAccess) {
        updateStatsOnPut(countAsAccess, now);
        record.onUpdate(now);

        if (countAsAccess) {
            record.onAccess(now);
        }
    }

    @SuppressWarnings("checkstyle:parameternumber")
    public Object updateMemory(Record record, Data key, Object oldValue, Object newValue,
                               boolean changeExpiryOnUpdate, long ttl, long maxIdle,
                               long expiryTime, long now, boolean backup) {
        Record latestRecordAfterUpdate = storage.updateRecordValue(key, record, newValue);
        if (changeExpiryOnUpdate) {
            expirySystem.add(key, ttl, maxIdle, expiryTime, now, now);
        }
        mutationObserver.onUpdateRecord(key, latestRecordAfterUpdate, oldValue, newValue, backup);
        return oldValue;
    }

    private Record getOrLoadRecord(@Nullable Record record, Data key,
                                   long now, Address callerAddress, boolean backup) {
        if (record != null) {
            accessRecord(key, record, now);
            return record;
        }

        return loadRecordOrNull(key, backup, callerAddress, now);
    }

    public Object putIntoMapStore(Record record, Data key, Object newValue,
                                  long ttlMillis, long maxIdleMillis,
                                  long now, UUID transactionId) {
        long expirationTime = expirySystem.calculateExpirationTime(ttlMillis, maxIdleMillis, now, now);
        newValue = mapDataStore.add(key, newValue, expirationTime, now, transactionId);
        if (mapDataStore.isPostProcessingMapStore()) {
            storage.updateRecordValue(key, record, newValue);
        }
        onStore(record);
        return newValue;
    }

    // returns newValue
    public Object putIntoMapStore0(Data key, Object newValue,
                                   long ttlMillis, long maxIdleMillis,
                                   long now, UUID transactionId) {
        long expirationTime = expirySystem.calculateExpirationTime(ttlMillis, maxIdleMillis, now, now);
        return mapDataStore.add(key, newValue, expirationTime, now, transactionId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public MapMergeResponse merge(MapMergeTypes<Object, Object> mergingEntry,
                                  SplitBrainMergePolicy<Object, MapMergeTypes<Object, Object>, Object> mergePolicy,
                                  CallerProvenance provenance) {
        checkIfLoaded();
        long now = getNow();

        mergingEntry = (MapMergeTypes<Object, Object>) serializationService.getManagedContext().initialize(mergingEntry);
        mergePolicy = (SplitBrainMergePolicy<Object, MapMergeTypes<Object, Object>, Object>)
                serializationService.getManagedContext().initialize(mergePolicy);

        Data key = (Data) mergingEntry.getRawKey();
        Record record = getRecordOrNull(key, now, false);
        Object newValue;
        Object oldValue;
        if (record == null) {
            newValue = mergePolicy.merge(mergingEntry, null);
            if (newValue == null) {
                return MapMergeResponse.NO_MERGE_APPLIED;
            }
            boolean persist = persistenceEnabledFor(provenance);
            Record newRecord = putNewRecord(key, null, newValue, UNSET, UNSET, UNSET, now,
                    null, ADDED, persist, false);
            mergeRecordExpiration(key, newRecord, mergingEntry, now);
            return MapMergeResponse.RECORD_CREATED;
        } else {
            oldValue = copyToHeapWhenNeeded(record.getValue());
            ExpiryMetadata expiryMetadata = expirySystem.getExpiryMetadata(key);
            MapMergeTypes<Object, Object> existingEntry
                    = createMergingEntry(serializationService, key, record, expiryMetadata);
            newValue = mergePolicy.merge(mergingEntry, existingEntry);
            // existing entry will be removed
            if (newValue == null) {
                if (persistenceEnabledFor(provenance)) {
                    mapDataStore.remove(key, now, null);
                }
                onStore(record);
                removeRecord0(key, record, false);
                return MapMergeResponse.RECORD_REMOVED;
            }

            if (valueComparator.isEqual(newValue, oldValue, serializationService)) {
                // When receiving WAN replicated data, it is possible that the merge policy rejects an incoming
                //  value, which would result in the above condition being true (merge policy selects the existing
                //  value as the outcome). However, we do not want to apply metadata changes if we are rejecting
                //  the merge and sticking with our original data. Due to current limitations of the
                //  SplitBrainMergePolicy, we have no view on whether the merge policy modified the outcome or not,
                //  only the resultant value. Long-term we need to address this shortfall in merge policies, but
                //  for now the additional check below allows us to make an educated guess about whether the merge
                //  changed data and use that. Since this only matters for WAN-received merge events, we can avoid
                //  additional overhead by checking provenance. Fixes HZ-3392, Backlog for merge changes: HZ-3397
                boolean shouldMergeExpiration = provenance != CallerProvenance.WAN
                        || valueComparator.isEqual(existingEntry.getRawValue(), mergingEntry.getRawValue(), serializationService);
                if (shouldMergeExpiration && mergeRecordExpiration(key, record, mergingEntry, now)) {
                    return MapMergeResponse.RECORD_EXPIRY_UPDATED;
                }
                return MapMergeResponse.RECORDS_ARE_EQUAL;
            }

            boolean persist = persistenceEnabledFor(provenance);
            updateRecord(record, key, oldValue, newValue, true, UNSET, UNSET, UNSET,
                    now, null, persist, true, false);
            mergeRecordExpiration(key, record, mergingEntry, now);
            return MapMergeResponse.RECORD_UPDATED;
        }
    }

    @Override
    public Object replace(Data key, Object update) {
        long now = getNow();
        return putInternal(key, update, true, UNSET, UNSET, UNSET,
                now, null, null, null, StaticParams.REPLACE_PARAMS);
    }

    @Override
    public boolean replace(Data key, Object expect, Object update) {
        long now = getNow();
        Object oldValue = putInternal(key, update, true, UNSET, UNSET, UNSET,
                now, expect, null, null, StaticParams.REPLACE_IF_SAME_PARAMS);
        return oldValue != null;
    }

    @Override
    public Object putTransient(Data key, Object value, long ttl, long maxIdle) {
        long now = getNow();
        Object oldValue = putInternal(key, value, true, ttl, maxIdle, UNSET,
                now, null, null, null, StaticParams.PUT_TRANSIENT_PARAMS);
        mapDataStore.addTransient(key, now);
        return oldValue;
    }

    @Override
    public Object putFromLoad(Data key, Object value, Address callerAddress) {
        return putFromLoadInternal(key, value, UNSET, UNSET, callerAddress, StaticParams.PUT_FROM_LOAD_PARAMS);
    }

    @Override
    public Object putFromLoad(Data key, Object value, long expirationTime, Address callerAddress, long now) {
        long ttl = toTtlFromExpiryTime(expirationTime, now);
        if (ttl <= 0) {
            return null;
        }
        return putFromLoadInternal(key, value, ttl, UNSET, callerAddress, StaticParams.PUT_FROM_LOAD_PARAMS);
    }

    @Override
    public Object putFromLoadBackup(Data key, Object value) {
        return putFromLoadInternal(key, value, UNSET, UNSET,
                null, StaticParams.PUT_FROM_LOAD_BACKUP_PARAMS);
    }

    @Override
    public Object putFromLoadBackup(Data key, Object value, long expirationTime, long now) {
        long ttl = toTtlFromExpiryTime(expirationTime, now);
        if (ttl <= 0) {
            return null;
        }
        return putFromLoadInternal(key, value, ttl, UNSET,
                null, StaticParams.PUT_FROM_LOAD_BACKUP_PARAMS);
    }

    private Object putFromLoadInternal(Data key, Object newValue, long ttl,
                                       long maxIdle, Address callerAddress, StaticParams staticParams) {
        checkKeyAndValue(key, newValue);

        if (shouldEvict()) {
            return null;
        }

        long now = getNow();
        Object oldValue = putInternal(key, newValue, true, ttl, maxIdle, UNSET,
                now, null, null, null, staticParams);

        if (!staticParams.isBackup() && mapEventPublisher.hasEventListener(name)) {
            Record record = getRecord(key);
            EntryEventType entryEventType = oldValue == null ? LOADED : UPDATED;
            mapEventPublisher.publishEvent(callerAddress, name, entryEventType, key, oldValue, record.getValue());
        }

        return oldValue;

    }

    private void checkKeyAndValue(Data key, Object value) {
        if (key == null || value == null) {
            String msg = String.format("Neither key nor value can be loaded as null.[mapName: %s, key: %s, value: %s]",
                    name, serializationService.toObject(key), serializationService.toObject(value));
            throw new NullPointerException(msg);
        }

        if (partitionService.getPartitionId(key) != partitionId) {
            throw new IllegalStateException("MapLoader loaded an item belongs to a different partition");
        }
    }

    @Override
    public boolean setWithUncountedAccess(Data dataKey, Object value,
                                          boolean changeExpiryOnUpdate, long ttl, long maxIdle) {
        long now = getNow();
        Object oldValue = putInternal(dataKey, value, changeExpiryOnUpdate, ttl, maxIdle, UNSET,
                now, null, null, null, StaticParams.SET_WITH_NO_ACCESS_PARAMS);
        return oldValue == null;
    }

    @Override
    public Object putIfAbsent(Data key, Object value, long ttl,
                              long maxIdle, Address callerAddress) {
        long now = getNow();
        return putInternal(key, value, true, ttl, maxIdle, UNSET,
                now, null, null, callerAddress, StaticParams.PUT_IF_ABSENT_PARAMS);
    }

    protected Object removeRecord(Data key, @Nonnull Record record,
                                  long now, CallerProvenance provenance,
                                  UUID transactionId) {
        Object oldValue = copyToHeapWhenNeeded(record.getValue());
        oldValue = mapServiceContext.interceptRemove(interceptorRegistry, oldValue);
        if (oldValue != null) {
            if (persistenceEnabledFor(provenance)) {
                mapDataStore.remove(key, now, transactionId);
            }
            onStore(record);
        }
        removeRecord0(key, record, false);
        return oldValue;
    }

    public void removeRecord0(Data key, @Nonnull Record record, boolean backup) {
        mutationObserver.onRemoveRecord(key, record, backup);
        removeKeyFromExpirySystem(key);
        storage.removeRecord(key, record);
    }

    public void removeByKey(Data key, boolean backup) {
        Record record = getRecord(key);
        removeRecord0(key, record, backup);
    }

    @Override
    public Record getRecordOrNull(Data key, boolean backup) {
        long now = getNow();
        return getRecordOrNull(key, now, backup);
    }

    public Record getRecordOrNull(Data key, long now, boolean backup) {
        return getRecordOrNull(key, now, backup, false);
    }

    public Record getRecordOrNull(Data key, boolean backup, boolean noCaching) {
        long now = getNow();
        return getRecordOrNull(key, now, backup, noCaching);
    }

    /**
     * Returns live record or null if record is already expired. Does not load missing keys from a map store.
     *
     * @param key       key to be accessed
     * @param now       the now timestamp
     * @param backup    true if partition is a backup-partition otherwise set false
     * @param noCaching true if the record should be returned as it is in the record store.
     *                  Applies to the tiered storage if a record read from device and then there is no
     *                  copying (caching) it in-memory region. For other types of record store
     *                  the flag is ignored.
     * @return live record or null
     * @see #get
     */
    public Record getRecordOrNull(Data key, long now, boolean backup, boolean noCaching) {
        Record record = storage.get(key);
        if (record != null) {
            return evictIfExpired(key, now, backup) ? null : record;
        }

        return null;
    }

    public void onStore(Record record) {
        if (record == null || mapDataStore == EMPTY_MAP_DATA_STORE) {
            return;
        }

        record.onStore();
    }

    private void updateStoreStats() {
        if (!(mapDataStore instanceof WriteBehindStore)
                || !mapContainer.getMapConfig().isPerEntryStatsEnabled()) {
            return;
        }

        long now = getNow();
        WriteBehindQueue<DelayedEntry> writeBehindQueue
                = ((WriteBehindStore) mapDataStore).getWriteBehindQueue();
        List<DelayedEntry> delayedEntries = writeBehindQueue.asList();
        for (DelayedEntry delayedEntry : delayedEntries) {
            Record record = getRecordOrNull(toData(delayedEntry.getKey()), now, false);
            onStore(record);
        }
    }

    @Override
    public boolean isKeyLoadFinished() {
        if (keyLoader == null) {
            // null means no MapLoader was configured
            return true;
        }
        return keyLoader.isKeyLoadFinished();
    }

    @Override
    public void checkIfLoaded() {
        if (mapDataStore == EMPTY_MAP_DATA_STORE
                || loadingFutures.isEmpty()) {
            return;
        }

        assert keyLoader != null;

        if (FutureUtil.allDone(loadingFutures)) {
            List<Future<?>> doneFutures = emptyList();
            try {
                doneFutures = FutureUtil.getAllDone(loadingFutures);
                // check all finished loading futures for exceptions
                FutureUtil.checkAllDone(doneFutures);
            } catch (Exception e) {
                logger.severe("Exception while loading map " + name, e);
                throw ExceptionUtil.rethrow(e);
            } finally {
                loadingFutures.removeAll(doneFutures);
            }
        } else {
            keyLoader.triggerLoadingWithDelay();
            throw new RetryableHazelcastException("Map " + getName()
                    + " is still loading data from external store");
        }
    }

    @Override
    public boolean isLoaded() {
        boolean result = FutureUtil.allDone(loadingFutures);
        if (result) {
            loadingFutures.removeAll(FutureUtil.getAllDone(loadingFutures));
        }

        return result;
    }

    // only used for testing purposes
    public Collection<Future<?>> getLoadingFutures() {
        return loadingFutures;
    }

    @Override
    public void startLoading() {
        if (logger.isFinestEnabled()) {
            logger.finest("StartLoading invoked " + getStateMessage());
        }
        if (mapStoreContext.isMapLoader() && !loadedOnCreate) {
            assert keyLoader != null;

            if (!loadedOnPreMigration) {
                if (logger.isFinestEnabled()) {
                    logger.finest("Triggering load " + getStateMessage());
                }
                loadedOnCreate = true;
                addLoadingFuture(keyLoader.startInitialLoad(mapStoreContext, partitionId));
            } else {
                if (logger.isFinestEnabled()) {
                    logger.finest("Promoting to loaded on migration " + getStateMessage());
                }
                keyLoader.promoteToLoadedOnMigration();
            }
        }
    }

    private void addLoadingFuture(Future<?> e) {
        if (e instanceof CompletableFuture<?> future) {
            future.whenCompleteAsync((result, throwable) -> {
                if (throwable != null) {
                    logger.warning("Loading completed exceptionally", throwable);
                }
            }, CALLER_RUNS);
        }
        loadingFutures.add(e);
    }

    @Override
    public void setPreMigrationLoadedStatus(boolean loaded) {
        loadedOnPreMigration = loaded;
    }

    @Override
    public void loadAll(boolean replaceExistingValues) {
        if (keyLoader == null) {
            return;
        }
        if (logger.isFinestEnabled()) {
            logger.finest("loadAll invoked " + getStateMessage());
        }

        logger.info("Starting to load all keys for map " + name + " on partitionId=" + partitionId);
        Future<?> loadingKeysFuture = keyLoader.startLoading(mapStoreContext, replaceExistingValues);
        addLoadingFuture(loadingKeysFuture);
    }

    @Override
    public void loadAllFromStore(List<Data> keys,
                                 boolean replaceExistingValues) {
        if (!keys.isEmpty()) {
            Future<?> f = recordStoreLoader.loadValues(keys, replaceExistingValues);
            addLoadingFuture(f);
        }

        // We should not track key loading here. IT's not key loading but values loading.
        // Apart from that it's irrelevant for RECEIVER nodes. SENDER and SENDER_BACKUP will track the key-loading anyway.
        // Fixes https://github.com/hazelcast/hazelcast/issues/9255
    }

    @Override
    public void updateLoadStatus(boolean lastBatch, Throwable exception) {
        if (keyLoader == null) {
            return;
        }

        keyLoader.trackLoading(lastBatch, exception);

        if (lastBatch) {
            logger.finest("Completed loading map " + name + " on partitionId=" + partitionId);
        }
    }

    @Override
    public void maybeDoInitialLoad() {
        if (keyLoader != null && keyLoader.shouldDoInitialLoad()) {
            loadAll(false);
        }
    }

    private String getStateMessage() {
        // due to weird issue with OpenJ9 implementation of string concatenation:
        //noinspection StringBufferReplaceableByString
        StringBuilder sb = new StringBuilder();
        sb.append("on partitionId=");
        sb.append(partitionId);
        sb.append(" on ");
        sb.append(mapServiceContext.getNodeEngine().getThisAddress());
        sb.append(" loadedOnCreate=");
        sb.append(loadedOnCreate);
        sb.append(" loadedOnPreMigration=");
        sb.append(loadedOnPreMigration);
        sb.append(" isLoaded=");
        sb.append(isLoaded());
        return sb.toString();
    }

    @Override
    public int evictAll(boolean backup) {
        checkIfLoaded();

        ArrayList<Data> keys = new ArrayList<>();
        ArrayList<Record> records = new ArrayList<>();
        // we don't remove locked keys. These are clearable records.
        forEach(new BiConsumer<>() {
            final Set<Data> lockedKeySet = lockStore.getLockedKeys();

            @Override
            public void accept(Data dataKey, Record record) {
                if (lockedKeySet != null && !lockedKeySet.contains(dataKey)) {
                    keys.add(isTieredStorageEnabled() ? toHeapData(dataKey) : dataKey);
                    records.add(record);
                }

            }
        }, true);

        flush(keys, records, backup);
        return evictBulk(keys, records, backup);
    }

    // TODO optimize when no map-datastore
    @Override
    public int clear(boolean backup) {
        checkIfLoaded();

        ArrayList<Data> keys = new ArrayList<>();
        ArrayList<Record> records = new ArrayList<>();
        // we don't remove locked keys. These are clearable records.
        forEach(new BiConsumer<>() {
            final Set<Data> lockedKeySet = lockStore.getLockedKeys();

            @Override
            public void accept(Data dataKey, Record record) {
                if (lockedKeySet != null && !lockedKeySet.contains(dataKey)) {
                    keys.add(isTieredStorageEnabled() ? toHeapData(dataKey) : dataKey);
                    records.add(record);
                }

            }
        }, isBackup(this));
        // This conversion is required by mapDataStore#removeAll call.
        mapDataStore.removeAll(keys);
        mapDataStore.reset();
        int removedKeyCount = removeBulk(keys, records, backup);
        if (removedKeyCount > 0) {
            updateStatsOnRemove(Clock.currentTimeMillis());
        }
        return removedKeyCount;
    }

    private boolean isBackup(RecordStore recordStore) {
        int partitionId = recordStore.getPartitionId();
        IPartition partition = partitionService.getPartition(partitionId, false);
        return !partition.isLocal();
    }

    @Override
    public void reset() {
        try {
            mutationObserver.onReset();
        } finally {
            mapDataStore.reset();
            expirySystem.clear();
            storage.clear(false);
            stats.reset();
        }
    }

    @Override
    public void destroy() {
        clearPartition(false, true);
    }

    @Override
    public void clearPartition(boolean onShutdown, boolean onStorageDestroy) {
        clearLockStore();
        mapDataStore.reset();

        if (onShutdown) {
            if (hasPooledMemoryAllocator()) {
                destroyStorageImmediate(true, true);
            } else {
                destroyStorageAfterClear(true, true);
            }
        } else {
            if (onStorageDestroy) {
                destroyStorageAfterClear(false, false);
            } else {
                clearStorage(false);
            }
        }
    }

    private boolean hasPooledMemoryAllocator() {
        NodeEngine nodeEngine = mapServiceContext.getNodeEngine();
        NativeMemoryConfig nativeMemoryConfig = nodeEngine.getConfig().getNativeMemoryConfig();
        return nativeMemoryConfig != null && nativeMemoryConfig.getAllocatorType() == POOLED;
    }

    public void destroyStorageImmediate(boolean isDuringShutdown,
                                        boolean internal) {
        mutationObserver.onDestroy(isDuringShutdown, internal);
        expirySystem.destroy();
        destroyMetadataStore();
        // Destroy storage in the end
        storage.destroy(isDuringShutdown);
    }

    /**
     * Calls also {@link #clearStorage(boolean)} to release allocated HD memory
     * of key+value pairs because
     * only releases internal resources of backing data structure.
     *
     * @param isDuringShutdown {@link Storage#clear(boolean)}
     * @param internal         see {@link MutationObserver#onDestroy(boolean, boolean)}}
     */
    public void destroyStorageAfterClear(boolean isDuringShutdown, boolean internal) {
        clearStorage(isDuringShutdown);
        destroyStorageImmediate(isDuringShutdown, internal);
    }

    private void clearStorage(boolean isDuringShutdown) {
        try {
            mutationObserver.onClear();
        } finally {
            expirySystem.clear();
            storage.clear(isDuringShutdown);
        }
    }

    private void clearLockStore() {
        NodeEngine nodeEngine = mapServiceContext.getNodeEngine();
        LockSupportService lockService = nodeEngine.getServiceOrNull(LockSupportService.SERVICE_NAME);
        if (lockService != null) {
            ObjectNamespace namespace = MapService.getObjectNamespace(name);
            lockService.clearLockStore(partitionId, namespace);
        }
    }

    @Override
    public Set<MapOperation> getOffloadedOperations() {
        return offloadedOperations;
    }

    @Override
    public boolean isTieredStorageEnabled() {
        return mapContainer.getMapConfig().getTieredStoreConfig().isEnabled();
    }
}
