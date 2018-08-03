/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.overdue.service;

import javax.inject.Named;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.lifecycle.api.BusService;
import org.killbill.billing.overdue.OverdueProperties;
import org.killbill.billing.overdue.OverdueService;
import org.killbill.billing.overdue.api.OverdueApiException;
import org.killbill.billing.overdue.api.OverdueConfig;
import org.killbill.billing.overdue.caching.OverdueConfigCache;
import org.killbill.billing.overdue.glue.DefaultOverdueModule;
import org.killbill.billing.overdue.listener.OverdueListener;
import org.killbill.billing.overdue.notification.OverdueNotifier;
import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.billing.tenant.api.TenantInternalApi;
import org.killbill.billing.tenant.api.TenantInternalApi.CacheInvalidationCallback;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class DefaultOverdueService implements OverdueService {

    private static final Logger log = LoggerFactory.getLogger(DefaultOverdueService.class);

    private final OverdueProperties properties;
    private final OverdueNotifier asyncNotifier;
    private final OverdueNotifier checkNotifier;
    private final BusService busService;
    private final OverdueListener listener;

    private final OverdueConfigCache overdueConfigCache;
    private final CacheInvalidationCallback overdueCacheInvalidationCallback;

    private final TenantInternalApi tenantInternalApi;
    private boolean isConfigLoaded;

    @Inject
    public DefaultOverdueService(final OverdueProperties properties,
                                 @Named(DefaultOverdueModule.OVERDUE_NOTIFIER_CHECK_NAMED) final OverdueNotifier checkNotifier,
                                 @Named(DefaultOverdueModule.OVERDUE_NOTIFIER_ASYNC_BUS_NAMED) final OverdueNotifier asyncNotifier,
                                 final BusService busService,
                                 final OverdueListener listener,
                                 final OverdueConfigCache overdueConfigCache,
                                 @Named(DefaultOverdueModule.OVERDUE_INVALIDATION_CALLBACK) final CacheInvalidationCallback overdueCacheInvalidationCallback,
                                 final TenantInternalApi tenantInternalApi) {
        this.properties = properties;
        this.checkNotifier = checkNotifier;
        this.asyncNotifier = asyncNotifier;
        this.busService = busService;
        this.listener = listener;
        this.isConfigLoaded = false;
        this.overdueConfigCache = overdueConfigCache;
        this.overdueCacheInvalidationCallback = overdueCacheInvalidationCallback;
        this.tenantInternalApi = tenantInternalApi;
    }

    @Override
    public String getName() {
        return KILLBILL_SERVICES.OVERDUE_SERVICE.getServiceName();
    }

    @Override
    public int getRegistrationOrdering() {
        return KILLBILL_SERVICES.OVERDUE_SERVICE.getRegistrationOrdering();
    }

    @LifecycleHandlerType(LifecycleLevel.LOAD_CATALOG)
    public synchronized void loadConfig() throws ServiceException {
        if (!isConfigLoaded) {
            try {
                overdueConfigCache.loadDefaultOverdueConfig(properties.getConfigURI());
                isConfigLoaded = true;
            } catch (OverdueApiException e) {
                log.warn("Overdue system disabled: unable to load the overdue config from uri='{}'", properties.getConfigURI(), e);
            }
        }
    }

    @LifecycleHandlerType(LifecycleHandlerType.LifecycleLevel.INIT_SERVICE)
    public void initialize() {
        registerForBus();
        checkNotifier.initialize();
        asyncNotifier.initialize();
        tenantInternalApi.initializeCacheInvalidationCallback(TenantKey.OVERDUE_CONFIG, overdueCacheInvalidationCallback);
    }

    private void registerForBus() {
        try {
            busService.getBus().register(listener);
        } catch (final EventBusException e) {
            log.error("Failed to register OverdueListener", e);
        }
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        checkNotifier.start();
        asyncNotifier.start();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() {
        try {
            busService.getBus().unregister(listener);
        } catch (final EventBusException e) {
            log.error("Failed to unregister OverdueListener", e);
        }
        checkNotifier.stop();
        asyncNotifier.stop();
    }

    @Override
    public OverdueConfig getOverdueConfig(final InternalTenantContext internalTenantContext) throws OverdueApiException {
        return overdueConfigCache.getOverdueConfig(internalTenantContext);
    }
}
