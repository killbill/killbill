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

package org.killbill.billing.usage.api.svcs;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.usage.api.RawUsageRecord;
import org.killbill.billing.usage.dao.RolledUpUsageDao;
import org.killbill.billing.usage.plugin.api.UsageContext;
import org.killbill.billing.usage.plugin.api.UsagePluginApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestDefaultInternalUserApiTransitions {

    private UsagePluginApi mockPlugin;
    private DefaultInternalUserApi api;
    private InternalTenantContext mockInternalContext;

    @SuppressWarnings("unchecked")
    @BeforeMethod(groups = "fast")
    public void setup() {
        mockPlugin = Mockito.mock(UsagePluginApi.class);

        final OSGIServiceRegistration<UsagePluginApi> pluginRegistry = Mockito.mock(OSGIServiceRegistration.class);
        Mockito.when(pluginRegistry.getAllServices()).thenReturn(Collections.singleton("test-plugin"));
        Mockito.when(pluginRegistry.getServiceForName("test-plugin")).thenReturn(mockPlugin);

        final TenantContext mockTenantContext = Mockito.mock(TenantContext.class);
        Mockito.when(mockTenantContext.getAccountId()).thenReturn(UUID.randomUUID());

        final InternalCallContextFactory contextFactory = Mockito.mock(InternalCallContextFactory.class);
        Mockito.when(contextFactory.createTenantContext(Mockito.any())).thenReturn(mockTenantContext);

        final RolledUpUsageDao dao = Mockito.mock(RolledUpUsageDao.class);

        mockInternalContext = Mockito.mock(InternalTenantContext.class);

        api = new DefaultInternalUserApi(dao, contextFactory, pluginRegistry);
    }

    @Test(groups = "fast")
    public void testUsageTransitionsPassedToPlugin() {
        final UUID subId = UUID.randomUUID();
        final Map.Entry<UUID, String> key = Map.entry(subId, "UNIT_A");
        final Set<DateTime> times = Set.of(new DateTime("2024-01-01T00:00:00Z"));
        final Map<Map.Entry<UUID, String>, Set<DateTime>> transitions = Map.of(key, times);

        final PluginProperty transitionsProp = new PluginProperty("USAGE_TRANSITIONS", transitions, false);
        final List<PluginProperty> props = List.of(transitionsProp);

        final DateTime start = new DateTime("2024-01-01T00:00:00Z");
        final DateTime end = new DateTime("2024-02-01T00:00:00Z");

        // Plugin returns an empty list so we don't need to stub the DAO
        Mockito.when(mockPlugin.getUsageForAccount(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
               .thenReturn(Collections.emptyList());

        api.getRawUsageForAccount(start, end, null, props, mockInternalContext);

        final ArgumentCaptor<UsageContext> contextCaptor = ArgumentCaptor.forClass(UsageContext.class);
        Mockito.verify(mockPlugin).getUsageForAccount(Mockito.eq(start), Mockito.eq(end), contextCaptor.capture(), Mockito.any());

        Assert.assertEquals(contextCaptor.getValue().getUsageTransitions(), transitions,
                            "Plugin must receive the USAGE_TRANSITIONS map via UsageContext");
    }

    @Test(groups = "fast")
    public void testUsageTransitionsNullWhenPropertyAbsent() {
        final List<PluginProperty> props = List.of(new PluginProperty("OTHER_KEY", "irrelevant", false));

        final DateTime start = new DateTime("2024-01-01T00:00:00Z");
        final DateTime end = new DateTime("2024-02-01T00:00:00Z");

        Mockito.when(mockPlugin.getUsageForAccount(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
               .thenReturn(Collections.emptyList());

        api.getRawUsageForAccount(start, end, null, props, mockInternalContext);

        final ArgumentCaptor<UsageContext> contextCaptor = ArgumentCaptor.forClass(UsageContext.class);
        Mockito.verify(mockPlugin).getUsageForAccount(Mockito.eq(start), Mockito.eq(end), contextCaptor.capture(), Mockito.any());

        Assert.assertNull(contextCaptor.getValue().getUsageTransitions(),
                          "getUsageTransitions() must be null when USAGE_TRANSITIONS is not in plugin properties");
    }

    @Test(groups = "fast")
    public void testUsageTransitionsNullWhenPropertyValueIsNotAMap() {
        // Malformed property: value is a String, not a Map — must be ignored
        final PluginProperty badProp = new PluginProperty("USAGE_TRANSITIONS", "not-a-map", false);
        final List<PluginProperty> props = List.of(badProp);

        final DateTime start = new DateTime("2024-01-01T00:00:00Z");
        final DateTime end = new DateTime("2024-02-01T00:00:00Z");

        Mockito.when(mockPlugin.getUsageForAccount(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
               .thenReturn(Collections.emptyList());

        api.getRawUsageForAccount(start, end, null, props, mockInternalContext);

        final ArgumentCaptor<UsageContext> contextCaptor = ArgumentCaptor.forClass(UsageContext.class);
        Mockito.verify(mockPlugin).getUsageForAccount(Mockito.eq(start), Mockito.eq(end), contextCaptor.capture(), Mockito.any());

        Assert.assertNull(contextCaptor.getValue().getUsageTransitions(),
                          "Non-Map USAGE_TRANSITIONS value must be treated as absent");
    }
}
