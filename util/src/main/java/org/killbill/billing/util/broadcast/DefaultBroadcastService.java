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

package org.killbill.billing.util.broadcast;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;

import org.killbill.billing.events.BroadcastInternalEvent;
import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.billing.util.broadcast.dao.BroadcastDao;
import org.killbill.billing.util.broadcast.dao.BroadcastModelDao;
import org.killbill.billing.util.config.definition.BroadcastConfig;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.commons.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultBroadcastService implements BroadcastService {

    private final static int TERMINATION_TIMEOUT_SEC = 5;

    private static final Logger logger = LoggerFactory.getLogger(DefaultBroadcastService.class);

    public static final String BROADCAST_SERVICE_NAME = "broadcast-service";

    private final BroadcastConfig broadcastConfig;
    private final BroadcastDao broadcastDao;
    private final PersistentBus eventBus;

    private AtomicLong latestRecordIdProcessed;
    private ScheduledExecutorService broadcastExecutor;
    private volatile boolean isStopped;

    @Inject
    public DefaultBroadcastService(final BroadcastDao broadcastDao, final BroadcastConfig broadcastConfig, final PersistentBus eventBus) {
        this.broadcastDao = broadcastDao;
        this.broadcastConfig = broadcastConfig;
        this.eventBus = eventBus;
        this.isStopped = false;
    }

    @Override
    public String getName() {
        return BROADCAST_SERVICE_NAME;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void initialize() {
        final BroadcastModelDao entry = broadcastDao.getLatestEntry();
        this.latestRecordIdProcessed = entry != null ? new AtomicLong(entry.getRecordId()) : new AtomicLong(0L);
        this.broadcastExecutor = Executors.newSingleThreadScheduledExecutor("BroadcastExecutor");
        this.isStopped = false;
    }

    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.START_SERVICE)
    public void start() {
        final TimeUnit pendingRateUnit = broadcastConfig.getBroadcastServiceRunningRate().getUnit();
        final long pendingPeriod = broadcastConfig.getBroadcastServiceRunningRate().getPeriod();
        broadcastExecutor.scheduleAtFixedRate(new BroadcastServiceRunnable(this, broadcastDao, eventBus), pendingPeriod, pendingPeriod, pendingRateUnit);

    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() {
        if (isStopped) {
            logger.warn("BroadcastExecutor is already in a stopped state");
            return;
        }
        try {
            broadcastExecutor.shutdown();
            boolean success = broadcastExecutor.awaitTermination(TERMINATION_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!success) {
                logger.warn("BroadcastExecutor failed to complete termination within {} sec", TERMINATION_TIMEOUT_SEC);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("BroadcastExecutor stop sequence got interrupted");
        } finally {
            isStopped = true;
        }
    }

    public boolean isStopped() {
        return isStopped;
    }

    public AtomicLong getLatestRecordIdProcessed() {
        return latestRecordIdProcessed;
    }

    public void setLatestRecordIdProcessed(final Long latestRecordIdProcessed) {
        this.latestRecordIdProcessed.set(latestRecordIdProcessed);
    }

    private static class BroadcastServiceRunnable implements Runnable {

        private final DefaultBroadcastService parent;
        private final BroadcastDao broadcastDao;
        private final PersistentBus eventBus;

        public BroadcastServiceRunnable(final DefaultBroadcastService defaultBroadcastService, final BroadcastDao broadcastDao, final PersistentBus eventBus) {
            this.parent = defaultBroadcastService;
            this.broadcastDao = broadcastDao;
            this.eventBus = eventBus;
        }

        @Override
        public void run() {
            if (parent.isStopped) {
                return;
            }

            final List<BroadcastModelDao> entries = broadcastDao.getLatestEntriesFrom(parent.getLatestRecordIdProcessed().get());
            for (BroadcastModelDao cur : entries) {
                if (parent.isStopped()) {
                    return;
                }

                final BroadcastInternalEvent event = new DefaultBroadcastInternalEvent(cur.getServiceName(), cur.getType(), cur.getEvent());
                try {
                    eventBus.post(event);
                } catch (final EventBusException e) {
                    logger.warn("Failed to post event {}", event, e);
                } finally {
                    parent.setLatestRecordIdProcessed(cur.getRecordId());
                }
            }
        }
    }
}
