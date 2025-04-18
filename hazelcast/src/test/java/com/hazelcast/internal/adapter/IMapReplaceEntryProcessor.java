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

package com.hazelcast.internal.adapter;

import com.hazelcast.map.EntryProcessor;

import java.io.Serial;
import java.util.Map;

public class IMapReplaceEntryProcessor implements EntryProcessor<Integer, String, String> {

    @Serial
    private static final long serialVersionUID = -4826323876651981295L;

    private final String oldString;
    private final String newString;

    public IMapReplaceEntryProcessor(String oldString, String newString) {
        this.oldString = oldString;
        this.newString = newString;
    }

    @Override
    public String process(Map.Entry<Integer, String> entry) {
        String value = entry.getValue();
        if (value == null) {
            return null;
        }

        String result = value.replace(oldString, newString);
        entry.setValue(result);
        return result;
    }

    @Override
    public EntryProcessor<Integer, String, String> getBackupProcessor() {
        return null;
    }
}
