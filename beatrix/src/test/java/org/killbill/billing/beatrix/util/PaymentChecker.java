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

package org.killbill.billing.beatrix.util;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.util.callcontext.CallContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

public class PaymentChecker {

    private static final Logger log = LoggerFactory.getLogger(PaymentChecker.class);

    private final PaymentApi paymentApi;
    private final InvoicePaymentApi invoicePaymentApi;
    private final AuditChecker auditChecker;

    @Inject
    public PaymentChecker(final PaymentApi paymentApi, final InvoicePaymentApi invoicePaymentApi, final AuditChecker auditChecker) {
        this.paymentApi = paymentApi;
        this.invoicePaymentApi = invoicePaymentApi;
        this.auditChecker = auditChecker;
    }

    public Payment checkPayment(final UUID accountId, final int paymentOrderingNumber, final CallContext context, final ExpectedPaymentCheck expected) throws PaymentApiException {
        final List<Payment> payments = paymentApi.getAccountPayments(accountId, false, false, ImmutableList.<PluginProperty>of(), context);
        Assert.assertEquals(payments.size(), paymentOrderingNumber);
        final Payment payment = payments.get(paymentOrderingNumber - 1);
        final PaymentTransaction transaction = getPurchaseTransaction(payment);
        if (transaction.getTransactionStatus() == TransactionStatus.UNKNOWN) {
            checkPaymentNoAuditForRuntimeException(accountId, payment, expected);
        } else {
            checkPayment(accountId, payment, context, expected);
        }
        return payment;
    }

    private PaymentTransaction getPurchaseTransaction(final Payment payment) {
        return Iterables.tryFind(payment.getTransactions(), new Predicate<PaymentTransaction>() {
            @Override
            public boolean apply(final PaymentTransaction input) {
                return input.getTransactionType() == TransactionType.PURCHASE;
            }
        }).get();
    }

    private void checkPayment(final UUID accountId, final Payment payment, final CallContext context, final ExpectedPaymentCheck expected) {
        Assert.assertEquals(payment.getAccountId(), accountId);
        Assert.assertEquals(payment.getCurrency(), expected.getCurrency());

        if (expected.getInvoiceId() != null) {
            for (final InvoicePayment invoicePayment : invoicePaymentApi.getInvoicePayments(payment.getId(), context)) {
                Assert.assertEquals(invoicePayment.getInvoiceId(), expected.getInvoiceId());
            }
        }

        final PaymentTransaction transaction = getPurchaseTransaction(payment);
        Assert.assertTrue(transaction.getAmount().compareTo(expected.getAmount()) == 0, "Actual amount " + transaction.getAmount() + ", expected amount " + expected.getAmount());
        Assert.assertEquals(transaction.getTransactionStatus(), expected.getStatus());
        Assert.assertEquals(transaction.getEffectiveDate().toLocalDate().compareTo(expected.getPaymentDate()), 0, "Actual date " + transaction.getEffectiveDate() + ", expected date " + expected.getPaymentDate());

        auditChecker.checkPaymentCreated(payment, context);
    }

    private void checkPaymentNoAuditForRuntimeException(final UUID accountId, final Payment payment, final ExpectedPaymentCheck expected) {
        Assert.assertEquals(payment.getAccountId(), accountId);
        final PaymentTransaction transaction = getPurchaseTransaction(payment);
        Assert.assertTrue(transaction.getAmount().compareTo(expected.getAmount()) == 0);
        Assert.assertEquals(transaction.getTransactionStatus(), expected.getStatus());
        Assert.assertEquals(payment.getCurrency(), expected.getCurrency());
    }

    public static class ExpectedPaymentCheck {

        private final LocalDate paymentDate;
        private final BigDecimal amount;
        private final TransactionStatus status;
        private final UUID invoiceId;
        private final Currency currency;

        public ExpectedPaymentCheck(final LocalDate paymentDate, final BigDecimal amount, final TransactionStatus status, final UUID invoiceId, final Currency currency) {
            this.paymentDate = paymentDate;
            this.amount = amount;
            this.status = status;
            this.invoiceId = invoiceId;
            this.currency = currency;
        }

        public Currency getCurrency() {
            return currency;
        }

        public LocalDate getPaymentDate() {
            return paymentDate;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public TransactionStatus getStatus() {
            return status;
        }

        public UUID getInvoiceId() {
            return invoiceId;
        }
    }
}
