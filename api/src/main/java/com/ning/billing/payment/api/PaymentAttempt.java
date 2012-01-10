/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.payment.api;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.invoice.api.Invoice;

public class PaymentAttempt {
    private final UUID paymentAttemptId;
    private final String paymentId;
    private final DateTime paymentAttemptDate;
    private final Invoice invoice;

    public PaymentAttempt(UUID paymentAttemptId, Invoice invoice) {
        this.paymentAttemptId = paymentAttemptId;
        this.paymentAttemptDate = new DateTime(DateTimeZone.UTC);
        this.invoice = invoice;
        this.paymentId = null;
    }

    public PaymentAttempt(UUID paymentAttemptId, Invoice invoice, String paymentId) {
        this(paymentAttemptId, invoice, paymentId, null);
    }

    public PaymentAttempt(UUID paymentAttemptId, Invoice invoice, String paymentId, DateTime paymentAttemptDate) {
        this.paymentAttemptId = paymentAttemptId;
        this.paymentAttemptDate = paymentAttemptDate == null ? new DateTime(DateTimeZone.UTC) : paymentAttemptDate;
        this.invoice = invoice;
        this.paymentId = paymentId;
    }

    public UUID getPaymentAttemptId() {
        return paymentAttemptId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public DateTime getPaymentAttemptDate() {
        return paymentAttemptDate;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    @Override
    public String toString() {
        return "PaymentAttempt [paymentAttemptId=" + paymentAttemptId + ", paymentId=" + paymentId + ", paymentAttemptDate=" + paymentAttemptDate + ", invoice=" + invoice + "]";
    }

}
