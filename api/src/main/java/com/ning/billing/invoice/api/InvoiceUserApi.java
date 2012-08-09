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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;

import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.callcontext.CallContext;

public interface InvoiceUserApi {

    /**
     * Get all invoices for a given account.
     *
     * @param accountId account id
     * @return all invoices
     */
    public List<Invoice> getInvoicesByAccount(UUID accountId);

    /**
     * Find invoices from a given day, for a given account.
     *
     * @param accountId account id
     * @param fromDate  the earliest target day to consider, in the account timezone
     * @return a list of invoices
     */
    public List<Invoice> getInvoicesByAccount(UUID accountId, LocalDate fromDate);

    /**
     * Retrieve the account balance.
     *
     * @param accountId account id
     * @return the account balance
     */
    public BigDecimal getAccountBalance(UUID accountId);

    /**
     * Retrieve an invoice by id.
     *
     * @param invoiceId invoice id
     * @return the invoice
     */
    public Invoice getInvoice(UUID invoiceId);

    /**
     * Retrieve an invoice by invoice number.
     *
     * @param number invoice number
     * @return the invoice
     */
    public Invoice getInvoiceByNumber(Integer number);

    /**
     * Record a payment for an invoice.
     *
     * @param invoicePayment invoice payment
     * @param context        call context
     */
    public void notifyOfPayment(InvoicePayment invoicePayment, CallContext context);

    /**
     * Find unpaid invoices for a given account, up to a given day.
     *
     * @param accountId account id
     * @param upToDate  the latest target day to consider, in the account timezone
     * @return a collection of invoices
     */
    public Collection<Invoice> getUnpaidInvoicesByAccountId(UUID accountId, LocalDate upToDate);

    /**
     * Trigger an invoice for a given account and a given day.
     *
     * @param accountId  account id
     * @param targetDate the target day, in the account timezone
     * @param dryRun     dry run mode or not
     * @param context    the call context
     * @return the invoice generated
     * @throws InvoiceApiException
     */
    public Invoice triggerInvoiceGeneration(UUID accountId, LocalDate targetDate, boolean dryRun, CallContext context) throws InvoiceApiException;

    /**
     * Mark an invoice as written off.
     *
     * @param invoiceId invoice id
     * @param context   call context
     * @throws TagApiException
     */
    public void tagInvoiceAsWrittenOff(UUID invoiceId, CallContext context) throws TagApiException;

    /**
     * Unmark an invoice as written off.
     *
     * @param invoiceId invoice id
     * @param context   call context
     * @throws TagApiException
     */
    public void tagInvoiceAsNotWrittenOff(UUID invoiceId, CallContext context) throws TagApiException;

    /**
     * Retrieve an external charge by id.
     *
     * @param externalChargeId external charge id
     * @return the external charge
     * @throws InvoiceApiException
     */
    public InvoiceItem getExternalChargeById(UUID externalChargeId) throws InvoiceApiException;

    /**
     * Add an external charge to an account.
     *
     * @param accountId     account id
     * @param amount        the external charge amount
     * @param description   a description for that charge
     * @param effectiveDate the day to post the external charge, in the account timezone
     * @param currency      the external charge currency
     * @param context       the call context
     * @return the external charge invoice item
     * @throws InvoiceApiException
     */
    public InvoiceItem insertExternalCharge(UUID accountId, BigDecimal amount, String description, LocalDate effectiveDate,
                                            Currency currency, CallContext context) throws InvoiceApiException;

    /**
     * Add an external charge to an invoice.
     *
     * @param accountId     account id
     * @param invoiceId     invoice id
     * @param amount        the external charge amount
     * @param description   a description for that charge
     * @param effectiveDate the day to post the external charge, in the account timezone
     * @param currency      the external charge currency
     * @param context       the call context
     * @return the external charge invoice item
     * @throws InvoiceApiException
     */
    public InvoiceItem insertExternalChargeForInvoice(UUID accountId, UUID invoiceId, BigDecimal amount, String description,
                                                      LocalDate effectiveDate, Currency currency, CallContext context) throws InvoiceApiException;

    /**
     * Retrieve a credit by id.
     *
     * @param creditId credit id
     * @return the credit
     * @throws InvoiceApiException
     */
    public InvoiceItem getCreditById(UUID creditId) throws InvoiceApiException;

    /**
     * Add a credit to an account.
     *
     * @param accountId     account id
     * @param amount        the credit amount
     * @param effectiveDate the day to grant the credit, in the account timezone
     * @param currency      the credit currency
     * @param context       the call context
     * @return the credit invoice item
     * @throws InvoiceApiException
     */
    public InvoiceItem insertCredit(UUID accountId, BigDecimal amount, LocalDate effectiveDate,
                                    Currency currency, CallContext context) throws InvoiceApiException;

    /**
     * Add a credit to an invoice. This can be used to adjust invoices.
     *
     * @param accountId     account id
     * @param invoiceId     invoice id
     * @param amount        the credit amount
     * @param effectiveDate the day to grant the credit, in the account timezone
     * @param currency      the credit currency
     * @param context       the call context
     * @return the credit invoice item
     * @throws InvoiceApiException
     */
    public InvoiceItem insertCreditForInvoice(UUID accountId, UUID invoiceId, BigDecimal amount, LocalDate effectiveDate,
                                              Currency currency, CallContext context) throws InvoiceApiException;

    /**
     * Adjust fully a given invoice item.
     *
     * @param accountId     account id
     * @param invoiceId     invoice id
     * @param invoiceItemId invoice item id
     * @param effectiveDate the effective date for this adjustment invoice item (in the account timezone)
     * @param context       the call context
     * @return the adjustment invoice item
     * @throws InvoiceApiException
     */
    public InvoiceItem insertInvoiceItemAdjustment(UUID accountId, UUID invoiceId, UUID invoiceItemId, LocalDate effectiveDate, CallContext context) throws InvoiceApiException;

    /**
     * Adjust partially a given invoice item.
     *
     * @param accountId     account id
     * @param invoiceId     invoice id
     * @param invoiceItemId invoice item id
     * @param effectiveDate the effective date for this adjustment invoice item (in the account timezone)
     * @param amount        the adjustment amount
     * @param currency      adjustment currency
     * @param context       the call context
     * @return the adjustment invoice item
     * @throws InvoiceApiException
     */
    public InvoiceItem insertInvoiceItemAdjustment(UUID accountId, UUID invoiceId, UUID invoiceItemId, LocalDate effectiveDate,
                                                   BigDecimal amount, Currency currency, CallContext context) throws InvoiceApiException;

    /**
     * Retrieve the invoice formatted in HTML.
     *
     * @param invoiceId invoice id
     * @return the invoice in HTML format
     * @throws AccountApiException
     * @throws IOException
     * @throws InvoiceApiException
     */
    public String getInvoiceAsHTML(UUID invoiceId) throws AccountApiException, IOException, InvoiceApiException;
}
