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

package com.hazelcast.client.impl.protocol.task.transactionalset;

import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.codec.TransactionalSetAddCodec;
import com.hazelcast.client.impl.protocol.task.AbstractTransactionalMessageTask;
import com.hazelcast.collection.impl.set.SetService;
import com.hazelcast.security.SecurityInterceptorConstants;
import com.hazelcast.transaction.TransactionalSet;
import com.hazelcast.instance.impl.Node;
import com.hazelcast.internal.nio.Connection;
import com.hazelcast.security.permission.ActionConstants;
import com.hazelcast.security.permission.SetPermission;
import com.hazelcast.transaction.TransactionContext;

import java.security.Permission;

public class TransactionalSetAddMessageTask
        extends AbstractTransactionalMessageTask<TransactionalSetAddCodec.RequestParameters> {

    public TransactionalSetAddMessageTask(ClientMessage clientMessage, Node node, Connection connection) {
        super(clientMessage, node, connection);
    }

    @Override
    protected Object innerCall() throws Exception {
        final TransactionContext context = endpoint.getTransactionContext(parameters.txnId);
        TransactionalSet<Object> set = context.getSet(parameters.name);
        return set.add(parameters.item);
    }

    @Override
    protected long getClientThreadId() {
        return parameters.threadId;
    }

    @Override
    protected TransactionalSetAddCodec.RequestParameters decodeClientMessage(ClientMessage clientMessage) {
        return TransactionalSetAddCodec.decodeRequest(clientMessage);
    }

    @Override
    protected ClientMessage encodeResponse(Object response) {
        return TransactionalSetAddCodec.encodeResponse((Boolean) response);
    }

    @Override
    public String getServiceName() {
        return SetService.SERVICE_NAME;
    }

    @Override
    public Permission getRequiredPermission() {
        return new SetPermission(parameters.name, ActionConstants.ACTION_ADD);
    }

    @Override
    public String getDistributedObjectName() {
        return parameters.name;
    }

    @Override
    public String getMethodName() {
        return SecurityInterceptorConstants.SET;
    }

    @Override
    public Object[] getParameters() {
        return new Object[]{parameters.item};
    }
}
