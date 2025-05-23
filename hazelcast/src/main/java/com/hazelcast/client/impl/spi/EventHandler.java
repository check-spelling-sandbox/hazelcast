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

import com.hazelcast.internal.nio.Connection;

/**
 * Base interface for all client {@link EventHandler}s.
 *
 * @param <E> event type
 */
public interface EventHandler<E> {

    void handle(E event);

    /**
     * This method is called before registration request is sent to node.
     * <p>
     * Note that this method will also be called while first registered node is dead
     * and re-registering to a second node.
     */
    default void beforeListenerRegister(Connection connection) {
    }

    /**
     * This method is called when registration request response is successfully returned from node.
     * <p>
     * Note that this method will also be called while first registered node is dead
     * and re-registering to a second node.
     */
    default void onListenerRegister(Connection connection) {
    }

}
