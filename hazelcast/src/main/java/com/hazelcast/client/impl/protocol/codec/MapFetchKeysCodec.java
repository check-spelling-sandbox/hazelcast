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
 * Fetches specified number of keys from the specified partition starting from specified table index.
 */
@SuppressWarnings("unused")
@Generated("5b5e0595095394ca3ca5280a107e8ba1")
public final class MapFetchKeysCodec {
    //hex: 0x013700
    public static final int REQUEST_MESSAGE_TYPE = 79616;
    //hex: 0x013701
    public static final int RESPONSE_MESSAGE_TYPE = 79617;
    private static final int REQUEST_BATCH_FIELD_OFFSET = PARTITION_ID_FIELD_OFFSET + INT_SIZE_IN_BYTES;
    private static final int REQUEST_INITIAL_FRAME_SIZE = REQUEST_BATCH_FIELD_OFFSET + INT_SIZE_IN_BYTES;
    private static final int RESPONSE_INITIAL_FRAME_SIZE = RESPONSE_BACKUP_ACKS_FIELD_OFFSET + BYTE_SIZE_IN_BYTES;

    private MapFetchKeysCodec() {
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings({"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
    public static class RequestParameters {

        /**
         * Name of the map.
         */
        public java.lang.String name;

        /**
         * The index-size pairs that define the state of iteration
         */
        public java.util.List<java.util.Map.Entry<java.lang.Integer, java.lang.Integer>> iterationPointers;

        /**
         * The number of items to be batched
         */
        public int batch;
    }

    public static ClientMessage encodeRequest(java.lang.String name, java.util.Collection<java.util.Map.Entry<java.lang.Integer, java.lang.Integer>> iterationPointers, int batch) {
        ClientMessage clientMessage = ClientMessage.createForEncode();
        clientMessage.setRetryable(true);
        clientMessage.setOperationName("Map.FetchKeys");
        ClientMessage.Frame initialFrame = new ClientMessage.Frame(new byte[REQUEST_INITIAL_FRAME_SIZE], UNFRAGMENTED_MESSAGE);
        encodeInt(initialFrame.content, TYPE_FIELD_OFFSET, REQUEST_MESSAGE_TYPE);
        encodeInt(initialFrame.content, PARTITION_ID_FIELD_OFFSET, -1);
        encodeInt(initialFrame.content, REQUEST_BATCH_FIELD_OFFSET, batch);
        clientMessage.add(initialFrame);
        StringCodec.encode(clientMessage, name);
        EntryListIntegerIntegerCodec.encode(clientMessage, iterationPointers);
        return clientMessage;
    }

    public static MapFetchKeysCodec.RequestParameters decodeRequest(ClientMessage clientMessage) {
        ClientMessage.ForwardFrameIterator iterator = clientMessage.frameIterator();
        RequestParameters request = new RequestParameters();
        ClientMessage.Frame initialFrame = iterator.next();
        request.batch = decodeInt(initialFrame.content, REQUEST_BATCH_FIELD_OFFSET);
        request.name = StringCodec.decode(iterator);
        request.iterationPointers = EntryListIntegerIntegerCodec.decode(iterator);
        return request;
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings({"URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
    public static class ResponseParameters {

        /**
         * The index-size pairs that define the state of iteration
         */
        public java.util.List<java.util.Map.Entry<java.lang.Integer, java.lang.Integer>> iterationPointers;

        /**
         * List of keys.
         */
        public java.util.List<com.hazelcast.internal.serialization.Data> keys;
    }

    public static ClientMessage encodeResponse(java.util.Collection<java.util.Map.Entry<java.lang.Integer, java.lang.Integer>> iterationPointers, java.util.Collection<com.hazelcast.internal.serialization.Data> keys) {
        ClientMessage clientMessage = ClientMessage.createForEncode();
        ClientMessage.Frame initialFrame = new ClientMessage.Frame(new byte[RESPONSE_INITIAL_FRAME_SIZE], UNFRAGMENTED_MESSAGE);
        encodeInt(initialFrame.content, TYPE_FIELD_OFFSET, RESPONSE_MESSAGE_TYPE);
        clientMessage.add(initialFrame);

        EntryListIntegerIntegerCodec.encode(clientMessage, iterationPointers);
        ListMultiFrameCodec.encode(clientMessage, keys, DataCodec::encode);
        return clientMessage;
    }

    public static MapFetchKeysCodec.ResponseParameters decodeResponse(ClientMessage clientMessage) {
        ClientMessage.ForwardFrameIterator iterator = clientMessage.frameIterator();
        ResponseParameters response = new ResponseParameters();
        //empty initial frame
        iterator.next();
        response.iterationPointers = EntryListIntegerIntegerCodec.decode(iterator);
        response.keys = ListMultiFrameCodec.decode(iterator, DataCodec::decode);
        return response;
    }
}
