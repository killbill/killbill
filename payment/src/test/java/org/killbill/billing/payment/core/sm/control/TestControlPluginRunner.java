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

package org.killbill.billing.payment.core.sm.control;

import java.math.BigDecimal;
import java.util.UUID;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.control.plugin.api.PaymentApiType;
import org.killbill.billing.control.plugin.api.PriorPaymentControlResult;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.provider.DefaultPaymentControlProviderPluginRegistry;
import org.killbill.billing.util.UUIDs;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestControlPluginRunner extends PaymentTestSuiteNoDB {

    @Test(groups = "fast")
    public void testPriorCallWithUnknownPlugin() throws Exception {
        final Account account = Mockito.mock(Account.class);
        final UUID paymentMethodId = UUIDs.randomUUID();
        final UUID paymentId = UUIDs.randomUUID();
        final String paymentExternalKey = UUIDs.randomUUID().toString();
        final UUID paymentTransactionId = UUIDs.randomUUID();
        final String paymentTransactionExternalKey = UUIDs.randomUUID().toString();
        final BigDecimal amount = BigDecimal.ONE;
        final Currency currency = Currency.USD;
        final ImmutableList<String> paymentControlPluginNames = ImmutableList.<String>of("not-registered");
        final ImmutableList<PluginProperty> pluginProperties = ImmutableList.<PluginProperty>of();

        final ControlPluginRunner controlPluginRunner = new ControlPluginRunner(new DefaultPaymentControlProviderPluginRegistry());
        final PriorPaymentControlResult paymentControlResult = controlPluginRunner.executePluginPriorCalls(account,
                                                                                                           paymentMethodId,
                                                                                                           null,
                                                                                                           null,
                                                                                                           paymentId,
                                                                                                           paymentExternalKey,
                                                                                                           paymentTransactionId,
                                                                                                           paymentTransactionExternalKey,
                                                                                                           PaymentApiType.PAYMENT_TRANSACTION,
                                                                                                           TransactionType.AUTHORIZE,
                                                                                                           null,
                                                                                                           amount,
                                                                                                           currency,
                                                                                                           null,
                                                                                                           null,
                                                                                                           true,
                                                                                                           paymentControlPluginNames,
                                                                                                           pluginProperties,
                                                                                                           callContext);
        Assert.assertEquals(paymentControlResult.getAdjustedAmount(), amount);
        Assert.assertEquals(paymentControlResult.getAdjustedCurrency(), currency);
        Assert.assertEquals(paymentControlResult.getAdjustedPaymentMethodId(), paymentMethodId);
        Assert.assertEquals(paymentControlResult.getAdjustedPluginProperties(), pluginProperties);
        Assert.assertFalse(paymentControlResult.isAborted());
    }
}
