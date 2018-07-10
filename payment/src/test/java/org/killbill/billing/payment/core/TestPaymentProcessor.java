/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.events.PaymentErrorInternalEvent;
import org.killbill.billing.events.PaymentInfoInternalEvent;
import org.killbill.billing.events.PaymentInternalEvent;
import org.killbill.billing.events.PaymentPluginErrorInternalEvent;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import org.awaitility.Awaitility;

import static java.math.BigDecimal.ZERO;

public class TestPaymentProcessor extends PaymentTestSuiteWithEmbeddedDB {

    private static final boolean SHOULD_LOCK_ACCOUNT = true;
    private static final ImmutableList<PluginProperty> PLUGIN_PROPERTIES = ImmutableList.<PluginProperty>of();
    private static final BigDecimal FIVE = new BigDecimal("5");
    private static final BigDecimal TEN = new BigDecimal("10");
    private static final Currency CURRENCY = Currency.BTC;

    private MockPaymentProviderPlugin mockPaymentProviderPlugin;
    private PaymentBusListener paymentBusListener;
    private Account account;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        if (hasFailed()) {
            return;
        }
        mockPaymentProviderPlugin = (MockPaymentProviderPlugin) registry.getServiceForName(MockPaymentProviderPlugin.PLUGIN_NAME);

        account = testHelper.createTestAccount(UUID.randomUUID().toString(), true);

