/*
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.killbill.billing.ErrorCode;
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
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.TrackingRecordId;
import org.killbill.billing.invoice.model.UsageInvoiceItem;
import org.killbill.billing.invoice.usage.details.UsageInArrearAggregate;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.usage.api.RawUsageRecord;
import org.killbill.billing.usage.api.RolledUpUnit;
import org.killbill.clock.ClockUtil;
import org.killbill.commons.utils.Preconditions;
import org.killbill.commons.utils.annotation.VisibleForTesting;
import org.killbill.commons.utils.collect.Iterables;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.config.definition.InvoiceConfig.InArrearMode;
import org.killbill.billing.util.config.definition.InvoiceConfig.UsageDetailMode;
import org.killbill.billing.util.currency.KillBillMoney;
import org.killbill.billing.util.jackson.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import static org.killbill.billing.invoice.usage.UsageUtils.getCapacityInArrearUnitTypes;
import static org.killbill.billing.invoice.usage.UsageUtils.getConsumableInArrearUnitTypes;

/**
 * There is one such class per subscriptionId, matching a given in arrear/consumable usage section and
 * referenced through a contiguous list of billing events.
 */
public abstract class ContiguousIntervalUsageInArrear {

    protected static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(ContiguousIntervalUsageInArrear.class);

    protected final List<TransitionTime> transitionTimes;
    protected final List<BillingEvent> billingEvents;
    // Ordering is important here!
    protected final LinkedHashMap<BillingEvent, Set<String>> allSeenUnitTypes;

    protected final Usage usage;
    protected final Set<String> unitTypes;
    protected final LocalDate targetDate;
    protected final UUID accountId;
    protected final UUID invoiceId;
    protected final AtomicBoolean isBuilt;
    protected final DateTime rawUsageStartDate;
    protected final InvoiceConfig invoiceConfig;
    protected final InternalTenantContext internalTenantContext;
    protected final UsageDetailMode usageDetailMode;
    protected final UsageClockUtil usageClockUtil;

    @VisibleForTesting
    static class TransitionTime {

        private final DateTime date;
        private final BillingEvent targetBillingEvent;

        public TransitionTime(final DateTime date, final BillingEvent targetBillingEvent) {
            this.date = date;
            this.targetBillingEvent = targetBillingEvent;
        }

        public DateTime getDate() {
            return date;
        }

        public BillingEvent getTargetBillingEvent() {
            return targetBillingEvent;
        }

        @Override
        public String toString() {
            return "TransitionTime{" +
                   "date=" + date +
                   '}';
        }
    }

    public ContiguousIntervalUsageInArrear(final Usage usage,
                                           final UUID accountId,
                                           final UUID invoiceId,
                                           final LocalDate targetDate,
                                           final DateTime rawUsageStartDate,
                                           final UsageDetailMode usageDetailMode,
                                           final InvoiceConfig invoiceConfig,
                                           final InternalTenantContext internalTenantContext) {
        this.usage = usage;
        this.accountId = accountId;
        this.invoiceId = invoiceId;
        this.unitTypes = usage.getUsageType() == UsageType.CAPACITY ? getCapacityInArrearUnitTypes(usage) : getConsumableInArrearUnitTypes(usage);
        this.targetDate = targetDate;
        this.rawUsageStartDate = rawUsageStartDate;
        this.invoiceConfig = invoiceConfig;
        this.internalTenantContext = internalTenantContext;
        this.billingEvents = new LinkedList<>();
        this.allSeenUnitTypes = new LinkedHashMap<BillingEvent, Set<String>>();
        this.transitionTimes = new LinkedList<>();
        this.isBuilt = new AtomicBoolean(false);
        this.usageDetailMode = usageDetailMode;
        this.usageClockUtil = new UsageClockUtil(invoiceConfig);
    }

