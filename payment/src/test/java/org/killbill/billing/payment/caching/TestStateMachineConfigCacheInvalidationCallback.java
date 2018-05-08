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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.killbill.automaton.StateMachineConfig;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestStateMachineConfigCacheInvalidationCallback extends PaymentTestSuiteNoDB {

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
    public void testInvalidation() throws Exception {
        final String pluginName = UUID.randomUUID().toString();

        final StateMachineConfig defaultPaymentStateMachineConfig = stateMachineConfigCache.getPaymentStateMachineConfig(UUID.randomUUID().toString(), internalCallContext);
        Assert.assertNotNull(defaultPaymentStateMachineConfig);

        final AtomicBoolean shouldThrow = new AtomicBoolean(false);

        Mockito.when(tenantInternalApi.getPluginPaymentStateMachineConfig(Mockito.eq(pluginName), Mockito.any(InternalTenantContext.class))).thenAnswer(new Answer<String>() {
            @Override
            public String answer(final InvocationOnMock invocation) throws Throwable {
                if (shouldThrow.get()) {
                    throw new RuntimeException("For test purposes");
                }
                return null;
            }
        });

        // Prime caches
        Assert.assertEquals(stateMachineConfigCache.getPaymentStateMachineConfig(pluginName, internalCallContext), defaultPaymentStateMachineConfig);
        Assert.assertEquals(stateMachineConfigCache.getPaymentStateMachineConfig(pluginName, otherMultiTenantContext), defaultPaymentStateMachineConfig);

        shouldThrow.set(true);

        // No exception (cached)
        Assert.assertEquals(stateMachineConfigCache.getPaymentStateMachineConfig(pluginName, internalCallContext), defaultPaymentStateMachineConfig);

        cacheInvalidationCallback.invalidateCache(TenantKey.PLUGIN_PAYMENT_STATE_MACHINE_, pluginName, multiTenantContext);

        try {
            stateMachineConfigCache.getPaymentStateMachineConfig(pluginName, multiTenantContext);
            Assert.fail();
        } catch (final RuntimeException exception) {
            Assert.assertTrue(exception.getCause() instanceof RuntimeException);
            Assert.assertEquals(exception.getCause().getMessage(), "For test purposes");
        }

        // No exception (cached)
        Assert.assertEquals(stateMachineConfigCache.getPaymentStateMachineConfig(pluginName, otherMultiTenantContext), defaultPaymentStateMachineConfig);
    }
}
