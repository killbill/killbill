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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.payment.MockRecurringInvoiceItem;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.control.InvoicePaymentControlPluginApi;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PluginPropertyModelDao;
import org.killbill.billing.retry.plugin.api.PaymentControlApiException;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestPaymentApi extends PaymentTestSuiteWithEmbeddedDB {

    final PaymentOptions INVOICE_PAYMENT = new PaymentOptions() {
        @Override
        public boolean isExternalPayment() {
            return false;
        }

        @Override
        public String getPaymentControlPluginName() {
            return InvoicePaymentControlPluginApi.PLUGIN_NAME;
        }
    };

    private Account account;

    @BeforeClass(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        account = testHelper.createTestAccount("bobo@gmail.com", true);
    }

    @Test(groups = "slow")
    public void testCreateSuccessPurchase() throws PaymentApiException {

        final BigDecimal requestedAmount = BigDecimal.TEN;

        final String paymentExternalKey = "bwwrr";
        final String transactionExternalKey = "krapaut";

        final DirectPayment payment = paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, paymentExternalKey, transactionExternalKey,
                                                                ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment.getExternalKey(), paymentExternalKey);
        assertEquals(payment.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment.getAccountId(), account.getId());
        assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getPurchasedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCurrency(), Currency.AED);

        assertEquals(payment.getTransactions().size(), 1);
        assertEquals(payment.getTransactions().get(0).getExternalKey(), transactionExternalKey);
        assertEquals(payment.getTransactions().get(0).getDirectPaymentId(), payment.getId());
        assertEquals(payment.getTransactions().get(0).getAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getTransactions().get(0).getCurrency(), Currency.AED);
        assertEquals(payment.getTransactions().get(0).getProcessedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getTransactions().get(0).getProcessedCurrency(), Currency.AED);

        assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment.getTransactions().get(0).getTransactionType(), TransactionType.PURCHASE);
        assertNull(payment.getTransactions().get(0).getGatewayErrorMsg());
        assertNull(payment.getTransactions().get(0).getGatewayErrorCode());
    }

    @Test(groups = "slow")
    public void testCreateSuccessAuthCapture() throws PaymentApiException {

        final BigDecimal authAmount = BigDecimal.TEN;
        final BigDecimal captureAmount = BigDecimal.ONE;

        final String paymentExternalKey = "bouzou";
        final String transactionExternalKey = "kaput";
        final String transactionExternalKey2 = "kapu2t";

        final DirectPayment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, authAmount, Currency.AED, paymentExternalKey, transactionExternalKey,
                                                                     ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment.getExternalKey(), paymentExternalKey);
        assertEquals(payment.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment.getAccountId(), account.getId());
        assertEquals(payment.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCurrency(), Currency.AED);

        assertEquals(payment.getTransactions().size(), 1);
        assertEquals(payment.getTransactions().get(0).getExternalKey(), transactionExternalKey);
        assertEquals(payment.getTransactions().get(0).getDirectPaymentId(), payment.getId());
        assertEquals(payment.getTransactions().get(0).getAmount().compareTo(authAmount), 0);
        assertEquals(payment.getTransactions().get(0).getCurrency(), Currency.AED);
        assertEquals(payment.getTransactions().get(0).getProcessedAmount().compareTo(authAmount), 0);
        assertEquals(payment.getTransactions().get(0).getProcessedCurrency(), Currency.AED);

        assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment.getTransactions().get(0).getTransactionType(), TransactionType.AUTHORIZE);
        assertNull(payment.getTransactions().get(0).getGatewayErrorMsg());
        assertNull(payment.getTransactions().get(0).getGatewayErrorCode());

        final DirectPayment payment2 = paymentApi.createCapture(account, payment.getId(), captureAmount, Currency.AED, transactionExternalKey2,
                                                                ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment2.getExternalKey(), paymentExternalKey);
        assertEquals(payment2.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment2.getAccountId(), account.getId());
        assertEquals(payment2.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment2.getCapturedAmount().compareTo(captureAmount), 0);
        assertEquals(payment2.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getCurrency(), Currency.AED);

        assertEquals(payment2.getTransactions().size(), 2);
        assertEquals(payment2.getTransactions().get(1).getExternalKey(), transactionExternalKey2);
        assertEquals(payment2.getTransactions().get(1).getDirectPaymentId(), payment.getId());
        assertEquals(payment2.getTransactions().get(1).getAmount().compareTo(captureAmount), 0);
        assertEquals(payment2.getTransactions().get(1).getCurrency(), Currency.AED);
        assertEquals(payment2.getTransactions().get(1).getProcessedAmount().compareTo(captureAmount), 0);
        assertEquals(payment2.getTransactions().get(1).getProcessedCurrency(), Currency.AED);

        assertEquals(payment2.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment2.getTransactions().get(1).getTransactionType(), TransactionType.CAPTURE);
        assertNull(payment2.getTransactions().get(1).getGatewayErrorMsg());
        assertNull(payment2.getTransactions().get(1).getGatewayErrorCode());
    }

    @Test(groups = "slow")
    public void testCreateSuccessAuthMultipleCaptureAndRefund() throws PaymentApiException {

        final BigDecimal authAmount = BigDecimal.TEN;
        final BigDecimal captureAmount = BigDecimal.ONE;

        final String paymentExternalKey = "courou";
        final String transactionExternalKey = "sioux";
        final String transactionExternalKey2 = "sioux2";
        final String transactionExternalKey3 = "sioux3";
        final String transactionExternalKey4 = "sioux4";

        final DirectPayment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, authAmount, Currency.USD, paymentExternalKey, transactionExternalKey,
                                                                     ImmutableList.<PluginProperty>of(), callContext);

        paymentApi.createCapture(account, payment.getId(), captureAmount, Currency.USD, transactionExternalKey2,
                                 ImmutableList.<PluginProperty>of(), callContext);

        final DirectPayment payment3 = paymentApi.createCapture(account, payment.getId(), captureAmount, Currency.USD, transactionExternalKey3,
                                                                ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment3.getExternalKey(), paymentExternalKey);
        assertEquals(payment3.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment3.getAccountId(), account.getId());
        assertEquals(payment3.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment3.getCapturedAmount().compareTo(captureAmount.add(captureAmount)), 0);
        assertEquals(payment3.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment3.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment3.getCurrency(), Currency.USD);
        assertEquals(payment3.getTransactions().size(), 3);

        final DirectPayment payment4 = paymentApi.createRefund(account, payment3.getId(), payment3.getCapturedAmount(), Currency.USD, transactionExternalKey4, ImmutableList.<PluginProperty>of(), callContext);
        assertEquals(payment4.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment4.getCapturedAmount().compareTo(captureAmount.add(captureAmount)), 0);
        assertEquals(payment4.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment4.getRefundedAmount().compareTo(payment3.getCapturedAmount()), 0);
        assertEquals(payment4.getTransactions().size(), 4);

        assertEquals(payment4.getTransactions().get(3).getExternalKey(), transactionExternalKey4);
        assertEquals(payment4.getTransactions().get(3).getDirectPaymentId(), payment.getId());
        assertEquals(payment4.getTransactions().get(3).getAmount().compareTo(payment3.getCapturedAmount()), 0);
        assertEquals(payment4.getTransactions().get(3).getCurrency(), Currency.USD);
        assertEquals(payment4.getTransactions().get(3).getProcessedAmount().compareTo(payment3.getCapturedAmount()), 0);
        assertEquals(payment4.getTransactions().get(3).getProcessedCurrency(), Currency.USD);
        assertEquals(payment4.getTransactions().get(3).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment4.getTransactions().get(3).getTransactionType(), TransactionType.REFUND);
        assertNull(payment4.getTransactions().get(3).getGatewayErrorMsg());
        assertNull(payment4.getTransactions().get(3).getGatewayErrorCode());
    }

    @Test(groups = "slow")
    public void testCreateSuccessPurchaseWithPaymentControl() throws PaymentApiException, InvoiceApiException, EventBusException {

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate now = clock.getUTCToday();

        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD);

        final String paymentExternalKey = invoice.getId().toString();
        final String transactionExternalKey = "brrrrrr";

        invoice.addInvoiceItem(new MockRecurringInvoiceItem(invoice.getId(), account.getId(),
                                                            subscriptionId,
                                                            bundleId,
                                                            "test plan", "test phase", null,
                                                            now,
                                                            now.plusMonths(1),
                                                            requestedAmount,
                                                            new BigDecimal("1.0"),
                                                            Currency.USD));

        final DirectPayment payment = paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, paymentExternalKey, transactionExternalKey,
                                                                                  ImmutableList.<PluginProperty>of(), INVOICE_PAYMENT, callContext);

        assertEquals(payment.getExternalKey(), paymentExternalKey);
        assertEquals(payment.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment.getAccountId(), account.getId());
        assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getPurchasedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCurrency(), Currency.USD);

        assertEquals(payment.getTransactions().size(), 1);
        assertEquals(payment.getTransactions().get(0).getExternalKey(), transactionExternalKey);
        assertEquals(payment.getTransactions().get(0).getDirectPaymentId(), payment.getId());
        assertEquals(payment.getTransactions().get(0).getAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payment.getTransactions().get(0).getProcessedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getTransactions().get(0).getProcessedCurrency(), Currency.USD);

        assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment.getTransactions().get(0).getTransactionType(), TransactionType.PURCHASE);
        assertNull(payment.getTransactions().get(0).getGatewayErrorMsg());
        assertNull(payment.getTransactions().get(0).getGatewayErrorCode());

        // Not stricly an API test but interesting to verify that we indeed went through the attempt logic
        final List<PaymentAttemptModelDao> attempts = paymentDao.getPaymentAttempts(payment.getExternalKey(), internalCallContext);
        assertEquals(attempts.size(), 1);
    }

    @Test(groups = "slow")
    public void testCreateAbortedPurchaseWithPaymentControl() throws InvoiceApiException, EventBusException {

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate now = clock.getUTCToday();

        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD);

        final String paymentExternalKey = invoice.getId().toString();
        final String transactionExternalKey = "brrrrrr";

        invoice.addInvoiceItem(new MockRecurringInvoiceItem(invoice.getId(), account.getId(),
                                                            subscriptionId,
                                                            bundleId,
                                                            "test plan", "test phase", null,
                                                            now,
                                                            now.plusMonths(1),
                                                            BigDecimal.ONE,
                                                            new BigDecimal("1.0"),
                                                            Currency.USD));

        try {
            paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, paymentExternalKey, transactionExternalKey,
                                                        ImmutableList.<PluginProperty>of(), INVOICE_PAYMENT, callContext);
            Assert.fail("Unexpected success");
        } catch (PaymentApiException e) {
            assertTrue(e.getCause() instanceof PaymentControlApiException);
        }
    }

    @Test(groups = "slow")
    public void testCreateSuccessRefundWithPaymentControl() throws PaymentApiException, InvoiceApiException, EventBusException {

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate now = clock.getUTCToday();

        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD);

        final String paymentExternalKey = invoice.getId().toString();
        final String transactionExternalKey = "sacrebleu";
        final String transactionExternalKey2 = "maisenfin";

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

        final DirectPayment payment = paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, paymentExternalKey, transactionExternalKey,
                                                                                  ImmutableList.<PluginProperty>of(), INVOICE_PAYMENT, callContext);

        final List<PluginProperty> refundProperties = ImmutableList.<PluginProperty>of();
        final DirectPayment payment2 = paymentApi.createRefundWithPaymentControl(account, payment.getId(), requestedAmount, Currency.USD, transactionExternalKey2,
                                                                                 refundProperties, INVOICE_PAYMENT, callContext);

        assertEquals(payment2.getTransactions().size(), 2);
        assertEquals(payment2.getExternalKey(), paymentExternalKey);
        assertEquals(payment2.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment2.getAccountId(), account.getId());
        assertEquals(payment2.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getPurchasedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment2.getRefundedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment2.getCurrency(), Currency.USD);
    }

    @Test(groups = "slow")
    public void testCreateAbortedRefundWithPaymentControl() throws PaymentApiException, InvoiceApiException, EventBusException {

        final BigDecimal requestedAmount = BigDecimal.ONE;
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate now = clock.getUTCToday();

        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD);

        final String paymentExternalKey = invoice.getId().toString();
        final String transactionExternalKey = "payment";
        final String transactionExternalKey2 = "refund";

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

        final DirectPayment payment = paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, paymentExternalKey, transactionExternalKey,
                                                                                  ImmutableList.<PluginProperty>of(), INVOICE_PAYMENT, callContext);

        final List<PluginProperty> refundProperties = ImmutableList.<PluginProperty>of();

        try {
            paymentApi.createRefundWithPaymentControl(account, payment.getId(), BigDecimal.TEN, Currency.USD, transactionExternalKey2,
                                                      refundProperties, INVOICE_PAYMENT, callContext);
        } catch (PaymentApiException e) {
            assertTrue(e.getCause() instanceof PaymentControlApiException);
        }
    }

    @Test(groups = "slow")
    public void testCreateSuccessRefundPaymentControlWithItemAdjustments() throws PaymentApiException, InvoiceApiException, EventBusException {

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate now = clock.getUTCToday();

        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD);

        final String paymentExternalKey = invoice.getId().toString();
        final String transactionExternalKey = "hopla";
        final String transactionExternalKey2 = "chouette";

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

        final DirectPayment payment = paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, paymentExternalKey, transactionExternalKey,
                                                                                  ImmutableList.<PluginProperty>of(), INVOICE_PAYMENT, callContext);

        final List<PluginProperty> refundProperties = new ArrayList<PluginProperty>();
        final HashMap<UUID, BigDecimal> uuidBigDecimalHashMap = new HashMap<UUID, BigDecimal>();
        uuidBigDecimalHashMap.put(invoiceItem.getId(), null);
        final PluginProperty refundIdsProp = new PluginProperty(InvoicePaymentControlPluginApi.PROP_IPCD_REFUND_IDS_WITH_AMOUNT_KEY, uuidBigDecimalHashMap, false);
        refundProperties.add(refundIdsProp);

        final DirectPayment payment2 = paymentApi.createRefundWithPaymentControl(account, payment.getId(), null, Currency.USD, transactionExternalKey2,
                                                                                 refundProperties, INVOICE_PAYMENT, callContext);

        assertEquals(payment2.getTransactions().size(), 2);
        assertEquals(payment2.getExternalKey(), paymentExternalKey);
        assertEquals(payment2.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment2.getAccountId(), account.getId());
        assertEquals(payment2.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getPurchasedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment2.getRefundedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment2.getCurrency(), Currency.USD);
    }

    @Test(groups = "slow")
    public void testNotifyPaymentPaymentOfChargeback() throws PaymentApiException {
        final BigDecimal requestedAmount = BigDecimal.TEN;

        final String paymentExternalKey = "couic";
        final String transactionExternalKey = "couac";
        final String transactionExternalKey2 = "couyc";
        final UUID transactionId = UUID.randomUUID();

        final DirectPayment payment = paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, paymentExternalKey, transactionExternalKey,
                                                                ImmutableList.<PluginProperty>of(), callContext);

        paymentApi.notifyPendingTransactionOfStateChanged(account, transactionId, false, callContext);
        final DirectPayment payment2 = paymentApi.getPayment(payment.getId(), false, ImmutableList.<PluginProperty>of(), callContext);


        assertEquals(payment2.getExternalKey(), paymentExternalKey);
        assertEquals(payment2.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment2.getAccountId(), account.getId());
        assertEquals(payment2.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getPurchasedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment2.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getCurrency(), Currency.AED);

        assertEquals(payment2.getTransactions().size(), 2);
        assertEquals(payment2.getTransactions().get(1).getExternalKey(), transactionExternalKey2);
        assertEquals(payment2.getTransactions().get(1).getDirectPaymentId(), payment.getId());
        assertEquals(payment2.getTransactions().get(1).getAmount().compareTo(requestedAmount), 0);
        assertEquals(payment2.getTransactions().get(1).getCurrency(), Currency.AED);

        assertEquals(payment2.getTransactions().get(1).getProcessedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment2.getTransactions().get(1).getProcessedCurrency(), Currency.AED);

        assertEquals(payment2.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment2.getTransactions().get(1).getTransactionType(), TransactionType.CHARGEBACK);
        assertNull(payment2.getTransactions().get(1).getGatewayErrorMsg());
        assertNull(payment2.getTransactions().get(1).getGatewayErrorCode());

        // Attempt to any other operation afterwards, that should fail
        try {
            paymentApi.createPurchase(account, account.getPaymentMethodId(), payment.getId(), requestedAmount, Currency.AED, paymentExternalKey, transactionExternalKey,
                                      ImmutableList.<PluginProperty>of(), callContext);
            Assert.fail("Purchase not succeed after a chargeback");
        } catch (PaymentApiException e) {
            Assert.assertTrue(true);
        }
    }


    @Test(groups = "slow")
    public void testCreatePaymentWithNoDefaultPaymentMethod() throws Exception {
        final LocalDate now = clock.getUTCToday();
        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD);

        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final BigDecimal requestedAmount = BigDecimal.TEN;

        invoice.addInvoiceItem(new MockRecurringInvoiceItem(invoice.getId(), account.getId(),
                                                            subscriptionId,
                                                            bundleId,
                                                            "test plan", "test phase", null,
                                                            now,
                                                            now.plusMonths(1),
                                                            requestedAmount,
                                                            new BigDecimal("1.0"),
                                                            Currency.USD));

        try {

            final Account accountNoPaymentMethod = testHelper.createTestAccount("bobo@gmail.com", false);

            paymentApi.createPurchase(accountNoPaymentMethod, accountNoPaymentMethod.getPaymentMethodId(), null, requestedAmount, Currency.AED, invoice.getId().toString(), "yo", ImmutableList.<PluginProperty>of(), callContext);
        } catch (final PaymentApiException e) {
            assertEquals(e.getCode(), ErrorCode.PAYMENT_NO_DEFAULT_PAYMENT_METHOD.getCode());
        }
    }
}
