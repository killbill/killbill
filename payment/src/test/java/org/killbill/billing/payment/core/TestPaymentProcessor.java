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

package org.killbill.billing.payment.core;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.events.BusInternalEvent;
import org.killbill.billing.events.PaymentErrorInternalEvent;
import org.killbill.billing.events.PaymentInfoInternalEvent;
import org.killbill.billing.events.PaymentPluginErrorInternalEvent;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import com.jayway.awaitility.Awaitility;

import static java.math.BigDecimal.ZERO;

public class TestPaymentProcessor extends PaymentTestSuiteWithEmbeddedDB {

    private static final boolean SHOULD_LOCK_ACCOUNT = true;
    private static final ImmutableList<PluginProperty> PLUGIN_PROPERTIES = ImmutableList.<PluginProperty>of();
    private static final BigDecimal FIVE = new BigDecimal("5");
    private static final BigDecimal TEN = new BigDecimal("10");
    private static final Currency CURRENCY = Currency.BTC;

    private PaymentBusListener paymentBusListener;
    private Account account;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        account = testHelper.createTestAccount(UUID.randomUUID().toString(), true);
        internalCallContext = new InternalCallContext(internalCallContext, 1L);

        paymentBusListener = new PaymentBusListener();
        eventBus.register(paymentBusListener);
    }

    @Test(groups = "slow")
    public void testClassicFlow() throws Exception {
        final String paymentExternalKey = UUID.randomUUID().toString();

        // AUTH pre-3DS
        final String authorizationKey = UUID.randomUUID().toString();
        final Payment authorization = paymentProcessor.createAuthorization(true, null, account, null, null, TEN, CURRENCY, paymentExternalKey, authorizationKey,
                                                                                       SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyPayment(authorization, paymentExternalKey, TEN, ZERO, ZERO, 1);
        final UUID paymentId = authorization.getId();
        verifyPaymentTransaction(authorization.getTransactions().get(0), authorizationKey, TransactionType.AUTHORIZE, TEN, paymentId);
        paymentBusListener.verify(1, account.getId(), paymentId, TEN);

        // AUTH post-3DS
        final String authorizationPost3DSKey = UUID.randomUUID().toString();
        final Payment authorizationPost3DS = paymentProcessor.createAuthorization(true, null, account, null, paymentId, TEN, CURRENCY, paymentExternalKey, authorizationPost3DSKey,
                                                                                              SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyPayment(authorizationPost3DS, paymentExternalKey, TEN, ZERO, ZERO, 2);
        verifyPaymentTransaction(authorizationPost3DS.getTransactions().get(1), authorizationPost3DSKey, TransactionType.AUTHORIZE, TEN, paymentId);
        paymentBusListener.verify(2, account.getId(), paymentId, TEN.add(TEN) /* STEPH the processedAmount is 20; probably something wrong in the MockPlugin */);

        // CAPTURE
        final String capture1Key = UUID.randomUUID().toString();
        final Payment partialCapture1 = paymentProcessor.createCapture(true, null, account, paymentId, FIVE, CURRENCY, capture1Key,
                                                                                   SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyPayment(partialCapture1, paymentExternalKey, TEN, FIVE, ZERO, 3);
        verifyPaymentTransaction(partialCapture1.getTransactions().get(2), capture1Key, TransactionType.CAPTURE, FIVE, paymentId);
        paymentBusListener.verify(3, account.getId(), paymentId, FIVE);

        // CAPTURE
        final String capture2Key = UUID.randomUUID().toString();
        final Payment partialCapture2 = paymentProcessor.createCapture(true, null, account, paymentId, FIVE, CURRENCY, capture2Key,
                                                                                   SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyPayment(partialCapture2, paymentExternalKey, TEN, TEN, ZERO, 4);
        verifyPaymentTransaction(partialCapture2.getTransactions().get(3), capture2Key, TransactionType.CAPTURE, FIVE, paymentId);
        paymentBusListener.verify(4, account.getId(), paymentId, FIVE.add(FIVE) /* STEPH the processedAmount is 20; probably something wrong in the MockPlugin */);

        // REFUND
        final String refund1Key = UUID.randomUUID().toString();
        final Payment partialRefund1 = paymentProcessor.createRefund(true, null, account, paymentId, FIVE, CURRENCY, refund1Key,
                                                                                 SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyPayment(partialRefund1, paymentExternalKey, TEN, TEN, FIVE, 5);
        verifyPaymentTransaction(partialRefund1.getTransactions().get(4), refund1Key, TransactionType.REFUND, FIVE, paymentId);
        paymentBusListener.verify(5, account.getId(), paymentId, FIVE);

        // REFUND
        final String refund2Key = UUID.randomUUID().toString();
        final Payment partialRefund2 = paymentProcessor.createRefund(true, null, account, paymentId, FIVE, CURRENCY, refund2Key,
                                                                                 SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyPayment(partialRefund2, paymentExternalKey, TEN, TEN, TEN, 6);
        verifyPaymentTransaction(partialRefund2.getTransactions().get(5), refund2Key, TransactionType.REFUND, FIVE, paymentId);
        paymentBusListener.verify(6, account.getId(), paymentId, FIVE.add(FIVE) /* STEPH the processedAmount is 20; probably something wrong in the MockPlugin */);
    }

    @Test(groups = "slow")
    public void testVoid() throws Exception {
        final String paymentExternalKey = UUID.randomUUID().toString();

        // AUTH
        final String authorizationKey = UUID.randomUUID().toString();
        final Payment authorization = paymentProcessor.createAuthorization(true, null, account, null, null, TEN, CURRENCY, paymentExternalKey, authorizationKey,
                                                                                       SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyPayment(authorization, paymentExternalKey, TEN, ZERO, ZERO, 1);
        final UUID paymentId = authorization.getId();
        verifyPaymentTransaction(authorization.getTransactions().get(0), authorizationKey, TransactionType.AUTHORIZE, TEN, paymentId);
        paymentBusListener.verify(1, account.getId(), paymentId, TEN);

        // VOID
        final String voidKey = UUID.randomUUID().toString();
        final Payment voidTransaction = paymentProcessor.createVoid(true, null, account, paymentId, voidKey,
                                                                                SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyPayment(voidTransaction, paymentExternalKey, TEN, ZERO, ZERO, 2);
        verifyPaymentTransaction(voidTransaction.getTransactions().get(1), voidKey, TransactionType.VOID, null, paymentId);
        paymentBusListener.verify(2, account.getId(), paymentId, null);
    }

    @Test(groups = "slow")
    public void testPurchase() throws Exception {
        final String paymentExternalKey = UUID.randomUUID().toString();

        // PURCHASE
        final String purchaseKey = UUID.randomUUID().toString();
        final Payment purchase = paymentProcessor.createPurchase(true, null, account, null, null, TEN, CURRENCY, paymentExternalKey, purchaseKey,
                                                                             SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyPayment(purchase, paymentExternalKey, ZERO, ZERO, ZERO, 1);
        final UUID paymentId = purchase.getId();
        verifyPaymentTransaction(purchase.getTransactions().get(0), purchaseKey, TransactionType.PURCHASE, TEN, paymentId);
        paymentBusListener.verify(1, account.getId(), paymentId, TEN);
    }

    @Test(groups = "slow")
    public void testCredit() throws Exception {
        final String paymentExternalKey = UUID.randomUUID().toString();

        // CREDIT
        final String creditKey = UUID.randomUUID().toString();
        final Payment purchase = paymentProcessor.createCredit(true, null, account, null, null, TEN, CURRENCY, paymentExternalKey, creditKey,
                                                                           SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyPayment(purchase, paymentExternalKey, ZERO, ZERO, ZERO, 1);
        final UUID paymentId = purchase.getId();
        verifyPaymentTransaction(purchase.getTransactions().get(0), creditKey, TransactionType.CREDIT, TEN, paymentId);
        paymentBusListener.verify(1, account.getId(), paymentId, TEN);
    }

    private void verifyPayment(final Payment payment, final String paymentExternalKey,
                                     final BigDecimal authAmount, final BigDecimal capturedAmount, final BigDecimal refundedAmount,
                                     final int transactionsSize) {
        Assert.assertEquals(payment.getAccountId(), account.getId());
        Assert.assertEquals(payment.getPaymentNumber(), new Integer(1));
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
        private final Collection<BusInternalEvent> paymentErrorEvents = new LinkedList<BusInternalEvent>();
        private final Collection<BusInternalEvent> paymentPluginErrorEvents = new LinkedList<BusInternalEvent>();

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

        public void verify(final int eventNb, final UUID accountId, final UUID paymentId, final BigDecimal amount) throws Exception {
            Awaitility.await()
                      .until(new Callable<Boolean>() {
                          @Override
                          public Boolean call() throws Exception {
                              return paymentInfoEvents.size() == eventNb;
                          }
                      });
            Assert.assertEquals(paymentErrorEvents.size(), 0);
            Assert.assertEquals(paymentPluginErrorEvents.size(), 0);

            verify(paymentInfoEvents.get(eventNb - 1), accountId, paymentId, amount);
        }

        private void verify(final PaymentInfoInternalEvent event, final UUID accountId, final UUID paymentId, @Nullable final BigDecimal amount) {
            Assert.assertEquals(event.getPaymentId(), paymentId);
            Assert.assertEquals(event.getAccountId(), accountId);
            if (amount == null) {
                Assert.assertEquals(event.getAmount().compareTo(BigDecimal.ZERO), 0);
            } else {
                Assert.assertEquals(event.getAmount().compareTo(amount), 0);
            }
            Assert.assertEquals(event.getStatus(), TransactionStatus.SUCCESS);
        }
    }
}
