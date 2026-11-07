/*
 * Copyright 2014-2026 The Billing Project, LLC
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

import java.util.Collections;
import java.util.Set;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.DefaultVersionedCatalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.catalog.io.VersionedCatalogLoader;
import org.killbill.billing.catalog.override.PriceOverrideSvc;
import org.killbill.billing.catalog.plugin.VersionedCatalogMapper;
import org.killbill.billing.catalog.plugin.api.CatalogPluginApi;
import org.killbill.billing.catalog.plugin.api.VersionedPluginCatalog;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestDefaultCatalogCachePlanNames {

    private CatalogPluginApi mockPlugin;
    private DefaultCatalogCache catalogCache;
    private InternalTenantContext mockContext;
    private VersionedPluginCatalog mockPluginCatalog;

    @SuppressWarnings("unchecked")
    @BeforeMethod(groups = "fast")
    public void setup() throws CatalogApiException {
        mockPlugin = Mockito.mock(CatalogPluginApi.class);
        mockPluginCatalog = Mockito.mock(VersionedPluginCatalog.class);

        final OSGIServiceRegistration<CatalogPluginApi> pluginRegistry = Mockito.mock(OSGIServiceRegistration.class);
        Mockito.when(pluginRegistry.getAllServices()).thenReturn(Collections.singleton("test-plugin"));
        Mockito.when(pluginRegistry.getServiceForName("test-plugin")).thenReturn(mockPlugin);

        // Plugin has no latest version (bypasses tenant catalog cache)
        Mockito.when(mockPlugin.getLatestCatalogVersion(Mockito.any(), Mockito.any())).thenReturn(null);

        final VersionedCatalogMapper mapper = Mockito.mock(VersionedCatalogMapper.class);
        Mockito.when(mapper.toVersionedCatalog(Mockito.any())).thenReturn(new DefaultVersionedCatalog());

        @SuppressWarnings("rawtypes")
        final CacheController rawCacheController = Mockito.mock(CacheController.class);
        final CacheControllerDispatcher dispatcher = Mockito.mock(CacheControllerDispatcher.class);
        Mockito.when(dispatcher.getCacheController(CacheType.TENANT_CATALOG)).thenReturn(rawCacheController);

        final VersionedCatalogLoader loader = Mockito.mock(VersionedCatalogLoader.class);
        // Return an empty catalog so defaultCatalog is not null if the non-plugin path is reached
        Mockito.when(loader.loadDefaultCatalog(Mockito.anyString())).thenReturn(new DefaultVersionedCatalog());

        final PriceOverrideSvc priceOverride = Mockito.mock(PriceOverrideSvc.class);
        final InternalCallContextFactory contextFactory = Mockito.mock(InternalCallContextFactory.class);
        Mockito.when(contextFactory.createTenantContext(Mockito.any())).thenReturn(Mockito.mock(org.killbill.billing.util.callcontext.TenantContext.class));

        catalogCache = new DefaultCatalogCache(pluginRegistry, mapper, dispatcher, loader, priceOverride, contextFactory);

        mockContext = Mockito.mock(InternalTenantContext.class);
        Mockito.when(mockContext.getAccountRecordId()).thenReturn(1L);
        Mockito.when(mockContext.getTenantRecordId()).thenReturn(99L);
    }

    @Test(groups = "fast")
    public void testPlanNamesForwardedToPlugin() throws CatalogApiException {
        final Set<String> planNames = Set.of("shotgun-monthly", "assault-monthly");

        // Mock the 3-arg overload explicitly (Mockito does not auto-execute default interface methods)
        Mockito.when(mockPlugin.getVersionedPluginCatalog(Mockito.eq(planNames), Mockito.any(), Mockito.any()))
               .thenReturn(mockPluginCatalog);

        final VersionedCatalog result = catalogCache.getCatalog(true, false, true, planNames, mockContext);
        Assert.assertNotNull(result);

        // Verify the plugin was called with exactly the planNames we passed
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Set<String>> planNamesCaptor = ArgumentCaptor.forClass(Set.class);
        Mockito.verify(mockPlugin).getVersionedPluginCatalog(planNamesCaptor.capture(), Mockito.any(), Mockito.any());
        Assert.assertEquals(planNamesCaptor.getValue(), planNames,
                            "Plugin must receive the planNames set passed to getCatalog()");
    }

    @Test(groups = "fast")
    public void testEmptyPlanNamesCallsPluginWith3ArgOverload() throws CatalogApiException {
        // Mock the 3-arg overload for empty set — this is what getCatalog(emptySet) will call
        Mockito.when(mockPlugin.getVersionedPluginCatalog(Mockito.eq(Collections.emptySet()), Mockito.any(), Mockito.any()))
               .thenReturn(mockPluginCatalog);

        final VersionedCatalog result = catalogCache.getCatalog(true, false, true, Collections.emptySet(), mockContext);
        Assert.assertNotNull(result);

        Mockito.verify(mockPlugin).getVersionedPluginCatalog(
                Mockito.eq(Collections.emptySet()), Mockito.any(), Mockito.any());
    }
}
