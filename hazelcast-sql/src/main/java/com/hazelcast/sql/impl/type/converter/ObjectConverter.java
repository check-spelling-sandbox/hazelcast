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

import com.hazelcast.core.HazelcastJsonValue;
import com.hazelcast.internal.serialization.SerializableByConvention;
import com.hazelcast.sql.impl.expression.RowValue;
import com.hazelcast.sql.impl.type.QueryDataTypeFamily;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Converter for arbitrary objects which do not have a more specific converter.
 */
@SerializableByConvention
public final class ObjectConverter extends Converter {

    public static final ObjectConverter INSTANCE = new ObjectConverter();

    private ObjectConverter() {
        super(ID_OBJECT, QueryDataTypeFamily.OBJECT);
    }

    @Override
    public Class<?> getValueClass() {
        return Object.class;
    }

    @Override
    public boolean asBoolean(Object val) {
        return resolveConverter(val, QueryDataTypeFamily.BOOLEAN).asBoolean(val);
    }

    @Override
    public byte asTinyint(Object val) {
        return resolveConverter(val, QueryDataTypeFamily.TINYINT).asTinyint(val);
    }

    @Override
    public short asSmallint(Object val) {
        return resolveConverter(val, QueryDataTypeFamily.SMALLINT).asSmallint(val);
    }

    @Override
    public int asInt(Object val) {
        return resolveConverter(val, QueryDataTypeFamily.INTEGER).asInt(val);
    }

    @Override
    public long asBigint(Object val) {
        return resolveConverter(val, QueryDataTypeFamily.BIGINT).asBigint(val);
    }

    @Override
    public BigDecimal asDecimal(Object val) {
        return resolveConverter(val, QueryDataTypeFamily.DECIMAL).asDecimal(val);
    }

    @Override
    public float asReal(Object val) {
        return resolveConverter(val, QueryDataTypeFamily.REAL).asReal(val);
    }

    @Override
    public double asDouble(Object val) {
        return resolveConverter(val, QueryDataTypeFamily.DOUBLE).asDouble(val);
    }

    @Override
    public LocalDate asDate(Object val) {
        return resolveConverter(val, QueryDataTypeFamily.DATE).asDate(val);
    }

    @Override
    public LocalTime asTime(Object val) {
        return resolveConverter(val, QueryDataTypeFamily.TIME).asTime(val);
    }

    @Override
    public LocalDateTime asTimestamp(Object val) {
        return resolveConverter(val, QueryDataTypeFamily.TIMESTAMP).asTimestamp(val);
    }

    @Override
    public OffsetDateTime asTimestampWithTimezone(Object val) {
        return resolveConverter(val, QueryDataTypeFamily.TIMESTAMP_WITH_TIME_ZONE).asTimestampWithTimezone(val);
    }

    @Override
    public String asVarchar(Object val) {
        Converter converter = Converters.getConverter(val.getClass());

        if (converter == this) {
            return val.toString();
        } else {
            return converter.asVarchar(val);
        }
    }

    @Override
    public Object asObject(Object val) {
        Converter converter = Converters.getConverter(val.getClass());

        if (converter == this) {
            return val;
        } else {
            return converter.asObject(val);
        }
    }

    @Override
    public Map<?, ?> asMap(Object val) {
        return resolveConverter(val, QueryDataTypeFamily.MAP).asMap(val);
    }

    @Override
    public HazelcastJsonValue asJson(final Object val) {
        return resolveConverter(val, QueryDataTypeFamily.JSON).asJson(val);
    }

    @Override
    public RowValue asRow(Object val) {
        return resolveConverter(val, QueryDataTypeFamily.ROW).asRow(val);
    }

    @Override
    public Object convertToSelf(Converter valConverter, Object val) {
        return valConverter.asObject(val);
    }

    private Converter resolveConverter(Object val, QueryDataTypeFamily target) {
        Converter converter = Converters.getConverter(val.getClass());

        if (converter == this) {
            throw cannotConvertError(target);
        }

        return converter;
    }
}