    /**
     * Builds the transitionTimes associated to that usage section.
     *
     * The transitionTimes are aligned on the BCD for that subscription and are separated by the billingPeriod, except for possibly the first
     * and last transitions. The billing events are all for this interval, meaning they all share the 'same' usage section except for the latest one
     * when closedInterval=true, as it marks the boundary to a new (ContiguousIntervalUsageInArrear) interval
     *
     * Example:
     *
     *  The following 3 billing events:
     *
     *                 BCD         BCD  targetDt
     *          CR     |    CHG    |    CANC
     *          |-----------|------------|
     *
     *  Leads to:
     *         |------|-----------|-----|
     *     Tr1(CR)  Tr2(CR)   Tr3(CHG) Tr4(CANC)
     *
     *     We end up with 4 transitions aligned on the BCD, except for first and last. Each transition points to the excepted billing event.
     *     (There is no transition for the CHG, since usage section is the same and so it does not represents a boundary)
     *
     * <p/>
     *
     * Those transition dates define the well defined billing granularity periods that should be billed for that specific usage section.
     *
     * @param closedInterval whether there was a last billing event referencing the usage section or whether this is ongoing and
     *                       then targetDate will define the endDate.
     */
    public ContiguousIntervalUsageInArrear build(final boolean closedInterval) {
        Preconditions.checkState(!isBuilt.get(), "!isBuilt");
        Preconditions.checkState((!closedInterval && billingEvents.size() >= 1) ||
                                 (closedInterval && billingEvents.size() >= 2),
                                 "closedInterval=%s, billingEvents.size()=%s", closedInterval, String.valueOf(billingEvents.size()));

        final BillingEvent firstBillingEvent = billingEvents.get(0);
        final DateTime startDate = firstBillingEvent.getEffectiveDate();

        final DateTime targetDateEndOfDay = usageClockUtil.toDateTimeAtEndOfDay(targetDate, internalTenantContext);
        if (targetDateEndOfDay.isBefore(startDate)) {
            return this;
        }

        final BillingEvent latestBillingEvent = billingEvents.get(billingEvents.size() - 1);
        // This should be enforced in the UsageInvoiceItemGenerator
        Preconditions.checkState(latestBillingEvent.getEffectiveDate().compareTo(targetDateEndOfDay) <= 0, "(remaining) billing events should be prior targetDate, latestBillingEvent=%s, targetDate=%s", latestBillingEvent.getEffectiveDate(), targetDate);
        final DateTime endDate = closedInterval ? latestBillingEvent.getEffectiveDate() : targetDateEndOfDay;

        if (startDate.compareTo(rawUsageStartDate) >= 0) {
            transitionTimes.add(new TransitionTime(startDate, firstBillingEvent));
        }

        // Go through each billing event (be) and add transitions as required:
        // - If we go from be1 -> be2, we don't include the transition from be2 -- will be included in the next round.
        //   (This makes sure that all transitions point to the right billing event be1)
        // - If we go from beLast -> endDt, we make sure we include the transition for endDt
        for (int i = 0; i < billingEvents.size(); i++) {
            final boolean lastEvent = (i == billingEvents.size() - 1);
            final BillingEvent billingEvent = billingEvents.get(i);
            final DateTime intervalStartDt = billingEvent.getEffectiveDate();
            final DateTime intervalEndDt = !lastEvent ?
                                            billingEvents.get(i + 1).getEffectiveDate() :
                                            endDate;

            addTransitionTimesForBillingEvent(billingEvent, intervalStartDt, intervalEndDt, billingEvent.getBillCycleDayLocal(), lastEvent);
        }


        // Symmetrical logic for endDate (on closedInterval) that we have with startDt
        if (closedInterval &&
            transitionTimes.size() > 0 &&
            (endDate.isAfter(transitionTimes.get(transitionTimes.size() - 1).getDate()) || /* Includes endDate if not already part (aligned) with a transition */
             (endDate.isEqual(startDate) && latestBillingEvent.getTransitionType() == SubscriptionBaseTransitionType.CANCEL))) /* Special treatment for final cancellation to make sure we include this usage point as part of the interval and bill for usage reported on cancellation date  */ {
            transitionTimes.add(new TransitionTime(endDate, latestBillingEvent));
        }
        isBuilt.set(true);
        return this;
    }

