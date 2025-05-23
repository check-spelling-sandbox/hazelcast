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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds some specific information for per cache in the node and shared by all partitions of that cache on the node.
 */
public class CacheContext {

    private final AtomicBoolean implicitMerkleTreeEnableLogged = new AtomicBoolean();
    private final AtomicLong entryCount = new AtomicLong();
    private final AtomicInteger cacheEntryListenerCount = new AtomicInteger();
    private final AtomicInteger invalidationListenerCount = new AtomicInteger();

    public long getEntryCount() {
        return entryCount.get();
    }

    public long increaseEntryCount() {
        return entryCount.incrementAndGet();
    }

    public long increaseEntryCount(long count) {
        return entryCount.addAndGet(count);
    }

    public long decreaseEntryCount() {
        return entryCount.decrementAndGet();
    }

    public long decreaseEntryCount(long count) {
        return entryCount.addAndGet(-count);
    }

    public void resetEntryCount() {
        entryCount.set(0L);
    }

    public int getCacheEntryListenerCount() {
        return cacheEntryListenerCount.get();
    }

    public void increaseCacheEntryListenerCount() {
        cacheEntryListenerCount.incrementAndGet();
    }

    public void decreaseCacheEntryListenerCount() {
        int newCount = cacheEntryListenerCount.decrementAndGet();
        assert newCount >= 0 : "CacheEntryListenerCount decremented to a value below zero! New value: " + newCount;
    }

    public void resetCacheEntryListenerCount() {
        cacheEntryListenerCount.set(0);
    }

    public int getInvalidationListenerCount() {
        return invalidationListenerCount.get();
    }

    public void increaseInvalidationListenerCount() {
        invalidationListenerCount.incrementAndGet();
    }

    public void decreaseInvalidationListenerCount() {
        invalidationListenerCount.decrementAndGet();
    }

    public void resetInvalidationListenerCount() {
        invalidationListenerCount.set(0);
    }

    boolean shouldLogImplicitMerkleTreeEnable() {
        return implicitMerkleTreeEnableLogged.compareAndSet(false, true);
    }

    @Override
    public String toString() {
        return "CacheContext{"
                + "entryCount=" + entryCount.get()
                + ", cacheEntryListenerCount=" + cacheEntryListenerCount.get()
                + ", invalidationListenerCount=" + invalidationListenerCount.get()
                + '}';
    }

}
