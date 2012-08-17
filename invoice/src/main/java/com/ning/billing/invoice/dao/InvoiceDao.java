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
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;

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

    List<InvoicePayment> getInvoicePayments(final UUID paymentId);

    void notifyOfPayment(final InvoicePayment invoicePayment, final CallContext context);

    BigDecimal getAccountBalance(final UUID accountId);

    public BigDecimal getAccountCBA(final UUID accountId);

    List<Invoice> getUnpaidInvoicesByAccountId(final UUID accountId, @Nullable final LocalDate upToDate);

    void test();

    List<Invoice> getAllInvoicesByAccount(final UUID accountId);

    void setWrittenOff(final UUID invoiceId, final CallContext context) throws TagApiException;

    void removeWrittenOff(final UUID invoiceId, final CallContext context) throws TagApiException;

    InvoicePayment postChargeback(final UUID invoicePaymentId, final BigDecimal amount, final CallContext context) throws InvoiceApiException;

    /**
     * Create a refund.
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
    InvoicePayment createRefund(final UUID paymentId, final BigDecimal amount, final boolean isInvoiceAdjusted, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts,
                                final UUID paymentCookieId, final CallContext context) throws InvoiceApiException;

    BigDecimal getRemainingAmountPaid(final UUID invoicePaymentId);

    UUID getAccountIdFromInvoicePaymentId(final UUID invoicePaymentId) throws InvoiceApiException;

    List<InvoicePayment> getChargebacksByAccountId(final UUID accountId);

    List<InvoicePayment> getChargebacksByPaymentId(final UUID paymentId);

    InvoicePayment getChargebackById(final UUID chargebackId) throws InvoiceApiException;

    /**
     * Retrieve am external charge by id.
     *
     * @param externalChargeId the external charge id
     * @return the external charge invoice item
     * @throws InvoiceApiException
     */
    InvoiceItem getExternalChargeById(final UUID externalChargeId) throws InvoiceApiException;

    /**
     * Add an external charge to a given account and invoice. If invoiceId is null, a new invoice will be created.
     *
     * @param accountId     the account id
     * @param invoiceId     the invoice id
     * @param bundleId      the bundle id
     * @param description   a description for that charge
     * @param amount        the external charge amount
     * @param effectiveDate the day to post the external charge, in the account timezone
     * @param currency      the external charge currency
     * @param context       the call context
     * @return the newly created external charge invoice item
     */
    InvoiceItem insertExternalCharge(final UUID accountId, @Nullable final UUID invoiceId, @Nullable final UUID bundleId, @Nullable final String description,
                                     final BigDecimal amount, final LocalDate effectiveDate, final Currency currency, final CallContext context);

    /**
     * Retrieve a credit by id.
     *
     * @param creditId the credit id
     * @return the credit invoice item
     * @throws InvoiceApiException
     */
    InvoiceItem getCreditById(final UUID creditId) throws InvoiceApiException;

    /**
     * Add a credit to a given account and invoice. If invoiceId is null, a new invoice will be created.
     *
     * @param accountId     the account id
     * @param invoiceId     the invoice id
     * @param amount        the credit amount
     * @param effectiveDate the day to grant the credit, in the account timezone
     * @param currency      the credit currency
     * @param context       the call context
     * @return the newly created credit invoice item
     */
    InvoiceItem insertCredit(final UUID accountId, @Nullable final UUID invoiceId, final BigDecimal amount,
                             final LocalDate effectiveDate, final Currency currency, final CallContext context);

    /**
     * Adjust an invoice item.
     *
     * @param accountId     the account id
     * @param invoiceId     the invoice id
     * @param invoiceItemId the invoice item id to adjust
     * @param effectiveDate adjustment effective date, in the account timezone
     * @param amount        the amount to adjust. Pass null to adjust the full amount of the original item
     * @param currency      the currency of the amount. Pass null to default to the original currency used
     * @param context       the call context
     * @return the newly created adjustment item
     */
    InvoiceItem insertInvoiceItemAdjustment(final UUID accountId, final UUID invoiceId, final UUID invoiceItemId, final LocalDate effectiveDate,
                                            @Nullable final BigDecimal amount, @Nullable final Currency currency, final CallContext context);
}
