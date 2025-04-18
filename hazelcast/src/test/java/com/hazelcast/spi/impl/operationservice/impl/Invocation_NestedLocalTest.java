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

package com.hazelcast.spi.impl.operationservice.impl;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spi.impl.InternalCompletableFuture;
import com.hazelcast.spi.impl.operationservice.OperationService;
import com.hazelcast.spi.properties.ClusterProperty;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static com.hazelcast.test.Accessors.getAddress;
import static com.hazelcast.test.Accessors.getOperationService;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class Invocation_NestedLocalTest extends Invocation_NestedAbstractTest {

    private static final String RESPONSE = "someresponse";

    @Test
    public void invokeOnPartition_outerGeneric_innerGeneric_forbidden() {
        HazelcastInstance local = createHazelcastInstance();
        OperationService operationService = getOperationService(local);

        InnerOperation innerOperation = new InnerOperation(RESPONSE, GENERIC_OPERATION);
        OuterOperation outerOperation = new OuterOperation(innerOperation, GENERIC_OPERATION);

        InternalCompletableFuture<Object> future =
                operationService.invokeOnPartition(null, outerOperation, outerOperation.getPartitionId());
        assertThatThrownBy(future::join)
                .isInstanceOf(Exception.class);
    }

    @Test
    public void invokeOnPartition_outerLocal_innerGeneric() {
        HazelcastInstance local = createHazelcastInstance();
        OperationService operationService = getOperationService(local);

        int partitionId = getPartitionId(local);
        InnerOperation innerOperation = new InnerOperation(RESPONSE, GENERIC_OPERATION);
        OuterOperation outerOperation = new OuterOperation(innerOperation, partitionId);
        InternalCompletableFuture<Object> future = operationService.invokeOnPartition(null, outerOperation, partitionId);

        assertEquals(RESPONSE, future.join());
    }

    @Test
    public void invokeOnPartition_outerLocal_innerSameInstance_samePartition() {
        HazelcastInstance local = createHazelcastInstance();
        OperationService operationService = getOperationService(local);

        int partitionId = getPartitionId(local);
        InnerOperation innerOperation = new InnerOperation(RESPONSE, partitionId);
        OuterOperation outerOperation = new OuterOperation(innerOperation, partitionId);
        InternalCompletableFuture<Object> future = operationService.invokeOnPartition(null, outerOperation, partitionId);

        assertEquals(RESPONSE, future.join());
    }

    @Test
    public void invokeOnPartition_outerLocal_innerSameInstance_callsDifferentPartition() {
        HazelcastInstance local = createHazelcastInstance();
        OperationService operationService = getOperationService(local);

        int outerPartitionId = getPartitionId(local);
        int innerPartitionId = randomPartitionIdNotMappedToSameThreadAsGivenPartitionIdOnInstance(local, outerPartitionId);
        InnerOperation innerOperation = new InnerOperation(RESPONSE, innerPartitionId);
        OuterOperation outerOperation = new OuterOperation(innerOperation, outerPartitionId);
        InternalCompletableFuture<Object> future = operationService.invokeOnPartition(null, outerOperation, outerPartitionId);


        assertThatThrownBy(future::joinInternal)
                .isInstanceOf(IllegalThreadStateException.class)
                .hasMessageContaining("cannot make remote call");
    }

    @Test
    public void invokeOnPartition_outerLocal_innerSameInstance_callsDifferentPartition_mappedToSameThread() {
        Config config = new Config();
        config.setProperty(ClusterProperty.PARTITION_COUNT.getName(), "2");
        config.setProperty(ClusterProperty.PARTITION_OPERATION_THREAD_COUNT.getName(), "1");
        HazelcastInstance local = createHazelcastInstance(config);
        final OperationService operationService = getOperationService(local);

        int outerPartitionId = 1;
        int innerPartitionId = 0;
        InnerOperation innerOperation = new InnerOperation(RESPONSE, innerPartitionId);
        OuterOperation outerOperation = new OuterOperation(innerOperation, outerPartitionId);
        InternalCompletableFuture<Object> future
                = operationService.invokeOnPartition(null, outerOperation, outerOperation.getPartitionId());

        assertThatThrownBy(future::joinInternal)
                .isInstanceOf(IllegalThreadStateException.class)
                .hasMessageContaining("cannot make remote call");
    }

    @Test
    public void invokeOnTarget_outerGeneric_innerGeneric() {
        HazelcastInstance local = createHazelcastInstance();
        OperationService operationService = getOperationService(local);

        InnerOperation innerOperation = new InnerOperation(RESPONSE, GENERIC_OPERATION);
        OuterOperation outerOperation = new OuterOperation(innerOperation, GENERIC_OPERATION);
        InternalCompletableFuture<Object> future = operationService.invokeOnTarget(null, outerOperation, getAddress(local));

        assertEquals(RESPONSE, future.join());
    }

    @Test
    public void invokeOnTarget_outerGeneric_innerSameInstance() {
        HazelcastInstance local = createHazelcastInstance();
        OperationService operationService = getOperationService(local);

        int innerPartitionId = getPartitionId(local);
        InnerOperation innerOperation = new InnerOperation(RESPONSE, innerPartitionId);
        OuterOperation outerOperation = new OuterOperation(innerOperation, GENERIC_OPERATION);
        InternalCompletableFuture<Object> future = operationService.invokeOnTarget(null, outerOperation, getAddress(local));

        assertEquals(RESPONSE, future.join());
    }
}
