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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Tier;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.model.UsageInvoiceItem;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.usage.api.UsageUserApi;
import org.killbill.billing.util.callcontext.TenantContext;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class SubscriptionConsumableInArrear {

    private UUID invoiceId;
    private final String unitType;
    private final List<BillingEvent> subscriptionBillingEvents;
    private final UsageUserApi usageApi;
    private final LocalDate targetDate;
    private final TenantContext context;

    public SubscriptionConsumableInArrear(final UUID invoiceId, final String unitType, final List<BillingEvent> subscriptionBillingEvents, final UsageUserApi usageApi, final LocalDate targetDate, final TenantContext context) {
        this.invoiceId = invoiceId;
        this.unitType = unitType;
        this.subscriptionBillingEvents = subscriptionBillingEvents;
        this.usageApi = usageApi;
        this.targetDate = targetDate;
        this.context = context;
    }

    public List<InvoiceItem> computeMissingUsageInvoiceItems(final List<InvoiceItem> existingUsage) throws CatalogApiException {

        final List<InvoiceItem> result = Lists.newLinkedList();
        final List<ContiguousInArrearUsageInterval> billingEventTransitionTimePeriods = computeBillingEventTransitionTimePeriods();
        for (ContiguousInArrearUsageInterval usageInterval : billingEventTransitionTimePeriods) {
            result.addAll(usageInterval.computeMissingItems(existingUsage));
        }
        return result;
    }


    static List<TieredBlock> getTieredBlocks(final Usage usage, final String unitType) {

        Preconditions.checkArgument(usage.getTiers().length > 0);

        final List<TieredBlock> result = Lists.newLinkedList();
        for (Tier tier : usage.getTiers()) {

            for (TieredBlock tierBlock : tier.getTieredBlocks()) {
                if (tierBlock.getUnit().getName().equals(unitType)) {
                    result.add(tierBlock);
                }
            }
        }
        return result;
    }

    static DateTime localDateToEndOfDayInAccountTimezone(final LocalDate input, final DateTimeZone accountTimeZone) {
        final DateTime dateTimeInAccountTimeZone = new DateTime(input.getYear(), input.getMonthOfYear(), input.getDayOfMonth(), 23, 59, 59, accountTimeZone);
        return new DateTime(dateTimeInAccountTimeZone, DateTimeZone.UTC);
    }


    private List<ContiguousInArrearUsageInterval> computeBillingEventTransitionTimePeriods() {

        final List<ContiguousInArrearUsageInterval> usageInterval = Lists.newLinkedList();

        ContiguousInArrearUsageInterval existingInterval = null;
        for (BillingEvent event : subscriptionBillingEvents) {
            final Usage usage = findUsage(event);
            if (usage == null || !usage.equals(existingInterval.getUsage())) {
                if (existingInterval != null) {
                    usageInterval.add(existingInterval.build(true));
                    existingInterval = null;
                }
            }

            if (usage != null) {
                if (existingInterval == null) {
                    existingInterval = new ContiguousInArrearUsageInterval(usage, invoiceId, unitType, usageApi, targetDate, context);
                }
                existingInterval.addBillingEvent(event);
            }
        }
        if (existingInterval != null) {
            usageInterval.add(existingInterval.build(false));
        }
        return usageInterval;
    }

    private Usage findUsage(final BillingEvent event) {
        if (event.getUsages().size() == 0) {
            return null;
        }
        for (Usage usage : event.getUsages()) {
            if (usage.getUsageType() != UsageType.CONSUMABLE ||
                usage.getBillingMode() != BillingMode.IN_ARREAR) {
                continue;
            }

            List<TieredBlock> tieredBlock = getTieredBlocks(usage, unitType);
            if (tieredBlock.size() > 0) {
                return usage;
            }
        }
        return null;
    }

    private void addMissingTransitionTimes(final List<LocalDate> transitionTimes, final List<UsageInvoiceItem> existingUsage) {

        Preconditions.checkArgument(transitionTimes.size() > 0);

        final LocalDate startDate = transitionTimes.get(0);
        final LocalDate endDate = transitionTimes.get(transitionTimes.size() - 1);

        for (UsageInvoiceItem ii : existingUsage) {
            if (ii.getEndDate().compareTo(startDate) <= 0 || ii.getStartDate().compareTo(endDate) >= 0) {
                continue;
            }
            if (ii.getStartDate().compareTo(startDate) < 0 && ii.getEndDate().compareTo(endDate) <= 0) {
                transitionTimes.add(ii.getEndDate());
            } else if (ii.getStartDate().compareTo(startDate) >= 0 && ii.getEndDate().compareTo(endDate) > 0) {
                transitionTimes.add(ii.getStartDate());
            } else {
                transitionTimes.add(ii.getStartDate());
                transitionTimes.add(ii.getEndDate());
            }
        }

        final Set<LocalDate> uniqueTransitions = new HashSet<LocalDate>(transitionTimes);
        transitionTimes.clear();
        transitionTimes.addAll(uniqueTransitions);
        Collections.sort(transitionTimes);
    }

}
