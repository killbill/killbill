/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.payment.core.janitor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.payment.provider.DefaultNoOpPaymentInfoPlugin;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.commons.utils.collect.Iterables;
import org.killbill.billing.util.globallocker.LockerType;
import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.LockFailedException;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestIncompletePaymentTransactionTaskWithDB extends PaymentTestSuiteWithEmbeddedDB {

    private MockPaymentProviderPlugin mockPaymentProviderPlugin;
    private Account account;

    @BeforeClass(groups = "slow")
    protected void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }
        super.beforeClass();

        mockPaymentProviderPlugin = (MockPaymentProviderPlugin) registry.getServiceForName(MockPaymentProviderPlugin.PLUGIN_NAME);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        mockPaymentProviderPlugin.clear();
        account = testHelper.createTestAccount(UUID.randomUUID().toString(), true);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/675")
    public void testHandleLockExceptions() throws PaymentApiException {
        final Payment payment = paymentApi.createPurchase(account,
                                                          account.getPaymentMethodId(),
                                                          null,
                                                          BigDecimal.TEN,
                                                          Currency.EUR,
                                                          null,
                                                          UUID.randomUUID().toString(),
                                                          UUID.randomUUID().toString(),
                                                          List.of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.PENDING.toString(), false)),
                                                          callContext);

        final UUID transactionId = payment.getTransactions().get(0).getId();
        final JanitorNotificationKey notificationKey = new JanitorNotificationKey(transactionId, incompletePaymentTransactionTask.getClass().toString(), true, 1);
        final UUID userToken = UUID.randomUUID();

        Assert.assertTrue(Iterables.isEmpty(incompletePaymentAttemptTask.janitorQueue.getFutureNotificationForSearchKeys(internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId())));

        GlobalLock lock = null;
        try {
            lock = locker.lockWithNumberOfTries(LockerType.ACCNT_INV_PAY.toString(), account.getId().toString(), paymentConfig.getMaxGlobalLockRetries());

            incompletePaymentAttemptTask.processNotification(notificationKey, userToken, internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId());

            final Iterable<NotificationEventWithMetadata<NotificationEvent>> futureNotifications = incompletePaymentAttemptTask.janitorQueue.getFutureNotificationForSearchKeys(internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId());
            Assert.assertFalse(Iterables.isEmpty(futureNotifications));
            final NotificationEventWithMetadata<NotificationEvent> notificationEventWithMetadata = Iterables.getFirst(futureNotifications, null);
            Assert.assertEquals(notificationEventWithMetadata.getUserToken(), userToken);
            Assert.assertEquals(notificationEventWithMetadata.getEvent().getClass(), JanitorNotificationKey.class);
            final JanitorNotificationKey event = (JanitorNotificationKey) notificationEventWithMetadata.getEvent();
            Assert.assertEquals(event.getUuidKey(), transactionId);
            Assert.assertEquals((int) event.getAttemptNumber(), 2);

            // Based on config "1h, 1d"
            Assert.assertTrue(notificationEventWithMetadata.getEffectiveDate().compareTo(clock.getUTCNow().plusDays(1).plusSeconds(5)) < 0);
        } catch (final LockFailedException e) {
            Assert.fail();
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/809")
    public void testUpdateWithinLock() throws PaymentApiException {
        final Payment payment = paymentApi.createAuthorization(account,
                                                          account.getPaymentMethodId(),
                                                          null,
                                                          BigDecimal.TEN,
                                                          Currency.EUR,
                                                          null,
                                                          UUID.randomUUID().toString(),
                                                          UUID.randomUUID().toString(),
                                                          List.of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.UNDEFINED.toString(), false)),
                                                          callContext);
        final PaymentModelDao paymentModel = paymentDao.getPayment(payment.getId(), internalCallContext);
        final UUID transactionId = payment.getTransactions().get(0).getId();
        final PaymentTransactionModelDao transactionModel = paymentDao.getPaymentTransaction(transactionId, internalCallContext);

        Assert.assertEquals(paymentModel.getStateName(), "AUTH_ERRORED");
        Assert.assertEquals(transactionModel.getTransactionStatus().toString(), "UNKNOWN");

        paymentDao.updatePaymentAndTransactionOnCompletion(
                account.getId(),
                null,
                payment.getId(),
                TransactionType.AUTHORIZE,
                "AUTH_SUCCESS",
                "AUTH_SUCCESS",
                transactionId,
                TransactionStatus.SUCCESS,
                BigDecimal.TEN,
                Currency.EUR,
                "200",
                "Ok",
                true,
                internalCallContext);

        paymentApi.createCapture(account,
                                 payment.getId(),
                                 BigDecimal.TEN,
                                 Currency.EUR,
                                 null,
                                 UUID.randomUUID().toString(),
                                 List.of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.PROCESSED.toString(), false)),
                                 callContext);

        final PaymentModelDao paymentAfterCapture = paymentDao.getPayment(payment.getId(), internalCallContext);
        Assert.assertEquals(paymentAfterCapture.getStateName(), "CAPTURE_SUCCESS");

        PaymentTransactionInfoPlugin paymentTransactionInfoPlugin = new DefaultNoOpPaymentInfoPlugin(
                payment.getId(),
                transactionId,
                TransactionType.AUTHORIZE,
                BigDecimal.TEN,
                Currency.EUR,
                transactionModel.getEffectiveDate(),
                transactionModel.getCreatedDate(),
                PaymentPluginStatus.PROCESSED,
                "200",
                "OK");
        incompletePaymentTransactionTask.updatePaymentAndTransactionIfNeeded(paymentModel.getAccountId(),
                                                                             transactionModel.getId(),
                                                                             payment.getTransactions().get(0).getTransactionStatus(),
                                                                             paymentTransactionInfoPlugin,
                                                                             true,
                                                                             internalCallContext);

        final PaymentModelDao paymentAfterJanitor = paymentDao.getPayment(payment.getId(), internalCallContext);
        Assert.assertEquals(paymentAfterJanitor.getStateName(), "CAPTURE_SUCCESS");
    }
}
