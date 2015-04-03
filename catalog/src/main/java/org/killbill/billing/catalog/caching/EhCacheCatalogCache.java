/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.catalog.caching;

import java.util.List;

import javax.inject.Inject;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.VersionedCatalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.io.VersionedCatalogLoader;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.cache.CacheLoaderArgument;
import org.killbill.billing.util.cache.TenantCatalogCacheLoader.LoaderCallback;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

public class EhCacheCatalogCache implements CatalogCache {

    private final Logger logger = LoggerFactory.getLogger(EhCacheCatalogCache.class);

    private final CacheController cacheController;
    private final VersionedCatalogLoader loader;
    private final CacheLoaderArgument cacheLoaderArgument;

    private VersionedCatalog defaultCatalog;

    @Inject
    public EhCacheCatalogCache(final CacheControllerDispatcher cacheControllerDispatcher, final VersionedCatalogLoader loader) {
        this.cacheController = cacheControllerDispatcher.getCacheController(CacheType.TENANT_CATALOG);
        this.loader = loader;
        this.cacheLoaderArgument = initializeCacheLoaderArgument(this);
        setDefaultCatalog();
    }

    @Override
    public void loadDefaultCatalog(final String url) throws CatalogApiException {
        if (url != null) {
            defaultCatalog = loader.loadDefaultCatalog(url);
        }
    }

    @Override
    public VersionedCatalog getCatalog(final InternalTenantContext tenantContext) throws CatalogApiException {
        if (tenantContext.getTenantRecordId() == InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID) {
            return defaultCatalog;
        }
        // The cache loader might choke on some bad xml -- unlikely since we check its validity prior storing it,
        // but to be on the safe side;;
        try {
            VersionedCatalog tenantCatalog = (VersionedCatalog) cacheController.get(tenantContext.getTenantRecordId(), cacheLoaderArgument);
            // It means we are using a default catalog in a multi-tenant deployment, that does not really match a real use case, but we want to support it
            // for test purpose.
            if (tenantCatalog == null) {
                tenantCatalog = new VersionedCatalog(defaultCatalog, tenantContext);
                cacheController.add(tenantContext.getTenantRecordId(), tenantCatalog);
            }
            return tenantCatalog;
        } catch (final IllegalStateException e) {
            throw new CatalogApiException(ErrorCode.CAT_INVALID_FOR_TENANT, tenantContext.getTenantRecordId());
        }
    }

    @Override
    public void clearCatalog(final InternalTenantContext tenantContext) {
        if (tenantContext.getTenantRecordId() != InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID) {
            cacheController.remove(tenantContext.getTenantRecordId());
        }
    }

    //
    // Build the LoaderCallback that is required to build the catalog from the xml from a module that knows
    // nothing about catalog.
    //
    // This is a contract between the TenantCatalogCacheLoader and the EhCacheCatalogCache
    private CacheLoaderArgument initializeCacheLoaderArgument(final EhCacheCatalogCache parentCache) {
        final LoaderCallback loaderCallback = new LoaderCallback() {
            @Override
            public Object loadCatalog(final List<String> catalogXMLs, final Long tenantRecordId) throws CatalogApiException {
                return loader.load(catalogXMLs, tenantRecordId);
            }
        };
        final Object[] args = new Object[1];
        args[0] = loaderCallback;
        final ObjectType irrelevant = null;
        final InternalTenantContext notUsed = null;
        return new CacheLoaderArgument(irrelevant, args, notUsed);
    }

    @VisibleForTesting
    void setDefaultCatalog() {
        try {
            // Provided in the classpath
            this.defaultCatalog = loader.loadDefaultCatalog("EmptyCatalog.xml");
        } catch (final CatalogApiException e) {
            this.defaultCatalog = new VersionedCatalog();
            logger.warn("Exception loading EmptyCatalog - should never happen!", e);
        }
    }
}
