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

package com.hazelcast.internal.config;

import com.hazelcast.config.DataConnectionConfig;

import javax.annotation.Nonnull;
import java.util.Properties;

public class DataConnectionConfigReadOnly extends DataConnectionConfig {

    public DataConnectionConfigReadOnly(DataConnectionConfig config) {
        super(config);
    }

    @Override
    public DataConnectionConfig setName(String name) {
        throw readOnly();
    }

    private RuntimeException readOnly() {
        return new UnsupportedOperationException("Config '" + getName() + "' is read-only");
    }

    @Override
    public DataConnectionConfig setType(@Nonnull String type) {
        throw readOnly();
    }

    @Override
    public DataConnectionConfig setShared(boolean shared) {
        throw readOnly();
    }

    @Override
    public DataConnectionConfig setProperties(Properties properties) {
        throw readOnly();
    }

    @Override
    public DataConnectionConfig setProperty(String key, String value) {
        throw readOnly();
    }
}
