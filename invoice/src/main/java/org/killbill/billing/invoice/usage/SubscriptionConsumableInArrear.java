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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.usage.api.UsageUserApi;
import org.killbill.billing.util.callcontext.TenantContext;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

/**
 * There is one such class created for each subscriptionId referenced in the billingEvents.
 */
public class SubscriptionConsumableInArrear {

    private final UUID invoiceId;
    private final List<BillingEvent> subscriptionBillingEvents;
    private final UsageUserApi usageApi;
    private final LocalDate targetDate;
    private final TenantContext context;
    private final boolean insertZeroAmountItems;

    public SubscriptionConsumableInArrear(final UUID invoiceId, final List<BillingEvent> subscriptionBillingEvents, final UsageUserApi usageApi, final boolean insertZeroAmountItems, LocalDate targetDate, final TenantContext context) {
        this.invoiceId = invoiceId;
        this.subscriptionBillingEvents = subscriptionBillingEvents;
        this.usageApi = usageApi;
        this.insertZeroAmountItems = insertZeroAmountItems;
        this.targetDate = targetDate;
        this.context = context;
    }

    /**
     * Based on billing events, (@code existingUsage} and targetDate, figure out what remains to be billed.
     *
     * @param existingUsage the existing on disk usage items.
     * @return
     * @throws CatalogApiException
     */
    public List<InvoiceItem> computeMissingUsageInvoiceItems(final List<InvoiceItem> existingUsage) throws CatalogApiException {

        final List<InvoiceItem> result = Lists.newLinkedList();
        final List<ContiguousIntervalConsumableInArrear> billingEventTransitionTimePeriods = computeInArrearUsageInterval();
        for (ContiguousIntervalConsumableInArrear usageInterval : billingEventTransitionTimePeriods) {
            result.addAll(usageInterval.computeMissingItems(existingUsage));
        }
        return result;
    }

    @VisibleForTesting
    List<ContiguousIntervalConsumableInArrear> computeInArrearUsageInterval() {

        final List<ContiguousIntervalConsumableInArrear> usageIntervals = Lists.newLinkedList();

        final Map<String, ContiguousIntervalConsumableInArrear> inFlightInArrearUsageIntervals = new HashMap<String, ContiguousIntervalConsumableInArrear>();

        final Set<String> allSeenUsage = new HashSet<String>();

        for (BillingEvent event : subscriptionBillingEvents) {


            // Extract all in arrear /consumable usage section for that billing event.
            final List<Usage> usages = findConsumableInArrearUsages(event);
            allSeenUsage.addAll(Collections2.transform(usages, new Function<Usage, String>() {
                @Override
                public String apply(final Usage input) {
                    return input.getName();
                }
            }));

            // All inflight usage interval are candidates to be closed unless we see that current billing event referencing the same usage section.
            final Set<String> toBeClosed = new HashSet<String>(allSeenUsage);

            for (Usage usage : usages) {

                // Add inflight usage interval if non existent
                ContiguousIntervalConsumableInArrear existingInterval = inFlightInArrearUsageIntervals.get(usage.getName());
                if (existingInterval == null) {
                    existingInterval = new ContiguousIntervalConsumableInArrear(usage, invoiceId, usageApi, insertZeroAmountItems, targetDate, context);
                    inFlightInArrearUsageIntervals.put(usage.getName(), existingInterval);
                }
                // Add billing event for that usage interval
                existingInterval.addBillingEvent(event);
                // Remove usage interval for toBeClosed set
                toBeClosed.remove(usage.getName());
            }

            // Build the usage interval that are no longer referenced
            for (String usageName : toBeClosed) {
                final ContiguousIntervalConsumableInArrear interval = inFlightInArrearUsageIntervals.remove(usageName);
                if (interval != null) {
                    interval.addBillingEvent(event);
                    usageIntervals.add(interval.build(true));
                }
            }
        }
        for (String usageName : inFlightInArrearUsageIntervals.keySet()) {
            usageIntervals.add(inFlightInArrearUsageIntervals.get(usageName).build(false));
        }
        inFlightInArrearUsageIntervals.clear();
        return usageIntervals;
    }

    List<Usage> findConsumableInArrearUsages(final BillingEvent event) {
        if (event.getUsages().size() == 0) {
            return Collections.emptyList();
        }

        final List<Usage> result = Lists.newArrayList();
        for (Usage usage : event.getUsages()) {
            if (usage.getUsageType() != UsageType.CONSUMABLE ||
                usage.getBillingMode() != BillingMode.IN_ARREAR) {
                continue;
            }
            result.add(usage);
        }
        return result;
    }
}
