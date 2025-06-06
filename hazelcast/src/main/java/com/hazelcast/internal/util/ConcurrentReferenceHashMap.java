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

package com.hazelcast.internal.util;

/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

import com.hazelcast.internal.serialization.SerializableByConvention;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.hazelcast.internal.util.Preconditions.checkNotNull;

/**
 * An advanced hash table supporting configurable garbage collection semantics
 * of keys and values, optional referential-equality, full concurrency of
 * retrievals, and adjustable expected concurrency for updates.
 * <p>
 * This table is designed around specific advanced use-cases. If there is any
 * doubt whether this table is for you, you most likely should be using
 * {@link java.util.concurrent.ConcurrentHashMap} instead.
 * <p>
 * This table supports strong, weak, and soft keys and values. By default keys
 * are weak, and values are strong. Such a configuration offers similar behavior
 * to {@link java.util.WeakHashMap}, entries of this table are periodically
 * removed once their corresponding keys are no longer referenced outside of
 * this table. In other words, this table will not prevent a key from being
 * discarded by the garbage collector. Once a key has been discarded by the
 * collector, the corresponding entry is no longer visible to this table;
 * however, the entry may occupy space until a future table operation decides to
 * reclaim it. For this reason, summary functions such as <tt>size</tt> and
 * <tt>isEmpty</tt> might return a value greater than the observed number of
 * entries. In order to support a high level of concurrency, stale entries are
 * only reclaimed during blocking (usually mutating) operations.
 * <p>
 * Enabling soft keys allows entries in this table to remain until their space
 * is absolutely needed by the garbage collector. This is unlike weak keys which
 * can be reclaimed as soon as they are no longer referenced by a normal strong
 * reference. The primary use case for soft keys is a cache, which ideally
 * occupies memory that is not in use for as long as possible.
 * <p>
 * By default, values are held using a normal strong reference. This provides
 * the commonly desired guarantee that a value will always have at least the
 * same life-span as it's key. For this reason, care should be taken to ensure
 * that a value never refers, either directly or indirectly, to its key, thereby
 * preventing reclamation. If this is unavoidable, then it is recommended to use
 * the same reference type in use for the key. However, it should be noted that
 * non-strong values may disappear before their corresponding key.
 * <p>
 * While this table does allow the use of both strong keys and values, it is
 * recommended you use {@link java.util.concurrent.ConcurrentHashMap} for such a
 * configuration, since it is optimized for that case.
 * <p>
 * Just like {@link java.util.concurrent.ConcurrentHashMap}, this class obeys
 * the same functional specification as {@link java.util.Hashtable}, and
 * includes versions of methods corresponding to each method of
 * <tt>Hashtable</tt>. However, even though all operations are thread-safe,
 * retrieval operations do <em>not</em> entail locking, and there is
 * <em>not</em> any support for locking the entire table in a way that
 * prevents all access. This class is fully interoperable with
 * <tt>Hashtable</tt> in programs that rely on its thread safety but not on
 * its synchronization details.
 * <p>
 * <p>
 * Retrieval operations (including <tt>get</tt>) generally do not block, so they
 * may overlap with update operations (including <tt>put</tt> and
 * <tt>remove</tt>). Retrievals reflect the results of the most recently
 * <em>completed</em> update operations holding upon their onset. For
 * aggregate operations such as <tt>putAll</tt> and <tt>clear</tt>,
 * concurrent retrievals may reflect insertion or removal of only some entries.
 * Similarly, Iterators and Enumerations return elements reflecting the state of
 * the hash table at some point at or since the creation of the
 * iterator/enumeration. They do <em>not</em> throw
 * {@link ConcurrentModificationException}. However, iterators are designed to
 * be used by only one thread at a time.
 * <p>
 * <p>
 * The allowed concurrency among update operations is guided by the optional
 * <tt>concurrencyLevel</tt> constructor argument (default <tt>16</tt>),
 * which is used as a hint for internal sizing. The table is internally
 * partitioned to try to permit the indicated number of concurrent updates
 * without contention. Because placement in hash tables is essentially random,
 * the actual concurrency will vary. Ideally, you should choose a value to
 * accommodate as many threads as will ever concurrently modify the table. Using
 * a significantly higher value than you need can waste space and time, and a
 * significantly lower value can lead to thread contention. But overestimates
 * and underestimates within an order of magnitude do not usually have much
 * noticeable impact. A value of one is appropriate when it is known that only
 * one thread will modify and all others will only read. Also, resizing this or
 * any other kind of hash table is a relatively slow operation, so, when
 * possible, it is a good idea that you provide estimates of expected table sizes in
 * constructors.
 * <p>
 * <p>
 * This class and its views and iterators implement all of the <em>optional</em>
 * methods of the {@link Map} and {@link Iterator} interfaces.
 * <p>
 * <p>
 * Like {@link Hashtable} but unlike {@link HashMap}, this class does
 * <em>not</em> allow <tt>null</tt> to be used as a key or value.
 * <p>
 * <p>
 * This class is a member of the <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 * @author Doug Lea
 * @author Jason T. Greene
 */
