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

import com.hazelcast.cp.CPGroupId;
import com.hazelcast.internal.util.BiTuple;

import java.util.concurrent.ConcurrentHashMap;

public final class CPMapRegistry {
    private final ConcurrentHashMap<BiTuple<CPGroupId, String>, CPMap> registry;
    public CPMapRegistry() {
        registry = new ConcurrentHashMap<>();
    }

    public CPMap get(CPGroupId groupId, String objectName) {
        BiTuple<CPGroupId, String> k = BiTuple.of(groupId, objectName);
        registry.putIfAbsent(k, new CPMap());
        return registry.get(k);
    }
}
