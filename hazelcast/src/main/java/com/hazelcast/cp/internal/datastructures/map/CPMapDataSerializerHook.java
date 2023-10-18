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

package com.hazelcast.cp.internal.datastructures.map;

import com.hazelcast.cp.internal.datastructures.map.operation.CPMapGetOp;
import com.hazelcast.cp.internal.datastructures.map.operation.CPMapPutOp;
import com.hazelcast.cp.internal.datastructures.map.operation.CPMapRemoveOp;
import com.hazelcast.cp.internal.datastructures.map.operation.CPMapSizeOp;
import com.hazelcast.internal.serialization.DataSerializerHook;
import com.hazelcast.internal.serialization.impl.FactoryIdHelper;
import com.hazelcast.nio.serialization.DataSerializableFactory;

public final class CPMapDataSerializerHook implements DataSerializerHook {
    public static final int PUT_OP = 1;
    public static final int DATA_MAP_CONTAINER = 2;
    public static final int GET_OP = 3;
    public static final int REMOVE_OP = 4;
    public static final int SIZE_OP = 5;
    private static final String RAFT_CPMAP_DS_FACTORY = "hazelcast.serialization.ds.raft.cpmap";
    private static final int RAFT_CPMAP_DS_FACTORY_DEFAULT_ID = -1079;
    public static final int F_ID = FactoryIdHelper.getFactoryId(RAFT_CPMAP_DS_FACTORY, RAFT_CPMAP_DS_FACTORY_DEFAULT_ID);

    @Override
    public int getFactoryId() {
        return F_ID;
    }

    @Override
    public DataSerializableFactory createFactory() {
        return typeId -> {
            switch (typeId) {
                case PUT_OP:
                    return new CPMapPutOp();
                case DATA_MAP_CONTAINER:
                    return new DataMapContainer();
                case GET_OP:
                    return new CPMapGetOp();
                case REMOVE_OP:
                    return new CPMapRemoveOp();
                case SIZE_OP:
                    return new CPMapSizeOp();
                default:
                    throw new IllegalArgumentException("Undefined type: " + typeId);
            }
        };
    }
}
