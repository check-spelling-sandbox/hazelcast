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

package com.hazelcast.internal.monitor.impl;

import com.hazelcast.collection.LocalCollectionStats;

public abstract class AbstractLocalCollectionStats implements LocalCollectionStats {

    public static final String LAST_ACCESS_TIME = "lastAccessTime";
    public static final String LAST_UPDATE_TIME = "lastUpdateTime";
    public static final String CREATION_TIME = "creationTime";

    protected AbstractLocalCollectionStats() {
    }

    public abstract void setLastAccessTime(long lastAccessTime);

    public abstract void setLastUpdateTime(long lastUpdateTime);

}
