/*
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

package org.killbill.billing.invoice.usage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.generator.BillingIntervalDetail;
import org.killbill.billing.invoice.model.UsageInvoiceItem;
import org.killbill.billing.invoice.usage.details.UsageInArrearAggregate;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.usage.RawUsage;
import org.killbill.billing.usage.api.RolledUpUnit;
import org.killbill.billing.usage.api.RolledUpUsage;
import org.killbill.billing.util.config.definition.InvoiceConfig.UsageDetailMode;
import org.killbill.billing.util.jackson.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import static org.killbill.billing.invoice.usage.UsageUtils.getCapacityInArrearUnitTypes;
import static org.killbill.billing.invoice.usage.UsageUtils.getConsumableInArrearUnitTypes;

/**
 * There is one such class per subscriptionId, matching a given in arrear/consumable usage section and
 * referenced through a contiguous list of billing events.
 */
public abstract class ContiguousIntervalUsageInArrear {

    private static final Logger log = LoggerFactory.getLogger(ContiguousIntervalUsageInArrear.class);

    protected final List<LocalDate> transitionTimes;
    protected final List<BillingEvent> billingEvents;

    protected final Usage usage;
    protected final Set<String> unitTypes;
    protected final List<RawUsage> rawSubscriptionUsage;
    protected final LocalDate targetDate;
    protected final UUID accountId;
    protected final UUID invoiceId;
    protected final AtomicBoolean isBuilt;
    protected final LocalDate rawUsageStartDate;
    protected final InternalTenantContext internalTenantContext;
    protected final UsageDetailMode usageDetailMode;
    protected static final ObjectMapper objectMapper = new ObjectMapper();

    public ContiguousIntervalUsageInArrear(final Usage usage,
                                           final UUID accountId,
                                           final UUID invoiceId,
                                           final List<RawUsage> rawSubscriptionUsage,
                                           final LocalDate targetDate,
                                           final LocalDate rawUsageStartDate,
                                           final UsageDetailMode usageDetailMode,
                                           final InternalTenantContext internalTenantContext) {
        this.usage = usage;
        this.accountId = accountId;
        this.invoiceId = invoiceId;
        this.unitTypes = usage.getUsageType() == UsageType.CAPACITY ? getCapacityInArrearUnitTypes(usage) : getConsumableInArrearUnitTypes(usage);
        this.rawSubscriptionUsage = filterInputRawUsage(rawSubscriptionUsage);
        this.targetDate = targetDate;
        this.rawUsageStartDate = rawUsageStartDate;
        this.internalTenantContext = internalTenantContext;
        this.billingEvents = Lists.newLinkedList();
        this.transitionTimes = Lists.newLinkedList();
        this.isBuilt = new AtomicBoolean(false);
        this.usageDetailMode = usageDetailMode;
    }


    /**
     * Builds the transitionTimes associated to that usage section. Those are determined based on billing events for when to start and when to stop,
     * the per usage billingPeriod and finally the targetDate.
     * <p/>
     * Those transition dates define the well defined billing granularity periods that should be billed for that specific usage section.
     *
     * @param closedInterval whether there was a last billing event referencing the usage section or whether this is ongoing and
     *                       then targetDate will define the endDate.
     */
    public ContiguousIntervalUsageInArrear build(final boolean closedInterval) {

        Preconditions.checkState(!isBuilt.get());
        Preconditions.checkState((!closedInterval && billingEvents.size() >= 1) ||
                                 (closedInterval && billingEvents.size() >= 2));

        final LocalDate startDate = internalTenantContext.toLocalDate(billingEvents.get(0).getEffectiveDate());
        if (targetDate.isBefore(startDate)) {
            return this;
        }
        final LocalDate endDate = closedInterval ? internalTenantContext.toLocalDate(billingEvents.get(billingEvents.size() - 1).getEffectiveDate()) : targetDate;

        final BillingIntervalDetail bid = new BillingIntervalDetail(startDate, endDate, targetDate, getBCD(), usage.getBillingPeriod(), usage.getBillingMode());

        int numberOfPeriod = 0;
        // First billingCycleDate prior startDate
        LocalDate nextBillCycleDate = bid.getFutureBillingDateFor(numberOfPeriod);
        if (startDate.compareTo(rawUsageStartDate) >= 0) {
            transitionTimes.add(startDate);
        }
        while (!nextBillCycleDate.isAfter(endDate)) {
            if (nextBillCycleDate.isAfter(startDate)) {
                if (nextBillCycleDate.compareTo(rawUsageStartDate) >= 0) {
                    transitionTimes.add(nextBillCycleDate);
                }
            }
            numberOfPeriod++;
            nextBillCycleDate = bid.getFutureBillingDateFor(numberOfPeriod);
        }
        if (closedInterval &&
            transitionTimes.size() > 0 &&
            endDate.isAfter(transitionTimes.get(transitionTimes.size() - 1))) {
            transitionTimes.add(endDate);
        }
        isBuilt.set(true);
        return this;
    }

