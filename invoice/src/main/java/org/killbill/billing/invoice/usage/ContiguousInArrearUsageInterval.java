/*
 * Copyright 2014 The Billing Project, Inc.
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
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
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
import org.killbill.billing.usage.api.RolledUpUsage;
import org.killbill.billing.usage.api.UsageUserApi;
import org.killbill.billing.util.callcontext.TenantContext;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import static org.killbill.billing.invoice.usage.SubscriptionConsumableInArrear.getTieredBlocks;
import static org.killbill.billing.invoice.usage.SubscriptionConsumableInArrear.localDateToEndOfDayInAccountTimezone;

public class ContiguousInArrearUsageInterval {

    private final List<LocalDate> transitionTimes;
    private final List<BillingEvent> billingEvents;

    private final Usage usage;
    private final String unitType;
    private final UsageUserApi usageApi;
    private final LocalDate targetDate;
    private final UUID invoiceId;
    private final TenantContext context;

    public ContiguousInArrearUsageInterval(final Usage usage, final UUID invoiceId, final String unitType, final UsageUserApi usageApi, final LocalDate targetDate, final TenantContext context) {
        this.usage = usage;
        this.invoiceId = invoiceId;
        this.unitType = unitType;
        this.usageApi = usageApi;
        this.targetDate = targetDate;
        this.context = context;
        this.billingEvents = Lists.newLinkedList();
        this.transitionTimes = Lists.newLinkedList();
    }

    public void addBillingEvent(final BillingEvent event) {
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



    public ContiguousInArrearUsageInterval build(final boolean closedInterval) {

        Preconditions.checkState((!closedInterval && billingEvents.size() >= 1) ||
                                 (closedInterval && billingEvents.size() >= 2));

        final LocalDate startDate = new LocalDate(billingEvents.get(0).getEffectiveDate(), getAccountTimeZone());
        if (targetDate.isBefore(startDate)) {
            return this;
        }

        final LocalDate endDate = closedInterval ? new LocalDate(billingEvents.get(billingEvents.size() - 1), getAccountTimeZone()) : targetDate;

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
        return this;
    }

    public List<InvoiceItem> computeMissingItems(final List<InvoiceItem> existingUsage) throws CatalogApiException {

        final List<InvoiceItem> result = Lists.newLinkedList();

        final List<RolledUpUsage> rolledUpUsages = getRolledUpUsage();
        for (RolledUpUsage ru : rolledUpUsages) {
            final LocalDate startRolledUpDate = new LocalDate(ru.getStartTime(), getAccountTimeZone());
            final LocalDate endRolledUpDate = new LocalDate(ru.getEndTime(), getAccountTimeZone());
            final BigDecimal billedUsage = computeBilledUsage(startRolledUpDate, endRolledUpDate, existingUsage);
            final BigDecimal toBeBilledUsage = computeToBeBilledUsage(ru.getAmount());
            if (billedUsage.compareTo(toBeBilledUsage) < 0) {
                InvoiceItem item = new UsageInvoiceItem(invoiceId, getAccountId(), getBundleId(), getSubscriptionId(), getPlanName(),
                                                        getPhaseName(), startRolledUpDate, endRolledUpDate, toBeBilledUsage.subtract(billedUsage), getCurrency(), unitType);
                result.add(item);
            }
        }
        return result;
    }

    private List<RolledUpUsage> getRolledUpUsage() {

        final Iterable<DateTime> transitions = Iterables.transform(transitionTimes, new Function<LocalDate, DateTime>() {
            @Override
            public DateTime apply(final LocalDate input) {
                return localDateToEndOfDayInAccountTimezone(input, getAccountTimeZone());
            }
        });
        final List<RolledUpUsage> usagesForInterval = usageApi.getAllUsageForSubscription(getSubscriptionId(), unitType, ImmutableList.copyOf(transitions), context);
        return usagesForInterval;
    }

    private final BigDecimal computeToBeBilledUsage(final BigDecimal units) throws CatalogApiException {

        // STEPH_USAGE need to review catalog xml which defines block tiers, ...
        final int blockSize = 0x1000;
        final int nbBlocks = units.intValue() / blockSize + ((units.intValue() % blockSize == 0) ? 0 : 1);

        // STEPH_USAGE this is wrong should use from each tier.
        final List<TieredBlock> tieredBlocks = getTieredBlocks(usage, unitType);
        for (TieredBlock tier : tieredBlocks) {
            if (tier.getMax() >= units.doubleValue()) {
                return tier.getPrice().getPrice(getCurrency());
            }
        }
        // Return from last tier
        return tieredBlocks.get(tieredBlocks.size() - 1).getPrice().getPrice(getCurrency());
    }

    private final BigDecimal computeBilledUsage(final LocalDate startDate, final LocalDate endDate, final List<InvoiceItem> existingUsage) {
        final Iterable<InvoiceItem> filteredUsageForInterval = Iterables.filter(existingUsage, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                // STEPH_USAGE what happens if we discover usage period that overlap (one side or both side) the [startDate, endDate] interval
                // STEPH_USAGE how to distinguish different usage charges (maybe different sections.) (needs to at least of the unitType in usage element
                return input.getInvoiceItemType() == InvoiceItemType.USAGE &&
                       input.getStartDate().compareTo(startDate) >= 0 &&
                       input.getEndDate().compareTo(endDate) <= 0;
            }
        });

        final BigDecimal billedAmount = BigDecimal.ZERO;
        for (InvoiceItem ii : filteredUsageForInterval) {
            billedAmount.add(ii.getAmount());
        }
        // Return the billed $ amount (not the # of units)
        return billedAmount;
    }

}
