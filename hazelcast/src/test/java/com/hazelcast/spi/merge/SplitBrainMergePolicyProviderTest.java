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

import com.hazelcast.config.InvalidConfigurationException;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.Set;

import static com.hazelcast.test.Accessors.getNode;
import static com.hazelcast.test.ReflectionsHelper.REFLECTIONS;
import static java.lang.reflect.Modifier.isAbstract;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class SplitBrainMergePolicyProviderTest extends HazelcastTestSupport {

    private SplitBrainMergePolicyProvider mergePolicyProvider;

    @Before
    public void setup() {
        mergePolicyProvider = new SplitBrainMergePolicyProvider(getNode(createHazelcastInstance()).getNodeEngine().getConfigClassLoader());
    }

    @Test
    public void getMergePolicy_withNotExistingMergePolicy() {
        assertThatThrownBy(() -> mergePolicyProvider.getMergePolicy("No such policy!", null))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasCauseInstanceOf(ClassNotFoundException.class);
    }

    @Test
    public void getMergePolicy_withNullPolicy() {
        assertThatThrownBy(() -> mergePolicyProvider.getMergePolicy(null, null))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    public void getMergePolicy_withAllImplementations() {
        Set<Class<? extends SplitBrainMergePolicy>> mergePolicyClasses = REFLECTIONS.getSubTypesOf(SplitBrainMergePolicy.class);
        for (Class<? extends SplitBrainMergePolicy> mergePolicyClass : mergePolicyClasses) {
            if (isAbstract(mergePolicyClass.getModifiers())) {
                continue;
            }
            assertMergePolicyCorrectlyInitialised(mergePolicyClass.getSimpleName(), mergePolicyClass);
            assertMergePolicyCorrectlyInitialised(mergePolicyClass.getName(), mergePolicyClass);
        }
    }

    @Test
    public void getMergePolicyUCN_withPolicyExists() {
        assertThatThrownBy(() -> mergePolicyProvider.getMergePolicy("No such policy!", "uc1"))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasCauseInstanceOf(ClassNotFoundException.class);
    }


    private void assertMergePolicyCorrectlyInitialised(String mergePolicyName,
                                                       Class<? extends SplitBrainMergePolicy> expectedMergePolicyClass) {
        SplitBrainMergePolicy mergePolicy = mergePolicyProvider.getMergePolicy(mergePolicyName, null);

        assertNotNull(mergePolicy);
        assertEquals(expectedMergePolicyClass, mergePolicy.getClass());
    }
}
