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

package com.ning.billing.invoice.api;

import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import com.ning.billing.catalog.api.Currency;

public interface InvoiceUserApi {
    public List<UUID> getInvoicesForPayment(DateTime targetDate, int numberOfDays);

    public List<Invoice> getInvoicesByAccount(UUID accountId);

    public List<InvoiceItem> getInvoiceItemsByAccount(UUID accountId);

    public Invoice getInvoice(UUID invoiceId);

    public void paymentAttemptFailed(UUID invoiceId, UUID paymentId, DateTime paymentAttemptDate);

    public void paymentAttemptSuccessful(UUID invoiceId, BigDecimal amount, Currency currency,
                                         UUID paymentId, DateTime paymentDate);
}