    private void addTransitionTimesForBillingEvent(final BillingEvent event, final DateTime startDate, final DateTime endDate, final int bcd, final boolean lastEvent) {
        final LocalDate startDateLocal = usageClockUtil.toLocalDate(startDate, internalTenantContext);
        final LocalDate endDateLocal =  usageClockUtil.toLocalDate(endDate, internalTenantContext);

        final BillingIntervalDetail bid = new BillingIntervalDetail(startDateLocal, endDateLocal, targetDate, bcd, usage.getBillingPeriod(), usage.getBillingMode(), InArrearMode.DEFAULT);

        int numberOfPeriod = 0;
        final LocalDate futureBillingDateFor = bid.getFutureBillingDateFor(numberOfPeriod);
        DateTime nextBillCycleDateEndOfDay =  futureBillingDateFor.compareTo(startDateLocal) == 0 ? startDate : usageClockUtil.toDateTimeAtStartOfDay(futureBillingDateFor, internalTenantContext);
        while (isBefore(nextBillCycleDateEndOfDay, endDate, lastEvent)) {
            if (nextBillCycleDateEndOfDay.compareTo(rawUsageStartDate) >= 0) {
                final TransitionTime lastTransition = transitionTimes.isEmpty() ? null : transitionTimes.get(transitionTimes.size() - 1);
                if (transitionTimes.isEmpty() || /* Adds transition if nothing already */
                    nextBillCycleDateEndOfDay.isAfter(lastTransition.getDate())) { /* Adds transition if strictly after the previous one */
                    transitionTimes.add(new TransitionTime(nextBillCycleDateEndOfDay, event));
                }
            }
            numberOfPeriod++;
            nextBillCycleDateEndOfDay = usageClockUtil.toDateTimeAtStartOfDay(bid.getFutureBillingDateFor(numberOfPeriod), internalTenantContext);
        }
    }
    
    private boolean isBefore(final DateTime transitionDate, final DateTime targetEndDate, final boolean beforeOrEqual) {
        if (beforeOrEqual) {
            return !transitionDate.isAfter(targetEndDate);
        } else {
            return transitionDate.isBefore(targetEndDate);
        }
    }


