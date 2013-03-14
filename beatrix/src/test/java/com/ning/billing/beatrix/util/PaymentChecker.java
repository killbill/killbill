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
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.util.callcontext.CallContext;

import com.google.inject.Inject;

public class PaymentChecker {

    private static final Logger log = LoggerFactory.getLogger(PaymentChecker.class);


    private final PaymentApi paymentApi;
    private final AuditChecker auditChecker;

    @Inject
    public PaymentChecker(final PaymentApi paymentApi, final AuditChecker auditChecker) {
        this.paymentApi = paymentApi;
        this.auditChecker = auditChecker;
    }

    public Payment checkPayment(final UUID accountId, final int paymentOrderingNumber, final CallContext context, ExpectedPaymentCheck expected) throws PaymentApiException {
        final List<Payment> payments = paymentApi.getAccountPayments(accountId, context);
        Assert.assertEquals(payments.size(), paymentOrderingNumber);
        final Payment payment = payments.get(paymentOrderingNumber - 1);
        if (payment.getPaymentStatus() == PaymentStatus.UNKNOWN) {
            checkPaymentNoAuditForRuntimeException(accountId, payment, context, expected);
        } else {
            checkPayment(accountId, payment, context, expected);
        }
        return payment;
    }

    private void checkPayment(final UUID accountId, final Payment payment, final CallContext context, final ExpectedPaymentCheck expected) {
        Assert.assertEquals(payment.getAccountId(), accountId);
        Assert.assertTrue(payment.getAmount().compareTo(expected.getAmount()) == 0);
        Assert.assertEquals(payment.getPaymentStatus(),expected.getStatus());
        Assert.assertEquals(payment.getInvoiceId(), expected.getInvoiceId());
        Assert.assertEquals(payment.getCurrency(), expected.getCurrency());
        auditChecker.checkPaymentCreated(payment, context);
    }

    private void checkPaymentNoAuditForRuntimeException(final UUID accountId, final Payment payment, final CallContext context, final ExpectedPaymentCheck expected) {
        Assert.assertEquals(payment.getAccountId(), accountId);
        Assert.assertTrue(payment.getAmount().compareTo(expected.getAmount()) == 0);
        Assert.assertEquals(payment.getPaymentStatus(),expected.getStatus());
        Assert.assertEquals(payment.getInvoiceId(), expected.getInvoiceId());
        Assert.assertEquals(payment.getCurrency(), expected.getCurrency());
    }

    public static class ExpectedPaymentCheck {

        private final LocalDate paymentDate;
        private final BigDecimal amount;
        private final PaymentStatus status;
        private final UUID invoiceId;
        private final Currency currency;

        public ExpectedPaymentCheck(final LocalDate paymentDate, final BigDecimal amount, final PaymentStatus status, final UUID invoiceId, final Currency currency) {
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

        public PaymentStatus getStatus() {
            return status;
        }

        public UUID getInvoiceId() {
            return invoiceId;
        }
    }
}
