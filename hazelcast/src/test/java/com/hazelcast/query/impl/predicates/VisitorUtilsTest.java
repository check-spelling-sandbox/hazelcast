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

package com.hazelcast.query.impl.predicates;

import com.hazelcast.query.Predicate;
import com.hazelcast.query.impl.IndexRegistry;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static com.hazelcast.query.impl.predicates.PredicateTestUtils.createMockVisitablePredicate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class VisitorUtilsTest extends HazelcastTestSupport {

    private IndexRegistry mockIndexes;

    @Before
    public void setUp() {
        mockIndexes = mock(IndexRegistry.class);
    }

    @Test
    public void testConstructor() {
        assertUtilityConstructor(VisitorUtils.class);
    }

    @Test
    public void acceptVisitor_whenEmptyInputArray_thenReturnOriginalArray() {
        Visitor mockVisitor = mock(Visitor.class);
        Predicate[] predicates = new Predicate[0];
        Predicate[] result = VisitorUtils.acceptVisitor(predicates, mockVisitor, mockIndexes);

        assertThat(result).isSameAs(predicates);
    }

    @Test
    public void acceptVisitor_whenNoChange_thenReturnOriginalArray() {
        Visitor mockVisitor = mock(Visitor.class);

        Predicate[] predicates = new Predicate[1];
        Predicate predicate = createMockVisitablePredicate();
        predicates[0] = predicate;

        Predicate[] result = VisitorUtils.acceptVisitor(predicates, mockVisitor, mockIndexes);
        assertThat(result).isSameAs(predicates);
    }

    @Test
    public void acceptVisitor_whenThereIsChange_thenReturnNewArray() {
        Visitor mockVisitor = mock(Visitor.class);

        Predicate[] predicates = new Predicate[2];
        Predicate p1 = createMockVisitablePredicate();
        predicates[0] = p1;

        Predicate transformed = mock(Predicate.class);
        Predicate p2 = createMockVisitablePredicate(transformed);
        predicates[1] = p2;

        Predicate[] result = VisitorUtils.acceptVisitor(predicates, mockVisitor, mockIndexes);
        assertThat(result).isNotSameAs(predicates);
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder(p1, transformed);
    }

    @Test
    public void acceptVisitor_whenThereIsNonVisitablePredicateAndNewArraysIsCreated_thenJustCopyTheNonVisitablePredicate() {
        Visitor mockVisitor = mock(Visitor.class);

        Predicate[] predicates = new Predicate[3];
        Predicate p1 = mock(Predicate.class);
        predicates[0] = p1;

        Predicate transformed = mock(Predicate.class);
        Predicate p2 = createMockVisitablePredicate(transformed);
        predicates[1] = p2;

        Predicate p3 = mock(Predicate.class);
        predicates[2] = p3;

        Predicate[] result = VisitorUtils.acceptVisitor(predicates, mockVisitor, mockIndexes);
        assertThat(result).isNotSameAs(predicates);
        assertThat(result).hasSize(3);
        assertThat(result).containsExactlyInAnyOrder(p1, transformed, p3);
    }
}
