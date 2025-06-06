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

package com.hazelcast.client.impl.protocol.codec.builtin;

import com.hazelcast.client.impl.protocol.ClientMessage;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import static com.hazelcast.client.impl.protocol.codec.builtin.FixedSizeTypesCodec.UUID_SIZE_IN_BYTES;
import static com.hazelcast.client.impl.protocol.codec.builtin.FixedSizeTypesCodec.decodeUUID;
import static com.hazelcast.client.impl.protocol.codec.builtin.FixedSizeTypesCodec.encodeUUID;

public final class SetUUIDCodec {

    private SetUUIDCodec() {
    }

    public static void encode(ClientMessage clientMessage, Collection<UUID> collection) {
        int itemCount = collection.size();
        ClientMessage.Frame frame = new ClientMessage.Frame(new byte[itemCount * UUID_SIZE_IN_BYTES]);
        Iterator<UUID> iterator = collection.iterator();
        for (int i = 0; i < itemCount; i++) {
            encodeUUID(frame.content, i * UUID_SIZE_IN_BYTES, iterator.next());
        }
        clientMessage.add(frame);
    }

    public static Set<UUID> decode(ClientMessage.ForwardFrameIterator iterator) {
        ClientMessage.Frame frame = iterator.next();
        int itemCount = frame.content.length / UUID_SIZE_IN_BYTES;
        Set<UUID> result = new HashSet<>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            result.add(decodeUUID(frame.content, i * UUID_SIZE_IN_BYTES));
        }
        return result;
    }
}
