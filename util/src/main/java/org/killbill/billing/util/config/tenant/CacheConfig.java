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

package org.killbill.billing.util.config.tenant;

import java.io.IOException;

import javax.annotation.Nullable;

import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.cache.CacheLoaderArgument;
import org.killbill.billing.util.cache.TenantConfigCacheLoader.LoaderCallback;
import org.killbill.billing.util.jackson.ObjectMapper;

import com.google.inject.Inject;

public class CacheConfig {

    private final CacheController<Long, PerTenantConfig> cacheController;
    private final CacheLoaderArgument cacheLoaderArgument;

    private final ObjectMapper objectMapper;

    @Inject
    public CacheConfig(final CacheControllerDispatcher cacheControllerDispatcher) {
        this.cacheController = cacheControllerDispatcher.getCacheController(CacheType.TENANT_CONFIG);
        this.objectMapper = new ObjectMapper();
        this.cacheLoaderArgument = initializeCacheLoaderArgument();

    }

    public PerTenantConfig getPerTenantConfig(final InternalTenantContext tenantContext) {
        final PerTenantConfig perTenantConfig = cacheController.get(tenantContext.getTenantRecordId(), cacheLoaderArgument);
        return perTenantConfig;
    }

    public void clearPerTenantConfig(final InternalTenantContext tenantContext) {
        cacheController.remove(tenantContext.getTenantRecordId());
    }

    private CacheLoaderArgument initializeCacheLoaderArgument() {
        final LoaderCallback loaderCallback = new LoaderCallback() {
            @Override
            public PerTenantConfig loadConfig(@Nullable final String inputJson) throws IOException {
                return inputJson != null ? objectMapper.readValue(inputJson, PerTenantConfig.class) : new PerTenantConfig();
            }
        };
        final Object[] args = new Object[1];
        args[0] = loaderCallback;
        final ObjectType irrelevant = null;
        final InternalTenantContext notUsed = null;
        return new CacheLoaderArgument(irrelevant, args, notUsed);
    }
}