    /**
     * Compute the missing usage invoice items based on what should be billed and what has been billed ($ amount comparison).
     *
     * @param existingUsage existing on disk usage items for the subscription
     * @throws CatalogApiException
     */
    public UsageInArrearItemsAndNextNotificationDate computeMissingItemsAndNextNotificationDate(final List<RawUsageRecord> rawSubscriptionUsage,
                                                                                                final Set<TrackingRecordId> allExistingTrackingIds,
                                                                                                final List<InvoiceItem> existingUsage,
                                                                                                final boolean isDryRun) throws CatalogApiException, InvoiceApiException {
        Preconditions.checkState(isBuilt.get(), "#computeMissingItemsAndNextNotificationDate(): isBuilt");

        if (transitionTimes.size() < 2) {
            return new UsageInArrearItemsAndNextNotificationDate(Collections.emptyList(), Collections.emptySet(), computeNextNotificationDate());
        }

        final List<InvoiceItem> result = new LinkedList<>();

        final RolledUpUnitsWithTracking allUsageWithTracking = getRolledUpUsage(rawSubscriptionUsage);
        final List<RolledUpUsageWithMetadata> allUsage = allUsageWithTracking.getUsage();

        final Set<TrackingRecordId> allTrackingIds = allUsageWithTracking.getTrackingIds();

        final Set<TrackingRecordId> existingTrackingIds = extractTrackingIds(allExistingTrackingIds);

        final Set<TrackingRecordId> newTrackingIds = allTrackingIds.stream()
                .filter(allRecord -> existingTrackingIds.stream().noneMatch(existingRecord -> existingRecord.isSimilarRecord(allRecord)))
                .collect(Collectors.toUnmodifiableSet());

        // Each RolledUpUsage 'ru' is for a specific time period and across all units
        for (final RolledUpUsageWithMetadata ru : allUsage) {

            final LocalDate ruStartLocal = usageClockUtil.toLocalDate(ru.getStart(), internalTenantContext);
            final LocalDate ruEndLocal = usageClockUtil.toLocalDate(ru.getEnd(), internalTenantContext);

            final InvoiceItem existingOverlappingItem = isContainedIntoExistingUsage(ruStartLocal, ruEndLocal, existingUsage);
            if (existingOverlappingItem != null) {
                // In case of blocking situations, when re-running the invoicing code, already billed usage maybe have another start and end date
                // because of blocking events. We need to skip these to avoid double billing (see gotchas in testWithPartialBlockBilling).
                log.warn("Ignoring usage {} between start={} and end={} as it has already been invoiced by invoiceItemId={}",
                          usage.getName(), ru.getStart(), ru.getEnd(), existingOverlappingItem.getId());
                continue;
            }
            //
            // Previously billed items:
            //
            // 1. Retrieves current price amount billed for that period of time (and usage section)
            final Iterable<InvoiceItem> billedItems = getBilledItems(ruStartLocal, ruEndLocal, existingUsage);
            // 2. Verify whether previously built items have the item_details section
            final boolean areAllBilledItemsWithDetails = areAllBilledItemsWithDetails(billedItems);
            // 3. verify if we already billed that period - use to decide whether we should include $0 items when there is nothing to bill for.
            final boolean isPeriodPreviouslyBilled = !Iterables.isEmpty(billedItems);
            // 4. Computes total billed usage amount
            final BigDecimal billedUsage = computeBilledUsage(billedItems);

            final List<RolledUpUnit> rolledUpUnits = ru.getRolledUpUnits();

            final UsageInArrearAggregate toBeBilledUsageDetails = getToBeBilledUsageDetails(ru.getStart(), ru.getEnd(), rolledUpUnits, billedItems, areAllBilledItemsWithDetails, isDryRun);
            final BigDecimal toBeBilledUsageUnrounded = toBeBilledUsageDetails.getAmount();
            // See https://github.com/killbill/killbill/issues/1124
            final BigDecimal toBeBilledUsage = KillBillMoney.of(toBeBilledUsageUnrounded, getCurrency());
            populateResults(ru.getStart(), ru.getEnd(), ru.getCatalogEffectiveDate(), billedUsage, toBeBilledUsage, toBeBilledUsageDetails, areAllBilledItemsWithDetails, isPeriodPreviouslyBilled, isDryRun, result);

        }
        final LocalDate nextNotificationDate = computeNextNotificationDate();
        return new UsageInArrearItemsAndNextNotificationDate(result, newTrackingIds, nextNotificationDate);
    }

    private InvoiceItem isContainedIntoExistingUsage(final LocalDate startDate, final LocalDate endDate, final List<InvoiceItem> existingUsage) {
        Preconditions.checkState(isBuilt.get(), "#isContainedIntoExistingUsage(): isBuilt");
        if (existingUsage.isEmpty()) {
            return null;
        }

        // If we bill usage on the same day (e.g Plan change on the same day), this check becomes invalid so we disable it.
        final boolean isSameDay = startDate.compareTo(endDate) == 0;
        if (isSameDay) {
            return null;
        }

        return existingUsage.stream()
                .filter(input -> {
                    if (input.getInvoiceItemType() != InvoiceItemType.USAGE) {
                        return false;
                    }
                    final UsageInvoiceItem usageInput = (UsageInvoiceItem) input;
                    return usageInput.getUsageName().equals(usage.getName()) &&
                           ((startDate.compareTo(usageInput.getStartDate()) >= 0 && endDate.compareTo(usageInput.getEndDate()) < 0) ||
                            (startDate.compareTo(usageInput.getStartDate()) > 0 && endDate.compareTo(usageInput.getEndDate()) <= 0));
                })
                .findFirst().orElse(null);
    }

    protected abstract void populateResults(final DateTime startDate, final DateTime endDate, final DateTime catalogEffectiveDate, final BigDecimal billedUsage, final BigDecimal toBeBilledUsage, final UsageInArrearAggregate toBeBilledUsageDetails, final boolean areAllBilledItemsWithDetails, final boolean isPeriodPreviouslyBilled, final boolean isDryRun, final List<InvoiceItem> result) throws InvoiceApiException;

