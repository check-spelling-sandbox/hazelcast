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

package com.hazelcast.aggregation.impl;

import com.hazelcast.aggregation.Aggregator;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

import java.io.IOException;
import java.util.Objects;

public final class FloatingPointSumAggregator<I> extends AbstractAggregator<I, Number, Double>
        implements IdentifiedDataSerializable {

    private double sum;

    public FloatingPointSumAggregator() {
        super();
    }

    public FloatingPointSumAggregator(String attributePath) {
        super(attributePath);
    }

    @Override
    public void accumulateExtracted(I entry, Number value) {
        sum += value.doubleValue();
    }

    @Override
    public void combine(Aggregator aggregator) {
        FloatingPointSumAggregator longSumAggregator = (FloatingPointSumAggregator) aggregator;
        this.sum += longSumAggregator.sum;
    }

    @Override
    public Double aggregate() {
        return sum;
    }

    @Override
    public int getFactoryId() {
        return AggregatorDataSerializerHook.F_ID;
    }

    @Override
    public int getClassId() {
        return AggregatorDataSerializerHook.FLOATING_POINT_SUM;
    }

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
        out.writeString(attributePath);
        out.writeDouble(sum);
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
        this.attributePath = in.readString();
        this.sum = in.readDouble();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        FloatingPointSumAggregator<?> that = (FloatingPointSumAggregator<?>) o;
        return Double.compare(that.sum, sum) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sum);
    }
}
