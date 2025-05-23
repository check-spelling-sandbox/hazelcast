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

package classloading;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.instance.impl.Node;
import com.hazelcast.spi.impl.InternalCompletableFuture;
import com.hazelcast.spi.impl.PartitionSpecificRunnable;
import com.hazelcast.spi.impl.executionservice.ExecutionService;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import static classloading.ThreadLeakTestUtils.assertHazelcastThreadShutdown;
import static classloading.ThreadLeakTestUtils.getAndLogThreads;
import static classloading.ThreadLeakTestUtils.getThreads;
import static com.hazelcast.instance.impl.TestUtil.getNode;
import static com.hazelcast.internal.util.ExceptionUtil.peel;
import static com.hazelcast.internal.util.ExceptionUtil.sneakyThrow;
import static com.hazelcast.test.HazelcastTestSupport.assertJoinable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(HazelcastSerialClassRunner.class)
@Category(QuickTest.class)
public class ThreadLeakTest {

    @After
    public void shutdownInstances() {
        Hazelcast.shutdownAll();
    }

    @Test
    public void testThreadLeak() {
        Set<Thread> oldThreads = getThreads();
        HazelcastInstance hz = Hazelcast.newHazelcastInstance();
        hz.shutdown();
        assertHazelcastThreadShutdown(oldThreads);
    }

    @Test
    public void testThreadLeakUtils() {
        final Set<Thread> threads = getThreads();
        final CountDownLatch latch = new CountDownLatch(1);

        Thread thread = new Thread("leaking-thread") {
            @Override
            public void run() {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        thread.start();

        Thread[] runningThreads = getAndLogThreads("There should be one thread running!", threads);
        assertNotNull("Expected to get running threads, but was null", runningThreads);
        assertEquals("Expected exactly one running thread", 1, runningThreads.length);

        latch.countDown();
        assertJoinable(thread);

        runningThreads = getAndLogThreads("There should be no threads running!", threads);
        assertNull("Expected to get null, but found running threads", runningThreads);

        assertHazelcastThreadShutdown(threads);
    }

    // Fixes https://github.com/hazelcast/hazelcast/issues/24484
    @Test
    public void testThreadLeakWithAsyncExecutor() {
        Set<Thread> oldThreads = getThreads();
        HazelcastInstance hz = Hazelcast.newHazelcastInstance();

        Node node = getNode(hz);
        ExecutorService executorService =
                node.getNodeEngine().getExecutionService().getExecutor(ExecutionService.ASYNC_EXECUTOR);

        executorService.submit(
                () -> {
                    InternalCompletableFuture<String> future = new InternalCompletableFuture<>();
                    PartitionSpecificRunnable task = new PartitionSpecificRunnable() {
                        @Override
                        public void run() {
                            future.complete("X");
                        }

                        @Override
                        public int getPartitionId() {
                            return 0;
                        }
                    };

                    hz.shutdown();
                    node.getNodeEngine().getOperationService().execute(task);
                    try {
                        future.get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw sneakyThrow(peel(e));
                    }
                });

        assertHazelcastThreadShutdown(oldThreads);
    }
}
