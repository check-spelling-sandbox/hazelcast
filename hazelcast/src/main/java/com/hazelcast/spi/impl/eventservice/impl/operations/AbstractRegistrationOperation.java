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

package com.hazelcast.spi.impl.eventservice.impl.operations;

import com.hazelcast.internal.cluster.ClusterService;
import com.hazelcast.internal.cluster.impl.ClusterTopologyChangedException;
import com.hazelcast.internal.util.executor.StripedRunnable;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.hazelcast.nio.serialization.impl.Versioned;
import com.hazelcast.spi.impl.AllowedDuringPassiveState;
import com.hazelcast.spi.impl.SpiDataSerializerHook;
import com.hazelcast.spi.impl.eventservice.impl.EventServiceImpl;
import com.hazelcast.spi.impl.operationservice.ExceptionAction;
import com.hazelcast.spi.impl.operationservice.Operation;
import com.hazelcast.spi.impl.operationservice.SelfResponseOperation;

import java.io.IOException;

import static com.hazelcast.internal.cluster.Versions.V5_3;
import static java.lang.String.format;

abstract class AbstractRegistrationOperation extends Operation
        implements AllowedDuringPassiveState, SelfResponseOperation, IdentifiedDataSerializable, Versioned {

    private int memberListVersion = -1;
    private int orderKey = -1;

    AbstractRegistrationOperation() {
    }

    AbstractRegistrationOperation(int memberListVersion, int orderKey) {
        this.memberListVersion = memberListVersion;
        this.orderKey = orderKey;
    }

    @Override
    public final void run() {
        if (orderKey == -1) {
            // run synchronously
            doRun();
        } else {
            // run asynchronously on executor dedicated for the key to preserve order
            // and wait for the execution to finish
            EventServiceImpl eventService = (EventServiceImpl) getNodeEngine().getEventService();

            eventService.executeEventCallback(new StripedRunnable() {
                @Override
                public void run() {
                    try {
                        doRun();
                    } catch (Throwable e) {
                        logError(e);
                        sendResponse(e);
                    }
                }

                @Override
                public int getKey() {
                    return orderKey;
                }
            }, true);
        }
    }

    private void doRun() {
        runInternal();
        checkMemberListVersion();
        sendResponse(null);
    }

    @Override
    public final boolean returnsResponse() {
        return false;
    }

    @Override
    public final Object getResponse() {
        throw new UnsupportedOperationException();
    }

    protected abstract void runInternal();

    private void checkMemberListVersion() {
        ClusterService clusterService = getNodeEngine().getClusterService();
        if (clusterService.isMaster()) {
            int currentMemberListVersion = clusterService.getMemberListVersion();
            if (currentMemberListVersion != memberListVersion) {
                throw new ClusterTopologyChangedException(
                        format("Current member list version %d does not match expected %d", currentMemberListVersion,
                                memberListVersion));
            }
        }
    }

    @Override
    protected final void writeInternal(ObjectDataOutput out) throws IOException {
        out.writeInt(memberListVersion);
        writeInternalImpl(out);
        if (out.getVersion().isGreaterOrEqual(V5_3)) {
            out.writeInt(orderKey);
        }
    }

    protected abstract void writeInternalImpl(ObjectDataOutput out) throws IOException;

    @Override
    protected final void readInternal(ObjectDataInput in) throws IOException {
        memberListVersion = in.readInt();
        readInternalImpl(in);
        if (in.getVersion().isGreaterOrEqual(V5_3)) {
            orderKey = in.readInt();
        }
    }

    protected abstract void readInternalImpl(ObjectDataInput in) throws IOException;

    @Override
    public ExceptionAction onInvocationException(Throwable throwable) {
        return (throwable instanceof ClusterTopologyChangedException)
                ? ExceptionAction.THROW_EXCEPTION
                : super.onInvocationException(throwable);
    }

    @Override
    public int getFactoryId() {
        return SpiDataSerializerHook.F_ID;
    }
}
