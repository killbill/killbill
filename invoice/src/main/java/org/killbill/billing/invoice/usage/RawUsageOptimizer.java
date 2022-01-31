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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.invoice.api.DryRunInfo;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceTrackingModelDao;
import org.killbill.billing.invoice.generator.InvoiceDateUtils;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.TrackingRecordId;
import org.killbill.billing.invoice.model.UsageInvoiceItem;
import org.killbill.billing.usage.InternalUserApi;
import org.killbill.billing.usage.api.RawUsageRecord;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;

public class RawUsageOptimizer {

    private static final Ordering<InvoiceItem> USAGE_ITEM_ORDERING = Ordering.natural()
                                                                             .onResultOf(new Function<InvoiceItem, Comparable>() {
                                                                                 @Override
                                                                                 public Comparable apply(final InvoiceItem invoiceItem) {
                                                                                     return invoiceItem.getEndDate();
                                                                                 }
                                                                             });

    private static final Logger log = LoggerFactory.getLogger(RawUsageOptimizer.class);

    private final InternalUserApi usageApi;
    private final InvoiceConfig config;
    private final InvoiceDao invoiceDao;
    private final Clock clock;

    @Inject
    public RawUsageOptimizer(final InvoiceConfig config, final InvoiceDao invoiceDao, final InternalUserApi usageApi, final Clock clock) {
        this.usageApi = usageApi;
        this.config = config;
        this.invoiceDao = invoiceDao;
        this.clock = clock;
    }

    public RawUsageOptimizerResult getInArrearUsage(final LocalDate firstEventStartDate, final LocalDate targetDate, final Iterable<InvoiceItem> existingUsageItems, final Map<String, Usage> knownUsage, @Nullable final DryRunInfo dryRunInfo, final InternalCallContext internalCallContext) {
        final int configRawUsagePreviousPeriod = config.getMaxRawUsagePreviousPeriod(internalCallContext);
        final LocalDate optimizedStartDate = configRawUsagePreviousPeriod >= 0 ? getOptimizedRawUsageStartDate(firstEventStartDate, targetDate, existingUsageItems, knownUsage, internalCallContext) : firstEventStartDate;
        log.debug("RawUsageOptimizerResult accountRecordId='{}', configRawUsagePreviousPeriod='{}', firstEventStartDate='{}', optimizedStartDate='{}',  targetDate='{}'",
                  internalCallContext.getAccountRecordId(), configRawUsagePreviousPeriod, firstEventStartDate, optimizedStartDate, targetDate);
        final List<RawUsageRecord> rawUsageData = usageApi.getRawUsageForAccount(optimizedStartDate, targetDate, dryRunInfo, internalCallContext);

        final List<InvoiceTrackingModelDao> trackingIds = invoiceDao.getTrackingsByDateRange(optimizedStartDate, targetDate, internalCallContext);
        final Set<TrackingRecordId> existingTrackingIds = new HashSet<TrackingRecordId>();
        for (final InvoiceTrackingModelDao invoiceTrackingModelDao : trackingIds) {
            existingTrackingIds.add(new TrackingRecordId(invoiceTrackingModelDao.getTrackingId(), invoiceTrackingModelDao.getInvoiceId(), invoiceTrackingModelDao.getSubscriptionId(), invoiceTrackingModelDao.getUnitType(), invoiceTrackingModelDao.getRecordDate()));
        }
        return new RawUsageOptimizerResult(optimizedStartDate, rawUsageData, existingTrackingIds);
    }

    @VisibleForTesting
    LocalDate getOptimizedRawUsageStartDate(final LocalDate firstEventStartDate, final LocalDate targetDate, final Iterable<InvoiceItem> existingUsageItems, final Map<String, Usage> knownUsage, final InternalCallContext internalCallContext) {


        // Extract all usage billing period known in that catalog
        final Collection<BillingPeriod> knownUsageBillingPeriod = new HashSet<BillingPeriod>();
        for (final Usage usage : knownUsage.values()) {
            knownUsageBillingPeriod.add(usage.getBillingPeriod());
        }

        //
        // We provide 2 implementations based on config, with the understanding that if 'isUsageZeroAmountDisabled' is set to true
        // and billing is not up to date (e.g Account was AUTO_INVOICING_OFF for past few periods), we may miss invoicing some old periods
        // because the optimization would prevent pulling enough (old) usage records.
        //
        final Map<BillingPeriod, LocalDate> perBillingPeriodMostRecentConsumableInArrearItemEndDate;
        if (config.isUsageZeroAmountDisabled(internalCallContext)) {
            perBillingPeriodMostRecentConsumableInArrearItemEndDate = getBillingPeriodMinDate2(knownUsageBillingPeriod, targetDate);
        } else {
            perBillingPeriodMostRecentConsumableInArrearItemEndDate = getBillingPeriodMinDate1(knownUsageBillingPeriod, existingUsageItems, knownUsage);
        }

        final int maxRawUsagePeriods = config.getMaxRawUsagePreviousPeriod(internalCallContext);
        // Extract the min from all the dates
        LocalDate targetStartDate = null;
        for (final Map.Entry<BillingPeriod, LocalDate> e : perBillingPeriodMostRecentConsumableInArrearItemEndDate.entrySet()) {
            final LocalDate value = e.getValue();
            final LocalDate targetBillingPeriodDate = value != null ? InvoiceDateUtils.recedeByNPeriods(value, e.getKey(), maxRawUsagePeriods) : null;
            if (targetStartDate == null || (targetBillingPeriodDate != null && targetBillingPeriodDate.compareTo(targetStartDate) < 0)) {
                targetStartDate = targetBillingPeriodDate;
            }
        }

        final LocalDate result = targetStartDate != null && targetStartDate.compareTo(firstEventStartDate) > 0 ? targetStartDate : firstEventStartDate;
        return result;
    }


