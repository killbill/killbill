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

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.generator.InvoiceItemGenerator.InvoiceItemGeneratorLogger;
import org.killbill.billing.invoice.usage.ContiguousIntervalUsageInArrear.UsageInArrearItemsAndNextNotificationDate;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.usage.RawUsage;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

/**
 * There is one such class created for each subscriptionId referenced in the billingEvents.
 */
public class SubscriptionUsageInArrear {

    private static final Comparator<RawUsage> RAW_USAGE_DATE_COMPARATOR = new Comparator<RawUsage>() {
        @Override
        public int compare(final RawUsage o1, final RawUsage o2) {
            int compared = o1.getDate().compareTo(o2.getDate());
            if (compared != 0) {
                return compared;
            } else {
                compared = o1.getUnitType().compareTo(o2.getUnitType());
                if (compared != 0) {
                    return compared;
                } else {
                    return o1.hashCode() != o2.hashCode() ? o1.hashCode() - o2.hashCode() : 0;
                }
            }
        }
    };

    private final UUID accountId;
    private final UUID invoiceId;
    private final List<BillingEvent> subscriptionBillingEvents;
    private final LocalDate targetDate;
    private final List<RawUsage> rawSubscriptionUsage;
    private final LocalDate rawUsageStartDate;
    private final InternalTenantContext internalTenantContext;

    public SubscriptionUsageInArrear(final UUID accountId,
                                     final UUID invoiceId,
                                     final List<BillingEvent> subscriptionBillingEvents,
                                     final List<RawUsage> rawUsage,
                                     final LocalDate targetDate,
                                     final LocalDate rawUsageStartDate,
                                     final InternalTenantContext internalTenantContext) {

        this.accountId = accountId;
        this.invoiceId = invoiceId;
        this.subscriptionBillingEvents = subscriptionBillingEvents;
        this.targetDate = targetDate;
        this.rawUsageStartDate = rawUsageStartDate;
        this.internalTenantContext = internalTenantContext;
        // Extract raw usage for that subscription and sort it by date
        this.rawSubscriptionUsage = Ordering.<RawUsage>from(RAW_USAGE_DATE_COMPARATOR).sortedCopy(Iterables.filter(rawUsage, new Predicate<RawUsage>() {
            @Override
            public boolean apply(final RawUsage input) {
                return input.getSubscriptionId().equals(subscriptionBillingEvents.get(0).getSubscription().getId());
            }
        }));
    }

    /**
     * Based on billing events, (@code existingUsage} and targetDate, figure out what remains to be billed.
     *
     * @param existingUsage the existing on disk usage items.
     * @throws CatalogApiException
     */
    public SubscriptionUsageInArrearItemsAndNextNotificationDate computeMissingUsageInvoiceItems(final List<InvoiceItem> existingUsage, final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger) throws CatalogApiException {
        final SubscriptionUsageInArrearItemsAndNextNotificationDate result = new SubscriptionUsageInArrearItemsAndNextNotificationDate();
        final List<ContiguousIntervalUsageInArrear> billingEventTransitionTimePeriods = computeInArrearUsageInterval();
        for (final ContiguousIntervalUsageInArrear usageInterval : billingEventTransitionTimePeriods) {
            final UsageInArrearItemsAndNextNotificationDate newItemsAndDate = usageInterval.computeMissingItemsAndNextNotificationDate(existingUsage);

            // For debugging purposes
            invoiceItemGeneratorLogger.append(usageInterval, newItemsAndDate.getInvoiceItems());

            result.addUsageInArrearItemsAndNextNotificationDate(usageInterval.getUsage().getName(), newItemsAndDate);
        }
        return result;
    }

    @VisibleForTesting
    List<ContiguousIntervalUsageInArrear> computeInArrearUsageInterval() {
        final List<ContiguousIntervalUsageInArrear> usageIntervals = Lists.newLinkedList();

        final Map<String, ContiguousIntervalUsageInArrear> inFlightInArrearUsageIntervals = new HashMap<String, ContiguousIntervalUsageInArrear>();

        final Set<String> allSeenUsage = new HashSet<String>();

        for (final BillingEvent event : subscriptionBillingEvents) {

            // Extract all in arrear /consumable usage section for that billing event.
            final List<Usage> usages = findUsageInArrearUsages(event);
            allSeenUsage.addAll(Collections2.transform(usages, new Function<Usage, String>() {
                @Override
                public String apply(final Usage input) {
                    return input.getName();
                }
            }));

            // All inflight usage interval are candidates to be closed unless we see that current billing event referencing the same usage section.
            final Set<String> toBeClosed = new HashSet<String>(allSeenUsage);

            for (final Usage usage : usages) {

                // Add inflight usage interval if non existent
                ContiguousIntervalUsageInArrear existingInterval = inFlightInArrearUsageIntervals.get(usage.getName());
                if (existingInterval == null) {
                    existingInterval = new ContiguousIntervalUsageInArrear(usage, accountId, invoiceId, rawSubscriptionUsage, targetDate, rawUsageStartDate, internalTenantContext);
                    inFlightInArrearUsageIntervals.put(usage.getName(), existingInterval);
                }
                // Add billing event for that usage interval
                existingInterval.addBillingEvent(event);
                // Remove usage interval for toBeClosed set
                toBeClosed.remove(usage.getName());
            }

            // Build the usage interval that are no longer referenced
            for (final String usageName : toBeClosed) {
                final ContiguousIntervalUsageInArrear interval = inFlightInArrearUsageIntervals.remove(usageName);
                if (interval != null) {
                    interval.addBillingEvent(event);
                    usageIntervals.add(interval.build(true));
                }
            }
        }
        for (final String usageName : inFlightInArrearUsageIntervals.keySet()) {
            usageIntervals.add(inFlightInArrearUsageIntervals.get(usageName).build(false));
        }
        inFlightInArrearUsageIntervals.clear();
        return usageIntervals;
    }

    private List<Usage> findUsageInArrearUsages(final BillingEvent event) {
        if (event.getUsages().isEmpty()) {
            return Collections.emptyList();
        }

        final List<Usage> result = Lists.newArrayList();
        for (final Usage usage : event.getUsages()) {
            if (usage.getBillingMode() != BillingMode.IN_ARREAR) {
                continue;
            }
            result.add(usage);
        }
        return result;
    }

    public class SubscriptionUsageInArrearItemsAndNextNotificationDate {

        private List<InvoiceItem> invoiceItems;
        private Map<String, LocalDate> perUsageNotificationDates;

        public SubscriptionUsageInArrearItemsAndNextNotificationDate() {
            this.invoiceItems = null;
            this.perUsageNotificationDates = null;
        }

        public void addUsageInArrearItemsAndNextNotificationDate(final String usageName, final UsageInArrearItemsAndNextNotificationDate input) {
            if (!input.getInvoiceItems().isEmpty()) {
                if (invoiceItems == null) {
                    invoiceItems = new LinkedList<InvoiceItem>();
                }
                invoiceItems.addAll(input.getInvoiceItems());

            }

            if (input.getNextNotificationDate() != null) {
                if (perUsageNotificationDates == null) {
                    perUsageNotificationDates = new HashMap<String, LocalDate>();
                }
                perUsageNotificationDates.put(usageName, input.getNextNotificationDate());
            }
        }

        public List<InvoiceItem> getInvoiceItems() {
            return invoiceItems != null ? invoiceItems : ImmutableList.<InvoiceItem>of();
        }

        public Map<String, LocalDate> getPerUsageNotificationDates() {
            return perUsageNotificationDates != null ? perUsageNotificationDates : ImmutableMap.<String, LocalDate>of();
        }
    }
}
