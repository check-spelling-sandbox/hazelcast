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

import com.hazelcast.aggregation.Aggregator;
import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.codec.MapAggregateWithPredicateCodec;
import com.hazelcast.instance.impl.Node;
import com.hazelcast.internal.nio.Connection;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.query.Predicate;
import com.hazelcast.security.SecurityInterceptorConstants;

public class MapAggregateWithPredicateMessageTask
        extends DefaultMapAggregateMessageTask<MapAggregateWithPredicateCodec.RequestParameters> {

    public MapAggregateWithPredicateMessageTask(ClientMessage clientMessage, Node node, Connection connection) {
        super(clientMessage, node, connection);
    }

    @Override
    protected Aggregator<?, ?> getAggregator() {
        return nodeEngine.getSerializationService().toObject(parameters.aggregator);
    }

    @Override
    protected Predicate getPredicate() {
        return nodeEngine.getSerializationService().toObject(parameters.predicate);
    }

    @Override
    protected MapAggregateWithPredicateCodec.RequestParameters decodeClientMessage(ClientMessage clientMessage) {
        return MapAggregateWithPredicateCodec.decodeRequest(clientMessage);
    }

    @Override
    protected ClientMessage encodeResponse(Object response) {
        Data data = nodeEngine.getSerializationService().toData(response);
        return MapAggregateWithPredicateCodec.encodeResponse(data);
    }

    @Override
    public String getDistributedObjectName() {
        return parameters.name;
    }

    @Override
    public String getMethodName() {
        return SecurityInterceptorConstants.AGGREGATE_WITH_PREDICATE;
    }

    @Override
    public Object[] getParameters() {
        return new Object[]{parameters.name, parameters.aggregator, parameters.predicate};
    }
}
