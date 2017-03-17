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

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.tenant.api.TenantInternalApi;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.config.tenant.PerTenantConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TenantConfigCacheLoader extends BaseCacheLoader<Long, PerTenantConfig> {

    private final Logger log = LoggerFactory.getLogger(TenantConfigCacheLoader.class);

    private final TenantInternalApi tenantApi;

    @Inject
    public TenantConfigCacheLoader(final TenantInternalApi tenantApi) {
        super();
        this.tenantApi = tenantApi;
    }

    @Override
    public CacheType getCacheType() {
        return CacheType.TENANT_CONFIG;
    }

    @Override
    public PerTenantConfig compute(final Long key, final CacheLoaderArgument cacheLoaderArgument) {
        final Long tenantRecordId = key;
        final InternalTenantContext internalTenantContext = new InternalTenantContext(tenantRecordId);

        if (cacheLoaderArgument.getArgs() == null || !(cacheLoaderArgument.getArgs()[0] instanceof LoaderCallback)) {
            throw new IllegalArgumentException("Missing LoaderCallback from the arguments ");
        }

        final LoaderCallback loader = (LoaderCallback) cacheLoaderArgument.getArgs()[0];

        final String jsonValue = tenantApi.getTenantConfig(internalTenantContext);

        try {
            return loader.loadConfig(jsonValue);
        } catch (final IOException e) {
            throw new IllegalArgumentException("Failed to deserialize per tenant config for tenant recordId = " + tenantRecordId, e);
        }
    }

    public interface LoaderCallback {

        public PerTenantConfig loadConfig(final String inputJson) throws IOException;
    }
}
