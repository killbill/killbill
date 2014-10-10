/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entity.EntityPersistenceException;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.EntityDao;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;

public interface InvoiceDao extends EntityDao<InvoiceModelDao, Invoice, InvoiceApiException> {

    void createInvoice(final InvoiceModelDao invoice, final List<InvoiceItemModelDao> invoiceItems,
                       final boolean isRealInvoice, final Map<UUID, List<DateTime>> callbackDateTimePerSubscriptions,
                       final InternalCallContext context);

    InvoiceModelDao getByNumber(Integer number, InternalTenantContext context) throws InvoiceApiException;

    List<InvoiceModelDao> getInvoicesByAccount(InternalTenantContext context);

    List<InvoiceModelDao> getInvoicesByAccount(LocalDate fromDate, InternalTenantContext context);

    List<InvoiceModelDao> getInvoicesBySubscription(UUID subscriptionId, InternalTenantContext context);

    public Pagination<InvoiceModelDao> searchInvoices(String searchKey, Long offset, Long limit, InternalTenantContext context);

    UUID getInvoiceIdByPaymentId(UUID paymentId, InternalTenantContext context);

    List<InvoicePaymentModelDao> getInvoicePayments(UUID paymentId, InternalTenantContext context);

    List<InvoicePaymentModelDao> getInvoicePaymentsByAccount(InternalTenantContext context);

    BigDecimal getAccountBalance(UUID accountId, InternalTenantContext context);

    public BigDecimal getAccountCBA(UUID accountId, InternalTenantContext context);

    List<InvoiceModelDao> getUnpaidInvoicesByAccountId(UUID accountId, @Nullable LocalDate upToDate, InternalTenantContext context);

    // Include migrated invoices
    List<InvoiceModelDao> getAllInvoicesByAccount(InternalTenantContext context);

    InvoicePaymentModelDao postChargeback(UUID paymentId, BigDecimal amount, Currency currency, InternalCallContext context) throws InvoiceApiException;

    InvoiceItemModelDao doCBAComplexity(InvoiceModelDao invoice, InternalCallContext context) throws InvoiceApiException;

        /**
         * Create a refund.
         *
         * @param paymentId                 payment associated with that refund
         * @param amount                    amount to refund
         * @param isInvoiceAdjusted         whether the refund should trigger an invoice or invoice item adjustment
         * @param invoiceItemIdsWithAmounts invoice item ids and associated amounts to adjust
         * @param transactionExternalKey    transaction refund externalKey
         * @param context                   the call callcontext
         * @return the created invoice payment object associated with this refund
         * @throws InvoiceApiException
         */
    InvoicePaymentModelDao createRefund(UUID paymentId, BigDecimal amount, boolean isInvoiceAdjusted, Map<UUID, BigDecimal> invoiceItemIdsWithAmounts,
                                        String transactionExternalKey, InternalCallContext context) throws InvoiceApiException;

    BigDecimal getRemainingAmountPaid(UUID invoicePaymentId, InternalTenantContext context);

    UUID getAccountIdFromInvoicePaymentId(UUID invoicePaymentId, InternalTenantContext context) throws InvoiceApiException;

    List<InvoicePaymentModelDao> getChargebacksByAccountId(UUID accountId, InternalTenantContext context);

    List<InvoicePaymentModelDao> getChargebacksByPaymentId(UUID paymentId, InternalTenantContext context);

    InvoicePaymentModelDao getChargebackById(UUID chargebackId, InternalTenantContext context) throws InvoiceApiException;

    /**
     * Retrieve am external charge by id.
     *
     * @param externalChargeId the external charge id
     * @return the external charge invoice item
     * @throws InvoiceApiException
     */
    InvoiceItemModelDao getExternalChargeById(UUID externalChargeId, InternalTenantContext context) throws InvoiceApiException;

    /**
     * Add one or more external charges to a given account.
     *
     * @param accountId     the account id
     * @param effectiveDate the effective date for newly created invoices (in the account timezone)
     * @param charges       the external charges
     * @param context       the call context
     * @return the newly created external charges invoice items
     */
    List<InvoiceItemModelDao> insertExternalCharges(UUID accountId, LocalDate effectiveDate, Iterable<InvoiceItemModelDao> charges, InternalCallContext context) throws InvoiceApiException;

    /**
     * Retrieve a credit by id.
     *
     * @param creditId the credit id
     * @return the credit invoice item
     * @throws InvoiceApiException
     */
    InvoiceItemModelDao getCreditById(UUID creditId, InternalTenantContext context) throws InvoiceApiException;

    /**
     * Add a credit to a given account and invoice. If invoiceId is null, a new invoice will be created.
     *
     * @param accountId     the account id
     * @param invoiceId     the invoice id
     * @param amount        the credit amount
     * @param effectiveDate the day to grant the credit, in the account timezone
     * @param currency      the credit currency
     * @param context       the call callcontext
     * @return the newly created credit invoice item
     */
    InvoiceItemModelDao insertCredit(UUID accountId, @Nullable UUID invoiceId, BigDecimal amount,
                                     LocalDate effectiveDate, Currency currency, InternalCallContext context);

    /**
     * Adjust an invoice item.
     *
     * @param accountId     the account id
     * @param invoiceId     the invoice id
     * @param invoiceItemId the invoice item id to adjust
     * @param effectiveDate adjustment effective date, in the account timezone
     * @param amount        the amount to adjust. Pass null to adjust the full amount of the original item
     * @param currency      the currency of the amount. Pass null to default to the original currency used
     * @param context       the call callcontext
     * @return the newly created adjustment item
     */
    InvoiceItemModelDao insertInvoiceItemAdjustment(UUID accountId, UUID invoiceId, UUID invoiceItemId, LocalDate effectiveDate,
                                                    @Nullable BigDecimal amount, @Nullable Currency currency, InternalCallContext context);

    /**
     * Delete a CBA item.
     *
     * @param accountId     the account id
     * @param invoiceId     the invoice id
     * @param invoiceItemId the invoice item id of the cba item to delete
     */
    void deleteCBA(UUID accountId, UUID invoiceId, UUID invoiceItemId, InternalCallContext context) throws InvoiceApiException;

    void notifyOfPayment(InvoicePaymentModelDao invoicePayment, InternalCallContext context);

    /**
     * @param accountId the account for which we need to rebalance the CBA
     * @param context   the callcontext
     */
    public void consumeExstingCBAOnAccountWithUnpaidInvoices(final UUID accountId, final InternalCallContext context);
}