    protected abstract UsageInArrearAggregate getToBeBilledUsageDetails(final DateTime startDate, final DateTime endDate, final List<RolledUpUnit> rolledUpUnits, final Iterable<InvoiceItem> billedItems, final boolean areAllBilledItemsWithDetails, final boolean isDryRun) throws CatalogApiException;

    private boolean areAllBilledItemsWithDetails(final Iterable<InvoiceItem> billedItems) {
        return Iterables.toStream(billedItems).noneMatch(input -> input.getItemDetails() == null || input.getItemDetails().isEmpty());
    }

    private LocalDate computeNextNotificationDate() {
        LocalDate result = null;
        final Iterator<BillingEvent> eventIt = billingEvents.iterator();
        BillingEvent nextEvent = eventIt.next();
        while (eventIt.hasNext()) {
            final BillingEvent thisEvent = nextEvent;
            nextEvent = eventIt.next();
            final LocalDate startDate = usageClockUtil.toLocalDate(thisEvent.getEffectiveDate(), internalTenantContext);
            final LocalDate endDate = usageClockUtil.toLocalDate(nextEvent.getEffectiveDate(), internalTenantContext);

            final BillingIntervalDetail bid = new BillingIntervalDetail(startDate, endDate, targetDate, thisEvent.getBillCycleDayLocal(), usage.getBillingPeriod(), BillingMode.IN_ARREAR, InArrearMode.DEFAULT);
            final LocalDate nextBillingCycleDate = bid.getNextBillingCycleDate();
            result = (result == null || result.compareTo(nextBillingCycleDate) < 0) ? nextBillingCycleDate : result;
        }

        final LocalDate startDate = usageClockUtil.toLocalDate(nextEvent.getEffectiveDate(), internalTenantContext);

        final BillingIntervalDetail bid = new BillingIntervalDetail(startDate, null, targetDate, nextEvent.getBillCycleDayLocal(), usage.getBillingPeriod(), BillingMode.IN_ARREAR, InArrearMode.DEFAULT);
        final LocalDate nextBillingCycleDate = bid.getNextBillingCycleDate();
        result = (result == null || result.compareTo(nextBillingCycleDate) < 0) ? nextBillingCycleDate : result;
        return result;
    }
    
