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
import com.hazelcast.cp.internal.RaftGroupId;
import com.hazelcast.cp.internal.RaftService;
import com.hazelcast.cp.internal.datastructures.spi.RaftManagedService;
import com.hazelcast.cp.internal.datastructures.spi.RaftRemoteService;
import com.hazelcast.spi.impl.NodeEngine;

import java.util.Properties;

public class CPMapService implements RaftManagedService, RaftRemoteService {
    public static final String SERVICE_NAME = "hz:raft:mapService";

    // state def= (cpgroup, object-name) |-> (Data(K) |-> Data(V))
    private final CPMapRegistry mapRegistry;
    private final NodeEngine nodeEngine;
    private volatile RaftService raftService; // volatile - I think due to some other thread potentially calling init

    public CPMapService(NodeEngine nodeEngine) {
        mapRegistry = new CPMapRegistry();
        this.nodeEngine = nodeEngine;
    }

    public CPMapRegistry getMapRegistry() {
        return mapRegistry;
    }

    @Override
    public void onCPSubsystemRestart() { // RaftManagedService
    }

    @Override
    public DistributedObject createProxy(String objectName) { // RaftRemoteService
        RaftGroupId groupId = raftService.createRaftGroupForProxy(objectName);
        return new CPMapProxy<>(nodeEngine, groupId, objectName);
    }

    @Override
    public boolean destroyRaftObject(CPGroupId groupId, String objectName) { // RaftRemoveService
        // nothing here but we remove (groupId, objectName) from state via raft op
        return false;
    }

    @Override
    public void init(NodeEngine nodeEngine, Properties properties) { // ManagedService, called after ctor
        raftService = nodeEngine.getService(RaftService.SERVICE_NAME);
    }

    @Override
    public void reset() { // ManagedService

    }

    @Override
    public void shutdown(boolean terminate) { // ManagedService

    }
}
