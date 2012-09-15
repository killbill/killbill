/*
 * Copyright 2010-2012 Ning, Inc.
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.callcontext.CallContext;

public interface InvoicePaymentApi {

    /**
     * @param accountId id of the account
     * @return All invoices, including migrated invoices
     */
    public List<Invoice> getAllInvoicesByAccount(UUID accountId);

    public Invoice getInvoice(UUID invoiceId) throws InvoiceApiException;

    public Invoice getInvoiceForPaymentId(UUID paymentId) throws InvoiceApiException;

    public List<InvoicePayment> getInvoicePayments(UUID paymentId);

    public InvoicePayment getInvoicePaymentForAttempt(UUID paymentId);

    public void notifyOfPayment(InvoicePayment invoicePayment, CallContext context);

    public void notifyOfPayment(UUID invoiceId, BigDecimal amountOutstanding, Currency currency, UUID paymentId, DateTime paymentDate, CallContext context);

    /**
     * Create a refund.
     *
     *
     * @param paymentId                 payment associated with that refund
     * @param amount                    amount to refund
     * @param isInvoiceAdjusted         whether the refund should trigger an invoice or invoice item adjustment
     * @param invoiceItemIdsWithAmounts invoice item ids and associated amounts to adjust
     * @param paymentCookieId           payment cookie id
     * @param context                   the call context
     * @return the created invoice payment object associated with this refund
     * @throws InvoiceApiException
     */
    public InvoicePayment createRefund(UUID paymentId, BigDecimal amount, boolean isInvoiceAdjusted, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts,
                                       UUID paymentCookieId, CallContext context) throws InvoiceApiException;

    public InvoicePayment createChargeback(UUID invoicePaymentId, BigDecimal amount, CallContext context) throws InvoiceApiException;

    public InvoicePayment createChargeback(UUID invoicePaymentId, CallContext context) throws InvoiceApiException;

    public BigDecimal getRemainingAmountPaid(UUID invoicePaymentId);

    public List<InvoicePayment> getChargebacksByAccountId(UUID accountId);

    public UUID getAccountIdFromInvoicePaymentId(UUID uuid) throws InvoiceApiException;

    public List<InvoicePayment> getChargebacksByPaymentId(UUID paymentId);

    public InvoicePayment getChargebackById(UUID chargebackId) throws InvoiceApiException;
}
