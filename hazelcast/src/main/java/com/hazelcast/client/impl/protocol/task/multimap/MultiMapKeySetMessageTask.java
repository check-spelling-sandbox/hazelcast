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
import com.hazelcast.client.impl.protocol.codec.MultiMapKeySetCodec;
import com.hazelcast.instance.impl.Node;
import com.hazelcast.internal.monitor.impl.LocalMapStatsImpl;
import com.hazelcast.internal.nio.Connection;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.multimap.impl.operations.MultiMapOperationFactory;
import com.hazelcast.multimap.impl.operations.MultiMapResponse;
import com.hazelcast.security.SecurityInterceptorConstants;
import com.hazelcast.security.permission.ActionConstants;
import com.hazelcast.security.permission.MultiMapPermission;
import com.hazelcast.spi.impl.operationservice.OperationFactory;

import java.security.Permission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;


/**
 * Client Protocol Task for handling messages with type ID:
 * {@link com.hazelcast.client.impl.protocol.codec.MultiMapMessageType#MULTIMAP_KEYSET}
 */
public class MultiMapKeySetMessageTask
        extends AbstractMultiMapAllPartitionsMessageTask<String> {

    public MultiMapKeySetMessageTask(ClientMessage clientMessage, Node node, Connection connection) {
        super(clientMessage, node, connection);
    }

    @Override
    protected OperationFactory createOperationFactory() {
        return new MultiMapOperationFactory(parameters, MultiMapOperationFactory.OperationFactoryType.KEY_SET);
    }

    @Override
    protected Object reduce(Map<Integer, Object> map) {
        List<Data> keys = new ArrayList<>();

        for (Object obj : map.values()) {
            if (obj == null) {
                continue;
            }
            MultiMapResponse response = (MultiMapResponse) obj;
            Collection<Data> coll = response.getCollection();
            if (coll != null) {
                keys.addAll(coll);
            }
        }
        updateStats(LocalMapStatsImpl::incrementOtherOperations);
        return keys;
    }

    @Override
    protected String decodeClientMessage(ClientMessage clientMessage) {
        return MultiMapKeySetCodec.decodeRequest(clientMessage);
    }

    @Override
    protected ClientMessage encodeResponse(Object response) {
        return MultiMapKeySetCodec.encodeResponse((List<Data>) response);
    }

    @Override
    public Permission getRequiredPermission() {
        return new MultiMapPermission(parameters, ActionConstants.ACTION_READ);
    }

    @Override
    public String getDistributedObjectName() {
        return parameters;
    }

    @Override
    public String getMethodName() {
        return SecurityInterceptorConstants.KEY_SET;
    }

    @Override
    public Object[] getParameters() {
        return null;
    }
}
