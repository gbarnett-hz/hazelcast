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

package com.hazelcast.cp.internal.datastructures.map.proxy;

import com.hazelcast.cp.ICPMap;
import com.hazelcast.cp.internal.RaftGroupId;
import com.hazelcast.cp.internal.RaftInvocationManager;
import com.hazelcast.cp.internal.RaftService;
import com.hazelcast.cp.internal.datastructures.map.DataMapContainer;
import com.hazelcast.cp.internal.datastructures.map.operation.CPMapGetOp;
import com.hazelcast.cp.internal.datastructures.map.operation.CPMapPutOp;
import com.hazelcast.cp.internal.datastructures.map.operation.CPMapRemoveOp;
import com.hazelcast.cp.internal.datastructures.map.operation.CPMapSizeOp;
import com.hazelcast.cp.internal.datastructures.map.operation.KvOp;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.internal.serialization.SerializationService;
import com.hazelcast.spi.impl.NodeEngine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// TDD PoC -- illustrative only
public class CPMapProxy<K, V> implements ICPMap<K, V> {

    private final RaftInvocationManager invocationManager;
    private final RaftGroupId groupId;
    private final String proxyName;
    private final SerializationService serializationService;
    private final String objectName;

    public CPMapProxy(NodeEngine nodeEngine, RaftGroupId groupId, String proxyName) {
        RaftService raftService = nodeEngine.getService(RaftService.SERVICE_NAME);
        invocationManager = raftService.getInvocationManager();
        this.groupId = groupId;
        this.proxyName = proxyName;
        serializationService = nodeEngine.getSerializationService();
        objectName = RaftService.getObjectNameForProxy(proxyName);
    }

    @Override
    public String getPartitionKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return proxyName;
    }

    @Override
    public String getServiceName() {
        return CPMapService.SERVICE_NAME;
    }

    @Override
    public void destroy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        Object raftOut = invocationManager.invoke(groupId, new CPMapSizeOp(objectName)).join();
        if (raftOut instanceof Integer) {
            return (int) raftOut;
        }
        throw new RuntimeException("Expected int");
    }

    @Override
    public Map<K, V> putAll(Map<K, V> keyValues) {
        return putAll(keyValues, KvOp.PUT_ALWAYS);
    }

    @Override
    public Map<K, V> putIfAbsentAll(Map<K, V> keyValues) {
        return putAll(keyValues, KvOp.PUT_IF_ABSENT);
    }

    @Override
    public Map<K, V> replaceAll(Map<K, V> keyValues) {
        return putAll(keyValues, KvOp.PUT_REPLACE);
    }

    @Override
    public Map<Object, V> removeAll(Set<Object> keys) {
        Set<Data> dataKeys = toDataSet(keys);
        Object raftOut = invocationManager.invoke(groupId, new CPMapRemoveOp(objectName, dataKeys)).join();
        DataMapContainer dataMapContainer = getDataMapContainer(raftOut);
        return getMapFromDataMapContainer(dataMapContainer, serializationService);
    }

    private static DataMapContainer getDataMapContainer(Object o) {
        Objects.requireNonNull(o, "DataMapContainer is null");
        if (o instanceof DataMapContainer) {
            return (DataMapContainer) o;
        }
        throw new IllegalArgumentException("Expected a DataMapContainer");
    }

    static <X, Y> Map<X, Y> getMapFromDataMapContainer(DataMapContainer dataMapContainer,
                                                       SerializationService serializationService) {
        Map<X, Y> result = new HashMap<>();
        for (Map.Entry<Data, Data> e : dataMapContainer.getMap().entrySet()) {
            X k = serializationService.toObject(e.getKey());
            Y v = serializationService.toObject(e.getValue());
            result.put(k, v);
        }
        return result;
    }


    private Map<K, V> putAll(Map<K, V> keyValues, KvOp kvOp) {
        Map<Data, Data> payload = new HashMap<>();
        for (Map.Entry<K, V> kv : keyValues.entrySet()) {
            Data k = serializationService.toData(kv.getKey());
            Data v = serializationService.toData(kv.getValue());
            payload.put(k, v);
        }
        Object raftOut = invocationManager.invoke(groupId, new CPMapPutOp(objectName, kvOp, payload)).join();
        DataMapContainer dataMapContainer = getDataMapContainer(raftOut);
        return getMapFromDataMapContainer(dataMapContainer, serializationService);
    }

    @Override
    public Map<Object, V> getAll(Set<Object> keys) {
        Set<Data> payload = toDataSet(keys);
        Object raftOut = invocationManager.invoke(groupId, new CPMapGetOp(objectName, payload)).join();
        DataMapContainer dataMapContainer = getDataMapContainer(raftOut);
        return getMapFromDataMapContainer(dataMapContainer, serializationService);
    }

    private Set<Data> toDataSet(Set<Object> s) {
        Set<Data> set = new HashSet<>();
        for (Object e : s) {
            set.add(serializationService.toData(e));
        }
        return set;
    }
}
