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

    public List<Invoice> getInvoicesByAccount(UUID accountId);

    /**
     * Find invoices from a given day, for a given account.
     *
     * @param accountId account id
     * @param fromDate  the earliest target day to consider, in the account timezone
     * @return a list of invoices
     */
    public List<Invoice> getInvoicesByAccount(UUID accountId, LocalDate fromDate);

    public BigDecimal getAccountBalance(UUID accountId);

    public Invoice getInvoice(UUID invoiceId);

    public Invoice getInvoiceByNumber(Integer number);

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

    public void tagInvoiceAsWrittenOff(UUID invoiceId, CallContext context) throws TagApiException;

    public void tagInvoiceAsNotWrittenOff(UUID invoiceId, CallContext context) throws TagApiException;

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
     * Add a credit to an invoice.
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

    public String getInvoiceAsHTML(UUID invoiceId) throws AccountApiException, IOException, InvoiceApiException;
}
