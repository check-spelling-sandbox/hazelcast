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

import com.hazelcast.core.EntryView;
import com.hazelcast.internal.iteration.IterationPointer;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.map.impl.EntryCostEstimator;
import com.hazelcast.map.impl.iterator.MapEntriesWithCursor;
import com.hazelcast.map.impl.iterator.MapKeysWithCursor;
import com.hazelcast.map.impl.operation.steps.engine.Step;
import com.hazelcast.map.impl.record.Record;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.Map;

/**
 * Represents actual storage layer behind a {@link RecordStore}.
 * Includes basic storage operations.
 *
 * @param <K> the key type to be put in this storage.
 * @param <R> the value type to be put in this storage.
 */
public interface Storage<K, R> {

    /**
     * @return true if current configuration of this {@link Storage}
     * supports executions as a chain of {@link Step} , false otherwise.
     */
    default boolean supportsSteppedRun() {
        return false;
    }

    void put(K key, R record);

    /**
     * Updates record's value. Performs an update in-place if the record can accommodate the
     * new value (applicable for the inlined records only). Otherwise, creates a new record
     * with the new value.
     *
     * @param key    the entry's key
     * @param record the record
     * @param value  the new value
     * @return the record that contains new value.
     */
    R updateRecordValue(K key, R record, Object value);

    R get(K key);

    /**
     * Returns a record from storage by key. The record is safe to use
     * in the scope of the callers thread so that the backing memory
     * cannot be GCed.
     * <p>
     * The call is equivalent to {@link #get(Object)} by default and is
     * overridden for TS storage.
     * @param key the search key
     * @return the record
     */
    default Record getSafe(K key) {
        return (Record) get(key);
    }

    /**
     * Gives the same result as {@link #get(Object)}, but with the
     * additional constraint that the supplied key must not just
     * be equal to, but be exactly the same key blob (at the same
     * memory address) as the one stored. The implementation of this
     * method is only needed for the HD memory-based implementations.
     */
    R getIfSameKey(K key);

    void removeRecord(Data dataKey, @Nonnull R record);

    boolean containsKey(K key);

    /**
     * Read-only and not thread-safe iterator.
     * <p>
     * Returned iterator from this method doesn't throw {@link
     * java.util.ConcurrentModificationException} to fail fast. Because fail
     * fast may not be the desired behaviour always. For example if you are
     * caching an iterator as in and you know that in next rounds you will
     * eventually visit all entries, you don't need fail fast behaviour.
     *
     * @return new read only iterator instance
     */
    Iterator<Map.Entry<Data, R>> mutationTolerantIterator();

    int size();

    boolean isEmpty();

    /**
     * @param isDuringShutdown only used by hot-restart.
     */
    void clear(boolean isDuringShutdown);

    void destroy(boolean isDuringShutdown);

    EntryCostEstimator getEntryCostEstimator();

    void setEntryCostEstimator(EntryCostEstimator entryCostEstimator);

    default void disposeDeferredBlocks() {
        // NOP intentionally.
    }

    /**
     * Used for sampling based eviction, returns sampled entries.
     *
     * @param sampleCount sample count.
     * @return sampled entries.
     */
    Iterable<EntryView> getRandomSamples(int sampleCount);

    /**
     * Fetch minimally {@code size} keys from the {@code pointers} position.
     * The key is fetched on-heap.
     * The method may return less keys if iteration has completed.
     * <p>
     * NOTE: The implementation is free to return more than {@code size} items.
     * This can happen if we cannot easily resume from the last returned item
     * by receiving the {@code pointers} of the last item. The index can
     * represent a bucket with multiple items and in this case the returned
     * object will contain all items in that bucket, regardless if we exceed
     * the requested {@code size}.
     *
     * @param pointers the pointers defining the state of iteration
     * @param size     the minimal count of returned items, unless iteration has completed
     * @return fetched keys and the new iteration state
     */
    MapKeysWithCursor fetchKeys(IterationPointer[] pointers, int size);

    /**
     * Fetch minimally {@code size} items from the {@code pointers} position.
     * Both the key and value are fetched on-heap.
     * <p>
     * NOTE: The implementation is free to return more than {@code size} items.
     * This can happen if we cannot easily resume from the last returned item
     * by receiving the {@code pointers} of the last item. The index can
     * represent a bucket with multiple items and in this case the returned
     * object will contain all items in that bucket, regardless if we exceed
     * the requested {@code size}.
     *
     * @param pointers the pointers defining the state of iteration
     * @param size     the minimal count of returned items
     * @return fetched entries and the new iteration state
     */
    MapEntriesWithCursor fetchEntries(IterationPointer[] pointers, int size);

    Data extractDataKeyFromLazy(EntryView entryView);

    Data toBackingDataKeyFormat(Data key);

    default int beforeOperation() {
        // no-op
        return -1;
    }

    default void afterOperation(int threadIndex) {
        // no-op
    }
}
