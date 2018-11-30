/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

import javax.inject.Inject;

import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.killbill.billing.tenant.api.user.DefaultTenantUserApi;

public class DefaultTenantService implements TenantService {

    private final TenantCacheInvalidation tenantCacheInvalidation;
    private final TenantCacheInvalidationCallback tenantCacheInvalidationCallback;

    @Inject
    public DefaultTenantService(final TenantCacheInvalidation tenantCacheInvalidation, final TenantCacheInvalidationCallback tenantCacheInvalidationCallback) {
        this.tenantCacheInvalidation = tenantCacheInvalidation;
        this.tenantCacheInvalidationCallback = tenantCacheInvalidationCallback;
    }

    @Override
    public String getName() {
        return KILLBILL_SERVICES.TENANT_SERVICE.getServiceName();
    }

    @Override
    public int getRegistrationOrdering() {
        return KILLBILL_SERVICES.TENANT_SERVICE.getRegistrationOrdering();
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void initialize() {
        tenantCacheInvalidation.initialize();
        for (TenantKey cacheableKey : DefaultTenantUserApi.CACHED_TENANT_KEY) {
            tenantCacheInvalidation.registerCallback(cacheableKey, tenantCacheInvalidationCallback);
        }
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        tenantCacheInvalidation.start();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() {
        tenantCacheInvalidation.stop();
    }
}
