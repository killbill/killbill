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

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OverriddenPlanCacheLoader extends BaseCacheLoader<String, Plan> {

    private final Logger log = LoggerFactory.getLogger(OverriddenPlanCacheLoader.class);

    @Inject
    public OverriddenPlanCacheLoader() {
        super();
    }

    @Override
    public CacheType getCacheType() {
        return CacheType.OVERRIDDEN_PLAN;
    }

    @Override
    public Plan compute(final String key, final CacheLoaderArgument cacheLoaderArgument) {
        if (cacheLoaderArgument.getArgs() == null || cacheLoaderArgument.getArgs().length != 2) {
            throw new IllegalArgumentException("Invalid arguments for overridden plans");
        }
        if (!(cacheLoaderArgument.getArgs()[0] instanceof LoaderCallback)) {
            throw new IllegalArgumentException("Invalid arguments for overridden plans: missing loaderCallback from argument");
        }

        if (!(cacheLoaderArgument.getArgs()[1] instanceof StaticCatalog)) {
            throw new IllegalArgumentException("Invalid arguments for overridden plans: missing catalog from argument");
        }

        final String planName = key;
        final LoaderCallback callback = (LoaderCallback) cacheLoaderArgument.getArgs()[0];
        final StaticCatalog catalog = (StaticCatalog) cacheLoaderArgument.getArgs()[1];
        final InternalTenantContext internalTenantContext = cacheLoaderArgument.getInternalTenantContext();
        try {
            log.info("Loading overridden plan {} for tenant {}", planName, internalTenantContext.getTenantRecordId());

            return callback.loadPlan(planName, catalog, internalTenantContext);
        } catch (final CatalogApiException e) {
            throw new IllegalStateException(String.format("Failed to load overridden plan for tenant %s : %s",
                                                          planName, internalTenantContext.getTenantRecordId()), e);
        }
    }

    public interface LoaderCallback {

        public Plan loadPlan(final String planName, final StaticCatalog catalog, final InternalTenantContext context) throws CatalogApiException;
    }
}
