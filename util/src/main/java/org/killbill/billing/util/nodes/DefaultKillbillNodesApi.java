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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.killbill.CreatorName;
import org.killbill.billing.broadcast.BroadcastApi;
import org.killbill.billing.osgi.api.PluginInfo;
import org.killbill.billing.platform.api.KillbillService.KILLBILL_SERVICES;
import org.killbill.billing.util.nodes.dao.NodeInfoDao;
import org.killbill.billing.util.nodes.dao.NodeInfoModelDao;
import org.killbill.billing.util.nodes.json.NodeInfoModelJson;
import org.killbill.billing.util.nodes.json.PluginInfoModelJson;
import org.killbill.clock.Clock;
import org.killbill.commons.utils.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

public class DefaultKillbillNodesApi implements KillbillNodesApi {

    private final Logger logger = LoggerFactory.getLogger(DefaultKillbillNodesApi.class);

    private final NodeInfoDao nodeInfoDao;
    private final BroadcastApi broadcastApi;
    private final NodeInfoMapper mapper;
    private final Clock clock;
    private final Function<NodeInfoModelDao, NodeInfo> nodeTransfomer;

    @Inject
    public DefaultKillbillNodesApi(final NodeInfoDao nodeInfoDao, final BroadcastApi broadcastApi, final NodeInfoMapper mapper, final Clock clock) {
        this.nodeInfoDao = nodeInfoDao;
        this.broadcastApi = broadcastApi;
        this.clock = clock;
        this.mapper = mapper;
        this.nodeTransfomer = input -> {
            try {
                final NodeInfoModelJson nodeInfoModelJson = mapper.deserializeNodeInfo(input.getNodeInfo());
                return new DefaultNodeInfo(nodeInfoModelJson);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Override
    public Iterable<NodeInfo> getNodesInfo() {
        final List<NodeInfoModelDao> allNodes = nodeInfoDao.getAll();
        return allNodes.stream().map(nodeTransfomer).collect(Collectors.toUnmodifiableList());
    }

    @Override
    public NodeInfo getCurrentNodeInfo() {
        final List<NodeInfoModelDao> allNodes = nodeInfoDao.getAll();

        final Optional<NodeInfoModelDao> currentNode =
                allNodes.stream()
                        .filter(nodeInfoModel -> CreatorName.get().equals(nodeInfoModel.getNodeName()))
                        .findFirst();

        if (currentNode.isEmpty()) {
            throw new IllegalStateException(String.format("No node found with the name %s in the cluster", CreatorName.get()));
        }

        return nodeTransfomer.apply(currentNode.get());
    }

    @Override
    public void triggerNodeCommand(final NodeCommand nodeCommand, final boolean localNodeOnly) {

        final String event;
        try {
            event = mapper.serializeNodeCommand(nodeCommand.getNodeCommandMetadata());
            broadcastApi.broadcast(KILLBILL_SERVICES.BROADCAST_SERVICE.getServiceName(), nodeCommand.getNodeCommandType(), event, clock.getUTCNow(), "unset", localNodeOnly);

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void notifyPluginChanged(final PluginInfo plugin, final Iterable<PluginInfo> latestPlugins) {
        final String updatedNodeInfoJson;
        try {
            updatedNodeInfoJson = computeLatestNodeInfo(latestPlugins);
            nodeInfoDao.updateNodeInfo(CreatorName.get(), updatedNodeInfoJson);
        } catch (final IOException e) {
            logger.warn("Failed to update nodeInfo after plugin change", e);
        }
    }

    private String computeLatestNodeInfo(final Iterable<PluginInfo> rawPluginInfo) throws IOException {

        final NodeInfoModelDao nodeInfo = nodeInfoDao.getByNodeName(CreatorName.get());
        final NodeInfoModelJson nodeInfoJson = mapper.deserializeNodeInfo(nodeInfo.getNodeInfo());

        final List<PluginInfo> pluginInfos = rawPluginInfo.iterator().hasNext() ?
                                             Iterables.toUnmodifiableList(rawPluginInfo) :
                                             Collections.emptyList();

        final NodeInfoModelJson updatedNodeInfoJson = new NodeInfoModelJson(CreatorName.get(),
                                                                            nodeInfoJson.getBootTime(),
                                                                            clock.getUTCNow(),
                                                                            nodeInfoJson.getKillbillVersion(),
                                                                            nodeInfoJson.getApiVersion(),
                                                                            nodeInfoJson.getPluginApiVersion(),
                                                                            nodeInfoJson.getCommonVersion(),
                                                                            nodeInfoJson.getPlatformVersion(),
                                                                            pluginInfos.stream().map(PluginInfoModelJson::new).collect(Collectors.toUnmodifiableList()));

        return mapper.serializeNodeInfo(updatedNodeInfoJson);
    }
}
