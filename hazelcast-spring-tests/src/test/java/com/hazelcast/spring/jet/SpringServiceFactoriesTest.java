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

package com.hazelcast.spring.jet;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.jet.JetService;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.test.SimpleEvent;
import com.hazelcast.jet.pipeline.test.TestSources;
import com.hazelcast.spring.CustomSpringExtension;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CompletionException;

import static com.hazelcast.jet.pipeline.test.AssertionSinks.assertAnyOrder;
import static com.hazelcast.jet.pipeline.test.AssertionSinks.assertCollectedEventually;
import static com.hazelcast.spring.jet.JetSpringServiceFactories.bean;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


@ExtendWith({SpringExtension.class, CustomSpringExtension.class})
@ContextConfiguration(locations = {"application-context-jet-service.xml"})
class SpringServiceFactoriesTest {

    @Autowired
    private JetService jet;

    @BeforeAll
    @AfterAll
    static void start() {
        Hazelcast.shutdownAll();
    }

    @Test
    void testMapBatchUsingSpringBean() {
        Pipeline pipeline = Pipeline.create();
        pipeline.readFrom(TestSources.items(1L, 2L, 3L, 4L, 5L, 6L))
                .mapUsingService(bean("calculator"), Calculator::multiply)
                .writeTo(assertAnyOrder(asList(-1L, -2L, -3L, -4L, -5L, -6L)));

        jet.newJob(pipeline).join();
    }

    @Test
    void testFilterBatchUsingSpringBean() {
        Pipeline pipeline = Pipeline.create();
        pipeline.readFrom(TestSources.items(1L, 2L, 3L, 4L, 5L, 6L))
                .filterUsingService(bean("calculator"), Calculator::filter)
                .writeTo(assertAnyOrder(asList(2L, 4L, 6L)));

        jet.newJob(pipeline).join();
    }

    @Test
    void testMapStreamUsingSpringBean() {
        Pipeline pipeline = Pipeline.create();
        pipeline.readFrom(TestSources.itemStream(100))
                .withNativeTimestamps(0)
                .map(SimpleEvent::sequence)
                .mapUsingService(bean("calculator"), Calculator::multiply)
                .writeTo(assertCollectedEventually(10, c -> {
                    assertTrue(c.size() > 100);
                    c.forEach(i -> assertTrue(i <= 0));
                }));

        Job job = jet.newJob(pipeline);
        assertJobCompleted(job);
    }

    @Test
    void testFilterStreamUsingSpringBean() {
        Pipeline pipeline = Pipeline.create();
        pipeline.readFrom(TestSources.itemStream(100))
                .withNativeTimestamps(0)
                .map(SimpleEvent::sequence)
                .filterUsingService(bean("calculator"), Calculator::filter)
                .writeTo(assertCollectedEventually(10, c -> {
                    assertTrue(c.size() > 100);
                    c.forEach(i -> assertEquals(0, i % 2));
                }));

        Job job = jet.newJob(pipeline);
        assertJobCompleted(job);
    }

    private static void assertJobCompleted(Job job) {
        try {
            job.join();
            fail("expected CompletionException");
        } catch (CompletionException e) {
            assertTrue(e.getMessage().contains("AssertionCompletedException: Assertion passed successfully"));
        }
    }
}
