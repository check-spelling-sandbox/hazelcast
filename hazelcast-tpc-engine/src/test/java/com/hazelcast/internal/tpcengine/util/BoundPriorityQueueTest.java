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

package com.hazelcast.internal.tpcengine.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class BoundPriorityQueueTest {

    @Test
    public void test() {
        int capacity = 16;
        BoundPriorityQueue<Integer> queue = new BoundPriorityQueue<>(capacity);
        for (int k = 0; k < capacity; k++) {
            queue.add(k);
        }

        boolean result = queue.offer(0);
        assertFalse(result);
    }
}
