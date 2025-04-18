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

package com.hazelcast.topic.impl.reliable;

import com.hazelcast.cluster.Member;
import com.hazelcast.internal.cluster.ClusterService;
import com.hazelcast.internal.namespace.NamespaceUtil;
import com.hazelcast.logging.ILogger;
import com.hazelcast.internal.serialization.SerializationService;
import com.hazelcast.spi.impl.NodeEngine;
import com.hazelcast.topic.ReliableMessageListener;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.Executor;

public class ReliableMessageRunner<E> extends MessageRunner<E> {

    private final ClusterService clusterService;
    private final ReliableTopicProxy<E> proxy;

    private final NodeEngine nodeEngine;
    private final @Nullable String namespace;

    ReliableMessageRunner(UUID id, ReliableMessageListener<E> listener,
                          SerializationService serializationService,
                          Executor executor, ILogger logger, ClusterService clusterService,
                          ReliableTopicProxy<E> proxy) {
        super(id, listener, proxy.ringbuffer, proxy.getName(), proxy.topicConfig.getReadBatchSize(),
                serializationService, executor, proxy.runnersMap, logger);
        this.clusterService = clusterService;
        this.proxy = proxy;
        this.nodeEngine = proxy.getNodeEngine();
        this.namespace = ReliableTopicService.lookupNamespace(nodeEngine, proxy.getName());
    }

    @Override
    protected void runWithNamespaceAwareness(Runnable runnable) {
        NamespaceUtil.runWithNamespace(nodeEngine, namespace, runnable);
    }

    @Override
    protected Member getMember(ReliableTopicMessage m) {
        return clusterService.getMember(m.getPublisherAddress());
    }

    @Override
    protected Throwable adjustThrowable(Throwable t) {
        return t;
    }


}
