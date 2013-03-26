/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.Seconds;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.payment.PaymentTestSuiteNoDB;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin.PaymentPluginStatus;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;

public class TestExternalPaymentProviderPlugin extends PaymentTestSuiteNoDB {

    private final Clock clock = new ClockMock();
    private ExternalPaymentProviderPlugin plugin;

    @Override
    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        plugin = new ExternalPaymentProviderPlugin(clock);
    }

    @Test(groups = "fast")
    public void testGetName() throws Exception {
        Assert.assertEquals(plugin.getName(), ExternalPaymentProviderPlugin.PLUGIN_NAME);
    }

    @Test(groups = "fast")
    public void testProcessPayment() throws Exception {

        final UUID accountId = UUID.randomUUID();
        final UUID paymentId = UUID.randomUUID();
        final UUID paymentMethodId = UUID.randomUUID();
        final BigDecimal amount = BigDecimal.TEN;
        final PaymentInfoPlugin paymentInfoPlugin = plugin.processPayment(accountId, paymentId, paymentMethodId, amount, Currency.BRL, callContext);

        Assert.assertEquals(paymentInfoPlugin.getAmount(), amount);
        Assert.assertEquals(Seconds.secondsBetween(paymentInfoPlugin.getCreatedDate(), clock.getUTCNow()).getSeconds(), 0);
        Assert.assertEquals(Seconds.secondsBetween(paymentInfoPlugin.getEffectiveDate(), clock.getUTCNow()).getSeconds(), 0);
        Assert.assertNull(paymentInfoPlugin.getGatewayError());
        Assert.assertNull(paymentInfoPlugin.getGatewayErrorCode());
        Assert.assertEquals(paymentInfoPlugin.getStatus(), PaymentPluginStatus.PROCESSED);

        final PaymentInfoPlugin retrievedPaymentInfoPlugin = plugin.getPaymentInfo(accountId, paymentId, callContext);
        Assert.assertEquals(retrievedPaymentInfoPlugin, paymentInfoPlugin);
    }

    @Test(groups = "fast", expectedExceptions = PaymentPluginApiException.class)
    public void testRefundForNonExistingPayment() throws Exception {
        plugin.processRefund(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, Currency.GBP, callContext);
    }

    @Test(groups = "fast", expectedExceptions = PaymentPluginApiException.class)
    public void testRefundTooLarge() throws Exception {

        final UUID accountId = UUID.randomUUID();
        final UUID paymentId = UUID.randomUUID();
        final UUID paymentMethodId = UUID.randomUUID();

        plugin.processPayment(accountId, paymentId, paymentMethodId, BigDecimal.ZERO, Currency.EUR, callContext);
        plugin.processRefund(accountId, paymentId, BigDecimal.ONE, Currency.EUR, callContext);
    }

    @Test(groups = "fast")
    public void testRefundTooLargeMultipleTimes() throws Exception {

        final UUID accountId = UUID.randomUUID();
        final UUID paymentId = UUID.randomUUID();
        final UUID paymentMethodId = UUID.randomUUID();

        plugin.processPayment(accountId, paymentId, paymentMethodId, BigDecimal.TEN, Currency.EUR, callContext);

        final Account account = Mockito.mock(Account.class);
        for (int i = 0; i < 10; i++) {
            plugin.processRefund(accountId, paymentId, BigDecimal.ONE, Currency.EUR, callContext);
        }

        try {
            plugin.processRefund(accountId, paymentId, BigDecimal.ONE, Currency.EUR, callContext);
            Assert.fail("Shouldn't have been able to refund");
        } catch (PaymentPluginApiException e) {
            Assert.assertTrue(true);
        }
    }
}