    @VisibleForTesting
    RolledUpUnitsWithTracking getRolledUpUsage(final List<RawUsageRecord> rawSubscriptionUsage) throws InvoiceApiException {

        final List<RolledUpUsageWithMetadata> result = new ArrayList<>();
        final Set<TrackingRecordId> trackingIds = new HashSet<>();

        final Iterator<RawUsageRecord> rawUsageIterator = rawSubscriptionUsage.iterator();
        if (!rawUsageIterator.hasNext()) {
            return new RolledUpUnitsWithTracking(getEmptyRolledUpUsage(), Collections.emptySet());
        }

        //
        // Skip all items before our first transition date
        //
        // 'prevRawUsage' keeps track of first unconsumed raw usage element
        RawUsageRecord prevRawUsage = null;
        while (rawUsageIterator.hasNext()) {
            final RawUsageRecord curRawUsage = rawUsageIterator.next();
            if (curRawUsage.getDate().compareTo(transitionTimes.get(0).getDate()) >= 0) {
                prevRawUsage = curRawUsage;
                break;
            }
        }

        // Optimize path where all raw usage items are outside or our transitionTimes range
        if (prevRawUsage == null || prevRawUsage.getDate().compareTo(transitionTimes.get(transitionTimes.size() - 1).getDate()) > 0) {
            return new RolledUpUnitsWithTracking(getEmptyRolledUpUsage(), Collections.emptySet());
        }

        //
        // Loop through each interval [prevDate, curDate) and consume as many rawSubscriptionUsage elements within that range
        // to create one RolledUpUsage per interval. The first loop will be used to set the 'prevDate'.
        //
        DateTime prevDate = null;
        DateTime prevCatalogEffectiveDate = null;
        for (int i = 0; i < transitionTimes.size(); i++) {
            final TransitionTime curTransition = transitionTimes.get(i);

            final DateTime curDate = curTransition.getDate();
            if (prevDate != null) {

                // Allocate and initialize new perRangeUnitToAmount for this interval and populate with rawSubscriptionUsage items
                final Map<String, BigDecimal> perRangeUnitToAmount = new HashMap<>();
                for (String unitType : unitTypes) {
                    perRangeUnitToAmount.put(unitType, BigDecimal.ZERO);
                }

                // Start consuming prevRawUsage element if it exists and falls into the range
                if (prevRawUsage != null) {

                    // Special treatment for final cancellation to make sure we include this usage point as part of the interval and bill for usage reported on cancellation date
                    final boolean isUsageForCancellationDay = i == transitionTimes.size() - 1 &&
                                                              curTransition.getTargetBillingEvent().getTransitionType() == SubscriptionBaseTransitionType.CANCEL &&
                                                              curDate.compareTo(prevRawUsage.getDate()) == 0;

                    if (prevRawUsage.getDate().compareTo(prevDate) >= 0 &&
                        (prevRawUsage.getDate().compareTo(curDate) < 0 || isUsageForCancellationDay)) {
                        final BigDecimal currentAmount = perRangeUnitToAmount.get(prevRawUsage.getUnitType());
                        final BigDecimal updatedAmount = computeUpdatedAmount(currentAmount, prevRawUsage.getAmount());
                        perRangeUnitToAmount.put(prevRawUsage.getUnitType(), updatedAmount);
                        trackingIds.add(new TrackingRecordId(prevRawUsage.getTrackingId(), invoiceId, prevRawUsage.getSubscriptionId(), prevRawUsage.getUnitType(), usageClockUtil.toLocalDate(prevRawUsage.getDate(), internalTenantContext)));
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
                        final RawUsageRecord curRawUsage = rawUsageIterator.next();
                        final boolean isUsageForCancellationDay = i == transitionTimes.size() - 1 &&
                                                                  curTransition.getTargetBillingEvent().getTransitionType() == SubscriptionBaseTransitionType.CANCEL &&
                                                                  curDate.compareTo(curRawUsage.getDate()) == 0;
                        // Special treatment for final cancellation to make sure we include this usage point as part of the interval and bill for usage reported on cancellation date
                        if (!isUsageForCancellationDay &&
                        		curRawUsage.getDate().compareTo(curDate) >= 0) {
                            prevRawUsage = curRawUsage;
                            break;
                        }

                        final BigDecimal currentAmount = perRangeUnitToAmount.get(curRawUsage.getUnitType());
                        final BigDecimal updatedAmount = computeUpdatedAmount(currentAmount, curRawUsage.getAmount());
                        perRangeUnitToAmount.put(curRawUsage.getUnitType(), updatedAmount);
                        trackingIds.add(new TrackingRecordId(curRawUsage.getTrackingId(), invoiceId, curRawUsage.getSubscriptionId(), curRawUsage.getUnitType(), usageClockUtil.toLocalDate(curRawUsage.getDate(), internalTenantContext)));
                    }
                }

                // If we did find some usage for that date range, let's populate the result
                if (!perRangeUnitToAmount.isEmpty()) {
                    final List<RolledUpUnit> rolledUpUnits = new ArrayList<RolledUpUnit>(perRangeUnitToAmount.size());
                    for (final Entry<String, BigDecimal> entry : perRangeUnitToAmount.entrySet()) {
                        final String unitType = entry.getKey();
                        // Sanity check: https://github.com/killbill/killbill/issues/1275
                        if (!allSeenUnitTypes.get(curTransition.getTargetBillingEvent()).contains(unitType)) {
                            // We have found some reported usage with a unit type that hasn't been handled by any ContiguousIntervalUsageInArrear
                            if (invoiceConfig.shouldParkAccountsWithUnknownUsage(internalTenantContext)) {
                                throw new InvoiceApiException(ErrorCode.UNEXPECTED_ERROR,
                                                              String.format("ILLEGAL INVOICING STATE: unit type %s is not defined in the catalog effective Date %s for subscription %s)",
                                                                            unitType, curTransition.getTargetBillingEvent().getCatalogEffectiveDate(), curTransition.getTargetBillingEvent().getSubscriptionId()));
                            } else {
                                log.warn("Ignoring unit type {} for subscription {} (not defined in the catalog effective Date {})", unitType, curTransition.getTargetBillingEvent().getSubscriptionId(), curTransition.getTargetBillingEvent().getCatalogEffectiveDate());
                                // Make sure to remove the associated tracking ids
                                final Iterator<TrackingRecordId> itr = trackingIds.iterator();
                                while (itr.hasNext()) {
                                    final TrackingRecordId t = itr.next();
                                    if (unitType.equals(t.getUnitType())) {
                                        itr.remove();
                                    }
                                }
                            }
                        } else if (unitTypes.contains(unitType)) { // Other usage type not for us -- safely ignore
                            rolledUpUnits.add(new DefaultRolledUpUnit(unitType, entry.getValue()));
                        }
                    }
                    result.add(new DefaultRolledUpUsageWithMetadata(getSubscriptionId(), prevDate, curDate, rolledUpUnits, prevCatalogEffectiveDate));
                }
            }
            prevDate = curDate;
            prevCatalogEffectiveDate = curTransition.getTargetBillingEvent().getCatalogEffectiveDate();
        }
        return new RolledUpUnitsWithTracking(result, trackingIds);
    }    

