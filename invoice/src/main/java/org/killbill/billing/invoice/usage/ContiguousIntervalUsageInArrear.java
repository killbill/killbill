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
import org.killbill.billing.catalog.api.Limit;
import org.killbill.billing.catalog.api.Tier;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.generator.BillingIntervalDetail;
import org.killbill.billing.invoice.model.UsageInvoiceItem;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.usage.RawUsage;
import org.killbill.billing.usage.api.RolledUpUnit;
import org.killbill.billing.usage.api.RolledUpUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import static org.killbill.billing.invoice.usage.UsageUtils.getCapacityInArrearTier;
import static org.killbill.billing.invoice.usage.UsageUtils.getCapacityInArrearUnitTypes;
import static org.killbill.billing.invoice.usage.UsageUtils.getConsumableInArrearTieredBlocks;
import static org.killbill.billing.invoice.usage.UsageUtils.getConsumableInArrearUnitTypes;



/**
 * There is one such class per subscriptionId, matching a given in arrear/consumable usage section and
 * referenced through a contiguous list of billing events.
 */
public class ContiguousIntervalUsageInArrear {

    private static final Logger log = LoggerFactory.getLogger(ContiguousIntervalUsageInArrear.class);

    private final List<LocalDate> transitionTimes;
    private final List<BillingEvent> billingEvents;

    private final Usage usage;
    private final Set<String> unitTypes;
    private final List<RawUsage> rawSubscriptionUsage;
    private final LocalDate targetDate;
    private final UUID accountId;
    private final UUID invoiceId;
    private final AtomicBoolean isBuilt;
    private final LocalDate rawUsageStartDate;
    private final InternalTenantContext internalTenantContext;

