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

package com.hazelcast.client.impl.protocol.task.map;

import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.instance.impl.Node;
import com.hazelcast.internal.nio.Connection;
import com.hazelcast.internal.util.Timer;
import com.hazelcast.map.impl.MapContainer;
import com.hazelcast.map.impl.MapService;
import com.hazelcast.security.SecurityInterceptorConstants;
import com.hazelcast.security.permission.ActionConstants;
import com.hazelcast.security.permission.MapPermission;

import java.security.Permission;

public abstract class AbstractMapSetMessageTask<P> extends AbstractMapPartitionMessageTask<P> {

    protected transient long startTimeNanos;

    protected AbstractMapSetMessageTask(ClientMessage clientMessage, Node node, Connection connection) {
        super(clientMessage, node, connection);
    }

    @Override
    protected void beforeProcess() {
        startTimeNanos = Timer.nanos();
    }

    @Override
    protected Object processResponseBeforeSending(Object response) {
        MapService mapService = getService(MapService.SERVICE_NAME);
        MapContainer mapContainer = mapService.getMapServiceContext().getMapContainer(getDistributedObjectName());
        if (mapContainer.getMapConfig().isStatisticsEnabled()) {
            mapService.getMapServiceContext().getLocalMapStatsProvider().getLocalMapStatsImpl(getDistributedObjectName())
                    .incrementSetLatencyNanos(Timer.nanosElapsed(startTimeNanos));
        }
        return response;
    }

    @Override
    public Permission getRequiredPermission() {
        return new MapPermission(getDistributedObjectName(), ActionConstants.ACTION_PUT);
    }

    @Override
    public String getServiceName() {
        return SecurityInterceptorConstants.IMAP_SERVICE;
    }

}