    // Default implementation that rely on existing invoice items to correctly figure out where we are with invoicing and therefore how much usage records we need to pull.
    Map<BillingPeriod, LocalDate> getBillingPeriodMinDate1(final Collection<BillingPeriod> knownUsageBillingPeriod, final Iterable<InvoiceItem> existingUsageItems, final Map<String, Usage> knownUsage) {

        if (!existingUsageItems.iterator().hasNext()) {
            return ImmutableMap.<BillingPeriod, LocalDate>of();
        }

        final Map<BillingPeriod, LocalDate> perBillingPeriodMostRecentConsumableInArrearItemEndDate = new HashMap<>();

        // Make sure all usage items are sorted by endDate
        final List<InvoiceItem> sortedUsageItems = USAGE_ITEM_ORDERING.sortedCopy(existingUsageItems);

        for (final BillingPeriod bp : knownUsageBillingPeriod) {
            perBillingPeriodMostRecentConsumableInArrearItemEndDate.put(bp, null);
        }

        final ListIterator<InvoiceItem> iterator = sortedUsageItems.listIterator(sortedUsageItems.size());
        while (iterator.hasPrevious()) {
            final InvoiceItem item = iterator.previous();
            if (!(item instanceof UsageInvoiceItem)) {
                // Help to debug https://github.com/killbill/killbill/issues/1095
                log.warn("RawUsageOptimizer : item id={}, expected to see an UsageInvoiceItem type, got class {}, classloader = {}",
                         item.getId(),
                         item.getClass(),
                         item.getClass().getClassLoader());
            }

            final Usage usage = knownUsage.get(item.getUsageName());
            if (perBillingPeriodMostRecentConsumableInArrearItemEndDate.get(usage.getBillingPeriod()) == null) {
                perBillingPeriodMostRecentConsumableInArrearItemEndDate.put(usage.getBillingPeriod(), item.getEndDate());
                if (!containsNullEntries(perBillingPeriodMostRecentConsumableInArrearItemEndDate)) {
                    break;
                }
            }
        }
        return perBillingPeriodMostRecentConsumableInArrearItemEndDate;
    }

    //
    // Non default implementation, i.e 'isUsageZeroAmountDisabled=true' that assumes we are up to date and simplify the logic by simply
    // computing a per-billing period date from min(today, targetDate).
    //
    @VisibleForTesting
    Map<BillingPeriod, LocalDate> getBillingPeriodMinDate2(final Collection<BillingPeriod> knownUsageBillingPeriod, final LocalDate targetDate) {

        final LocalDate utcToday = clock.getUTCToday();
        final LocalDate minTodayTargetDate = utcToday.compareTo(targetDate) < 0 ? utcToday : targetDate;
        final Map<BillingPeriod, LocalDate> perBillingPeriodMostRecentConsumableInArrearItemEndDate = new HashMap<>();

        for (final BillingPeriod bp : knownUsageBillingPeriod) {
            //
            // E.g with org.killbill.invoice.readMaxRawUsagePreviousPeriod=0, any optimized date prior or equal to this would return
            // enough usage item to bill the previous period.
            //
            final LocalDate perBPStartDate = InvoiceDateUtils.recedeByNPeriods(minTodayTargetDate, bp, 1);
            perBillingPeriodMostRecentConsumableInArrearItemEndDate.put(bp, perBPStartDate);
        }
        return perBillingPeriodMostRecentConsumableInArrearItemEndDate;
    }


    private boolean containsNullEntries(final Map<BillingPeriod, LocalDate> entries) {
        for (final LocalDate entry : entries.values()) {
            if (entry == null) {
                return true;
            }
        }
        return false;
    }

    public static class RawUsageOptimizerResult {

        private final LocalDate rawUsageStartDate;
        private final List<RawUsageRecord> rawUsage;
        private final Set<TrackingRecordId> existingTrackingIds;

        public RawUsageOptimizerResult(final LocalDate rawUsageStartDate, final List<RawUsageRecord> rawUsage, final Set<TrackingRecordId> existingTrackingIds) {
            this.rawUsageStartDate = rawUsageStartDate;
            this.rawUsage = rawUsage;
            this.existingTrackingIds = existingTrackingIds;
        }

        public LocalDate getRawUsageStartDate() {
            return rawUsageStartDate;
        }

        public List<RawUsageRecord> getRawUsage() {
            return rawUsage;
        }

        public Set<TrackingRecordId> getExistingTrackingIds() {
            return existingTrackingIds;
        }
    }
}