    public ContiguousIntervalUsageInArrear(final Usage usage,
                                           final UUID accountId,
                                           final UUID invoiceId,
                                           final List<RawUsage> rawSubscriptionUsage,
                                           final LocalDate targetDate,
                                           final LocalDate rawUsageStartDate,
                                           final InternalTenantContext internalTenantContext) {
        this.usage = usage;
        this.accountId = accountId;
        this.invoiceId = invoiceId;
        this.unitTypes = usage.getUsageType() == UsageType.CAPACITY ? getCapacityInArrearUnitTypes(usage) : getConsumableInArrearUnitTypes(usage);
        this.rawSubscriptionUsage = rawSubscriptionUsage;
        this.targetDate = targetDate;
        this.rawUsageStartDate = rawUsageStartDate;
        this.internalTenantContext = internalTenantContext;
        this.billingEvents = Lists.newLinkedList();
        this.transitionTimes = Lists.newLinkedList();
        this.isBuilt = new AtomicBoolean(false);
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
        if (closedInterval && endDate.isAfter(transitionTimes.get(transitionTimes.size() - 1))) {
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
    public UsageInArrearItemsAndNextNotificationDate computeMissingItemsAndNextNotificationDate(final List<InvoiceItem> existingUsage) throws CatalogApiException {

        Preconditions.checkState(isBuilt.get());

        if (transitionTimes.size() < 2) {
            return new UsageInArrearItemsAndNextNotificationDate(ImmutableList.<InvoiceItem>of(), computeNextNotificationDate());
        }

        final List<InvoiceItem> result = Lists.newLinkedList();

        // We start by generating 'marker' USAGE items with $0 that will allow to correctly insert the next notification for when there is no USAGE to bill.
        // Those will be removed by the invoicing code later so as to not end up with superfluous $0 items
        LocalDate prevDate = null;
        for (final LocalDate curDate : transitionTimes) {
            if (prevDate != null) {
                final InvoiceItem item = new UsageInvoiceItem(invoiceId, accountId, getBundleId(), getSubscriptionId(), getPlanName(),
                                                              getPhaseName(), usage.getName(), prevDate, curDate, BigDecimal.ZERO, getCurrency());
                result.add(item);
            }
            prevDate = curDate;
        }

        final List<RolledUpUsage> allUsage = getRolledUpUsage();
        for (final RolledUpUsage ru : allUsage) {

            BigDecimal toBeBilledUsage = BigDecimal.ZERO;
            if (usage.getUsageType() == UsageType.CAPACITY) {
                toBeBilledUsage = computeToBeBilledCapacityInArrear(ru.getRolledUpUnits());
            } else /* UsageType.CONSUMABLE */{

                // Compute total price amount that should be billed for that period of time (and usage section) across unitTypes.
                for (final RolledUpUnit cur : ru.getRolledUpUnits()) {
                    if (!unitTypes.contains(cur.getUnitType())) {
                        log.warn("ContiguousIntervalConsumableInArrear is skipping unitType " + cur.getUnitType());
                        continue;
                    }

                    final BigDecimal toBeBilledForUnit = computeToBeBilledConsumableInArrear(cur);
                    toBeBilledUsage = toBeBilledUsage.add(toBeBilledForUnit);
                }
            }
            // Retrieves current price amount billed for that period of time (and usage section)
            final Iterable<InvoiceItem> billedItems = getBilledItems(ru.getStart(), ru.getEnd(), existingUsage);
            final BigDecimal billedUsage = computeBilledUsage(billedItems);

            // Compare the two and add the missing piece if required.
            if (!billedItems.iterator().hasNext() || billedUsage.compareTo(toBeBilledUsage) < 0) {
                final BigDecimal amountToBill = toBeBilledUsage.subtract(billedUsage);
                if (amountToBill.compareTo(BigDecimal.ZERO) > 0) {
                    final InvoiceItem item = new UsageInvoiceItem(invoiceId, accountId, getBundleId(), getSubscriptionId(), getPlanName(),
                                                                  getPhaseName(), usage.getName(), ru.getStart(), ru.getEnd(), amountToBill, getCurrency());
                    result.add(item);
                }
            }
        }

        final LocalDate nextNotificationdate = computeNextNotificationDate();
        return new UsageInArrearItemsAndNextNotificationDate(result, nextNotificationdate);
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
        final Iterator<RawUsage> rawUsageIterator = rawSubscriptionUsage.iterator();
        if (!rawUsageIterator.hasNext()) {
            return ImmutableList.of();
        }

        final List<RolledUpUsage> result = new ArrayList<RolledUpUsage>();

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
        if (prevRawUsage.getDate().compareTo(transitionTimes.get(transitionTimes.size() - 1)) >= 0) {
            return ImmutableList.of();
        }

        //
        // Loop through each interval [prevDate, curDate) and consume as many rawSubscriptionUsage elements within that range
        // to create one RolledUpUsage per interval. If an interval does not have any rawSubscriptionUsage element, there will be no
        // matching RolledUpUsage for that interval, and we'll detect that in the 'computeMissingItems' logic
        //
        LocalDate prevDate = null;
        for (final LocalDate curDate : transitionTimes) {

            if (prevDate != null) {

                // Allocate new perRangeUnitToAmount for this interval and populate with rawSubscriptionUsage items
                final Map<String, Long> perRangeUnitToAmount = new HashMap<String, Long>();

                // Start consuming prevRawUsage element if it exists and falls into the range
                if (prevRawUsage != null) {
                    if (prevRawUsage.getDate().compareTo(prevDate) >= 0 && prevRawUsage.getDate().compareTo(curDate) < 0) {
                        final Long currentAmount = perRangeUnitToAmount.get(prevRawUsage.getUnitType());
                        final Long updatedAmount =  computeUpdatedAmount(currentAmount, prevRawUsage.getAmount());
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
                        final Long updatedAmount =  computeUpdatedAmount(currentAmount, curRawUsage.getAmount());
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


    private Limit getTierLimit(final Tier tier, final String unitType) {
        for (final Limit cur : tier.getLimits()) {
            if (cur.getUnit().getName().equals(unitType)) {
                return cur;
            }
        }
        Preconditions.checkState(false, "Could not find unit type " + unitType + " in usage tier ");
        return null;
    }

    /**
     * @param roUnits the list of rolled up units for the period
     * @return the price amount that should be billed for that period/unitType
     * @throws CatalogApiException
     */
    @VisibleForTesting
    BigDecimal computeToBeBilledCapacityInArrear(final List<RolledUpUnit> roUnits) throws CatalogApiException {
        Preconditions.checkState(isBuilt.get());

        final List<Tier> tiers = getCapacityInArrearTier(usage);

        for (final Tier cur : tiers) {
            boolean complies = true;
            for (final RolledUpUnit ro : roUnits) {
                final Limit tierLimit = getTierLimit(cur, ro.getUnitType());
                // We ignore the min and only look at the max Limit as the tiers should be contiguous.
                // Specifying a -1 value for last max tier will make the validation works
                if (tierLimit.getMax() != (double) -1 && ro.getAmount().doubleValue() > tierLimit.getMax()) {
                    complies = false;
                    break;
                }
            }
            if (complies) {
                return cur.getRecurringPrice().getPrice(getCurrency());
            }
        }
        // Probably invalid catalog config
        final Joiner joiner = Joiner.on(", ");
        joiner.join(roUnits);
        Preconditions.checkState(false, "Could not find tier for usage " + usage.getName()+ "matching with data = " + joiner.join(roUnits));
        return null;
    }

    /**
     * @param roUnit the rolled up unit for the period
     * @return the price amount that should be billed for that period/unitType
     * @throws CatalogApiException
     */
    @VisibleForTesting
    BigDecimal computeToBeBilledConsumableInArrear(final RolledUpUnit roUnit) throws CatalogApiException {

        Preconditions.checkState(isBuilt.get());

        BigDecimal result = BigDecimal.ZERO;

        final List<TieredBlock> tieredBlocks = getConsumableInArrearTieredBlocks(usage, roUnit.getUnitType());
        int remainingUnits = roUnit.getAmount().intValue();
        for (final TieredBlock tieredBlock : tieredBlocks) {

            final int blockTierSize = tieredBlock.getSize().intValue();
            final int tmp = remainingUnits / blockTierSize + (remainingUnits % blockTierSize == 0 ? 0 : 1);
            final int nbUsedTierBlocks;
            if (tmp > tieredBlock.getMax()) {
                nbUsedTierBlocks = tieredBlock.getMax().intValue();
                remainingUnits -= tieredBlock.getMax() * blockTierSize;
            } else {
                nbUsedTierBlocks = tmp;
                remainingUnits = 0;
            }
            result = result.add(tieredBlock.getPrice().getPrice(getCurrency()).multiply(new BigDecimal(nbUsedTierBlocks)));
        }
        return result;
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

    Iterable<InvoiceItem> getBilledItems(final LocalDate startDate, final LocalDate endDate, final List<InvoiceItem> existingUsage) {

        Preconditions.checkState(isBuilt.get());
        return Iterables.filter(existingUsage, new Predicate<InvoiceItem>() {
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

    public String getPhaseName() {
        return billingEvents.get(0).getPlanPhase().getName();
    }

    public Currency getCurrency() {
        return billingEvents.get(0).getCurrency();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ContiguousIntervalConsumableInArrear{");
        sb.append("transitionTimes=").append(transitionTimes);
        sb.append(", billingEvents=").append(billingEvents);
        sb.append(", rawSubscriptionUsage=").append(rawSubscriptionUsage);
        sb.append(", rawUsageStartDate=").append(rawUsageStartDate);
        sb.append('}');
        return sb.toString();
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
}
