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

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;

public interface InvoiceInternalApi {

    public Invoice getInvoiceById(UUID invoiceId, InternalTenantContext context) throws InvoiceApiException;

    public Collection<Invoice> getUnpaidInvoicesByAccountId(UUID accountId, LocalDate upToDate, InternalTenantContext context);

    public BigDecimal getAccountBalance(UUID accountId, InternalTenantContext context);

    public void notifyOfPayment(UUID invoiceId, BigDecimal amountOutstanding, Currency currency, UUID paymentId, DateTime paymentDate, InternalCallContext context) throws InvoiceApiException;

    public void notifyOfPayment(InvoicePayment invoicePayment, InternalCallContext context) throws InvoiceApiException;

    public InvoicePayment getInvoicePaymentForAttempt(UUID paymentId, InternalTenantContext context) throws InvoiceApiException;

    public Invoice getInvoiceForPaymentId(UUID paymentId, InternalTenantContext context) throws InvoiceApiException;

    /**
     * Create a refund.
     *
     * @param paymentId                 payment associated with that refund
     * @param amount                    amount to refund
     * @param isInvoiceAdjusted         whether the refund should trigger an invoice or invoice item adjustment
     * @param invoiceItemIdsWithAmounts invoice item ids and associated amounts to adjust
     * @param paymentCookieId           payment cookie id
     * @param context                   the call callcontext
     * @return the created invoice payment object associated with this refund
     * @throws InvoiceApiException
     */
    public InvoicePayment createRefund(UUID paymentId, BigDecimal amount, boolean isInvoiceAdjusted, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts,
                                       UUID paymentCookieId, InternalCallContext context) throws InvoiceApiException;

    /**
     * Rebalance CBA for account which have credit and unpaid invoices
     *
     * @param accountId account id
     * @param context the callcontext
     */
    public void consumeExistingCBAOnAccountWithUnpaidInvoices(final UUID accountId, final InternalCallContext context) throws InvoiceApiException;


    /**
     * Insert a new notification with a notificationDate of today to trigger a new invoice on the account.
     *
     * @param accountId        account id
     * @param accountTimeZone  timezone of the account
     * @param context          the context
     *
     * @throws InvoiceApiException
     */
    public void scheduleInvoiceForAccount(UUID accountId, DateTimeZone accountTimeZone, InternalCallContext context) throws InvoiceApiException;
}
