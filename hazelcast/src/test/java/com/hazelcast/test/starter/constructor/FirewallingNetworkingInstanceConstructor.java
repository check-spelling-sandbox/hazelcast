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

package com.hazelcast.test.starter.constructor;

import com.hazelcast.internal.server.Server;
import com.hazelcast.test.starter.HazelcastStarterConstructor;

import java.lang.reflect.Constructor;
import java.util.Set;

import static com.hazelcast.test.starter.HazelcastProxyFactory.proxyArgumentsIfNeeded;
import static com.hazelcast.test.starter.ReflectionUtils.getFieldValueReflectively;

@HazelcastStarterConstructor(classNames = {"com.hazelcast.internal.nio.tcp.FirewallingNetworkingService"})
public class FirewallingNetworkingInstanceConstructor extends AbstractStarterObjectConstructor {

    public FirewallingNetworkingInstanceConstructor(Class<?> targetClass) {
        super(targetClass);
    }

    @Override
    Object createNew0(Object delegate) throws Exception {
        // obtain reference to constructor FirewallingNetworkingService(ConnectionManager, Set<Address>)
        Constructor<?> constructor = targetClass.getDeclaredConstructor(Server.class, Set.class);

        Object networkingService = getFieldValueReflectively(delegate, "delegate");
        Set blockedAddresses = (Set) getFieldValueReflectively(delegate, "blockedAddresses");
        Object[] args = new Object[]{networkingService, blockedAddresses};

        Object[] proxiedArgs = proxyArgumentsIfNeeded(args, targetClass.getClassLoader());
        return constructor.newInstance(proxiedArgs);
    }
}
