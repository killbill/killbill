/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import javax.inject.Named;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.catalog.caching.CatalogCache;
import org.killbill.billing.catalog.glue.CatalogModule;
import org.killbill.billing.catalog.io.VersionedCatalogLoader;
import org.killbill.billing.platform.api.KillbillService;
import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.billing.tenant.api.TenantInternalApi;
import org.killbill.billing.tenant.api.TenantInternalApi.CacheInvalidationCallback;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.killbill.billing.util.config.definition.CatalogConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class DefaultCatalogService implements KillbillService, CatalogService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCatalogService.class);

    private final CatalogConfig config;
    private final TenantInternalApi tenantInternalApi;
    private final CatalogCache catalogCache;
    private final CacheInvalidationCallback cacheInvalidationCallback;
    private final VersionedCatalogLoader versionedCatalogLoader;
    private boolean isInitialized;

    @Inject
    public DefaultCatalogService(final CatalogConfig config,
                                 final TenantInternalApi tenantInternalApi,
                                 final CatalogCache catalogCache,
                                 @Named(CatalogModule.CATALOG_INVALIDATION_CALLBACK) final CacheInvalidationCallback cacheInvalidationCallback,
                                 final VersionedCatalogLoader versionedCatalogLoader) {
        this.config = config;
        this.catalogCache = catalogCache;
        this.cacheInvalidationCallback = cacheInvalidationCallback;
        this.tenantInternalApi = tenantInternalApi;
        this.versionedCatalogLoader = versionedCatalogLoader;
        this.isInitialized = false;
    }

    @LifecycleHandlerType(LifecycleLevel.LOAD_CATALOG)
    public synchronized void loadCatalog() throws ServiceException {
        if (!isInitialized) {
            try {
                // In multi-tenant mode, the property is not required
                if (config.getCatalogURI() != null && !config.getCatalogURI().isEmpty()) {
                    catalogCache.loadDefaultCatalog(config.getCatalogURI());
                    log.info("Successfully loaded the default catalog {}", config.getCatalogURI());
                }
                isInitialized = true;
            } catch (final Exception e) {
                throw new ServiceException(e);
            }
        }
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public synchronized void initialize() throws ServiceException {
        tenantInternalApi.initializeCacheInvalidationCallback(TenantKey.CATALOG, cacheInvalidationCallback);
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() {
        versionedCatalogLoader.close();
    }

    @Override
    public String getName() {
        return KILLBILL_SERVICES.CATALOG_SERVICE.getServiceName();
    }

    @Override
    public int getRegistrationOrdering() {
        return KILLBILL_SERVICES.CATALOG_SERVICE.getRegistrationOrdering();
    }

    @Override
    public VersionedCatalog getFullCatalog(final boolean useDefaultCatalog, final boolean filterTemplateCatalog, final InternalTenantContext context) throws CatalogApiException {
        return getCatalog(useDefaultCatalog, filterTemplateCatalog, false, context);
    }

    @Override
    public VersionedCatalog getFullCatalogForInternalUse(final boolean useDefaultCatalog, final boolean filterTemplateCatalog, final InternalTenantContext context) throws CatalogApiException {
        return getCatalog(useDefaultCatalog, filterTemplateCatalog, true, context);
    }

    private VersionedCatalog getCatalog(final boolean useDefaultCatalog, final boolean filterTemplateCatalog, final boolean internalUse, final InternalTenantContext context) throws CatalogApiException {
        return catalogCache.getCatalog(useDefaultCatalog, filterTemplateCatalog, internalUse, context);
    }
}
