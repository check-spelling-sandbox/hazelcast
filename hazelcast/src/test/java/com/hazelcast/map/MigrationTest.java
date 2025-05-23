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

package com.hazelcast.map;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cluster.Address;
import com.hazelcast.spi.properties.ClusterProperty;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.hazelcast.internal.partition.TestPartitionUtils.getAllReplicaAddresses;
import static com.hazelcast.internal.partition.TestPartitionUtils.getOngoingReplicaSyncRequests;
import static com.hazelcast.internal.partition.TestPartitionUtils.getScheduledReplicaSyncRequests;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(HazelcastSerialClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class MigrationTest extends HazelcastTestSupport {

    protected Config getConfig(String mapName) {
        Config config = smallInstanceConfig();
        config.setProperty(ClusterProperty.PARTITION_CHUNKED_MAX_MIGRATING_DATA_IN_MB.getName(), "1");
        return config;
    }

    @Test
    public void testMigration_whenAddingInstances_withStatisticsEnabled() {
        int size = 1_000;
        String name = randomString();
        Config config = getConfig(name);
        TestHazelcastInstanceFactory nodeFactory = createHazelcastInstanceFactory(3);
        HazelcastInstance instance1 = nodeFactory.newHazelcastInstance(config);

        Map<Integer, Value> map = instance1.getMap(name);
        for (int i = 0; i < size; i++) {
            map.put(i, new Value(i));
        }

        HazelcastInstance instance2 = nodeFactory.newHazelcastInstance(config);
        waitAllForSafeState(instance1, instance2);

        assertEquals("Some records have been lost.", size, map.values().size());
        for (int i = 0; i < size; i++) {
            assertEquals(i, map.get(i).value);
        }

        HazelcastInstance instance3 = nodeFactory.newHazelcastInstance(config);
        waitAllForSafeState(instance1, instance2, instance3);

        assertEquals("Some records have been lost.", size, map.values().size());
        for (int i = 0; i < size; i++) {
            assertEquals(i, map.get(i).value);
        }

        List<HazelcastInstance> list = new ArrayList<>(3);
        list.add(instance1);
        list.add(instance2);
        list.add(instance3);
        assertThatMigrationIsDoneAndReplicasAreIntact(list);
    }

    @Test
    public void testMigration_whenRemovingInstances_withStatisticsDisabled() {
        int size = 1_000;
        String name = randomString();
        Config config = getConfig(name);
        MapConfig mapConfig = config.getMapConfig(name);
        mapConfig.setStatisticsEnabled(false);

        TestHazelcastInstanceFactory nodeFactory = createHazelcastInstanceFactory(3);
        HazelcastInstance instance1 = nodeFactory.newHazelcastInstance(config);
        HazelcastInstance instance2 = nodeFactory.newHazelcastInstance(config);
        HazelcastInstance instance3 = nodeFactory.newHazelcastInstance(config);

        IMap<Integer, Value> map = instance1.getMap(name);
        for (int i = 0; i < size; i++) {
            map.put(i, new Value(i));
        }
        instance2.shutdown();
        instance3.shutdown();

        waitAllForSafeState(instance1);
        assertEquals("Some records have been lost.", size, map.values().size());
        for (int i = 0; i < size; i++) {
            assertEquals(i, map.get(i).value);
        }
        assertThatMigrationIsDoneAndReplicasAreIntact(singletonList(instance1));
    }

    private static void assertThatMigrationIsDoneAndReplicasAreIntact(List<HazelcastInstance> list) {
        // assert that we have as much replicas as instances in the given list
        for (Map.Entry<Integer, List<Address>> entry : getAllReplicaAddresses(list).entrySet()) {
            int replicaIndex = 0;
            for (Address address : entry.getValue()) {
                assertNotNull("Replica of index " + replicaIndex + " is lost!", address);
                if (++replicaIndex == list.size()) {
                    break;
                }
            }
        }

        // assert that no replica syncs are ongoing or scheduled (migrations should be done)
        for (HazelcastInstance instance : list) {
            assertEquals(0, getOngoingReplicaSyncRequests(instance).size());
            assertEquals(0, getScheduledReplicaSyncRequests(instance).size());
        }
    }

    static class Value implements Serializable {

        int value;
        @SuppressWarnings("unused")
        byte[] payload = new byte[100 * 1024];

        Value(int value) {
            this.value = value;
        }

    }

}
