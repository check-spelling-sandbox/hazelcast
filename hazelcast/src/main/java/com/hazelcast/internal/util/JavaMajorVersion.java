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

package com.hazelcast.internal.util;

import com.hazelcast.internal.util.JavaVersion.FutureJavaVersion;
import com.hazelcast.internal.util.JavaVersion.UnknownVersion;

/**
 * Interface used by {@link JavaVersion}, {@link UnknownVersion} and {@link
 * FutureJavaVersion}. This interface is needed only to do version comparison
 * safely on runtime environments with versions not listed in {@link JavaVersion}.
 */
public interface JavaMajorVersion {
    /**
     * Returns the major version or null if the version is unknown.
     */
    Integer getMajorVersion();
}
