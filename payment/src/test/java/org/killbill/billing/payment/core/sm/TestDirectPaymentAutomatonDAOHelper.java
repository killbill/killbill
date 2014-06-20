/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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
import org.killbill.billing.payment.api.PaymentStatus;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestDirectPaymentAutomatonDAOHelper extends PaymentTestSuiteWithEmbeddedDB {

    private final String directPaymentExternalKey = UUID.randomUUID().toString();
    private final String directPaymentTransactionExternalKey = UUID.randomUUID().toString();
    private final BigDecimal amount = new BigDecimal("9320.19200001");
    private final Currency currency = Currency.CAD;

    private DirectPaymentStateContext directPaymentStateContext;

    @Test(groups = "slow")
    public void testFailToRetrieveDirectPayment() throws Exception {
        // Verify a dummy payment doesn't exist
        final DirectPaymentAutomatonDAOHelper daoHelper = createDAOHelper(UUID.randomUUID(), directPaymentExternalKey, directPaymentTransactionExternalKey, amount, currency);
        try {
            daoHelper.getDirectPayment();
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_NO_SUCH_PAYMENT.getCode());
        }
    }

    @Test(groups = "slow")
    public void testCreateNewDirectPaymentTransaction() throws Exception {
        // Create a payment and transaction based on the context
        final DirectPaymentAutomatonDAOHelper daoHelper = createDAOHelper(null, directPaymentExternalKey, directPaymentTransactionExternalKey, amount, currency);
        daoHelper.createNewDirectPaymentTransaction();

        final PaymentModelDao directPayment1 = daoHelper.getDirectPayment();
        Assert.assertEquals(directPayment1.getExternalKey(), directPaymentExternalKey);
        Assert.assertNull(directPayment1.getStateName());
        Assert.assertEquals(directPaymentStateContext.getDirectPaymentTransactionModelDao().getTransactionExternalKey(), directPaymentTransactionExternalKey);
        Assert.assertEquals(directPaymentStateContext.getDirectPaymentTransactionModelDao().getAmount().compareTo(amount), 0);
        Assert.assertEquals(directPaymentStateContext.getDirectPaymentTransactionModelDao().getCurrency(), currency);

        // Verify we can update them
        final PaymentTransactionInfoPlugin paymentInfoPlugin = Mockito.mock(PaymentTransactionInfoPlugin.class);
        Mockito.when(paymentInfoPlugin.getAmount()).thenReturn(new BigDecimal("82010.222"));
        Mockito.when(paymentInfoPlugin.getCurrency()).thenReturn(Currency.CAD);
        Mockito.when(paymentInfoPlugin.getStatus()).thenReturn(PaymentPluginStatus.PROCESSED);
        Mockito.when(paymentInfoPlugin.getGatewayErrorCode()).thenReturn(UUID.randomUUID().toString().substring(0, 5));
        Mockito.when(paymentInfoPlugin.getGatewayError()).thenReturn(UUID.randomUUID().toString());
        daoHelper.processPaymentInfoPlugin(PaymentStatus.SUCCESS, paymentInfoPlugin, "SOME_STATE");

        final PaymentModelDao directPayment2 = daoHelper.getDirectPayment();
        Assert.assertEquals(directPayment2.getExternalKey(), directPaymentExternalKey);
        Assert.assertEquals(directPayment2.getStateName(), "SOME_STATE");
        Assert.assertEquals(directPaymentStateContext.getDirectPaymentTransactionModelDao().getPaymentId(), directPayment2.getId());
        Assert.assertEquals(directPaymentStateContext.getDirectPaymentTransactionModelDao().getTransactionExternalKey(), directPaymentTransactionExternalKey);
        Assert.assertEquals(directPaymentStateContext.getDirectPaymentTransactionModelDao().getPaymentStatus(), PaymentStatus.SUCCESS);
        Assert.assertEquals(directPaymentStateContext.getDirectPaymentTransactionModelDao().getAmount().compareTo(amount), 0);
        Assert.assertEquals(directPaymentStateContext.getDirectPaymentTransactionModelDao().getCurrency(), currency);
        Assert.assertEquals(directPaymentStateContext.getDirectPaymentTransactionModelDao().getProcessedAmount().compareTo(paymentInfoPlugin.getAmount()), 0);
        Assert.assertEquals(directPaymentStateContext.getDirectPaymentTransactionModelDao().getProcessedCurrency(), paymentInfoPlugin.getCurrency());
        Assert.assertEquals(directPaymentStateContext.getDirectPaymentTransactionModelDao().getGatewayErrorCode(), paymentInfoPlugin.getGatewayErrorCode());
        Assert.assertEquals(directPaymentStateContext.getDirectPaymentTransactionModelDao().getGatewayErrorMsg(), paymentInfoPlugin.getGatewayError());
    }

    @Test(groups = "slow")
    public void testNoDefaultPaymentMethod() throws Exception {
        final DirectPaymentAutomatonDAOHelper daoHelper = createDAOHelper(UUID.randomUUID(), directPaymentExternalKey, directPaymentTransactionExternalKey, amount, currency);
        try {
            daoHelper.getDefaultPaymentMethodId();
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_NO_DEFAULT_PAYMENT_METHOD.getCode());
        }
    }

    @Test(groups = "slow")
    public void testNoPaymentMethod() throws Exception {
        final DirectPaymentAutomatonDAOHelper daoHelper = createDAOHelper(UUID.randomUUID(), directPaymentExternalKey, directPaymentTransactionExternalKey, amount, currency);
        try {
            daoHelper.getPaymentProviderPlugin();
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD.getCode());
        }
    }

    private DirectPaymentAutomatonDAOHelper createDAOHelper(@Nullable final UUID directPaymentId, final String directPaymentExternalKey,
                                                            final String directPaymentTransactionExternalKey,
                                                            final BigDecimal amount, final Currency currency) throws Exception {
        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(UUID.randomUUID());
        // No default payment method

        directPaymentStateContext = new DirectPaymentStateContext(directPaymentId,
                                                                  directPaymentExternalKey,
                                                                  directPaymentTransactionExternalKey,
                                                                  TransactionType.CAPTURE,
                                                                  account,
                                                                  UUID.randomUUID(),
                                                                  amount,
                                                                  currency,
                                                                  false,
                                                                  ImmutableList.<PluginProperty>of(),
                                                                  internalCallContext,
                                                                  callContext);

        return new DirectPaymentAutomatonDAOHelper(directPaymentStateContext, clock.getUTCNow(), paymentDao, registry, internalCallContext);
    }
}
