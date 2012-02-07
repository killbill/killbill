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

package com.ning.billing.payment;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.invoice.api.Invoice;

public class PaymentAttempt {
    private final UUID paymentAttemptId;
    private final UUID accountId;
    private final UUID invoiceId;
    private final BigDecimal paymentAttemptAmount;
    private final DateTime paymentAttemptDate;

    public PaymentAttempt(UUID paymentAttemptId, Invoice invoice) {
        this.paymentAttemptId = paymentAttemptId;
        this.accountId = invoice.getAccountId();
        this.invoiceId = invoice.getId();
        this.paymentAttemptAmount = invoice.getAmountOutstanding();
        this.paymentAttemptDate = new DateTime(DateTimeZone.UTC);
    }

    public UUID getPaymentAttemptId() {
        return paymentAttemptId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public BigDecimal getPaymentAttemptAmount() {
        return paymentAttemptAmount;
    }

        public DateTime getPaymentAttemptDate() {
            return paymentAttemptDate;
    }

        @Override
        public String toString() {
            return "PaymentAttempt [paymentAttemptId=" + paymentAttemptId + ", accountId=" + accountId + ", invoiceId=" + invoiceId + ", paymentAttemptAmount=" + paymentAttemptAmount + ", paymentAttemptDate=" + paymentAttemptDate + "]";
        }

}
