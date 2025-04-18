/*
 * Copyright 2025 Hazelcast Inc.
 *
 * Licensed under the Hazelcast Community License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://hazelcast.com/hazelcast-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.sql.impl.type.converter;

import com.hazelcast.internal.serialization.SerializableByConvention;

import java.time.OffsetDateTime;

/**
 * Converter for {@link OffsetDateTime} type.
 */
@SerializableByConvention
public final class OffsetDateTimeConverter extends AbstractTimestampWithTimezoneConverter {

    public static final OffsetDateTimeConverter INSTANCE = new OffsetDateTimeConverter();

    private OffsetDateTimeConverter() {
        super(ID_OFFSET_DATE_TIME);
    }

    @Override
    public Class<?> getValueClass() {
        return OffsetDateTime.class;
    }

    @Override
    public OffsetDateTime asTimestampWithTimezone(Object val) {
        return (OffsetDateTime) val;
    }
}
