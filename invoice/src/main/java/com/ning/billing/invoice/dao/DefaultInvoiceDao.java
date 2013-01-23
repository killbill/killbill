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

package com.ning.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.InvoicePayment.InvoicePaymentType;
import com.ning.billing.invoice.api.user.DefaultInvoiceAdjustmentEvent;
import com.ning.billing.invoice.model.InvoiceItemList;
import com.ning.billing.invoice.notification.NextBillingDatePoster;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.entity.dao.EntityDaoBase;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import com.ning.billing.util.svcsapi.bus.InternalBus;
import com.ning.billing.util.svcsapi.bus.InternalBus.EventBusException;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Ordering;
import com.google.inject.Inject;

public class DefaultInvoiceDao extends EntityDaoBase<InvoiceModelDao, Invoice, InvoiceApiException> implements InvoiceDao {

    private static final Logger log = LoggerFactory.getLogger(DefaultInvoiceDao.class);

    private final NextBillingDatePoster nextBillingDatePoster;
    private final InternalBus eventBus;

    @Inject
    public DefaultInvoiceDao(final IDBI dbi,
                             final NextBillingDatePoster nextBillingDatePoster,
                             final InternalBus eventBus,
                             final Clock clock,
                             final CacheControllerDispatcher cacheControllerDispatcher,
                             final NonEntityDao nonEntityDao) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi, clock, cacheControllerDispatcher, nonEntityDao), InvoiceSqlDao.class);
        this.nextBillingDatePoster = nextBillingDatePoster;
        this.eventBus = eventBus;
    }

    @Override
    protected InvoiceApiException generateAlreadyExistsException(final InvoiceModelDao entity, final InternalCallContext context) {
        return new InvoiceApiException(ErrorCode.INVOICE_ACCOUNT_ID_INVALID, entity.getId());
    }

    @Override
    public List<InvoiceModelDao> getInvoicesByAccount(final UUID accountId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<InvoiceModelDao>>() {
            @Override
            public List<InvoiceModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao invoiceDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

                final List<InvoiceModelDao> invoices = invoiceDao.getInvoicesByAccount(accountId.toString(), context);
                populateChildren(invoices, entitySqlDaoWrapperFactory, context);

                return invoices;
            }
        });
    }

    @Override
    public List<InvoiceModelDao> getAllInvoicesByAccount(final UUID accountId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<InvoiceModelDao>>() {
            @Override
            public List<InvoiceModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return getAllInvoicesByAccountFromTransaction(accountId, entitySqlDaoWrapperFactory, context);
            }
        });
    }

    @Override
    public List<InvoiceModelDao> getInvoicesByAccount(final UUID accountId, final LocalDate fromDate, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<InvoiceModelDao>>() {
            @Override
            public List<InvoiceModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao invoiceDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

                final List<InvoiceModelDao> invoices = invoiceDao.getInvoicesByAccountAfterDate(accountId.toString(),
                                                                                                fromDate.toDateTimeAtStartOfDay().toDate(),
                                                                                                context);
                populateChildren(invoices, entitySqlDaoWrapperFactory, context);

                return invoices;
            }
        });
    }

    @Override
    public List<InvoiceModelDao> get(final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<InvoiceModelDao>>() {
            @Override
            public List<InvoiceModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao invoiceDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

                final List<InvoiceModelDao> invoices = invoiceDao.get(context);
                populateChildren(invoices, entitySqlDaoWrapperFactory, context);

                return invoices;
            }
        });
    }

    @Override
    public InvoiceModelDao getById(final UUID invoiceId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<InvoiceModelDao>() {
            @Override
            public InvoiceModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao invoiceDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

                final InvoiceModelDao invoice = invoiceDao.getById(invoiceId.toString(), context);
                if (invoice == null) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
                }
                populateChildren(invoice, entitySqlDaoWrapperFactory, context);
                return invoice;
            }
        });
    }

    @Override
    public InvoiceModelDao getByNumber(final Integer number, final InternalTenantContext context) throws InvoiceApiException {

        if (number == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_INVALID_NUMBER, "(null)");
        }

        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<InvoiceModelDao>() {
            @Override
            public InvoiceModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao invoiceDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

                final InvoiceModelDao invoice = invoiceDao.getByRecordId(number.longValue(), context);
                if (invoice == null) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_NUMBER_NOT_FOUND, number.longValue());
                }
                populateChildren(invoice, entitySqlDaoWrapperFactory, context);
                return invoice;
            }
        });
    }

    @Override
    public void createInvoice(final InvoiceModelDao invoice, final List<InvoiceItemModelDao> invoiceItems,
                              final List<InvoicePaymentModelDao> invoicePayments, final boolean isRealInvoice, final Map<UUID, DateTime> callbackDateTimePerSubscriptions,
                              final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

                final InvoiceModelDao currentInvoice = transactional.getById(invoice.getId().toString(), context);
                if (currentInvoice == null) {
                    // We only want to insert that invoice if there are real invoiceItems associated to it -- if not, this is just
                    // a shell invoice and we only need to insert the invoiceItems -- for the already existing invoices
                    if (isRealInvoice) {
                        transactional.create(invoice, context);
                    }

                    // Create the invoice items
                    final InvoiceItemSqlDao transInvoiceItemSqlDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
                    for (final InvoiceItemModelDao invoiceItemModelDao : invoiceItems) {
                        transInvoiceItemSqlDao.create(invoiceItemModelDao, context);
                    }

                    // Now we check whether we generated any credit that could be used on some unpaid invoices
                    useExistingCBAFromTransaction(invoice.getAccountId(), entitySqlDaoWrapperFactory, context);

                    notifyOfFutureBillingEvents(entitySqlDaoWrapperFactory, invoice.getAccountId(), callbackDateTimePerSubscriptions, context.getUserToken());

                    // Create associated payments
                    final InvoicePaymentSqlDao invoicePaymentSqlDao = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class);
                    invoicePaymentSqlDao.batchCreateFromTransaction(invoicePayments, context);

                }
                return null;
            }
        });
    }

    @Override
    public List<InvoiceModelDao> getInvoicesBySubscription(final UUID subscriptionId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<InvoiceModelDao>>() {
            @Override
            public List<InvoiceModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao invoiceDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

                final List<InvoiceModelDao> invoices = invoiceDao.getInvoicesBySubscription(subscriptionId.toString(), context);
                populateChildren(invoices, entitySqlDaoWrapperFactory, context);

                return invoices;
            }
        });
    }

    @Override
    public BigDecimal getAccountBalance(final UUID accountId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<BigDecimal>() {
            @Override
            public BigDecimal inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                BigDecimal cba = BigDecimal.ZERO;

                BigDecimal accountBalance = BigDecimal.ZERO;
                final List<InvoiceModelDao> invoices = getAllInvoicesByAccountFromTransaction(accountId, entitySqlDaoWrapperFactory, context);
                for (final InvoiceModelDao cur : invoices) {
                    accountBalance = accountBalance.add(InvoiceModelDaoHelper.getBalance(cur));
                    cba = cba.add(InvoiceModelDaoHelper.getCBAAmount(cur));
                }
                return accountBalance.subtract(cba);
            }
        });
    }

    @Override
    public BigDecimal getAccountCBA(final UUID accountId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<BigDecimal>() {
            @Override
            public BigDecimal inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return getAccountCBAFromTransaction(accountId, entitySqlDaoWrapperFactory, context);
            }
        });
    }

    @Override
    public List<InvoiceModelDao> getUnpaidInvoicesByAccountId(final UUID accountId, @Nullable final LocalDate upToDate, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<InvoiceModelDao>>() {
            @Override
            public List<InvoiceModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return getUnpaidInvoicesByAccountFromTransaction(accountId, entitySqlDaoWrapperFactory, upToDate, context);
            }
        });
    }


    @Override
    public UUID getInvoiceIdByPaymentId(final UUID paymentId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<UUID>() {
            @Override
            public UUID inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class).getInvoiceIdByPaymentId(paymentId.toString(), context);
            }
        });
    }

    @Override
    public List<InvoicePaymentModelDao> getInvoicePayments(final UUID paymentId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<InvoicePaymentModelDao>>() {
            @Override
            public List<InvoicePaymentModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getInvoicePayments(paymentId.toString(), context);
            }
        });
    }

    @Override
    public InvoicePaymentModelDao createRefund(final UUID paymentId, final BigDecimal requestedRefundAmount, final boolean isInvoiceAdjusted,
                                               final Map<UUID, BigDecimal> invoiceItemIdsWithNullAmounts, final UUID paymentCookieId,
                                               final InternalCallContext context)
            throws InvoiceApiException {
        final boolean isInvoiceItemAdjusted = isInvoiceAdjusted && invoiceItemIdsWithNullAmounts.size() > 0;

        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<InvoicePaymentModelDao>() {
            @Override
            public InvoicePaymentModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final InvoicePaymentSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class);

                final InvoiceSqlDao transInvoiceDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

                final InvoicePaymentModelDao payment = transactional.getByPaymentId(paymentId.toString(), context);
                if (payment == null) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_PAYMENT_BY_ATTEMPT_NOT_FOUND, paymentId);
                }

                // Retrieve the amounts to adjust, if needed
                final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts = computeItemAdjustments(payment.getInvoiceId().toString(),
                                                                                               entitySqlDaoWrapperFactory,
                                                                                               invoiceItemIdsWithNullAmounts,
                                                                                               context);

                // Compute the actual amount to refund
                final BigDecimal requestedPositiveAmount = computePositiveRefundAmount(payment, requestedRefundAmount, invoiceItemIdsWithAmounts);

                // Before we go further, check if that refund already got inserted -- the payment system keeps a state machine
                // and so this call may be called several time for the same  paymentCookieId (which is really the refundId)
                final InvoicePaymentModelDao existingRefund = transactional.getPaymentsForCookieId(paymentCookieId.toString(), context);
                if (existingRefund != null) {
                    return existingRefund;
                }

                final InvoicePaymentModelDao refund = new InvoicePaymentModelDao(UUID.randomUUID(), context.getCreatedDate(), InvoicePaymentType.REFUND,
                                                                                 payment.getInvoiceId(), paymentId,
                                                                                 context.getCreatedDate(), requestedPositiveAmount.negate(),
                                                                                 payment.getCurrency(), paymentCookieId, payment.getId());
                transactional.create(refund, context);

                // Retrieve invoice after the Refund
                final InvoiceModelDao invoice = transInvoiceDao.getById(payment.getInvoiceId().toString(), context);
                if (invoice != null) {
                    populateChildren(invoice, entitySqlDaoWrapperFactory, context);
                } else {
                    throw new IllegalStateException("Invoice shouldn't be null for payment " + payment.getId());
                }

                final BigDecimal invoiceBalanceAfterRefund = InvoiceModelDaoHelper.getBalance(invoice);
                final InvoiceItemSqlDao transInvoiceItemDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);

                // At this point, we created the refund which made the invoice balance positive and applied any existing
                // available CBA to that invoice.
                // We now need to adjust the invoice and/or invoice items if needed and specified.
                if (isInvoiceAdjusted && !isInvoiceItemAdjusted) {
                    // Invoice adjustment
                    final BigDecimal maxBalanceToAdjust = (invoiceBalanceAfterRefund.compareTo(BigDecimal.ZERO) <= 0) ? BigDecimal.ZERO : invoiceBalanceAfterRefund;
                    final BigDecimal requestedPositiveAmountToAdjust = requestedPositiveAmount.compareTo(maxBalanceToAdjust) > 0 ? maxBalanceToAdjust : requestedPositiveAmount;
                    if (requestedPositiveAmountToAdjust.compareTo(BigDecimal.ZERO) > 0) {
                        final InvoiceItemModelDao adjItem = new InvoiceItemModelDao(context.getCreatedDate(), InvoiceItemType.REFUND_ADJ, invoice.getId(), invoice.getAccountId(),
                                                                                    null, null, null, null, context.getCreatedDate().toLocalDate(), null,
                                                                                    requestedPositiveAmountToAdjust.negate(), null, invoice.getCurrency(), null);
                        transInvoiceItemDao.create(adjItem, context);
                    }
                } else if (isInvoiceAdjusted) {
                    // Invoice item adjustment
                    for (final UUID invoiceItemId : invoiceItemIdsWithAmounts.keySet()) {
                        final BigDecimal adjAmount = invoiceItemIdsWithAmounts.get(invoiceItemId);
                        final InvoiceItemModelDao item = createAdjustmentItem(entitySqlDaoWrapperFactory, invoice.getId(), invoiceItemId, adjAmount,
                                                                              invoice.getCurrency(), context.getCreatedDate().toLocalDate(),
                                                                              context);
                        transInvoiceItemDao.create(item, context);
                    }
                }

                // Now we check whether we have any credit that could be used on some unpaid invoices (for which payment was just refunded)
                useExistingCBAFromTransaction(invoice.getAccountId(), entitySqlDaoWrapperFactory, context);

                // Notify the bus since the balance of the invoice changed
                notifyBusOfInvoiceAdjustment(entitySqlDaoWrapperFactory, invoice.getId(), invoice.getAccountId(), context.getUserToken(), context);

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
     * @param entitySqlDaoWrapperFactory    the EntitySqlDaoWrapperFactory from the current transaction
     * @param invoiceItemIdsWithNullAmounts the original mapping between invoice item ids and amount to refund (contains null)
     * @param context                       the tenant context
     * @return the final mapping between invoice item ids and amount to refund
     * @throws InvoiceApiException
     */
    private Map<UUID, BigDecimal> computeItemAdjustments(final String invoiceId,
                                                         final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory,
                                                         final Map<UUID, BigDecimal> invoiceItemIdsWithNullAmounts,
                                                         final InternalTenantContext context) throws InvoiceApiException {
        // Populate the missing amounts for individual items, if needed
        final Builder<UUID, BigDecimal> invoiceItemIdsWithAmountsBuilder = new Builder<UUID, BigDecimal>();
        if (invoiceItemIdsWithNullAmounts.size() == 0) {
            return invoiceItemIdsWithAmountsBuilder.build();
        }

        // Retrieve invoice before the Refund
        final InvoiceModelDao invoice = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class).getById(invoiceId, context);
        if (invoice != null) {
            populateChildren(invoice, entitySqlDaoWrapperFactory, context);
        } else {
            throw new IllegalStateException("Invoice shouldn't be null for id " + invoiceId);
        }

        for (final UUID invoiceItemId : invoiceItemIdsWithNullAmounts.keySet()) {
            final BigDecimal adjAmount = Objects.firstNonNull(invoiceItemIdsWithNullAmounts.get(invoiceItemId),
                                                              getInvoiceItemAmountForId(invoice, invoiceItemId));
            final BigDecimal adjAmountRemainingAfterRepair = computeItemAdjustmentAmount(invoiceItemId, adjAmount, invoice.getInvoiceItems());
            if (adjAmountRemainingAfterRepair.compareTo(BigDecimal.ZERO) > 0) {
                invoiceItemIdsWithAmountsBuilder.put(invoiceItemId, adjAmountRemainingAfterRepair);
            }
        }

        return invoiceItemIdsWithAmountsBuilder.build();
    }

    /**
     * @param invoiceItem  item we are adjusting
     * @param requestedPositiveAmountToAdjust
     *                     amount we are adjusting for that item
     * @param invoiceItems list of all invoice items on this invoice
     * @return the amount we should really adjust based on whether or not the item got repaired
     */
    private BigDecimal computeItemAdjustmentAmount(final UUID invoiceItem, final BigDecimal requestedPositiveAmountToAdjust, final List<InvoiceItemModelDao> invoiceItems) {

        BigDecimal positiveRepairedAmount = BigDecimal.ZERO;

        final Collection<InvoiceItemModelDao> repairedItems = Collections2.filter(invoiceItems, new Predicate<InvoiceItemModelDao>() {
            @Override
            public boolean apply(final InvoiceItemModelDao input) {
                return (input.getType() == InvoiceItemType.REPAIR_ADJ && input.getLinkedItemId().equals(invoiceItem));
            }
        });
        for (final InvoiceItemModelDao cur : repairedItems) {
            // Repair item are negative so we negate to make it positive
            positiveRepairedAmount = positiveRepairedAmount.add(cur.getAmount().negate());
        }
        return (positiveRepairedAmount.compareTo(requestedPositiveAmountToAdjust) >= 0) ? BigDecimal.ZERO : requestedPositiveAmountToAdjust.subtract(positiveRepairedAmount);
    }

    private BigDecimal getInvoiceItemAmountForId(final InvoiceModelDao invoice, final UUID invoiceItemId) throws InvoiceApiException {
        for (final InvoiceItemModelDao invoiceItem : invoice.getInvoiceItems()) {
            if (invoiceItem.getId().equals(invoiceItemId)) {
                return invoiceItem.getAmount();
            }
        }

        throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_NOT_FOUND, invoiceItemId);
    }

    @VisibleForTesting
    BigDecimal computePositiveRefundAmount(final InvoicePaymentModelDao payment, final BigDecimal requestedAmount, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts) throws InvoiceApiException {
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
    public InvoicePaymentModelDao postChargeback(final UUID invoicePaymentId, final BigDecimal amount, final InternalCallContext context) throws InvoiceApiException {

        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<InvoicePaymentModelDao>() {
            @Override
            public InvoicePaymentModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final InvoicePaymentSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class);

                final BigDecimal maxChargedBackAmount = getRemainingAmountPaidFromTransaction(invoicePaymentId, entitySqlDaoWrapperFactory, context);
                final BigDecimal requestedChargedBackAmout = (amount == null) ? maxChargedBackAmount : amount;
                if (requestedChargedBackAmout.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new InvoiceApiException(ErrorCode.CHARGE_BACK_AMOUNT_IS_NEGATIVE);
                }
                if (requestedChargedBackAmout.compareTo(maxChargedBackAmount) > 0) {
                    throw new InvoiceApiException(ErrorCode.CHARGE_BACK_AMOUNT_TOO_HIGH, requestedChargedBackAmout, maxChargedBackAmount);
                }

                final InvoicePaymentModelDao payment = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getById(invoicePaymentId.toString(), context);
                if (payment == null) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_PAYMENT_NOT_FOUND, invoicePaymentId.toString());
                }
                final InvoicePaymentModelDao chargeBack = new InvoicePaymentModelDao(UUID.randomUUID(), context.getCreatedDate(), InvoicePaymentType.CHARGED_BACK,
                                                                                     payment.getInvoiceId(), payment.getPaymentId(), context.getCreatedDate(),
                                                                                     requestedChargedBackAmout.negate(), payment.getCurrency(), null, payment.getId());
                transactional.create(chargeBack, context);

                // Notify the bus since the balance of the invoice changed
                final UUID accountId = transactional.getAccountIdFromInvoicePaymentId(chargeBack.getId().toString(), context);
                notifyBusOfInvoiceAdjustment(entitySqlDaoWrapperFactory, payment.getInvoiceId(), accountId, context.getUserToken(), context);

                // Now we check whether we have any credit that could be used on some unpaid invoices (for which payment was just charged back)
                useExistingCBAFromTransaction(accountId, entitySqlDaoWrapperFactory, context);

                return chargeBack;
            }
        });
    }

    @Override
    public BigDecimal getRemainingAmountPaid(final UUID invoicePaymentId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<BigDecimal>() {
            @Override
            public BigDecimal inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return getRemainingAmountPaidFromTransaction(invoicePaymentId, entitySqlDaoWrapperFactory, context);
            }
        });
    }

    @Override
    public UUID getAccountIdFromInvoicePaymentId(final UUID invoicePaymentId, final InternalTenantContext context) throws InvoiceApiException {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<UUID>() {
            @Override
            public UUID inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final UUID accountId = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getAccountIdFromInvoicePaymentId(invoicePaymentId.toString(), context);
                if (accountId == null) {
                    throw new InvoiceApiException(ErrorCode.CHARGE_BACK_COULD_NOT_FIND_ACCOUNT_ID, invoicePaymentId);
                } else {
                    return accountId;
                }
            }
        });
    }

    @Override
    public List<InvoicePaymentModelDao> getChargebacksByAccountId(final UUID accountId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<InvoicePaymentModelDao>>() {
            @Override
            public List<InvoicePaymentModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getChargeBacksByAccountId(accountId.toString(), context);
            }
        });
    }

    @Override
    public List<InvoicePaymentModelDao> getChargebacksByPaymentId(final UUID paymentId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<InvoicePaymentModelDao>>() {
            @Override
            public List<InvoicePaymentModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getChargebacksByPaymentId(paymentId.toString(), context);
            }
        });
    }

    @Override
    public InvoicePaymentModelDao getChargebackById(final UUID chargebackId, final InternalTenantContext context) throws InvoiceApiException {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<InvoicePaymentModelDao>() {
            @Override
            public InvoicePaymentModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final InvoicePaymentModelDao chargeback = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getById(chargebackId.toString(), context);
                if (chargeback == null) {
                    throw new InvoiceApiException(ErrorCode.CHARGE_BACK_DOES_NOT_EXIST, chargebackId);
                } else {
                    return chargeback;
                }
            }
        });
    }

    @Override
    public InvoiceItemModelDao getExternalChargeById(final UUID externalChargeId, final InternalTenantContext context) throws InvoiceApiException {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<InvoiceItemModelDao>() {
            @Override
            public InvoiceItemModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class).getById(externalChargeId.toString(), context);
            }
        });
    }

    @Override
    public void notifyOfPayment(final InvoicePaymentModelDao invoicePayment, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).create(invoicePayment, context);
                return null;
            }
        });
    }

    @Override
    public InvoiceItemModelDao insertExternalCharge(final UUID accountId, @Nullable final UUID invoiceId, @Nullable final UUID bundleId, final String description,
                                                    final BigDecimal amount, final LocalDate effectiveDate, final Currency currency, final InternalCallContext context)
            throws InvoiceApiException {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<InvoiceItemModelDao>() {
            @Override
            public InvoiceItemModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

                UUID invoiceIdForExternalCharge = invoiceId;
                // Create an invoice for that external charge if it doesn't exist
                if (invoiceIdForExternalCharge == null) {
                    final InvoiceModelDao invoiceForExternalCharge = new InvoiceModelDao(accountId, effectiveDate, effectiveDate, currency);
                    transactional.create(invoiceForExternalCharge, context);
                    invoiceIdForExternalCharge = invoiceForExternalCharge.getId();
                }

                final InvoiceItemModelDao externalCharge = new InvoiceItemModelDao(context.getCreatedDate(), InvoiceItemType.EXTERNAL_CHARGE,
                                                                                   invoiceIdForExternalCharge, accountId,
                                                                                   bundleId, null, description, null,
                                                                                   effectiveDate, null, amount, null,
                                                                                   currency, null);
                final InvoiceItemSqlDao transInvoiceItemDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
                transInvoiceItemDao.create(externalCharge, context);

                // At this point, reread the invoice and figure out if we need to consume some of the CBA
                final InvoiceModelDao invoice = transactional.getById(invoiceIdForExternalCharge.toString(), context);
                if (invoice == null) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceIdForExternalCharge);
                }
                populateChildren(invoice, entitySqlDaoWrapperFactory, context);

                // Now we check whether we have any credit that could be used towards that charge
                useExistingCBAFromTransaction(accountId, entitySqlDaoWrapperFactory, context);

                // Notify the bus since the balance of the invoice changed
                // TODO should we post an InvoiceCreationInternalEvent event instead? Note! This will trigger a payment (see InvoiceHandler)
                notifyBusOfInvoiceAdjustment(entitySqlDaoWrapperFactory, invoiceId, accountId, context.getUserToken(), context);

                return externalCharge;
            }
        });
    }

    @Override
    public InvoiceItemModelDao getCreditById(final UUID creditId, final InternalTenantContext context) throws InvoiceApiException {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<InvoiceItemModelDao>() {
            @Override
            public InvoiceItemModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class).getById(creditId.toString(), context);
            }
        });
    }

    @Override
    public InvoiceItemModelDao insertCredit(final UUID accountId, @Nullable final UUID invoiceId, final BigDecimal positiveCreditAmount,
                                            final LocalDate effectiveDate, final Currency currency, final InternalCallContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<InvoiceItemModelDao>() {
            @Override
            public InvoiceItemModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

                UUID invoiceIdForCredit = invoiceId;
                // Create an invoice for that credit if it doesn't exist
                if (invoiceIdForCredit == null) {
                    final InvoiceModelDao invoiceForCredit = new InvoiceModelDao(accountId, effectiveDate, effectiveDate, currency);
                    transactional.create(invoiceForCredit, context);
                    invoiceIdForCredit = invoiceForCredit.getId();
                }

                // Note! The amount is negated here!
                final InvoiceItemModelDao credit = new InvoiceItemModelDao(context.getCreatedDate(), InvoiceItemType.CREDIT_ADJ, invoiceIdForCredit,
                                                                           accountId, null, null, null, null, effectiveDate,
                                                                           null, positiveCreditAmount.negate(), null,
                                                                           currency, null);
                insertItemAndAddCBAIfNeeded(entitySqlDaoWrapperFactory, credit, context);

                // Notify the bus since the balance of the invoice changed
                notifyBusOfInvoiceAdjustment(entitySqlDaoWrapperFactory, invoiceId, accountId, context.getUserToken(), context);

                return credit;
            }
        });
    }

    @Override
    public InvoiceItemModelDao insertInvoiceItemAdjustment(final UUID accountId, final UUID invoiceId, final UUID invoiceItemId,
                                                           final LocalDate effectiveDate, @Nullable final BigDecimal positiveAdjAmount,
                                                           @Nullable final Currency currency, final InternalCallContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<InvoiceItemModelDao>() {
            @Override
            public InvoiceItemModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceItemModelDao invoiceItemAdjustment = createAdjustmentItem(entitySqlDaoWrapperFactory, invoiceId, invoiceItemId, positiveAdjAmount,
                                                                                       currency, effectiveDate, context);
                insertItemAndAddCBAIfNeeded(entitySqlDaoWrapperFactory, invoiceItemAdjustment, context);
                notifyBusOfInvoiceAdjustment(entitySqlDaoWrapperFactory, invoiceId, accountId, context.getUserToken(), context);

                return invoiceItemAdjustment;
            }
        });
    }

    @Override
    public void deleteCBA(final UUID accountId, final UUID invoiceId, final UUID invoiceItemId, final InternalCallContext context) throws InvoiceApiException {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

                // Retrieve the invoice and make sure it belongs to the right account
                final InvoiceModelDao invoice = transactional.getById(invoiceId.toString(), context);
                if (invoice == null || !invoice.getAccountId().equals(accountId)) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
                }

                // Retrieve the invoice item and make sure it belongs to the right invoice
                final InvoiceItemSqlDao invoiceItemSqlDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
                final InvoiceItemModelDao cbaItem = invoiceItemSqlDao.getById(invoiceItemId.toString(), context);
                if (cbaItem == null || !cbaItem.getInvoiceId().equals(invoice.getId())) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_NOT_FOUND, invoiceItemId);
                }

                // First, adjust the same invoice with the CBA amount to "delete"
                final InvoiceItemModelDao cbaAdjItem = new InvoiceItemModelDao(context.getCreatedDate(), InvoiceItemType.CBA_ADJ, invoice.getId(), invoice.getAccountId(),
                                                                               null, null, null, null, context.getCreatedDate().toLocalDate(),
                                                                               null, cbaItem.getAmount().negate(), null, cbaItem.getCurrency(), cbaItem.getId());
                invoiceItemSqlDao.create(cbaAdjItem, context);

                // Verify the final invoice balance is not negative
                populateChildren(invoice, entitySqlDaoWrapperFactory, context);
                if (InvoiceModelDaoHelper.getBalance(invoice).compareTo(BigDecimal.ZERO) < 0) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_WOULD_BE_NEGATIVE);
                }

                // If there is more account credit than CBA we adjusted, we're done.
                // Otherwise, we need to find further invoices on which this credit was consumed
                final BigDecimal accountCBA = getAccountCBAFromTransaction(accountId, entitySqlDaoWrapperFactory, context);
                if (accountCBA.compareTo(BigDecimal.ZERO) < 0) {
                    if (accountCBA.compareTo(cbaItem.getAmount().negate()) < 0) {
                        throw new IllegalStateException("The account balance can't be lower than the amount adjusted");
                    }
                    final List<InvoiceModelDao> invoicesFollowing = transactional.getInvoicesByAccountAfterDate(accountId.toString(),
                                                                                                                invoice.getInvoiceDate().toDateTimeAtStartOfDay().toDate(),
                                                                                                                context);
                    populateChildren(invoicesFollowing, entitySqlDaoWrapperFactory, context);

                    // The remaining amount to adjust (i.e. the amount of credits used on following invoices)
                    // is the current account CBA balance (minus the sign)
                    BigDecimal positiveRemainderToAdjust = accountCBA.negate();
                    for (final InvoiceModelDao invoiceFollowing : invoicesFollowing) {
                        if (invoiceFollowing.getId().equals(invoice.getId())) {
                            continue;
                        }

                        // Add a single adjustment per invoice
                        BigDecimal positiveCBAAdjItemAmount = BigDecimal.ZERO;

                        for (final InvoiceItemModelDao cbaUsed : invoiceFollowing.getInvoiceItems()) {
                            // Ignore non CBA items or credits (CBA >= 0)
                            if (!InvoiceItemType.CBA_ADJ.equals(cbaUsed.getType()) ||
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
                        final InvoiceItemModelDao nextCBAAdjItem = new InvoiceItemModelDao(context.getCreatedDate(), InvoiceItemType.CBA_ADJ, invoiceFollowing.getId(),
                                                                                           invoice.getAccountId(), null, null, null, null,
                                                                                           context.getCreatedDate().toLocalDate(), null,
                                                                                           positiveCBAAdjItemAmount, null, cbaItem.getCurrency(), cbaItem.getId());
                        invoiceItemSqlDao.create(nextCBAAdjItem, context);
                        if (positiveRemainderToAdjust.compareTo(BigDecimal.ZERO) == 0) {
                            break;
                        }
                    }
                }

                return null;
            }
        });
    }

    public void consumeExstingCBAOnAccountWithUnpaidInvoices(final UUID accountId, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                useExistingCBAFromTransaction(accountId, entitySqlDaoWrapperFactory, context);
                return null;
            }
        });
    }

    private void useExistingCBAFromTransaction(final UUID accountId, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalCallContext context) throws InvoiceApiException, EntityPersistenceException {

        final BigDecimal accountCBA = getAccountCBAFromTransaction(accountId, entitySqlDaoWrapperFactory, context);
        if (accountCBA.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        final List<InvoiceModelDao> unpaidInvoices = getUnpaidInvoicesByAccountFromTransaction(accountId, entitySqlDaoWrapperFactory, null, context);
        // We order the same os BillingStateCalculator-- should really share the comparator
        final List<InvoiceModelDao> orderedUnpaidInvoices = Ordering.from(new Comparator<InvoiceModelDao>() {
            @Override
            public int compare(final InvoiceModelDao i1, final InvoiceModelDao i2) {
                return i1.getInvoiceDate().compareTo(i2.getInvoiceDate());
            }
        }).immutableSortedCopy(unpaidInvoices);

        BigDecimal remainingAccountCBA = accountCBA;
        for (InvoiceModelDao cur : orderedUnpaidInvoices) {
            final BigDecimal curInvoiceBalance = InvoiceModelDaoHelper.getBalance(cur);
            final BigDecimal cbaToApplyOnInvoice = remainingAccountCBA.compareTo(curInvoiceBalance) <= 0 ? remainingAccountCBA : curInvoiceBalance;
            remainingAccountCBA = remainingAccountCBA.subtract(cbaToApplyOnInvoice);


            final InvoiceItemModelDao cbaAdjItem = new InvoiceItemModelDao(context.getCreatedDate(), InvoiceItemType.CBA_ADJ,
                                                                           cur.getId(), cur.getAccountId(),
                                                                           null, null, null, null,
                                                                           context.getCreatedDate().toLocalDate(),
                                                                           null, cbaToApplyOnInvoice.negate(), null,
                                                                           cur.getCurrency(), null);

            final InvoiceItemSqlDao transInvoiceItemDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
            transInvoiceItemDao.create(cbaAdjItem, context);

            if (remainingAccountCBA.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
        }

        // TODO Should we send an event on the bus for Analytics?
    }


    private List<InvoiceModelDao> getUnpaidInvoicesByAccountFromTransaction(final UUID accountId, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final LocalDate upToDate, final InternalTenantContext context) {
        final List<InvoiceModelDao> invoices = getAllInvoicesByAccountFromTransaction(accountId, entitySqlDaoWrapperFactory, context);
        final Collection<InvoiceModelDao> unpaidInvoices = Collections2.filter(invoices, new Predicate<InvoiceModelDao>() {
            @Override
            public boolean apply(final InvoiceModelDao in) {
                return (InvoiceModelDaoHelper.getBalance(in).compareTo(BigDecimal.ZERO) >= 1) && (upToDate == null || !in.getTargetDate().isAfter(upToDate));
            }
        });
        return new ArrayList<InvoiceModelDao>(unpaidInvoices);
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
    private InvoiceItemModelDao createAdjustmentItem(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final UUID invoiceId, final UUID invoiceItemId,
                                                     final BigDecimal positiveAdjAmount, final Currency currency,
                                                     final LocalDate effectiveDate, final InternalCallContext context) throws InvoiceApiException {
        // First, retrieve the invoice item in question
        final InvoiceItemSqlDao invoiceItemSqlDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
        final InvoiceItemModelDao invoiceItemToBeAdjusted = invoiceItemSqlDao.getById(invoiceItemId.toString(), context);
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
        return new InvoiceItemModelDao(context.getCreatedDate(), InvoiceItemType.ITEM_ADJ, invoiceItemToBeAdjusted.getInvoiceId(), invoiceItemToBeAdjusted.getAccountId(),
                                       null, null, null, null, effectiveDate, effectiveDate, amountToAdjust.negate(), null, currencyForAdjustment, invoiceItemToBeAdjusted.getId());
    }

    /**
     * Create an invoice item and adjust the invoice with a CBA item if the new invoice balance is negative.
     *
     * @param entitySqlDaoWrapperFactory the EntitySqlDaoWrapperFactory from the current transaction
     * @param item                       the invoice item to create
     * @param context                    the call context
     */
    private void insertItemAndAddCBAIfNeeded(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory,
                                             final InvoiceItemModelDao item,
                                             final InternalCallContext context) throws EntityPersistenceException {
        final InvoiceItemSqlDao transInvoiceItemDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
        transInvoiceItemDao.create(item, context);

        addCBAIfNeeded(entitySqlDaoWrapperFactory, item.getInvoiceId(), context);
    }

    /**
     * Adjust the invoice with a CBA item if the new invoice balance is negative.
     *
     * @param entitySqlDaoWrapperFactory the EntitySqlDaoWrapperFactory from the current transaction
     * @param invoiceId                  the invoice id to adjust
     * @param context                    the call context
     */
    private void addCBAIfNeeded(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory,
                                final UUID invoiceId,
                                final InternalCallContext context) throws EntityPersistenceException {
        final InvoiceModelDao invoice = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class).getById(invoiceId.toString(), context);
        if (invoice != null) {
            populateChildren(invoice, entitySqlDaoWrapperFactory, context);
        } else {
            throw new IllegalStateException("Invoice shouldn't be null for this item at this stage " + invoiceId);
        }

        // If invoice balance becomes negative we add some CBA item
        final BigDecimal balance = InvoiceModelDaoHelper.getBalance(invoice);
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            final InvoiceItemSqlDao transInvoiceItemDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
            final InvoiceItemModelDao cbaAdjItem = new InvoiceItemModelDao(context.getCreatedDate(), InvoiceItemType.CBA_ADJ, invoice.getId(), invoice.getAccountId(),
                                                                           null, null, null, null, context.getCreatedDate().toLocalDate(),
                                                                           null, balance.negate(), null, invoice.getCurrency(), null);
            transInvoiceItemDao.create(cbaAdjItem, context);
        }
    }

    private BigDecimal getAccountCBAFromTransaction(final UUID accountId,
                                                    final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory,
                                                    final InternalTenantContext context) {
        BigDecimal cba = BigDecimal.ZERO;
        final List<InvoiceModelDao> invoices = getAllInvoicesByAccountFromTransaction(accountId, entitySqlDaoWrapperFactory, context);
        for (final InvoiceModelDao cur : invoices) {
            final InvoiceItemList invoiceItems = new InvoiceItemList(cur.getInvoiceItems());
            cba = cba.add(invoiceItems.getCBAAmount());
        }
        return cba;
    }

    private void populateChildren(final InvoiceModelDao invoice, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        getInvoiceItemsWithinTransaction(invoice, entitySqlDaoWrapperFactory, context);
        getInvoicePaymentsWithinTransaction(invoice, entitySqlDaoWrapperFactory, context);
    }

    private void populateChildren(final List<InvoiceModelDao> invoices, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        getInvoiceItemsWithinTransaction(invoices, entitySqlDaoWrapperFactory, context);
        getInvoicePaymentsWithinTransaction(invoices, entitySqlDaoWrapperFactory, context);
    }

    private List<InvoiceModelDao> getAllInvoicesByAccountFromTransaction(final UUID accountId, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final List<InvoiceModelDao> invoices = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class).getAllInvoicesByAccount(accountId.toString(), context);
        populateChildren(invoices, entitySqlDaoWrapperFactory, context);
        return invoices;
    }

    private BigDecimal getRemainingAmountPaidFromTransaction(final UUID invoicePaymentId, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final BigDecimal amount = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getRemainingAmountPaid(invoicePaymentId.toString(), context);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private void getInvoiceItemsWithinTransaction(final List<InvoiceModelDao> invoices, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        for (final InvoiceModelDao invoice : invoices) {
            getInvoiceItemsWithinTransaction(invoice, entitySqlDaoWrapperFactory, context);
        }
    }

    private void getInvoiceItemsWithinTransaction(final InvoiceModelDao invoice, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final String invoiceId = invoice.getId().toString();

        final InvoiceItemSqlDao transInvoiceItemSqlDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
        final List<InvoiceItemModelDao> items = transInvoiceItemSqlDao.getInvoiceItemsByInvoice(invoiceId, context);
        invoice.addInvoiceItems(items);
    }

    private void getInvoicePaymentsWithinTransaction(final List<InvoiceModelDao> invoices, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        for (final InvoiceModelDao invoice : invoices) {
            getInvoicePaymentsWithinTransaction(invoice, entitySqlDaoWrapperFactory, context);
        }
    }

    private void getInvoicePaymentsWithinTransaction(final InvoiceModelDao invoice, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final InvoicePaymentSqlDao invoicePaymentSqlDao = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class);
        final String invoiceId = invoice.getId().toString();
        final List<InvoicePaymentModelDao> invoicePayments = invoicePaymentSqlDao.getPaymentsForInvoice(invoiceId, context);
        invoice.addPayments(invoicePayments);
    }

    private void notifyOfFutureBillingEvents(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final UUID accountId,
                                             final Map<UUID, DateTime> callbackDateTimePerSubscriptions, final UUID userToken) {
        for (final UUID subscriptionId : callbackDateTimePerSubscriptions.keySet()) {
            final DateTime callbackDateTimeUTC = callbackDateTimePerSubscriptions.get(subscriptionId);
            nextBillingDatePoster.insertNextBillingNotification(entitySqlDaoWrapperFactory, accountId, subscriptionId, callbackDateTimeUTC, userToken);
        }
    }

    private void notifyBusOfInvoiceAdjustment(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final UUID invoiceId, final UUID accountId,
                                              final UUID userToken, final InternalCallContext context) {
        try {
            eventBus.postFromTransaction(new DefaultInvoiceAdjustmentEvent(invoiceId, accountId, userToken, context.getAccountRecordId(), context.getTenantRecordId()),
                                         entitySqlDaoWrapperFactory, context);
        } catch (EventBusException e) {
            log.warn("Failed to post adjustment event for invoice " + invoiceId, e);
        }
    }
}