        paymentBusListener = new PaymentBusListener();
        eventBus.register(paymentBusListener);
    }

    @Test(groups = "slow")
    public void testGetAccountPaymentsWithJanitor() throws Exception {
        final String paymentExternalKey = UUID.randomUUID().toString();

        final Iterable<PluginProperty> pluginPropertiesToDriveTransationToUnknown = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.UNDEFINED, false));

        final String authorizationKey = UUID.randomUUID().toString();
        final Payment authorization = paymentProcessor.createAuthorization(true, null, account, null, null, TEN, CURRENCY, null, paymentExternalKey, authorizationKey,
                                                                           null, null, SHOULD_LOCK_ACCOUNT, pluginPropertiesToDriveTransationToUnknown, callContext, internalCallContext);
        verifyPayment(authorization, paymentExternalKey, ZERO, ZERO, ZERO, 1);
        final UUID paymentId = authorization.getId();
        verifyPaymentTransaction(authorization.getTransactions().get(0), authorizationKey, TransactionType.AUTHORIZE, TEN, paymentId);
        paymentBusListener.verify(0, 0, 1, account.getId(), paymentId, ZERO, TransactionStatus.UNKNOWN);

        mockPaymentProviderPlugin.overridePaymentPluginStatus(paymentId, authorization.getTransactions().get(0).getId(), PaymentPluginStatus.PROCESSED);

        final List<Payment> payments = paymentControlAwareRefresher.getAccountPayments(account.getId(), true, false, callContext, internalCallContext);
        Assert.assertEquals(payments.size(), 1);
        verifyPayment(payments.get(0), paymentExternalKey, TEN, ZERO, ZERO, 1);
        verifyPaymentTransaction(payments.get(0).getTransactions().get(0), authorizationKey, TransactionType.AUTHORIZE, TEN, paymentId);
        paymentBusListener.verify(1, 0, 1, account.getId(), paymentId, TEN, TransactionStatus.SUCCESS);
    }

    @Test(groups = "slow")
    public void testClassicFlow() throws Exception {
        final String paymentExternalKey = UUID.randomUUID().toString();

        final Iterable<PluginProperty> pluginPropertiesToDriveTransationToPending = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.PENDING, false));

        // AUTH pre-3DS
        final String authorizationKey = UUID.randomUUID().toString();
        final Payment authorization = paymentProcessor.createAuthorization(true, null, account, null, null, TEN, CURRENCY, null,paymentExternalKey, authorizationKey,
                                                                           null, null, SHOULD_LOCK_ACCOUNT, pluginPropertiesToDriveTransationToPending, callContext, internalCallContext);
        verifyPayment(authorization, paymentExternalKey, ZERO, ZERO, ZERO, 1);
        final UUID paymentId = authorization.getId();
        verifyPaymentTransaction(authorization.getTransactions().get(0), authorizationKey, TransactionType.AUTHORIZE, TEN, paymentId);
        paymentBusListener.verify(1, account.getId(), paymentId, TEN, TransactionStatus.PENDING);

        // AUTH post-3DS
        final Payment authorizationPost3DS = paymentProcessor.createAuthorization(true, null, account, null, paymentId, TEN, CURRENCY, null,paymentExternalKey, authorizationKey,
                                                                                  null, null, SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyPayment(authorizationPost3DS, paymentExternalKey, TEN, ZERO, ZERO, 1);
        verifyPaymentTransaction(authorizationPost3DS.getTransactions().get(0), authorizationKey, TransactionType.AUTHORIZE, TEN, paymentId);
        paymentBusListener.verify(2, account.getId(), paymentId, TEN, TransactionStatus.SUCCESS);

        // CAPTURE
        final String capture1Key = UUID.randomUUID().toString();
        final Payment partialCapture1 = paymentProcessor.createCapture(true, null, account, paymentId, FIVE, CURRENCY, null,capture1Key,
                                                                       null, SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyPayment(partialCapture1, paymentExternalKey, TEN, FIVE, ZERO, 2);
        verifyPaymentTransaction(partialCapture1.getTransactions().get(1), capture1Key, TransactionType.CAPTURE, FIVE, paymentId);
        paymentBusListener.verify(3, account.getId(), paymentId, FIVE, TransactionStatus.SUCCESS);

        // CAPTURE
        final String capture2Key = UUID.randomUUID().toString();
        final Payment partialCapture2 = paymentProcessor.createCapture(true, null, account, paymentId, FIVE, CURRENCY, null,capture2Key,
                                                                       null, SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyPayment(partialCapture2, paymentExternalKey, TEN, TEN, ZERO, 3);
        verifyPaymentTransaction(partialCapture2.getTransactions().get(2), capture2Key, TransactionType.CAPTURE, FIVE, paymentId);
        paymentBusListener.verify(4, account.getId(), paymentId, FIVE, TransactionStatus.SUCCESS);

        // REFUND
        final String refund1Key = UUID.randomUUID().toString();
        final Payment partialRefund1 = paymentProcessor.createRefund(true, null, account, paymentId, FIVE, CURRENCY, null,refund1Key,
                                                                     null, SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyPayment(partialRefund1, paymentExternalKey, TEN, TEN, FIVE, 4);
        verifyPaymentTransaction(partialRefund1.getTransactions().get(3), refund1Key, TransactionType.REFUND, FIVE, paymentId);
        paymentBusListener.verify(5, account.getId(), paymentId, FIVE, TransactionStatus.SUCCESS);

        // REFUND
        final String refund2Key = UUID.randomUUID().toString();
        final Payment partialRefund2 = paymentProcessor.createRefund(true, null, account, paymentId, FIVE, CURRENCY, null,refund2Key,
                                                                     null, SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyPayment(partialRefund2, paymentExternalKey, TEN, TEN, TEN, 5);
        verifyPaymentTransaction(partialRefund2.getTransactions().get(4), refund2Key, TransactionType.REFUND, FIVE, paymentId);
        paymentBusListener.verify(6, account.getId(), paymentId, FIVE, TransactionStatus.SUCCESS);
    }

    @Test(groups = "slow")
    public void testVoid() throws Exception {
        final String paymentExternalKey = UUID.randomUUID().toString();

        // AUTH
        final String authorizationKey = UUID.randomUUID().toString();
        final Payment authorization = paymentProcessor.createAuthorization(true, null, account, null, null, TEN, CURRENCY, null,paymentExternalKey, authorizationKey,
                                                                           null, null, SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyPayment(authorization, paymentExternalKey, TEN, ZERO, ZERO, 1);
        final UUID paymentId = authorization.getId();
        verifyPaymentTransaction(authorization.getTransactions().get(0), authorizationKey, TransactionType.AUTHORIZE, TEN, paymentId);
        paymentBusListener.verify(1, account.getId(), paymentId, TEN, TransactionStatus.SUCCESS);

        // VOID
        final String voidKey = UUID.randomUUID().toString();
        final Payment voidTransaction = paymentProcessor.createVoid(true, null, account, paymentId, null, voidKey,
                                                                    null, SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyPayment(voidTransaction, paymentExternalKey, ZERO, ZERO, ZERO, 2);
        verifyPaymentTransaction(voidTransaction.getTransactions().get(1), voidKey, TransactionType.VOID, null, paymentId);
        paymentBusListener.verify(2, account.getId(), paymentId, null, TransactionStatus.SUCCESS);
    }

    @Test(groups = "slow")
    public void testPurchase() throws Exception {
        final String paymentExternalKey = UUID.randomUUID().toString();

        // PURCHASE
        final String purchaseKey = UUID.randomUUID().toString();
        final Payment purchase = paymentProcessor.createPurchase(true, null, account, null, null, TEN, CURRENCY, null,paymentExternalKey, purchaseKey,
                                                                 null, null, SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyPayment(purchase, paymentExternalKey, ZERO, ZERO, ZERO, 1);
        final UUID paymentId = purchase.getId();
        verifyPaymentTransaction(purchase.getTransactions().get(0), purchaseKey, TransactionType.PURCHASE, TEN, paymentId);
        paymentBusListener.verify(1, account.getId(), paymentId, TEN, TransactionStatus.SUCCESS);
    }

    @Test(groups = "slow")
    public void testCredit() throws Exception {
        final String paymentExternalKey = UUID.randomUUID().toString();

        // CREDIT
        final String creditKey = UUID.randomUUID().toString();
        final Payment purchase = paymentProcessor.createCredit(true, null, account, null, null, TEN, CURRENCY, null,paymentExternalKey, creditKey,
                                                               null, null, SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyPayment(purchase, paymentExternalKey, ZERO, ZERO, ZERO, 1);
        final UUID paymentId = purchase.getId();
        verifyPaymentTransaction(purchase.getTransactions().get(0), creditKey, TransactionType.CREDIT, TEN, paymentId);
        paymentBusListener.verify(1, account.getId(), paymentId, TEN, TransactionStatus.SUCCESS);
    }

    @Test(groups = "slow")
    public void testInvalidTransition() throws Exception {
        final String paymentExternalKey = UUID.randomUUID().toString();
        final Iterable<PluginProperty> pluginPropertiesToDriveTransationToPending = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.ERROR, false));

        // AUTH
        final String authorizationKey = UUID.randomUUID().toString();
        final Payment authorization = paymentProcessor.createAuthorization(true, null, account, null, null, TEN, CURRENCY, null,paymentExternalKey, authorizationKey,
                                                                           null, null, SHOULD_LOCK_ACCOUNT, pluginPropertiesToDriveTransationToPending, callContext, internalCallContext);
        verifyPayment(authorization, paymentExternalKey, ZERO, ZERO, ZERO, 1);
        final UUID paymentId = authorization.getId();
        verifyPaymentTransaction(authorization.getTransactions().get(0), authorizationKey, TransactionType.AUTHORIZE, TEN, paymentId);
        paymentBusListener.verify(0, 1, 0, account.getId(), paymentId, ZERO, TransactionStatus.PAYMENT_FAILURE);

        // REFUND
        final String refundKey = UUID.randomUUID().toString();
        try {
            paymentProcessor.createRefund(true, null, account, paymentId, TEN, CURRENCY, null,refundKey,
                                          null, SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
        }
        final Payment refreshedPayment = paymentRefresher.getPayment(authorization.getId(), false, false, PLUGIN_PROPERTIES, callContext, internalCallContext);
        // Make sure no state has been created (no UNKNOWN transaction for the refund)
        verifyPayment(refreshedPayment, paymentExternalKey, ZERO, ZERO, ZERO, 1);
        paymentBusListener.verify(0, 1, 0, account.getId(), paymentId, ZERO, TransactionStatus.PAYMENT_FAILURE);
    }

    @Test(groups = "slow")
    public void testNotifyPendingPaymentOfStateChanged() throws Exception {
        final String paymentExternalKey = UUID.randomUUID().toString();
        final Iterable<PluginProperty> pluginPropertiesToDriveTransationToPending = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.PENDING, false));

        // Create Pending AUTH
        final String authorizationKey = UUID.randomUUID().toString();
        final Payment authorization = paymentProcessor.createAuthorization(true, null, account, null, null, TEN, CURRENCY, null,paymentExternalKey, authorizationKey,
                                                                           null, null, SHOULD_LOCK_ACCOUNT, pluginPropertiesToDriveTransationToPending, callContext, internalCallContext);
        final PaymentTransaction pendingTransaction = authorization.getTransactions().get(0);
        Assert.assertEquals(pendingTransaction.getTransactionStatus(), TransactionStatus.PENDING);

        final UUID transactionId = pendingTransaction.getId();
        // Override plugin status of payment
        mockPaymentProviderPlugin.overridePaymentPluginStatus(authorization.getId(), transactionId, PaymentPluginStatus.PROCESSED);
        // Notify that state has changed, after changing the state in the plugin
        final Payment updatedPayment = paymentProcessor.notifyPendingPaymentOfStateChanged(account, transactionId, true, callContext, internalCallContext);
        verifyPayment(updatedPayment, paymentExternalKey, TEN, ZERO, ZERO, 1);

        final PaymentTransaction updatedTransaction = updatedPayment.getTransactions().get(0);
        Assert.assertEquals(updatedTransaction.getTransactionStatus(), TransactionStatus.SUCCESS);
    }

    private void verifyPayment(final Payment payment, final String paymentExternalKey,
                               final BigDecimal authAmount, final BigDecimal capturedAmount, final BigDecimal refundedAmount,
                               final int transactionsSize) {
        Assert.assertEquals(payment.getAccountId(), account.getId());
        // We cannot assume the number to be 1 here as the auto_increment implementation
        // depends on the database. On h2, it is implemented as a sequence, and the payment number
        // would be 33, 34, 35, etc. depending on the test
        // See also http://h2database.com/html/grammar.html#create_sequence
        Assert.assertTrue(payment.getPaymentNumber() > 0);
        Assert.assertEquals(payment.getExternalKey(), paymentExternalKey);
        Assert.assertEquals(payment.getAuthAmount().compareTo(authAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(capturedAmount), 0);
        Assert.assertEquals(payment.getRefundedAmount().compareTo(refundedAmount), 0);
        Assert.assertEquals(payment.getCurrency(), CURRENCY);
        Assert.assertEquals(payment.getTransactions().size(), transactionsSize);
    }

    private void verifyPaymentTransaction(final PaymentTransaction paymentTransaction, final String paymentTransactionExternalKey,
                                          final TransactionType transactionType, @Nullable final BigDecimal amount, final UUID paymentId) {
        Assert.assertEquals(paymentTransaction.getPaymentId(), paymentId);
        Assert.assertEquals(paymentTransaction.getExternalKey(), paymentTransactionExternalKey);
        Assert.assertEquals(paymentTransaction.getTransactionType(), transactionType);
        if (amount == null) {
            Assert.assertNull(paymentTransaction.getAmount());
            Assert.assertNull(paymentTransaction.getCurrency());
        } else {
            Assert.assertEquals(paymentTransaction.getAmount().compareTo(amount), 0);
            Assert.assertEquals(paymentTransaction.getCurrency(), CURRENCY);
        }
    }

    private static final class PaymentBusListener {

        private final List<PaymentInfoInternalEvent> paymentInfoEvents = new LinkedList<PaymentInfoInternalEvent>();
        private final List<PaymentInternalEvent> paymentErrorEvents = new LinkedList<PaymentInternalEvent>();
        private final List<PaymentInternalEvent> paymentPluginErrorEvents = new LinkedList<PaymentInternalEvent>();

        @Subscribe
        public void paymentInfo(final PaymentInfoInternalEvent event) {
            paymentInfoEvents.add(event);
        }

        @Subscribe
        public void paymentError(final PaymentErrorInternalEvent event) {
            paymentErrorEvents.add(event);
        }

        @Subscribe
        public void paymentPluginError(final PaymentPluginErrorInternalEvent event) {
            paymentPluginErrorEvents.add(event);
        }

        private void verify(final int eventNb, final UUID accountId, final UUID paymentId, final BigDecimal amount, final TransactionStatus transactionStatus) throws Exception {
            verify(eventNb, 0, 0, accountId, paymentId, amount, transactionStatus);
        }

        private void verify(final int nbInfoEvents, final int nbErrorEvents, final int nbPluginErrorEvents, final UUID accountId, final UUID paymentId, final BigDecimal amount, final TransactionStatus transactionStatus) throws Exception {
            Awaitility.await()
                      .until(new Callable<Boolean>() {
                          @Override
                          public Boolean call() throws Exception {
                              return paymentInfoEvents.size() == nbInfoEvents && paymentErrorEvents.size() == nbErrorEvents && paymentPluginErrorEvents.size() == nbPluginErrorEvents;
                          }
                      });

            if (transactionStatus == TransactionStatus.SUCCESS || transactionStatus == TransactionStatus.PENDING) {
                verify(paymentInfoEvents.get(paymentInfoEvents.size() - 1), accountId, paymentId, amount, transactionStatus);
            } else if (transactionStatus == TransactionStatus.PAYMENT_FAILURE) {
                verify(paymentErrorEvents.get(paymentErrorEvents.size() - 1), accountId, paymentId, amount, transactionStatus);
            } else {
                verify(paymentPluginErrorEvents.get(paymentPluginErrorEvents.size() - 1), accountId, paymentId, amount, transactionStatus);
            }
        }

        private void verify(final PaymentInternalEvent event, final UUID accountId, final UUID paymentId, @Nullable final BigDecimal amount, final TransactionStatus transactionStatus) {
            Assert.assertEquals(event.getPaymentId(), paymentId);
            Assert.assertEquals(event.getAccountId(), accountId);
            if (amount == null) {
                Assert.assertNull(event.getAmount());
            } else {
                Assert.assertEquals(event.getAmount().compareTo(amount), 0);
            }
            Assert.assertEquals(event.getStatus(), transactionStatus);
        }
    }
}
