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

package com.hazelcast.multimap.impl.txn;

import com.hazelcast.internal.util.UUIDSerializationUtil;
import com.hazelcast.multimap.impl.MultiMapContainer;
import com.hazelcast.multimap.impl.MultiMapDataSerializerHook;
import com.hazelcast.multimap.impl.operations.AbstractKeyBasedMultiMapOperation;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.spi.impl.operationservice.BackupOperation;
import com.hazelcast.transaction.TransactionException;

import java.io.IOException;
import java.util.UUID;

public class TxnRollbackBackupOperation extends AbstractKeyBasedMultiMapOperation implements BackupOperation {

    private UUID caller;

    public TxnRollbackBackupOperation() {
    }

    public TxnRollbackBackupOperation(String name, Data dataKey, UUID caller, long threadId) {
        super(name, dataKey);
        this.caller = caller;
        this.threadId = threadId;
    }

    @Override
    public void run() throws Exception {
        MultiMapContainer container = getOrCreateContainerWithoutAccess();
        if (container.isLocked(dataKey) && !container.unlock(dataKey, caller, threadId, getCallId())) {
            throw new TransactionException("Lock is not owned by the transaction! -> " + container.getLockOwnerInfo(dataKey));
        }
    }

    @Override
    protected void writeInternal(ObjectDataOutput out) throws IOException {
        super.writeInternal(out);
        UUIDSerializationUtil.writeUUID(out, caller);
    }

    @Override
    protected void readInternal(ObjectDataInput in) throws IOException {
        super.readInternal(in);
        caller = UUIDSerializationUtil.readUUID(in);
    }

    @Override
    public int getClassId() {
        return MultiMapDataSerializerHook.TXN_ROLLBACK_BACKUP;
    }
}