    /**
     * Compute the missing usage invoice items based on what should be billed and what has been billed ($ amount comparison).
     *
     * @param existingUsage existing on disk usage items for the subscription
     * @throws CatalogApiException
     */
    public UsageInArrearItemsAndNextNotificationDate computeMissingItemsAndNextNotificationDate(final List<InvoiceItem> existingUsage) throws CatalogApiException, InvoiceApiException {

        Preconditions.checkState(isBuilt.get());

        if (transitionTimes.size() < 2) {
            return new UsageInArrearItemsAndNextNotificationDate(ImmutableList.<InvoiceItem>of(), computeNextNotificationDate());
        }

        final List<InvoiceItem> result = Lists.newLinkedList();
        final List<RolledUpUsage> allUsage = getRolledUpUsage();
        // Each RolledUpUsage 'ru' is for a specific time period and across all units
        for (final RolledUpUsage ru : allUsage) {

            //
            // Previously billed items:
            //
            // 1. Retrieves current price amount billed for that period of time (and usage section)
            final Iterable<InvoiceItem> billedItems = getBilledItems(ru.getStart(), ru.getEnd(), existingUsage);
            // 2. Verify whether previously built items have the item_details section
            final boolean areAllBilledItemsWithDetails = areAllBilledItemsWithDetails(billedItems);
            // 3. verify if we already billed that period - use to decide whether we should include $0 items when there is nothing to bill for.
            final boolean isPeriodPreviouslyBilled = !Iterables.isEmpty(billedItems);
            // 4. Computes total billed usage amount
            final BigDecimal billedUsage = computeBilledUsage(billedItems);

            final List<RolledUpUnit> rolledUpUnits = ru.getRolledUpUnits();

            final UsageInArrearAggregate toBeBilledUsageDetails = getToBeBilledUsageDetails(rolledUpUnits, billedItems, areAllBilledItemsWithDetails);
            final BigDecimal toBeBilledUsage = toBeBilledUsageDetails.getAmount();
            populateResults(ru.getStart(), ru.getEnd(), billedUsage, toBeBilledUsage, toBeBilledUsageDetails, areAllBilledItemsWithDetails, isPeriodPreviouslyBilled, result);
        }
        final LocalDate nextNotificationDate = computeNextNotificationDate();
        return new UsageInArrearItemsAndNextNotificationDate(result, nextNotificationDate);
    }

    protected abstract void populateResults(final LocalDate startDate, final LocalDate endDate, final BigDecimal billedUsage, final BigDecimal toBeBilledUsage, final UsageInArrearAggregate toBeBilledUsageDetails, final boolean areAllBilledItemsWithDetails, final boolean isPeriodPreviouslyBilled, final List<InvoiceItem> result) throws InvoiceApiException;

    protected abstract UsageInArrearAggregate getToBeBilledUsageDetails(final List<RolledUpUnit> rolledUpUnits, final Iterable<InvoiceItem> billedItems, final boolean areAllBilledItemsWithDetails) throws CatalogApiException;

