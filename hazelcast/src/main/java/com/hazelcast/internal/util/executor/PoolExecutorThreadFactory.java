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

package com.hazelcast.internal.util.executor;

import com.hazelcast.internal.namespace.impl.NodeEngineThreadLocalContext;
import com.hazelcast.spi.impl.NodeEngine;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hazelcast.internal.util.EmptyStatement.ignore;

public class PoolExecutorThreadFactory extends AbstractExecutorThreadFactory {

    private final String threadNamePrefix;
    private final AtomicInteger idGen = new AtomicInteger();
    // to reuse previous thread IDs
    private final Queue<Integer> idQ = new LinkedBlockingQueue<>(1000);

    private final NodeEngine nodeEngine;

    /**
     * Creates a new instance of {@link PoolExecutorThreadFactory} without {@link NodeEngine} context.
     * This constructor is typically only used for Client-side implementations.
     *
     * @param threadNamePrefix the thread name prefix for this factory
     * @param classLoader      the parent {@link ClassLoader} for this factory
     */
    public PoolExecutorThreadFactory(String threadNamePrefix, ClassLoader classLoader) {
        this(threadNamePrefix, classLoader, null);
    }

    /**
     * Creates a new instance of {@link PoolExecutorThreadFactory} with {@link NodeEngine} context.
     * This constructor is typically used for all Member-side implementations.
     *
     * @param threadNamePrefix the thread name prefix for this factory
     * @param classLoader      the parent {@link ClassLoader} for this factory
     * @param nodeEngine       the {@link NodeEngine} context for the relevant member
     */
    public PoolExecutorThreadFactory(String threadNamePrefix, ClassLoader classLoader, NodeEngine nodeEngine) {
        super(classLoader);
        this.threadNamePrefix = threadNamePrefix;
        this.nodeEngine = nodeEngine;
    }

    @Override
    protected Thread createThread(Runnable r) {
        Integer id = idQ.poll();
        if (id == null) {
            id = idGen.incrementAndGet();
        }
        String name = threadNamePrefix + id;
        return createThread(r, name, id);
    }

    protected ManagedThread createThread(Runnable r, String name, int id) {
        return new ManagedThread(r, name, id);
    }

    protected class ManagedThread extends HazelcastManagedThread {

        private final int id;

        public ManagedThread(Runnable target, String name, int id) {
            super(target, name);
            this.id = id;
        }

        @Override
        protected void executeRun() {
            // nodeEngine can be null (not provided in context, i.e. client executions), but that's fine here
            NodeEngineThreadLocalContext.declareNodeEngineReference(nodeEngine);
            super.executeRun();
        }

        @Override
        protected void afterRun() {
            try {
                idQ.offer(id);
            } catch (Throwable ignored) {
                ignore(ignored);
            }
        }
    }
}
