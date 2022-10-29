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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.CreatorName;
import org.killbill.billing.osgi.api.PluginInfo;
import org.killbill.billing.osgi.api.PluginsInfoApi;
import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.commons.utils.collect.Iterables;
import org.killbill.billing.util.nodes.dao.NodeInfoDao;
import org.killbill.billing.util.nodes.dao.NodeInfoModelDao;
import org.killbill.billing.util.nodes.json.NodeInfoModelJson;
import org.killbill.billing.util.nodes.json.PluginInfoModelJson;
import org.killbill.clock.Clock;
import org.killbill.commons.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

public class DefaultKillbillNodesService implements KillbillNodesService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultKillbillNodesService.class);

    private static final int TERMINATION_TIMEOUT_SEC = 5;
    private static final int TIME_PERIOD_SEC = 5;
    private static final int INITIAL_DELAY_SEC = TIME_PERIOD_SEC;

    private final NodeInfoDao nodeInfoDao;
    private final PluginsInfoApi pluginInfoApi;
    private final Clock clock;
    private final NodeInfoMapper mapper;

    private ScheduledExecutorService nodeInfoExecutor;
    private volatile boolean isStopped;

    @Inject
    public DefaultKillbillNodesService(final NodeInfoDao nodeInfoDao, final PluginsInfoApi pluginInfoApi, final Clock clock, final NodeInfoMapper mapper) {
        this.nodeInfoDao = nodeInfoDao;
        this.pluginInfoApi = pluginInfoApi;
        this.clock = clock;
        this.mapper = mapper;
        this.isStopped = false;
    }

    @Override
    public String getName() {
        return KILLBILL_SERVICES.NODES_SERVICE.getServiceName() ;
    }

    @Override
    public int getRegistrationOrdering() {
        return KILLBILL_SERVICES.NODES_SERVICE.getRegistrationOrdering();
    }

    @LifecycleHandlerType(LifecycleLevel.BOOT)
    public void init() {
        try {
            // Compute a first version early on before plugins were installed to at least provide info about Kill Bill component versions
            createBootNodeInfo(true);
        } catch (final JsonProcessingException e) {
            logger.error("Failed to create bootNodeInfo", e);
        }
    }

    public static class NodeInfoRunnable implements Runnable {
        final NodeInfoDao nodeInfoDao;

        public NodeInfoRunnable(final NodeInfoDao nodeInfoDao) {
            this.nodeInfoDao = nodeInfoDao;
        }

        @Override
        public void run() {
            nodeInfoDao.setUpdatedDate(CreatorName.get());
        }
    }


    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        try {
            // Re-Compute including the plugins
            createBootNodeInfo(false);
            this.nodeInfoExecutor = Executors.newSingleThreadScheduledExecutor("NodeInfoExecutor");
            nodeInfoExecutor.scheduleAtFixedRate(new NodeInfoRunnable(nodeInfoDao), INITIAL_DELAY_SEC, TIME_PERIOD_SEC, TimeUnit.SECONDS);
            // In tests, the service is created once and re-used.
            this.isStopped = false;
            logger.info("Created nodeInfo for {}", CreatorName.get());
        } catch (final JsonProcessingException e) {
            logger.error("Failed to create bootNodeInfo", e);
        }
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() {
        logger.info("Deleting nodeInfo for {}", CreatorName.get());
        nodeInfoDao.delete(CreatorName.get());
        if (isStopped) {
            return;
        }
        try {
            nodeInfoExecutor.shutdown();
            final boolean success = nodeInfoExecutor.awaitTermination(TERMINATION_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!success) {
                logger.warn("NodeInfoExecutor failed to complete termination within " + TERMINATION_TIMEOUT_SEC + "sec");
                nodeInfoExecutor.shutdownNow();
            }
        } catch (final InterruptedException e) {
            nodeInfoExecutor.shutdownNow();
            Thread.currentThread().interrupt();
            logger.warn("NodeInfoExecutor stop sequence got interrupted");
        } finally {
            isStopped = true;
        }
    }

    private void createBootNodeInfo(final boolean skipPlugins) throws JsonProcessingException {
        final DateTime bootTime = clock.getUTCNow();
        final Iterable<PluginInfo> rawPluginInfo = skipPlugins ? Collections.emptyList() : pluginInfoApi.getPluginsInfo();
        final List<PluginInfo> pluginInfo = !Iterables.isEmpty(rawPluginInfo) ? Iterables.toUnmodifiableList(rawPluginInfo) : Collections.emptyList();
        final String kbVersion = org.killbill.billing.util.nodes.KillbillVersions.getKillbillVersion();
        final String kbApiVersion  = org.killbill.billing.util.nodes.KillbillVersions.getApiVersion();
        final String kbPluginApiVersion  = org.killbill.billing.util.nodes.KillbillVersions.getPluginApiVersion();
        final String kbPlatformVersion  = org.killbill.billing.util.nodes.KillbillVersions.getPlatformVersion();
        final String kbCommonVersion  = org.killbill.billing.util.nodes.KillbillVersions.getCommonVersion();


        final NodeInfoModelJson nodeInfo = new NodeInfoModelJson(CreatorName.get(), bootTime, bootTime, kbVersion, kbApiVersion, kbPluginApiVersion, kbCommonVersion, kbPlatformVersion,
             pluginInfo.stream().map(PluginInfoModelJson::new).collect(Collectors.toUnmodifiableList()));

        final String nodeInfoValue = mapper.serializeNodeInfo(nodeInfo);
        final NodeInfoModelDao bootNodeInfo = new NodeInfoModelDao(CreatorName.get(), clock.getUTCNow(), nodeInfoValue);
        nodeInfoDao.create(bootNodeInfo);
    }
}
