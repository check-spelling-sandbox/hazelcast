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

package com.hazelcast.client.impl.protocol.codec;

import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.Generated;
import com.hazelcast.client.impl.protocol.codec.builtin.*;
import com.hazelcast.client.impl.protocol.codec.custom.*;

import javax.annotation.Nullable;

import static com.hazelcast.client.impl.protocol.ClientMessage.*;
import static com.hazelcast.client.impl.protocol.codec.builtin.FixedSizeTypesCodec.*;

/*
 * This file is auto-generated by the Hazelcast Client Protocol Code Generator.
 * To change this file, edit the templates or the protocol
 * definitions on the https://github.com/hazelcast/hazelcast-client-protocol
 * and regenerate it.
 */

/**
 * Adds a new vector collection configuration to a running cluster.
 */
@SuppressWarnings("unused")
@Generated("d43f7b6044aeff86e4d9eba48be499cb")
public final class DynamicConfigAddVectorCollectionConfigCodec {
    //hex: 0x1B1400
    public static final int REQUEST_MESSAGE_TYPE = 1774592;
    //hex: 0x1B1401
    public static final int RESPONSE_MESSAGE_TYPE = 1774593;
    private static final int REQUEST_BACKUP_COUNT_FIELD_OFFSET = PARTITION_ID_FIELD_OFFSET + INT_SIZE_IN_BYTES;
    private static final int REQUEST_ASYNC_BACKUP_COUNT_FIELD_OFFSET = REQUEST_BACKUP_COUNT_FIELD_OFFSET + INT_SIZE_IN_BYTES;
    private static final int REQUEST_MERGE_BATCH_SIZE_FIELD_OFFSET = REQUEST_ASYNC_BACKUP_COUNT_FIELD_OFFSET + INT_SIZE_IN_BYTES;
    private static final int REQUEST_INITIAL_FRAME_SIZE = REQUEST_MERGE_BATCH_SIZE_FIELD_OFFSET + INT_SIZE_IN_BYTES;
    private static final int RESPONSE_INITIAL_FRAME_SIZE = RESPONSE_BACKUP_ACKS_FIELD_OFFSET + BYTE_SIZE_IN_BYTES;

