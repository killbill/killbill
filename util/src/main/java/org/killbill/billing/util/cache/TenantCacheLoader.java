/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.util.cache;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantInternalApi;
import org.killbill.billing.util.cache.Cachable.CacheType;

@Singleton
public class TenantCacheLoader extends BaseCacheLoader<String, Tenant> {

    private final TenantInternalApi tenantApi;

    @Inject
    public TenantCacheLoader(final TenantInternalApi tenantApi) {
        super();
        this.tenantApi = tenantApi;
    }

    @Override
    public CacheType getCacheType() {
        return CacheType.TENANT;
    }

    @Override
    public Tenant compute(final String key, final CacheLoaderArgument cacheLoaderArgument) {
        try {
            return tenantApi.getTenantByApiKey(key);
        } catch (final TenantApiException e) {
            throw new IllegalStateException("TenantCacheLoader cannot find value for key " + key);
        }
    }
}
