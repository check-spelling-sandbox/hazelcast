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

package com.hazelcast.client.impl.protocol.task.cache;

import com.hazelcast.cache.impl.CacheOperationProvider;
import com.hazelcast.cache.impl.operation.CachePutOperation;
import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.codec.CacheReplaceCodec;
import com.hazelcast.instance.impl.Node;
import com.hazelcast.internal.nio.Connection;
import com.hazelcast.security.SecurityInterceptorConstants;
import com.hazelcast.security.permission.ActionConstants;
import com.hazelcast.security.permission.CachePermission;
import com.hazelcast.spi.impl.operationservice.Operation;

import javax.cache.expiry.ExpiryPolicy;
import java.security.Permission;

/**
 * This client request specifically calls {@link CachePutOperation} on the server side.
 *
 * @see CachePutOperation
 */
public class CacheReplaceMessageTask
        extends AbstractCacheMessageTask<CacheReplaceCodec.RequestParameters> {

    public CacheReplaceMessageTask(ClientMessage clientMessage, Node node, Connection connection) {
        super(clientMessage, node, connection);
    }

    @Override
    protected Operation prepareOperation() {
        CacheOperationProvider operationProvider = getOperationProvider(parameters.name);
        ExpiryPolicy expiryPolicy = serializationService.toObject(parameters.expiryPolicy);
        return operationProvider
                .createReplaceOperation(parameters.key, parameters.oldValue, parameters.newValue,
                        expiryPolicy, parameters.completionId);
    }

    @Override
    protected CacheReplaceCodec.RequestParameters decodeClientMessage(ClientMessage clientMessage) {
        return CacheReplaceCodec.decodeRequest(clientMessage);
    }

    @Override
    protected ClientMessage encodeResponse(Object response) {
        return CacheReplaceCodec.encodeResponse(serializationService.toData(response));
    }

    @Override
    public Permission getRequiredPermission() {
        return new CachePermission(parameters.name, ActionConstants.ACTION_PUT);
    }

    @Override
    public String getDistributedObjectName() {
        return parameters.name;
    }

    @Override
    public Object[] getParameters() {
        if (parameters.expiryPolicy == null && parameters.oldValue != null) {
            return new Object[]{parameters.key, parameters.oldValue, parameters.newValue};
        }
        if (parameters.oldValue == null && parameters.expiryPolicy == null) {
            return new Object[]{parameters.key, parameters.newValue};
        }
        if (parameters.oldValue == null && parameters.expiryPolicy != null) {
            return new Object[]{parameters.key, parameters.newValue, parameters.expiryPolicy};
        }
        return new Object[]{parameters.key, parameters.oldValue, parameters.newValue, parameters.expiryPolicy};
    }

    @Override
    public String getMethodName() {
        return SecurityInterceptorConstants.REPLACE;
    }
}
