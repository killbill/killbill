/*
 * Copyright 2015 Groupon, Inc
 * Copyright 2015 The Billing Project, LLC
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

package org.killbill.billing.overdue.caching;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.overdue.OverdueTestSuiteNoDB;
import org.killbill.billing.overdue.api.OverdueApiException;
import org.killbill.billing.overdue.api.OverdueConfig;
import org.killbill.xmlloader.UriAccessor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.io.CharStreams;
import com.google.common.io.Resources;

public class TestDefaultOverdueConfigCache extends OverdueTestSuiteNoDB {

    private InternalTenantContext multiTenantContext;
    private InternalTenantContext otherMultiTenantContext;

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        cacheControllerDispatcher.clearAll();

        multiTenantContext = Mockito.mock(InternalTenantContext.class);
        Mockito.when(multiTenantContext.getAccountRecordId()).thenReturn(456L);
        Mockito.when(multiTenantContext.getTenantRecordId()).thenReturn(99L);

        otherMultiTenantContext = Mockito.mock(InternalCallContext.class);
        Mockito.when(otherMultiTenantContext.getAccountRecordId()).thenReturn(123L);
        Mockito.when(otherMultiTenantContext.getTenantRecordId()).thenReturn(112233L);
    }

    //
    // Verify the default OverdueConfig is returned when used in mono-tenant and overdue system property has not been set
    //
    @Test(groups = "fast")
    public void testMissingDefaultOverdueConfig() throws OverdueApiException {
        overdueConfigCache.loadDefaultOverdueConfig((String) null);
        final OverdueConfig result = overdueConfigCache.getOverdueConfig(internalCallContext);
        Assert.assertNotNull(result);
        Assert.assertEquals(result.getOverdueStatesAccount().getStates().length, 1);
        Assert.assertTrue(result.getOverdueStatesAccount().getStates()[0].isClearState());
    }

    //
    // Verify the default OverdueConfig is returned when system property has been set (and OverdueConfigCache has been initialized)
    //
    @Test(groups = "fast")
    public void testDefaultOverdueConfig() throws OverdueApiException {
        overdueConfigCache.loadDefaultOverdueConfig(Resources.getResource("org/killbill/billing/overdue/OverdueConfig.xml").toExternalForm());

        final OverdueConfig result = overdueConfigCache.getOverdueConfig(internalCallContext);
        Assert.assertNotNull(result);
        Assert.assertEquals(result.getOverdueStatesAccount().getStates().length, 1);
        Assert.assertTrue(result.getOverdueStatesAccount().getStates()[0].isClearState());

        // Verify the lookup with other contexts
        Assert.assertEquals(overdueConfigCache.getOverdueConfig(multiTenantContext), result);
        Assert.assertEquals(overdueConfigCache.getOverdueConfig(otherMultiTenantContext), result);
        Assert.assertEquals(overdueConfigCache.getOverdueConfig(Mockito.mock(InternalTenantContext.class)), result);
        Assert.assertEquals(overdueConfigCache.getOverdueConfig(Mockito.mock(InternalCallContext.class)), result);
    }

    //
    // Verify OverdueConfigCache returns per tenant overdue config:
    // 1. We first mock TenantInternalApi to return a different overdue config than the default one
    // 2. We then mock TenantInternalApi to throw RuntimeException which means overdue config was cached and there was no additional call
    //    to the TenantInternalApi api (otherwise test would fail with RuntimeException)
    //
    @Test(groups = "fast")
    public void testExistingTenantOverdue() throws OverdueApiException, URISyntaxException, IOException {
        final InternalCallContext differentMultiTenantContext = Mockito.mock(InternalCallContext.class);
        Mockito.when(differentMultiTenantContext.getTenantRecordId()).thenReturn(55667788L);

        final AtomicBoolean shouldThrow = new AtomicBoolean(false);
        final Long multiTenantRecordId = multiTenantContext.getTenantRecordId();
        final Long otherMultiTenantRecordId = otherMultiTenantContext.getTenantRecordId();

        final InputStream tenantInputOverdueConfig = UriAccessor.accessUri(new URI(Resources.getResource("org/killbill/billing/overdue/OverdueConfig2.xml").toExternalForm()));
        final String tenantOverdueConfigXML = CharStreams.toString(new InputStreamReader(tenantInputOverdueConfig, "UTF-8"));
        final InputStream otherTenantInputOverdueConfig = UriAccessor.accessUri(new URI(Resources.getResource("org/killbill/billing/overdue/OverdueConfig.xml").toExternalForm()));
        final String otherTenantOverdueConfigXML = CharStreams.toString(new InputStreamReader(otherTenantInputOverdueConfig, "UTF-8"));
        Mockito.when(tenantInternalApi.getTenantOverdueConfig(Mockito.any(InternalTenantContext.class))).thenAnswer(new Answer<String>() {
            @Override
            public String answer(final InvocationOnMock invocation) throws Throwable {
                if (shouldThrow.get()) {
                    throw new RuntimeException();
                }
                final InternalTenantContext internalContext = (InternalTenantContext) invocation.getArguments()[0];
                if (multiTenantRecordId.equals(internalContext.getTenantRecordId())) {
                    return tenantOverdueConfigXML;
                } else if (otherMultiTenantRecordId.equals(internalContext.getTenantRecordId())) {
                    return otherTenantOverdueConfigXML;
                } else {
                    return null;
                }
            }
        });

        // Verify the lookup for a non-cached tenant. No system config is set yet but DefaultOverdueConfigCache returns a default no-op one
        OverdueConfig differentResult = overdueConfigCache.getOverdueConfig(differentMultiTenantContext);
        Assert.assertNotNull(differentResult);
        Assert.assertEquals(differentResult.getOverdueStatesAccount().getStates().length, 1);
        Assert.assertTrue(differentResult.getOverdueStatesAccount().getStates()[0].isClearState());

        // Make sure the cache loader isn't invoked, see https://github.com/killbill/killbill/issues/298
        shouldThrow.set(true);

        differentResult = overdueConfigCache.getOverdueConfig(differentMultiTenantContext);
        Assert.assertNotNull(differentResult);
        Assert.assertEquals(differentResult.getOverdueStatesAccount().getStates().length, 1);
        Assert.assertTrue(differentResult.getOverdueStatesAccount().getStates()[0].isClearState());

        shouldThrow.set(false);

        // Set a default config
        overdueConfigCache.loadDefaultOverdueConfig(Resources.getResource("org/killbill/billing/overdue/OverdueConfig.xml").toExternalForm());

        // Verify the lookup for this tenant
        final OverdueConfig result = overdueConfigCache.getOverdueConfig(multiTenantContext);
        Assert.assertNotNull(result);
        Assert.assertEquals(result.getOverdueStatesAccount().getStates().length, 1);
        Assert.assertFalse(result.getOverdueStatesAccount().getStates()[0].isClearState());

        // Verify the lookup for another tenant
        final OverdueConfig otherResult = overdueConfigCache.getOverdueConfig(otherMultiTenantContext);
        Assert.assertNotNull(otherResult);
        Assert.assertEquals(otherResult.getOverdueStatesAccount().getStates().length, 1);
        Assert.assertTrue(otherResult.getOverdueStatesAccount().getStates()[0].isClearState());

        shouldThrow.set(true);

        // Verify the lookup for this tenant
        final OverdueConfig result2 = overdueConfigCache.getOverdueConfig(multiTenantContext);
        Assert.assertEquals(result2, result);

        // Verify the lookup with another context for the same tenant
        final InternalCallContext sameMultiTenantContext = Mockito.mock(InternalCallContext.class);
        Mockito.when(sameMultiTenantContext.getAccountRecordId()).thenReturn(9102L);
        Mockito.when(sameMultiTenantContext.getTenantRecordId()).thenReturn(multiTenantRecordId);
        Assert.assertEquals(overdueConfigCache.getOverdueConfig(sameMultiTenantContext), result);

        // Verify the lookup with the other tenant
        Assert.assertEquals(overdueConfigCache.getOverdueConfig(otherMultiTenantContext), otherResult);
    }
}
