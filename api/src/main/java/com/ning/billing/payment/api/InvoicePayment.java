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

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Currency;

public class InvoicePayment {
        private final UUID invoiceId;
        private final UUID paymentAttemptId;
        private final DateTime paymentAttemptDate;
        private final BigDecimal amount;
        private final Currency currency;

        public InvoicePayment(UUID invoiceId, BigDecimal amount, Currency currency, UUID paymentAttemptId, DateTime paymentAttemptDate) {
            this.invoiceId = invoiceId;
            this.paymentAttemptId = paymentAttemptId;
            this.paymentAttemptDate = paymentAttemptDate;
            this.amount = amount;
            this.currency = currency;
        }

        public UUID getInvoiceId() {
            return invoiceId;
        }

        public UUID getPaymentAttemptId() {
            return paymentAttemptId;
        }

        public DateTime getPaymentAttemptDate() {
            return paymentAttemptDate;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public Currency getCurrency() {
            return currency;
        }

        @Override
        public String toString() {
            return "InvoicePayment [invoiceId=" + invoiceId + ", paymentAttemptId=" + paymentAttemptId + ", paymentAttemptDate=" + paymentAttemptDate + ", amount=" + amount + ", currency=" + currency + "]";
        }

}
