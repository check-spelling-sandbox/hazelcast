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

package com.hazelcast.map.impl;

import com.hazelcast.config.MapConfig;
import com.hazelcast.internal.services.ServiceNamespace;
import com.hazelcast.map.impl.recordstore.RecordStore;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

public interface PartitionContainer {

    ConcurrentMap<String, RecordStore> getMaps();

    Collection<RecordStore> getAllRecordStores();

    Collection<ServiceNamespace> getAllNamespaces(int replicaIndex);

    Collection<ServiceNamespace> getNamespaces(Predicate<MapConfig> predicate, int replicaIndex);

    int getPartitionId();

    MapService getMapService();

    RecordStore getRecordStore(String name);

    RecordStore getRecordStore(String name, boolean skipLoadingOnCreate);

    RecordStore getRecordStoreForHotRestart(String name);

    RecordStore getExistingRecordStore(String mapName);

    void destroyMap(MapContainer mapContainer);

    boolean hasRunningCleanup();

    void setHasRunningCleanup(boolean hasRunningCleanup);

    long getLastCleanupTime();

    void setLastCleanupTime(long lastCleanupTime);

    long getLastCleanupTimeCopy();

    void setLastCleanupTimeCopy(long lastCleanupTimeCopy);

    /**
     * Cleans up the container's state if the enclosing partition is migrated
     * off this member. Whether cleanup is needed is decided based on the
     * provided {@code replicaIndex}.
     *
     * @param replicaIndex The replica index to use for deciding per map whether
     *                     cleanup is necessary or not
     */
    void cleanUpOnMigration(int replicaIndex);
}
