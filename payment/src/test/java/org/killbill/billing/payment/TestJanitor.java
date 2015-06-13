/*
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

package org.killbill.billing.payment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentOptions;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.janitor.Janitor;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.invoice.InvoicePaymentRoutingPluginApi;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import static org.testng.Assert.assertEquals;

public class TestJanitor extends PaymentTestSuiteWithEmbeddedDB {

    final PaymentOptions INVOICE_PAYMENT = new PaymentOptions() {
        @Override
        public boolean isExternalPayment() {
            return false;
        }

        @Override
        public List<String> getPaymentControlPluginNames() {
            return ImmutableList.of(InvoicePaymentRoutingPluginApi.PLUGIN_NAME);
        }
    };

    @Inject
    private Janitor janitor;

    private Account account;

    @Override
    protected KillbillConfigSource getConfigSource() {
        return getConfigSource("/payment.properties",
                               ImmutableMap.<String, String>of("org.killbill.payment.provider.default", MockPaymentProviderPlugin.PLUGIN_NAME,
                                                               "killbill.payment.engine.events.off", "false",
                                                               "org.killbill.payment.janitor.rate", "500ms")
                              );
    }

    @BeforeClass(groups = "slow")
    protected void beforeClass() throws Exception {
        super.beforeClass();
        janitor.start();
    }

    @AfterClass(groups = "slow")
    protected void afterClass() throws Exception {
        janitor.stop();
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        account = testHelper.createTestAccount("bobo@gmail.com", true);
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        super.afterMethod();
    }

    @Test(groups = "slow")
    public void testCreateSuccessPurchaseWithPaymentControl() throws PaymentApiException, InvoiceApiException, EventBusException {

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate now = clock.getUTCToday();

        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD);

        final String paymentExternalKey = invoice.getId().toString();
        final String transactionExternalKey = "wouf wouf";

        invoice.addInvoiceItem(new MockRecurringInvoiceItem(invoice.getId(), account.getId(),
                                                            subscriptionId,
                                                            bundleId,
                                                            "test plan",
                                                            "test phase", null,
                                                            now,
                                                            now.plusMonths(1),
                                                            requestedAmount,
                                                            new BigDecimal("1.0"),
                                                            Currency.USD));

        final Payment payment = paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, paymentExternalKey, transactionExternalKey,
                                                                            createPropertiesForInvoice(invoice), INVOICE_PAYMENT, callContext);
        assertEquals(payment.getTransactions().size(), 1);
        assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment.getTransactions().get(0).getTransactionType(), TransactionType.PURCHASE);

        final List<PaymentAttemptModelDao> attempts = paymentDao.getPaymentAttempts(paymentExternalKey, internalCallContext);
        assertEquals(attempts.size(), 1);

        final PaymentAttemptModelDao attempt = attempts.get(0);
        assertEquals(attempt.getStateName(), "SUCCESS");

        // Ok now the fun part starts... we modify the attempt state to be 'INIT' and wait the the Janitor to do its job.
        paymentDao.updatePaymentAttempt(attempt.getId(), attempt.getTransactionId(), "INIT", internalCallContext);
        final PaymentAttemptModelDao attempt2 = paymentDao.getPaymentAttempt(attempt.getId(), internalCallContext);
        assertEquals(attempt2.getStateName(), "INIT");

        clock.addDays(1);
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
        }
        ;

        final PaymentAttemptModelDao attempt3 = paymentDao.getPaymentAttempt(attempt.getId(), internalCallContext);
        assertEquals(attempt3.getStateName(), "SUCCESS");
    }

    @Test(groups = "slow")
    public void testCreateSuccessRefundPaymentControlWithItemAdjustments() throws PaymentApiException, InvoiceApiException, EventBusException {

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate now = clock.getUTCToday();

        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD);

        final String paymentExternalKey = invoice.getId().toString();
        final String transactionExternalKey = "craboom";
        final String transactionExternalKey2 = "qwerty";

        final InvoiceItem invoiceItem = new MockRecurringInvoiceItem(invoice.getId(), account.getId(),
                                                                     subscriptionId,
                                                                     bundleId,
                                                                     "test plan", "test phase", null,
                                                                     now,
                                                                     now.plusMonths(1),
                                                                     requestedAmount,
                                                                     new BigDecimal("1.0"),
                                                                     Currency.USD);
        invoice.addInvoiceItem(invoiceItem);

        final Payment payment = paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, paymentExternalKey, transactionExternalKey,
                                                                            createPropertiesForInvoice(invoice), INVOICE_PAYMENT, callContext);

        final List<PluginProperty> refundProperties = new ArrayList<PluginProperty>();
        final HashMap<UUID, BigDecimal> uuidBigDecimalHashMap = new HashMap<UUID, BigDecimal>();
        uuidBigDecimalHashMap.put(invoiceItem.getId(), new BigDecimal("1.0"));
        final PluginProperty refundIdsProp = new PluginProperty(InvoicePaymentRoutingPluginApi.PROP_IPCD_REFUND_IDS_WITH_AMOUNT_KEY, uuidBigDecimalHashMap, false);
        refundProperties.add(refundIdsProp);

        final Payment payment2 = paymentApi.createRefundWithPaymentControl(account, payment.getId(), null, Currency.USD, transactionExternalKey2,
                                                                           refundProperties, INVOICE_PAYMENT, callContext);

        assertEquals(payment2.getTransactions().size(), 2);
        PaymentTransaction refundTransaction = payment2.getTransactions().get(1);
        assertEquals(refundTransaction.getTransactionType(), TransactionType.REFUND);

        final List<PaymentAttemptModelDao> attempts = paymentDao.getPaymentAttempts(paymentExternalKey, internalCallContext);
        assertEquals(attempts.size(), 2);

        final PaymentAttemptModelDao refundAttempt = attempts.get(1);
        assertEquals(refundAttempt.getTransactionType(), TransactionType.REFUND);

        // Ok now the fun part starts... we modify the attempt state to be 'INIT' and wait the the Janitor to do its job.
        paymentDao.updatePaymentAttempt(refundAttempt.getId(), refundAttempt.getTransactionId(), "INIT", internalCallContext);
        final PaymentAttemptModelDao attempt2 = paymentDao.getPaymentAttempt(refundAttempt.getId(), internalCallContext);
        assertEquals(attempt2.getStateName(), "INIT");

        clock.addDays(1);
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
        }
        ;

        final PaymentAttemptModelDao attempt3 = paymentDao.getPaymentAttempt(refundAttempt.getId(), internalCallContext);
        assertEquals(attempt3.getStateName(), "SUCCESS");
    }

    @Test(groups = "slow")
    public void testUnknownEntries() throws PaymentApiException, InvoiceApiException, EventBusException {

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final String paymentExternalKey = "qwru";
        final String transactionExternalKey = "lkjdsf";

        final Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, account.getCurrency(), paymentExternalKey,
                                                               transactionExternalKey, ImmutableList.<PluginProperty>of(), callContext);

        // Artificially move the transaction status to UNKNOWN
        final String paymentStateName = paymentSMHelper.getErroredStateForTransaction(TransactionType.AUTHORIZE).toString();
        paymentDao.updatePaymentAndTransactionOnCompletion(account.getId(), payment.getId(), TransactionType.AUTHORIZE, paymentStateName, paymentStateName,
                                                           payment.getTransactions().get(0).getId(), TransactionStatus.UNKNOWN, requestedAmount, account.getCurrency(),
                                                           "foo", "bar", internalCallContext);
        // The UnknownPaymentTransactionTask will look for UNKNOWN payment that *just happened* , and that are not too old (less than 7 days)
        clock.addDays(1);
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
        }

        final Payment updatedPayment = paymentApi.getPayment(payment.getId(), false, ImmutableList.<PluginProperty>of(), callContext);
        assertEquals(updatedPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);

    }



    @Test(groups = "slow")
    public void testPendingEntries() throws PaymentApiException, InvoiceApiException, EventBusException {

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final String paymentExternalKey = "jhj44";
        final String transactionExternalKey = "4jhjj2";

        final Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, account.getCurrency(), paymentExternalKey,
                                                               transactionExternalKey, ImmutableList.<PluginProperty>of(), callContext);

        // Artificially move the transaction status to PENDING
        final String paymentStateName = paymentSMHelper.getPendingStateForTransaction(TransactionType.AUTHORIZE).toString();
        paymentDao.updatePaymentAndTransactionOnCompletion(account.getId(), payment.getId(), TransactionType.AUTHORIZE, paymentStateName, paymentStateName,
                                                           payment.getTransactions().get(0).getId(), TransactionStatus.PENDING, requestedAmount, account.getCurrency(),
                                                           "loup", "chat", internalCallContext);
        clock.addDays(1);
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
        }

        final Payment updatedPayment = paymentApi.getPayment(payment.getId(), false, ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(updatedPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);

    }

    private List<PluginProperty> createPropertiesForInvoice(final Invoice invoice) {
        final List<PluginProperty> result = new ArrayList<PluginProperty>();
        result.add(new PluginProperty(InvoicePaymentRoutingPluginApi.PROP_IPCD_INVOICE_ID, invoice.getId().toString(), false));
        return result;
    }
}

