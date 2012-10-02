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
import com.ning.billing.util.callcontext.TenantContext;

public interface InvoiceUserApi {

    /**
     * Get all invoices for a given account.
     *
     * @param accountId account id
     * @param context   the tenant context
     * @return all invoices
     */
    public List<Invoice> getInvoicesByAccount(UUID accountId, TenantContext context);

    /**
     * Find invoices from a given day, for a given account.
     *
     * @param accountId account id
     * @param fromDate  the earliest target day to consider, in the account timezone
     * @param context   the tenant context
     * @return a list of invoices
     */
    public List<Invoice> getInvoicesByAccount(UUID accountId, LocalDate fromDate, TenantContext context);

    /**
     * Retrieve the account balance.
     *
     * @param accountId account id
     * @param context   the tenant context
     * @return the account balance
     */
    public BigDecimal getAccountBalance(UUID accountId, TenantContext context);

    /**
     * Retrieve the account CBA.
     *
     * @param accountId account id
     * @param context   the tenant context
     * @return the account CBA
     */
    public BigDecimal getAccountCBA(UUID accountId, TenantContext context);

    /**
     * Retrieve an invoice by id.
     *
     * @param invoiceId invoice id
     * @param context   the tenant context
     * @return the invoice
     */
    public Invoice getInvoice(UUID invoiceId, TenantContext context) throws InvoiceApiException;

    /**
     * Retrieve an invoice by invoice number.
     *
     * @param number  invoice number
     * @param context the tenant context
     * @return the invoice
     */
    public Invoice getInvoiceByNumber(Integer number, TenantContext context) throws InvoiceApiException;

    /**
     * Record a payment for an invoice.
     *
     * @param invoicePayment invoice payment
     * @param context        call context
     */
    public void notifyOfPayment(InvoicePayment invoicePayment, CallContext context) throws InvoiceApiException;

    /**
     * Find unpaid invoices for a given account, up to a given day.
     *
     * @param accountId account id
     * @param upToDate  the latest target day to consider, in the account timezone
     * @param context   the tenant context
     * @return a collection of invoices
     */
    public Collection<Invoice> getUnpaidInvoicesByAccountId(UUID accountId, LocalDate upToDate, TenantContext context);

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
    public void tagInvoiceAsWrittenOff(UUID invoiceId, CallContext context) throws TagApiException, InvoiceApiException;

    /**
     * Unmark an invoice as written off.
     *
     * @param invoiceId invoice id
     * @param context   call context
     * @throws TagApiException
     */
    public void tagInvoiceAsNotWrittenOff(UUID invoiceId, CallContext context) throws TagApiException, InvoiceApiException;

    /**
     * Retrieve an external charge by id.
     *
     * @param externalChargeId external charge id
     * @param context          the tenant context
     * @return the external charge
     * @throws InvoiceApiException
     */
    public InvoiceItem getExternalChargeById(UUID externalChargeId, TenantContext context) throws InvoiceApiException;

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
     * Add an external charge to an account tied to a particular bundle.
     *
     * @param accountId     account id
     * @param bundleId      bundle id
     * @param amount        the external charge amount
     * @param description   a description for that charge
     * @param effectiveDate the day to post the external charge, in the account timezone
     * @param currency      the external charge currency
     * @param context       the call context
     * @return the external charge invoice item
     * @throws InvoiceApiException
     */
    public InvoiceItem insertExternalChargeForBundle(UUID accountId, UUID bundleId, BigDecimal amount, String description, LocalDate effectiveDate,
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
     * Add an external charge to an invoice tied to a particular bundle.
     *
     * @param accountId     account id
     * @param invoiceId     invoice id
     * @param bundleId      bundle id
     * @param amount        the external charge amount
     * @param description   a description for that charge
     * @param effectiveDate the day to post the external charge, in the account timezone
     * @param currency      the external charge currency
     * @param context       the call context
     * @return the external charge invoice item
     * @throws InvoiceApiException
     */
    public InvoiceItem insertExternalChargeForInvoiceAndBundle(UUID accountId, UUID invoiceId, UUID bundleId, BigDecimal amount, String description,
                                                               LocalDate effectiveDate, Currency currency, CallContext context) throws InvoiceApiException;

    /**
     * Retrieve a credit by id.
     *
     * @param creditId credit id
     * @param context  the tenant context
     * @return the credit
     * @throws InvoiceApiException
     */
    public InvoiceItem getCreditById(UUID creditId, TenantContext context) throws InvoiceApiException;

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
     * Delete a CBA item.
     *
     * @param accountId     account id
     * @param invoiceId     invoice id
     * @param invoiceItemId invoice item id (must be of type CBA_ADJ)
     * @param context       the call context
     * @throws InvoiceApiException
     */
    public void deleteCBA(UUID accountId, UUID invoiceId, UUID invoiceItemId, CallContext context) throws InvoiceApiException;

    /**
     * Retrieve the invoice formatted in HTML.
     *
     * @param invoiceId invoice id
     * @param context   the tenant context
     * @return the invoice in HTML format
     * @throws AccountApiException
     * @throws IOException
     * @throws InvoiceApiException
     */
    public String getInvoiceAsHTML(UUID invoiceId, TenantContext context) throws AccountApiException, IOException, InvoiceApiException;
}
