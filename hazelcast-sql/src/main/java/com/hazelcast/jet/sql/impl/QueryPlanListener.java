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

package com.hazelcast.jet.sql.impl;

import com.hazelcast.jet.sql.impl.opt.physical.PhysicalRel;

/**
 * Listener which hooks finally optimized query plan by calling
 * {@link QueryPlanListener#onQueryPlanBuilt} in {@link CalciteSqlOptimizerImpl#optimize}.
 */
public interface QueryPlanListener {
    void onQueryPlanBuilt(PhysicalRel rootRel);
}
