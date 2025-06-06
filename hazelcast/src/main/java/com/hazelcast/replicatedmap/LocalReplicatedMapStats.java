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

package com.hazelcast.replicatedmap;

import com.hazelcast.internal.monitor.MemberState;
import com.hazelcast.map.LocalMapStats;

/**
 * Local replicated map statistics to be used by {@link MemberState}
 * implementations.
 */
public interface LocalReplicatedMapStats extends LocalMapStats {

    /**
     * Increments the number of {@link ReplicatedMap#values()} calls.
     */
    default void incrementValuesCallCount() {
    }

    /**
     * Increments the number of {@link ReplicatedMap#entrySet()} calls.
     */
    default void incrementEntrySetCallCount() {
    }
}
