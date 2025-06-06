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

package com.hazelcast.test.starter.constructor.test;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.internal.partition.InternalPartition;
import com.hazelcast.internal.partition.InternalPartitionService;
import com.hazelcast.internal.partition.PartitionReplica;
import com.hazelcast.internal.partition.impl.InternalPartitionImpl;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import com.hazelcast.test.starter.constructor.PartitionReplicaConstructor;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static com.hazelcast.test.Accessors.getNode;
import static org.junit.Assert.assertEquals;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class PartitionReplicaConstructorTest extends HazelcastTestSupport {

    @Test
    public void testConstructor() {
        HazelcastInstance hz = createHazelcastInstance();
        warmUpPartitions(hz);

        InternalPartitionService partitionService = getNode(hz).getPartitionService();
        InternalPartition[] partitions = partitionService.getInternalPartitions();
        InternalPartitionImpl partition = (InternalPartitionImpl) partitions[0];
        PartitionReplica replica = partition.getReplica(0);

        PartitionReplicaConstructor constructor = new PartitionReplicaConstructor(PartitionReplica.class);
        PartitionReplica clonedReplica = (PartitionReplica) constructor.createNew(replica);

        assertEquals(replica.address(), clonedReplica.address());
        assertEquals(replica.uuid(), clonedReplica.uuid());
    }
}
