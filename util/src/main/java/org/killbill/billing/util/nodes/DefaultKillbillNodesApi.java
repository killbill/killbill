/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.util.nodes;

import java.io.IOException;
import java.util.List;

import org.killbill.billing.broadcast.BroadcastApi;
import org.killbill.billing.osgi.api.PluginInfo;
import org.killbill.billing.util.nodes.dao.NodeInfoDao;
import org.killbill.billing.util.nodes.dao.NodeInfoModelDao;
import org.killbill.billing.util.nodes.json.NodeInfoModelJson;
import org.killbill.clock.Clock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

public class DefaultKillbillNodesApi implements KillbillNodesApi {

    private final NodeInfoDao nodeInfoDao;
    private final BroadcastApi broadcastApi;
    private final NodeInfoMapper mapper;
    private final Clock clock;

    @Inject
    public DefaultKillbillNodesApi(final NodeInfoDao nodeInfoDao, final BroadcastApi broadcastApi, final NodeInfoMapper mapper, final Clock clock) {
        this.nodeInfoDao = nodeInfoDao;
        this.broadcastApi = broadcastApi;
        this.clock = clock;
        this.mapper = mapper;
    }

    @Override
    public Iterable<NodeInfo> getNodesInfo() {
        final List<NodeInfoModelDao> allNodes = nodeInfoDao.getAll();

        final Iterable<NodeInfoModelJson> allModelNodes = Iterables.transform(allNodes, new Function<NodeInfoModelDao, NodeInfoModelJson>() {
            @Override
            public NodeInfoModelJson apply(final NodeInfoModelDao input) {
                try {
                    return mapper.deserializeNodeInfo(input.getNodeInfo());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        return Iterables.transform(allModelNodes, new Function<NodeInfoModelJson, NodeInfo>() {
            @Override
            public NodeInfo apply(final NodeInfoModelJson input) {
                return new DefaultNodeInfo(input);
            }
        });
    }

    @Override
    public void triggerNodeCommand(final NodeCommand nodeCommand) {

        final String event;
        try {
            event = mapper.serializeNodeCommand(nodeCommand.getNodeCommandMetadata());
            broadcastApi.broadcast(DefaultKillbillNodesService.NODES_SERVICE_NAME, nodeCommand.getNodeCommandType(), event, clock.getUTCNow(), "unset");

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void notifyPluginChanged(final Iterable<PluginInfo> iterable) {

    }
}
