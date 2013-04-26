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

package com.ning.billing.invoice.generator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.joda.time.Months;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.model.BillingMode;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.model.InAdvanceBillingMode;
import com.ning.billing.invoice.model.InvalidDateSequenceException;
import com.ning.billing.invoice.model.InvoicingConfiguration;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.invoice.model.RecurringInvoiceItemData;
import com.ning.billing.invoice.model.RepairAdjInvoiceItem;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.config.InvoiceConfig;
import com.ning.billing.util.svcapi.junction.BillingEvent;
import com.ning.billing.util.svcapi.junction.BillingEventSet;
import com.ning.billing.util.svcapi.junction.BillingModeType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;


/**
 * Terminology for repair scenarii:
 *
 * - A 'repaired' item is an item that was generated and that needs to be repaired because the plan changed for that subscription on that period of time
 * - The 'repair' item is the item that cancels the (to be) repaired item; the repair item amount might not match (to be) repaired item because:
 *   * the (to be) repaired item was already adjusted so we will only repair what is left
 *   * in case of partial repair we only repair the part that is not used
 * - The 'reparee' item is only present on disk-- in the existing item list -- in case of full repair; in that case it represents the portion of the item that should still
 *   be invoiced for the plan of the repaired item. In case of partial repair it is merged with the repair item and does not exist except as a virtual item in the proposed list
 *
 *
 *
 * Example. We had a 20 subscription for a given period; we charged that amount and later discovered that only 3/4 of the time period were used after which the subscription was cancelled (immediate canellation)
 *
 * Full repair logic:
 *
 * Invoice 1:                   Invoice 2:
 *           +20 (repaired)             +5 (reparee)
 *           -20 (repair)
 *
 * Partial repair logic:
 *
 * Invoice 1:                   Invoice 2: (N/A)
 *           +20 (repaired)
 *           -15 (repair)
 *
 * The current version of the code uses partial repair logic but is able to deal with 'full repair' scenarii.
 *
 */

public class DefaultInvoiceGenerator implements InvoiceGenerator {

    private static final Logger log = LoggerFactory.getLogger(DefaultInvoiceGenerator.class);
    private static final int ROUNDING_MODE = InvoicingConfiguration.getRoundingMode();
    private static final int NUMBER_OF_DECIMALS = InvoicingConfiguration.getNumberOfDecimals();

    private final Clock clock;
    private final InvoiceConfig config;

    @Inject
    public DefaultInvoiceGenerator(final Clock clock, final InvoiceConfig config) {
        this.clock = clock;
        this.config = config;
    }

    /*
     * adjusts target date to the maximum invoice target date, if future invoices exist
     */
    @Override
    public Invoice generateInvoice(final UUID accountId, @Nullable final BillingEventSet events,
                                   @Nullable final List<Invoice> existingInvoices,
                                   final LocalDate targetDate,
                                   final Currency targetCurrency) throws InvoiceApiException {
        if ((events == null) || (events.size() == 0) || events.isAccountAutoInvoiceOff()) {
            return null;
        }

        validateTargetDate(targetDate);

        final List<InvoiceItem> existingItems = new ArrayList<InvoiceItem>();
        if (existingInvoices != null) {
            for (final Invoice invoice : existingInvoices) {
                for (final InvoiceItem item : invoice.getInvoiceItems()) {
                    if (item.getSubscriptionId() == null || // Always include migration invoices, credits, external charges etc.
                        !events.getSubscriptionIdsWithAutoInvoiceOff()
                               .contains(item.getSubscriptionId())) { //don't add items with auto_invoice_off tag
                        existingItems.add(item);
                    }
                }
            }
        }

        final LocalDate adjustedTargetDate = adjustTargetDate(existingInvoices, targetDate);

        final Invoice invoice = new DefaultInvoice(accountId, clock.getUTCToday(), adjustedTargetDate, targetCurrency);
        final UUID invoiceId = invoice.getId();

        // Generate list of proposed invoice items based on billing events from junction-- proposed items are ALL items since beginning of time
        final List<InvoiceItem> proposedItems = generateInvoiceItems(invoiceId, accountId, events, adjustedTargetDate, targetCurrency);

        // Remove repaired and repair items -- since they never change and can't be regenerated
        removeRepairedAndRepairInvoiceItems(existingItems, proposedItems);

        // Remove from both lists the items in common
        removeMatchingInvoiceItems(existingItems, proposedItems);

        // Add repair items based on what is left in existing items
        addRepairItems(existingItems, proposedItems);

        // Finally add this new items on the new invoice
        invoice.addInvoiceItems(proposedItems);

        return proposedItems.size() != 0 ? invoice : null;
    }

