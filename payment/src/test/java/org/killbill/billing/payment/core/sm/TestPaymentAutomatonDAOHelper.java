/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.core.sm;

import java.math.BigDecimal;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestPaymentAutomatonDAOHelper extends PaymentTestSuiteWithEmbeddedDB {

    private final String paymentExternalKey = UUID.randomUUID().toString();
    private final String paymentTransactionExternalKey = UUID.randomUUID().toString();
    private final BigDecimal amount = new BigDecimal("9320.19200001");
    private final Currency currency = Currency.CAD;

    private PaymentStateContext paymentStateContext;

    @Test(groups = "slow")
    public void testFailToRetrievePayment() throws Exception {
        // Verify a dummy payment doesn't exist
        final PaymentAutomatonDAOHelper daoHelper = createDAOHelper(UUID.randomUUID(), paymentExternalKey, paymentTransactionExternalKey, amount, currency);
        try {
            daoHelper.getPayment();
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_NO_SUCH_PAYMENT.getCode());
        }
    }

    @Test(groups = "slow")
    public void testCreateNewPaymentTransaction() throws Exception {
        // Create a payment and transaction based on the context
        final PaymentAutomatonDAOHelper daoHelper = createDAOHelper(null, paymentExternalKey, paymentTransactionExternalKey, amount, currency);
        daoHelper.createNewPaymentTransaction();

        final PaymentModelDao payment1 = daoHelper.getPayment();
        Assert.assertEquals(payment1.getExternalKey(), paymentExternalKey);
        Assert.assertNull(payment1.getStateName());
        Assert.assertEquals(paymentStateContext.getPaymentTransactionModelDao().getTransactionExternalKey(), paymentTransactionExternalKey);
        Assert.assertEquals(paymentStateContext.getPaymentTransactionModelDao().getAmount().compareTo(amount), 0);
        Assert.assertEquals(paymentStateContext.getPaymentTransactionModelDao().getCurrency(), currency);

        // Verify we can update them
        final PaymentTransactionInfoPlugin paymentInfoPlugin = Mockito.mock(PaymentTransactionInfoPlugin.class);
        Mockito.when(paymentInfoPlugin.getAmount()).thenReturn(new BigDecimal("82010.222"));
        Mockito.when(paymentInfoPlugin.getCurrency()).thenReturn(Currency.CAD);
        Mockito.when(paymentInfoPlugin.getStatus()).thenReturn(PaymentPluginStatus.PROCESSED);
        Mockito.when(paymentInfoPlugin.getGatewayErrorCode()).thenReturn(UUID.randomUUID().toString().substring(0, 5));
        Mockito.when(paymentInfoPlugin.getGatewayError()).thenReturn(UUID.randomUUID().toString());
        daoHelper.processPaymentInfoPlugin(TransactionStatus.SUCCESS, paymentInfoPlugin, "SOME_STATE");

        final PaymentModelDao payment2 = daoHelper.getPayment();
        Assert.assertEquals(payment2.getExternalKey(), paymentExternalKey);
        Assert.assertEquals(payment2.getStateName(), "SOME_STATE");
        Assert.assertEquals(paymentStateContext.getPaymentTransactionModelDao().getPaymentId(), payment2.getId());
        Assert.assertEquals(paymentStateContext.getPaymentTransactionModelDao().getTransactionExternalKey(), paymentTransactionExternalKey);
        Assert.assertEquals(paymentStateContext.getPaymentTransactionModelDao().getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(paymentStateContext.getPaymentTransactionModelDao().getAmount().compareTo(amount), 0);
        Assert.assertEquals(paymentStateContext.getPaymentTransactionModelDao().getCurrency(), currency);
        Assert.assertEquals(paymentStateContext.getPaymentTransactionModelDao().getProcessedAmount().compareTo(paymentInfoPlugin.getAmount()), 0);
        Assert.assertEquals(paymentStateContext.getPaymentTransactionModelDao().getProcessedCurrency(), paymentInfoPlugin.getCurrency());
        Assert.assertEquals(paymentStateContext.getPaymentTransactionModelDao().getGatewayErrorCode(), paymentInfoPlugin.getGatewayErrorCode());
        Assert.assertEquals(paymentStateContext.getPaymentTransactionModelDao().getGatewayErrorMsg(), paymentInfoPlugin.getGatewayError());
    }

    @Test(groups = "slow")
    public void testNoPaymentMethod() throws Exception {
        final PaymentAutomatonDAOHelper daoHelper = createDAOHelper(UUID.randomUUID(), paymentExternalKey, paymentTransactionExternalKey, amount, currency);
        try {
            daoHelper.getPaymentPluginApi();
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD.getCode());
        }
    }

    private PaymentAutomatonDAOHelper createDAOHelper(@Nullable final UUID paymentId, final String paymentExternalKey,
                                                      final String paymentTransactionExternalKey,
                                                      final BigDecimal amount, final Currency currency) throws Exception {
        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(UUID.randomUUID());
        // No default payment method

        paymentStateContext = new PaymentStateContext(true,
                                                      paymentId,
                                                      null,
                                                      null,
                                                      paymentExternalKey,
                                                      paymentTransactionExternalKey,
                                                      TransactionType.CAPTURE,
                                                      account,
                                                      UUID.randomUUID(),
                                                      amount,
                                                      currency,
                                                      null,
                                                      null,
                                                      null,
                                                      false,
                                                      null,
                                                      ImmutableList.<PluginProperty>of(),
                                                      internalCallContext,
                                                      callContext);

        return new PaymentAutomatonDAOHelper(paymentStateContext, clock.getUTCNow(), paymentDao, paymentPluginServiceRegistration, internalCallContext, eventBus, paymentSMHelper);
    }
}
