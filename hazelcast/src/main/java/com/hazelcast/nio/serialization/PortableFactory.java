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

package com.hazelcast.nio.serialization;

/**
 * PortableFactory is used to create Portable instances during de-serialization.
 *
 * @see com.hazelcast.nio.serialization.Portable
 * @see com.hazelcast.nio.serialization.VersionedPortable
 *
 * @deprecated Portable Serialization has been deprecated. We recommend you use Compact Serialization as Portable Serialization
 * will be removed as of version 7.0.
 */
@Deprecated(since = "5.4", forRemoval = true)
@FunctionalInterface
public interface PortableFactory {

    /**
     * Creates a Portable instance using the given class ID
     *
     * @param classId portable class ID
     * @return portable instance or null if class ID is not known by this factory
     */
    Portable create(int classId);
}
