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
@Generated("d7cb8d750092ed5465d48c5570218eff")
public final class CapacityCodec {
    private static final int VALUE_FIELD_OFFSET = 0;
    private static final int UNIT_FIELD_OFFSET = VALUE_FIELD_OFFSET + LONG_SIZE_IN_BYTES;
    private static final int INITIAL_FRAME_SIZE = UNIT_FIELD_OFFSET + INT_SIZE_IN_BYTES;

    private CapacityCodec() {
    }

    public static void encode(ClientMessage clientMessage, com.hazelcast.memory.Capacity capacity) {
        clientMessage.add(BEGIN_FRAME.copy());

        ClientMessage.Frame initialFrame = new ClientMessage.Frame(new byte[INITIAL_FRAME_SIZE]);
        encodeLong(initialFrame.content, VALUE_FIELD_OFFSET, capacity.getValue());
        encodeInt(initialFrame.content, UNIT_FIELD_OFFSET, capacity.getUnit());
        clientMessage.add(initialFrame);

        clientMessage.add(END_FRAME.copy());
    }

    public static com.hazelcast.memory.Capacity decode(ClientMessage.ForwardFrameIterator iterator) {
        // begin frame
        iterator.next();

        ClientMessage.Frame initialFrame = iterator.next();
        long value = decodeLong(initialFrame.content, VALUE_FIELD_OFFSET);
        int unit = decodeInt(initialFrame.content, UNIT_FIELD_OFFSET);

        fastForwardToEndFrame(iterator);

        return CustomTypeFactory.createCapacity(value, unit);
    }
}
