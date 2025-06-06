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

package com.hazelcast.collection.impl.list.operations;

import com.hazelcast.collection.impl.collection.CollectionDataSerializerHook;
import com.hazelcast.collection.impl.collection.operations.CollectionOperation;
import com.hazelcast.collection.impl.list.ListContainer;
import com.hazelcast.internal.nio.IOUtil;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.spi.impl.operationservice.ReadonlyOperation;

import java.io.IOException;

public class ListIndexOfOperation extends CollectionOperation implements ReadonlyOperation {

    private boolean last;
    private Data value;

    public ListIndexOfOperation() {
    }

    public ListIndexOfOperation(String name, boolean last, Data value) {
        super(name);
        this.last = last;
        this.value = value;
    }

    @Override
    public void run() throws Exception {
        ListContainer listContainer = getOrCreateListContainer();
        response = listContainer.indexOf(last, value);
    }

    @Override
    public int getClassId() {
        return CollectionDataSerializerHook.LIST_INDEX_OF;
    }

    @Override
    protected void writeInternal(ObjectDataOutput out) throws IOException {
        super.writeInternal(out);
        out.writeBoolean(last);
        IOUtil.writeData(out, value);
    }

    @Override
    protected void readInternal(ObjectDataInput in) throws IOException {
        super.readInternal(in);
        last = in.readBoolean();
        value = IOUtil.readData(in);
    }
}