    private DynamicConfigAddVectorCollectionConfigCodec() {
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings({"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
    public static class RequestParameters {

        /**
         * vector collection name
         */
        public java.lang.String name;

        /**
         * vector index configurations
         */
        public java.util.List<com.hazelcast.config.vector.VectorIndexConfig> indexConfigs;

        /**
         * number of synchronous backups
         */
        public int backupCount;

        /**
         * number of asynchronous backups
         */
        public int asyncBackupCount;

        /**
         * Name of an existing configured split brain protection to be used to determine the minimum number of members
         * required in the cluster for the VectorCollection to remain functional. When {@code null}, split brain protection
         * does not apply to this VectorCollection's operations.
         */
        public @Nullable java.lang.String splitBrainProtectionName;

        /**
         * Name of a class implementing SplitBrainMergePolicy that handles merging of values for this VectorCollection
         * while recovering from network partitioning.
         */
        public java.lang.String mergePolicy;

        /**
         * Number of entries to be sent in a merge operation.
         */
        public int mergeBatchSize;

        /**
         * Name of the User Code Namespace applied to this instance.
         */
        public @Nullable java.lang.String userCodeNamespace;

        /**
         * True if the backupCount is received from the client, false otherwise.
         * If this is false, backupCount has the default value for its type.
         */
        public boolean isBackupCountExists;

        /**
         * True if the asyncBackupCount is received from the client, false otherwise.
         * If this is false, asyncBackupCount has the default value for its type.
         */
        public boolean isAsyncBackupCountExists;

        /**
         * True if the splitBrainProtectionName is received from the client, false otherwise.
         * If this is false, splitBrainProtectionName has the default value for its type.
         */
        public boolean isSplitBrainProtectionNameExists;

        /**
         * True if the mergePolicy is received from the client, false otherwise.
         * If this is false, mergePolicy has the default value for its type.
         */
        public boolean isMergePolicyExists;

        /**
         * True if the mergeBatchSize is received from the client, false otherwise.
         * If this is false, mergeBatchSize has the default value for its type.
         */
        public boolean isMergeBatchSizeExists;

        /**
         * True if the userCodeNamespace is received from the client, false otherwise.
         * If this is false, userCodeNamespace has the default value for its type.
         */
        public boolean isUserCodeNamespaceExists;
    }

    public static ClientMessage encodeRequest(java.lang.String name, java.util.List<com.hazelcast.config.vector.VectorIndexConfig> indexConfigs, int backupCount, int asyncBackupCount, @Nullable java.lang.String splitBrainProtectionName, java.lang.String mergePolicy, int mergeBatchSize, @Nullable java.lang.String userCodeNamespace) {
        ClientMessage clientMessage = ClientMessage.createForEncode();
        clientMessage.setRetryable(false);
        clientMessage.setOperationName("DynamicConfig.AddVectorCollectionConfig");
        ClientMessage.Frame initialFrame = new ClientMessage.Frame(new byte[REQUEST_INITIAL_FRAME_SIZE], UNFRAGMENTED_MESSAGE);
        encodeInt(initialFrame.content, TYPE_FIELD_OFFSET, REQUEST_MESSAGE_TYPE);
        encodeInt(initialFrame.content, PARTITION_ID_FIELD_OFFSET, -1);
        encodeInt(initialFrame.content, REQUEST_BACKUP_COUNT_FIELD_OFFSET, backupCount);
        encodeInt(initialFrame.content, REQUEST_ASYNC_BACKUP_COUNT_FIELD_OFFSET, asyncBackupCount);
        encodeInt(initialFrame.content, REQUEST_MERGE_BATCH_SIZE_FIELD_OFFSET, mergeBatchSize);
        clientMessage.add(initialFrame);
        StringCodec.encode(clientMessage, name);
        ListMultiFrameCodec.encode(clientMessage, indexConfigs, VectorIndexConfigCodec::encode);
        CodecUtil.encodeNullable(clientMessage, splitBrainProtectionName, StringCodec::encode);
        StringCodec.encode(clientMessage, mergePolicy);
        CodecUtil.encodeNullable(clientMessage, userCodeNamespace, StringCodec::encode);
        return clientMessage;
    }

    public static DynamicConfigAddVectorCollectionConfigCodec.RequestParameters decodeRequest(ClientMessage clientMessage) {
        ClientMessage.ForwardFrameIterator iterator = clientMessage.frameIterator();
        RequestParameters request = new RequestParameters();
        ClientMessage.Frame initialFrame = iterator.next();
        if (initialFrame.content.length >= REQUEST_BACKUP_COUNT_FIELD_OFFSET + INT_SIZE_IN_BYTES) {
            request.backupCount = decodeInt(initialFrame.content, REQUEST_BACKUP_COUNT_FIELD_OFFSET);
            request.isBackupCountExists = true;
        } else {
            request.isBackupCountExists = false;
        }
        if (initialFrame.content.length >= REQUEST_ASYNC_BACKUP_COUNT_FIELD_OFFSET + INT_SIZE_IN_BYTES) {
            request.asyncBackupCount = decodeInt(initialFrame.content, REQUEST_ASYNC_BACKUP_COUNT_FIELD_OFFSET);
            request.isAsyncBackupCountExists = true;
        } else {
            request.isAsyncBackupCountExists = false;
        }
        if (initialFrame.content.length >= REQUEST_MERGE_BATCH_SIZE_FIELD_OFFSET + INT_SIZE_IN_BYTES) {
            request.mergeBatchSize = decodeInt(initialFrame.content, REQUEST_MERGE_BATCH_SIZE_FIELD_OFFSET);
            request.isMergeBatchSizeExists = true;
        } else {
            request.isMergeBatchSizeExists = false;
        }
        request.name = StringCodec.decode(iterator);
        request.indexConfigs = ListMultiFrameCodec.decode(iterator, VectorIndexConfigCodec::decode);
        if (iterator.hasNext()) {
            request.splitBrainProtectionName = CodecUtil.decodeNullable(iterator, StringCodec::decode);
            request.isSplitBrainProtectionNameExists = true;
        } else {
            request.isSplitBrainProtectionNameExists = false;
        }
        if (iterator.hasNext()) {
            request.mergePolicy = StringCodec.decode(iterator);
            request.isMergePolicyExists = true;
        } else {
            request.isMergePolicyExists = false;
        }
        if (iterator.hasNext()) {
            request.userCodeNamespace = CodecUtil.decodeNullable(iterator, StringCodec::decode);
            request.isUserCodeNamespaceExists = true;
        } else {
            request.isUserCodeNamespaceExists = false;
        }
        return request;
    }

    public static ClientMessage encodeResponse() {
        ClientMessage clientMessage = ClientMessage.createForEncode();
        ClientMessage.Frame initialFrame = new ClientMessage.Frame(new byte[RESPONSE_INITIAL_FRAME_SIZE], UNFRAGMENTED_MESSAGE);
        encodeInt(initialFrame.content, TYPE_FIELD_OFFSET, RESPONSE_MESSAGE_TYPE);
        clientMessage.add(initialFrame);

        return clientMessage;
    }
}
