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

package com.hazelcast.client.impl.protocol.task.multimap;

import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.codec.MultiMapClearCodec;
import com.hazelcast.core.EntryEventType;
import com.hazelcast.instance.impl.Node;
import com.hazelcast.internal.monitor.impl.LocalMapStatsImpl;
import com.hazelcast.multimap.impl.MultiMapService;
import com.hazelcast.multimap.impl.operations.MultiMapOperationFactory;
import com.hazelcast.internal.nio.Connection;
import com.hazelcast.security.SecurityInterceptorConstants;
import com.hazelcast.security.permission.ActionConstants;
import com.hazelcast.security.permission.MultiMapPermission;
import com.hazelcast.spi.impl.operationservice.OperationFactory;

import java.security.Permission;
import java.util.Map;

/**
 * Client Protocol Task for handling messages with type ID:
 * {@link com.hazelcast.client.impl.protocol.codec.MultiMapMessageType#MULTIMAP_CLEAR}
 */
public class MultiMapClearMessageTask
        extends AbstractMultiMapAllPartitionsMessageTask<String> {

    public MultiMapClearMessageTask(ClientMessage clientMessage, Node node, Connection connection) {
        super(clientMessage, node, connection);
    }

    protected OperationFactory createOperationFactory() {
        return new MultiMapOperationFactory(parameters, MultiMapOperationFactory.OperationFactoryType.CLEAR);
    }

    @Override
    protected Object reduce(Map<Integer, Object> map) {
        int totalAffectedEntries = 0;
        for (Object affectedEntries : map.values()) {
            totalAffectedEntries += (Integer) affectedEntries;
        }
        updateStats(LocalMapStatsImpl::incrementOtherOperations);
        final MultiMapService service = getService(MultiMapService.SERVICE_NAME);
        service.publishMultiMapEvent(parameters, EntryEventType.CLEAR_ALL, totalAffectedEntries);
        return null;
    }

    @Override
    protected String decodeClientMessage(ClientMessage clientMessage) {
        return MultiMapClearCodec.decodeRequest(clientMessage);
    }

    @Override
    protected ClientMessage encodeResponse(Object response) {
        return MultiMapClearCodec.encodeResponse();
    }

    @Override
    public Permission getRequiredPermission() {
        return new MultiMapPermission(parameters, ActionConstants.ACTION_REMOVE);
    }

    @Override
    public String getDistributedObjectName() {
        return parameters;
    }

    @Override
    public String getMethodName() {
        return SecurityInterceptorConstants.CLEAR;
    }

    @Override
    public Object[] getParameters() {
        return null;
    }

}
