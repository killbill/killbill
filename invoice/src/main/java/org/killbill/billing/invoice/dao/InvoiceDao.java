/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.EntityDao;

public interface InvoiceDao extends EntityDao<InvoiceModelDao, Invoice, InvoiceApiException> {

    // Used by InvoiceDispatcher only for regular invoice runs
    void createInvoice(final InvoiceModelDao invoice,
                       final BillingEventSet billingEvents,
                       final Set<InvoiceTrackingModelDao> trackingIds,
                       final FutureAccountNotifications callbackDateTimePerSubscriptions,
                       final ExistingInvoiceMetadata existingInvoiceMetadata,
                       final InternalCallContext context);

    // Used by APIs, for HA, etc.
    List<InvoiceItemModelDao> createInvoices(final List<InvoiceModelDao> invoices,
                                             final BillingEventSet billingEvents,
                                             final Set<InvoiceTrackingModelDao> trackingIds,
                                             final InternalCallContext context);

    void setFutureAccountNotificationsForEmptyInvoice(final UUID accountId, final FutureAccountNotifications callbackDateTimePerSubscriptions,
                                                      final InternalCallContext context);

    void rescheduleInvoiceNotification(final UUID accountId, final DateTime nextRescheduleDt, final InternalCallContext context);

    InvoiceModelDao getByNumber(Integer number, InternalTenantContext context) throws InvoiceApiException;

    InvoiceModelDao getByInvoiceItem(final UUID uuid, final InternalTenantContext context) throws InvoiceApiException;

    List<InvoiceModelDao> getInvoicesByAccount(final Boolean includeVoidedInvoices, InternalTenantContext context);

    List<InvoiceModelDao> getInvoicesByAccount(final Boolean includeVoidedInvoices, LocalDate fromDate, LocalDate upToDate, InternalTenantContext context);

    List<InvoiceModelDao> getInvoicesBySubscription(UUID subscriptionId, InternalTenantContext context);

    Pagination<InvoiceModelDao> searchInvoices(String searchKey, Long offset, Long limit, InternalTenantContext context);

    UUID getInvoiceIdByPaymentId(UUID paymentId, InternalTenantContext context);

    List<InvoicePaymentModelDao> getInvoicePaymentsByPaymentId(UUID paymentId, InternalTenantContext context);

    List<InvoicePaymentModelDao> getInvoicePaymentsByAccount(InternalTenantContext context);

    List<InvoicePaymentModelDao> getInvoicePaymentsByInvoice(final UUID invoiceId, InternalTenantContext context);

    InvoicePaymentModelDao getInvoicePaymentByCookieId(String cookieId, InternalTenantContext internalTenantContext);

    InvoicePaymentModelDao getInvoicePayment(UUID invoicePaymentId, InternalTenantContext internalTenantContext);

    BigDecimal getAccountBalance(UUID accountId, InternalTenantContext context);

    BigDecimal getAccountCBA(UUID accountId, InternalTenantContext context);

    List<InvoiceModelDao> getUnpaidInvoicesByAccountId(UUID accountId, @Nullable LocalDate startDate, @Nullable LocalDate upToDate, InternalTenantContext context);

    // Include migrated invoices
    List<InvoiceModelDao> getAllInvoicesByAccount(final Boolean includeVoidedInvoices, InternalTenantContext context);

    InvoicePaymentModelDao postChargeback(UUID paymentId, final UUID paymentAttemptId, String chargebackTransactionExternalKey, BigDecimal amount, Currency currency, InternalCallContext context) throws InvoiceApiException;

    InvoicePaymentModelDao postChargebackReversal(UUID paymentId, final UUID paymentAttemptId, String chargebackTransactionExternalKey, InternalCallContext context) throws InvoiceApiException;

    InvoiceItemModelDao doCBAComplexity(InvoiceModelDao invoice, InternalCallContext context) throws InvoiceApiException;

    Map<UUID, BigDecimal> computeItemAdjustments(final String invoiceId,
                                                 final Map<UUID, BigDecimal> invoiceItemIdsWithNullAmounts,
                                                 final InternalTenantContext context) throws InvoiceApiException;

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
    InvoicePaymentModelDao createRefund(UUID paymentId, UUID paymentAttemptId, BigDecimal amount, boolean isInvoiceAdjusted, Map<UUID, BigDecimal> invoiceItemIdsWithAmounts,
                                        String transactionExternalKey, boolean success, InternalCallContext context) throws InvoiceApiException;

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
     * Retrieve a credit by id.
     *
     * @param creditId the credit id
     * @return the credit invoice item
     * @throws InvoiceApiException
     */
    InvoiceItemModelDao getCreditById(UUID creditId, InternalTenantContext context) throws InvoiceApiException;

