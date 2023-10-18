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

import com.hazelcast.core.DistributedObject;
import com.hazelcast.cp.CPGroupId;
import com.hazelcast.cp.internal.datastructures.spi.RaftManagedService;
import com.hazelcast.cp.internal.datastructures.spi.RaftRemoteService;
import com.hazelcast.internal.serialization.Data;
import com.hazelcast.internal.util.BiTuple;
import com.hazelcast.spi.impl.NodeEngine;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class CPMapService implements RaftManagedService, RaftRemoteService {
    public static final String SERVICE_NAME = "hz:raft:mapService";

    // state def= (cpgroup, object-name) |-> (Data(K) |-> Data(V))
    private final Map<BiTuple<CPGroupId, String>, HashMap<Data, Data>> state;
    private final NodeEngine nodeEngine;

    public CPMapService(NodeEngine nodeEngine) {
        state = new ConcurrentHashMap<>();
        this.nodeEngine = nodeEngine;
    }

    @Override
    public void onCPSubsystemRestart() { // RaftManagedService
    }

    @Override
    public DistributedObject createProxy(String objectName) { // RaftRemoteService
        return new CPMapProxy<>(objectName);
    }

    @Override
    public boolean destroyRaftObject(CPGroupId groupId, String objectName) { // RaftRemoveService
        // nothing here but we remove (groupId, objectName) from state via raft op
        return false;
    }

    @Override
    public void init(NodeEngine nodeEngine, Properties properties) { // ManagedService

    }

    @Override
    public void reset() { // ManagedService

    }

    @Override
    public void shutdown(boolean terminate) { // ManagedService

    }
}
