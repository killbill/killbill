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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.exceptions.TransactionFailedException;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePayment.InvoicePaymentType;
import com.ning.billing.invoice.api.user.DefaultInvoiceAdjustmentEvent;
import com.ning.billing.invoice.model.CreditAdjInvoiceItem;
import com.ning.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.DefaultInvoicePayment;
import com.ning.billing.invoice.model.ExternalChargeInvoiceItem;
import com.ning.billing.invoice.model.ItemAdjInvoiceItem;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.invoice.model.RefundAdjInvoiceItem;
import com.ning.billing.invoice.notification.NextBillingDatePoster;
import com.ning.billing.util.ChangeType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.EntityAudit;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.svcsapi.bus.Bus;
import com.ning.billing.util.svcsapi.bus.Bus.EventBusException;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.inject.Inject;

public class AuditedInvoiceDao implements InvoiceDao {

    private static final Logger log = LoggerFactory.getLogger(AuditedInvoiceDao.class);

    private final InvoiceSqlDao invoiceSqlDao;
    private final InvoicePaymentSqlDao invoicePaymentSqlDao;
    private final NextBillingDatePoster nextBillingDatePoster;
    private final InvoiceItemSqlDao invoiceItemSqlDao;
    private final Clock clock;
    private final Bus eventBus;