    private List<RolledUpUsageWithMetadata> getEmptyRolledUpUsage() {
        final List<RolledUpUsageWithMetadata> result = new ArrayList<RolledUpUsageWithMetadata>();

        final TransitionTime initialTransition = transitionTimes.get(transitionTimes.size() - 2);
        final TransitionTime endTransition = transitionTimes.get(transitionTimes.size() - 1);

        for (String unitType : unitTypes) {
            final List<RolledUpUnit> emptyRolledUptUnits = new ArrayList<RolledUpUnit>();
            emptyRolledUptUnits.add(new DefaultRolledUpUnit(unitType, BigDecimal.ZERO));
            final DefaultRolledUpUsageWithMetadata defaultForUnit = new DefaultRolledUpUsageWithMetadata(getSubscriptionId(), initialTransition.getDate(), endTransition.getDate(), emptyRolledUptUnits,
                                                                                                         initialTransition.getTargetBillingEvent().getCatalogEffectiveDate());
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
    private BigDecimal computeUpdatedAmount(@Nullable BigDecimal currentAmount, @Nullable BigDecimal newAmount) {
        currentAmount = currentAmount == null ? BigDecimal.ZERO : currentAmount;
        newAmount = newAmount == null ? BigDecimal.ZERO : newAmount;

        if (usage.getUsageType() == UsageType.CAPACITY) {
            return currentAmount.max(newAmount);
        } else /* UsageType.CONSUMABLE */ {
            return currentAmount.add(newAmount);
        }
    }

    private Set<TrackingRecordId> extractTrackingIds(final Set<TrackingRecordId> input) {
        return input.stream()
                .filter(trackingRecordId -> trackingRecordId.getSubscriptionId().equals(getSubscriptionId()))
                .collect(Collectors.toUnmodifiableSet());

    }

    /**
     * @param filteredUsageForInterval the list of invoiceItem to consider
     * @return the price amount that was already billed for that period and usage section (across unitTypes)
     */
    @VisibleForTesting
    BigDecimal computeBilledUsage(final Iterable<InvoiceItem> filteredUsageForInterval) {
        Preconditions.checkState(isBuilt.get(), "#computeBilledUsage(): isBuilt");

        BigDecimal billedAmount = BigDecimal.ZERO;
        for (final InvoiceItem ii : filteredUsageForInterval) {
            billedAmount = billedAmount.add(ii.getAmount());
        }
        // Return the billed $ amount (not the # of units)
        return billedAmount;
    }

    List<InvoiceItem> getBilledItems(final LocalDate startDate, final LocalDate endDate, final List<InvoiceItem> existingUsage) {
        Preconditions.checkState(isBuilt.get(), "#getBilledItems(): isBuilt");


        return existingUsage.stream().filter(input -> {
            if (input.getInvoiceItemType() != InvoiceItemType.USAGE) {
                return false;
            }

            // STEPH what happens if we discover usage period that overlap (one side or both side) the [startDate, endDate] interval
            final UsageInvoiceItem usageInput = (UsageInvoiceItem) input;

            // If we encounter items that were already built on the same day (e.g previous change of Plan on the same day)
            // but we are now billing for a larger period, we want to exclude such existing invoice item as they were already
            // billed for their own usage records - see TestChangeUsagePlanWithDateTime#testChangePlanOnSameDayAndRecordUsage
            final boolean isSameDay = startDate.compareTo(endDate) == 0;
            if (!isSameDay &&
                (usageInput.getStartDate().compareTo(usageInput.getEndDate()) == 0)) {
                return false;
            }

            return usageInput.getUsageName().equals(usage.getName()) &&
                   usageInput.getStartDate().compareTo(startDate) >= 0 &&
                   usageInput.getEndDate().compareTo(endDate) <= 0;
        }).collect(Collectors.toUnmodifiableList());
    }

    public List<DateTime> getTransitionTimes() {
        return transitionTimes.stream()
                              .map(t -> t.getDate())
                              .collect(Collectors.toList());
    }

    public void addBillingEvent(final BillingEvent event) {
        Preconditions.checkState(!isBuilt.get(), "#addBillingEvent(): !isBuilt");
        billingEvents.add(event);
    }

    public void addAllSeenUnitTypesForBillingEvent(final BillingEvent event, final Set<String> allSeenUnitTypesForBillingEvent) {
        Preconditions.checkState(!isBuilt.get(), "#addAllSeenUnitTypesForBillingEvent(): !isBuilt");
        if (allSeenUnitTypes.get(event) == null) {
            allSeenUnitTypes.put(event, new HashSet<String>());
        }
        allSeenUnitTypes.get(event).addAll(allSeenUnitTypesForBillingEvent);
    }

    public void addAllSeenUnitTypesFromPrevBillingEvent(final BillingEvent event) {
        final Set<String> allSeenUnitTypesFromPrevBillingEvent = Iterables.getLast(allSeenUnitTypes.values());
        addAllSeenUnitTypesForBillingEvent(event, allSeenUnitTypesFromPrevBillingEvent);
    }

    public Usage getUsage() {
        return usage;
    }

    public UUID getBundleId() {
        return billingEvents.get(0).getBundleId();
    }

    public UUID getSubscriptionId() {
        return billingEvents.get(0).getSubscriptionId();
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

    protected String toJson(final Object usageInArrearAggregate) {
        try {
            return objectMapper.writeValueAsString(usageInArrearAggregate);
        } catch (JsonProcessingException e) {
            Preconditions.checkState(false, e.getMessage());
            return null;
        }
    }

    public Set<String> getUnitTypes() {
        return unitTypes;
    }

    public static class RolledUpUnitsWithTracking {

        private final List<RolledUpUsageWithMetadata> usage;
        private final Set<TrackingRecordId> trackingIds;

        public RolledUpUnitsWithTracking(final List<RolledUpUsageWithMetadata> usage, final Set<TrackingRecordId> trackingIds) {
            this.usage = usage;
            this.trackingIds = trackingIds;
        }

        public List<RolledUpUsageWithMetadata> getUsage() {
            return usage;
        }

        public Set<TrackingRecordId> getTrackingIds() {
            return trackingIds;
        }
    }

    public static class UsageInArrearItemsAndNextNotificationDate {

        private final List<InvoiceItem> invoiceItems;
        private final LocalDate nextNotificationDate;
        private final Set<TrackingRecordId> trackingIds;

        public UsageInArrearItemsAndNextNotificationDate(final List<InvoiceItem> invoiceItems, final Set<TrackingRecordId> trackingIds, final LocalDate nextNotificationDate) {
            this.invoiceItems = invoiceItems;
            this.nextNotificationDate = nextNotificationDate;
            this.trackingIds = trackingIds;
        }

        public List<InvoiceItem> getInvoiceItems() {
            return invoiceItems;
        }

        public LocalDate getNextNotificationDate() {
            return nextNotificationDate;
        }

        public Set<TrackingRecordId> getTrackingIds() {
            return trackingIds;
        }
    }

}
