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

package org.killbill.billing.catalog;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.caching.CatalogCache;
import org.killbill.billing.catalog.caching.EhCacheCatalogCache;
import org.killbill.billing.catalog.io.VersionedCatalogLoader;
import org.killbill.billing.platform.api.KillbillService;
import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.config.CatalogConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class DefaultCatalogService implements KillbillService, CatalogService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCatalogService.class);
    private static final String CATALOG_SERVICE_NAME = "catalog-service";

    private final CatalogConfig config;
    private boolean isInitialized;

    private final CatalogCache catalogCache;

    @Inject
    public DefaultCatalogService(final CatalogConfig config,
                                 final VersionedCatalogLoader loader,
                                 final CacheControllerDispatcher cacheControllerDispatcher) {
        this.config = config;
        this.isInitialized = false;
        this.catalogCache = new EhCacheCatalogCache(cacheControllerDispatcher.getCacheController(CacheType.TENANT_CATALOG), loader);
    }

    @LifecycleHandlerType(LifecycleLevel.LOAD_CATALOG)
    public synchronized void loadCatalog() throws ServiceException {
        if (!isInitialized) {
            try {
                // In multi-tenant mode, the property is not required
                if (config.getCatalogURI() != null && !config.getCatalogURI().isEmpty()) {
                    catalogCache.loadDefaultCatalog(config.getCatalogURI());
                    log.info("Successfully loaded the default catalog " + config.getCatalogURI());
                }
                isInitialized = true;
            } catch (Exception e) {
                throw new ServiceException(e);
            }
        }
    }

    @Override
    public String getName() {
        return CATALOG_SERVICE_NAME;
    }

    @Override
    public Catalog getFullCatalog(final InternalTenantContext context) throws CatalogApiException {
        return getCatalog(context);
    }

    @Override
    public StaticCatalog getCurrentCatalog(final InternalTenantContext context) throws CatalogApiException {
        return getCatalog(context);
    }

    private VersionedCatalog getCatalog(final InternalTenantContext context) throws CatalogApiException {
        return catalogCache.getCatalog(context);
    }
}
