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
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.DirectPaymentTransaction;
import org.killbill.billing.payment.api.PaymentStatus;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;
import com.jayway.awaitility.Awaitility;

import static java.math.BigDecimal.ZERO;

public class TestDirectPaymentProcessor extends PaymentTestSuiteWithEmbeddedDB {

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
        final String directPaymentExternalKey = UUID.randomUUID().toString();

        // AUTH pre-3DS
        final String authorizationKey = UUID.randomUUID().toString();
        final DirectPayment authorization = directPaymentProcessor.createAuthorization(account, null, null, TEN, CURRENCY, directPaymentExternalKey, authorizationKey,
                                                                                       SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyDirectPayment(authorization, directPaymentExternalKey, TEN, ZERO, ZERO, 1);
        final UUID directPaymentId = authorization.getId();
        verifyDirectPaymentTransaction(authorization.getTransactions().get(0), authorizationKey, TransactionType.AUTHORIZE, TEN, directPaymentId);
        paymentBusListener.verify(1, account.getId(), directPaymentId, TEN);

        // AUTH post-3DS
        final String authorizationPost3DSKey = UUID.randomUUID().toString();
        final DirectPayment authorizationPost3DS = directPaymentProcessor.createAuthorization(account, null, directPaymentId, TEN, CURRENCY, directPaymentExternalKey, authorizationPost3DSKey,
                                                                                              SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyDirectPayment(authorizationPost3DS, directPaymentExternalKey, TEN, ZERO, ZERO, 2);
        verifyDirectPaymentTransaction(authorizationPost3DS.getTransactions().get(1), authorizationPost3DSKey, TransactionType.AUTHORIZE, TEN, directPaymentId);
        paymentBusListener.verify(2, account.getId(), directPaymentId, TEN);

        // CAPTURE
        final String capture1Key = UUID.randomUUID().toString();
        final DirectPayment partialCapture1 = directPaymentProcessor.createCapture(account, directPaymentId, FIVE, CURRENCY, capture1Key,
                                                                                   SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyDirectPayment(partialCapture1, directPaymentExternalKey, TEN, FIVE, ZERO, 3);
        verifyDirectPaymentTransaction(partialCapture1.getTransactions().get(2), capture1Key, TransactionType.CAPTURE, FIVE, directPaymentId);
        paymentBusListener.verify(3, account.getId(), directPaymentId, FIVE);

        // CAPTURE
        final String capture2Key = UUID.randomUUID().toString();
        final DirectPayment partialCapture2 = directPaymentProcessor.createCapture(account, directPaymentId, FIVE, CURRENCY, capture2Key,
                                                                                   SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyDirectPayment(partialCapture2, directPaymentExternalKey, TEN, TEN, ZERO, 4);
        verifyDirectPaymentTransaction(partialCapture2.getTransactions().get(3), capture2Key, TransactionType.CAPTURE, FIVE, directPaymentId);
        paymentBusListener.verify(4, account.getId(), directPaymentId, FIVE);

        // REFUND
        final String refund1Key = UUID.randomUUID().toString();
        final DirectPayment partialRefund1 = directPaymentProcessor.createRefund(account, directPaymentId, FIVE, CURRENCY, refund1Key,
                                                                                 SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyDirectPayment(partialRefund1, directPaymentExternalKey, TEN, TEN, FIVE, 5);
        verifyDirectPaymentTransaction(partialRefund1.getTransactions().get(4), refund1Key, TransactionType.REFUND, FIVE, directPaymentId);
        paymentBusListener.verify(5, account.getId(), directPaymentId, FIVE);

        // REFUND
        final String refund2Key = UUID.randomUUID().toString();
        final DirectPayment partialRefund2 = directPaymentProcessor.createRefund(account, directPaymentId, FIVE, CURRENCY, refund2Key,
                                                                                 SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyDirectPayment(partialRefund2, directPaymentExternalKey, TEN, TEN, TEN, 6);
        verifyDirectPaymentTransaction(partialRefund2.getTransactions().get(5), refund2Key, TransactionType.REFUND, FIVE, directPaymentId);
        paymentBusListener.verify(6, account.getId(), directPaymentId, FIVE);
    }

    @Test(groups = "slow")
    public void testVoid() throws Exception {
        final String directPaymentExternalKey = UUID.randomUUID().toString();

        // AUTH
        final String authorizationKey = UUID.randomUUID().toString();
        final DirectPayment authorization = directPaymentProcessor.createAuthorization(account, null, null, TEN, CURRENCY, directPaymentExternalKey, authorizationKey,
                                                                                       SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyDirectPayment(authorization, directPaymentExternalKey, TEN, ZERO, ZERO, 1);
        final UUID directPaymentId = authorization.getId();
        verifyDirectPaymentTransaction(authorization.getTransactions().get(0), authorizationKey, TransactionType.AUTHORIZE, TEN, directPaymentId);
        paymentBusListener.verify(1, account.getId(), directPaymentId, TEN);

        // VOID
        final String voidKey = UUID.randomUUID().toString();
        final DirectPayment voidTransaction = directPaymentProcessor.createVoid(account, directPaymentId, voidKey,
                                                                                SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyDirectPayment(voidTransaction, directPaymentExternalKey, TEN, ZERO, ZERO, 2);
        verifyDirectPaymentTransaction(voidTransaction.getTransactions().get(1), voidKey, TransactionType.VOID, null, directPaymentId);
        paymentBusListener.verify(2, account.getId(), directPaymentId, null);
    }

    @Test(groups = "slow")
    public void testPurchase() throws Exception {
        final String directPaymentExternalKey = UUID.randomUUID().toString();

        // PURCHASE
        final String purchaseKey = UUID.randomUUID().toString();
        final DirectPayment purchase = directPaymentProcessor.createPurchase(account, null, null, TEN, CURRENCY, directPaymentExternalKey, purchaseKey,
                                                                             SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyDirectPayment(purchase, directPaymentExternalKey, ZERO, ZERO, ZERO, 1);
        final UUID directPaymentId = purchase.getId();
        verifyDirectPaymentTransaction(purchase.getTransactions().get(0), purchaseKey, TransactionType.PURCHASE, TEN, directPaymentId);
        paymentBusListener.verify(1, account.getId(), directPaymentId, TEN);
    }

    @Test(groups = "slow")
    public void testCredit() throws Exception {
        final String directPaymentExternalKey = UUID.randomUUID().toString();

        // CREDIT
        final String creditKey = UUID.randomUUID().toString();
        final DirectPayment purchase = directPaymentProcessor.createCredit(account, null, null, TEN, CURRENCY, directPaymentExternalKey, creditKey,
                                                                           SHOULD_LOCK_ACCOUNT, PLUGIN_PROPERTIES, callContext, internalCallContext);
        verifyDirectPayment(purchase, directPaymentExternalKey, ZERO, ZERO, ZERO, 1);
        final UUID directPaymentId = purchase.getId();
        verifyDirectPaymentTransaction(purchase.getTransactions().get(0), creditKey, TransactionType.CREDIT, TEN, directPaymentId);
        paymentBusListener.verify(1, account.getId(), directPaymentId, TEN);
    }

    private void verifyDirectPayment(final DirectPayment directPayment, final String directPaymentExternalKey,
                                     final BigDecimal authAmount, final BigDecimal capturedAmount, final BigDecimal refundedAmount,
                                     final int transactionsSize) {
        Assert.assertEquals(directPayment.getAccountId(), account.getId());
        Assert.assertEquals(directPayment.getPaymentNumber(), new Integer(1));
        Assert.assertEquals(directPayment.getExternalKey(), directPaymentExternalKey);
        Assert.assertEquals(directPayment.getAuthAmount().compareTo(authAmount), 0);
        Assert.assertEquals(directPayment.getCapturedAmount().compareTo(capturedAmount), 0);
        Assert.assertEquals(directPayment.getRefundedAmount().compareTo(refundedAmount), 0);
        Assert.assertEquals(directPayment.getCurrency(), CURRENCY);
        Assert.assertEquals(directPayment.getTransactions().size(), transactionsSize);
    }

    private void verifyDirectPaymentTransaction(final DirectPaymentTransaction directPaymentTransaction, final String directPaymentTransactionExternalKey,
                                                final TransactionType transactionType, @Nullable final BigDecimal amount, final UUID directPaymentId) {
        Assert.assertEquals(directPaymentTransaction.getDirectPaymentId(), directPaymentId);
        Assert.assertEquals(directPaymentTransaction.getExternalKey(), directPaymentTransactionExternalKey);
        Assert.assertEquals(directPaymentTransaction.getTransactionType(), transactionType);
        if (amount == null) {
            Assert.assertNull(directPaymentTransaction.getAmount());
            Assert.assertNull(directPaymentTransaction.getCurrency());
        } else {
            Assert.assertEquals(directPaymentTransaction.getAmount().compareTo(amount), 0);
            Assert.assertEquals(directPaymentTransaction.getCurrency(), CURRENCY);
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
            Assert.assertNull(event.getInvoiceId());
            if (amount == null) {
                Assert.assertNull(event.getAmount());
            } else {
                Assert.assertEquals(event.getAmount().compareTo(amount), 0);
            }
            Assert.assertEquals(event.getStatus(), PaymentStatus.SUCCESS);
        }
    }
}
