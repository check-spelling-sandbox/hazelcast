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

package com.hazelcast.client.impl.spi;

import com.hazelcast.client.LoadBalancer;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.impl.connection.ClientConnection;
import com.hazelcast.client.config.RoutingMode;
import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.spi.impl.ClientInvocation;
import com.hazelcast.cluster.Member;
import com.hazelcast.internal.nio.Connection;
import com.hazelcast.internal.nio.ConnectionListener;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Invocation service for Hazelcast clients.
 * <p>
 * Allows remote invocations on different targets like {@link ClientConnection},
 * partition owners or {@link Member} based targets.
 */
public interface ClientInvocationService {

    /**
     * @param invocation to be invoked
     * @param connection to be invoked on
     * @return true if successfully send to given connection, false otherwise
     */
    boolean invokeOnConnection(ClientInvocation invocation, ClientConnection connection);

    /**
     * @param invocation  to be invoked
     * @param partitionId partition id that invocation should go to
     * @return true if successfully send to partition owner, false otherwise
     */
    boolean invokeOnPartitionOwner(ClientInvocation invocation,
                                   int partitionId);

    /**
     * @param invocation to be invoked
     * @param uuid       member uuid that invocation should go to
     * @return true if successfully send the member with given uuid, false otherwise
     */
    boolean invokeOnTarget(ClientInvocation invocation,
                           UUID uuid);

    /**
     * Behaviour of this method varies for {@link RoutingMode#SINGLE_MEMBER}, {@link RoutingMode#MULTI_MEMBER},
     * and {@link RoutingMode#ALL_MEMBERS} routing clients.
     * {@link RoutingMode#SINGLE_MEMBER} routing invokes on only available connection
     * {@link RoutingMode#MULTI_MEMBER} and {@link RoutingMode#ALL_MEMBERS} routing randomly picks a connection
     * to invoke on via {@link LoadBalancer}.
     *
     * @param invocation to be invoked
     * @return true if successfully send, false otherwise
     */
    boolean invoke(ClientInvocation invocation);

    boolean isRedoOperation();

    /**
     * @see ClientConfig#isBackupAckToClientEnabled()
     */
    boolean isBackupAckToClientEnabled();

    Consumer<ClientMessage> getResponseHandler();

    /**
     * This will be called on each connection close.
     * Note that is different from {@link ConnectionListener#connectionRemoved(Connection)} where `connectionRemoved`
     * means an authenticated connection is disconnected
     *
     * @param connection closed connection
     */
    void onConnectionClose(ClientConnection connection);

    /**
     * @param connection provided connection to query if it has any associated invocation
     * @return {@code true} if there is an ongoing invocation that
     * still uses the provided connection, {@code false} otherwise
     */
    boolean isConnectionInUse(@Nonnull ClientConnection connection);
}
