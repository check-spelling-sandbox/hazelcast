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

package com.hazelcast.osgi;

import com.hazelcast.core.HazelcastInstance;

/**
 * Contract point for {@link com.hazelcast.core.HazelcastInstance} implementations based on OSGi service.
 */
public interface HazelcastOSGiInstance
        extends HazelcastInstance {

    /**
     * Gets the delegated (underlying) {@link com.hazelcast.core.HazelcastInstance}.
     *
     * @return the delegated (underlying) {@link com.hazelcast.core.HazelcastInstance}
     */
    HazelcastInstance getDelegatedInstance();

    /**
     * Gets the owner {@link HazelcastOSGiService} of this instance.
     *
     * @return the owner {@link HazelcastOSGiService} of this instance
     */
    HazelcastOSGiService getOwnerService();

}
