/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.beatrix.util;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.InvoicePaymentType;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.Refund;
import com.ning.billing.util.callcontext.CallContext;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
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

    public Refund checkRefund(final UUID paymentId, final CallContext context, ExpectedRefundCheck expected) throws PaymentApiException {

        final List<Refund> refunds = paymentApi.getPaymentRefunds(paymentId, context);
        Assert.assertEquals(refunds.size(), 1);

        final InvoicePayment refundInvoicePayment = getInvoicePaymentEntry(paymentId, InvoicePaymentType.REFUND, context);
        final InvoicePayment invoicePayment = getInvoicePaymentEntry(paymentId, InvoicePaymentType.ATTEMPT, context);

        final Refund refund = refunds.get(0);
        Assert.assertEquals(refund.getPaymentId(), expected.getPaymentId());
        Assert.assertEquals(refund.getCurrency(), expected.getCurrency());
        Assert.assertEquals(refund.isAdjusted(), expected.isAdjusted);
        Assert.assertEquals(refund.getRefundAmount().compareTo(expected.getRefundAmount()), 0);

        Assert.assertEquals(refundInvoicePayment.getPaymentId(), paymentId);
        Assert.assertEquals(refundInvoicePayment.getLinkedInvoicePaymentId(), invoicePayment.getId());
        Assert.assertEquals(refundInvoicePayment.getPaymentCookieId(), refund.getId());
        Assert.assertEquals(refundInvoicePayment.getInvoiceId(), invoicePayment.getInvoiceId());
        Assert.assertEquals(refundInvoicePayment.getAmount().compareTo(expected.getRefundAmount().negate()), 0);
        Assert.assertEquals(refundInvoicePayment.getCurrency(), expected.getCurrency());

        return refund;
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
