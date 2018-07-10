/*
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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.provider.DefaultNoOpPaymentMethodPlugin;
import org.killbill.billing.payment.provider.ExternalPaymentProviderPlugin;
import org.killbill.billing.util.entity.Pagination;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestExternalPaymentPlugin extends PaymentTestSuiteWithEmbeddedDB {

    private Account account;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        account = testHelper.createTestAccount("bob@gmail.com", false);
        account = addTestExternalPaymentMethod(account, new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), true, null));
    }

    @Test(groups = "slow")
    public void testGetPaymentInfoWithAndWithoutPluginInfo() throws PaymentApiException, PaymentPluginApiException {

        final BigDecimal requestedAmount = BigDecimal.TEN;

        final String paymentExternalKey = "externalKey";
        final String transactionExternalKey = "transactionKey";

        final Payment payment = paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, null, paymentExternalKey, transactionExternalKey,
                                                          ImmutableList.<PluginProperty>of(), callContext);

        final Payment paymentPluginInfoFalse = paymentApi.getPayment(payment.getId(), false, false, ImmutableList.<PluginProperty>of(), callContext);
        final Payment paymentPluginInfoTrue = paymentApi.getPayment(payment.getId(), true, false, ImmutableList.<PluginProperty>of(), callContext);

        Assert.assertEquals(paymentPluginInfoFalse.getAccountId(), paymentPluginInfoTrue.getAccountId());

        Assert.assertEquals(paymentPluginInfoFalse.getTransactions().size(), paymentPluginInfoTrue.getTransactions().size());
        Assert.assertEquals(paymentPluginInfoFalse.getExternalKey(), paymentPluginInfoTrue.getExternalKey());
        Assert.assertEquals(paymentPluginInfoFalse.getPaymentMethodId(), paymentPluginInfoTrue.getPaymentMethodId());
        Assert.assertEquals(paymentPluginInfoFalse.getAccountId(), paymentPluginInfoTrue.getAccountId());
        Assert.assertEquals(paymentPluginInfoFalse.getAuthAmount().compareTo(paymentPluginInfoTrue.getAuthAmount()), 0);
        Assert.assertEquals(paymentPluginInfoFalse.getCapturedAmount().compareTo(paymentPluginInfoTrue.getCapturedAmount()), 0);
        Assert.assertEquals(paymentPluginInfoFalse.getPurchasedAmount().compareTo(paymentPluginInfoTrue.getPurchasedAmount()), 0);
        Assert.assertEquals(paymentPluginInfoFalse.getRefundedAmount().compareTo(paymentPluginInfoTrue.getRefundedAmount()), 0);
        Assert.assertEquals(paymentPluginInfoFalse.getCurrency(), paymentPluginInfoTrue.getCurrency());

        for (int i = 0; i < paymentPluginInfoFalse.getTransactions().size(); i++) {
            final PaymentTransaction noPluginInfoPaymentTransaction = paymentPluginInfoFalse.getTransactions().get(i);
            final PaymentTransaction paymentTransaction = paymentPluginInfoTrue.getTransactions().get(i);
            Assert.assertEquals(noPluginInfoPaymentTransaction.getAmount().compareTo(paymentTransaction.getAmount()), 0);
            Assert.assertEquals(noPluginInfoPaymentTransaction.getProcessedAmount().compareTo(paymentTransaction.getProcessedAmount()), 0);
            Assert.assertEquals(noPluginInfoPaymentTransaction.getCurrency(), paymentTransaction.getCurrency());
            Assert.assertEquals(noPluginInfoPaymentTransaction.getExternalKey(), paymentTransaction.getExternalKey());
            Assert.assertEquals(noPluginInfoPaymentTransaction.getTransactionStatus(), paymentTransaction.getTransactionStatus());
            Assert.assertEquals(noPluginInfoPaymentTransaction.getPaymentInfoPlugin(), paymentTransaction.getPaymentInfoPlugin());
        }

    }

    @Test(groups = "slow")
    public void testGetPaymentMethodsWithAndWithoutPluginInfo() throws PaymentApiException, PaymentPluginApiException {

        final BigDecimal requestedAmount = BigDecimal.TEN;

        final String paymentExternalKey = "externalKey";
        final String transactionExternalKey = "transactionKey";

        final Payment payment = paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount,
                                                          Currency.AED, null, paymentExternalKey, transactionExternalKey,
                                                          ImmutableList.<PluginProperty>of(), callContext);

        final Pagination<PaymentMethod> paymentMethods = paymentApi.getPaymentMethods(0L, 10L, false, null, callContext);
        final Pagination<PaymentMethod> paymentMethodsPlugin = paymentApi.getPaymentMethods(0L, 10L, true, null, callContext);

        Assert.assertTrue(paymentMethods.getTotalNbRecords() == 1);
        Assert.assertTrue(paymentMethodsPlugin.getTotalNbRecords() == 1);

        PaymentMethod paymentMethod = paymentMethods.iterator().next();
        PaymentMethod paymentMethodPlugin = paymentMethodsPlugin.iterator().next();

        Assert.assertEquals(paymentMethod.getAccountId(), paymentMethodPlugin.getAccountId());
        Assert.assertEquals(paymentMethod.getId(), paymentMethodPlugin.getId());
        Assert.assertEquals(paymentMethod.getExternalKey(), paymentMethodPlugin.getExternalKey());
        Assert.assertEquals(paymentMethod.getPluginName(), paymentMethodPlugin.getPluginName());

        Assert.assertNull(paymentMethod.getPluginDetail());
        Assert.assertNotNull(paymentMethodPlugin.getPluginDetail());
        Assert.assertTrue(paymentMethodPlugin.getPluginDetail().getProperties().isEmpty());

    }

    private Account addTestExternalPaymentMethod(final Account account, final PaymentMethodPlugin paymentMethodInfo)
            throws Exception {
        final UUID paymentMethodId = paymentApi.addPaymentMethod(account, paymentMethodInfo.getExternalPaymentMethodId(),
                                                                 ExternalPaymentProviderPlugin.PLUGIN_NAME, true,
                                                                 paymentMethodInfo, ImmutableList.<PluginProperty>of(), callContext);
        return accountApi.getAccountById(account.getId(), internalCallContext);
    }

}
