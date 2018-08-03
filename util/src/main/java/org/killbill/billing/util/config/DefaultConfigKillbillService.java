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

package org.killbill.billing.util.config;

import javax.inject.Inject;
import javax.inject.Named;

import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.billing.tenant.api.TenantInternalApi;
import org.killbill.billing.tenant.api.TenantInternalApi.CacheInvalidationCallback;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.killbill.billing.util.glue.CacheModule;
import org.killbill.billing.util.glue.ConfigModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultConfigKillbillService implements ConfigKillbillService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultConfigKillbillService.class);

    private final TenantInternalApi tenantInternalApi;
    private final CacheInvalidationCallback cacheInvalidationCallback;

    @Inject
    public DefaultConfigKillbillService(final TenantInternalApi tenantInternalApi, @Named(ConfigModule.CONFIG_INVALIDATION_CALLBACK) final CacheInvalidationCallback cacheInvalidationCallback) {
        this.tenantInternalApi = tenantInternalApi;
        this.cacheInvalidationCallback = cacheInvalidationCallback;
    }

    @Override
    public String getName() {
        return KILLBILL_SERVICES.CONFIG_SERVICE.getServiceName();
    }

    @Override
    public int getRegistrationOrdering() {
        return KILLBILL_SERVICES.CONFIG_SERVICE.getRegistrationOrdering();
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public synchronized void initialize() throws ServiceException {
        tenantInternalApi.initializeCacheInvalidationCallback(TenantKey.PER_TENANT_CONFIG, cacheInvalidationCallback);
    }

}
