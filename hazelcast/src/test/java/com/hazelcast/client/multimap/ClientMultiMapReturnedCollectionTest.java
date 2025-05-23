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

package com.hazelcast.client.multimap;

import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.config.Config;
import com.hazelcast.config.MultiMapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.multimap.MultiMap;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class ClientMultiMapReturnedCollectionTest {

    private static final String SET_MAP = "set-map";
    private static final String LIST_MAP = "list-map";
    private final TestHazelcastFactory hazelcastFactory = new TestHazelcastFactory();

    private HazelcastInstance client;

    @After
    public void tearDown() {
        hazelcastFactory.terminateAll();
    }

    @Before
    public void setup() {
        Config config = new Config();
        config.getMultiMapConfig(SET_MAP).setValueCollectionType(MultiMapConfig.ValueCollectionType.SET);
        config.getMultiMapConfig(LIST_MAP).setValueCollectionType(MultiMapConfig.ValueCollectionType.LIST);

        hazelcastFactory.newHazelcastInstance(config);
        client = hazelcastFactory.newHazelcastClient();
    }

    @Test
    public void testGet_withSetBackedValueCollection() {
        MultiMap<Integer, Integer> multiMap = client.getMultiMap(SET_MAP);

        multiMap.put(0, 1);
        multiMap.put(0, 1);
        multiMap.put(0, 2);

        Collection<Integer> collection = multiMap.get(0);

        assertEquals(2, collection.size());
    }

    @Test
    public void testGet_withSetBackedValueCollection_onEmptyMultiMap() {
        MultiMap<Integer, Integer> multiMap = client.getMultiMap(SET_MAP);
        Collection<Integer> collection = multiMap.get(0);

        assertEquals(0, collection.size());
    }

    @Test
    public void testGet_withListBackedValueCollection() {
        MultiMap<Integer, Integer> multiMap = client.getMultiMap(LIST_MAP);

        multiMap.put(0, 1);
        multiMap.put(0, 1);
        multiMap.put(0, 2);

        Collection<Integer> collection = multiMap.get(0);

        assertEquals(3, collection.size());
    }

    @Test
    public void testGet_withListBackedValueCollection_onEmptyMultiMap() {
        MultiMap<Integer, Integer> multiMap = client.getMultiMap(LIST_MAP);
        Collection<Integer> collection = multiMap.get(0);

        assertEquals(0, collection.size());
    }

    @Test
    public void testRemove_withSetBackedValueCollection() {
        MultiMap<Integer, Integer> multiMap = client.getMultiMap(SET_MAP);

        multiMap.put(0, 1);
        multiMap.put(0, 1);
        multiMap.put(0, 2);

        Collection<Integer> collection = multiMap.remove(0);

        assertEquals(2, collection.size());
    }

    @Test
    public void testRemove_withSetBackedValueCollection_onEmptyMultiMap() {
        MultiMap<Integer, Integer> multiMap = client.getMultiMap(SET_MAP);
        Collection<Integer> collection = multiMap.remove(0);

        assertEquals(0, collection.size());
    }

    @Test
    public void testRemove_withListBackedValueCollection() {
        MultiMap<Integer, Integer> multiMap = client.getMultiMap(LIST_MAP);

        multiMap.put(0, 1);
        multiMap.put(0, 1);
        multiMap.put(0, 2);

        Collection<Integer> collection = multiMap.remove(0);

        assertEquals(3, collection.size());
    }

    @Test
    public void testRemove_withListBackedValueCollection_onEmptyMultiMap() {
        MultiMap<Integer, Integer> multiMap = client.getMultiMap(LIST_MAP);
        Collection<Integer> collection = multiMap.remove(0);

        assertEquals(0, collection.size());
    }
}
