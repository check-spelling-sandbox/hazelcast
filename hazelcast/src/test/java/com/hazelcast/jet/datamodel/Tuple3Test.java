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

package com.hazelcast.jet.datamodel;

import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static com.hazelcast.jet.datamodel.Tuple3.tuple3;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class Tuple3Test {
    private Tuple3<String, String, String> t;

    @Test
    public void when_useFactory_then_everythingThere() {
        // When
        t = tuple3("a", "b", "c");

        // Then
        assertEquals("a", t.f0());
        assertEquals("b", t.f1());
        assertEquals("c", t.f2());
    }

    @Test
    public void when_equalTuples_thenEqualsTrueAndHashCodesEqual() {
        // Given
        t = tuple3("a", "b", "c");
        Tuple3<String, String, String> t_b = tuple3("a", "b", "c");

        // When - Then
        assertEquals(t, t_b);
        assertEquals(t.hashCode(), t_b.hashCode());
    }

    @Test
    public void when_unequalTuples_thenEqualsFalse() {
        // Given
        t = tuple3("a", "b", "c");
        Tuple3<String, String, String> t_b = tuple3("a", "b", "xc");

        // When - Then
        assertNotEquals(t, t_b);
    }

    @Test
    public void when_toString_then_noFailures() {
        assertNotNull(tuple3("a", "b", "c").toString());
        assertNotNull(tuple3(null, null, null).toString());
    }
}
