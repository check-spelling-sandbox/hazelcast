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

package com.hazelcast.jet.sql.impl.validate.operators.special;

import com.hazelcast.jet.sql.impl.validate.HazelcastCallBinding;
import com.hazelcast.jet.sql.impl.validate.operators.common.HazelcastSpecialOperator;
import org.apache.calcite.sql.SqlKind;

/**
 * Hazelcast implementation of CREATE DATA CONNECTION operator.
 */
public class HazelcastCreateDataConnectionOperator extends HazelcastSpecialOperator {
    public HazelcastCreateDataConnectionOperator() {
        super("CREATE DATA CONNECTION", SqlKind.OTHER_DDL);
    }

    @Override
    protected boolean checkOperandTypes(HazelcastCallBinding callBinding, boolean throwOnFailure) {
        throw new UnsupportedOperationException("Never called for CREATE DATA CONNECTION.");
    }
}
