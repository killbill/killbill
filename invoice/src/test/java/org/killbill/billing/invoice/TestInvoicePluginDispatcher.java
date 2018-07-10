/*
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

package org.killbill.billing.invoice;

import java.util.Collection;
import java.util.Iterator;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.invoice.provider.DefaultNoOpInvoiceProviderPlugin;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.tenant.api.TenantInternalApi;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import static org.testng.Assert.assertEquals;

public class TestInvoicePluginDispatcher extends InvoiceTestSuiteNoDB {

    // Reversed lexicographic order on purpose to test ordering
    private final String PLUGIN_1 = "C_plugin1";
    private final String PLUGIN_2 = "B_plugin2";
    private final String PLUGIN_3 = "A_plugin3";

    @Inject
    protected InvoicePluginDispatcher invoicePluginDispatcher;
    @Inject
    OSGIServiceRegistration<InvoicePluginApi> pluginRegistry;

    @Inject
    TenantInternalApi tenantInternalApi;

    @Override
    protected KillbillConfigSource getConfigSource() {
        return getConfigSource("/resource.properties", ImmutableMap.<String, String>builder()
                .put("org.killbill.invoice.plugin", Joiner.on(",").join(PLUGIN_1, PLUGIN_2))
                .build());
    }

    @Override
    @BeforeMethod(groups = "fast")
    public void beforeMethod() {
        super.beforeMethod();
        for (final String name : pluginRegistry.getAllServices()) {
            pluginRegistry.unregisterService(name);
        }
    }

    @Test(groups = "fast")
    public void testWithNoConfig() throws Exception {
        // We Use the per-tenant config and specify a empty list of plugins
        Mockito.when(tenantInternalApi.getTenantConfig(Mockito.any(InternalCallContext.class))).thenReturn("{\"org.killbill.invoice.plugin\":\"\"}");
        // We register one plugin
        registerPlugin(PLUGIN_1);

        final Collection<String> result = invoicePluginDispatcher.getResultingPluginNameList(internalCallContext);
        // Se expect to seee the list of registered plugins
        assertEquals(result.size(), 1);
        final Iterator<String> iterator = result.iterator();
        assertEquals(iterator.next(), PLUGIN_1);
    }

    @Test(groups = "fast")
    public void testWithNoRegistration() throws Exception {
        // Nothing has been registered, we see nothing
        final Collection<String> result = invoicePluginDispatcher.getResultingPluginNameList(internalCallContext);
        assertEquals(result.size(), 0);
    }

    @Test(groups = "fast")
    public void testWithCorrectOrder() throws Exception {
        // 3 plugins registered in correct order but only 2 got specified in config
        registerPlugin(PLUGIN_1);
        registerPlugin(PLUGIN_2);
        registerPlugin(PLUGIN_3);

        final Collection<String> result = invoicePluginDispatcher.getResultingPluginNameList(internalCallContext);
        assertEquals(result.size(), 2);
        final Iterator<String> iterator = result.iterator();
        assertEquals(iterator.next(), PLUGIN_1);
        assertEquals(iterator.next(), PLUGIN_2);

        assertEquals(invoicePluginDispatcher.getInvoicePlugins(internalCallContext).keySet(), result);
    }

    @Test(groups = "fast")
    public void testWithIncorrectCorrectOrder() throws Exception {
        // 3 plugins registered in *incorrect* order and  only 2 got specified in config
        registerPlugin(PLUGIN_2);
        registerPlugin(PLUGIN_3);
        registerPlugin(PLUGIN_1);

        final Collection<String> result = invoicePluginDispatcher.getResultingPluginNameList(internalCallContext);
        assertEquals(result.size(), 2);
        final Iterator<String> iterator = result.iterator();
        assertEquals(iterator.next(), PLUGIN_1);
        assertEquals(iterator.next(), PLUGIN_2);

        assertEquals(invoicePluginDispatcher.getInvoicePlugins(internalCallContext).keySet(), result);
    }

    private void registerPlugin(final String plugin) {
        pluginRegistry.registerService(new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return plugin;
            }

            @Override
            public String getPluginName() {
                return plugin;
            }

            @Override
            public String getRegistrationName() {
                return plugin;
            }
        }, new DefaultNoOpInvoiceProviderPlugin());
    }
}