    /**
     * Delete a CBA item.
     *
     * @param accountId     the account id
     * @param invoiceId     the invoice id
     * @param invoiceItemId the invoice item id of the cba item to delete
     */
    void deleteCBA(UUID accountId, UUID invoiceId, UUID invoiceItemId, InternalCallContext context) throws InvoiceApiException;

    void notifyOfPaymentInit(InvoicePaymentModelDao invoicePayment, UUID paymentAttemptId, InternalCallContext context);

    void notifyOfPaymentCompletion(InvoicePaymentModelDao invoicePayment, UUID paymentAttemptId, InternalCallContext context);

    /**
     * @param accountId the account for which we need to rebalance the CBA
     * @param context   the callcontext
     */
    void consumeExstingCBAOnAccountWithUnpaidInvoices(final UUID accountId, final InternalCallContext context);

    /**
     * Update invoice status
     *
     * @param invoiceId the invoice id
     * @param newState the new invoice state
     * @param context the tenant context
     * @throws InvoiceApiException if any unexpected error occurs
     */
    void changeInvoiceStatus(UUID invoiceId, InvoiceStatus newState, InternalCallContext context) throws InvoiceApiException;

    /**
     * Save parent/child invoice relationship
     *
     * @param invoiceRelation the invoice relation object
     * @param context the tenant context
     * @throws InvoiceApiException if any unexpected error occurs
     */
    void createParentChildInvoiceRelation(final InvoiceParentChildModelDao invoiceRelation, final InternalCallContext context) throws InvoiceApiException;

    /**
     * Get parent/child invoice relationship by parent invoice id
     *
     * @param parentInvoiceId the parent invoice id
     * @param context the tenant context
     * @return a list of parent-children relation
     * @throws InvoiceApiException if any unexpected error occurs
     */
    List<InvoiceParentChildModelDao> getChildInvoicesByParentInvoiceId(UUID parentInvoiceId, final InternalCallContext context) throws InvoiceApiException;

    /**
     * Retrieve parent invoice by the parent account id
     *
     * @param parentAccountId the parent account id
     * @param context the tenant context
     * @return a parent invoice in DRAFT status
     * @throws InvoiceApiException if any unexpected error occurs
     */
    InvoiceModelDao getParentDraftInvoice(UUID parentAccountId, InternalCallContext context) throws InvoiceApiException;

    /**
     * Update invoice item amount
     *
     * @param invoiceItemId the invoice item id
     * @param amount the new amount value
     * @param context the tenant context
     * @throws InvoiceApiException if any unexpected error occurs
     */
    void updateInvoiceItemAmount(UUID invoiceItemId, BigDecimal amount, InternalCallContext context) throws InvoiceApiException;

    /**
     * Move a given child credit to the parent level
     *
     * @param childAccount the child account
     * @param childAccountContext the tenant context for the child account id
     * @throws InvoiceApiException if any unexpected error occurs
     */
    void transferChildCreditToParent(Account childAccount, InternalCallContext childAccountContext) throws InvoiceApiException;

    /**
     * Retrieve invoice items details associated to Parent SUMMARY invoice item
     *
     * @param parentInvoiceId the parent invoice id
     * @param context the tenant context
     * @return a list of invoice items associated with a parent invoice
     * @throws InvoiceApiException if any unexpected error occurs
     */
    List<InvoiceItemModelDao> getInvoiceItemsByParentInvoice(UUID parentInvoiceId, final InternalTenantContext context) throws InvoiceApiException;

    List<InvoiceTrackingModelDao> getTrackingsByDateRange(LocalDate startDate, LocalDate endDate, InternalCallContext context);

    List<AuditLogWithHistory> getInvoiceAuditLogsWithHistoryForId(final UUID invoiceId, final AuditLevel auditLevel, final InternalTenantContext context);

    List<AuditLogWithHistory> getInvoiceItemAuditLogsWithHistoryForId(final UUID invoiceItemId, final AuditLevel auditLevel, final InternalTenantContext context);

    List<AuditLogWithHistory> getInvoicePaymentAuditLogsWithHistoryForId(final UUID invoicePaymentId, final AuditLevel auditLevel, final InternalTenantContext context);

}
