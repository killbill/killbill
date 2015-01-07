/*
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

package org.killbill.billing.catalog.caching;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.CatalogTestSuiteNoDB;
import org.killbill.billing.catalog.DefaultProduct;
import org.killbill.billing.catalog.VersionedCatalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.tenant.api.TenantInternalApi.CacheInvalidationCallback;
import org.killbill.xmlloader.UriAccessor;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.common.io.Resources;

public class TestEhCacheCatalogCache extends CatalogTestSuiteNoDB {

    final CacheInvalidationCallback cacheInvalidationCallback = Mockito.mock(CacheInvalidationCallback.class);

    @BeforeMethod(groups = "fast")
    protected void beforeMethod() throws Exception {
        cacheControllerDispatcher.clearAll();
    }

    //
    // Verify CatalogCache throws CatalogApiException when used in mono-tenant and catalog system property has not been set
    //
    @Test(groups = "fast", expectedExceptions = CatalogApiException.class)
    public void testMissingDefaultCatalog() throws CatalogApiException {

        final InternalTenantContext tenantContext = Mockito.mock(InternalTenantContext.class);
        Mockito.when(tenantContext.getTenantRecordId()).thenReturn(0L);
        catalogCache.loadDefaultCatalog(null);
        Mockito.when(tenantInternalApi.getTenantCatalogs(tenantContext)).thenReturn(ImmutableList.<String>of());
        catalogCache.getCatalog(internalCallContext);
    }

    //
    // Verify CatalogCache returns default catalog when system property has been set (and CatalogCache has been initialized)
    //
    @Test(groups = "fast")
    public void testDefaultCatalog() throws CatalogApiException {

        final InternalTenantContext tenantContext = Mockito.mock(InternalTenantContext.class);
        Mockito.when(tenantContext.getTenantRecordId()).thenReturn(0L);

        catalogCache.loadDefaultCatalog(Resources.getResource("SpyCarBasic.xml").toExternalForm());
        Mockito.when(tenantInternalApi.getTenantCatalogs(tenantContext)).thenReturn(ImmutableList.<String>of());
        VersionedCatalog result = catalogCache.getCatalog(internalCallContext);
        Assert.assertNotNull(result);
        final DefaultProduct[] products = result.getProducts(clock.getUTCNow());
        Assert.assertEquals(products.length, 3);
    }

    //
    // Verify CatalogCache returns default catalog for the (non 0) tenant when its tenant catalog has not been uploaded
    //
    @Test(groups = "fast")
    public void testMissingTenantCatalog() throws CatalogApiException, URISyntaxException, IOException {

        catalogCache.loadDefaultCatalog(Resources.getResource("SpyCarBasic.xml").toExternalForm());

        final InternalTenantContext tenantContext = Mockito.mock(InternalTenantContext.class);
        Mockito.when(tenantContext.getTenantRecordId()).thenReturn(99L);

        Mockito.when(tenantInternalApi.getTenantCatalogs(Mockito.any(InternalTenantContext.class))).thenReturn(ImmutableList.<String>of());
        VersionedCatalog result = catalogCache.getCatalog(tenantContext);
        Assert.assertNotNull(result);
        final DefaultProduct[] products = result.getProducts(clock.getUTCNow());
        Assert.assertEquals(products.length, 3);
    }

    //
    // Verify CatalogCache returns per tenant catalog:
    // 1. We first mock TenantInternalApi to return a different catalog than the default one
    // 2. We then mock TenantInternalApi to throw RuntimeException which means catalog was cached and there was no additional call
    //    to the TenantInternalApi api (otherwise test would fail with RuntimeException)
    //
    @Test(groups = "fast")
    public void testExistingTenantCatalog() throws CatalogApiException, URISyntaxException, IOException {

        catalogCache.loadDefaultCatalog(Resources.getResource("SpyCarBasic.xml").toExternalForm());

        final InputStream inputCatalog = UriAccessor.accessUri(new URI(Resources.getResource("SpyCarAdvanced.xml").toExternalForm()));
        final String catalogXML = CharStreams.toString(new InputStreamReader(inputCatalog, "UTF-8"));

        final InternalTenantContext tenantContext = Mockito.mock(InternalTenantContext.class);
        Mockito.when(tenantContext.getTenantRecordId()).thenReturn(156L);

        Mockito.when(tenantInternalApi.getTenantCatalogs(Mockito.any(InternalTenantContext.class))).thenReturn(ImmutableList.<String>of(catalogXML));
        VersionedCatalog result = catalogCache.getCatalog(tenantContext);
        Assert.assertNotNull(result);
        final DefaultProduct[] products = result.getProducts(clock.getUTCNow());
        Assert.assertEquals(products.length, 6);

        Mockito.when(tenantInternalApi.getTenantCatalogs(tenantContext)).thenThrow(RuntimeException.class);

        VersionedCatalog result2 = catalogCache.getCatalog(tenantContext);
        Assert.assertNotNull(result2);
        final DefaultProduct[] products2 = result.getProducts(clock.getUTCNow());
        Assert.assertEquals(products2.length, 6);
    }
}
