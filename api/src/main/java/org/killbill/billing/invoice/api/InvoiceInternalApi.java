/*
 * Copyright 2010-2011 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.invoice.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.util.callcontext.TenantContext;

public interface InvoiceInternalApi {

    public Invoice getInvoiceById(UUID invoiceId, InternalTenantContext context) throws InvoiceApiException;

    public Collection<Invoice> getUnpaidInvoicesByAccountId(UUID accountId, LocalDate upToDate, InternalTenantContext context);

    public BigDecimal getAccountBalance(UUID accountId, InternalTenantContext context);

    public void recordPaymentAttemptInit(UUID invoiceId, BigDecimal amountOutstanding, Currency currency, Currency processedCurrency, UUID paymentId, String transactionExternalKey, DateTime paymentDate, InternalCallContext context) throws InvoiceApiException;

    public void recordPaymentAttemptCompletion(UUID invoiceId, BigDecimal amountOutstanding, Currency currency, Currency processedCurrency, UUID paymentId, String transactionExternalKey, DateTime paymentDate, boolean success, InternalCallContext context) throws InvoiceApiException;

    public InvoicePayment getInvoicePaymentForAttempt(UUID paymentId, InternalTenantContext context) throws InvoiceApiException;

    public InvoicePayment getInvoicePaymentForChargeback(UUID paymentId, InternalTenantContext context) throws InvoiceApiException;

    public Invoice getInvoiceForPaymentId(UUID paymentId, InternalTenantContext context) throws InvoiceApiException;

    /**
     * Create a refund.
     *
     * @param paymentId                 payment associated with that refund
     * @param amount                    amount to refund
     * @param isInvoiceAdjusted         whether the refund should trigger an invoice or invoice item adjustment
     * @param invoiceItemIdsWithAmounts invoice item ids and associated amounts to adjust
     * @param transactionExternalKey    refund transaction externalKey
     * @param context                   the call callcontext
     * @return the created invoice payment object associated with this refund
     * @throws InvoiceApiException
     */
    public InvoicePayment recordRefund(UUID paymentId, BigDecimal amount, boolean isInvoiceAdjusted, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts,
                                       String transactionExternalKey, InternalCallContext context) throws InvoiceApiException;

    public InvoicePayment recordChargeback(UUID paymentId, String chargebackTransactionExternalKey, BigDecimal amount, Currency currency, InternalCallContext context) throws InvoiceApiException;

    public InvoicePayment recordChargebackReversal(UUID paymentId, String chargebackTransactionExternalKey, InternalCallContext context) throws InvoiceApiException;

    /**
     * Rebalance CBA for account which have credit and unpaid invoices
     *
     * @param accountId account id
     * @param context   the callcontext
     */
    public void consumeExistingCBAOnAccountWithUnpaidInvoices(final UUID accountId, final InternalCallContext context) throws InvoiceApiException;

    public Map<UUID, BigDecimal> validateInvoiceItemAdjustments(final UUID paymentId, final Map<UUID, BigDecimal> idWithAmount, final InternalTenantContext context) throws InvoiceApiException;

    public void commitInvoice(UUID invoiceId, InternalCallContext context) throws InvoiceApiException;

    public List<InvoicePayment> getInvoicePayments(UUID paymentId, TenantContext context);

    public List<InvoicePayment> getInvoicePaymentsByAccount(UUID accountId, TenantContext context);

    public List<InvoicePayment> getInvoicePaymentsByInvoice(UUID invoiceId, InternalTenantContext context);

    public InvoicePayment getInvoicePaymentByCookieId(String cookieId, TenantContext context);
}
