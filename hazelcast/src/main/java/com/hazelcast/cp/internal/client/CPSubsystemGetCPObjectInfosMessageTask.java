/*
 * Copyright (c) 2008-2024, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.cp.internal.client;

import com.hazelcast.client.impl.protocol.ClientMessage;
import com.hazelcast.client.impl.protocol.codec.CPSubsystemGetCPObjectInfosCodec;
import com.hazelcast.client.impl.protocol.task.AbstractAsyncMessageTask;
import com.hazelcast.cp.CPGroupId;
import com.hazelcast.cp.internal.RaftService;
import com.hazelcast.instance.impl.Node;
import com.hazelcast.internal.nio.Connection;

import java.security.Permission;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class CPSubsystemGetCPObjectInfosMessageTask extends
        AbstractAsyncMessageTask<CPSubsystemGetCPObjectInfosCodec.RequestParameters, Collection<String>> {

    public CPSubsystemGetCPObjectInfosMessageTask(ClientMessage clientMessage, Node node, Connection connection) {
        super(clientMessage, node, connection);
    }

    @Override
    protected CompletableFuture<Collection<String>> processInternal() {
        CPGroupId groupId = parameters.cpGroupId;
        String serviceName = parameters.serviceName;
        boolean returnTombstone = parameters.tombstone;

        RaftService raftService = getService(getServiceName());
        return raftService.getObjectNames(groupId, serviceName, returnTombstone);
    }

    @Override
    protected CPSubsystemGetCPObjectInfosCodec.RequestParameters decodeClientMessage(ClientMessage clientMessage) {
        return CPSubsystemGetCPObjectInfosCodec.decodeRequest(clientMessage);
    }

    @Override
    protected ClientMessage encodeResponse(Object response) {
        return CPSubsystemGetCPObjectInfosCodec.encodeResponse((Collection<String>) response);
    }

    @Override
    public String getServiceName() {
        return RaftService.SERVICE_NAME;
    }

    @Override
    public Permission getRequiredPermission() {
        // we may want to check permission in beforeIntercept like GetDistributedObjectInfos does.
        return null;
    }

    @Override
    public String getDistributedObjectName() {
        return null;
    }

    @Override
    public String getMethodName() {
        return "getCPObjectInfos";
    }

    @Override
    public Object[] getParameters() {
        return new Object[]{parameters.serviceName, parameters.cpGroupId, parameters.tombstone};
    }

}
