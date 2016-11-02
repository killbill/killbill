/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;
import javax.inject.Named;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.events.BusInternalEvent;
import org.killbill.billing.tenant.api.TenantInternalApi.CacheInvalidationCallback;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.killbill.billing.tenant.api.user.DefaultTenantConfigChangeInternalEvent;
import org.killbill.billing.tenant.api.user.DefaultTenantConfigDeletionInternalEvent;
import org.killbill.billing.tenant.dao.TenantBroadcastDao;
import org.killbill.billing.tenant.dao.TenantBroadcastModelDao;
import org.killbill.billing.tenant.dao.TenantDao;
import org.killbill.billing.tenant.dao.TenantKVModelDao;
import org.killbill.billing.tenant.glue.DefaultTenantModule;
import org.killbill.billing.util.config.definition.TenantConfig;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.commons.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

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

    private final Multimap<TenantKey, CacheInvalidationCallback> cache;
    private final TenantBroadcastDao broadcastDao;
    private final TenantConfig tenantConfig;
    private final PersistentBus eventBus;
    private final TenantDao tenantDao;
    private AtomicLong latestRecordIdProcessed;
    private volatile boolean isStopped;

    private ScheduledExecutorService tenantExecutor;

    @Inject
    public TenantCacheInvalidation(@Named(DefaultTenantModule.NO_CACHING_TENANT) final TenantBroadcastDao broadcastDao,
                                   @Named(DefaultTenantModule.NO_CACHING_TENANT) final TenantDao tenantDao,
                                   final PersistentBus eventBus,
                                   final TenantConfig tenantConfig) {
        this.cache = HashMultimap.<TenantKey, CacheInvalidationCallback>create();
        this.broadcastDao = broadcastDao;
        this.tenantConfig = tenantConfig;
        this.tenantDao = tenantDao;
        this.eventBus = eventBus;
        this.isStopped = false;
    }

    public void initialize() {
        final TenantBroadcastModelDao entry = broadcastDao.getLatestEntry();
        this.latestRecordIdProcessed = entry != null ? new AtomicLong(entry.getRecordId()) : new AtomicLong(0L);
        this.tenantExecutor = Executors.newSingleThreadScheduledExecutor("TenantExecutor");
        this.isStopped = false;
    }

    public void start() {
        final TimeUnit pendingRateUnit = tenantConfig.getTenantBroadcastServiceRunningRate().getUnit();
        final long pendingPeriod = tenantConfig.getTenantBroadcastServiceRunningRate().getPeriod();
        tenantExecutor.scheduleAtFixedRate(new TenantCacheInvalidationRunnable(this, broadcastDao, tenantDao), pendingPeriod, pendingPeriod, pendingRateUnit);

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

    public void registerCallback(final TenantKey key, final CacheInvalidationCallback value) {
        cache.put(key, value);

    }

    public Collection<CacheInvalidationCallback> getCacheInvalidations(final TenantKey key) {
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

    public PersistentBus getEventBus() {
        return eventBus;
    }

    public static class TenantCacheInvalidationRunnable implements Runnable {

        private final TenantCacheInvalidation parent;
        private final TenantBroadcastDao broadcastDao;
        private final TenantDao tenantDao;

        public TenantCacheInvalidationRunnable(final TenantCacheInvalidation parent,
                                               final TenantBroadcastDao broadcastDao,
                                               final TenantDao tenantDao) {
            this.parent = parent;
            this.broadcastDao = broadcastDao;
            this.tenantDao = tenantDao;
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

                try {
                    final TenantKeyAndCookie tenantKeyAndCookie = extractTenantKeyAndCookie(cur.getType());
                    if (tenantKeyAndCookie != null) {
                        final Collection<CacheInvalidationCallback> callbacks = parent.getCacheInvalidations(tenantKeyAndCookie.getTenantKey());
                        if (!callbacks.isEmpty()) {
                            final InternalTenantContext tenantContext = new InternalTenantContext(cur.getTenantRecordId());
                            for (final CacheInvalidationCallback callback : callbacks) {
                                callback.invalidateCache(tenantKeyAndCookie.getTenantKey(), tenantKeyAndCookie.getCookie(), tenantContext);
                            }

                            final Long tenantKvsTargetRecordId = cur.getTargetRecordId();
                            final BusInternalEvent event;
                            if (tenantKvsTargetRecordId != null) {
                                final TenantKVModelDao tenantModelDao = tenantDao.getKeyByRecordId(tenantKvsTargetRecordId, tenantContext);
                                if (tenantModelDao == null) {
                                    // Probably inactive entry
                                    continue;
                                }
                                event = new DefaultTenantConfigChangeInternalEvent(tenantModelDao.getId(), cur.getType(),
                                                                                   null, tenantContext.getTenantRecordId(), cur.getUserToken());
                            } else {
                                event = new DefaultTenantConfigDeletionInternalEvent(cur.getType(),
                                                                                     null, tenantContext.getTenantRecordId(), cur.getUserToken());
                            }
                            try {
                                parent.getEventBus().post(event);
                            } catch (final EventBusException e) {
                                logger.warn("Failed to post event {}", event, e);
                            }
                        }
                    } else {
                        logger.warn("Failed to find CacheInvalidationCallback for " + cur.getType());
                    }
                } finally {
                    parent.setLatestRecordIdProcessed(cur.getRecordId());
                }
            }
        }

        private TenantKeyAndCookie extractTenantKeyAndCookie(final String key) {
            final TenantKey tenantKey = Iterables.tryFind(ImmutableList.copyOf(TenantKey.values()), new Predicate<TenantKey>() {
                @Override
                public boolean apply(final TenantKey input) {
                    return key.startsWith(input.toString());
                }
            }).orNull();
            if (tenantKey == null) {
                return null;
            }

            final String cookie = !key.equals(tenantKey.toString()) ?
                                  key.substring(tenantKey.toString().length()) :
                                  null;
            return new TenantKeyAndCookie(tenantKey, cookie);
        }

    }

    private static final class TenantKeyAndCookie {

        private final TenantKey tenantKey;
        private final Object cookie;

        public TenantKeyAndCookie(final TenantKey tenantKey, final Object cookie) {
            this.tenantKey = tenantKey;
            this.cookie = cookie;
        }

        public TenantKey getTenantKey() {
            return tenantKey;
        }

        public Object getCookie() {
            return cookie;
        }
    }
}
