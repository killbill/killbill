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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.generator.InvoiceItemGenerator.InvoiceItemGeneratorLogger;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.TrackingRecordId;
import org.killbill.billing.invoice.usage.ContiguousIntervalUsageInArrear.UsageInArrearItemsAndNextNotificationDate;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.usage.api.RawUsageRecord;
import org.killbill.commons.utils.annotation.VisibleForTesting;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.config.definition.InvoiceConfig.UsageDetailMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * There is one such class created for each subscriptionId referenced in the billingEvents.
 */
public class SubscriptionUsageInArrear {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionUsageInArrear.class);

    private static final Comparator<RawUsageRecord> RAW_USAGE_DATE_COMPARATOR = new Comparator<RawUsageRecord>() {
        @Override
        public int compare(final RawUsageRecord o1, final RawUsageRecord o2) {

            // Compare by subscriptionId (not really needed as we only have entry per subscription in this class)
            int compared = o1.getSubscriptionId().compareTo(o2.getSubscriptionId());
            if (compared != 0) {
                return compared;
            }
            // Compare by recordDate
            compared = o1.getDate().compareTo(o2.getDate());
            if (compared != 0) {
                return compared;
            }
            // Compare by unitType
            compared = o1.getUnitType().compareTo(o2.getUnitType());
            if (compared != 0) {
                return compared;
            }
            // Compare by amount
            compared = o1.getAmount().compareTo(o2.getAmount());
            if (compared != 0) {
                return compared;
            }
            // Compare by trackingId
            return o1.getTrackingId().compareTo(o2.getTrackingId());
        }
    };

    private final UUID accountId;
    private final UUID subscriptionId;
    private final UUID invoiceId;
    private final List<BillingEvent> subscriptionBillingEvents;
    private final LocalDate targetDate;
    private final DateTime rawUsageStartDate;
    private final InternalTenantContext internalTenantContext;
    private final UsageDetailMode usageDetailMode;
    private final InvoiceConfig invoiceConfig;

    final List<ContiguousIntervalUsageInArrear> usageIntervals;

    public SubscriptionUsageInArrear(final UUID subscriptionId,
                                     final UUID accountId,
                                     final UUID invoiceId,
                                     final List<BillingEvent> subscriptionBillingEvents,
                                     final LocalDate targetDate,
                                     final DateTime rawUsageStartDate,
                                     final UsageDetailMode usageDetailMode,
                                     final InvoiceConfig invoiceConfig,
                                     final InternalTenantContext internalTenantContext) throws CatalogApiException {

        this.subscriptionId = subscriptionId;
        this.accountId = accountId;
        this.invoiceId = invoiceId;
        this.subscriptionBillingEvents = subscriptionBillingEvents;
        this.targetDate = targetDate;
        this.rawUsageStartDate = rawUsageStartDate;
        this.internalTenantContext = internalTenantContext;
        this.usageDetailMode = usageDetailMode;
        this.invoiceConfig = invoiceConfig;
        this.usageIntervals = computeInArrearUsageInterval();
    }

    public List<ContiguousIntervalUsageInArrear> getUsageIntervals() {
        return usageIntervals;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    /**
     * Based on billing events, (@code existingUsage} and targetDate, figure out what remains to be billed.
     *
     * @param existingUsage the existing on disk usage items.
     * @throws CatalogApiException
     */
    public SubscriptionUsageInArrearItemsAndNextNotificationDate computeMissingUsageInvoiceItems(final List<InvoiceItem> existingUsage,
                                                                                                 final List<RawUsageRecord> rawUsage,
                                                                                                 final Set<TrackingRecordId> existingTrackingIds,
                                                                                                 final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger,
                                                                                                 final boolean isDryRun) throws CatalogApiException, InvoiceApiException {
        final List<RawUsageRecord> rawSubscriptionUsage = rawUsage.stream()
                                                                  .filter(input -> input.getSubscriptionId().equals(subscriptionId))
                                                                  .sorted(RAW_USAGE_DATE_COMPARATOR)
                                                                  .collect(Collectors.toUnmodifiableList());
        final SubscriptionUsageInArrearItemsAndNextNotificationDate result = new SubscriptionUsageInArrearItemsAndNextNotificationDate();
        for (final ContiguousIntervalUsageInArrear usageInterval : usageIntervals) {
            final UsageInArrearItemsAndNextNotificationDate newItemsWithDetailsAndDate = usageInterval.computeMissingItemsAndNextNotificationDate(rawSubscriptionUsage, existingTrackingIds, existingUsage, isDryRun);

            // For debugging purposes
            invoiceItemGeneratorLogger.append(usageInterval, newItemsWithDetailsAndDate.getInvoiceItems());

            result.addUsageInArrearItemsAndNextNotificationDate(usageInterval.getUsage().getName(), newItemsWithDetailsAndDate);
            result.addTrackingIds(newItemsWithDetailsAndDate.getTrackingIds());
        }
        return result;
    }

    @VisibleForTesting
    List<ContiguousIntervalUsageInArrear> computeInArrearUsageInterval() throws CatalogApiException {
        final List<ContiguousIntervalUsageInArrear> usageIntervals = new LinkedList<>();

        final Map<UsageKey, ContiguousIntervalUsageInArrear> inFlightInArrearUsageIntervals = new HashMap<>();

        final Set<UsageKey> allSeenUsage = new HashSet<>();

        for (final BillingEvent event : subscriptionBillingEvents) {
            // Extract all in arrear /consumable usage section for that billing event.
            final List<Usage> usages = findUsageInArrearUsages(event);
            for (final Usage usage : usages) {
                allSeenUsage.add(new UsageKey(usage.getName(), event.getCatalogEffectiveDate()));
            }

            // All inflight usage interval are candidates to be closed unless we see that current billing event referencing the same usage section.
            final Set<UsageKey> toBeClosed = new HashSet<UsageKey>(allSeenUsage);

            // Will contain all unit types that each BillingEvent has looked at, as defined in the catalog
            final List<ContiguousIntervalUsageInArrear> contiguousIntervalsUsageInArrear = new LinkedList<ContiguousIntervalUsageInArrear>();
            final Set<String> allSeenUnitTypesForBillingEvent = new HashSet<String>();

            for (final Usage usage : usages) {

                final UsageKey usageKey = new UsageKey(usage.getName(), event.getCatalogEffectiveDate());


                // Add inflight usage interval if non-existent
                ContiguousIntervalUsageInArrear existingInterval = inFlightInArrearUsageIntervals.get(usageKey);
                if (existingInterval == null) {
                    existingInterval = usage.getUsageType() == UsageType.CAPACITY ?
                                       new ContiguousIntervalCapacityUsageInArrear(usage, accountId, invoiceId, targetDate, rawUsageStartDate, usageDetailMode, invoiceConfig, internalTenantContext) :
                                       new ContiguousIntervalConsumableUsageInArrear(usage, accountId, invoiceId, targetDate, rawUsageStartDate, usageDetailMode, invoiceConfig, internalTenantContext);
                    inFlightInArrearUsageIntervals.put(usageKey, existingInterval);
                }
                // Add billing event for that usage interval
                existingInterval.addBillingEvent(event);
                // Remove usage interval for toBeClosed set
                toBeClosed.remove(usageKey);

                allSeenUnitTypesForBillingEvent.addAll(existingInterval.getUnitTypes());
                contiguousIntervalsUsageInArrear.add(existingInterval);
            }

            // Add all seen unit types (across all intervals) for all intervals
            for (final ContiguousIntervalUsageInArrear contiguousIntervalUsageInArrear : contiguousIntervalsUsageInArrear) {
                contiguousIntervalUsageInArrear.addAllSeenUnitTypesForBillingEvent(event, allSeenUnitTypesForBillingEvent);
            }

            // Build the usage interval that are no longer referenced (maybe because the usage section was removed, or
            // because a new catalog version is effective)
            for (final UsageKey usageKey : toBeClosed) {
                final ContiguousIntervalUsageInArrear interval = inFlightInArrearUsageIntervals.remove(usageKey);
                if (interval != null) {
                    interval.addBillingEvent(event);
                    // We look at the unit types defined for the last billing event
                    interval.addAllSeenUnitTypesFromPrevBillingEvent(event);
                    usageIntervals.add(interval.build(true));
                }
            }
        }

        for (final Entry<UsageKey, ContiguousIntervalUsageInArrear> entry : inFlightInArrearUsageIntervals.entrySet()) {
            usageIntervals.add(entry.getValue().build(false));
        }
        inFlightInArrearUsageIntervals.clear();
        return usageIntervals;
    }

    private List<Usage> findUsageInArrearUsages(final BillingEvent event) throws CatalogApiException {
        if (event.getUsages().isEmpty()) {
            return Collections.emptyList();
        }

        final List<Usage> result = new ArrayList<>();
        for (final Usage usage : event.getUsages()) {
            if (usage.getBillingMode() != BillingMode.IN_ARREAR) {
                continue;
            }
            result.add(usage);
        }
        return result;
    }

    public static class SubscriptionUsageInArrearItemsAndNextNotificationDate {

        private final List<InvoiceItem> invoiceItems;
        private final Map<String, LocalDate> perUsageNotificationDates;
        private final Set<TrackingRecordId> trackingIds;

        public SubscriptionUsageInArrearItemsAndNextNotificationDate() {
            this.invoiceItems = new LinkedList<InvoiceItem>();
            this.perUsageNotificationDates = new HashMap<String, LocalDate>();
            this.trackingIds = new HashSet<>();
        }

        public void addUsageInArrearItemsAndNextNotificationDate(final String usageName, final UsageInArrearItemsAndNextNotificationDate input) {
            if (!input.getInvoiceItems().isEmpty()) {
                invoiceItems.addAll(input.getInvoiceItems());
            }

            if (input.getNextNotificationDate() != null) {
                perUsageNotificationDates.put(usageName, input.getNextNotificationDate());
            }
        }

        public void addTrackingIds(final Set<TrackingRecordId> input) {
            trackingIds.addAll(input);
        }

        public List<InvoiceItem> getInvoiceItems() {
            return invoiceItems;
        }

        public Map<String, LocalDate> getPerUsageNotificationDates() {
            return perUsageNotificationDates;
        }

        public Set<TrackingRecordId> getTrackingIds() {
            return trackingIds;
        }
    }

    private static class UsageKey {

        private final String usageName;
        private final DateTime catalogVersion;

        public UsageKey(final String usageName, final DateTime catalogVersion) {
            this.usageName = usageName;
            this.catalogVersion = catalogVersion;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final UsageKey usageKey = (UsageKey) o;

            if (usageName != null ? !usageName.equals(usageKey.usageName) : usageKey.usageName != null) {
                return false;
            }
            return catalogVersion != null ? catalogVersion.compareTo(usageKey.catalogVersion) == 0 : usageKey.catalogVersion == null;
        }

        @Override
        public int hashCode() {
            int result = usageName != null ? usageName.hashCode() : 0;
            result = 31 * result + (catalogVersion != null ? catalogVersion.hashCode() : 0);
            return result;
        }
    }

}
