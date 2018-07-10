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

package org.killbill.billing.beatrix.util;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.util.callcontext.CallContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

public class RefundChecker {

    private static final Logger log = LoggerFactory.getLogger(RefundChecker.class);

    private final PaymentApi paymentApi;
    private final InvoicePaymentApi invoicePaymentApi;
    private final AuditChecker auditChecker;
    private final InvoiceUserApi invoiceUserApi;

    @Inject
    public RefundChecker(final PaymentApi paymentApi, final InvoicePaymentApi invoicePaymentApi, final InvoiceUserApi invoiceApi, final AuditChecker auditChecker) {
        this.paymentApi = paymentApi;
        this.invoicePaymentApi = invoicePaymentApi;
        this.auditChecker = auditChecker;
        this.invoiceUserApi = invoiceApi;
    }

    public PaymentTransaction checkRefund(final UUID paymentId, final CallContext context, ExpectedRefundCheck expected) throws PaymentApiException {

        final Payment payment = paymentApi.getPayment(paymentId, false, false, ImmutableList.<PluginProperty>of(), context);
        final PaymentTransaction refund = Iterables.tryFind(payment.getTransactions(), new Predicate<PaymentTransaction>() {
            @Override
            public boolean apply(final PaymentTransaction input) {
                return input.getTransactionType() == TransactionType.REFUND;
            }
        }).orNull();

        Assert.assertNotNull(refund);

        final InvoicePayment refundInvoicePayment = getInvoicePaymentEntry(paymentId, InvoicePaymentType.REFUND, context);
        final InvoicePayment invoicePayment = getInvoicePaymentEntry(paymentId, InvoicePaymentType.ATTEMPT, context);

        Assert.assertEquals(refund.getPaymentId(), expected.getPaymentId());
        Assert.assertEquals(refund.getCurrency(), expected.getCurrency());
        Assert.assertEquals(refund.getAmount().compareTo(expected.getRefundAmount()), 0);

        Assert.assertEquals(refundInvoicePayment.getPaymentId(), paymentId);
        Assert.assertEquals(refundInvoicePayment.getLinkedInvoicePaymentId(), invoicePayment.getId());
        Assert.assertEquals(refundInvoicePayment.getPaymentCookieId(), refund.getExternalKey());
        Assert.assertEquals(refundInvoicePayment.getInvoiceId(), invoicePayment.getInvoiceId());
        Assert.assertEquals(refundInvoicePayment.getAmount().compareTo(expected.getRefundAmount().negate()), 0);
        Assert.assertEquals(refundInvoicePayment.getCurrency(), expected.getCurrency());

        return refund;
    }

    private PaymentTransaction getRefundTransaction(final Payment payment) {
        return Iterables.tryFind(payment.getTransactions(), new Predicate<PaymentTransaction>() {
            @Override
            public boolean apply(final PaymentTransaction input) {
                return input.getTransactionType() == TransactionType.REFUND;
            }
        }).get();
    }

    private InvoicePayment getInvoicePaymentEntry(final UUID paymentId, final InvoicePaymentType type, final CallContext context) {
        final List<InvoicePayment> invoicePayments = invoicePaymentApi.getInvoicePayments(paymentId, context);
        final Collection<InvoicePayment> refundInvoicePayments = Collections2.filter(invoicePayments, new Predicate<InvoicePayment>() {
            @Override
            public boolean apply(@Nullable final InvoicePayment invoicePayment) {
                return invoicePayment.getType() == type && invoicePayment.getPaymentId().equals(paymentId);
            }
        });
        Assert.assertEquals(refundInvoicePayments.size(), 1);
        return refundInvoicePayments.iterator().next();
    }

    public static class ExpectedRefundCheck {

        private final UUID paymentId;
        private final boolean isAdjusted;
        private final BigDecimal refundAmount;
        private final Currency currency;
        private final LocalDate refundDate;

        public ExpectedRefundCheck(final UUID paymentId, final boolean adjusted, final BigDecimal refundAmount, final Currency currency, final LocalDate refundDate) {
            this.paymentId = paymentId;
            isAdjusted = adjusted;
            this.refundAmount = refundAmount;
            this.currency = currency;
            this.refundDate = refundDate;
        }

        public UUID getPaymentId() {
            return paymentId;
        }

        public boolean isAdjusted() {
            return isAdjusted;
        }

        public BigDecimal getRefundAmount() {
            return refundAmount;
        }

        public Currency getCurrency() {
            return currency;
        }

        public LocalDate getRefundDate() {
            return refundDate;
        }
    }
}
