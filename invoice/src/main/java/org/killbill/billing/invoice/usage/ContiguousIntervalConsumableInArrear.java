/*
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.invoice.usage;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.generator.BillingIntervalDetail;
import org.killbill.billing.invoice.model.UsageInvoiceItem;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.usage.api.RolledUpUnit;
import org.killbill.billing.usage.api.RolledUpUsage;
import org.killbill.billing.usage.api.UsageUserApi;
import org.killbill.billing.util.callcontext.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import static org.killbill.billing.invoice.usage.UsageUtils.getConsumableInArrearTieredBlocks;
import static org.killbill.billing.invoice.usage.UsageUtils.getConsumableInArrearUnitTypes;

/**
 * There is one such class per subscriptionId, matching a given in arrear/consumable usage section and
 * referenced through a contiguous list of billing events.
 */
public class ContiguousIntervalConsumableInArrear {

    private static final Logger log = LoggerFactory.getLogger(ContiguousIntervalConsumableInArrear.class);

    private final List<LocalDate> transitionTimes;
    private final List<BillingEvent> billingEvents;

    private final Usage usage;
    private final Set<String> unitTypes;
    private final UsageUserApi usageApi;
    private final LocalDate targetDate;
    private final UUID invoiceId;
    private final TenantContext context;
    private final AtomicBoolean isBuilt;
    private final boolean insertZeroAmountItems;

    public ContiguousIntervalConsumableInArrear(final Usage usage, final UUID invoiceId, final UsageUserApi usageApi, final boolean insertZeroAmountItems, final LocalDate targetDate, final TenantContext context) {
        this.usage = usage;
        this.invoiceId = invoiceId;
        this.unitTypes = getConsumableInArrearUnitTypes(usage);
        this.usageApi = usageApi;
        this.insertZeroAmountItems = insertZeroAmountItems;
        this.targetDate = targetDate;
        this.context = context;
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
     * @return
     */
    public ContiguousIntervalConsumableInArrear build(final boolean closedInterval) {

        Preconditions.checkState(!isBuilt.get());
        Preconditions.checkState((!closedInterval && billingEvents.size() >= 1) ||
                                 (closedInterval && billingEvents.size() >= 2));

        final LocalDate startDate = new LocalDate(billingEvents.get(0).getEffectiveDate(), getAccountTimeZone());
        if (targetDate.isBefore(startDate)) {
            return this;
        }
        final LocalDate endDate = closedInterval ? new LocalDate(billingEvents.get(billingEvents.size() - 1).getEffectiveDate(), getAccountTimeZone()) : targetDate;

        final BillingIntervalDetail bid = new BillingIntervalDetail(startDate, endDate, targetDate, getBCD(), usage.getBillingPeriod());

        int numberOfPeriod = 0;
        // First billingCycleDate prior startDate
        LocalDate nextBillCycleDate = bid.getFutureBillingDateFor(numberOfPeriod);
        transitionTimes.add(startDate);
        while (!nextBillCycleDate.isAfter(endDate)) {
            if (nextBillCycleDate.isAfter(startDate)) {
                transitionTimes.add(nextBillCycleDate);
            }
            numberOfPeriod++;
            nextBillCycleDate = bid.getFutureBillingDateFor(numberOfPeriod);
        }
        isBuilt.set(true);
        return this;
    }

    /**
     * Compute the missing usage invoice items based on what should be billed and what has been billed ($ amount comparison).
     *
     * @param existingUsage existing on disk usage items for the subscription
     * @return
     * @throws CatalogApiException
     */
    public List<InvoiceItem> computeMissingItems(final List<InvoiceItem> existingUsage) throws CatalogApiException {

        Preconditions.checkState(isBuilt.get());

        final List<InvoiceItem> result = Lists.newLinkedList();

        final List<RolledUpUsage> allUsage = getRolledUpUsage();
        for (final RolledUpUsage ru : allUsage) {

            // Compute total price amount that should be billed for that period of time (and usage section) across unitTypes.
            BigDecimal toBeBilledUsage = BigDecimal.ZERO;
            for (final RolledUpUnit cur : ru.getRolledUpUnits()) {
                if (!unitTypes.contains(cur.getUnitType())) {
                    log.warn("ContiguousIntervalConsumableInArrear is skipping unitType " + cur.getUnitType());
                    continue;
                }

                final BigDecimal toBeBilledForUnit = computeToBeBilledUsage(cur.getAmount(), cur.getUnitType());
                toBeBilledUsage = toBeBilledUsage.add(toBeBilledForUnit);

            }
            // Retrieves current price amount billed for that period of time (and usage section)
            final Iterable<InvoiceItem> billedItems = getBilledItems(ru.getStart(), ru.getEnd(), existingUsage);
            final BigDecimal billedUsage = computeBilledUsage(billedItems);

            // Compare the two and add the missing piece if required.
            if (!billedItems.iterator().hasNext() || billedUsage.compareTo(toBeBilledUsage) < 0) {
                final BigDecimal amountToBill = toBeBilledUsage.subtract(billedUsage);
                if (amountToBill.compareTo(BigDecimal.ZERO) > 0 || insertZeroAmountItems) {
                    InvoiceItem item = new UsageInvoiceItem(invoiceId, getAccountId(), getBundleId(), getSubscriptionId(), getPlanName(),
                                                            getPhaseName(), usage.getName(), ru.getStart(), ru.getEnd(), amountToBill, getCurrency());
                    result.add(item);
                }
            }
        }
        return result;
    }

    /**
     * @return a list of {@code RolledUpUsage} for each period (between two transitions) * each unitType.
     */
    List<RolledUpUsage> getRolledUpUsage() {
        // There needs to be at least two transitions to define an interval to bill
        if (transitionTimes.size() <= 1) {
            return Collections.emptyList();
        }
        return usageApi.getAllUsageForSubscription(getSubscriptionId(), transitionTimes, context);
    }

    /**
     * @param nbUnits  the number of used units for a given period
     * @param unitType the type of unit
     * @return the price amount that should be billed for that period/unitType
     * @throws CatalogApiException
     */
    @VisibleForTesting
    BigDecimal computeToBeBilledUsage(final Long nbUnits, final String unitType) throws CatalogApiException {

        Preconditions.checkState(isBuilt.get());

        BigDecimal result = BigDecimal.ZERO;
        final List<TieredBlock> tieredBlocks = getConsumableInArrearTieredBlocks(usage, unitType);
        int remainingUnits = nbUnits.intValue();
        for (TieredBlock tieredBlock : tieredBlocks) {

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
        for (InvoiceItem ii : filteredUsageForInterval) {
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

    public UUID getAccountId() {
        return billingEvents.get(0).getAccount().getId();
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

    public DateTimeZone getAccountTimeZone() {
        return billingEvents.get(0).getTimeZone();
    }

}
