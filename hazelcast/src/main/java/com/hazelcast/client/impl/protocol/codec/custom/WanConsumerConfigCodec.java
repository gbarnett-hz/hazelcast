/*
 * Copyright (c) 2008-2023, Hazelcast, Inc. All Rights Reserved.
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

@Generated("0b952be37e69c1cce3450cb0f2f2e231")
public final class WanConsumerConfigCodec {
    private static final int PERSIST_WAN_REPLICATED_DATA_FIELD_OFFSET = 0;
    private static final int INITIAL_FRAME_SIZE = PERSIST_WAN_REPLICATED_DATA_FIELD_OFFSET + BOOLEAN_SIZE_IN_BYTES;

    private WanConsumerConfigCodec() {
    }

    public static void encode(ClientMessage clientMessage, com.hazelcast.config.WanConsumerConfig wanConsumerConfig) {
        clientMessage.add(BEGIN_FRAME.copy());

        Frame initialFrame = new Frame(new byte[INITIAL_FRAME_SIZE]);
        encodeBoolean(initialFrame.content, PERSIST_WAN_REPLICATED_DATA_FIELD_OFFSET, wanConsumerConfig.isPersistWanReplicatedData());
        clientMessage.add(initialFrame);

        StringCodec.encode(clientMessage, wanConsumerConfig.getClassName());
        MapCodec.encodeNullable(clientMessage, wanConsumerConfig.getProperties(), StringCodec::encode, DataCodec::encode);

        clientMessage.add(END_FRAME.copy());
    }

    public static com.hazelcast.config.WanConsumerConfig decode(ForwardFrameIterator iterator) {
        // begin frame
        iterator.next();

        Frame initialFrame = iterator.next();
        boolean persistWanReplicatedData = decodeBoolean(initialFrame.content, PERSIST_WAN_REPLICATED_DATA_FIELD_OFFSET);

        String className = StringCodec.decode(iterator);
        java.util.Map<String, com.hazelcast.internal.serialization.Data> properties = MapCodec.decodeNullable(iterator, StringCodec::decode, DataCodec::decode);

        fastForwardToEndFrame(iterator);

        return new com.hazelcast.config.WanConsumerConfig(persistWanReplicatedData, className, properties);
    }
}