@SuppressWarnings("all")
@SerializableByConvention
public class ConcurrentReferenceHashMap<K, V> extends AbstractMap<K, V>
        implements com.hazelcast.internal.util.IConcurrentMap<K, V>, Serializable {

    /*
     * The basic strategy is to subdivide the table among Segments,
     * each of which itself is a concurrently readable hash table.
     */

    /**
     * An option specifying which Java reference type should be used to refer
     * to a key and/or value.
     */
    public static enum ReferenceType {
        /**
         * Indicates a normal Java strong reference should be used
         */
        STRONG,
        /**
         * Indicates a {@link WeakReference} should be used
         */
        WEAK,
        /**
         * Indicates a {@link SoftReference} should be used
         */
        SOFT
    }

    ;

    /**
     * Behavior-changing configuration options for the map
     */
    public static enum Option {
        /**
         * Indicates that referential-equality (== instead of .equals()) should
         * be used when locating keys. This offers similar behavior to {@link IdentityHashMap}
         */
        IDENTITY_COMPARISONS
    }

    ;

    /* ---------------- Constants -------------- */

    static final ReferenceType DEFAULT_KEY_TYPE = ReferenceType.WEAK;

    static final ReferenceType DEFAULT_VALUE_TYPE = ReferenceType.STRONG;


    /**
     * The default initial capacity for this table,
     * used when not otherwise specified in a constructor.
     */
    static final int DEFAULT_INITIAL_CAPACITY = 16;

    /**
     * The default load factor for this table, used when not
     * otherwise specified in a constructor.
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * The default concurrency level for this table, used when not
     * otherwise specified in a constructor.
     */
    static final int DEFAULT_CONCURRENCY_LEVEL = 16;

    /**
     * The maximum capacity, used if a higher value is implicitly
     * specified by either of the constructors with arguments.  MUST
     * be a power of two &lt;= 1&lt;&lt;30 to ensure that entries are indexable
     * using ints.
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * The maximum number of segments to allow; used to bound
     * constructor arguments.
     */
    static final int MAX_SEGMENTS = 1 << 16;

    /**
     * Number of unsynchronized retries in size and containsValue
     * methods before resorting to locking. This is used to avoid
     * unbounded retries if tables undergo continuous modification
     * which would make it impossible to obtain an accurate result.
     */
    static final int RETRIES_BEFORE_LOCK = 2;

    @Serial
    private static final long serialVersionUID = 7249069246763182397L;

    /* ---------------- Fields -------------- */

    /**
     * Mask value for indexing into segments. The upper bits of a
     * key's hash code are used to choose the segment.
     */
    final int segmentMask;

    /**
     * Shift value for indexing within segments.
     */
    final int segmentShift;

    /**
     * The segments, each of which is a specialized hash table
     */
    final Segment<K, V>[] segments;

    boolean identityComparisons;

    transient Set<K> keySet;
    transient Set<Map.Entry<K, V>> entrySet;
    transient Collection<V> values;

    /* ---------------- Small Utilities -------------- */

    /**
     * Applies a supplemental hash function to a given hashCode, which
     * defends against poor quality hash functions.  This is critical
     * because ConcurrentReferenceHashMap uses power-of-two length hash tables,
     * that otherwise encounter collisions for hashCodes that do not
     * differ in lower or upper bits.
     */
    private static int hash(int h) {
        // Spread bits to regularize both segment and index locations,
        // using variant of single-word Wang/Jenkins hash.
        h += (h << 15) ^ 0xffffcd7d;
        h ^= (h >>> 10);
        h += (h << 3);
        h ^= (h >>> 6);
        h += (h << 2) + (h << 14);
        return h ^ (h >>> 16);
    }

    /**
     * Returns the segment that should be used for key with given hash
     *
     * @param hash the hash code for the key
     * @return the segment
     */
    final Segment<K, V> segmentFor(int hash) {
        return segments[(hash >>> segmentShift) & segmentMask];
    }

    protected int hashOf(Object key) {
        return hash(identityComparisons ? System.identityHashCode(key) : key.hashCode());
    }

    /* ---------------- Inner Classes -------------- */

    interface KeyReference {
        int keyHash();

        Object keyRef();
    }

    /**
     * A weak-key reference which stores the key hash needed for reclamation.
     */
    static final class WeakKeyReference<K> extends WeakReference<K> implements KeyReference {
        final int hash;

        WeakKeyReference(K key, int hash, ReferenceQueue<Object> refQueue) {
            super(key, refQueue);
            this.hash = hash;
        }

        @Override
        public final int keyHash() {
            return hash;
        }

        @Override
        public final Object keyRef() {
            return this;
        }
    }

    /**
     * A soft-key reference which stores the key hash needed for reclamation.
     */
    static final class SoftKeyReference<K> extends SoftReference<K> implements KeyReference {
        final int hash;

        SoftKeyReference(K key, int hash, ReferenceQueue<Object> refQueue) {
            super(key, refQueue);
            this.hash = hash;
        }

        @Override
        public final int keyHash() {
            return hash;
        }

        @Override
        public final Object keyRef() {
            return this;
        }
    }

    static final class WeakValueReference<V> extends WeakReference<V> implements KeyReference {
        final Object keyRef;
        final int hash;

        WeakValueReference(V value, Object keyRef, int hash, ReferenceQueue<Object> refQueue) {
            super(value, refQueue);
            this.keyRef = keyRef;
            this.hash = hash;
        }

        @Override
        public final int keyHash() {
            return hash;
        }

        @Override
        public final Object keyRef() {
            return keyRef;
        }
    }

    static final class SoftValueReference<V> extends SoftReference<V> implements KeyReference {
        final Object keyRef;
        final int hash;

        SoftValueReference(V value, Object keyRef, int hash, ReferenceQueue<Object> refQueue) {
            super(value, refQueue);
            this.keyRef = keyRef;
            this.hash = hash;
        }

        @Override
        public final int keyHash() {
            return hash;
        }

        @Override
        public final Object keyRef() {
            return keyRef;
        }
    }

    /**
     * ConcurrentReferenceHashMap list entry. Note that this is never exported
     * out as a user-visible Map.Entry.
     * <p>
     * Because the value field is volatile, not final, it is legal wrt
     * the Java Memory Model for an unsynchronized reader to see null
     * instead of initial value when read via a data race.  Although a
     * reordering leading to this is not likely to ever actually
     * occur, the Segment.readValueUnderLock method is used as a
     * backup in case a null (pre-initialized) value is ever seen in
     * an unsynchronized access method.
     */
    static final class HashEntry<K, V> {
        final Object keyRef;
        final int hash;
        volatile Object valueRef;
        final HashEntry<K, V> next;

        HashEntry(K key, int hash, HashEntry<K, V> next, V value,
                  ReferenceType keyType, ReferenceType valueType,
                  ReferenceQueue<Object> refQueue) {
            this.hash = hash;
            this.next = next;
            this.keyRef = newKeyReference(key, keyType, refQueue);
            this.valueRef = newValueReference(value, valueType, refQueue);
        }

        final Object newKeyReference(K key, ReferenceType keyType,
                                     ReferenceQueue<Object> refQueue) {
            if (keyType == ReferenceType.WEAK) {
                return new WeakKeyReference<K>(key, hash, refQueue);
            }
            if (keyType == ReferenceType.SOFT) {
                return new SoftKeyReference<K>(key, hash, refQueue);
            }

            return key;
        }

        final Object newValueReference(V value, ReferenceType valueType,
                                       ReferenceQueue<Object> refQueue) {
            if (valueType == ReferenceType.WEAK) {
                return new WeakValueReference<V>(value, keyRef, hash, refQueue);
            }
            if (valueType == ReferenceType.SOFT) {
                return new SoftValueReference<V>(value, keyRef, hash, refQueue);
            }

            return value;
        }

        @SuppressWarnings("unchecked")
        final K key() {
            if (keyRef instanceof KeyReference) {
                return ((Reference<K>) keyRef).get();
            }
            return (K) keyRef;
        }

        final V value() {
            return dereferenceValue(valueRef);
        }

        @SuppressWarnings("unchecked")
        final V dereferenceValue(Object value) {
            if (value instanceof KeyReference) {
                return ((Reference<V>) value).get();
            }
            return (V) value;
        }

        final void setValue(V value, ReferenceType valueType, ReferenceQueue<Object> refQueue) {
            this.valueRef = newValueReference(value, valueType, refQueue);
        }

        @SuppressWarnings("unchecked")
        static final <K, V> HashEntry<K, V>[] newArray(int i) {
            return new HashEntry[i];
        }
    }

    /**
     * Segments are specialized versions of hash tables.  This
     * subclasses from ReentrantLock opportunistically, just to
     * simplify some locking and avoid separate construction.
     */
    @SerializableByConvention
    static final class Segment<K, V> extends ReentrantLock implements Serializable {
        /*
         * Segments maintain a table of entry lists that are ALWAYS
         * kept in a consistent state, so they can be read without locking.
         * Next fields of nodes are immutable (final).  All list
         * additions are performed at the front of each bin. This
         * makes it easy to check changes, and also fast to traverse.
         * When nodes would otherwise be changed, new nodes are
         * created to replace them. This works well for hash tables
         * since the bin lists tend to be short. (The average length
         * is less than two for the default load factor threshold.)
         *
         * Read operations can thus proceed without locking, but rely
         * on selected uses of volatiles to ensure that completed
         * write operations performed by other threads are
         * noticed. For most purposes, the "count" field, tracking the
         * number of elements, serves as that volatile variable
         * ensuring visibility.  This is convenient because this field
         * needs to be read in many read operations anyway:
         *
         *   - All (unsynchronized) read operations must first read the
         *     "count" field, and should not look at table entries if
         *     it is 0.
         *
         *   - All (synchronized) write operations should write to
         *     the "count" field after structurally changing any bin.
         *     The operations must not take any action that could even
         *     momentarily cause a concurrent read operation to see
         *     inconsistent data. This is made easier by the nature of
         *     the read operations in Map. For example, no operation
         *     can reveal that the table has grown but the threshold
         *     has not yet been updated, so there are no atomicity
         *     requirements for this with respect to reads.
         *
         * As a guide, all critical volatile reads and writes to the
         * count field are marked in code comments.
         */

        @Serial
        private static final long serialVersionUID = 2249069246763182397L;

        /**
         * The number of elements in this segment's region.
         */
        @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification =
                "I trust Doug Lea's technical decision")
        transient volatile int count;

        /**
         * Number of updates that alter the size of the table. This is
         * used during bulk-read methods to make sure they see a
         * consistent snapshot: If modCounts change during a traversal
         * of segments computing size or checking containsValue, then
         * we might have an inconsistent view of state so (usually) we
         * must retry.
         */
        @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification =
                "I trust Doug Lea's technical decision")
        transient int modCount;

        /**
         * The table is rehashed when its size exceeds this threshold.
         * (The value of this field is always <tt>(int)(capacity *
         * loadFactor)</tt>.)
         */
        transient int threshold;

        /**
         * The per-segment table.
         */
        transient volatile HashEntry<K, V>[] table;

        /**
         * The load factor for the hash table.  Even though this value
         * is same for all segments, it is replicated to avoid needing
         * links to outer object.
         *
         * @serial
         */
        final float loadFactor;

        /**
         * The collected weak-key reference queue for this segment.
         * This should be (re)initialized whenever table is assigned,
         */
        transient volatile ReferenceQueue<Object> refQueue;

        final ReferenceType keyType;

        final ReferenceType valueType;

        final boolean identityComparisons;

        Segment(int initialCapacity, float lf, ReferenceType keyType,
                ReferenceType valueType, boolean identityComparisons) {
            loadFactor = lf;
            this.keyType = keyType;
            this.valueType = valueType;
            this.identityComparisons = identityComparisons;
            setTable(HashEntry.<K, V>newArray(initialCapacity));
        }

        @SuppressWarnings("unchecked")
        static final <K, V> Segment<K, V>[] newArray(int i) {
            return new Segment[i];
        }

        private boolean keyEq(Object src, Object dest) {
            return identityComparisons ? src == dest : src.equals(dest);
        }

        /**
         * Sets table to new HashEntry array.
         * Call only while holding lock or in constructor.
         */
        void setTable(HashEntry<K, V>[] newTable) {
            threshold = (int) (newTable.length * loadFactor);
            table = newTable;
            refQueue = new ReferenceQueue<>();
        }

        /**
         * Returns properly casted first entry of bin for given hash.
         */
        HashEntry<K, V> getFirst(int hash) {
            HashEntry<K, V>[] tab = table;
            return tab[hash & (tab.length - 1)];
        }

        HashEntry<K, V> newHashEntry(K key, int hash, HashEntry<K, V> next, V value) {
            return new HashEntry<>(key, hash, next, value, keyType, valueType, refQueue);
        }

        /**
         * Reads value field of an entry under lock. Called if value
         * field ever appears to be null. This is possible only if a
         * compiler happens to reorder a HashEntry initialization with
         * its table assignment, which is legal under memory model
         * but is not known to ever occur.
         */
        V readValueUnderLock(HashEntry<K, V> e) {
            lock();
            try {
                removeStale();
                return e.value();
            } finally {
                unlock();
            }
        }

        /* Specialized implementations of map methods */

        V get(Object key, int hash) {
            // read-volatile
            if (count != 0) {
                HashEntry<K, V> e = getFirst(hash);
                while (e != null) {
                    if (e.hash == hash && keyEq(key, e.key())) {
                        Object opaque = e.valueRef;
                        if (opaque != null) {
                            return e.dereferenceValue(opaque);
                        }
                        // recheck
                        return readValueUnderLock(e);
                    }
                    e = e.next;
                }
            }
            return null;
        }

        boolean containsKey(Object key, int hash) {
            // read-volatile
            if (count != 0) {
                HashEntry<K, V> e = getFirst(hash);
                while (e != null) {
                    if (e.hash == hash && keyEq(key, e.key())) {
                        return true;
                    }
                    e = e.next;
                }
            }
            return false;
        }

        boolean containsValue(Object value) {
            // read-volatile
            if (count != 0) {
                HashEntry<K, V>[] tab = table;
                int len = tab.length;
                for (int i = 0; i < len; i++) {
                    for (HashEntry<K, V> e = tab[i]; e != null; e = e.next) {
                        Object opaque = e.valueRef;
                        V v;
                        if (opaque == null) {
                            // recheck
                            v = readValueUnderLock(e);
                        } else {
                            v = e.dereferenceValue(opaque);
                        }
                        if (value.equals(v)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        boolean replace(K key, int hash, V oldValue, V newValue) {
            lock();
            try {
                return replaceInternal2(key, hash, oldValue, newValue);
            } finally {
                unlock();
            }
        }

        private boolean replaceInternal2(K key, int hash, V oldValue, V newValue) {
            removeStale();
            HashEntry<K, V> e = getFirst(hash);
            while (e != null && (e.hash != hash || !keyEq(key, e.key()))) {
                e = e.next;
            }
            boolean replaced = false;
            if (e != null && oldValue.equals(e.value())) {
                replaced = true;
                e.setValue(newValue, valueType, refQueue);
            }
            return replaced;
        }

        V replace(K key, int hash, V newValue) {
            lock();
            try {
                return replaceInternal(key, hash, newValue);
            } finally {
                unlock();
            }
        }

        private V replaceInternal(K key, int hash, V newValue) {
            removeStale();
            HashEntry<K, V> e = getFirst(hash);
            while (e != null && (e.hash != hash || !keyEq(key, e.key()))) {
                e = e.next;
            }
            V oldValue = null;
            if (e != null) {
                oldValue = e.value();
                e.setValue(newValue, valueType, refQueue);
            }
            return oldValue;
        }

        V applyIfPresent(K key, int hash, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
            lock();
            try {
                V oldValue = get(key, hash);
                if (oldValue == null) {
                    return null;
                }

                V newValue = remappingFunction.apply(key, oldValue);

                if (newValue == null) {
                    removeInternal(key, hash, oldValue, false);
                    return null;
                } else {
                    putInternal(key, hash, newValue, null, false);
                    return newValue;
                }
            } finally {
                unlock();
            }
        }

        V apply(K key, int hash, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
            lock();
            try {
                V oldValue = get(key, hash);
                V newValue = remappingFunction.apply(key, oldValue);

                if (newValue == null) {
                    // delete mapping
                    if (oldValue != null) {
                        // something to remove
                        removeInternal(key, hash, oldValue, false);
                        return null;
                    } else {
                        // nothing to do. Leave things as they were.
                        return null;
                    }
                } else {
                    // add or replace old mapping
                    putInternal(key, hash, newValue, null, false);
                    return newValue;
                }
            } finally {
                unlock();
            }
        }


        V merge(K key, V value, int hash, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
            lock();
            try {
                V oldValue = get(key, hash);
                V newValue = (oldValue == null) ? value : remappingFunction.apply(oldValue, value);

                if (newValue == null) {
                    removeInternal(key, hash, oldValue, false);
                    return null;
                } else {
                    putInternal(key, hash, newValue, null, false);
                    return newValue;
                }
            } finally {
                unlock();
            }
        }

        /**
         * This method must be called with exactly one of <code>value</code> and
         * <code>function</code> non-null.
         **/
        V put(K key, int hash, V value, Function<? super K, ? extends V> function, boolean onlyIfAbsent) {
            lock();
            try {
                return putInternal(key, hash, value, function, onlyIfAbsent);
            } finally {
                unlock();
            }
        }

        private V putInternal(K key, int hash, V value, Function<? super K, ? extends V> function, boolean onlyIfAbsent) {
            removeStale();
            int c = count;
            // ensure capacity
            if (c++ > threshold) {
                int reduced = rehash();
                // adjust from possible weak cleanups
                if (reduced > 0) {
                    // write-volatile
                    count = (c -= reduced) - 1;
                }
            }
            HashEntry<K, V>[] tab = table;
            int index = hash & (tab.length - 1);
            HashEntry<K, V> first = tab[index];
            HashEntry<K, V> e = first;
            while (e != null && (e.hash != hash || !keyEq(key, e.key()))) {
                e = e.next;
            }
            V resultValue;
            if (e != null) {
                resultValue = e.value();
                if (!onlyIfAbsent) {
                    e.setValue(getValue(key, value, function), valueType, refQueue);
                }
            } else {
                V v = getValue(key, value, function);
                resultValue = function != null ? v : null;

                if (v != null) {
                    ++modCount;
                    tab[index] = newHashEntry(key, hash, first, v);
                    // write-volatile
                    count = c;
                }
            }
            return resultValue;
        }

        V getValue(K key, V value, Function<? super K, ? extends V> function) {
            return value != null ? value : function.apply(key);
        }

        int rehash() {
            HashEntry<K, V>[] oldTable = table;
            int oldCapacity = oldTable.length;
            if (oldCapacity >= MAXIMUM_CAPACITY) {
                return 0;
            }

            /*
             * Reclassify nodes in each list to new Map.  Because we are
             * using power-of-two expansion, the elements from each bin
             * must either stay at the same index, or move with a power of two
             * offset. We eliminate unnecessary node creation by catching
             * cases where old nodes can be reused because their next
             * fields won't change. Statistically, at the default
             * threshold, only about one-sixth of them need cloning when
             * a table doubles. The nodes they replace will be garbage
             * collectable as soon as they are no longer referenced by any
             * reader thread that may be in the midst of traversing table
             * right now.
             */

            HashEntry<K, V>[] newTable = HashEntry.newArray(oldCapacity << 1);
            threshold = (int) (newTable.length * loadFactor);
            int sizeMask = newTable.length - 1;
            int reduce = 0;
            for (int i = 0; i < oldCapacity; i++) {
                // We need to guarantee that any existing reads of old Map can
                //  proceed. So we cannot yet null out each bin.
                HashEntry<K, V> e = oldTable[i];
                if (e != null) {
                    HashEntry<K, V> next = e.next;
                    int idx = e.hash & sizeMask;
                    //  Single node on list
                    if (next == null) {
                        newTable[idx] = e;
                    } else {
                        // Reuse trailing consecutive sequence at same slot
                        HashEntry<K, V> lastRun = e;
                        int lastIdx = idx;
                        for (HashEntry<K, V> last = next;
                             last != null;
                             last = last.next) {
                            int k = last.hash & sizeMask;
                            if (k != lastIdx) {
                                lastIdx = k;
                                lastRun = last;
                            }
                        }
                        newTable[lastIdx] = lastRun;
                        // Clone all remaining nodes
                        for (HashEntry<K, V> p = e; p != lastRun; p = p.next) {
                            // Skip GC'd weak refs
                            K key = p.key();
                            if (key == null) {
                                reduce++;
                                continue;
                            }
                            int k = p.hash & sizeMask;
                            HashEntry<K, V> n = newTable[k];
                            newTable[k] = newHashEntry(key, p.hash, n, p.value());
                        }
                    }
                }
            }
            table = newTable;
            return reduce;
        }

        /**
         * Remove: match on key only if value is null, else match both.
         */
        V remove(Object key, int hash, Object value, boolean refRemove) {
            lock();
            try {
                return removeInternal(key, hash, value, refRemove);
            } finally {
                unlock();
            }
        }

        private V removeInternal(Object key, int hash, Object value, boolean refRemove) {
            if (!refRemove) {
                removeStale();
            }
            int c = count - 1;
            HashEntry<K, V>[] tab = table;
            int index = hash & (tab.length - 1);
            HashEntry<K, V> first = tab[index];
            HashEntry<K, V> e = first;
            // a ref remove operation compares the Reference instance
            while (e != null && key != e.keyRef && (refRemove || hash != e.hash || !keyEq(key, e.key()))) {
                e = e.next;
            }

            V oldValue = null;
            if (e != null) {
                V v = e.value();
                if (value == null || value.equals(v)) {
                    oldValue = v;
                    // All entries following removed node can stay
                    // in list, but all preceding ones need to be
                    // cloned.
                    ++modCount;
                    HashEntry<K, V> newFirst = e.next;
                    for (HashEntry<K, V> p = first; p != e; p = p.next) {
                        K pKey = p.key();
                        // Skip GC'd keys
                        if (pKey == null) {
                            c--;
                            continue;
                        }
                        newFirst = newHashEntry(pKey, p.hash, newFirst, p.value());
                    }
                    tab[index] = newFirst;
                    // write-volatile
                    count = c;
                }
            }
            return oldValue;
        }

        final void removeStale() {
            KeyReference ref;
            while ((ref = (KeyReference) refQueue.poll()) != null) {
                remove(ref.keyRef(), ref.keyHash(), null, true);
            }
        }

        void clear() {
            if (count != 0) {
                lock();
                try {
                    HashEntry<K, V>[] tab = table;
                    for (int i = 0; i < tab.length; i++) {
                        tab[i] = null;
                    }
                    ++modCount;
                    // replace the reference queue to avoid unnecessary stale cleanups
                    refQueue = new ReferenceQueue<>();
                    // write-volatile
                    count = 0;
                } finally {
                    unlock();
                }
            }
        }
    }

    /* ---------------- Public operations -------------- */

    /**
     * Creates a new, empty map with the specified initial
     * capacity, reference types, load factor, and concurrency level.
     * <p>
     * Behavioral changing options such as {@link Option#IDENTITY_COMPARISONS}
     * can also be specified.
     *
     * @param initialCapacity  the initial capacity. The implementation
     *                         performs internal sizing to accommodate this many elements.
     * @param loadFactor       the load factor threshold, used to control resizing.
     *                         Resizing may be performed when the average number of elements per
     *                         bin exceeds this threshold.
     * @param concurrencyLevel the estimated number of concurrently
     *                         updating threads. The implementation performs internal sizing
     *                         to try to accommodate this many threads.
     * @param keyType          the reference type to use for keys
     * @param valueType        the reference type to use for values
     * @param options          the behavioral options
     * @throws IllegalArgumentException if the initial capacity is
     *                                  negative or the load factor or concurrencyLevel are
     *                                  nonpositive.
     */
    public ConcurrentReferenceHashMap(int initialCapacity,
                                      float loadFactor, int concurrencyLevel,
                                      ReferenceType keyType, ReferenceType valueType,
                                      EnumSet<Option> options) {
        if (!(loadFactor > 0) || initialCapacity < 0 || concurrencyLevel <= 0) {
            throw new IllegalArgumentException();
        }
        if (concurrencyLevel > MAX_SEGMENTS) {
            concurrencyLevel = MAX_SEGMENTS;
        }
        // Find power-of-two sizes best matching arguments
        int sshift = 0;
        int ssize = 1;
        while (ssize < concurrencyLevel) {
            ++sshift;
            ssize <<= 1;
        }
        segmentShift = 32 - sshift;
        segmentMask = ssize - 1;
        this.segments = Segment.newArray(ssize);
        if (initialCapacity > MAXIMUM_CAPACITY) {
            initialCapacity = MAXIMUM_CAPACITY;
        }
        int c = initialCapacity / ssize;
        if (c * ssize < initialCapacity) {
            ++c;
        }
        int cap = 1;
        while (cap < c) {
            cap <<= 1;
        }
        identityComparisons = options != null && options.contains(Option.IDENTITY_COMPARISONS);
        for (int i = 0; i < this.segments.length; ++i) {
            this.segments[i] = new Segment<>(cap, loadFactor, keyType, valueType, identityComparisons);
        }
    }

    /**
     * Creates a new, empty map with the specified initial
     * capacity, load factor, and concurrency level.
     *
     * @param initialCapacity  the initial capacity. The implementation
     *                         performs internal sizing to accommodate this number of elements.
     * @param loadFactor       the load factor threshold, used to control resizing.
     *                         Resizing may be performed when the average number of elements per
     *                         bin exceeds this threshold.
     * @param concurrencyLevel the estimated number of concurrently
     *                         updating threads. The implementation performs internal sizing
     *                         to try to accommodate this many threads.
     * @throws IllegalArgumentException if the initial capacity is
     *                                  negative or the load factor or concurrencyLevel are
     *                                  nonpositive.
     */
    public ConcurrentReferenceHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
        this(initialCapacity, loadFactor, concurrencyLevel, DEFAULT_KEY_TYPE, DEFAULT_VALUE_TYPE, null);
    }

    /**
     * Creates a new, empty map with the specified initial capacity
     * and load factor and with the default reference types (weak keys,
     * strong values), and concurrencyLevel (16).
     *
     * @param initialCapacity The implementation performs internal
     *                        sizing to accommodate this number of elements.
     * @param loadFactor      the load factor threshold, used to control resizing.
     *                        Resizing may be performed when the average number of elements per
     *                        bin exceeds this threshold.
     * @throws IllegalArgumentException if the initial capacity of
     *                                  elements is negative or the load factor is nonpositive
     * @since 1.6
     */
    public ConcurrentReferenceHashMap(int initialCapacity, float loadFactor) {
        this(initialCapacity, loadFactor, DEFAULT_CONCURRENCY_LEVEL);
    }


    /**
     * Creates a new, empty map with the specified initial capacity,
     * reference types, and with a default load factor (0.75) and concurrencyLevel (16).
     *
     * @param initialCapacity the initial capacity. The implementation
     *                        performs internal sizing to accommodate this many elements.
     * @param keyType         the reference type to use for keys
     * @param valueType       the reference type to use for values
     * @throws IllegalArgumentException if the initial capacity of
     *                                  elements is negative.
     */
    public ConcurrentReferenceHashMap(int initialCapacity,
                                      ReferenceType keyType, ReferenceType valueType) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL,
                keyType, valueType, null);
    }

    /**
     * Creates a new, empty reference map with the specified key
     * and value reference types.
     *
     * @param keyType   the reference type to use for keys
     * @param valueType the reference type to use for values
     * @throws IllegalArgumentException if the initial capacity of
     *                                  elements is negative.
     */
    public ConcurrentReferenceHashMap(ReferenceType keyType, ReferenceType valueType) {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL,
                keyType, valueType, null);
    }

    /**
     * Creates a new, empty reference map with the specified reference types
     * and behavioral options.
     *
     * @param keyType   the reference type to use for keys
     * @param valueType the reference type to use for values
     * @throws IllegalArgumentException if the initial capacity of
     *                                  elements is negative.
     */
    public ConcurrentReferenceHashMap(ReferenceType keyType, ReferenceType valueType, EnumSet<Option> options) {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL,
                keyType, valueType, options);
    }


    /**
     * Creates a new, empty map with the specified initial capacity,
     * and with default reference types (weak keys, strong values),
     * load factor (0.75) and concurrencyLevel (16).
     *
     * @param initialCapacity the initial capacity. The implementation
     *                        performs internal sizing to accommodate this many elements.
     * @throws IllegalArgumentException if the initial capacity of
     *                                  elements is negative.
     */
    public ConcurrentReferenceHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL);
    }

    /**
     * Creates a new, empty map with a default initial capacity (16),
     * reference types (weak keys, strong values), default
     * load factor (0.75) and concurrencyLevel (16).
     */
    public ConcurrentReferenceHashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL);
    }

    /**
     * Creates a new map with the same mappings as the given map.
     * The map is created with a capacity of 1.5 times the number
     * of mappings in the given map or 16 (whichever is greater),
     * and a default load factor (0.75) and concurrencyLevel (16).
     *
     * @param m the map
     */
    public ConcurrentReferenceHashMap(Map<? extends K, ? extends V> m) {
        this(Math.max((int) (m.size() / DEFAULT_LOAD_FACTOR) + 1,
                DEFAULT_INITIAL_CAPACITY),
                DEFAULT_LOAD_FACTOR, DEFAULT_CONCURRENCY_LEVEL
        );
        putAll(m);
    }

    /**
     * Returns <tt>true</tt> if this map contains no key-value mappings.
     *
     * @return <tt>true</tt> if this map contains no key-value mappings
     */
    @Override
    public boolean isEmpty() {
        final Segment<K, V>[] segments = this.segments;
        /*
         * We keep track of per-segment modCounts to avoid ABA
         * problems in which an element in one segment was added and
         * in another removed during traversal, in which case the
         * table was never actually empty at any point. Note the
         * similar use of modCounts in the size() and containsValue()
         * methods, which are the only other methods also susceptible
         * to ABA problems.
         */
        int[] mc = new int[segments.length];
        int mcsum = 0;
        for (int i = 0; i < segments.length; ++i) {
            if (segments[i].count != 0) {
                return false;
            } else {
                mcsum += mc[i] = segments[i].modCount;
            }
        }
        // If mcsum happens to be zero, then we know we got a snapshot
        // before any modifications at all were made.  This is
        // probably common enough to bother tracking.
        if (mcsum != 0) {
            for (int i = 0; i < segments.length; ++i) {
                if (segments[i].count != 0 || mc[i] != segments[i].modCount) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Returns the number of key-value mappings in this map.  If the
     * map contains more than <tt>Integer.MAX_VALUE</tt> elements, returns
     * <tt>Integer.MAX_VALUE</tt>.
     *
     * @return the number of key-value mappings in this map
     */
    @Override
    public int size() {
        final Segment<K, V>[] segments = this.segments;
        long sum = 0;
        long check = 0;
        int[] mc = new int[segments.length];
        // Try a few times to get accurate count. On failure due to
        // continuous async changes in table, resort to locking.
        for (int k = 0; k < RETRIES_BEFORE_LOCK; ++k) {
            check = 0;
            sum = 0;
            int mcsum = 0;
            for (int i = 0; i < segments.length; ++i) {
                sum += segments[i].count;
                mcsum += mc[i] = segments[i].modCount;
            }
            if (mcsum != 0) {
                for (int i = 0; i < segments.length; ++i) {
                    check += segments[i].count;
                    if (mc[i] != segments[i].modCount) {
                        // force retry
                        check = -1;
                        break;
                    }
                }
            }
            if (check == sum) {
                break;
            }
        }
        if (check != sum) {
            // Resort to locking all segments
            sum = 0;
            for (int i = 0; i < segments.length; ++i) {
                segments[i].lock();
            }
            for (int i = 0; i < segments.length; ++i) {
                sum += segments[i].count;
            }
            for (int i = 0; i < segments.length; ++i) {
                segments[i].unlock();
            }
        }
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     * <p>
     * <p>If this map contains a mapping from a key
     * {@code k} to a value {@code v} such that {@code key.equals(k)},
     * then this method returns {@code v}; otherwise it returns
     * {@code null}.  (There can be at most one such mapping.)
     *
     * @throws NullPointerException if the specified key is null
     */
    @Override
    public V get(Object key) {
        int hash = hashOf(key);
        return segmentFor(hash).get(key, hash);
    }

    /**
     * Tests if the specified object is a key in this table.
     *
     * @param key possible key
     * @return <tt>true</tt> if and only if the specified object
     * is a key in this table, as determined by the
     * <tt>equals</tt> method; <tt>false</tt> otherwise.
     * @throws NullPointerException if the specified key is null
     */
    @Override
    public boolean containsKey(Object key) {
        int hash = hashOf(key);
        return segmentFor(hash).containsKey(key, hash);
    }

    /**
     * Returns <tt>true</tt> if this map maps one or more keys to the
     * specified value. Note: This method requires a full internal
     * traversal of the hash table, therefore it is much slower than the
     * method <tt>containsKey</tt>.
     *
     * @param value value whose presence in this map is to be tested
     * @return <tt>true</tt> if this map maps one or more keys to the
     * specified value
     * @throws NullPointerException if the specified value is null
     */
    @Override
    public boolean containsValue(Object value) {
        if (value == null) {
            throw new NullPointerException();
        }
        // See explanation of modCount use above
        final Segment<K, V>[] segments = this.segments;
        int[] mc = new int[segments.length];
        // Try a few times without locking
        for (int k = 0; k < RETRIES_BEFORE_LOCK; ++k) {
            int sum = 0;
            int mcsum = 0;
            for (int i = 0; i < segments.length; ++i) {
                int c = segments[i].count;
                mcsum += mc[i] = segments[i].modCount;
                if (segments[i].containsValue(value)) {
                    return true;
                }
            }
            boolean cleanSweep = true;
            if (mcsum != 0) {
                for (int i = 0; i < segments.length; ++i) {
                    int c = segments[i].count;
                    if (mc[i] != segments[i].modCount) {
                        cleanSweep = false;
                        break;
                    }
                }
            }
            if (cleanSweep) {
                return false;
            }
        }
        // Resort to locking all segments
        for (int i = 0; i < segments.length; ++i) {
            segments[i].lock();
        }
        boolean found = false;
        try {
            for (int i = 0; i < segments.length; ++i) {
                if (segments[i].containsValue(value)) {
                    found = true;
                    break;
                }
            }
        } finally {
            for (int i = 0; i < segments.length; ++i) {
                segments[i].unlock();
            }
        }
        return found;
    }

    /**
     * Legacy method testing if some key maps into the specified value
     * in this table.  This method is identical in functionality to
     * {@link #containsValue}, and exists solely to ensure
     * full compatibility with class {@link java.util.Hashtable},
     * which supported this method prior to introduction of the
     * Java Collections framework.
     *
     * @param value a value to search for
     * @return <tt>true</tt> if and only if some key maps to the
     * <tt>value</tt> argument in this table as
     * determined by the <tt>equals</tt> method;
     * <tt>false</tt> otherwise
     * @throws NullPointerException if the specified value is null
     */
    public boolean contains(Object value) {
        return containsValue(value);
    }

    /**
     * Maps the specified key to the specified value in this table.
     * Neither the key nor the value can be null.
     * <p>
     * <p> The value can be retrieved by calling the <tt>get</tt> method
     * with a key that is equal to the original key.
     *
     * @param key   key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with <tt>key</tt>, or
     * <tt>null</tt> if there was no mapping for <tt>key</tt>
     * @throws NullPointerException if the specified key or value is null
     */
    @Override
    public V put(K key, V value) {
        if (value == null) {
            throw new NullPointerException();
        }
        int hash = hashOf(key);
        return segmentFor(hash).put(key, hash, value, null, false);
    }

    /**
     * {@inheritDoc}
     *
     * @return the previous value associated with the specified key,
     * or <tt>null</tt> if there was no mapping for the key
     * @throws NullPointerException if the specified key or value is null
     */
    @Override
    public V putIfAbsent(K key, V value) {
        if (value == null) {
            throw new NullPointerException();
        }
        int hash = hashOf(key);
        return segmentFor(hash).put(key, hash, value, null, true);
    }

    /***
     * {@inheritDoc}
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @implSpec The default implementation is equivalent to the following steps for this
     * {@code map}, then returning the current value or {@code null} if now
     * absent:
     * <p>
     * <pre> {@code
     * if (map.get(key) == null) {
     *     V newValue = mappingFunction.apply(key);
     *     if (newValue != null)
     *         return map.putIfAbsent(key, newValue);
     * }
     * }</pre>
     * <p>
     * The default implementation may retry these steps when multiple
     * threads attempt updates including potentially calling the mapping
     * function multiple times.
     * <p>
     * <p>This implementation assumes that the ConcurrentMap cannot contain null
     * values and {@code get()} returning null unambiguously means the key is
     * absent. Implementations which support null values <strong>must</strong>
     * override this default implementation.
     */
    @Override
    public V applyIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        checkNotNull(key);
        checkNotNull(mappingFunction);

        int hash = hashOf(key);
        Segment<K, V> segment = segmentFor(hash);
        V v = segment.get(key, hash);
        return v == null ? segment.put(key, hash, null, mappingFunction, true) : v;
    }

    @Override
    public V applyIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        checkNotNull(key);
        checkNotNull(remappingFunction);

        int hash = hashOf(key);
        Segment<K, V> segment = segmentFor(hash);
        V v = segment.get(key, hash);
        if (v == null) {
            return null;
        }

        return segmentFor(hash).applyIfPresent(key, hash, remappingFunction);
    }

    @Override
    public V apply(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        checkNotNull(key);
        checkNotNull(remappingFunction);

        int hash = hashOf(key);
        Segment<K, V> segment = segmentFor(hash);
        return segment.apply(key, hash, remappingFunction);
    }

    /**
     * Copies all of the mappings from the specified map to this one.
     * These mappings replace any mappings that this map had for any of the
     * keys currently in the specified map.
     *
     * @param m mappings to be stored in this map
     */
    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    /**
     * Removes the key (and its corresponding value) from this map.
     * This method does nothing if the key is not in the map.
     *
     * @param key the key that needs to be removed
     * @return the previous value associated with <tt>key</tt>, or
     * <tt>null</tt> if there was no mapping for <tt>key</tt>
     * @throws NullPointerException if the specified key is null
     */
    @Override
    public V remove(Object key) {
        int hash = hashOf(key);
        return segmentFor(hash).remove(key, hash, null, false);
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if the specified key is null
     */
    @Override
    public boolean remove(Object key, Object value) {
        int hash = hashOf(key);
        if (value == null) {
            return false;
        }
        return segmentFor(hash).remove(key, hash, value, false) != null;
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if any of the arguments are null
     */
    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        if (oldValue == null || newValue == null) {
            throw new NullPointerException();
        }
        int hash = hashOf(key);
        return segmentFor(hash).replace(key, hash, oldValue, newValue);
    }

    /**
     * {@inheritDoc}
     *
     * @return the previous value associated with the specified key,
     * or <tt>null</tt> if there was no mapping for the key
     * @throws NullPointerException if the specified key or value is null
     */
    @Override
    public V replace(K key, V value) {
        if (value == null) {
            throw new NullPointerException();
        }
        int hash = hashOf(key);
        return segmentFor(hash).replace(key, hash, value);
    }

    /**
     * Removes all of the mappings from this map.
     */
    @Override
    public void clear() {
        for (int i = 0; i < segments.length; ++i) {
            segments[i].clear();
        }
    }

    /**
     * Removes any stale entries whose keys have been finalized. Use of this
     * method is normally not necessary since stale entries are automatically
     * removed lazily, when blocking operations are required. However, there
     * are some cases where this operation should be performed eagerly, such
     * as cleaning up old references to a ClassLoader in a multi-classloader
     * environment.
     * <p>
     * Note: this method will acquire locks one at a time across all segments
     * of this table, so this method should be used sparingly.
     */
    public void purgeStaleEntries() {
        for (int i = 0; i < segments.length; ++i) {
            segments[i].removeStale();
        }
    }


    /**
     * Returns a {@link Set} view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  The set supports element
     * removal, which removes the corresponding mapping from this map,
     * via the <tt>Iterator.remove</tt>, <tt>Set.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt>
     * operations.  It does not support the <tt>add</tt> or
     * <tt>addAll</tt> operations.
     * <p>
     * <p>The view's <tt>iterator</tt> is a "weakly consistent" iterator
     * that will never throw {@link ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    @Override
    public Set<K> keySet() {
        Set<K> ks = keySet;
        return (ks != null) ? ks : (keySet = new KeySet());
    }

    /**
     * Returns a {@link Collection} view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.  The collection
     * supports element removal, which removes the corresponding
     * mapping from this map, via the <tt>Iterator.remove</tt>,
     * <tt>Collection.remove</tt>, <tt>removeAll</tt>,
     * <tt>retainAll</tt>, and <tt>clear</tt> operations.  It does not
     * support the <tt>add</tt> or <tt>addAll</tt> operations.
     * <p>
     * <p>The view's <tt>iterator</tt> is a "weakly consistent" iterator
     * that will never throw {@link ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    @Override
    public Collection<V> values() {
        Collection<V> vs = values;
        return (vs != null) ? vs : (values = new Values());
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  The set supports element
     * removal, which removes the corresponding mapping from the map,
     * via the <tt>Iterator.remove</tt>, <tt>Set.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt>
     * operations.  It does not support the <tt>add</tt> or
     * <tt>addAll</tt> operations.
     * <p>
     * <p>The view's <tt>iterator</tt> is a "weakly consistent" iterator
     * that will never throw {@link ConcurrentModificationException},
     * and is guaranteed to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to)
     * reflect any modifications subsequent to construction.
     */
    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        Set<Map.Entry<K, V>> es = entrySet;
        return (es != null) ? es : (entrySet = new EntrySet(false));
    }

    public Set<Map.Entry<K, V>> cachedEntrySet() {
        Set<Map.Entry<K, V>> es = entrySet;
        return (es != null) ? es : (entrySet = new EntrySet(true));
    }

    /**
     * Returns an enumeration of the keys in this table.
     *
     * @return an enumeration of the keys in this table
     * @see #keySet()
     */
    public Enumeration<K> keys() {
        return new KeyIterator();
    }

    /**
     * Returns an enumeration of the values in this table.
     *
     * @return an enumeration of the values in this table
     * @see #values()
     */
    public Enumeration<V> elements() {
        return new ValueIterator();
    }

    /* ---------------- Iterator Support -------------- */

    protected abstract class HashIterator {
        int nextSegmentIndex;
        int nextTableIndex;
        HashEntry<K, V>[] currentTable;
        HashEntry<K, V> nextEntry;
        HashEntry<K, V> lastReturned;
        // Strong reference to weak key (prevents gc)
        K currentKey;

        HashIterator() {
            nextSegmentIndex = segments.length - 1;
            nextTableIndex = -1;
            advance();
        }

        public boolean hasMoreElements() {
            return hasNext();
        }

        final void advance() {
            if (nextEntry != null && (nextEntry = nextEntry.next) != null) {
                return;
            }
            while (nextTableIndex >= 0) {
                if ((nextEntry = currentTable[nextTableIndex--]) != null) {
                    return;
                }
            }
            while (nextSegmentIndex >= 0) {
                Segment<K, V> seg = segments[nextSegmentIndex--];
                if (seg.count != 0) {
                    currentTable = seg.table;
                    for (int j = currentTable.length - 1; j >= 0; --j) {
                        if ((nextEntry = currentTable[j]) != null) {
                            nextTableIndex = j - 1;
                            return;
                        }
                    }
                }
            }
        }

        public boolean hasNext() {
            while (nextEntry != null) {
                if (nextEntry.key() != null) {
                    return true;
                }
                advance();
            }
            return false;
        }

        HashEntry<K, V> nextEntry() {
            do {
                if (nextEntry == null) {
                    throw new NoSuchElementException();
                }
                lastReturned = nextEntry;
                currentKey = lastReturned.key();
                advance();
            } while /* Skip GC'd keys */ (currentKey == null);
            return lastReturned;
        }

        public void remove() {
            if (lastReturned == null) {
                throw new IllegalStateException();
            }
            ConcurrentReferenceHashMap.this.remove(currentKey);
            lastReturned = null;
        }
    }

    final class KeyIterator extends HashIterator implements Iterator<K>, Enumeration<K> {
        @Override
        public K next() {
            return super.nextEntry().key();
        }

        @Override
        public K nextElement() {
            return super.nextEntry().key();
        }
    }

    final class ValueIterator extends HashIterator implements Iterator<V>, Enumeration<V> {
        @Override
        public V next() {
            return super.nextEntry().value();
        }

        @Override
        public V nextElement() {
            return super.nextEntry().value();
        }
    }

    /**
     * Custom Entry class used by EntryIterator.next(), that relays setValue
     * changes to the underlying map.
     */
    @SerializableByConvention
    protected class WriteThroughEntry extends SimpleEntry<K, V> {
        @Serial
        private static final long serialVersionUID = -7900634345345313646L;

        protected WriteThroughEntry(K k, V v) {
            super(k, v);
        }

        /**
         * Set our entry's value and writes it through to the map. The
         * value to return is somewhat arbitrary: since a
         * WriteThroughEntry does not necessarily track asynchronous
         * changes, the most recent "previous" value could be
         * different from what we return (or could even have been
         * removed in which case the put will re-establish). We do not
         * and cannot guarantee more.
         */
        @Override
        public V setValue(V value) {
            if (value == null) {
                throw new NullPointerException();
            }
            V v = super.setValue(value);
            ConcurrentReferenceHashMap.this.put(getKey(), value);
            return v;
        }
    }

    final class EntryIterator extends HashIterator implements Iterator<Entry<K, V>> {
        @Override
        public Map.Entry<K, V> next() {
            HashEntry<K, V> e = super.nextEntry();
            return new WriteThroughEntry(e.key(), e.value());
        }
    }

    final class CachedEntryIterator extends HashIterator implements Iterator<Entry<K, V>> {
        private InitializableEntry entry = new InitializableEntry();

        @Override
        public Map.Entry<K, V> next() {
            HashEntry<K, V> e = super.nextEntry();
            return entry.init(e.key(), e.value());
        }
    }

    protected static class InitializableEntry<K, V> implements Entry<K, V> {
        private K key;
        private V value;

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        public Entry<K, V> init(K key, V value) {
            this.key = key;
            this.value = value;
            return this;
        }

        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException();
        }
    }

    final class KeySet extends AbstractSet<K> {
        @Override
        public Iterator<K> iterator() {
            return new KeyIterator();
        }

        @Override
        public int size() {
            return ConcurrentReferenceHashMap.this.size();
        }

        @Override
        public boolean isEmpty() {
            return ConcurrentReferenceHashMap.this.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return ConcurrentReferenceHashMap.this.containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            return ConcurrentReferenceHashMap.this.remove(o) != null;
        }

        @Override
        public void clear() {
            ConcurrentReferenceHashMap.this.clear();
        }
    }

    final class Values extends AbstractCollection<V> {
        @Override
        public Iterator<V> iterator() {
            return new ValueIterator();
        }

        @Override
        public int size() {
            return ConcurrentReferenceHashMap.this.size();
        }

        @Override
        public boolean isEmpty() {
            return ConcurrentReferenceHashMap.this.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return ConcurrentReferenceHashMap.this.containsValue(o);
        }

        @Override
        public void clear() {
            ConcurrentReferenceHashMap.this.clear();
        }
    }

    final class EntrySet extends AbstractSet<Map.Entry<K, V>> {
        private final boolean cached;

        public EntrySet(boolean cached) {
            this.cached = cached;
        }

        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return cached ? new CachedEntryIterator() : new EntryIterator();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            V v = ConcurrentReferenceHashMap.this.get(e.getKey());
            return v != null && v.equals(e.getValue());
        }

        @Override
        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            return ConcurrentReferenceHashMap.this.remove(e.getKey(), e.getValue());
        }

        @Override
        public int size() {
            return ConcurrentReferenceHashMap.this.size();
        }

        @Override
        public boolean isEmpty() {
            return ConcurrentReferenceHashMap.this.isEmpty();
        }

        @Override
        public void clear() {
            ConcurrentReferenceHashMap.this.clear();
        }
    }

    /* ---------------- Serialization Support -------------- */

    /**
     * Save the state of the <tt>ConcurrentReferenceHashMap</tt> instance to a
     * stream (i.e., serialize it).
     *
     * @param s the stream
     * @serialData the key (Object) and value (Object)
     * for each key-value mapping, followed by a null pair.
     * The key-value mappings are emitted in no particular order.
     */
    private void writeObject(java.io.ObjectOutputStream s) throws IOException {
        s.defaultWriteObject();

        for (int k = 0; k < segments.length; ++k) {
            Segment<K, V> seg = segments[k];
            seg.lock();
            try {
                HashEntry<K, V>[] tab = seg.table;
                for (int i = 0; i < tab.length; ++i) {
                    for (HashEntry<K, V> e = tab[i]; e != null; e = e.next) {
                        K key = e.key();
                        // Skip GC'd keys
                        if (key == null) {
                            continue;
                        }
                        s.writeObject(key);
                        s.writeObject(e.value());
                    }
                }
            } finally {
                seg.unlock();
            }
        }
        s.writeObject(null);
        s.writeObject(null);
    }

    /**
     * Reconstitute the <tt>ConcurrentReferenceHashMap</tt> instance from a
     * stream (i.e., deserialize it).
     *
     * @param s the stream
     */
    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        // Initialize each segment to be minimally sized, and let grow.
        for (int i = 0; i < segments.length; ++i) {
            segments[i].setTable(new HashEntry[1]);
        }

        // Read the keys and values, and put the mappings in the table
        while (true) {
            K key = (K) s.readObject();
            V value = (V) s.readObject();
            if (key == null) {
                break;
            }
            put(key, value);
        }
    }
}
