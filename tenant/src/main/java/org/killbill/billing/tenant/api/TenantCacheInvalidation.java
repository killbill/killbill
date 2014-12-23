/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.tenant.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;
import javax.inject.Named;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.tenant.api.TenantInternalApi.CacheInvalidationCallback;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.killbill.billing.tenant.dao.TenantBroadcastDao;
import org.killbill.billing.tenant.dao.TenantBroadcastModelDao;
import org.killbill.billing.tenant.glue.DefaultTenantModule;
import org.killbill.billing.util.config.TenantConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class manages the callbacks that have been registered when per tenant objects have been inserted into the
 * tenant_kvs store; the flow is the following (for e.g catalog):
 * 1. CatalogUserApi is invoked to retrieve per tenant catalog
 * 2. If cache is empty, TenantCacheLoader is invoked and uses TenantInternalApi is load the data; at that time, the invalidation callback
 * is registered
 * <p/>
 * When this class initializes, it reads the current entry in the tenant_broadcasts table and from then on, keeps polling for new entries; when new
 * entries are found, it invokes the callback to invalidate the current caching and force the TenantCacheLoader to be invoked again.
 */
public class TenantCacheInvalidation {

    private final static int TERMINATION_TIMEOUT_SEC = 5;
    private static final Logger logger = LoggerFactory.getLogger(TenantCacheInvalidation.class);

    private final Map<CacheInvalidationKey, CacheInvalidationCallback> cache;
    private final TenantBroadcastDao broadcastDao;
    private final ScheduledExecutorService tenantExecutor;
    private final TenantConfig tenantConfig;
    private AtomicLong latestRecordIdProcessed;
    private volatile boolean isStopped;

    @Inject
    public TenantCacheInvalidation(@Named(DefaultTenantModule.NO_CACHING_TENANT) final TenantBroadcastDao broadcastDao,
                                   @Named(DefaultTenantModule.TENANT_EXECUTOR_NAMED) final ScheduledExecutorService tenantExecutor,
                                   final TenantConfig tenantConfig) {
        this.cache = new HashMap<CacheInvalidationKey, CacheInvalidationCallback>();
        this.broadcastDao = broadcastDao;
        this.tenantExecutor = tenantExecutor;
        this.tenantConfig = tenantConfig;
        this.isStopped = false;
    }

    public void initialize() {
        final TenantBroadcastModelDao entry = broadcastDao.getLatestEntry();
        this.latestRecordIdProcessed = entry != null ? new AtomicLong(entry.getRecordId()) : new AtomicLong(0L);

    }

    public void start() {
        if (isStopped) {
            logger.warn("TenantExecutor is in a stopped state, abort start sequence");
            return;
        }
        final TimeUnit pendingRateUnit = tenantConfig.getTenantBroadcastServiceRunningRate().getUnit();
        final long pendingPeriod = tenantConfig.getTenantBroadcastServiceRunningRate().getPeriod();
        tenantExecutor.scheduleAtFixedRate(new TenantCacheInvalidationRunnable(this, broadcastDao), pendingPeriod, pendingPeriod, pendingRateUnit);

    }

    public void stop() {
        if (isStopped) {
            logger.warn("TenantExecutor is already in a stopped state");
            return;
        }
        try {
            tenantExecutor.shutdown();
            boolean success = tenantExecutor.awaitTermination(TERMINATION_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!success) {
                logger.warn("TenantExecutor failed to complete termination within " + TERMINATION_TIMEOUT_SEC + "sec");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("TenantExecutor stop sequence got interrupted");
        } finally {
            isStopped = true;
        }
    }

    public void registerCallback(final CacheInvalidationKey key, final CacheInvalidationCallback value) {
        if (!cache.containsKey(key)) {
            cache.put(key, value);
        }
    }

    public CacheInvalidationCallback getCacheInvalidation(final CacheInvalidationKey key) {
        return cache.get(key);
    }

    public AtomicLong getLatestRecordIdProcessed() {
        return latestRecordIdProcessed;
    }

    public boolean isStopped() {
        return isStopped;
    }

    public void setLatestRecordIdProcessed(final Long newProcessedRecordId) {
        this.latestRecordIdProcessed.set(newProcessedRecordId);
    }

    public static class TenantCacheInvalidationRunnable implements Runnable {

        private final TenantCacheInvalidation parent;
        private final TenantBroadcastDao broadcastDao;

        public TenantCacheInvalidationRunnable(final TenantCacheInvalidation parent,
                                               final TenantBroadcastDao broadcastDao) {
            this.parent = parent;
            this.broadcastDao = broadcastDao;
        }

        @Override
        public void run() {
            if (parent.isStopped) {
                return;
            }
            final List<TenantBroadcastModelDao> entries = broadcastDao.getLatestEntriesFrom(parent.getLatestRecordIdProcessed().get());
            for (TenantBroadcastModelDao cur : entries) {
                if (parent.isStopped()) {
                    return;
                }

                final CacheInvalidationKey key = new CacheInvalidationKey(cur.getTenantRecordId(), TenantKey.valueOf(cur.getType()));
                final CacheInvalidationCallback callback = parent.getCacheInvalidation(key);
                if (callback != null) {
                    final InternalTenantContext tenantContext = new InternalTenantContext(cur.getTenantRecordId(), null);
                    callback.invalidateCache(tenantContext);
                }
                parent.setLatestRecordIdProcessed(cur.getRecordId());
            }
        }
    }

    public static final class CacheInvalidationKey {

        private final Long tenantRecordId;
        private final TenantKey type;

        public CacheInvalidationKey(final Long tenantRecordId, final TenantKey type) {
            this.tenantRecordId = tenantRecordId;
            this.type = type;
        }

        public Long getTenantRecordId() {
            return tenantRecordId;
        }

        public TenantKey getType() {
            return type;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CacheInvalidationKey)) {
                return false;
            }

            final CacheInvalidationKey that = (CacheInvalidationKey) o;
            if (tenantRecordId != null ? !tenantRecordId.equals(that.tenantRecordId) : that.tenantRecordId != null) {
                return false;
            }
            if (type != that.type) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int result = tenantRecordId != null ? tenantRecordId.hashCode() : 0;
            result = 31 * result + (type != null ? type.hashCode() : 0);
            return result;
        }
    }
}
