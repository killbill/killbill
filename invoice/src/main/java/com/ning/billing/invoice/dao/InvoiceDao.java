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

import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.util.callcontext.CallContext;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface InvoiceDao {
    void create(Invoice invoice, CallContext context);

    Invoice getById(final UUID id);

    List<Invoice> get();

    List<Invoice> getInvoicesByAccount(final UUID accountId);

    List<Invoice> getInvoicesByAccount(final UUID accountId, final DateTime fromDate);

    List<Invoice> getInvoicesBySubscription(final UUID subscriptionId);

    UUID getInvoiceIdByPaymentAttemptId(final UUID paymentAttemptId);

    InvoicePayment getInvoicePayment(final UUID paymentAttemptId);

    void notifyOfPaymentAttempt(final InvoicePayment invoicePayment, final CallContext context);

    BigDecimal getAccountBalance(final UUID accountId);

    List<Invoice> getUnpaidInvoicesByAccountId(final UUID accountId, final DateTime upToDate);

    void test();

	List<Invoice> getAllInvoicesByAccount(final UUID accountId);

    void setWrittenOff(UUID invoiceId, CallContext context);

    void removeWrittenOff(UUID invoiceId, CallContext context) throws InvoiceApiException;

    void postChargeBack(UUID invoicePaymentId, BigDecimal amount, CallContext context) throws InvoiceApiException;

    BigDecimal getRemainingAmountPaid(UUID invoicePaymentId);
}