    private boolean areAllBilledItemsWithDetails(final Iterable<InvoiceItem> billedItems) {
        boolean atLeastOneItemWithoutDetails = Iterables.any(billedItems, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return input.getItemDetails() == null || input.getItemDetails().isEmpty();
            }
        });
        return !atLeastOneItemWithoutDetails;
    }

    private LocalDate computeNextNotificationDate() {
        LocalDate result = null;
        final Iterator<BillingEvent> eventIt = billingEvents.iterator();
        BillingEvent nextEvent = eventIt.next();
        while (eventIt.hasNext()) {
            final BillingEvent thisEvent = nextEvent;
            nextEvent = eventIt.next();
            final LocalDate startDate = internalTenantContext.toLocalDate(thisEvent.getEffectiveDate());
            final LocalDate endDate = internalTenantContext.toLocalDate(nextEvent.getEffectiveDate());

            final BillingIntervalDetail bid = new BillingIntervalDetail(startDate, endDate, targetDate, thisEvent.getBillCycleDayLocal(), usage.getBillingPeriod(), BillingMode.IN_ARREAR);
            final LocalDate nextBillingCycleDate = bid.getNextBillingCycleDate();
            result = (result == null || result.compareTo(nextBillingCycleDate) < 0) ? nextBillingCycleDate : result;
        }

        final LocalDate startDate = internalTenantContext.toLocalDate(nextEvent.getEffectiveDate());
        final BillingIntervalDetail bid = new BillingIntervalDetail(startDate, null, targetDate, nextEvent.getBillCycleDayLocal(), usage.getBillingPeriod(), BillingMode.IN_ARREAR);
        final LocalDate nextBillingCycleDate = bid.getNextBillingCycleDate();
        result = (result == null || result.compareTo(nextBillingCycleDate) < 0) ? nextBillingCycleDate : result;
        return result;
    }


    @VisibleForTesting
    List<RolledUpUsage> getRolledUpUsage() {

        final List<RolledUpUsage> result = new ArrayList<RolledUpUsage>();

        final Iterator<RawUsage> rawUsageIterator = rawSubscriptionUsage.iterator();
        if (!rawUsageIterator.hasNext()) {
            return getEmptyRolledUpUsage();
        }


        //
        // Skip all items before our first transition date
        //
        // prevRawUsage keeps track of first unconsumed raw usage element
        RawUsage prevRawUsage = null;
        while (rawUsageIterator.hasNext()) {
            final RawUsage curRawUsage = rawUsageIterator.next();
            if (curRawUsage.getDate().compareTo(transitionTimes.get(0)) >= 0) {
                prevRawUsage = curRawUsage;
                break;
            }
        }

        // Optimize path where all raw usage items are outside or our transitionTimes range
        if (prevRawUsage == null || prevRawUsage.getDate().compareTo(transitionTimes.get(transitionTimes.size() - 1)) >= 0) {
            return getEmptyRolledUpUsage();
        }

        //
        // Loop through each interval [prevDate, curDate) and consume as many rawSubscriptionUsage elements within that range
        // to create one RolledUpUsage per interval.
        //
        LocalDate prevDate = null;
        for (final LocalDate curDate : transitionTimes) {

            if (prevDate != null) {

                // Allocate and initialize new perRangeUnitToAmount for this interval and populate with rawSubscriptionUsage items
                final Map<String, Long> perRangeUnitToAmount = new HashMap<String, Long>();
                for (String unitType : unitTypes) {
                    perRangeUnitToAmount.put(unitType, 0L);
                }


                // Start consuming prevRawUsage element if it exists and falls into the range
                if (prevRawUsage != null) {
                    if (prevRawUsage.getDate().compareTo(prevDate) >= 0 && prevRawUsage.getDate().compareTo(curDate) < 0) {
                        final Long currentAmount = perRangeUnitToAmount.get(prevRawUsage.getUnitType());
                        final Long updatedAmount = computeUpdatedAmount(currentAmount, prevRawUsage.getAmount());
                        perRangeUnitToAmount.put(prevRawUsage.getUnitType(), updatedAmount);
                        prevRawUsage = null;
                    }
                }

                //
                // If prevRawUsage != null it means that our first and current rawSubscriptionUsage does not fall into that interval; we can't
                // just 'continue' as we need to correctly set next 'prevDate'
                // If prevRawUsage == null, then consume as much as we can for that interval. Note that the stop condition requires consuming
                // one additional element which will become the prevRawUsage for the next interval.
                //
                if (prevRawUsage == null) {
                    while (rawUsageIterator.hasNext()) {
                        final RawUsage curRawUsage = rawUsageIterator.next();
                        if (curRawUsage.getDate().compareTo(curDate) >= 0) {
                            prevRawUsage = curRawUsage;
                            break;
                        }

                        final Long currentAmount = perRangeUnitToAmount.get(curRawUsage.getUnitType());
                        final Long updatedAmount = computeUpdatedAmount(currentAmount, curRawUsage.getAmount());
                        perRangeUnitToAmount.put(curRawUsage.getUnitType(), updatedAmount);
                    }
                }

                // If we did find some usage for that date range, let's populate the result
                if (!perRangeUnitToAmount.isEmpty()) {
                    final List<RolledUpUnit> rolledUpUnits = new ArrayList<RolledUpUnit>(perRangeUnitToAmount.size());
                    for (final String unitType : perRangeUnitToAmount.keySet()) {
                        rolledUpUnits.add(new DefaultRolledUpUnit(unitType, perRangeUnitToAmount.get(unitType)));
                    }
                    result.add(new DefaultRolledUpUsage(getSubscriptionId(), prevDate, curDate, rolledUpUnits));
                }
            }
            prevDate = curDate;
        }
        return result;
    }

    private List<RolledUpUsage> getEmptyRolledUpUsage() {
        final List<RolledUpUsage> result = new ArrayList<RolledUpUsage>();
        final LocalDate startDate = transitionTimes.get(transitionTimes.size() - 2);
        final LocalDate endDate = transitionTimes.get(transitionTimes.size() - 1);
        for (String unitType : unitTypes) {
            final List<RolledUpUnit> emptyRolledUptUnits = new ArrayList<RolledUpUnit>();
            emptyRolledUptUnits.add(new DefaultRolledUpUnit(unitType, 0L));
            final DefaultRolledUpUsage defaultForUnit = new DefaultRolledUpUsage(getSubscriptionId(), startDate, endDate, emptyRolledUptUnits);
            result.add(defaultForUnit);
        }
        return result;
    }

    /**
     * Based on usage type compute new amount
     *
     * @param currentAmount
     * @param newAmount
     * @return
     */
    private Long computeUpdatedAmount(@Nullable Long currentAmount, @Nullable Long newAmount) {

        currentAmount = currentAmount == null ? 0L : currentAmount;
        newAmount = newAmount == null ? 0L : newAmount;

        if (usage.getUsageType() == UsageType.CAPACITY) {
            return Math.max(currentAmount, newAmount);
        } else /* UsageType.CONSUMABLE */ {
            return currentAmount + newAmount;
        }
    }

    private List<RawUsage> filterInputRawUsage(final List<RawUsage> rawSubscriptionUsage) {
        final Iterable<RawUsage> filteredList = Iterables.filter(rawSubscriptionUsage, new Predicate<RawUsage>() {
            @Override
            public boolean apply(final RawUsage input) {
                return unitTypes.contains(input.getUnitType());
            }
        });
        return ImmutableList.copyOf(filteredList);
    }
    
    /**
     * @param filteredUsageForInterval the list of invoiceItem to consider
     * @return the price amount that was already billed for that period and usage section (across unitTypes)
     */
    @VisibleForTesting
    BigDecimal computeBilledUsage(final Iterable<InvoiceItem> filteredUsageForInterval) {

        Preconditions.checkState(isBuilt.get());
        BigDecimal billedAmount = BigDecimal.ZERO;
        for (final InvoiceItem ii : filteredUsageForInterval) {
            billedAmount = billedAmount.add(ii.getAmount());
        }
        // Return the billed $ amount (not the # of units)
        return billedAmount;
    }

    List<InvoiceItem> getBilledItems(final LocalDate startDate, final LocalDate endDate, final List<InvoiceItem> existingUsage) {

        Preconditions.checkState(isBuilt.get());
        final Iterable<InvoiceItem> filteredResult = Iterables.filter(existingUsage, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                if (input.getInvoiceItemType() != InvoiceItemType.USAGE) {
                    return false;
                }
                // STEPH what happens if we discover usage period that overlap (one side or both side) the [startDate, endDate] interval
                final UsageInvoiceItem usageInput = (UsageInvoiceItem) input;
                return usageInput.getUsageName().equals(usage.getName()) &&
                       usageInput.getStartDate().compareTo(startDate) >= 0 &&
                       usageInput.getEndDate().compareTo(endDate) <= 0;
            }
        });
        return ImmutableList.copyOf(filteredResult);
    }

    @VisibleForTesting
    List<LocalDate> getTransitionTimes() {
        return transitionTimes;
    }

    public void addBillingEvent(final BillingEvent event) {
        Preconditions.checkState(!isBuilt.get());
        billingEvents.add(event);
    }

    public Usage getUsage() {
        return usage;
    }

    public int getBCD() {
        return billingEvents.get(0).getBillCycleDayLocal();
    }

    public UUID getBundleId() {
        return billingEvents.get(0).getSubscription().getBundleId();
    }

    public UUID getSubscriptionId() {
        return billingEvents.get(0).getSubscription().getId();
    }

    // STEPH_USAGE planName/phaseName,BCD,... might not be correct if we changed plan but Usage section was exactly similar
    public String getPlanName() {
        return billingEvents.get(0).getPlan().getName();
    }

    public String getProductName() {
        return billingEvents.get(0).getPlan().getProduct().getName();
    }

    public String getPhaseName() {
        return billingEvents.get(0).getPlanPhase().getName();
    }

    public Currency getCurrency() {
        return billingEvents.get(0).getCurrency();
    }


    public class UsageInArrearItemsAndNextNotificationDate {

        private final List<InvoiceItem> invoiceItems;
        private final LocalDate nextNotificationDate;

        public UsageInArrearItemsAndNextNotificationDate(final List<InvoiceItem> invoiceItems, final LocalDate nextNotificationDate) {
            this.invoiceItems = invoiceItems;
            this.nextNotificationDate = nextNotificationDate;
        }

        public List<InvoiceItem> getInvoiceItems() {
            return invoiceItems;
        }

        public LocalDate getNextNotificationDate() {
            return nextNotificationDate;
        }
    }

    protected String toJson(final Object usageInArrearAggregate) {
        try {
            return objectMapper.writeValueAsString(usageInArrearAggregate);
        } catch (JsonProcessingException e) {
            Preconditions.checkState(false, e.getMessage());
            return null;
        }
    }

}
