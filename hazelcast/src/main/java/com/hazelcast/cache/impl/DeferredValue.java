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

package com.hazelcast.cache.impl;

import com.hazelcast.internal.serialization.Data;
import com.hazelcast.internal.serialization.SerializationService;

import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.hazelcast.internal.serialization.impl.SerializationUtil.isNullData;

/**
 * Thread-safe holder of value and/or its serialized form.
 *
 * @param <V> the type of value
 */
public class DeferredValue<V> {

    public static final DeferredValue NULL_VALUE = NullDeferredValue.NULL;

    private volatile Data serializedValue;
    private volatile V value;
    private volatile boolean valueExists;
    private volatile boolean serializedValueExists;

    private DeferredValue() {
    }

    // get-or-deserialize-and-get
    public V get(SerializationService serializationService) {
        if (!valueExists) {
            // it's ok to deserialize twice in case of race
            assert serializationService != null;
            value = serializationService.toObject(serializedValue);
            valueExists = true;
        }
        return value;
    }

    public Data getSerializedValue(SerializationService serializationService) {
        if (!serializedValueExists) {
            assert serializationService != null;
            serializedValue = serializationService.toData(value);
            serializedValueExists = true;
        }
        return serializedValue;
    }

    public DeferredValue<V> shallowCopy() {
        return shallowCopy(true, null);
    }

    /**
     * returns a new DeferredValue representing the same value as this,
     * possibly creating a serialized value
     *
     * @param resolved is false, force serialization of the returned copy
     * @param serializationService service to use to serialize
     * @return a shallow copy of DeferredValue
     */
    public DeferredValue<V> shallowCopy(boolean resolved, SerializationService serializationService) {
        DeferredValue<V> copy = new DeferredValue<>();
        if (serializedValueExists) {
            copy.serializedValueExists = true;
            copy.serializedValue = serializedValue;
        } else if (!resolved && serializationService != null) {
            copy.serializedValueExists = true;
            copy.serializedValue = getSerializedValue(serializationService);
        }
        if (valueExists) {
            copy.valueExists = true;
            copy.value = value;
        }
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DeferredValue<?> deferredValue = (DeferredValue<?>) o;

        if (valueExists && deferredValue.valueExists) {
            return value != null ? value.equals(deferredValue.value) : deferredValue.value == null;
        }
        if (serializedValueExists && deferredValue.serializedValueExists) {
            return serializedValue != null ? serializedValue.equals(deferredValue.serializedValue)
                    : deferredValue.serializedValue == null;
        }
        throw new IllegalArgumentException("Cannot compare serialized vs deserialized value");
    }

    @Override
    public int hashCode() {
        int result = serializedValue != null ? serializedValue.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (valueExists ? 1 : 0);
        result = 31 * result + (serializedValueExists ? 1 : 0);
        return result;
    }

    public static <V> DeferredValue<V> withSerializedValue(Data serializedValue) {
        if (serializedValue == null || isNullData(serializedValue)) {
            return NULL_VALUE;
        }
        DeferredValue<V> deferredValue = new DeferredValue<>();
        deferredValue.serializedValue = serializedValue;
        deferredValue.serializedValueExists = true;
        return deferredValue;
    }

    public static <V> DeferredValue<V> withValue(V value) {
        if (value == null) {
            return NULL_VALUE;
        }
        DeferredValue<V> deferredValue = new DeferredValue<>();
        deferredValue.value = value;
        deferredValue.valueExists = true;
        return deferredValue;
    }

    @SuppressWarnings("unchecked")
    public static <V> DeferredValue<V> withNullValue() {
        return (DeferredValue<V>) NULL_VALUE;
    }

    public static <V> Set<DeferredValue<V>> concurrentSetOfValues(Set<V> values) {
        Set<DeferredValue<V>> result = Collections.newSetFromMap(new ConcurrentHashMap<>());
        for (V value : values) {
            result.add(DeferredValue.withValue(value));
        }
        return result;
    }

    /**
     * Adapts a {@code Set<DeferredValue<V>>} as a {@code Set<V>}. Operations on the returned set pass through to
     * the original {@code deferredValues} set.
     *
     * @param deferredValues        original set of deferred values to be adapted
     * @param serializationService  reference to {@link SerializationService}
     * @param <V>                   the value type
     * @return                      a {@code Set<V>} that wraps the {@code deferredValues} set.
     */
    public static <V> Set<V> asPassThroughSet(Set<DeferredValue<V>> deferredValues, SerializationService serializationService) {
        return new DeferredValueSet<>(serializationService, deferredValues);
    }

    private static class DeferredValueSet<V> extends AbstractSet<V> {
        private final SerializationService serializationService;
        private final Set<DeferredValue<V>> delegate;

        DeferredValueSet(SerializationService serializationService, Set<DeferredValue<V>> delegate) {
            this.serializationService = serializationService;
            this.delegate = delegate;
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public Iterator<V> iterator() {
            return new DeferredValueIterator<>(serializationService, delegate.iterator());
        }

        @Override
        public boolean add(V v) {
            return delegate.add(DeferredValue.withValue(v));
        }

        @Override
        public boolean remove(Object o) {
            return delegate.remove(DeferredValue.withValue(o));
        }

        @Override
        public void clear() {
            delegate.clear();
        }
    }

    private static class DeferredValueIterator<V> implements Iterator<V> {
        private final SerializationService serializationService;
        private final Iterator<DeferredValue<V>> iterator;

        DeferredValueIterator(SerializationService serializationService, Iterator<DeferredValue<V>> iterator) {
            this.serializationService = serializationService;
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public V next() {
            DeferredValue<V> next = iterator.next();
            return next.get(serializationService);
        }

        @Override
        public void remove() {
            iterator.remove();
        }
    }

    static class NullDeferredValue extends DeferredValue {

        static final DeferredValue NULL = new NullDeferredValue();

        @Override
        public Object get(SerializationService serializationService) {
            return null;
        }

        @Override
        public Data getSerializedValue(SerializationService serializationService) {
            return null;
        }

        @Override
        public DeferredValue shallowCopy() {
            return NULL;
        }

        @Override
        public DeferredValue shallowCopy(boolean resolved, SerializationService serializationService) {
            return NULL;
        }

        @Override
        public boolean equals(Object o) {
            return NULL == o;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public String toString() {
            return "NullDeferredValue";
        }
    }
}
