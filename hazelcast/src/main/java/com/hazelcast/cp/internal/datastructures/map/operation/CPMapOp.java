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
import com.hazelcast.cp.internal.datastructures.map.proxy.CPMap;
import com.hazelcast.cp.internal.datastructures.map.proxy.CPMapService;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

public abstract class CPMapOp extends RaftOp implements IdentifiedDataSerializable {
    @Override
    public String getServiceName() {
        return CPMapService.SERVICE_NAME;
    }

    @Override
    public int getFactoryId() {
        return CPMapDataSerializerHook.F_ID;
    }

    protected CPMap getMap(CPGroupId groupId, String objectName) {
        CPMapService service = getService();
        return service.getMapRegistry().get(groupId, objectName);
    }
}
