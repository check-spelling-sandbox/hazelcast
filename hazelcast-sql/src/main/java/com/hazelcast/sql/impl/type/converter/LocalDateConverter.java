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
import com.hazelcast.sql.impl.type.QueryDataTypeFamily;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * Converter for {@link LocalDate} type.
 */
@SerializableByConvention
public final class LocalDateConverter extends AbstractTemporalConverter {

    public static final LocalDateConverter INSTANCE = new LocalDateConverter();

    private LocalDateConverter() {
        super(ID_LOCAL_DATE, QueryDataTypeFamily.DATE);
    }

    @Override
    public Class<?> getValueClass() {
        return LocalDate.class;
    }

    @Override
    public String asVarchar(Object val) {
        return cast(val).toString();
    }

    @Override
    public LocalDate asDate(Object val) {
        return cast(val);
    }

    @Override
    public LocalDateTime asTimestamp(Object val) {
        LocalDate date = cast(val);

        return dateToTimestamp(date);
    }

    @Override
    public OffsetDateTime asTimestampWithTimezone(Object val) {
        LocalDate date = cast(val);

        LocalDateTime timestamp = dateToTimestamp(date);

        return timestampToTimestampWithTimezone(timestamp);
    }

    @Override
    public Object convertToSelf(Converter valConverter, Object val) {
        return valConverter.asDate(val);
    }

    private LocalDate cast(Object val) {
        return ((LocalDate) val);
    }
}
