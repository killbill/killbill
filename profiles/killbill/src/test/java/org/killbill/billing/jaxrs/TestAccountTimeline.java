/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.jaxrs;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.AccountTimeline;
import org.killbill.billing.client.model.gen.AuditLog;
import org.killbill.billing.client.model.gen.Credit;
import org.killbill.billing.client.model.gen.EventSubscription;
import org.killbill.billing.client.model.gen.Invoice;
import org.killbill.billing.client.model.gen.InvoicePayment;
import org.killbill.billing.client.model.gen.InvoicePaymentTransaction;
import org.killbill.billing.client.model.gen.Payment;
import org.killbill.billing.client.model.gen.PaymentTransaction;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.ChangeType;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestAccountTimeline extends TestJaxrsBase {

    private static final String PAYMENT_REQUEST_PROCESSOR = "PaymentRequestProcessor";
    private static final String TRANSITION = "SubscriptionBaseTransition";

    @Test(groups = "slow", description = "Can retrieve the timeline without audits")
    public void testAccountTimeline() throws Exception {
        clock.setTime(new DateTime(2012, 4, 25, 0, 3, 42, 0));

        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        final AccountTimeline timeline = getAccountTimeline(accountJson.getAccountId(), AuditLevel.NONE);
        Assert.assertEquals(timeline.getPayments().size(), 1);
        Assert.assertEquals(timeline.getInvoices().size(), 2);
        Assert.assertEquals(timeline.getBundles().size(), 1);
        Assert.assertEquals(timeline.getBundles().get(0).getSubscriptions().size(), 1);
        Assert.assertEquals(timeline.getBundles().get(0).getSubscriptions().get(0).getEvents().size(), 3);
        Assert.assertNotNull(timeline.getInvoices().get(0).getBundleKeys());

        final List<EventSubscription> events = timeline.getBundles().get(0).getSubscriptions().get(0).getEvents();
        Assert.assertEquals(events.get(0).getEffectiveDate(), new LocalDate(2012, 4, 25));
        Assert.assertEquals(events.get(0).getEventType(), SubscriptionEventType.START_ENTITLEMENT);
        Assert.assertEquals(events.get(1).getEffectiveDate(), new LocalDate(2012, 4, 25));
        Assert.assertEquals(events.get(1).getEventType(), SubscriptionEventType.START_BILLING);
        Assert.assertEquals(events.get(2).getEffectiveDate(), new LocalDate(2012, 5, 25));
        Assert.assertEquals(events.get(2).getEventType(), SubscriptionEventType.PHASE);
    }

    @Test(groups = "slow", description = "Can retrieve the timeline with audits")
    public void testAccountTimelineWithAudits() throws Exception {
        final DateTime startTime = clock.getUTCNow();
        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();
        final DateTime endTime = clock.getUTCNow();

        // Add credit
        final Invoice invoice = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, requestOptions).get(1);
        final BigDecimal creditAmount = BigDecimal.ONE;
        final Credit credit = new Credit();
        credit.setAccountId(accountJson.getAccountId());
        credit.setCreditAmount(creditAmount);
        creditApi.createCredit(credit, true, NULL_PLUGIN_PROPERTIES, requestOptions);

        // Add refund
        final Payment postedPayment = accountApi.getPaymentsForAccount(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions).get(0);
        final BigDecimal refundAmount = BigDecimal.ONE;
        final InvoicePaymentTransaction refund = new InvoicePaymentTransaction();
        refund.setPaymentId(postedPayment.getPaymentId());
        refund.setAmount(refundAmount);
        invoicePaymentApi.createRefundWithAdjustments(postedPayment.getPaymentId(), refund, accountJson.getPaymentMethodId(), NULL_PLUGIN_PROPERTIES, requestOptions);

        // Add chargeback
        final BigDecimal chargebackAmount = BigDecimal.ONE;
        final InvoicePaymentTransaction chargeback = new InvoicePaymentTransaction();
        chargeback.setPaymentId(postedPayment.getPaymentId());
        chargeback.setAmount(chargebackAmount);
        invoicePaymentApi.createChargeback(postedPayment.getPaymentId(), chargeback, requestOptions);

        // Verify payments
        verifyPayments(accountJson.getAccountId(), startTime, endTime, refundAmount, chargebackAmount);

        // Verify invoices
        verifyInvoices(accountJson.getAccountId(), startTime, endTime);

        // Verify credits
        verifyCredits(accountJson.getAccountId(), startTime, endTime, creditAmount);

        // Verify bundles
        verifyBundles(accountJson.getAccountId(), startTime, endTime);
    }

    private void verifyPayments(final UUID accountId, final DateTime startTime, final DateTime endTime,
                                final BigDecimal refundAmount, final BigDecimal chargebackAmount) throws Exception {
        for (final AuditLevel auditLevel : AuditLevel.values()) {
            final AccountTimeline timeline = getAccountTimeline(accountId, auditLevel);

            Assert.assertEquals(timeline.getPayments().size(), 1);
            final InvoicePayment payment = timeline.getPayments().get(0);

            // Verify payments
            final List<PaymentTransaction> purchaseTransactions = getInvoicePaymentTransactions(timeline.getPayments(), TransactionType.PURCHASE);
            Assert.assertEquals(purchaseTransactions.size(), 1);
            final PaymentTransaction purchaseTransaction = purchaseTransactions.get(0);

            // Verify refunds
            final List<PaymentTransaction> refundTransactions = getInvoicePaymentTransactions(timeline.getPayments(), TransactionType.REFUND);
            Assert.assertEquals(refundTransactions.size(), 1);
            final PaymentTransaction refundTransaction = refundTransactions.get(0);
            Assert.assertEquals(refundTransaction.getPaymentId(), payment.getPaymentId());
            Assert.assertEquals(refundTransaction.getAmount().compareTo(refundAmount), 0);

            final List<PaymentTransaction> chargebackTransactions = getInvoicePaymentTransactions(timeline.getPayments(), TransactionType.CHARGEBACK);
            Assert.assertEquals(chargebackTransactions.size(), 1);
            final PaymentTransaction chargebackTransaction = chargebackTransactions.get(0);
            Assert.assertEquals(chargebackTransaction.getPaymentId(), payment.getPaymentId());
            Assert.assertEquals(chargebackTransaction.getAmount().compareTo(chargebackAmount), 0);

            // Verify audits
            final List<AuditLog> paymentAuditLogs = purchaseTransaction.getAuditLogs();
            final List<AuditLog> refundAuditLogs = refundTransaction.getAuditLogs();
            final List<AuditLog> chargebackAuditLogs = chargebackTransaction.getAuditLogs();

            if (AuditLevel.NONE.equals(auditLevel)) {
                // Audits for payments
                Assert.assertEquals(paymentAuditLogs.size(), 0);

                // Audits for refunds
                Assert.assertEquals(refundAuditLogs.size(), 0);

                // Audits for chargebacks
                Assert.assertEquals(chargebackAuditLogs.size(), 0);
            } else if (AuditLevel.MINIMAL.equals(auditLevel)) {
                // Audits for payments
                Assert.assertEquals(paymentAuditLogs.size(), 1);
                verifyAuditLog(paymentAuditLogs.get(0), ChangeType.INSERT, null, null, PAYMENT_REQUEST_PROCESSOR, startTime, endTime);

                // Audits for refunds
                Assert.assertEquals(refundAuditLogs.size(), 1);
                verifyAuditLog(refundAuditLogs.get(0), ChangeType.INSERT, reason, comment, createdBy, startTime, endTime);

                // Audits for chargebacks
                Assert.assertEquals(chargebackAuditLogs.size(), 1);
                verifyAuditLog(chargebackAuditLogs.get(0), ChangeType.INSERT, reason, comment, createdBy, startTime, endTime);
            } else {
                // Audits for payments
                Assert.assertEquals(paymentAuditLogs.size(), 2);
                verifyAuditLog(paymentAuditLogs.get(0), ChangeType.INSERT, null, null, PAYMENT_REQUEST_PROCESSOR, startTime, endTime);
                verifyAuditLog(paymentAuditLogs.get(1), ChangeType.UPDATE, null, null, PAYMENT_REQUEST_PROCESSOR, startTime, endTime);

                // Audits for refunds
                Assert.assertEquals(refundAuditLogs.size(), 2);
                verifyAuditLog(refundAuditLogs.get(0), ChangeType.INSERT, reason, comment, createdBy, startTime, endTime);
                verifyAuditLog(refundAuditLogs.get(1), ChangeType.UPDATE, reason, comment, createdBy, startTime, endTime);

                // Audits for chargebacks
                Assert.assertEquals(chargebackAuditLogs.size(), 2);
                verifyAuditLog(chargebackAuditLogs.get(0), ChangeType.INSERT, reason, comment, createdBy, startTime, endTime);
                verifyAuditLog(chargebackAuditLogs.get(1), ChangeType.UPDATE, reason, comment, createdBy, startTime, endTime);
            }
        }
    }

    private void verifyInvoices(final UUID accountId, final DateTime startTime, final DateTime endTime) throws Exception {
        for (final AuditLevel auditLevel : AuditLevel.values()) {
            final AccountTimeline timeline = getAccountTimeline(accountId, auditLevel);

            // Verify invoices
            Assert.assertEquals(timeline.getInvoices().size(), 3);

            // Verify audits
            final List<AuditLog> firstInvoiceAuditLogs = timeline.getInvoices().get(0).getAuditLogs();
            final List<AuditLog> secondInvoiceAuditLogs = timeline.getInvoices().get(1).getAuditLogs();
            final List<AuditLog> thirdInvoiceAuditLogs = timeline.getInvoices().get(2).getAuditLogs();
            if (AuditLevel.NONE.equals(auditLevel)) {
                Assert.assertEquals(firstInvoiceAuditLogs.size(), 0);
                Assert.assertEquals(secondInvoiceAuditLogs.size(), 0);
                Assert.assertEquals(thirdInvoiceAuditLogs.size(), 0);
            } else {
                Assert.assertEquals(firstInvoiceAuditLogs.size(), 1);
                verifyAuditLog(firstInvoiceAuditLogs.get(0), ChangeType.INSERT, null, null, TRANSITION, startTime, endTime);
                Assert.assertEquals(secondInvoiceAuditLogs.size(), 1);
                verifyAuditLog(secondInvoiceAuditLogs.get(0), ChangeType.INSERT, null, null, TRANSITION, startTime, endTime);
                Assert.assertEquals(thirdInvoiceAuditLogs.size(), 1);
                verifyAuditLog(thirdInvoiceAuditLogs.get(0), ChangeType.INSERT, reason, comment, createdBy, startTime, endTime);
            }
        }
    }

    private void verifyCredits(final UUID accountId, final DateTime startTime, final DateTime endTime, final BigDecimal creditAmount) throws Exception {
        for (final AuditLevel auditLevel : AuditLevel.values()) {
            final AccountTimeline timeline = getAccountTimeline(accountId, auditLevel);

            // Verify credits
            final List<Credit> credits = timeline.getInvoices().get(1).getCredits();
            Assert.assertEquals(credits.size(), 1);
            Assert.assertEquals(credits.get(0).getCreditAmount().compareTo(creditAmount.negate()), 0);

            // Verify audits
            final List<AuditLog> creditAuditLogs = credits.get(0).getAuditLogs();
            if (AuditLevel.NONE.equals(auditLevel)) {
                Assert.assertEquals(creditAuditLogs.size(), 0);
            } else {
                Assert.assertEquals(creditAuditLogs.size(), 1);
                verifyAuditLog(creditAuditLogs.get(0), ChangeType.INSERT, reason, comment, createdBy, startTime, endTime);
            }
        }
    }

    private void verifyBundles(final UUID accountId, final DateTime startTime, final DateTime endTime) throws Exception {
        for (final AuditLevel auditLevel : AuditLevel.values()) {
            final AccountTimeline timeline = getAccountTimeline(accountId, auditLevel);

            // Verify bundles
            Assert.assertEquals(timeline.getBundles().size(), 1);
            Assert.assertEquals(timeline.getBundles().get(0).getSubscriptions().size(), 1);
            Assert.assertEquals(timeline.getBundles().get(0).getSubscriptions().get(0).getEvents().size(), 3);

            // Verify audits
            final List<AuditLog> bundleAuditLogs = timeline.getBundles().get(0).getAuditLogs();
            final List<AuditLog> subscriptionAuditLogs = timeline.getBundles().get(0).getSubscriptions().get(0).getAuditLogs();
            final List<AuditLog> subscriptionEvent1AuditLogs = timeline.getBundles().get(0).getSubscriptions().get(0).getEvents().get(0).getAuditLogs();
            final List<AuditLog> subscriptionEvent2AuditLogs = timeline.getBundles().get(0).getSubscriptions().get(0).getEvents().get(1).getAuditLogs();
            if (AuditLevel.NONE.equals(auditLevel)) {
                // Audits for bundles
                Assert.assertEquals(bundleAuditLogs.size(), 0);

                // Audits for subscriptions
                Assert.assertEquals(subscriptionAuditLogs.size(), 0);

                // Audit for subscription events
                Assert.assertEquals(subscriptionEvent1AuditLogs.size(), 0);
                Assert.assertEquals(subscriptionEvent2AuditLogs.size(), 0);
            } else if (AuditLevel.MINIMAL.equals(auditLevel)) {
                // Audits for bundles
                Assert.assertEquals(bundleAuditLogs.size(), 1);
                verifyAuditLog(bundleAuditLogs.get(0), ChangeType.INSERT, reason, comment, createdBy, startTime, endTime);

                // Audits for subscriptions
                Assert.assertEquals(subscriptionAuditLogs.size(), 1);
                verifyAuditLog(subscriptionAuditLogs.get(0), ChangeType.INSERT, reason, comment, createdBy, startTime, endTime);

                // Audit for subscription events
                Assert.assertEquals(subscriptionEvent1AuditLogs.size(), 1);
                verifyAuditLog(subscriptionEvent1AuditLogs.get(0), ChangeType.INSERT, reason, comment, createdBy, startTime, endTime);
                Assert.assertEquals(subscriptionEvent2AuditLogs.size(), 1);
                verifyAuditLog(subscriptionEvent2AuditLogs.get(0), ChangeType.INSERT, reason, comment, createdBy, startTime, endTime);
            } else {
                // Audits for bundles
                Assert.assertEquals(bundleAuditLogs.size(), 3);
                verifyAuditLog(bundleAuditLogs.get(0), ChangeType.INSERT, reason, comment, createdBy, startTime, endTime);
                verifyAuditLog(bundleAuditLogs.get(1), ChangeType.UPDATE, null, null, TRANSITION, startTime, endTime);
                verifyAuditLog(bundleAuditLogs.get(2), ChangeType.UPDATE, null, null, TRANSITION, startTime, endTime);

                // Audits for subscriptions
                Assert.assertEquals(subscriptionAuditLogs.size(), 3);
                verifyAuditLog(subscriptionAuditLogs.get(0), ChangeType.INSERT, reason, comment, createdBy, startTime, endTime);
                verifyAuditLog(subscriptionAuditLogs.get(1), ChangeType.UPDATE, null, null, TRANSITION, startTime, endTime);
                verifyAuditLog(subscriptionAuditLogs.get(2), ChangeType.UPDATE, null, null, TRANSITION, startTime, endTime);

                // Audit for subscription events
                Assert.assertEquals(subscriptionEvent1AuditLogs.size(), 1);
                verifyAuditLog(subscriptionEvent1AuditLogs.get(0), ChangeType.INSERT, reason, comment, createdBy, startTime, endTime);
                Assert.assertEquals(subscriptionEvent2AuditLogs.size(), 1);
                verifyAuditLog(subscriptionEvent2AuditLogs.get(0), ChangeType.INSERT, reason, comment, createdBy, startTime, endTime);
            }
        }
    }

    private void verifyAuditLog(final AuditLog auditLogJson, final ChangeType changeType, @Nullable final String reasonCode,
                                @Nullable final String comments, @Nullable final String changedBy,
                                final DateTime startTime, final DateTime endTime) {
        Assert.assertEquals(auditLogJson.getChangeType(), changeType.toString());
        Assert.assertFalse(auditLogJson.getChangeDate().isBefore(startTime));
        // Flaky
        //Assert.assertFalse(auditLogJson.getChangeDate().isAfter(endTime));
        Assert.assertEquals(auditLogJson.getReasonCode(), reasonCode);
        Assert.assertEquals(auditLogJson.getComments(), comments);
        Assert.assertEquals(auditLogJson.getChangedBy(), changedBy);
    }

    private AccountTimeline getAccountTimeline(final UUID accountId, final AuditLevel auditLevel) throws KillBillClientException {
        final AccountTimeline accountTimeline = accountApi.getAccountTimeline(accountId, false, auditLevel, requestOptions);

        // Verify also the parallel path
        final AccountTimeline accountTimelineInParallel = accountApi.getAccountTimeline(accountId, true, auditLevel, requestOptions);
        Assert.assertEquals(accountTimelineInParallel, accountTimeline);

        return accountTimeline;
    }
}