    /**
     * At this point either we have 0 existingItem left or those left need to be repaired
     *
     * @param existingItems the list of remaining existing items
     * @param proposedItems the list of remaining proposed items
     */
    void addRepairItems(final List<InvoiceItem> existingItems, final List<InvoiceItem> proposedItems) {
        for (final InvoiceItem existingItem : existingItems) {
            if (existingItem.getInvoiceItemType() == InvoiceItemType.RECURRING ||
                existingItem.getInvoiceItemType() == InvoiceItemType.FIXED) {
                final BigDecimal existingAdjustedPositiveAmount = getAdjustedPositiveAmount(existingItems, existingItem.getId());
                final BigDecimal amountNegated = existingItem.getAmount() == null ? null : existingItem.getAmount().subtract(existingAdjustedPositiveAmount).negate();
                if (amountNegated != null && amountNegated.compareTo(BigDecimal.ZERO) < 0) {
                    final RepairAdjInvoiceItem candidateRepairItem = new RepairAdjInvoiceItem(existingItem.getInvoiceId(), existingItem.getAccountId(), existingItem.getStartDate(), existingItem.getEndDate(), amountNegated, existingItem.getCurrency(), existingItem.getId());
                    addRepairItem(existingItem, candidateRepairItem, proposedItems);
                }
            }
        }
    }

    /**
     * Add the repair item for the (yet to be) repairedItem. It will merge the candidateRepairItem with reparee item
     *
     * @param repairedItem        the item being repaired
     * @param candidateRepairItem the repair item we would have if we were to repair the full period
     * @param proposedItems       the list of proposed items
     */
    void addRepairItem(final InvoiceItem repairedItem, final RepairAdjInvoiceItem candidateRepairItem, final List<InvoiceItem> proposedItems) {
        InvoiceItem repareeItem = null;
        for (final InvoiceItem cur : proposedItems) {
            if (isRepareeItemForRepairedItem(repairedItem, cur)) {
                if (repareeItem == null) {
                    repareeItem = cur;
                } else {
                    log.warn("Found multiple reparee item for repaired invoice item " + repairedItem.getId());
                }
            }
        }
        // If we repaired the full period there is no repairee item
        if (repareeItem == null) {
            proposedItems.add(candidateRepairItem);
            return;
        }

        final BigDecimal partialRepairAmount = candidateRepairItem.getAmount().add(repareeItem.getAmount());
        if (partialRepairAmount.compareTo(BigDecimal.ZERO) < 0) {
            final RepairAdjInvoiceItem repairItem = new RepairAdjInvoiceItem(candidateRepairItem.getInvoiceId(), candidateRepairItem.getAccountId(), repareeItem.getEndDate(), candidateRepairItem.getEndDate(), partialRepairAmount, candidateRepairItem.getCurrency(), candidateRepairItem.getLinkedItemId());
            proposedItems.remove(repareeItem);
            proposedItems.add(repairItem);
        }

    }

