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

package com.hazelcast.cardinality.impl.operations;

import com.hazelcast.cardinality.impl.CardinalityEstimatorDataSerializerHook;
import com.hazelcast.spi.impl.operationservice.ReadonlyOperation;

public class EstimateOperation
        extends AbstractCardinalityEstimatorOperation
        implements ReadonlyOperation {

    private long estimate;

    public EstimateOperation() {
    }

    public EstimateOperation(String name) {
        super(name);
    }

    @Override
    public int getClassId() {
        return CardinalityEstimatorDataSerializerHook.ESTIMATE;
    }

    @Override
    public void run() throws Exception {
        estimate = getCardinalityEstimatorContainer().estimate();
    }

    @Override
    public Object getResponse() {
        return estimate;
    }
}
