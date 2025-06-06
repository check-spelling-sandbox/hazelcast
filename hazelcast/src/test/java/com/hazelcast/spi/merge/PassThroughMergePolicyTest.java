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

package com.hazelcast.spi.merge;

import com.hazelcast.internal.serialization.impl.DefaultSerializationServiceBuilder;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.spi.merge.SplitBrainMergeTypes.MapMergeTypes;
import com.hazelcast.internal.serialization.SerializationService;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class PassThroughMergePolicyTest {

    private static final SerializationService SERIALIZATION_SERVICE = new DefaultSerializationServiceBuilder().build();
    private static final Data EXISTING = SERIALIZATION_SERVICE.toData("EXISTING");
    private static final Data MERGING = SERIALIZATION_SERVICE.toData("MERGING");

    private SplitBrainMergePolicy<String, MapMergeTypes<Object, String>, Object> mergePolicy;

    @Before
    public void setup() {
        mergePolicy = new PassThroughMergePolicy<>();
    }

    @Test
    public void merge_mergingNotNull() {
        MapMergeTypes existing = mergingValueWithGivenValue(EXISTING);
        MapMergeTypes merging = mergingValueWithGivenValue(MERGING);

        assertEquals(MERGING, mergePolicy.merge(merging, existing));
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void merge_mergingNull() {
        MapMergeTypes existing = mergingValueWithGivenValue(EXISTING);
        MapMergeTypes merging = null;

        assertEquals(EXISTING, mergePolicy.merge(merging, existing));
    }

    private MapMergeTypes mergingValueWithGivenValue(Data value) {
        MapMergeTypes mergingValue = mock(MapMergeTypes.class);
        try {
            when(mergingValue.getRawValue()).thenReturn(value);
            return mergingValue;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
