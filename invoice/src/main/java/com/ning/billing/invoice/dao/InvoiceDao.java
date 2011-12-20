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

package com.ning.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import com.ning.billing.invoice.api.Invoice;

public interface InvoiceDao {
    void save(Invoice invoice);

    Invoice getById(final String id);

    List<Invoice> get();

    List<Invoice> getInvoicesByAccount(final String accountId);

    List<Invoice> getInvoicesBySubscription(final String subscriptionId);

    List<UUID> getInvoicesForPayment(final Date targetDate,
                                     final int numberOfDays);

    void notifySuccessfulPayment(final String invoiceId,
                                 final BigDecimal paymentAmount,
                                 final String currency,
                                 final String paymentId,
                                 final Date paymentDate);

    void notifyFailedPayment(final String invoiceId,
                             final String paymentId,
                             final Date paymentAttemptDate);

    void test();
}
