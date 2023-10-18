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

import com.hazelcast.internal.serialization.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.hazelcast.cp.internal.datastructures.map.operation.KvOp.PUT_ALWAYS;
import static com.hazelcast.cp.internal.datastructures.map.operation.KvOp.PUT_IF_ABSENT;
import static com.hazelcast.cp.internal.datastructures.map.operation.KvOp.PUT_REPLACE;

public final class CPMap {
    private final Map<com.hazelcast.cp.internal.datastructures.map.operation.KvOp, KvOp> kvOps;
    private final Map<KeyOp, KOp> kOps;

    private enum KeyOp {
        GET,
        REMOVE
    }

    private interface KvOp {
        Data apply(Map<Data, Data> m, Data k, Data v);
    }

    private interface KOp {
        Data apply(Map<Data, Data> m, Data k);
    }

    private final Map<Data, Data> map;

    public CPMap() {
        map = new ConcurrentHashMap<>();
        kvOps = Map.of(
                PUT_ALWAYS, Map::put,
                PUT_IF_ABSENT, Map::putIfAbsent,
                PUT_REPLACE, Map::replace);
        kOps = Map.of(
                KeyOp.GET, Map::get,
                KeyOp.REMOVE, Map::remove);
    }

    public Map<Data, Data> put(Map<Data, Data> kvs) {
        return applyKvOp(kvs, kvOps.get(PUT_ALWAYS));
    }

    public Map<Data, Data> putIfAbsent(Map<Data, Data> kvs) {
        return applyKvOp(kvs, kvOps.get(PUT_IF_ABSENT));
    }

    public Map<Data, Data> replace(Map<Data, Data> kvs) {
        return applyKvOp(kvs, kvOps.get(PUT_REPLACE));
    }

    public Map<Data, Data> get(Set<Data> keys) {
        return applyKOp(keys, kOps.get(KeyOp.GET));
    }

    public Map<Data, Data> remove(Set<Data> keys) {
        return applyKOp(keys, kOps.get(KeyOp.REMOVE));
    }

    public int size() {
        return map.size();
    }

    private Map<Data, Data> applyKvOp(Map<Data, Data> kvs, KvOp put) {
        Map<Data, Data> previousValues = new HashMap<>();
        for (Map.Entry<Data, Data> e : kvs.entrySet()) {
            Data previousValue = put.apply(map, e.getKey(), e.getValue());
            previousValues.put(e.getKey(), previousValue);
        }
        return previousValues;
    }

    private Map<Data, Data> applyKOp(Set<Data> keys, KOp kop) {
        Map<Data, Data> result = new HashMap<>();
        for (Data key : keys) {
            result.put(key, kop.apply(map, key));
        }
        return result;
    }
}
