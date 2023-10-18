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

package com.hazelcast.cp.internal.datastructures.map.operation;

import com.hazelcast.cp.CPGroupId;
import com.hazelcast.cp.internal.RaftOp;
import com.hazelcast.cp.internal.datastructures.map.CPMapDataSerializerHook;
import com.hazelcast.cp.internal.datastructures.map.DataMapContainer;
import com.hazelcast.cp.internal.datastructures.map.proxy.CPMap;
import com.hazelcast.cp.internal.datastructures.map.proxy.CPMapService;
import com.hazelcast.internal.nio.IOUtil;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CPMapPutOp extends RaftOp implements IdentifiedDataSerializable {
    private String objectName;
    private KvOp kvOp;
    private Map<Data, Data> kvs;

    public CPMapPutOp() {
        kvs = new HashMap<>();
    }

    public CPMapPutOp(String objectName, KvOp kvOp, Map<Data, Data> kvs) {
        this.objectName = objectName;
        this.kvOp = kvOp;
        this.kvs = new HashMap<>(kvs);
    }

    @Override
    public Object run(CPGroupId groupId, long commitIndex) throws Exception {
        CPMapService service = getService();
        CPMap cpMap = service.getMapRegistry().get(groupId, objectName);
        Map<Data, Data> results;
        switch (kvOp) {
            case PUT_ALWAYS:
                results = cpMap.put(kvs);
                break;
            case PUT_IF_ABSENT:
                results = cpMap.putIfAbsent(kvs);
                break;
            case PUT_REPLACE:
                results = cpMap.replace(kvs);
                break;
            default:
                throw new IllegalArgumentException("Unknown write semantics: " + kvOp);
        }
        return new DataMapContainer(results);
    }

    @Override
    protected String getServiceName() {
        return CPMapService.SERVICE_NAME;
    }

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
        out.writeString(objectName);
        out.writeInt(kvOp.getId());
        out.writeInt(kvs.size());
        for (Map.Entry<Data, Data> e : kvs.entrySet()) {
            IOUtil.writeData(out, e.getKey());
            IOUtil.writeData(out, e.getValue());
        }
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
        objectName = in.readString();
        kvOp = KvOp.fromId(in.readInt());
        int kvSize = in.readInt();
        for (int i = 0; i < kvSize; i++) {
            Data key = IOUtil.readData(in);
            Data value = IOUtil.readData(in);
            kvs.put(key, value);
        }
    }

    @Override
    public int getFactoryId() {
        return CPMapDataSerializerHook.F_ID;
    }

    @Override
    public int getClassId() {
        return CPMapDataSerializerHook.PUT_OP;
    }
}
