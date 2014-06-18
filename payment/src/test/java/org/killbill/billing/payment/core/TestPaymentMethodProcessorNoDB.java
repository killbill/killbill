/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.payment.core;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.provider.ExternalPaymentProviderPlugin;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestPaymentMethodProcessorNoDB extends PaymentTestSuiteNoDB {

    @Test(groups = "fast")
    public void testGetExternalPaymentProviderPlugin() throws Exception {
        final Iterable<PluginProperty> properties = ImmutableList.<PluginProperty>of();
        final UUID accountId = UUID.randomUUID();
        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(accountId);
        Mockito.when(account.getExternalKey()).thenReturn(accountId.toString());

        Assert.assertEquals(paymentMethodProcessor.getPaymentMethods(account, false, properties, internalCallContext).size(), 0);

        // The first call should create the payment method
        final ExternalPaymentProviderPlugin providerPlugin = paymentMethodProcessor.createPaymentMethodAndGetExternalPaymentProviderPlugin(account, properties, callContext, internalCallContext);
        final List<PaymentMethod> paymentMethods = paymentMethodProcessor.getPaymentMethods(account, false, properties, internalCallContext);
        Assert.assertEquals(paymentMethods.size(), 1);
        Assert.assertEquals(paymentMethods.get(0).getPluginName(), ExternalPaymentProviderPlugin.PLUGIN_NAME);
        Assert.assertEquals(paymentMethods.get(0).getAccountId(), account.getId());

        // The succeeding calls should not create any other payment method
        final UUID externalPaymentMethodId = paymentMethods.get(0).getId();
        for (int i = 0; i < 50; i++) {
            final ExternalPaymentProviderPlugin foundProviderPlugin = paymentMethodProcessor.createPaymentMethodAndGetExternalPaymentProviderPlugin(account, properties, callContext, internalCallContext);
            Assert.assertNotNull(foundProviderPlugin);

            final List<PaymentMethod> foundPaymentMethods = paymentMethodProcessor.getPaymentMethods(account, false, properties, internalCallContext);
            Assert.assertEquals(foundPaymentMethods.size(), 1);
            Assert.assertEquals(foundPaymentMethods.get(0).getPluginName(), ExternalPaymentProviderPlugin.PLUGIN_NAME);
            Assert.assertEquals(foundPaymentMethods.get(0).getAccountId(), account.getId());
            Assert.assertEquals(foundPaymentMethods.get(0).getId(), externalPaymentMethodId);
        }
    }
}
