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

package com.hazelcast.client.map;

import com.hazelcast.map.QueryBounceTest;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.SlowTest;
import com.hazelcast.test.bounce.BounceTestConfiguration;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 * Test querying a cluster from Hazelcast clients while members are shutting down and joining.
 */
@RunWith(HazelcastSerialClassRunner.class)
@Category(SlowTest.class)
public class ClientQueryBounceTest extends QueryBounceTest {
    @Override
    protected BounceTestConfiguration.DriverType getDriverType() {
        return BounceTestConfiguration.DriverType.CLIENT;
    }
}
