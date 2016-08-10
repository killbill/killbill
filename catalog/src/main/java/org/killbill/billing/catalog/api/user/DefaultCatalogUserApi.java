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

package org.killbill.billing.catalog.api.user;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.CatalogUpdater;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.StandaloneCatalogWithPriceOverride;
import org.killbill.billing.catalog.VersionedCatalog;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.CatalogUserApi;
import org.killbill.billing.catalog.api.SimplePlanDescriptor;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.caching.CatalogCache;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.xmlloader.XMLLoader;

public class DefaultCatalogUserApi implements CatalogUserApi {

    private final CatalogService catalogService;
    private final InternalCallContextFactory internalCallContextFactory;
    private final TenantUserApi tenantApi;
    private final CatalogCache catalogCache;


    @Inject
    public DefaultCatalogUserApi(final CatalogService catalogService,
                                 final TenantUserApi tenantApi,
                                 final CatalogCache catalogCache,
                                 final InternalCallContextFactory internalCallContextFactory) {
        this.catalogService = catalogService;
        this.tenantApi = tenantApi;
        this.catalogCache = catalogCache;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public Catalog getCatalog(final String catalogName, final TenantContext tenantContext) throws CatalogApiException {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(tenantContext);
        return catalogService.getFullCatalog(true, true, internalTenantContext);
    }

    @Override
    public StaticCatalog getCurrentCatalog(final String catalogName, final TenantContext tenantContext) throws CatalogApiException {
        final InternalTenantContext internalTenantContext = createInternalTenantContext(tenantContext);
        return catalogService.getCurrentCatalog(true, true, internalTenantContext);
    }

    @Override
    public void uploadCatalog(final String catalogXML, final CallContext callContext) throws CatalogApiException {
        try {
            // Validation purpose:  Will throw if bad XML or catalog validation fails
            final InputStream stream = new ByteArrayInputStream(catalogXML.getBytes());
            XMLLoader.getObjectFromStream(new URI("dummy"), stream, StandaloneCatalog.class);

            final InternalTenantContext internalTenantContext = createInternalTenantContext(callContext);
            catalogCache.clearCatalog(internalTenantContext);
            tenantApi.addTenantKeyValue(TenantKey.CATALOG.toString(), catalogXML, callContext);
        } catch (TenantApiException e) {
            throw new CatalogApiException(e);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }


    @Override
    public void createDefaultEmptyCatalog(final DateTime effectiveDate, final CallContext callContext) throws CatalogApiException {

        try {
            final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(callContext);
            final StandaloneCatalog currentCatalog = getCurrentStandaloneCatalogForTenant(internalTenantContext);
            final CatalogUpdater catalogUpdater = (currentCatalog != null) ?
                                                  new CatalogUpdater(currentCatalog) :
                                                  new CatalogUpdater("dummy", BillingMode.IN_ADVANCE, effectiveDate, null);

            catalogCache.clearCatalog(internalTenantContext);
            tenantApi.updateTenantKeyValue(TenantKey.CATALOG.toString(), catalogUpdater.getCatalogXML(), callContext);
        } catch (TenantApiException e) {
            throw new CatalogApiException(e);
        }
    }

    @Override
    public void addSimplePlan(final SimplePlanDescriptor descriptor, final DateTime effectiveDate, final CallContext callContext) throws CatalogApiException {

        try {
            final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(callContext);
            final StandaloneCatalog currentCatalog = getCurrentStandaloneCatalogForTenant(internalTenantContext);
            final CatalogUpdater catalogUpdater = (currentCatalog != null) ?
                                                  new CatalogUpdater(currentCatalog) :
                                                  new CatalogUpdater("dummy", BillingMode.IN_ADVANCE, effectiveDate, descriptor.getCurrency());

            catalogUpdater.addSimplePlanDescriptor(descriptor);

            catalogCache.clearCatalog(internalTenantContext);
            tenantApi.updateTenantKeyValue(TenantKey.CATALOG.toString(), catalogUpdater.getCatalogXML(), callContext);
        } catch (TenantApiException e) {
            throw new CatalogApiException(e);
        }
    }


    private StandaloneCatalog getCurrentStandaloneCatalogForTenant(final InternalTenantContext internalTenantContext) throws CatalogApiException {
        final VersionedCatalog versionedCatalog = (VersionedCatalog) catalogService.getCurrentCatalog(false, false, internalTenantContext);
        if (versionedCatalog != null && !versionedCatalog.getVersions().isEmpty()) {
            final StandaloneCatalog standaloneCatalogWithPriceOverride = versionedCatalog.getVersions().get(versionedCatalog.getVersions().size() - 1);
            return standaloneCatalogWithPriceOverride;
        } else {
            return null;
        }
    }

    private InternalTenantContext createInternalTenantContext(final TenantContext tenantContext) {
        // Only tenantRecordId will be populated -- this is important to always create the (ehcache) key the same way
        return internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(tenantContext);
    }

}
