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

package com.hazelcast.multimap.impl;

import com.hazelcast.config.MergePolicyConfig;
import com.hazelcast.spi.impl.NodeEngine;
import com.hazelcast.spi.impl.merge.AbstractContainerCollector;

import java.util.Collection;
import java.util.Iterator;

class MultiMapContainerCollector extends AbstractContainerCollector<MultiMapContainer> {

    private final MultiMapPartitionContainer[] partitionContainers;

    MultiMapContainerCollector(NodeEngine nodeEngine, MultiMapPartitionContainer[] partitionContainers) {
        super(nodeEngine);
        this.partitionContainers = partitionContainers;
    }

    @Override
    protected Iterator<MultiMapContainer> containerIterator(int partitionId) {
        MultiMapPartitionContainer partitionContainer = partitionContainers[partitionId];
        if (partitionContainer == null) {
            return new EmptyIterator();
        }
        return partitionContainer.containerMap.values().iterator();
    }

    @Override
    protected MergePolicyConfig getMergePolicyConfig(MultiMapContainer container) {
        return container.getConfig().getMergePolicyConfig();
    }

    @Override
    protected String getUserNamespaceContainer(MultiMapContainer container) {
        return container.getConfig().getUserCodeNamespace();
    }

    @Override
    protected void destroy(MultiMapContainer container) {
        container.destroy();
    }

    @Override
    protected void destroyBackup(MultiMapContainer container) {
        container.destroy();
    }

    @Override
    protected long getMergingValueCount() {
        long size = 0;
        for (Collection<MultiMapContainer> containers : getCollectedContainers().values()) {
            for (MultiMapContainer container : containers) {
                size += container.size();
            }
        }
        return size;
    }
}
