/*
 * Copyright 2016-2017 Groupon, Inc
 * Copyright 2016-2017 The Billing Project, LLC
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

package org.killbill.billing.payment.caching;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.killbill.automaton.StateMachineConfig;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.xmlloader.UriAccessor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;

public class TestStateMachineConfigCache extends PaymentTestSuiteNoDB {

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

    @Test(groups = "fast")
    public void testMissingPluginStateMachineConfig() throws PaymentApiException {
        Assert.assertNotNull(stateMachineConfigCache.getPaymentStateMachineConfig(UUID.randomUUID().toString(), internalCallContext));
        Assert.assertNotNull(stateMachineConfigCache.getPaymentStateMachineConfig(UUID.randomUUID().toString(), multiTenantContext));
        Assert.assertNotNull(stateMachineConfigCache.getPaymentStateMachineConfig(UUID.randomUUID().toString(), otherMultiTenantContext));
    }

    @Test(groups = "fast")
    public void testExistingTenantStateMachineConfig() throws PaymentApiException, URISyntaxException, IOException {
        final String pluginName = UUID.randomUUID().toString();

        final InternalCallContext differentMultiTenantContext = Mockito.mock(InternalCallContext.class);
        Mockito.when(differentMultiTenantContext.getTenantRecordId()).thenReturn(55667788L);

        final AtomicBoolean shouldThrow = new AtomicBoolean(false);
        final Long multiTenantRecordId = multiTenantContext.getTenantRecordId();
        final Long otherMultiTenantRecordId = otherMultiTenantContext.getTenantRecordId();

        Mockito.when(tenantInternalApi.getPluginPaymentStateMachineConfig(Mockito.eq(pluginName), Mockito.any(InternalTenantContext.class))).thenAnswer(new Answer<String>() {
            @Override
            public String answer(final InvocationOnMock invocation) throws Throwable {
                if (shouldThrow.get()) {
                    throw new RuntimeException("For test purposes");
                }
                final InternalTenantContext internalContext = (InternalTenantContext) invocation.getArguments()[1];
                if (multiTenantRecordId.equals(internalContext.getTenantRecordId())) {
                    return new String(ByteStreams.toByteArray(UriAccessor.accessUri(Resources.getResource(PaymentModule.DEFAULT_STATE_MACHINE_PAYMENT_XML).toExternalForm())));
                } else if (otherMultiTenantRecordId.equals(internalContext.getTenantRecordId())) {
                    return new String(ByteStreams.toByteArray(UriAccessor.accessUri(Resources.getResource(PaymentModule.DEFAULT_STATE_MACHINE_RETRY_XML).toExternalForm())));
                } else {
                    return null;
                }
            }
        });

        // Verify the lookup for a non-cached tenant. No system config is set yet but EhCacheStateMachineConfigCache returns a default empty one
        final StateMachineConfig defaultStateMachineConfig = stateMachineConfigCache.getPaymentStateMachineConfig(pluginName, differentMultiTenantContext);
        Assert.assertNotNull(defaultStateMachineConfig);

        // Make sure the cache loader isn't invoked, see https://github.com/killbill/killbill/issues/300
        shouldThrow.set(true);

        final StateMachineConfig defaultStateMachineConfig2 = stateMachineConfigCache.getPaymentStateMachineConfig(pluginName, differentMultiTenantContext);
        Assert.assertNotNull(defaultStateMachineConfig2);
        Assert.assertEquals(defaultStateMachineConfig2, defaultStateMachineConfig);

        shouldThrow.set(false);

        // Verify the lookup for this tenant
        Assert.assertEquals(stateMachineConfigCache.getPaymentStateMachineConfig(UUID.randomUUID().toString(), multiTenantContext), defaultStateMachineConfig);
        final StateMachineConfig result = stateMachineConfigCache.getPaymentStateMachineConfig(pluginName, multiTenantContext);
        Assert.assertNotNull(result);
        Assert.assertNotEquals(result, defaultStateMachineConfig);
        Assert.assertEquals(result.getStateMachines().length, 8);

        // Verify the lookup for another tenant
        Assert.assertEquals(stateMachineConfigCache.getPaymentStateMachineConfig(UUID.randomUUID().toString(), otherMultiTenantContext), defaultStateMachineConfig);
        final StateMachineConfig otherResult = stateMachineConfigCache.getPaymentStateMachineConfig(pluginName, otherMultiTenantContext);
        Assert.assertNotNull(otherResult);
        Assert.assertEquals(otherResult.getStateMachines().length, 1);

        shouldThrow.set(true);

        // Verify the lookup for this tenant
        Assert.assertEquals(stateMachineConfigCache.getPaymentStateMachineConfig(pluginName, multiTenantContext), result);

        // Verify the lookup with another context for the same tenant
        final InternalCallContext sameMultiTenantContext = Mockito.mock(InternalCallContext.class);
        Mockito.when(sameMultiTenantContext.getAccountRecordId()).thenReturn(9102L);
        Mockito.when(sameMultiTenantContext.getTenantRecordId()).thenReturn(multiTenantRecordId);
        Assert.assertEquals(stateMachineConfigCache.getPaymentStateMachineConfig(pluginName, sameMultiTenantContext), result);

        // Verify the lookup with the other tenant
        Assert.assertEquals(stateMachineConfigCache.getPaymentStateMachineConfig(pluginName, otherMultiTenantContext), otherResult);

        // Verify clearing the cache works
        stateMachineConfigCache.clearPaymentStateMachineConfig(pluginName, multiTenantContext);
        Assert.assertEquals(stateMachineConfigCache.getPaymentStateMachineConfig(pluginName, otherMultiTenantContext), otherResult);
        try {
            stateMachineConfigCache.getPaymentStateMachineConfig(pluginName, multiTenantContext);
            Assert.fail();
        } catch (final RuntimeException exception) {
            Assert.assertTrue(exception.getCause() instanceof RuntimeException);
            Assert.assertEquals(exception.getCause().getMessage(), "For test purposes");
        }
    }
}
