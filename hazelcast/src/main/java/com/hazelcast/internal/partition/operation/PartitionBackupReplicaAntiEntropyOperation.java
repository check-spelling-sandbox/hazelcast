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

package com.hazelcast.internal.partition.operation;

import com.hazelcast.cluster.Address;
import com.hazelcast.internal.partition.InternalPartitionService;
import com.hazelcast.internal.partition.PartitionReplica;
import com.hazelcast.internal.partition.ReplicaErrorLogger;
import com.hazelcast.internal.partition.impl.InternalPartitionImpl;
import com.hazelcast.internal.partition.impl.InternalPartitionServiceImpl;
import com.hazelcast.internal.partition.impl.PartitionReplicaManager;
import com.hazelcast.internal.services.ServiceNamespace;
import com.hazelcast.logging.ILogger;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.spi.impl.AllowedDuringPassiveState;
import com.hazelcast.spi.impl.NodeEngine;
import com.hazelcast.spi.impl.operationservice.PartitionAwareOperation;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.hazelcast.internal.partition.impl.PartitionDataSerializerHook.PARTITION_BACKUP_REPLICA_ANTI_ENTROPY;
import static com.hazelcast.internal.partition.impl.PartitionReplicaManager.REQUIRES_SYNC;

// should not be an urgent operation. required to be in order with backup operations on target node
public final class PartitionBackupReplicaAntiEntropyOperation
        extends AbstractPartitionOperation
        implements PartitionAwareOperation, AllowedDuringPassiveState {

    // Only reason of CHM usage is not to get
    // ConcurrentModificationException from
    // PartitionBackupReplicaAntiEntropyOperation#toString method
    private ConcurrentMap<ServiceNamespace, Long> versions;
    private boolean returnResponse;
    private boolean response = true;

    public PartitionBackupReplicaAntiEntropyOperation() {
    }

    public PartitionBackupReplicaAntiEntropyOperation(ConcurrentMap<ServiceNamespace, Long> versions,
                                                      boolean returnResponse) {
        this.versions = versions;
        this.returnResponse = returnResponse;
    }

    @Override
    public void run() {
        if (!isNodeStartCompleted()) {
            response = false;
            return;
        }

        InternalPartitionServiceImpl partitionService = getService();
        int partitionId = getPartitionId();
        int replicaIndex = getReplicaIndex();

        InternalPartitionImpl partition = partitionService.getPartitionStateManager().getPartitionImpl(partitionId);
        int currentReplicaIndex = partition.getReplicaIndex(PartitionReplica.from(getNodeEngine().getLocalMember()));

        ILogger logger = getLogger();
        if (replicaIndex != currentReplicaIndex) {
            logger.fine("Anti-entropy operation for partitionId=" + getPartitionId() + ", replicaIndex=" + getReplicaIndex()
                    + " is received, but this node is not the expected backup replica!"
                    + " Current replicaIndex=" + currentReplicaIndex);
            response = false;
            return;
        }

        Address ownerAddress = partition.getOwnerOrNull();
        if (!getCallerAddress().equals(ownerAddress)) {
            logger.fine("Anti-entropy operation for partitionId=" + getPartitionId() + ", replicaIndex=" + getReplicaIndex()
                    + " is received from " + getCallerAddress() + ", but it's not the known primary replica owner: "
                    + ownerAddress);
            response = false;
            return;
        }

        PartitionReplicaManager replicaManager = partitionService.getReplicaManager();
        replicaManager.retainNamespaces(partitionId, versions.keySet());

        if (logger.isFinestEnabled()) {
            logger.finest("Retained namespaces for partitionId=" + partitionId + ", replicaIndex=" + replicaIndex
                    + ". Namespaces=" + replicaManager.getNamespaces(partitionId));
        }

        Iterator<Map.Entry<ServiceNamespace, Long>> iter = versions.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<ServiceNamespace, Long> entry = iter.next();
            ServiceNamespace ns = entry.getKey();
            long primaryVersion = entry.getValue();

            long[] currentVersions = replicaManager.getPartitionReplicaVersions(partitionId, ns);
            long currentVersion = currentVersions[replicaIndex - 1];

            if (replicaManager.isPartitionReplicaVersionDirty(partitionId, ns) || currentVersion != primaryVersion
                || currentVersion == REQUIRES_SYNC) {
                logBackupVersionMismatch(ns, currentVersion, primaryVersion);
                continue;
            }
            iter.remove();
        }

        if (!versions.isEmpty()) {
            replicaManager.triggerPartitionReplicaSync(partitionId, versions.keySet(), replicaIndex);
            response = false;
        }
    }

    private boolean isNodeStartCompleted() {
        NodeEngine nodeEngine = getNodeEngine();
        boolean startCompleted = nodeEngine.getNode().getNodeExtension().isStartCompleted();
        if (!startCompleted) {
            ILogger logger = getLogger();
            if (logger.isFinestEnabled()) {
                logger.finest("Anti-entropy operation for partitionId=" + getPartitionId()
                        + ", replicaIndex=" + getReplicaIndex() + " is received before startup is completed.");
            }
        }
        return startCompleted;
    }

    private void logBackupVersionMismatch(ServiceNamespace ns, long currentVersion, long primaryVersion) {
        ILogger logger = getLogger();
        if (logger.isFinestEnabled()) {
            logger.finest("partitionId=" + getPartitionId() + ", replicaIndex=" + getReplicaIndex()
                    + ", ns=" + ns + " version is not matching to version of the owner or replica is marked as dirty! "
                    + " Expected-version=" + primaryVersion + ", Current-version=" + currentVersion);
        }
    }

    @Override
    public boolean returnsResponse() {
        return returnResponse;
    }

    @Override
    public Object getResponse() {
        return response;
    }

    @Override
    public boolean validatesTarget() {
        return false;
    }

    @Override
    public String getServiceName() {
        return InternalPartitionService.SERVICE_NAME;
    }

    @Override
    public void logError(Throwable e) {
        ReplicaErrorLogger.log(e, getLogger());
    }

    @Override
    protected void writeInternal(ObjectDataOutput out) throws IOException {
        out.writeInt(versions.size());
        for (Map.Entry<ServiceNamespace, Long> entry : versions.entrySet()) {
            out.writeObject(entry.getKey());
            out.writeLong(entry.getValue());
        }
        out.writeBoolean(returnResponse);
    }

    @Override
    protected void readInternal(ObjectDataInput in) throws IOException {
        int len = in.readInt();
        ConcurrentMap<ServiceNamespace, Long> versionsByNamespace = new ConcurrentHashMap<>(len);
        for (int i = 0; i < len; i++) {
            ServiceNamespace ns = in.readObject();
            long v = in.readLong();
            versionsByNamespace.put(ns, v);
        }

        versions = versionsByNamespace;
        returnResponse = in.readBoolean();
    }

    @Override
    protected void toString(StringBuilder sb) {
        super.toString(sb);
        sb.append(", versions=").append(versions);
    }

    @Override
    public int getClassId() {
        return PARTITION_BACKUP_REPLICA_ANTI_ENTROPY;
    }
}