    /**
     * Check whether or not the invoiceItem passed is the reparee for that repaired invoice item
     *
     * @param repairedInvoiceItem the repaired invoice item
     * @param invoiceItem         any invoice item to compare to
     * @return true if invoiceItem is the reparee for that repaired invoice item
     */
    @VisibleForTesting
    boolean isRepareeItemForRepairedItem(final InvoiceItem repairedInvoiceItem, final InvoiceItem invoiceItem) {
        return !repairedInvoiceItem.getId().equals(invoiceItem.getId()) &&
               repairedInvoiceItem.getInvoiceItemType().equals(invoiceItem.getInvoiceItemType()) &&
               // We assume the items are correctly created, so that the subscription id check implicitly
               // verifies that account id and bundle id matches
               repairedInvoiceItem.getSubscriptionId().equals(invoiceItem.getSubscriptionId()) &&
               // The reparee item is the "portion used" of the repaired item, hence it will have the same start date
               repairedInvoiceItem.getStartDate().compareTo(invoiceItem.getStartDate()) == 0 &&
               // Similarly, check the "portion used" is less than the original service end date. The check
               // is strict, otherwise there wouldn't be anything to repair
               ((repairedInvoiceItem.getEndDate() == null && invoiceItem.getEndDate() == null) ||
                (repairedInvoiceItem.getEndDate() != null && invoiceItem.getEndDate() != null &&
                 repairedInvoiceItem.getEndDate().isAfter(invoiceItem.getEndDate()))) &&
               // Finally, for the tricky part... In case of complete repairs, the new item will always meet all of the
               // following conditions: same type, subscription, start date. Depending on the catalog configuration, the end
               // date check could also match (e.g. repair from annual to monthly). For that scenario, we need to default
               // to catalog checks (the rate check is a lame check for versioned catalogs).
               Objects.firstNonNull(repairedInvoiceItem.getPlanName(), "").equals(Objects.firstNonNull(invoiceItem.getPlanName(), "")) &&
               Objects.firstNonNull(repairedInvoiceItem.getPhaseName(), "").equals(Objects.firstNonNull(invoiceItem.getPhaseName(), "")) &&
               Objects.firstNonNull(repairedInvoiceItem.getRate(), BigDecimal.ZERO).compareTo(Objects.firstNonNull(invoiceItem.getRate(), BigDecimal.ZERO)) == 0;
    }


