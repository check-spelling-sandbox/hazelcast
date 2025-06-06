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

package com.hazelcast.internal.ascii;

import com.hazelcast.config.AdvancedNetworkConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.RestServerEndpointConfig;
import org.junit.Ignore;

public class RestClusterMultiendpointTest
        extends RestClusterTest {

    @Override
    protected Config createConfig() {
        Config c = new Config();
        AdvancedNetworkConfig anc = c.getAdvancedNetworkConfig();
        anc.setEnabled(true);
        return c;
    }

    @Override
    protected Config createConfigWithRestEnabled() {
        Config config = createConfig();
        config.getAdvancedNetworkConfig().setRestEndpointConfig(new RestServerEndpointConfig().enableAllGroups());
        return config;
    }

    @Override
    @Ignore("There is no port set for multi-endpoint when REST is disabled")
    public void testDisabledRest() {
    }
}
