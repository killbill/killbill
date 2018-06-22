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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.CatalogTestSuiteNoDB;
import org.killbill.billing.catalog.DefaultVersionedCatalog;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.StandaloneCatalogWithPriceOverride;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.xmlloader.UriAccessor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.common.io.Resources;

public class TestEhCacheCatalogCache extends CatalogTestSuiteNoDB {

    private InternalTenantContext multiTenantContext;
    private InternalTenantContext otherMultiTenantContext;

    @BeforeMethod(groups = "fast")
    protected void beforeMethod() throws Exception {
        cacheControllerDispatcher.clearAll();

        multiTenantContext = Mockito.mock(InternalTenantContext.class);
        Mockito.when(multiTenantContext.getAccountRecordId()).thenReturn(456L);
        Mockito.when(multiTenantContext.getTenantRecordId()).thenReturn(99L);

        otherMultiTenantContext = Mockito.mock(InternalCallContext.class);
        Mockito.when(otherMultiTenantContext.getAccountRecordId()).thenReturn(123L);
        Mockito.when(otherMultiTenantContext.getTenantRecordId()).thenReturn(112233L);

        ((EhCacheCatalogCache) catalogCache).setDefaultCatalog();
    }

    //
    // Verify CatalogCache returns default Catalog when used in mono-tenant and catalog system property has not been set
    //
    @Test(groups = "fast")
    public void testMissingDefaultCatalog() throws CatalogApiException {
        catalogCache.loadDefaultCatalog(null);
        Assert.assertEquals(catalogCache.getCatalog(true, true, false, internalCallContext).getCatalogName(), "EmptyCatalog");
    }

    //
    // Verify CatalogCache returns default catalog when system property has been set (and CatalogCache has been initialized)
    //
    @Test(groups = "fast")
    public void testDefaultCatalog() throws CatalogApiException {
        catalogCache.loadDefaultCatalog(Resources.getResource("SpyCarBasic.xml").toExternalForm());

        final DefaultVersionedCatalog result = catalogCache.getCatalog(true, true, false, internalCallContext);
        Assert.assertNotNull(result);
        final Collection<Product> products = result.getProducts(clock.getUTCNow());
        Assert.assertEquals(products.size(), 3);

        // Verify the lookup with other contexts
        final DefaultVersionedCatalog resultForMultiTenantContext = new DefaultVersionedCatalog(result.getClock());
        for (final StandaloneCatalog cur : result.getVersions()) {
            resultForMultiTenantContext.add(new StandaloneCatalogWithPriceOverride(cur, priceOverride, multiTenantContext.getTenantRecordId(), internalCallContextFactory));
        }

        Assert.assertEquals(catalogCache.getCatalog(true, true, false, multiTenantContext).getCatalogName(), resultForMultiTenantContext.getCatalogName());
        Assert.assertEquals(catalogCache.getCatalog(true, true, false, multiTenantContext).getVersions().size(), resultForMultiTenantContext.getVersions().size());
        for (int i = 0; i < catalogCache.getCatalog(true, true, false, multiTenantContext).getVersions().size(); i++) {
           Assert.assertEquals(((StandaloneCatalogWithPriceOverride) catalogCache.getCatalog(true, true, false, multiTenantContext).getVersions().get(i)).getTenantRecordId(), ((StandaloneCatalogWithPriceOverride) resultForMultiTenantContext.getVersions().get(i)).getTenantRecordId());
        }
    }