    // We check to see if there are any adjustments that point to the item we are trying to repair
    // If we did any CREDIT_ADJ or REFUND_ADJ, then we unfortunately we can't know what is the intent
    // was as it applies to the full Invoice, so we ignore it. That might result in an extra positive CBA
    // that would have to be corrected manually. This is the best we can do, and administrators should always
    // use ITEM_ADJUSTMENT rather than CREDIT_ADJ or REFUND_ADJ when possible.
    //
    BigDecimal getAdjustedPositiveAmount(final List<InvoiceItem> existingItems, final UUID linkedItemId) {
        BigDecimal totalAdjustedOnItem = BigDecimal.ZERO;
        final Collection<InvoiceItem> invoiceItems = Collections2.filter(existingItems, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem item) {
                return item.getInvoiceItemType() == InvoiceItemType.ITEM_ADJ &&
                       item.getLinkedItemId() != null && item.getLinkedItemId().equals(linkedItemId);
            }
        });

        for (final InvoiceItem invoiceItem : invoiceItems) {
            totalAdjustedOnItem = totalAdjustedOnItem.add(invoiceItem.getAmount());
        }
        return totalAdjustedOnItem.negate();
    }

    private void validateTargetDate(final LocalDate targetDate) throws InvoiceApiException {
        final int maximumNumberOfMonths = config.getNumberOfMonthsInFuture();

        if (Months.monthsBetween(clock.getUTCToday(), targetDate).getMonths() > maximumNumberOfMonths) {
            throw new InvoiceApiException(ErrorCode.INVOICE_TARGET_DATE_TOO_FAR_IN_THE_FUTURE, targetDate.toString());
        }
    }

    private LocalDate adjustTargetDate(final List<Invoice> existingInvoices, final LocalDate targetDate) {
        if (existingInvoices == null) {
            return targetDate;
        }

        LocalDate maxDate = targetDate;

        for (final Invoice invoice : existingInvoices) {
            if (invoice.getTargetDate().isAfter(maxDate)) {
                maxDate = invoice.getTargetDate();
            }
        }
        return maxDate;
    }

    /*
     * Removes all matching items from both submitted collections
     */
    void removeMatchingInvoiceItems(final List<InvoiceItem> existingInvoiceItems,
                                    final List<InvoiceItem> proposedItems) {
        // We can't just use sets here as order matters (we want to keep duplicated in existingInvoiceItems)
        final Iterator<InvoiceItem> proposedItemIterator = proposedItems.iterator();
        while (proposedItemIterator.hasNext()) {
            final InvoiceItem proposedItem = proposedItemIterator.next();

            final Iterator<InvoiceItem> existingItemIterator = existingInvoiceItems.iterator();
            while (existingItemIterator.hasNext()) {
                final InvoiceItem existingItem = existingItemIterator.next();
                if (existingItem.matches(proposedItem)) {
                    existingItemIterator.remove();
                    proposedItemIterator.remove();
                    break;
                }
            }
        }
    }

    /**
     * Remove from the existing item list all repaired items-- both repaired and repair
     * If this is a partial repair, we also need to find the reparee from the proposed list
     * and remove it.
     *
     * @param existingItems input list of existing items
     * @param proposedItems input list of proposed item
     */
    void removeRepairedAndRepairInvoiceItems(final List<InvoiceItem> existingItems, final List<InvoiceItem> proposedItems) {

        final List<UUID> itemsToRemove = new ArrayList<UUID>();
        for (final InvoiceItem item : existingItems) {
            if (item.getInvoiceItemType() == InvoiceItemType.REPAIR_ADJ) {
                itemsToRemove.add(item.getId());
                itemsToRemove.add(item.getLinkedItemId());

                final InvoiceItem repairedInvoiceItem = getRepairedInvoiceItem(item.getLinkedItemId(), existingItems);
                // if this is a full repair there is no reparee so nothing to remove; if not reparee needs to be removed from proposed list
                if (!isFullRepair(repairedInvoiceItem, item, existingItems)) {
                    removeProposedRepareeForPartialrepair(repairedInvoiceItem, proposedItems);

                }
            }
        }
        final Iterator<InvoiceItem> iterator = existingItems.iterator();
        while (iterator.hasNext()) {
            final InvoiceItem item = iterator.next();
            if (itemsToRemove.contains(item.getId())) {
                iterator.remove();
            }
        }
    }

    /**
     * A full repair is one when the whole period was repaired. we reconstruct all the adjustment + repair pointing to the repaired item
     * and if the amount matches this is a full repair.
     *
     * @param repairedItem  the repaired item
     * @param repairItem    the repair item
     * @param existingItems the list of existing items
     * @return true if this is a full repair.
     */
    private boolean isFullRepair(final InvoiceItem repairedItem, final InvoiceItem repairItem, final List<InvoiceItem> existingItems) {

        final BigDecimal adjustedPositiveAmount = getAdjustedPositiveAmount(existingItems, repairedItem.getId());
        final BigDecimal repairAndAdjustedPositiveAmount = repairItem.getAmount().negate().add(adjustedPositiveAmount);
        return (repairedItem.getAmount().compareTo(repairAndAdjustedPositiveAmount) == 0);
    }

    /**
     * Removes the reparee from proposed list of items if it exists.
     *
     * @param repairedItem  the repaired item
     * @param proposedItems the list of existing items
     */
    protected void removeProposedRepareeForPartialrepair(final InvoiceItem repairedItem, final List<InvoiceItem> proposedItems) {
        final Iterator<InvoiceItem> it = proposedItems.iterator();
        while (it.hasNext()) {
            final InvoiceItem cur = it.next();
            if (isRepareeItemForRepairedItem(repairedItem, cur)) {
                it.remove();
                break;
            }
        }
    }


    private InvoiceItem getRepairedInvoiceItem(final UUID repairedInvoiceItemId, final List<InvoiceItem> existingItems) {
        for (InvoiceItem cur : existingItems) {
            if (cur.getId().equals(repairedInvoiceItemId)) {
                return cur;
            }
        }
        log.warn("Cannot find repaired invoice item " + repairedInvoiceItemId);
        return null;
    }


    private List<InvoiceItem> generateInvoiceItems(final UUID invoiceId, final UUID accountId, final BillingEventSet events,
                                                   final LocalDate targetDate, final Currency currency) throws InvoiceApiException {
        final List<InvoiceItem> items = new ArrayList<InvoiceItem>();

        if (events.size() == 0) {
            return items;
        }

        // Pretty-print the generated invoice items from the junction events
        final StringBuilder logStringBuilder = new StringBuilder("Invoice items generated for invoiceId ")
                .append(invoiceId)
                .append(" and accountId ")
                .append(accountId);

        final Iterator<BillingEvent> eventIt = events.iterator();
        BillingEvent nextEvent = eventIt.next();
        while (eventIt.hasNext()) {
            final BillingEvent thisEvent = nextEvent;
            nextEvent = eventIt.next();
            if (!events.getSubscriptionIdsWithAutoInvoiceOff().
                    contains(thisEvent.getSubscription().getId())) { // don't consider events for subscriptions that have auto_invoice_off
                final BillingEvent adjustedNextEvent = (thisEvent.getSubscription().getId() == nextEvent.getSubscription().getId()) ? nextEvent : null;
                items.addAll(processEvents(invoiceId, accountId, thisEvent, adjustedNextEvent, targetDate, currency, logStringBuilder));
            }
        }
        items.addAll(processEvents(invoiceId, accountId, nextEvent, null, targetDate, currency, logStringBuilder));

        log.info(logStringBuilder.toString());

        return items;
    }

    // Turn a set of events into a list of invoice items. Note that the dates on the invoice items will be rounded (granularity of a day)
    private List<InvoiceItem> processEvents(final UUID invoiceId, final UUID accountId, final BillingEvent thisEvent, @Nullable final BillingEvent nextEvent,
                                            final LocalDate targetDate, final Currency currency,
                                            final StringBuilder logStringBuilder) throws InvoiceApiException {
        final List<InvoiceItem> items = new ArrayList<InvoiceItem>();

        // Handle fixed price items
        final InvoiceItem fixedPriceInvoiceItem = generateFixedPriceItem(invoiceId, accountId, thisEvent, targetDate, currency);
        if (fixedPriceInvoiceItem != null) {
            items.add(fixedPriceInvoiceItem);
        }

        // Handle recurring items
        final BillingPeriod billingPeriod = thisEvent.getBillingPeriod();
        if (billingPeriod != BillingPeriod.NO_BILLING_PERIOD) {
            final BillingMode billingMode = instantiateBillingMode(thisEvent.getBillingMode());
            final LocalDate startDate = new LocalDate(thisEvent.getEffectiveDate(), thisEvent.getTimeZone());

            if (!startDate.isAfter(targetDate)) {
                final LocalDate endDate = (nextEvent == null) ? null : new LocalDate(nextEvent.getEffectiveDate(), nextEvent.getTimeZone());

                final int billCycleDayLocal = thisEvent.getBillCycleDayLocal();

                final List<RecurringInvoiceItemData> itemData;
                try {
                    itemData = billingMode.calculateInvoiceItemData(startDate, endDate, targetDate, billCycleDayLocal, billingPeriod);
                } catch (InvalidDateSequenceException e) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_INVALID_DATE_SEQUENCE, startDate, endDate, targetDate);
                }

                for (final RecurringInvoiceItemData itemDatum : itemData) {
                    final BigDecimal rate = thisEvent.getRecurringPrice();

                    if (rate != null) {
                        final BigDecimal amount = itemDatum.getNumberOfCycles().multiply(rate).setScale(NUMBER_OF_DECIMALS, ROUNDING_MODE);

                        final RecurringInvoiceItem recurringItem = new RecurringInvoiceItem(invoiceId,
                                                                                            accountId,
                                                                                            thisEvent.getSubscription().getBundleId(),
                                                                                            thisEvent.getSubscription().getId(),
                                                                                            thisEvent.getPlan().getName(),
                                                                                            thisEvent.getPlanPhase().getName(),
                                                                                            itemDatum.getStartDate(), itemDatum.getEndDate(),
                                                                                            amount, rate, currency);
                        items.add(recurringItem);
                    }
                }
            }
        }

        // For debugging purposes
        logStringBuilder.append("\n")
                        .append(thisEvent);
        for (final InvoiceItem item : items) {
            logStringBuilder.append("\n\t")
                            .append(item);
        }

        return items;
    }

    private BillingMode instantiateBillingMode(final BillingModeType billingMode) {
        switch (billingMode) {
            case IN_ADVANCE:
                return new InAdvanceBillingMode();
            default:
                throw new UnsupportedOperationException();
        }
    }

    InvoiceItem generateFixedPriceItem(final UUID invoiceId, final UUID accountId, final BillingEvent thisEvent,
                                       final LocalDate targetDate, final Currency currency) {
        final LocalDate roundedStartDate = new LocalDate(thisEvent.getEffectiveDate(), thisEvent.getTimeZone());

        if (roundedStartDate.isAfter(targetDate)) {
            return null;
        } else {
            final BigDecimal fixedPrice = thisEvent.getFixedPrice();

            if (fixedPrice != null) {
                return new FixedPriceInvoiceItem(invoiceId, accountId, thisEvent.getSubscription().getBundleId(),
                                                 thisEvent.getSubscription().getId(),
                                                 thisEvent.getPlan().getName(), thisEvent.getPlanPhase().getName(),
                                                 roundedStartDate, fixedPrice, currency);
            } else {
                return null;
            }
        }
    }
}
