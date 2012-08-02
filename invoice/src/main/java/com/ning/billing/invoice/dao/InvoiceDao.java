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
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;

import com.ning.billing.account.api.BillCycleDay;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.callcontext.CallContext;

public interface InvoiceDao {

    void create(final Invoice invoice, final int billCycleDayUTC, final boolean isRealInvoice, final CallContext context);

    Invoice getById(final UUID id);

    Invoice getByNumber(final Integer number);

    List<Invoice> get();

    List<Invoice> getInvoicesByAccount(final UUID accountId);

    List<Invoice> getInvoicesByAccount(final UUID accountId, final LocalDate fromDate);

    List<Invoice> getInvoicesBySubscription(final UUID subscriptionId);

    UUID getInvoiceIdByPaymentId(final UUID paymentId);

    InvoicePayment getInvoicePayment(final UUID paymentId);

    void notifyOfPayment(final InvoicePayment invoicePayment, final CallContext context);

    BigDecimal getAccountBalance(final UUID accountId);

    public BigDecimal getAccountCBA(final UUID accountId);

    List<Invoice> getUnpaidInvoicesByAccountId(final UUID accountId, @Nullable final LocalDate upToDate);

    void test();

    List<Invoice> getAllInvoicesByAccount(final UUID accountId);

    void setWrittenOff(final UUID invoiceId, final CallContext context) throws TagApiException;

    void removeWrittenOff(final UUID invoiceId, final CallContext context) throws TagApiException;

    InvoicePayment postChargeback(final UUID invoicePaymentId, final BigDecimal amount, final CallContext context) throws InvoiceApiException;

    InvoicePayment createRefund(UUID paymentId, BigDecimal amount, boolean isInvoiceAdjusted, UUID paymentCookieId, CallContext context) throws InvoiceApiException;

    BigDecimal getRemainingAmountPaid(final UUID invoicePaymentId);

    UUID getAccountIdFromInvoicePaymentId(final UUID invoicePaymentId) throws InvoiceApiException;

    List<InvoicePayment> getChargebacksByAccountId(final UUID accountId);

    List<InvoicePayment> getChargebacksByPaymentId(final UUID paymentId);

    InvoicePayment getChargebackById(final UUID chargebackId) throws InvoiceApiException;

    InvoiceItem getCreditById(final UUID creditId) throws InvoiceApiException;

    InvoiceItem insertCredit(final UUID accountId, final UUID invoiceId, final BigDecimal amount,
                             final LocalDate effectiveDate, final Currency currency, final CallContext context);

}
