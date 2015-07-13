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

package org.killbill.billing.payment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.callcontext.InternalCallContext;
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
import org.killbill.billing.payment.bus.PaymentBusEventHandler;
import org.killbill.billing.payment.core.janitor.Janitor;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.glue.DefaultPaymentService;
import org.killbill.billing.payment.invoice.InvoicePaymentControlPluginApi;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestJanitor extends PaymentTestSuiteWithEmbeddedDB {

    final PaymentOptions INVOICE_PAYMENT = new PaymentOptions() {
        @Override
        public boolean isExternalPayment() {
            return false;
        }

        @Override
        public List<String> getPaymentControlPluginNames() {
            return ImmutableList.of(InvoicePaymentControlPluginApi.PLUGIN_NAME);
        }
    };

    @Inject
    protected TestApiListener testListener;
    @Inject
    protected InternalCallContextFactory internalCallContextFactory;
    @Inject
    protected NotificationQueueService notificationQueueService;
    @Inject
    private Janitor janitor;
    @Inject
    private PaymentBusEventHandler handler;
    private MockPaymentProviderPlugin mockPaymentProviderPlugin;

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
        mockPaymentProviderPlugin = (MockPaymentProviderPlugin) registry.getServiceForName(MockPaymentProviderPlugin.PLUGIN_NAME);
        janitor.initialize();
        janitor.start();
    }

    @AfterClass(groups = "slow")
    protected void afterClass() throws Exception {
        janitor.stop();
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        eventBus.register(handler);
        testListener.reset();
        eventBus.register(testListener);
        mockPaymentProviderPlugin.clear();
        account = testHelper.createTestAccount("bobo@gmail.com", true);
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        eventBus.unregister(handler);
        eventBus.unregister(testListener);
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
        final PluginProperty refundIdsProp = new PluginProperty(InvoicePaymentControlPluginApi.PROP_IPCD_REFUND_IDS_WITH_AMOUNT_KEY, uuidBigDecimalHashMap, false);
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

        final PaymentAttemptModelDao attempt3 = paymentDao.getPaymentAttempt(refundAttempt.getId(), internalCallContext);
        assertEquals(attempt3.getStateName(), "SUCCESS");
    }

    @Test(groups = "slow")
    public void testUnknownEntries() throws PaymentApiException, InvoiceApiException, EventBusException {

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final String paymentExternalKey = "qwru";
        final String transactionExternalKey = "lkjdsf";

        testListener.pushExpectedEvent(NextEvent.PAYMENT);
        final Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, account.getCurrency(), paymentExternalKey,
                                                               transactionExternalKey, ImmutableList.<PluginProperty>of(), callContext);
        testListener.assertListenerStatus();

        // Artificially move the transaction status to UNKNOWN
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        final String paymentStateName = paymentSMHelper.getErroredStateForTransaction(TransactionType.AUTHORIZE).toString();
        testListener.pushExpectedEvent(NextEvent.PAYMENT_PLUGIN_ERROR);
        paymentDao.updatePaymentAndTransactionOnCompletion(account.getId(), payment.getId(), TransactionType.AUTHORIZE, paymentStateName, paymentStateName,
                                                           payment.getTransactions().get(0).getId(), TransactionStatus.UNKNOWN, requestedAmount, account.getCurrency(),
                                                           "foo", "bar", internalCallContext);
        testListener.assertListenerStatus();

        // Move clock for notification to be processed
        clock.addDeltaFromReality(5 * 60 * 1000);
        assertNotificationsCompleted(internalCallContext, 5);

        final Payment updatedPayment = paymentApi.getPayment(payment.getId(), false, ImmutableList.<PluginProperty>of(), callContext);
        assertEquals(updatedPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
    }

    @Test(groups = "slow")
    public void testUnknownEntriesWithFailures() throws PaymentApiException, EventBusException {

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final String paymentExternalKey = "minus";
        final String transactionExternalKey = "plus";

        // Make sure the state as seen by the plugin will be in PaymentPluginStatus.ERROR, which will be returned later to Janitor
        mockPaymentProviderPlugin.makeNextPaymentFailWithError();
        testListener.pushExpectedEvent(NextEvent.PAYMENT_ERROR);
        final Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, account.getCurrency(), paymentExternalKey,
                                                               transactionExternalKey, ImmutableList.<PluginProperty>of(), callContext);
        testListener.assertListenerStatus();

        // Artificially move the transaction status to UNKNOWN
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
        final String paymentStateName = paymentSMHelper.getErroredStateForTransaction(TransactionType.AUTHORIZE).toString();
        testListener.pushExpectedEvent(NextEvent.PAYMENT_PLUGIN_ERROR);
        paymentDao.updatePaymentAndTransactionOnCompletion(account.getId(), payment.getId(), TransactionType.AUTHORIZE, paymentStateName, paymentStateName,
                                                           payment.getTransactions().get(0).getId(), TransactionStatus.UNKNOWN, requestedAmount, account.getCurrency(),
                                                           "foo", "bar", internalCallContext);
        testListener.assertListenerStatus();

        final List<PaymentTransactionModelDao> paymentTransactionHistoryBeforeJanitor = getPaymentTransactionHistory(transactionExternalKey);
        Assert.assertEquals(paymentTransactionHistoryBeforeJanitor.size(), 3);

        // Move clock for notification to be processed
        clock.addDeltaFromReality(5 * 60 * 1000);
        assertNotificationsCompleted(internalCallContext, 5);

        // Proves the Janitor ran (and updated the transaction)
        final List<PaymentTransactionModelDao> paymentTransactionHistoryAfterJanitor = getPaymentTransactionHistory(transactionExternalKey);
        Assert.assertEquals(paymentTransactionHistoryAfterJanitor.size(), 4);
        Assert.assertEquals(paymentTransactionHistoryAfterJanitor.get(3).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);

        final Payment updatedPayment = paymentApi.getPayment(payment.getId(), false, ImmutableList.<PluginProperty>of(), callContext);
        // Janitor should have moved us to PAYMENT_FAILURE
        assertEquals(updatedPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
    }

    @Test(groups = "slow")
    public void testUnknownEntriesWithExceptions() throws PaymentApiException, EventBusException {

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final String paymentExternalKey = "minus";
        final String transactionExternalKey = "plus";

        // Make sure the state as seen by the plugin will be in PaymentPluginStatus.ERROR, which will be returned later to Janitor
        mockPaymentProviderPlugin.makeNextPaymentFailWithException();
        try {
            testListener.pushExpectedEvent(NextEvent.PAYMENT_PLUGIN_ERROR);
            paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, account.getCurrency(), paymentExternalKey,
                                           transactionExternalKey, ImmutableList.<PluginProperty>of(), callContext);
        } catch (PaymentApiException ignore) {
            testListener.assertListenerStatus();
        }
        final Payment payment = paymentApi.getPaymentByExternalKey(paymentExternalKey, false, ImmutableList.<PluginProperty>of(), callContext);

        // Artificially move the transaction status to UNKNOWN
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);

        final String paymentStateName = paymentSMHelper.getErroredStateForTransaction(TransactionType.AUTHORIZE).toString();
        testListener.pushExpectedEvent(NextEvent.PAYMENT_PLUGIN_ERROR);
        paymentDao.updatePaymentAndTransactionOnCompletion(account.getId(), payment.getId(), TransactionType.AUTHORIZE, paymentStateName, paymentStateName,
                                                           payment.getTransactions().get(0).getId(), TransactionStatus.UNKNOWN, requestedAmount, account.getCurrency(),
                                                           "foo", "bar", internalCallContext);
        testListener.assertListenerStatus();

        // Move clock for notification to be processed
        clock.addDeltaFromReality(5 * 60 * 1000);
        // NO because we will keep retrying as we can't fix it...
        //assertNotificationsCompleted(internalCallContext, 5);

        final List<PaymentTransactionModelDao> paymentTransactionHistoryBeforeJanitor = getPaymentTransactionHistory(transactionExternalKey);
        Assert.assertEquals(paymentTransactionHistoryBeforeJanitor.size(), 3);

        // Nothing new happened
        final List<PaymentTransactionModelDao> paymentTransactionHistoryAfterJanitor = getPaymentTransactionHistory(transactionExternalKey);
        Assert.assertEquals(paymentTransactionHistoryAfterJanitor.size(), 3);
    }

    @Test(groups = "slow")
    public void testPendingEntries() throws PaymentApiException, EventBusException, NoSuchNotificationQueue {

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final String paymentExternalKey = "jhj44";
        final String transactionExternalKey = "4jhjj2";

        testListener.pushExpectedEvent(NextEvent.PAYMENT);
        final Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, account.getCurrency(), paymentExternalKey,
                                                               transactionExternalKey, ImmutableList.<PluginProperty>of(), callContext);
        testListener.assertListenerStatus();

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);

        // Artificially move the transaction status to PENDING
        final String paymentStateName = paymentSMHelper.getPendingStateForTransaction(TransactionType.AUTHORIZE).toString();
        testListener.pushExpectedEvent(NextEvent.PAYMENT);
        paymentDao.updatePaymentAndTransactionOnCompletion(account.getId(), payment.getId(), TransactionType.AUTHORIZE, paymentStateName, paymentStateName,
                                                           payment.getTransactions().get(0).getId(), TransactionStatus.PENDING, requestedAmount, account.getCurrency(),
                                                           "loup", "chat", internalCallContext);
        testListener.assertListenerStatus();

        // Move clock for notification to be processed
        clock.addDeltaFromReality(5 * 60 * 1000);

        assertNotificationsCompleted(internalCallContext, 5);
        final Payment updatedPayment = paymentApi.getPayment(payment.getId(), false, ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(updatedPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
    }

    private List<PluginProperty> createPropertiesForInvoice(final Invoice invoice) {
        final List<PluginProperty> result = new ArrayList<PluginProperty>();
        result.add(new PluginProperty(InvoicePaymentControlPluginApi.PROP_IPCD_INVOICE_ID, invoice.getId().toString(), false));
        return result;
    }

    // I wish we had a simplest way to query our history rows..
    private List<PaymentTransactionModelDao> getPaymentTransactionHistory(final String transactionExternalKey) {
        return dbi.withHandle(new HandleCallback<List<PaymentTransactionModelDao>>() {
            @Override
            public List<PaymentTransactionModelDao> withHandle(final Handle handle) throws Exception {
                final List<Map<String, Object>> queryResult = handle.select("select * from payment_transaction_history where transaction_external_key = ? order by record_id asc",
                                                                            transactionExternalKey);
                final List<PaymentTransactionModelDao> result = new ArrayList<PaymentTransactionModelDao>(queryResult.size());
                for (final Map<String, Object> row : queryResult) {
                    final PaymentTransactionModelDao transactionModelDao = new PaymentTransactionModelDao(UUID.fromString((String) row.get("id")),
                                                                                                          null,
                                                                                                          (String) row.get("transaction_external_key"),
                                                                                                          null,
                                                                                                          null,
                                                                                                          UUID.fromString((String) row.get("payment_id")),
                                                                                                          TransactionType.valueOf((String) row.get("transaction_type")),
                                                                                                          null,
                                                                                                          TransactionStatus.valueOf((String) row.get("transaction_status")),
                                                                                                          (BigDecimal) row.get("amount"),
                                                                                                          Currency.valueOf((String) row.get("currency")),
                                                                                                          (String) row.get("gateway_error_code"),
                                                                                                          (String) row.get("gateway_error_msg"));
                    result.add(transactionModelDao);
                }
                return result;
            }
        });
    }

    private void assertNotificationsCompleted(final InternalCallContext internalCallContext, final long timeoutSec) {
        try {
            await().atMost(timeoutSec, SECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    final List<NotificationEventWithMetadata<NotificationEvent>> notifications = notificationQueueService.getNotificationQueue(DefaultPaymentService.SERVICE_NAME, Janitor.QUEUE_NAME).getFutureOrInProcessingNotificationForSearchKeys(internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId());
                    return notifications.isEmpty();
                }
            });
        } catch (final Exception e) {
            fail("Test failed ", e);
        }
    }
}

