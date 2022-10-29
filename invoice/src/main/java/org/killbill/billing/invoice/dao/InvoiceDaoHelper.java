/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.commons.utils.Preconditions;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.commons.utils.collect.Iterables;
import org.killbill.billing.util.dao.CounterMappings;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InvoiceDaoHelper {

    private static final Logger log = LoggerFactory.getLogger(InvoiceDaoHelper.class);

    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public InvoiceDaoHelper(final InternalCallContextFactory internalCallContextFactory) {
        this.internalCallContextFactory = internalCallContextFactory;
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
     * @param context                       the tenant callcontext
     * @return the final mapping between invoice item ids and amount to refund
     * @throws org.killbill.billing.invoice.api.InvoiceApiException
     */
    public Map<UUID, BigDecimal> computeItemAdjustments(final String invoiceId,
                                                        final List<Tag> invoicesTags,
                                                        final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                                        final Map<UUID, BigDecimal> invoiceItemIdsWithNullAmounts,
                                                        final InternalTenantContext context) throws InvoiceApiException {
        // Populate the missing amounts for individual items, if needed
        final Map<UUID, BigDecimal> outputItemIdsWithAmounts = new HashMap<>();
        // Retrieve invoice before the Refund
        final InvoiceModelDao invoice = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class).getById(invoiceId, context);
        if (invoice != null) {
            populateChildren(invoice, invoicesTags, entitySqlDaoWrapperFactory, context);
        } else {
            throw new IllegalStateException("Invoice shouldn't be null for id " + invoiceId);
        }

        //
        // If we have an item amount, we 'd like to use it, but we need to check first that it is lesser or equal than maximum allowed
        //If, not we compute maximum value we can adjust per item
        for (final UUID invoiceItemId : invoiceItemIdsWithNullAmounts.keySet()) {
            final List<InvoiceItemModelDao> adjustedOrRepairedItems = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class).getAdjustedOrRepairedInvoiceItemsByLinkedId(invoiceItemId.toString(), context);
            computeItemAdjustmentsForTargetInvoiceItem(getInvoiceItemForId(invoice, invoiceItemId), adjustedOrRepairedItems, invoiceItemIdsWithNullAmounts, outputItemIdsWithAmounts);
        }
        return outputItemIdsWithAmounts;
    }

    private static void computeItemAdjustmentsForTargetInvoiceItem(final InvoiceItemModelDao targetInvoiceItem,
                                                                   final List<InvoiceItemModelDao> adjustedOrRepairedItems,
                                                                   final Map<UUID, BigDecimal> inputAdjInvoiceItem,
                                                                   final Map<UUID, BigDecimal> outputAdjInvoiceItem) throws InvoiceApiException {
        final BigDecimal originalItemAmount = targetInvoiceItem.getAmount();
        final BigDecimal maxAdjLeftAmount = computeItemAdjustmentAmount(originalItemAmount, adjustedOrRepairedItems);

        final BigDecimal proposedItemAmount = inputAdjInvoiceItem.get(targetInvoiceItem.getId());
        if (proposedItemAmount != null && proposedItemAmount.compareTo(maxAdjLeftAmount) > 0) {
            throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_ADJUSTMENT_AMOUNT_INVALID, proposedItemAmount, maxAdjLeftAmount);
        }

        final BigDecimal itemAmountToAdjust = Objects.requireNonNullElse(proposedItemAmount, maxAdjLeftAmount);
        if (itemAmountToAdjust.compareTo(BigDecimal.ZERO) > 0) {
            outputAdjInvoiceItem.put(targetInvoiceItem.getId(), itemAmountToAdjust);
        }
    }

    /**
     * @param requestedPositiveAmountToAdjust amount we are adjusting for that item
     * @param adjustedOrRepairedItems         list of all adjusted or repaired linking to this item
     * @return the amount we should really adjust based on whether or not the item got repaired
     */
    private static BigDecimal computeItemAdjustmentAmount(final BigDecimal requestedPositiveAmountToAdjust, final List<InvoiceItemModelDao> adjustedOrRepairedItems) {

        BigDecimal positiveAdjustedOrRepairedAmount = BigDecimal.ZERO;

        for (final InvoiceItemModelDao cur : adjustedOrRepairedItems) {
            // Adjustment or repair items are negative so we negate to make it positive
            positiveAdjustedOrRepairedAmount = positiveAdjustedOrRepairedAmount.add(cur.getAmount().negate());
        }
        return (positiveAdjustedOrRepairedAmount.compareTo(requestedPositiveAmountToAdjust) >= 0) ? BigDecimal.ZERO : requestedPositiveAmountToAdjust.subtract(positiveAdjustedOrRepairedAmount);
    }

    private InvoiceItemModelDao getInvoiceItemForId(final InvoiceModelDao invoice, final UUID invoiceItemId) throws InvoiceApiException {
        for (final InvoiceItemModelDao invoiceItem : invoice.getInvoiceItems()) {
            if (invoiceItem.getId().equals(invoiceItemId)) {
                return invoiceItem;
            }
        }
        throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_NOT_FOUND, invoiceItemId);
    }

    public BigDecimal computePositiveRefundAmount(final InvoicePaymentModelDao payment, final BigDecimal requestedRefundAmount, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts) throws InvoiceApiException {
        final BigDecimal maxRefundAmount = payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
        final BigDecimal requestedPositiveAmount = requestedRefundAmount == null ? maxRefundAmount : requestedRefundAmount;
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
        if (amountFromItems.compareTo(BigDecimal.ZERO) != 0 && requestedPositiveAmount.compareTo(amountFromItems) < 0) {
            throw new InvoiceApiException(ErrorCode.REFUND_AMOUNT_DONT_MATCH_ITEMS_TO_ADJUST, requestedPositiveAmount, amountFromItems);
        }
        return requestedPositiveAmount;
    }

    public List<InvoiceModelDao> getUnpaidInvoicesByAccountFromTransaction(final UUID accountId,
                                                                           final List<Tag> invoicesTags,
                                                                           final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                                                           @Nullable final LocalDate startDate,
                                                                           final LocalDate upToDate,
                                                                           final InternalTenantContext context) {
        final List<InvoiceModelDao> invoices = getAllInvoicesByAccountFromTransaction(false, true, invoicesTags, entitySqlDaoWrapperFactory, context);
        log.debug("Found invoices={} for accountId={}", invoices, accountId);
        return getUnpaidInvoicesByAccountFromTransaction(invoices, startDate, upToDate);
    }

    public List<InvoiceModelDao> getUnpaidInvoicesByAccountFromTransaction(final List<InvoiceModelDao> invoices, @Nullable final LocalDate startDate, @Nullable final LocalDate upToDate) {
        final Collection<InvoiceModelDao> unpaidInvoices = invoices.stream()
                .filter(in -> {
                    final InvoiceModelDao invoice = (in.getParentInvoice() == null) ? in : in.getParentInvoice();
                    final BigDecimal balance = InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(invoice);
                    log.debug("Computed balance={} for invoice={}", balance, in);
                    return InvoiceStatus.COMMITTED.equals(in.getStatus()) &&
                           (balance.compareTo(BigDecimal.ZERO) >= 1 && !in.isWrittenOff()) &&
                           (startDate == null || in.getTargetDate() == null || in.getTargetDate().compareTo(startDate) >= 0) &&
                           (upToDate == null || in.getTargetDate() == null || in.getTargetDate().compareTo(upToDate) <= 0);
                })
                .collect(Collectors.toUnmodifiableList());
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
    public InvoiceItemModelDao createAdjustmentItem(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final UUID invoiceId, final UUID invoiceItemId,
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
        final BigDecimal amountToAdjust = Objects.requireNonNullElse(positiveAdjAmount, invoiceItemToBeAdjusted.getAmount());
        // TODO - should we enforce the currency (and respect the original one) here if the amount passed was null?
        final Currency currencyForAdjustment = Objects.requireNonNullElse(currency, invoiceItemToBeAdjusted.getCurrency());

        // Finally, create the adjustment
        // Note! The amount is negated here!
        return new InvoiceItemModelDao(context.getCreatedDate(), InvoiceItemType.ITEM_ADJ, invoiceItemToBeAdjusted.getInvoiceId(), invoiceItemToBeAdjusted.getAccountId(),
                                       null, null, null, invoiceItemToBeAdjusted.getProductName(), invoiceItemToBeAdjusted.getPlanName(), invoiceItemToBeAdjusted.getPhaseName(), invoiceItemToBeAdjusted.getUsageName(),
                                        invoiceItemToBeAdjusted.getCatalogEffectiveDate(), effectiveDate, effectiveDate, amountToAdjust.negate(), null, currencyForAdjustment, invoiceItemToBeAdjusted.getId());
    }

    public void populateChildren(final InvoiceModelDao invoice, final List<Tag> invoicesTags, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        // !!! Anything updated here needs to also be reflected in   void populateChildren(final Iterable<InvoiceModelDao> invoices,...)
        setInvoiceItemsWithinTransaction(invoice, entitySqlDaoWrapperFactory, context);
        setInvoicePaymentsWithinTransaction(invoice, entitySqlDaoWrapperFactory, context);
        setTrackingIdsFromTransaction(invoice, entitySqlDaoWrapperFactory, context);
        setInvoiceWrittenOff(invoice, invoicesTags);
        setInvoiceRepaired(invoice, entitySqlDaoWrapperFactory, context);
        if (!invoice.isParentInvoice()) {
            setParentInvoice(invoice, invoicesTags, entitySqlDaoWrapperFactory, context);
        }
    }

    public void populateChildren(final Iterable<InvoiceModelDao> invoices, final List<Tag> invoicesTags, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        // !!! Anything updated here needs to also be reflected in   void populateChildren(final InvoiceModelDao invoice,...)
        if (Iterables.isEmpty(invoices)) {
            return;
        }

        setInvoiceItemsWithinTransaction(invoices, entitySqlDaoWrapperFactory, context);
        setInvoicePaymentsWithinTransaction(invoices, entitySqlDaoWrapperFactory, context);
        setTrackingIdsFromTransaction(invoices, entitySqlDaoWrapperFactory, context);
        setInvoicesWrittenOff(invoices, invoicesTags);
        setInvoicesRepaired(invoices, entitySqlDaoWrapperFactory, context);

        final Iterable<InvoiceModelDao> nonParentInvoices = Iterables.toStream(invoices)
                .filter(invoice -> !invoice.isParentInvoice())
                .collect(Collectors.toUnmodifiableList());

        if (!Iterables.isEmpty(nonParentInvoices)) {
            setParentInvoice(nonParentInvoices, invoicesTags, entitySqlDaoWrapperFactory, context);
        }
    }

    public List<InvoiceModelDao> getAllInvoicesByAccountFromTransaction(final Boolean includeVoidedInvoices,
    																	final Boolean includeInvoiceComponents, 
                                                                        final List<Tag> invoicesTags,
                                                                        final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                                                        final InternalTenantContext context) {
        final List<InvoiceModelDao> invoices = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class).getByAccountRecordId(context);
        final List<InvoiceModelDao> filtered = invoices.stream()
                                                       .filter(invoice -> includeVoidedInvoices || !InvoiceStatus.VOID.equals(invoice.getStatus()))
                                                       .collect(Collectors.toUnmodifiableList());
        if (includeInvoiceComponents) {
            populateChildren(filtered, invoicesTags, entitySqlDaoWrapperFactory, context);
        }
        return invoices;
    }

    public BigDecimal getRemainingAmountPaidFromTransaction(final UUID invoicePaymentId, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final BigDecimal amount = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getRemainingAmountPaid(invoicePaymentId.toString(), context);
        return amount == null ? BigDecimal.ZERO : amount;
    }


    private void setInvoiceItemsWithinTransaction(final InvoiceModelDao invoice, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final InvoiceItemSqlDao invoiceItemSqlDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
        final List<InvoiceItemModelDao> invoiceItems = invoiceItemSqlDao.getInvoiceItemsForInvoices(List.of(invoice.getId()), context);
        // Make sure to set invoice items to a non-null value
        final List<InvoiceItemModelDao> invoiceItemsForInvoice = Objects.requireNonNullElse(invoiceItems, Collections.emptyList());
        log.debug("Found items={} for invoice={}", invoiceItemsForInvoice, invoice);
        invoice.addInvoiceItems(invoiceItemsForInvoice);
    }


    private Iterable<UUID> mapInvoicesToInvoiceIds(final Iterable<InvoiceModelDao> invoices) {
        return Iterables.toStream(invoices)
                .map(InvoiceModelDao::getId)
                .collect(Collectors.toUnmodifiableList());
    }


    private void setInvoiceItemsWithinTransaction(final Iterable<InvoiceModelDao> invoices, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final InvoiceItemSqlDao invoiceItemSqlDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
        final Iterable<UUID> invoiceIds = mapInvoicesToInvoiceIds(invoices);
        final List<InvoiceItemModelDao> invoiceItemsForAccount = invoiceItemSqlDao.getInvoiceItemsForInvoices(invoiceIds, context);

        final Map<UUID, List<InvoiceItemModelDao>> invoiceItemsPerInvoiceId = new HashMap<>();
        for (final InvoiceItemModelDao item : invoiceItemsForAccount) {
            if (invoiceItemsPerInvoiceId.get(item.getInvoiceId()) == null) {
                invoiceItemsPerInvoiceId.put(item.getInvoiceId(), new LinkedList<InvoiceItemModelDao>());
            }
            invoiceItemsPerInvoiceId.get(item.getInvoiceId()).add(item);
        }

        for (final InvoiceModelDao invoice : invoices) {
            // Make sure to set invoice items to a non-null value
            final List<InvoiceItemModelDao> invoiceItemsForInvoice = Objects.requireNonNullElse(invoiceItemsPerInvoiceId.get(invoice.getId()), Collections.emptyList());
            log.debug("Found items={} for invoice={}", invoiceItemsForInvoice, invoice);
            invoice.addInvoiceItems(invoiceItemsForInvoice);
        }
    }

    private void setInvoicePaymentsWithinTransaction(final InvoiceModelDao invoice, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final InvoicePaymentSqlDao invoicePaymentSqlDao = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class);
        final List<InvoicePaymentModelDao> invoicePayments = invoicePaymentSqlDao.getAllPaymentsForInvoiceIncludedInit(invoice.getId().toString(), context);
        log.debug("Found payments={} for invoice={}", invoicePayments, invoice);
        invoice.addPayments(invoicePayments);

        for (final InvoicePaymentModelDao invoicePayment : invoicePayments) {
            if (invoicePayment.getCurrency() != invoicePayment.getProcessedCurrency()) {
                // If any entry is set with a different processed currency, we use it as a processed currency.
                invoice.setProcessedCurrency(invoicePayment.getProcessedCurrency());
                break;
            }
        }
    }

    private void setInvoicePaymentsWithinTransaction(final Iterable<InvoiceModelDao> invoices, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final InvoicePaymentSqlDao invoicePaymentSqlDao = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class);
        final Iterable<UUID> invoiceIds = mapInvoicesToInvoiceIds(invoices);
        final List<InvoicePaymentModelDao> invoicePaymentsForAccount = invoicePaymentSqlDao.getPaymentsForInvoices(invoiceIds, context);

        final Map<UUID, List<InvoicePaymentModelDao>> invoicePaymentsPerInvoiceId = new HashMap<>();
        for (final InvoicePaymentModelDao invoicePayment : invoicePaymentsForAccount) {
            if (invoicePaymentsPerInvoiceId.get(invoicePayment.getInvoiceId()) == null) {
                invoicePaymentsPerInvoiceId.put(invoicePayment.getInvoiceId(), new LinkedList<InvoicePaymentModelDao>());
            }
            invoicePaymentsPerInvoiceId.get(invoicePayment.getInvoiceId()).add(invoicePayment);
        }

        for (final InvoiceModelDao invoice : invoices) {
            // Make sure to set payments to a non-null value
            final List<InvoicePaymentModelDao> invoicePaymentsForInvoice = Objects.requireNonNullElse(invoicePaymentsPerInvoiceId.get(invoice.getId()), Collections.emptyList());
            log.debug("Found payments={} for invoice={}", invoicePaymentsForInvoice, invoice);
            invoice.addPayments(invoicePaymentsForInvoice);

            for (final InvoicePaymentModelDao invoicePayment : invoicePaymentsForInvoice) {
                if (invoicePayment.getCurrency() != invoicePayment.getProcessedCurrency()) {
                    // If any entry is set with a different processed currency, we use it as a processed currency.
                    invoice.setProcessedCurrency(invoicePayment.getProcessedCurrency());
                    break;
                }
            }
        }
    }

    private void setInvoiceWrittenOff(final InvoiceModelDao invoice, final List<Tag> invoicesTags) {
        setInvoicesWrittenOff(List.of(invoice), invoicesTags);
    }

    private void setInvoiceRepaired(final InvoiceModelDao invoice, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        setInvoicesRepaired(List.of(invoice), entitySqlDaoWrapperFactory, context);
    }

    private void setInvoicesWrittenOff(final Iterable<InvoiceModelDao> invoices, final List<Tag> invoicesTags) {
        filterForWrittenOff(invoicesTags).forEach(tag -> Iterables.toStream(invoices)
                                                                  .filter(input -> input.getId().equals(tag.getObjectId()))
                                                                  .findFirst()
                                                                  .ifPresent(foundInvoice -> foundInvoice.setIsWrittenOff(true)));
    }

    private Stream<String> mapInvoicesToInvoiceIdsStream(final Iterable<InvoiceModelDao> invoices) {
        return Iterables.toStream(invoices).map(input -> input.getId().toString());
    }

    private void setInvoicesRepaired(final Iterable<InvoiceModelDao> invoices, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final InvoiceItemSqlDao invoiceItemSqlDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
        final Iterable<String> invoiceIds = mapInvoicesToInvoiceIdsStream(invoices).collect(Collectors.toUnmodifiableList());
        if (Iterables.isEmpty(invoiceIds)) {
            return;
        }

        final Iterable<CounterMappings> repairedMapRes = invoiceItemSqlDao.getRepairMap(invoiceIds, context);
        final Map<String, Integer> repairedMap = CounterMappings.toMap(repairedMapRes);
        for (final InvoiceModelDao cur : invoices) {
            final Integer repairedItems = repairedMap.get(cur.getId().toString());
            if (repairedItems != null && repairedItems > 0) {
                cur.setRepaired(true);
            }
        }
    }


    private void setTrackingIdsFromTransaction(final InvoiceModelDao invoice, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        setTrackingIdsFromTransaction(List.of(invoice), entitySqlDaoWrapperFactory, context);
    }

    private void setTrackingIdsFromTransaction(final Iterable<InvoiceModelDao> invoices, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final Set<String> invoiceIds = mapInvoicesToInvoiceIdsStream(invoices).collect(Collectors.toUnmodifiableSet());

        final InvoiceTrackingSqlDao invoiceTrackingidSqlDao = entitySqlDaoWrapperFactory.become(InvoiceTrackingSqlDao.class);
        final List<InvoiceTrackingModelDao> trackingIds = invoiceTrackingidSqlDao.getTrackingsForInvoices(invoiceIds, context);

        final Map<UUID, List<InvoiceTrackingModelDao>> invoiceTrackingIdsPerInvoiceId = new HashMap<>();
        for (final InvoiceTrackingModelDao cur : trackingIds) {
            if (invoiceTrackingIdsPerInvoiceId.get(cur.getInvoiceId()) == null) {
                invoiceTrackingIdsPerInvoiceId.put(cur.getInvoiceId(), new LinkedList<>());
            }
            invoiceTrackingIdsPerInvoiceId.get(cur.getInvoiceId()).add(cur);
        }

        for (final InvoiceModelDao invoice : invoices) {
            if (invoiceTrackingIdsPerInvoiceId.get(invoice.getId()) != null) {
                final List<InvoiceTrackingModelDao> perInvoiceTrackingIds = invoiceTrackingIdsPerInvoiceId.get(invoice.getId());
                final Set<String> transform = perInvoiceTrackingIds.stream()
                        .map(InvoiceTrackingModelDao::getTrackingId)
                        .collect(Collectors.toUnmodifiableSet());
                invoice.addTrackingIds(transform);
            }
        }
    }

    private Iterable<Tag> filterForWrittenOff(final List<Tag> tags) {
        return tags.stream()
                .filter(input -> input.getTagDefinitionId().equals(ControlTagType.WRITTEN_OFF.getId()))
                .collect(Collectors.toUnmodifiableList());
    }

    private void setParentInvoice(final InvoiceModelDao invoice, final List<Tag> invoicesTags, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext childContext) {
        final InvoiceParentChildrenSqlDao invoiceParentChildrenSqlDao = entitySqlDaoWrapperFactory.become(InvoiceParentChildrenSqlDao.class);
        final List<InvoiceParentChildModelDao> mappings = invoiceParentChildrenSqlDao.getParentChildMappingsByChildInvoiceIds(List.of(invoice.getId().toString()), childContext);
        if (mappings.isEmpty()) {
            return;
        }

        Preconditions.checkState(mappings.size() == 1, String.format("Expected only one parent mapping for invoice %s", invoice.getId()));

        final UUID parentInvoiceId = mappings.get(0).getParentInvoiceId();
        final InvoiceSqlDao invoiceSqlDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);
        final InvoiceModelDao parentInvoice = invoiceSqlDao.getById(parentInvoiceId.toString(), childContext);

        final Long parentAccountRecordId = internalCallContextFactory.getRecordIdFromObject(parentInvoice.getAccountId(), ObjectType.ACCOUNT, internalCallContextFactory.createTenantContext(childContext));
        final InternalTenantContext parentContext = internalCallContextFactory.createInternalTenantContext(childContext.getTenantRecordId(), parentAccountRecordId);
        // Note the misnomer here, populateChildren simply populates the content of these invoices (unrelated to HA)
        populateChildren(parentInvoice, invoicesTags, entitySqlDaoWrapperFactory, parentContext);
        invoice.addParentInvoice(parentInvoice);
    }

    private void setParentInvoice(final Iterable<InvoiceModelDao> childInvoices, final List<Tag> invoicesTags, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext childContext) {
        final Collection<String> childInvoiceIds = new HashSet<String>();
        for (final InvoiceModelDao childInvoice : childInvoices) {
            childInvoiceIds.add(childInvoice.getId().toString());
        }

        // DAO: retrieve the mappings between parent and child invoices
        final InvoiceParentChildrenSqlDao invoiceParentChildrenSqlDao = entitySqlDaoWrapperFactory.become(InvoiceParentChildrenSqlDao.class);
        final List<InvoiceParentChildModelDao> mappings = invoiceParentChildrenSqlDao.getParentChildMappingsByChildInvoiceIds(childInvoiceIds, childContext);
        if (mappings.isEmpty()) {
            return;
        }

        final Map<UUID, InvoiceParentChildModelDao> mappingPerChildInvoiceId = new HashMap<UUID, InvoiceParentChildModelDao>();
        final Collection<String> parentInvoiceIdsAsStrings = new HashSet<String>();
        for (final InvoiceParentChildModelDao mapping : mappings) {
            mappingPerChildInvoiceId.put(mapping.getChildInvoiceId(), mapping);
            parentInvoiceIdsAsStrings.add(mapping.getParentInvoiceId().toString());
        }

        // DAO: retrieve all parents invoices in bulk, for all child invoices
        final InvoiceSqlDao invoiceSqlDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);
        final List<InvoiceModelDao> parentInvoices = invoiceSqlDao.getByIds(parentInvoiceIdsAsStrings, childContext);

        // Group the parent invoices by (parent) account id (most likely, we only have one parent account group, except in re-parenting cases)
        final Map<UUID, List<InvoiceModelDao>> parentInvoicesGroupedByParentAccountId = new HashMap<UUID, List<InvoiceModelDao>>();
        // Create also a convenient mapping (needed below later)
        final Map<UUID, InvoiceModelDao> parentInvoiceByParentInvoiceId = new HashMap<UUID, InvoiceModelDao>();
        for (final InvoiceModelDao parentInvoice : parentInvoices) {
            if (parentInvoicesGroupedByParentAccountId.get(parentInvoice.getAccountId()) == null) {
                parentInvoicesGroupedByParentAccountId.put(parentInvoice.getAccountId(), new LinkedList<InvoiceModelDao>());
            }
            parentInvoicesGroupedByParentAccountId.get(parentInvoice.getAccountId()).add(parentInvoice);

            parentInvoiceByParentInvoiceId.put(parentInvoice.getId(), parentInvoice);
        }

        // DAO: populate the parent invoices in bulk
        for (final Entry<UUID, List<InvoiceModelDao>> entry : parentInvoicesGroupedByParentAccountId.entrySet()) {
            final List<InvoiceModelDao> parentInvoicesForOneParentAccountId = entry.getValue();
            final Long parentAccountRecordId = internalCallContextFactory.getRecordIdFromObject(entry.getKey(), ObjectType.ACCOUNT, internalCallContextFactory.createTenantContext(childContext));
            final InternalTenantContext parentContext = internalCallContextFactory.createInternalTenantContext(childContext.getTenantRecordId(), parentAccountRecordId);
            // Note the misnomer here, populateChildren simply populates the content of these invoices (unrelated to HA)
            populateChildren(parentInvoicesForOneParentAccountId, invoicesTags, entitySqlDaoWrapperFactory, parentContext);
        }

        for (final InvoiceModelDao invoice : childInvoices) {
            final InvoiceParentChildModelDao mapping = mappingPerChildInvoiceId.get(invoice.getId());
            if (mapping == null) {
                continue;
            }

            final InvoiceModelDao parentInvoice = parentInvoiceByParentInvoiceId.get(mapping.getParentInvoiceId());
            if (parentInvoice != null) {
                invoice.addParentInvoice(parentInvoice);
            }
        }
    }
}
