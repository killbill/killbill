/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class InvoiceDaoHelper {

    private static final Logger log = LoggerFactory.getLogger(InvoiceDaoHelper.class);

    private final TagInternalApi tagInternalApi;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public InvoiceDaoHelper(final TagInternalApi tagInternalApi, final InternalCallContextFactory internalCallContextFactory) {
        this.tagInternalApi = tagInternalApi;
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
                                                        final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                                        final Map<UUID, BigDecimal> invoiceItemIdsWithNullAmounts,
                                                        final InternalTenantContext context) throws InvoiceApiException {
        // Populate the missing amounts for individual items, if needed
        final Map<UUID, BigDecimal> outputItemIdsWithAmounts = new HashMap<UUID, BigDecimal>();
        // Retrieve invoice before the Refund
        final InvoiceModelDao invoice = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class).getById(invoiceId, context);
        if (invoice != null) {
            populateChildren(invoice, entitySqlDaoWrapperFactory, context);
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


    private static void computeItemAdjustmentsForTargetInvoiceItem(final InvoiceItemModelDao targetInvoiceItem, final List<InvoiceItemModelDao> adjustedOrRepairedItems, final Map<UUID, BigDecimal> inputAdjInvoiceItem, final Map<UUID, BigDecimal> outputAdjInvoiceItem) throws InvoiceApiException {
        final BigDecimal originalItemAmount = targetInvoiceItem.getAmount();
        final BigDecimal maxAdjLeftAmount = computeItemAdjustmentAmount(originalItemAmount, adjustedOrRepairedItems);

        final BigDecimal proposedItemAmount = inputAdjInvoiceItem.get(targetInvoiceItem.getId());
        if (proposedItemAmount != null && proposedItemAmount.compareTo(maxAdjLeftAmount) > 0) {
            throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_ADJUSTMENT_AMOUNT_INVALID, proposedItemAmount, maxAdjLeftAmount);
        }

        final BigDecimal itemAmountToAdjust = Objects.firstNonNull(proposedItemAmount, maxAdjLeftAmount);
        if (itemAmountToAdjust.compareTo(BigDecimal.ZERO) > 0) {
            outputAdjInvoiceItem.put(targetInvoiceItem.getId(), itemAmountToAdjust);
        }
    }

    /**
     * @param requestedPositiveAmountToAdjust amount we are adjusting for that item
     * @param adjustedOrRepairedItems        list of all adjusted or repaired linking to this item
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

    public List<InvoiceModelDao> getUnpaidInvoicesByAccountFromTransaction(final UUID accountId, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final LocalDate upToDate, final InternalTenantContext context) {
        final List<InvoiceModelDao> invoices = getAllInvoicesByAccountFromTransaction(entitySqlDaoWrapperFactory, context);
        log.debug("Found invoices={} for accountId={}", invoices, accountId);
        return getUnpaidInvoicesByAccountFromTransaction(invoices, upToDate);
    }

    public List<InvoiceModelDao> getUnpaidInvoicesByAccountFromTransaction(final List<InvoiceModelDao> invoices, @Nullable final LocalDate upToDate) {
        final Collection<InvoiceModelDao> unpaidInvoices = Collections2.filter(invoices, new Predicate<InvoiceModelDao>() {
            @Override
            public boolean apply(final InvoiceModelDao in) {
                final InvoiceModelDao invoice = (in.getParentInvoice() == null) ? in : in.getParentInvoice();
                final BigDecimal balance = InvoiceModelDaoHelper.getBalance(invoice);
                log.debug("Computed balance={} for invoice={}", balance, in);
                return InvoiceStatus.COMMITTED.equals(in.getStatus()) && (balance.compareTo(BigDecimal.ZERO) >= 1) &&
                       (upToDate == null || in.getTargetDate() == null || !in.getTargetDate().isAfter(upToDate));
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
        final BigDecimal amountToAdjust = Objects.firstNonNull(positiveAdjAmount, invoiceItemToBeAdjusted.getAmount());
        // TODO - should we enforce the currency (and respect the original one) here if the amount passed was null?
        final Currency currencyForAdjustment = Objects.firstNonNull(currency, invoiceItemToBeAdjusted.getCurrency());

        // Finally, create the adjustment
        // Note! The amount is negated here!
        return new InvoiceItemModelDao(context.getCreatedDate(), InvoiceItemType.ITEM_ADJ, invoiceItemToBeAdjusted.getInvoiceId(), invoiceItemToBeAdjusted.getAccountId(),
                                       null, null, null, null, null, null, effectiveDate, effectiveDate, amountToAdjust.negate(), null, currencyForAdjustment, invoiceItemToBeAdjusted.getId());
    }

    public void populateChildren(final InvoiceModelDao invoice, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        getInvoiceItemsWithinTransaction(ImmutableList.<InvoiceModelDao>of(invoice), entitySqlDaoWrapperFactory, context);
        getInvoicePaymentsWithinTransaction(ImmutableList.<InvoiceModelDao>of(invoice), entitySqlDaoWrapperFactory, context);
        setInvoiceWrittenOff(invoice, context);
        getParentInvoice(ImmutableList.<InvoiceModelDao>of(invoice), entitySqlDaoWrapperFactory, context);
    }

    public void populateChildren(final Iterable<InvoiceModelDao> invoices, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        getInvoiceItemsWithinTransaction(invoices, entitySqlDaoWrapperFactory, context);
        getInvoicePaymentsWithinTransaction(invoices, entitySqlDaoWrapperFactory, context);
        setInvoicesWrittenOff(invoices, context);
        getParentInvoice(invoices, entitySqlDaoWrapperFactory, context);
    }

    public List<InvoiceModelDao> getAllInvoicesByAccountFromTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final List<InvoiceModelDao> invoices = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class).getByAccountRecordId(context);
        populateChildren(invoices, entitySqlDaoWrapperFactory, context);
        return invoices;
    }

    public BigDecimal getRemainingAmountPaidFromTransaction(final UUID invoicePaymentId, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final BigDecimal amount = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getRemainingAmountPaid(invoicePaymentId.toString(), context);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private void getInvoiceItemsWithinTransaction(final Iterable<InvoiceModelDao> invoices, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final InvoiceItemSqlDao invoiceItemSqlDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
        final List<InvoiceItemModelDao> invoiceItemsForAccount = invoiceItemSqlDao.getByAccountRecordId(context);

        final Map<UUID, List<InvoiceItemModelDao>> invoiceItemsPerInvoiceId = new HashMap<UUID, List<InvoiceItemModelDao>>();
        for (final InvoiceItemModelDao item : invoiceItemsForAccount) {
            if (invoiceItemsPerInvoiceId.get(item.getInvoiceId()) == null) {
                invoiceItemsPerInvoiceId.put(item.getInvoiceId(), new LinkedList<InvoiceItemModelDao>());
            }
            invoiceItemsPerInvoiceId.get(item.getInvoiceId()).add(item);
        }

        for (final InvoiceModelDao invoice : invoices) {
            // Make sure to set invoice items to a non-null value
            final List<InvoiceItemModelDao> invoiceItemsForInvoice = Objects.firstNonNull(invoiceItemsPerInvoiceId.get(invoice.getId()), ImmutableList.<InvoiceItemModelDao>of());
            log.debug("Found items={} for invoice={}", invoiceItemsForInvoice, invoice);
            invoice.addInvoiceItems(invoiceItemsForInvoice);
        }
    }

    private void getInvoicePaymentsWithinTransaction(final Iterable<InvoiceModelDao> invoices, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final InvoicePaymentSqlDao invoicePaymentSqlDao = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class);
        final List<InvoicePaymentModelDao> invoicePaymentsForAccount = invoicePaymentSqlDao.getByAccountRecordId(context);;

        final Map<UUID, List<InvoicePaymentModelDao>> invoicePaymentsPerInvoiceId = new HashMap<UUID, List<InvoicePaymentModelDao>>();
        for (final InvoicePaymentModelDao invoicePayment : invoicePaymentsForAccount) {
            if (invoicePaymentsPerInvoiceId.get(invoicePayment.getInvoiceId()) == null) {
                invoicePaymentsPerInvoiceId.put(invoicePayment.getInvoiceId(), new LinkedList<InvoicePaymentModelDao>());
            }
            invoicePaymentsPerInvoiceId.get(invoicePayment.getInvoiceId()).add(invoicePayment);
        }

        for (final InvoiceModelDao invoice : invoices) {
            // Make sure to set payments to a non-null value
            final List<InvoicePaymentModelDao> invoicePaymentsForInvoice = Objects.firstNonNull(invoicePaymentsPerInvoiceId.get(invoice.getId()), ImmutableList.<InvoicePaymentModelDao>of());
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

    private void setInvoicesWrittenOff(final Iterable<InvoiceModelDao> invoices, final InternalTenantContext internalTenantContext) {

        final List<Tag> tags = tagInternalApi.getTagsForAccountType(ObjectType.INVOICE, false, internalTenantContext);
        final Iterable<Tag> writtenOffTags = filterForWrittenOff(tags);
        for (final Tag cur : writtenOffTags) {
            final InvoiceModelDao foundInvoice = Iterables.tryFind(invoices, new Predicate<InvoiceModelDao>() {
                @Override
                public boolean apply(final InvoiceModelDao input) {
                    return input.getId().equals(cur.getObjectId());
                }
            }).orNull();
            if (foundInvoice != null) {
                foundInvoice.setIsWrittenOff(true);
            }
        }
    }

    private void setInvoiceWrittenOff(final InvoiceModelDao invoice, final InternalTenantContext internalTenantContext) {
        final List<Tag> tags =  tagInternalApi.getTags(invoice.getId(), ObjectType.INVOICE, internalTenantContext);
        final Iterable<Tag> writtenOffTags = filterForWrittenOff(tags);
        invoice.setIsWrittenOff(writtenOffTags.iterator().hasNext());
    }

    private Iterable<Tag> filterForWrittenOff(final List<Tag> tags) {
        return Iterables.filter(tags, new Predicate<Tag>() {
            @Override
            public boolean apply(final Tag input) {
                return  input.getTagDefinitionId().equals(ControlTagType.WRITTEN_OFF.getId());
            }
        });
    }

    private void getParentInvoice(final Iterable<InvoiceModelDao> invoices, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext internalTenantContext) {

        final InvoiceSqlDao invoiceSqlDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);
        for (InvoiceModelDao invoice : invoices) {
            if (invoice.isParentInvoice()) continue;
            final InvoiceModelDao parentInvoice = invoiceSqlDao.getParentInvoiceByChildInvoiceId(invoice.getId().toString(), internalTenantContext);
            if (parentInvoice != null) {
                final Long parentAccountRecordId = internalCallContextFactory.getRecordIdFromObject(parentInvoice.getAccountId(), ObjectType.ACCOUNT, internalCallContextFactory.createTenantContext(internalTenantContext));
                final InternalTenantContext parentContext = internalCallContextFactory.createInternalTenantContext(internalTenantContext.getTenantRecordId(), parentAccountRecordId);
                populateChildren(parentInvoice, entitySqlDaoWrapperFactory, parentContext);
                invoice.addParentInvoice(parentInvoice);
            }
        }
    }

}
