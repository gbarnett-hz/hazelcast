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
import com.hazelcast.cp.internal.datastructures.map.proxy.CPMapService;
import com.hazelcast.internal.nio.IOUtil;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class CPMapGetOp extends RaftOp implements IdentifiedDataSerializable {

    private String objectName;
    private Set<Data> keys;

    public CPMapGetOp() {
        keys = new HashSet<>();
    }

    public CPMapGetOp(String objectName, Set<Data> keys) {
        this.objectName = objectName;
        this.keys = new HashSet<>(keys);
    }

    @Override
    public Object run(CPGroupId groupId, long commitIndex) throws Exception {
        CPMapService service = getService();
        return new DataMapContainer(service.getMapRegistry().get(groupId, objectName).get(keys));
    }

    @Override
    protected String getServiceName() {
        return CPMapService.SERVICE_NAME;
    }

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
        out.writeString(objectName);
        out.writeInt(keys.size());
        for (Data key : keys) {
            IOUtil.writeData(out, key);
        }
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
        objectName = in.readString();
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            Data k = IOUtil.readData(in);
            keys.add(k);
        }
    }

    @Override
    public int getFactoryId() {
        return CPMapDataSerializerHook.F_ID;
    }

    @Override
    public int getClassId() {
        return CPMapDataSerializerHook.GET_OP;
    }
}
