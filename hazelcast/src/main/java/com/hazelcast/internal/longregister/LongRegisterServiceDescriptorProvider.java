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

package com.hazelcast.internal.longregister;

import com.hazelcast.spi.impl.NodeEngine;
import com.hazelcast.spi.impl.servicemanager.ServiceDescriptor;
import com.hazelcast.spi.impl.servicemanager.ServiceDescriptorProvider;

public class LongRegisterServiceDescriptorProvider implements ServiceDescriptorProvider {

    @Override
    public ServiceDescriptor[] createServiceDescriptors() {
        return new ServiceDescriptor[] {new LongRegisterServiceDescriptor()};
    }

    private static class LongRegisterServiceDescriptor implements ServiceDescriptor {
        @Override
        public String getServiceName() {
            return LongRegisterService.SERVICE_NAME;
        }

        @Override
        public Object getService(NodeEngine nodeEngine) {
            return new LongRegisterService(nodeEngine);
        }
    }
}