    @Inject
    public AuditedInvoiceDao(final IDBI dbi,
            final NextBillingDatePoster nextBillingDatePoster,
            final Clock clock,
            final Bus eventBus) {
        this.invoiceSqlDao = dbi.onDemand(InvoiceSqlDao.class);
        this.invoicePaymentSqlDao = dbi.onDemand(InvoicePaymentSqlDao.class);
        this.invoiceItemSqlDao = dbi.onDemand(InvoiceItemSqlDao.class);
        this.nextBillingDatePoster = nextBillingDatePoster;
        this.clock = clock;
        this.eventBus = eventBus;
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId, final InternalTenantContext context) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                final List<Invoice> invoices = invoiceDao.getInvoicesByAccount(accountId.toString(), context);
                populateChildren(invoices, invoiceDao, context);
                return invoices;
            }
        });
    }

    @Override
    public List<Invoice> getAllInvoicesByAccount(final UUID accountId, final InternalTenantContext context) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                return getAllInvoicesByAccountFromTransaction(accountId, invoiceDao, context);
            }
        });
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId, final LocalDate fromDate, final InternalTenantContext context) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                final List<Invoice> invoices = invoiceDao.getInvoicesByAccountAfterDate(accountId.toString(),
                                                                                        fromDate.toDateTimeAtStartOfDay().toDate(),
                                                                                        context);

                populateChildren(invoices, invoiceDao, context);

                return invoices;
            }
        });
    }

    @Override
    public List<Invoice> get(final InternalTenantContext context) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                final List<Invoice> invoices = invoiceDao.get(context);

                populateChildren(invoices, invoiceDao, context);

                return invoices;
            }
        });
    }

    @Override
    public Invoice getById(final UUID invoiceId, final InternalTenantContext context) throws InvoiceApiException {
        try {
            return invoiceSqlDao.inTransaction(new Transaction<Invoice, InvoiceSqlDao>() {
                @Override
                public Invoice inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                    final Invoice invoice = invoiceDao.getById(invoiceId.toString(), context);
                    if (invoice == null) {
                        throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
                    }
                    populateChildren(invoice, invoiceDao, context);
                    return invoice;
                }
            });
        } catch (TransactionFailedException e) {
            if (e.getCause() instanceof InvoiceApiException) {
                throw (InvoiceApiException) e.getCause();
            } else {
                throw e;
            }
        }
    }

    @Override
    public Invoice getByNumber(final Integer number, final InternalTenantContext context) throws InvoiceApiException {
        // The invoice number is just the record id
        final Invoice result = invoiceSqlDao.getByRecordId(number.longValue(), context);
        if (result == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, number);
        }
        return result;
    }

    @Override
    public void create(final Invoice invoice, final int billCycleDayUTC, final boolean isRealInvoice, final InternalCallContext context) {
        invoiceSqlDao.inTransaction(new Transaction<Void, InvoiceSqlDao>() {
            @Override
            public Void inTransaction(final InvoiceSqlDao transactional, final TransactionStatus status) throws Exception {
                final Invoice currentInvoice = transactional.getById(invoice.getId().toString(), context);
                if (currentInvoice == null) {
                    final List<EntityAudit> audits = new ArrayList<EntityAudit>();

                    // We only want to insert that invoice if there are real invoiceItems associated to it-- if not, this is just
                    // a shell invoice and we only need to insert the invoiceItems-- for the already existing invoices

                    if (isRealInvoice) {
                        transactional.create(invoice, context);
                        final Long recordId = transactional.getRecordId(invoice.getId().toString(), context);
                        audits.add(new EntityAudit(TableName.INVOICES, recordId, ChangeType.INSERT));
                    }

                    List<Long> recordIdList;

                    final List<InvoiceItem> invoiceItems = invoice.getInvoiceItems();
                    final InvoiceItemSqlDao transInvoiceItemSqlDao = transactional.become(InvoiceItemSqlDao.class);
                    transInvoiceItemSqlDao.batchCreateFromTransaction(invoiceItems, context);
                    recordIdList = transInvoiceItemSqlDao.getRecordIds(invoice.getId().toString(), context);
                    audits.addAll(createAudits(TableName.INVOICE_ITEMS, recordIdList));

                    final List<InvoiceItem> recurringInvoiceItems = invoice.getInvoiceItems(RecurringInvoiceItem.class);
                    notifyOfFutureBillingEvents(transactional, invoice.getAccountId(), recurringInvoiceItems);

                    final List<InvoicePayment> invoicePayments = invoice.getPayments();
                    final InvoicePaymentSqlDao invoicePaymentSqlDao = transactional.become(InvoicePaymentSqlDao.class);
                    invoicePaymentSqlDao.batchCreateFromTransaction(invoicePayments, context);
                    recordIdList = invoicePaymentSqlDao.getRecordIds(invoice.getId().toString(), context);
                    audits.addAll(createAudits(TableName.INVOICE_PAYMENTS, recordIdList));

                    transactional.insertAuditFromTransaction(audits, context);
                }
                return null;
            }
        });
    }

    private List<EntityAudit> createAudits(final TableName tableName, final List<Long> recordIdList) {
        final List<EntityAudit> entityAuditList = new ArrayList<EntityAudit>();
        for (final Long recordId : recordIdList) {
            entityAuditList.add(new EntityAudit(tableName, recordId, ChangeType.INSERT));
        }

        return entityAuditList;
    }

    @Override
    public List<Invoice> getInvoicesBySubscription(final UUID subscriptionId, final InternalTenantContext context) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {
                final List<Invoice> invoices = invoiceDao.getInvoicesBySubscription(subscriptionId.toString(), context);

                populateChildren(invoices, invoiceDao, context);

                return invoices;
            }
        });
    }

    @Override
    public BigDecimal getAccountBalance(final UUID accountId, final InternalTenantContext context) {

        return invoiceSqlDao.inTransaction(new Transaction<BigDecimal, InvoiceSqlDao>() {
            @Override
            public BigDecimal inTransaction(final InvoiceSqlDao transactional, final TransactionStatus status) throws Exception {
                BigDecimal cba = BigDecimal.ZERO;

                BigDecimal accountBalance = BigDecimal.ZERO;
                final List<Invoice> invoices = getAllInvoicesByAccountFromTransaction(accountId, transactional, context);
                for (final Invoice cur : invoices) {
                    accountBalance = accountBalance.add(cur.getBalance());
                    cba = cba.add(cur.getCBAAmount());
                }
                return accountBalance.subtract(cba);
            }
        });
    }

    @Override
    public BigDecimal getAccountCBA(final UUID accountId, final InternalTenantContext context) {
        return invoiceSqlDao.inTransaction(new Transaction<BigDecimal, InvoiceSqlDao>() {
            @Override
            public BigDecimal inTransaction(final InvoiceSqlDao transactional, final TransactionStatus status) throws Exception {
                return getAccountCBAFromTransaction(accountId, transactional, context);
            }
        });
    }

    @Override
    public void notifyOfPayment(final InvoicePayment invoicePayment, final InternalCallContext context) {
        invoicePaymentSqlDao.inTransaction(new Transaction<Void, InvoicePaymentSqlDao>() {
            @Override
            public Void inTransaction(final InvoicePaymentSqlDao transactional, final TransactionStatus status) throws Exception {
                transactional.notifyOfPayment(invoicePayment, context);

                final String invoicePaymentId = invoicePayment.getId().toString();
                final Long recordId = transactional.getRecordId(invoicePaymentId, context);
                final EntityAudit audit = new EntityAudit(TableName.INVOICE_PAYMENTS, recordId, ChangeType.INSERT);
                transactional.insertAuditFromTransaction(audit, context);

                return null;
            }
        });
    }

    @Override
    public List<Invoice> getUnpaidInvoicesByAccountId(final UUID accountId, @Nullable final LocalDate upToDate, final InternalTenantContext context) {
        return invoiceSqlDao.inTransaction(new Transaction<List<Invoice>, InvoiceSqlDao>() {
            @Override
            public List<Invoice> inTransaction(final InvoiceSqlDao invoiceDao, final TransactionStatus status) throws Exception {

                final List<Invoice> invoices = getAllInvoicesByAccountFromTransaction(accountId, invoiceDao, context);
                final Collection<Invoice> unpaidInvoices = Collections2.filter(invoices, new Predicate<Invoice>() {
                    @Override
                    public boolean apply(final Invoice in) {
                        return (in.getBalance().compareTo(BigDecimal.ZERO) >= 1) && (upToDate == null || !in.getTargetDate().isAfter(upToDate));
                    }
                });
                return new ArrayList<Invoice>(unpaidInvoices);
            }
        });
    }

    @Override
    public UUID getInvoiceIdByPaymentId(final UUID paymentId, final InternalTenantContext context) {
        return invoiceSqlDao.getInvoiceIdByPaymentId(paymentId.toString(), context);
    }

    @Override
    public List<InvoicePayment> getInvoicePayments(final UUID paymentId, final InternalTenantContext context) {
        return invoicePaymentSqlDao.getInvoicePayments(paymentId.toString(), context);
    }

    @Override

    public InvoicePayment createRefund(final UUID paymentId, final BigDecimal requestedRefundAmount, final boolean isInvoiceAdjusted,
                                       final Map<UUID, BigDecimal> invoiceItemIdsWithNullAmounts, final UUID paymentCookieId,
                                       final InternalCallContext context)
            throws InvoiceApiException {
        final boolean isInvoiceItemAdjusted = isInvoiceAdjusted && invoiceItemIdsWithNullAmounts.size() > 0;

        return invoicePaymentSqlDao.inTransaction(new Transaction<InvoicePayment, InvoicePaymentSqlDao>() {
            @Override
            public InvoicePayment inTransaction(final InvoicePaymentSqlDao transactional, final TransactionStatus status) throws Exception {
                final List<EntityAudit> audits = new ArrayList<EntityAudit>();

                final InvoiceSqlDao transInvoiceDao = transactional.become(InvoiceSqlDao.class);

                final InvoicePayment payment = transactional.getByPaymentId(paymentId.toString(), context);
                if (payment == null) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_PAYMENT_BY_ATTEMPT_NOT_FOUND, paymentId);
                }

                // Retrieve the amounts to adjust, if needed
                final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts = computeItemAdjustments(payment.getInvoiceId().toString(),
                                                                                               transInvoiceDao,
                                                                                               invoiceItemIdsWithNullAmounts,
                                                                                               context);

                // Compute the actual amount to refund
                final BigDecimal requestedPositiveAmount = computePositiveRefundAmount(payment, requestedRefundAmount, invoiceItemIdsWithAmounts);

                // Before we go further, check if that refund already got inserted -- the payment system keeps a state machine
                // and so this call may be called several time for the same  paymentCookieId (which is really the refundId)
                final InvoicePayment existingRefund = transactional.getPaymentsForCookieId(paymentCookieId.toString(), context);
                if (existingRefund != null) {
                    return existingRefund;
                }

                final InvoicePayment refund = new DefaultInvoicePayment(UUID.randomUUID(), InvoicePaymentType.REFUND, paymentId,
                        payment.getInvoiceId(), context.getCreatedDate(), requestedPositiveAmount.negate(),
                        payment.getCurrency(), paymentCookieId, payment.getId());
                transactional.create(refund, context);
                final Long refundRecordId = transactional.getRecordId(refund.getId().toString(), context);
                audits.add(new EntityAudit(TableName.REFUNDS, refundRecordId, ChangeType.INSERT));

                // Retrieve invoice after the Refund
                final Invoice invoice = transInvoiceDao.getById(payment.getInvoiceId().toString(), context);
                if (invoice != null) {
                    populateChildren(invoice, transInvoiceDao, context);
                } else {
                    throw new IllegalStateException("Invoice shouldn't be null for payment " + payment.getId());
                }

                final BigDecimal invoiceBalanceAfterRefund = invoice.getBalance();
                final InvoiceItemSqlDao transInvoiceItemDao = transInvoiceDao.become(InvoiceItemSqlDao.class);

                // At this point, we created the refund which made the invoice balance positive and applied any existing
                // available CBA to that invoice.
                // We now need to adjust the invoice and/or invoice items if needed and specified.
                if (isInvoiceAdjusted && !isInvoiceItemAdjusted) {
                    // Invoice adjustment
                    final BigDecimal maxBalanceToAdjust = (invoiceBalanceAfterRefund.compareTo(BigDecimal.ZERO) <= 0) ? BigDecimal.ZERO : invoiceBalanceAfterRefund;
                    final BigDecimal requestedPositiveAmountToAdjust = requestedPositiveAmount.compareTo(maxBalanceToAdjust) > 0 ? maxBalanceToAdjust : requestedPositiveAmount;
                    if (requestedPositiveAmountToAdjust.compareTo(BigDecimal.ZERO) > 0) {
                        final InvoiceItem adjItem = new RefundAdjInvoiceItem(invoice.getId(), invoice.getAccountId(), context.getCreatedDate().toLocalDate(), requestedPositiveAmountToAdjust.negate(), invoice.getCurrency());
                        transInvoiceItemDao.create(adjItem, context);
                        final Long adjItemRecordId = transInvoiceItemDao.getRecordId(adjItem.getId().toString(), context);
                        audits.add(new EntityAudit(TableName.INVOICE_ITEMS, adjItemRecordId, ChangeType.INSERT));
                    }
                } else if (isInvoiceAdjusted) {
                    // Invoice item adjustment
                    for (final UUID invoiceItemId : invoiceItemIdsWithAmounts.keySet()) {
                        final BigDecimal adjAmount = invoiceItemIdsWithAmounts.get(invoiceItemId);
                        final InvoiceItem item = createAdjustmentItem(transInvoiceDao, invoice.getId(), invoiceItemId, adjAmount,
                                                                      invoice.getCurrency(), context.getCreatedDate().toLocalDate(),
                                                                      context);
                        transInvoiceItemDao.create(item, context);
                        final Long itemRecordId = transInvoiceItemDao.getRecordId(item.getId().toString(), context);
                        audits.add(new EntityAudit(TableName.INVOICE_ITEMS, itemRecordId, ChangeType.INSERT));
                    }
                }

                // Notify the bus since the balance of the invoice changed
                notifyBusOfInvoiceAdjustment(transactional, invoice.getId(), invoice.getAccountId(), context.getUserToken(), context);

                // Save audit logs
                transactional.insertAuditFromTransaction(audits, context);

                return refund;
            }
        });
    }

    /**
     * Find amounts to adjust for individual items, if not specified.
     * The user gives us a list of items to adjust associated with a given amount (how much to refund per invoice item).
     * In case of full adjustments, the amount can be null: in this case, we retrieve the original amount for the invoice
     * item.
     *
     * @param invoiceId                     original invoice id
     * @param transInvoiceDao               the transactional InvoiceSqlDao
     * @param invoiceItemIdsWithNullAmounts the original mapping between invoice item ids and amount to refund (contains null)
     * @param context                       the tenant context
     * @return the final mapping between invoice item ids and amount to refund
     * @throws InvoiceApiException
     */
    private Map<UUID, BigDecimal> computeItemAdjustments(final String invoiceId,
                                                         final InvoiceSqlDao transInvoiceDao,
                                                         final Map<UUID, BigDecimal> invoiceItemIdsWithNullAmounts,
                                                         final InternalTenantContext context) throws InvoiceApiException {
        // Populate the missing amounts for individual items, if needed
        final Builder<UUID, BigDecimal> invoiceItemIdsWithAmountsBuilder = new Builder<UUID, BigDecimal>();
        if (invoiceItemIdsWithNullAmounts.size() == 0) {
            return invoiceItemIdsWithAmountsBuilder.build();
        }

        // Retrieve invoice before the Refund
        final Invoice invoice = transInvoiceDao.getById(invoiceId, context);
        if (invoice != null) {
            populateChildren(invoice, transInvoiceDao, context);
        } else {
            throw new IllegalStateException("Invoice shouldn't be null for id " + invoiceId);
        }

        for (final UUID invoiceItemId : invoiceItemIdsWithNullAmounts.keySet()) {
            final BigDecimal adjAmount = Objects.firstNonNull(invoiceItemIdsWithNullAmounts.get(invoiceItemId),
                    getInvoiceItemAmountForId(invoice, invoiceItemId));
            final BigDecimal adjAmountRemainingAfterRepair = computeItemAdjustmentAmount(invoiceItemId, adjAmount, invoice.getInvoiceItems());
            if (adjAmountRemainingAfterRepair.compareTo(BigDecimal.ZERO) > 0) {
                invoiceItemIdsWithAmountsBuilder.put(invoiceItemId,  adjAmountRemainingAfterRepair);
            }
        }

        return invoiceItemIdsWithAmountsBuilder.build();
    }

    /**
     *
     * @param invoiceItem                          item we are adjusting
     * @param requestedPositiveAmountToAdjust      amount we are adjusting for that item
     * @param invoiceItems                         list of all invoice items on this invoice
     * @return                                     the amount we should really adjust based on whether or not the item got repaired
     */
    private BigDecimal computeItemAdjustmentAmount(final UUID invoiceItem, final BigDecimal requestedPositiveAmountToAdjust, final List<InvoiceItem> invoiceItems) {

        BigDecimal positiveRepairedAmount = BigDecimal.ZERO;

        final Collection<InvoiceItem> repairedItems = Collections2.filter(invoiceItems, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(@Nullable InvoiceItem input) {
                return (input.getInvoiceItemType() == InvoiceItemType.REPAIR_ADJ && input.getLinkedItemId().equals(invoiceItem));
            }
        });
        for (InvoiceItem cur : repairedItems) {
            // Repair item are negative so we negate to make it positive
            positiveRepairedAmount = positiveRepairedAmount.add(cur.getAmount().negate());
        }
        return (positiveRepairedAmount.compareTo(requestedPositiveAmountToAdjust) >= 0) ? BigDecimal.ZERO : requestedPositiveAmountToAdjust.subtract(positiveRepairedAmount);
    }


    private BigDecimal getInvoiceItemAmountForId(final Invoice invoice, final UUID invoiceItemId) throws InvoiceApiException {
        for (final InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
            if (invoiceItem.getId().equals(invoiceItemId)) {
                return invoiceItem.getAmount();
            }
        }

        throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_NOT_FOUND, invoiceItemId);
    }

    @VisibleForTesting
    BigDecimal computePositiveRefundAmount(final InvoicePayment payment, final BigDecimal requestedAmount, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts) throws InvoiceApiException {
        final BigDecimal maxRefundAmount = payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
        final BigDecimal requestedPositiveAmount = requestedAmount == null ? maxRefundAmount : requestedAmount;
        // This check is good but not enough, we need to also take into account previous refunds
        // (But that should have been checked in the payment call already)
        if (requestedPositiveAmount.compareTo(maxRefundAmount) > 0) {
            throw new InvoiceApiException(ErrorCode.REFUND_AMOUNT_TOO_HIGH, requestedPositiveAmount, maxRefundAmount);
        }

        // Verify if the requested amount matches the invoice items to adjust, if specified
        BigDecimal amountFromItems = BigDecimal.ZERO;
        for (final BigDecimal itemAmount : invoiceItemIdsWithAmounts.values()) {
            amountFromItems = amountFromItems.add(itemAmount);
        }

        // Sanity check: if some items were specified, then the sum should be equal to specified refund amount, if specified
        if (amountFromItems.compareTo(BigDecimal.ZERO) != 0 && requestedPositiveAmount.compareTo(amountFromItems) != 0) {
            throw new InvoiceApiException(ErrorCode.REFUND_AMOUNT_DONT_MATCH_ITEMS_TO_ADJUST, requestedPositiveAmount, amountFromItems);
        }
        return requestedPositiveAmount;
    }

    @Override
    public InvoicePayment postChargeback(final UUID invoicePaymentId, final BigDecimal amount, final InternalCallContext context) throws InvoiceApiException {

        return invoicePaymentSqlDao.inTransaction(new Transaction<InvoicePayment, InvoicePaymentSqlDao>() {
            @Override
            public InvoicePayment inTransaction(final InvoicePaymentSqlDao transactional, final TransactionStatus status) throws Exception {
                final BigDecimal maxChargedBackAmount = getRemainingAmountPaidFromTransaction(invoicePaymentId, transactional, context);
                final BigDecimal requestedChargedBackAmout = (amount == null) ? maxChargedBackAmount : amount;
                if (requestedChargedBackAmout.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new InvoiceApiException(ErrorCode.CHARGE_BACK_AMOUNT_IS_NEGATIVE);
                }
                if (requestedChargedBackAmout.compareTo(maxChargedBackAmount) > 0) {
                    throw new InvoiceApiException(ErrorCode.CHARGE_BACK_AMOUNT_TOO_HIGH, requestedChargedBackAmout, maxChargedBackAmount);
                }

                final InvoicePayment payment = invoicePaymentSqlDao.getById(invoicePaymentId.toString(), context);
                if (payment == null) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_PAYMENT_NOT_FOUND, invoicePaymentId.toString());
                } else {
                    final InvoicePayment chargeBack = new DefaultInvoicePayment(UUID.randomUUID(), InvoicePaymentType.CHARGED_BACK, payment.getPaymentId(),
                            payment.getInvoiceId(), context.getCreatedDate(), requestedChargedBackAmout.negate(), payment.getCurrency(), null, payment.getId());
                    transactional.create(chargeBack, context);

                    // Add audit
                    final Long recordId = transactional.getRecordId(chargeBack.getId().toString(), context);
                    final EntityAudit audit = new EntityAudit(TableName.INVOICE_PAYMENTS, recordId, ChangeType.INSERT);
                    transactional.insertAuditFromTransaction(audit, context);

                    // Notify the bus since the balance of the invoice changed
                    final UUID accountId = transactional.getAccountIdFromInvoicePaymentId(chargeBack.getId().toString(), context);
                    notifyBusOfInvoiceAdjustment(transactional, payment.getInvoiceId(), accountId, context.getUserToken(), context);

                    return chargeBack;
                }
            }
        });
    }

    @Override
    public BigDecimal getRemainingAmountPaid(final UUID invoicePaymentId, final InternalTenantContext context) {
        return getRemainingAmountPaidFromTransaction(invoicePaymentId, invoicePaymentSqlDao, context);
    }

    @Override
    public UUID getAccountIdFromInvoicePaymentId(final UUID invoicePaymentId, final InternalTenantContext context) throws InvoiceApiException {
        final UUID accountId = invoicePaymentSqlDao.getAccountIdFromInvoicePaymentId(invoicePaymentId.toString(), context);
        if (accountId == null) {
            throw new InvoiceApiException(ErrorCode.CHARGE_BACK_COULD_NOT_FIND_ACCOUNT_ID, invoicePaymentId);
        } else {
            return accountId;
        }
    }

    @Override
    public List<InvoicePayment> getChargebacksByAccountId(final UUID accountId, final InternalTenantContext context) {
        return invoicePaymentSqlDao.getChargeBacksByAccountId(accountId.toString(), context);
    }

    @Override
    public List<InvoicePayment> getChargebacksByPaymentId(final UUID paymentId, final InternalTenantContext context) {
        return invoicePaymentSqlDao.getChargebacksByPaymentId(paymentId.toString(), context);
    }

    @Override
    public InvoicePayment getChargebackById(final UUID chargebackId, final InternalTenantContext context) throws InvoiceApiException {
        final InvoicePayment chargeback = invoicePaymentSqlDao.getById(chargebackId.toString(), context);
        if (chargeback == null) {
            throw new InvoiceApiException(ErrorCode.CHARGE_BACK_DOES_NOT_EXIST, chargebackId);
        } else {
            return chargeback;
        }
    }

    @Override
    public InvoiceItem getExternalChargeById(final UUID externalChargeId, final InternalTenantContext context) throws InvoiceApiException {
        return invoiceItemSqlDao.getById(externalChargeId.toString(), context);
    }

    @Override
    public InvoiceItem insertExternalCharge(final UUID accountId, @Nullable final UUID invoiceId, @Nullable final UUID bundleId, final String description,
                                            final BigDecimal amount, final LocalDate effectiveDate, final Currency currency, final InternalCallContext context)
            throws InvoiceApiException {
        return invoiceSqlDao.inTransaction(new Transaction<InvoiceItem, InvoiceSqlDao>() {
            @Override
            public InvoiceItem inTransaction(final InvoiceSqlDao transactional, final TransactionStatus status) throws Exception {
                final List<EntityAudit> audits = new ArrayList<EntityAudit>();

                UUID invoiceIdForExternalCharge = invoiceId;
                // Create an invoice for that external charge if it doesn't exist
                if (invoiceIdForExternalCharge == null) {
                    final Invoice invoiceForExternalCharge = new DefaultInvoice(accountId, effectiveDate, effectiveDate, currency);
                    transactional.create(invoiceForExternalCharge, context);
                    final Long invoiceRecordId = transactional.getRecordId(invoiceForExternalCharge.getId().toString(), context);
                    audits.add(new EntityAudit(TableName.INVOICES, invoiceRecordId, ChangeType.INSERT));

                    invoiceIdForExternalCharge = invoiceForExternalCharge.getId();
                }

                final InvoiceItem externalCharge = new ExternalChargeInvoiceItem(invoiceIdForExternalCharge, accountId,
                        bundleId, description,
                        effectiveDate, amount, currency);

                final InvoiceItemSqlDao transInvoiceItemDao = transactional.become(InvoiceItemSqlDao.class);
                transInvoiceItemDao.create(externalCharge, context);
                final Long invoiceItemRecordId = transInvoiceItemDao.getRecordId(externalCharge.getId().toString(), context);
                audits.add(new EntityAudit(TableName.INVOICE_ITEMS, invoiceItemRecordId, ChangeType.INSERT));

                // At this point, reread the invoice and figure out if we need to consume some of the CBA
                final Invoice invoice = transactional.getById(invoiceIdForExternalCharge.toString(), context);
                if (invoice == null) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceIdForExternalCharge);
                }
                populateChildren(invoice, transactional, context);

                final BigDecimal accountCbaAvailable = getAccountCBAFromTransaction(invoice.getAccountId(), transactional, context);
                if (accountCbaAvailable.compareTo(BigDecimal.ZERO) > 0 && invoice.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                    final BigDecimal cbaAmountToConsume = accountCbaAvailable.compareTo(invoice.getBalance()) > 0 ? invoice.getBalance().negate() : accountCbaAvailable.negate();
                    final InvoiceItem cbaAdjItem = new CreditBalanceAdjInvoiceItem(invoice.getId(), invoice.getAccountId(), context.getCreatedDate().toLocalDate(), cbaAmountToConsume, invoice.getCurrency());
                    transInvoiceItemDao.create(cbaAdjItem, context);
                    final Long cbaAdjItemRecordId = transInvoiceItemDao.getRecordId(cbaAdjItem.getId().toString(), context);
                    audits.add(new EntityAudit(TableName.INVOICE_ITEMS, cbaAdjItemRecordId, ChangeType.INSERT));
                }

                // Notify the bus since the balance of the invoice changed
                notifyBusOfInvoiceAdjustment(transactional, invoiceId, accountId, context.getUserToken(), context);

                // Save audit logs
                transactional.insertAuditFromTransaction(audits, context);

                return externalCharge;
            }
        });
    }

    @Override
    public InvoiceItem getCreditById(final UUID creditId, final InternalTenantContext context) throws InvoiceApiException {
        return invoiceItemSqlDao.getById(creditId.toString(), context);
    }

    @Override
    public InvoiceItem insertCredit(final UUID accountId, @Nullable final UUID invoiceId, final BigDecimal positiveCreditAmount,
                                    final LocalDate effectiveDate, final Currency currency, final InternalCallContext context) {
        return invoiceSqlDao.inTransaction(new Transaction<InvoiceItem, InvoiceSqlDao>() {
            @Override
            public InvoiceItem inTransaction(final InvoiceSqlDao transactional, final TransactionStatus status) throws Exception {
                final List<EntityAudit> audits = new ArrayList<EntityAudit>();

                UUID invoiceIdForCredit = invoiceId;
                // Create an invoice for that credit if it doesn't exist
                if (invoiceIdForCredit == null) {
                    final Invoice invoiceForCredit = new DefaultInvoice(accountId, effectiveDate, effectiveDate, currency);
                    transactional.create(invoiceForCredit, context);
                    final Long invoiceForCreditRecordId = transactional.getRecordId(invoiceForCredit.getId().toString(), context);
                    audits.add(new EntityAudit(TableName.INVOICES, invoiceForCreditRecordId, ChangeType.INSERT));

                    invoiceIdForCredit = invoiceForCredit.getId();
                }

                // Note! The amount is negated here!
                final InvoiceItem credit = new CreditAdjInvoiceItem(invoiceIdForCredit, accountId, effectiveDate, positiveCreditAmount.negate(), currency);
                insertItemAndAddCBAIfNeeded(transactional, credit, audits, context);

                // Notify the bus since the balance of the invoice changed
                notifyBusOfInvoiceAdjustment(transactional, invoiceId, accountId, context.getUserToken(), context);

                // Save audit logs
                transactional.insertAuditFromTransaction(audits, context);

                return credit;
            }
        });
    }

    @Override
    public InvoiceItem insertInvoiceItemAdjustment(final UUID accountId, final UUID invoiceId, final UUID invoiceItemId,
                                                   final LocalDate effectiveDate, @Nullable final BigDecimal positiveAdjAmount,
                                                   @Nullable final Currency currency, final InternalCallContext context) {
        return invoiceSqlDao.inTransaction(new Transaction<InvoiceItem, InvoiceSqlDao>() {
            @Override
            public InvoiceItem inTransaction(final InvoiceSqlDao transactional, final TransactionStatus status) throws Exception {
                final List<EntityAudit> audits = new ArrayList<EntityAudit>();
                final InvoiceItem invoiceItemAdjustment = createAdjustmentItem(transactional, invoiceId, invoiceItemId, positiveAdjAmount,
                                                                               currency, effectiveDate, context);
                insertItemAndAddCBAIfNeeded(transactional, invoiceItemAdjustment, audits, context);
                notifyBusOfInvoiceAdjustment(transactional, invoiceId, accountId, context.getUserToken(), context);

                // Save audit logs
                transactional.insertAuditFromTransaction(audits, context);

                return invoiceItemAdjustment;
            }
        });
    }

    @Override
    public void deleteCBA(final UUID accountId, final UUID invoiceId, final UUID invoiceItemId, final InternalCallContext context) throws InvoiceApiException {
        invoiceSqlDao.inTransaction(new Transaction<Void, InvoiceSqlDao>() {
            @Override
            public Void inTransaction(final InvoiceSqlDao transactional, final TransactionStatus status) throws Exception {
                final List<EntityAudit> audits = new ArrayList<EntityAudit>();

                // Retrieve the invoice and make sure it belongs to the right account
                final Invoice invoice = transactional.getById(invoiceId.toString(), context);
                if (invoice == null || !invoice.getAccountId().equals(accountId)) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
                }

                // Retrieve the invoice item and make sure it belongs to the right invoice
                final InvoiceItemSqlDao invoiceItemSqlDao = transactional.become(InvoiceItemSqlDao.class);
                final InvoiceItem cbaItem = invoiceItemSqlDao.getById(invoiceItemId.toString(), context);
                if (cbaItem == null || !cbaItem.getInvoiceId().equals(invoice.getId())) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_NOT_FOUND, invoiceItemId);
                }

                // First, adjust the same invoice with the CBA amount to "delete"
                final InvoiceItem cbaAdjItem = new CreditBalanceAdjInvoiceItem(invoice.getId(), invoice.getAccountId(), context.getCreatedDate().toLocalDate(),
                        cbaItem.getId(), cbaItem.getAmount().negate(), cbaItem.getCurrency());
                invoiceItemSqlDao.create(cbaAdjItem, context);
                final Long cbaAdjItemRecordId = invoiceItemSqlDao.getRecordId(cbaAdjItem.getId().toString(), context);
                audits.add(new EntityAudit(TableName.INVOICE_ITEMS, cbaAdjItemRecordId, ChangeType.INSERT));

                // Verify the final invoice balance is not negative
                populateChildren(invoice, transactional, context);
                if (invoice.getBalance().compareTo(BigDecimal.ZERO) < 0) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_WOULD_BE_NEGATIVE);
                }

                // If there is more account credit than CBA we adjusted, we're done.
                // Otherwise, we need to find further invoices on which this credit was consumed
                final BigDecimal accountCBA = getAccountCBAFromTransaction(accountId, invoiceSqlDao, context);
                if (accountCBA.compareTo(BigDecimal.ZERO) < 0) {
                    if (accountCBA.compareTo(cbaItem.getAmount().negate()) < 0) {
                        throw new IllegalStateException("The account balance can't be lower than the amount adjusted");
                    }
                    final List<Invoice> invoicesFollowing = transactional.getInvoicesByAccountAfterDate(accountId.toString(),
                                                                                                        invoice.getInvoiceDate().toDateTimeAtStartOfDay().toDate(),
                                                                                                        context);
                    populateChildren(invoicesFollowing, transactional, context);

                    // The remaining amount to adjust (i.e. the amount of credits used on following invoices)
                    // is the current account CBA balance (minus the sign)
                    BigDecimal positiveRemainderToAdjust = accountCBA.negate();
                    for (final Invoice invoiceFollowing : invoicesFollowing) {
                        if (invoiceFollowing.getId().equals(invoice.getId())) {
                            continue;
                        }

                        // Add a single adjustment per invoice
                        BigDecimal positiveCBAAdjItemAmount = BigDecimal.ZERO;

                        for (final InvoiceItem cbaUsed : invoiceFollowing.getInvoiceItems()) {
                            // Ignore non CBA items or credits (CBA >= 0)
                            if (!InvoiceItemType.CBA_ADJ.equals(cbaUsed.getInvoiceItemType()) ||
                                    cbaUsed.getAmount().compareTo(BigDecimal.ZERO) >= 0) {
                                continue;
                            }

                            final BigDecimal positiveCBAUsedAmount = cbaUsed.getAmount().negate();
                            final BigDecimal positiveNextCBAAdjItemAmount;
                            if (positiveCBAUsedAmount.compareTo(positiveRemainderToAdjust) < 0) {
                                positiveNextCBAAdjItemAmount = positiveCBAUsedAmount;
                                positiveRemainderToAdjust = positiveRemainderToAdjust.subtract(positiveNextCBAAdjItemAmount);
                            } else {
                                positiveNextCBAAdjItemAmount = positiveRemainderToAdjust;
                                positiveRemainderToAdjust = BigDecimal.ZERO;
                            }
                            positiveCBAAdjItemAmount = positiveCBAAdjItemAmount.add(positiveNextCBAAdjItemAmount);

                            if (positiveRemainderToAdjust.compareTo(BigDecimal.ZERO) == 0) {
                                break;
                            }
                        }

                        // Add the adjustment on that invoice
                        final InvoiceItem nextCBAAdjItem = new CreditBalanceAdjInvoiceItem(invoiceFollowing.getId(), invoice.getAccountId(), context.getCreatedDate().toLocalDate(),
                                cbaItem.getId(), positiveCBAAdjItemAmount, cbaItem.getCurrency());
                        invoiceItemSqlDao.create(nextCBAAdjItem, context);
                        final Long nextCBAAdjItemRecordId = invoiceItemSqlDao.getRecordId(nextCBAAdjItem.getId().toString(), context);
                        audits.add(new EntityAudit(TableName.INVOICE_ITEMS, nextCBAAdjItemRecordId, ChangeType.INSERT));

                        if (positiveRemainderToAdjust.compareTo(BigDecimal.ZERO) == 0) {
                            break;
                        }
                    }
                }

                // Save audit logs
                transactional.insertAuditFromTransaction(audits, context);

                return null;
            }
        });
    }

    @Override
    public void test(final InternalTenantContext context) {
        invoiceSqlDao.test(context);
    }

    /**
     * Create an adjustment for a given invoice item. This just creates the object in memory, it doesn't write it to disk.
     *
     * @param invoiceId         the invoice id
     * @param invoiceItemId     the invoice item id to adjust
     * @param effectiveDate     adjustment effective date, in the account timezone
     * @param positiveAdjAmount the amount to adjust. Pass null to adjust the full amount of the original item
     * @param currency          the currency of the amount. Pass null to default to the original currency used
     * @return the adjustment item
     */
    private InvoiceItem createAdjustmentItem(final InvoiceSqlDao transactional, final UUID invoiceId, final UUID invoiceItemId,
                                             final BigDecimal positiveAdjAmount, final Currency currency,
                                             final LocalDate effectiveDate, final InternalTenantContext context) throws InvoiceApiException {
        // First, retrieve the invoice item in question
        final InvoiceItemSqlDao invoiceItemSqlDao = transactional.become(InvoiceItemSqlDao.class);
        final InvoiceItem invoiceItemToBeAdjusted = invoiceItemSqlDao.getById(invoiceItemId.toString(), context);
        if (invoiceItemToBeAdjusted == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_NOT_FOUND, invoiceItemId);
        }

        // Validate the invoice it belongs to
        if (!invoiceItemToBeAdjusted.getInvoiceId().equals(invoiceId)) {
            throw new InvoiceApiException(ErrorCode.INVOICE_INVALID_FOR_INVOICE_ITEM_ADJUSTMENT, invoiceItemId, invoiceId);
        }

        // Retrieve the amount and currency if needed
        final BigDecimal amountToAdjust = Objects.firstNonNull(positiveAdjAmount, invoiceItemToBeAdjusted.getAmount());
        // TODO - should we enforce the currency (and respect the original one) here if the amount passed was null?
        final Currency currencyForAdjustment = Objects.firstNonNull(currency, invoiceItemToBeAdjusted.getCurrency());

        // Finally, create the adjustment
        // Note! The amount is negated here!
        return new ItemAdjInvoiceItem(invoiceItemToBeAdjusted, effectiveDate, amountToAdjust.negate(), currencyForAdjustment);
    }

    /**
     * Create an invoice item and adjust the invoice with a CBA item if the new invoice balance is negative.
     *
     * @param transactional the InvoiceSqlDao
     * @param item          the invoice item to create
     * @param audits        the audits to populate
     * @param context       the call context
     */
    private void insertItemAndAddCBAIfNeeded(final InvoiceSqlDao transactional, final InvoiceItem item,
                                             final List<EntityAudit> audits, final InternalCallContext context) {
        final InvoiceItemSqlDao transInvoiceItemDao = transactional.become(InvoiceItemSqlDao.class);
        transInvoiceItemDao.create(item, context);

        final Long invoiceItemRecordId = transInvoiceItemDao.getRecordId(item.getId().toString(), context);
        audits.add(new EntityAudit(TableName.INVOICE_ITEMS, invoiceItemRecordId, ChangeType.INSERT));

        addCBAIfNeeded(transactional, item.getInvoiceId(), audits, context);
    }

    /**
     * Adjust the invoice with a CBA item if the new invoice balance is negative.
     *
     * @param transactional the InvoiceSqlDao
     * @param invoiceId     the invoice id to adjust
     * @param audits        the audits to populate
     * @param context       the call context
     */
    private void addCBAIfNeeded(final InvoiceSqlDao transactional, final UUID invoiceId,
                                final List<EntityAudit> audits, final InternalCallContext context) {
        final Invoice invoice = transactional.getById(invoiceId.toString(), context);
        if (invoice != null) {
            populateChildren(invoice, transactional, context);
        } else {
            throw new IllegalStateException("Invoice shouldn't be null for this item at this stage " + invoiceId);
        }

        // If invoice balance becomes negative we add some CBA item
        if (invoice.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            final InvoiceItemSqlDao transInvoiceItemDao = transactional.become(InvoiceItemSqlDao.class);
            final InvoiceItem cbaAdjItem = new CreditBalanceAdjInvoiceItem(invoice.getId(), invoice.getAccountId(), context.getCreatedDate().toLocalDate(),
                    invoice.getBalance().negate(), invoice.getCurrency());
            transInvoiceItemDao.create(cbaAdjItem, context);
            final Long cbaAdjItemRecordId = transInvoiceItemDao.getRecordId(cbaAdjItem.getId().toString(), context);
            audits.add(new EntityAudit(TableName.INVOICE_ITEMS, cbaAdjItemRecordId, ChangeType.INSERT));
        }
    }

    private BigDecimal getAccountCBAFromTransaction(final UUID accountId, final InvoiceSqlDao transactional, final InternalTenantContext context) {
        BigDecimal cba = BigDecimal.ZERO;
        final List<Invoice> invoices = getAllInvoicesByAccountFromTransaction(accountId, transactional, context);
        for (final Invoice cur : invoices) {
            cba = cba.add(cur.getCBAAmount());
        }
        return cba;
    }

    private void populateChildren(final Invoice invoice, final InvoiceSqlDao invoiceSqlDao, final InternalTenantContext context) {
        getInvoiceItemsWithinTransaction(invoice, invoiceSqlDao, context);
        getInvoicePaymentsWithinTransaction(invoice, invoiceSqlDao, context);
    }

    private void populateChildren(final List<Invoice> invoices, final InvoiceSqlDao invoiceSqlDao, final InternalTenantContext context) {
        getInvoiceItemsWithinTransaction(invoices, invoiceSqlDao, context);
        getInvoicePaymentsWithinTransaction(invoices, invoiceSqlDao, context);
    }

    private List<Invoice> getAllInvoicesByAccountFromTransaction(final UUID accountId, final InvoiceSqlDao transactional, final InternalTenantContext context) {
        final List<Invoice> invoices = transactional.getAllInvoicesByAccount(accountId.toString(), context);
        populateChildren(invoices, transactional, context);
        return invoices;
    }

    private BigDecimal getRemainingAmountPaidFromTransaction(final UUID invoicePaymentId, final InvoicePaymentSqlDao transactional, final InternalTenantContext context) {
        final BigDecimal amount = transactional.getRemainingAmountPaid(invoicePaymentId.toString(), context);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private void getInvoiceItemsWithinTransaction(final List<Invoice> invoices, final InvoiceSqlDao invoiceDao, final InternalTenantContext context) {
        for (final Invoice invoice : invoices) {
            getInvoiceItemsWithinTransaction(invoice, invoiceDao, context);
        }
    }

    private void getInvoiceItemsWithinTransaction(final Invoice invoice, final InvoiceSqlDao transactional, final InternalTenantContext context) {
        final String invoiceId = invoice.getId().toString();

        final InvoiceItemSqlDao transInvoiceItemSqlDao = transactional.become(InvoiceItemSqlDao.class);
        final List<InvoiceItem> items = transInvoiceItemSqlDao.getInvoiceItemsByInvoice(invoiceId, context);
        invoice.addInvoiceItems(items);
    }

    private void getInvoicePaymentsWithinTransaction(final List<Invoice> invoices, final InvoiceSqlDao invoiceDao, final InternalTenantContext context) {
        for (final Invoice invoice : invoices) {
            getInvoicePaymentsWithinTransaction(invoice, invoiceDao, context);
        }
    }

    private void getInvoicePaymentsWithinTransaction(final Invoice invoice, final InvoiceSqlDao invoiceSqlDao, final InternalTenantContext context) {
        final InvoicePaymentSqlDao invoicePaymentSqlDao = invoiceSqlDao.become(InvoicePaymentSqlDao.class);
        final String invoiceId = invoice.getId().toString();
        final List<InvoicePayment> invoicePayments = invoicePaymentSqlDao.getPaymentsForInvoice(invoiceId, context);
        invoice.addPayments(invoicePayments);
    }

    private void notifyOfFutureBillingEvents(final InvoiceSqlDao dao, final UUID accountId, final List<InvoiceItem> invoiceItems) {

        for (final InvoiceItem item : invoiceItems) {
            if (item.getInvoiceItemType() == InvoiceItemType.RECURRING) {
                final RecurringInvoiceItem recurringInvoiceItem = (RecurringInvoiceItem) item;
                if ((recurringInvoiceItem.getEndDate() != null) &&
                        (recurringInvoiceItem.getAmount() == null ||
                        recurringInvoiceItem.getAmount().compareTo(BigDecimal.ZERO) >= 0)) {
                    //
                    // We insert a future notification for each recurring subscription at the end of the service period  = new CTD of the subscription
                    //
                    nextBillingDatePoster.insertNextBillingNotification(dao, accountId, recurringInvoiceItem.getSubscriptionId(),
                            recurringInvoiceItem.getEndDate().toDateTimeAtCurrentTime());
                }
            }
        }
    }

    private void notifyBusOfInvoiceAdjustment(final Transmogrifier transactional, final UUID invoiceId, final UUID accountId,
                                              final UUID userToken, final InternalCallContext context) {
        try {
            eventBus.postFromTransaction(new DefaultInvoiceAdjustmentEvent(invoiceId, accountId, userToken, context.getAccountRecordId(), context.getTenantRecordId()),
                    transactional, context);
        } catch (EventBusException e) {
            log.warn("Failed to post adjustment event for invoice " + invoiceId, e);
        }
    }
}