    //
    // Verify CatalogCache returns per tenant catalog:
    // 1. We first mock TenantInternalApi to return a different catalog than the default one
    // 2. We then mock TenantInternalApi to throw RuntimeException which means catalog was cached and there was no additional call
    //    to the TenantInternalApi api (otherwise test would fail with RuntimeException)
    //
    @Test(groups = "fast")
    public void testExistingTenantCatalog() throws CatalogApiException, URISyntaxException, IOException {
        final InternalCallContext differentMultiTenantContext = Mockito.mock(InternalCallContext.class);
        Mockito.when(differentMultiTenantContext.getTenantRecordId()).thenReturn(55667788L);

        final AtomicBoolean shouldThrow = new AtomicBoolean(false);
        final Long multiTenantRecordId = multiTenantContext.getTenantRecordId();
        final Long otherMultiTenantRecordId = otherMultiTenantContext.getTenantRecordId();

        final InputStream tenantInputCatalog = UriAccessor.accessUri(new URI(Resources.getResource("SpyCarAdvanced.xml").toExternalForm()));
        final String tenantCatalogXML = CharStreams.toString(new InputStreamReader(tenantInputCatalog, "UTF-8"));
        final InputStream otherTenantInputCatalog = UriAccessor.accessUri(new URI(Resources.getResource("SpyCarBasic.xml").toExternalForm()));
        final String otherTenantCatalogXML = CharStreams.toString(new InputStreamReader(otherTenantInputCatalog, "UTF-8"));
        Mockito.when(tenantInternalApi.getTenantCatalogs(Mockito.any(InternalTenantContext.class))).thenAnswer(new Answer<List<String>>() {
            @Override
            public List<String> answer(final InvocationOnMock invocation) throws Throwable {
                if (shouldThrow.get()) {
                    throw new RuntimeException();
                }
                final InternalTenantContext internalContext = (InternalTenantContext) invocation.getArguments()[0];
                if (multiTenantRecordId.equals(internalContext.getTenantRecordId())) {
                    return ImmutableList.<String>of(tenantCatalogXML);
                } else if (otherMultiTenantRecordId.equals(internalContext.getTenantRecordId())) {
                    return ImmutableList.<String>of(otherTenantCatalogXML);
                } else {
                    return ImmutableList.<String>of();
                }
            }
        });

        // Verify the lookup for a non-cached tenant. No system config is set yet but EhCacheCatalogCache returns a default empty one
        VersionedCatalog differentResult = catalogCache.getCatalog(true, true, false, differentMultiTenantContext);
        Assert.assertNotNull(differentResult);
        Assert.assertEquals(differentResult.getCatalogName(), "EmptyCatalog");

        // Make sure the cache loader isn't invoked, see https://github.com/killbill/killbill/issues/300
        shouldThrow.set(true);

        differentResult = catalogCache.getCatalog(true, true, false, differentMultiTenantContext);
        Assert.assertNotNull(differentResult);
        Assert.assertEquals(differentResult.getCatalogName(), "EmptyCatalog");

        shouldThrow.set(false);

        // Set a default config
        catalogCache.loadDefaultCatalog(Resources.getResource("SpyCarBasic.xml").toExternalForm());

        // Verify the lookup for this tenant
        final DefaultVersionedCatalog result = catalogCache.getCatalog(true, true, false, multiTenantContext);
        Assert.assertNotNull(result);
        final Collection<Product> products = result.getProducts(clock.getUTCNow());
        Assert.assertEquals(products.size(), 6);

        // Verify the lookup for another tenant
        final DefaultVersionedCatalog otherResult = catalogCache.getCatalog(true, true, false, otherMultiTenantContext);
        Assert.assertNotNull(otherResult);
        final Collection<Product> otherProducts = otherResult.getProducts(clock.getUTCNow());
        Assert.assertEquals(otherProducts.size(), 3);

        shouldThrow.set(true);

        // Verify the lookup for this tenant
        final VersionedCatalog result2 = catalogCache.getCatalog(true, true, false, multiTenantContext);
        Assert.assertEquals(result2, result);

        // Verify the lookup with another context for the same tenant
        final InternalCallContext sameMultiTenantContext = Mockito.mock(InternalCallContext.class);
        Mockito.when(sameMultiTenantContext.getAccountRecordId()).thenReturn(9102L);
        Mockito.when(sameMultiTenantContext.getTenantRecordId()).thenReturn(multiTenantRecordId);
        Assert.assertEquals(catalogCache.getCatalog(true, true, false, sameMultiTenantContext), result);

        // Verify the lookup with the other tenant
        Assert.assertEquals(catalogCache.getCatalog(true, true, false, otherMultiTenantContext), otherResult);
    }
}
