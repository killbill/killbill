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

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.payment.PaymentTestSuiteNoDB;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentPluginStatus;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.clock.Clock;
import com.ning.billing.clock.ClockMock;

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
    public void testProcessPayment() throws Exception {

        final UUID accountId = UUID.randomUUID();
        final UUID paymentId = UUID.randomUUID();
        final UUID paymentMethodId = UUID.randomUUID();
        final BigDecimal amount = BigDecimal.TEN;
        final PaymentInfoPlugin paymentInfoPlugin = plugin.processPayment(accountId, paymentId, paymentMethodId, amount, Currency.BRL, callContext);

        Assert.assertEquals(paymentInfoPlugin.getAmount(), amount);
        Assert.assertNull(paymentInfoPlugin.getGatewayError());
        Assert.assertNull(paymentInfoPlugin.getGatewayErrorCode());
        Assert.assertEquals(paymentInfoPlugin.getStatus(), PaymentPluginStatus.PROCESSED);

        final PaymentInfoPlugin retrievedPaymentInfoPlugin = plugin.getPaymentInfo(accountId, paymentId, callContext);
        Assert.assertEquals(retrievedPaymentInfoPlugin.getStatus(), PaymentPluginStatus.PROCESSED);
    }
}
