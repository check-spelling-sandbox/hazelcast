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
 * This method returns a clone of the original value, so modifying the returned value does not change the actual
 * value in the map. You should put the modified value back to make changes visible to all nodes.
 */
@SuppressWarnings("unused")
@Generated("acdd4d8e68da52a3186a2061fc64dd7d")
public final class MapGetCodec {
    //hex: 0x010200
    public static final int REQUEST_MESSAGE_TYPE = 66048;
    //hex: 0x010201
    public static final int RESPONSE_MESSAGE_TYPE = 66049;
    private static final int REQUEST_THREAD_ID_FIELD_OFFSET = PARTITION_ID_FIELD_OFFSET + INT_SIZE_IN_BYTES;
    private static final int REQUEST_INITIAL_FRAME_SIZE = REQUEST_THREAD_ID_FIELD_OFFSET + LONG_SIZE_IN_BYTES;
    private static final int RESPONSE_INITIAL_FRAME_SIZE = RESPONSE_BACKUP_ACKS_FIELD_OFFSET + BYTE_SIZE_IN_BYTES;

    private MapGetCodec() {
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings({"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
    public static class RequestParameters {

        /**
         * Name of the map.
         */
        public java.lang.String name;

        /**
         * Key for the map entry.
         */
        public com.hazelcast.internal.serialization.Data key;

        /**
         * The id of the user thread performing the operation. It is used to guarantee that only the lock holder thread (if a lock exists on the entry) can perform the requested operation.
         */
        public long threadId;
    }

    public static ClientMessage encodeRequest(java.lang.String name, com.hazelcast.internal.serialization.Data key, long threadId) {
        ClientMessage clientMessage = ClientMessage.createForEncode();
        clientMessage.setContainsSerializedDataInRequest(true);
        clientMessage.setRetryable(true);
        clientMessage.setOperationName("Map.Get");
        ClientMessage.Frame initialFrame = new ClientMessage.Frame(new byte[REQUEST_INITIAL_FRAME_SIZE], UNFRAGMENTED_MESSAGE);
        encodeInt(initialFrame.content, TYPE_FIELD_OFFSET, REQUEST_MESSAGE_TYPE);
        encodeInt(initialFrame.content, PARTITION_ID_FIELD_OFFSET, -1);
        encodeLong(initialFrame.content, REQUEST_THREAD_ID_FIELD_OFFSET, threadId);
        clientMessage.add(initialFrame);
        StringCodec.encode(clientMessage, name);
        DataCodec.encode(clientMessage, key);
        return clientMessage;
    }

    public static MapGetCodec.RequestParameters decodeRequest(ClientMessage clientMessage) {
        ClientMessage.ForwardFrameIterator iterator = clientMessage.frameIterator();
        RequestParameters request = new RequestParameters();
        ClientMessage.Frame initialFrame = iterator.next();
        request.threadId = decodeLong(initialFrame.content, REQUEST_THREAD_ID_FIELD_OFFSET);
        request.name = StringCodec.decode(iterator);
        request.key = DataCodec.decode(iterator);
        return request;
    }

    public static ClientMessage encodeResponse(@Nullable com.hazelcast.internal.serialization.Data response) {
        ClientMessage clientMessage = ClientMessage.createForEncode();
        ClientMessage.Frame initialFrame = new ClientMessage.Frame(new byte[RESPONSE_INITIAL_FRAME_SIZE], UNFRAGMENTED_MESSAGE);
        encodeInt(initialFrame.content, TYPE_FIELD_OFFSET, RESPONSE_MESSAGE_TYPE);
        clientMessage.add(initialFrame);

        CodecUtil.encodeNullable(clientMessage, response, DataCodec::encode);
        return clientMessage;
    }

    /**
     * The value for the key if exists
     */
    public static com.hazelcast.internal.serialization.Data decodeResponse(ClientMessage clientMessage) {
        ClientMessage.ForwardFrameIterator iterator = clientMessage.frameIterator();
        //empty initial frame
        iterator.next();
        return CodecUtil.decodeNullable(iterator, DataCodec::decode);
    }
}
