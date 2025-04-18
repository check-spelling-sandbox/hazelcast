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

package com.hazelcast.client.impl.protocol.codec.custom;

import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.Generated;
import com.hazelcast.client.impl.protocol.codec.builtin.*;

import static com.hazelcast.client.impl.protocol.codec.builtin.CodecUtil.fastForwardToEndFrame;
import static com.hazelcast.client.impl.protocol.ClientMessage.*;
import static com.hazelcast.client.impl.protocol.codec.builtin.FixedSizeTypesCodec.*;

@SuppressWarnings("unused")
@Generated("c94fe329521e5b5efdb261d92cfc0dce")
public final class StackTraceElementCodec {
    private static final int LINE_NUMBER_FIELD_OFFSET = 0;
    private static final int INITIAL_FRAME_SIZE = LINE_NUMBER_FIELD_OFFSET + INT_SIZE_IN_BYTES;

    private StackTraceElementCodec() {
    }

    public static void encode(ClientMessage clientMessage, java.lang.StackTraceElement stackTraceElement) {
        clientMessage.add(BEGIN_FRAME.copy());

        ClientMessage.Frame initialFrame = new ClientMessage.Frame(new byte[INITIAL_FRAME_SIZE]);
        encodeInt(initialFrame.content, LINE_NUMBER_FIELD_OFFSET, stackTraceElement.getLineNumber());
        clientMessage.add(initialFrame);

        StringCodec.encode(clientMessage, stackTraceElement.getClassName());
        StringCodec.encode(clientMessage, stackTraceElement.getMethodName());
        CodecUtil.encodeNullable(clientMessage, stackTraceElement.getFileName(), StringCodec::encode);

        clientMessage.add(END_FRAME.copy());
    }

    public static java.lang.StackTraceElement decode(ClientMessage.ForwardFrameIterator iterator) {
        // begin frame
        iterator.next();

        ClientMessage.Frame initialFrame = iterator.next();
        int lineNumber = decodeInt(initialFrame.content, LINE_NUMBER_FIELD_OFFSET);

        java.lang.String className = StringCodec.decode(iterator);
        java.lang.String methodName = StringCodec.decode(iterator);
        java.lang.String fileName = CodecUtil.decodeNullable(iterator, StringCodec::decode);

        fastForwardToEndFrame(iterator);

        return new java.lang.StackTraceElement(className, methodName, fileName, lineNumber);
    }
}
