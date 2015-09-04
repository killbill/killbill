/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.invoice.generator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.SubscriptionFutureNotificationDates;
import org.killbill.billing.invoice.usage.RawUsageOptimizer;
import org.killbill.billing.invoice.usage.RawUsageOptimizer.RawUsageOptimizerResult;
import org.killbill.billing.invoice.usage.SubscriptionConsumableInArrear;
import org.killbill.billing.invoice.usage.SubscriptionConsumableInArrear.SubscriptionConsumableInArrearItemsAndNextNotificationDate;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

public class UsageInvoiceItemGenerator extends InvoiceItemGenerator {

    private static final Logger log = LoggerFactory.getLogger(UsageInvoiceItemGenerator.class);

    private final RawUsageOptimizer rawUsageOptimizer;

    @Inject
    public UsageInvoiceItemGenerator(final RawUsageOptimizer rawUsageOptimizer) {
        this.rawUsageOptimizer = rawUsageOptimizer;
    }


    @Override
    public List<InvoiceItem> generateItems(final Account account,
                                           final UUID invoiceId,
                                           final BillingEventSet eventSet,
                                           @Nullable final List<Invoice> existingInvoices,
                                           final LocalDate targetDate,
                                           final Currency targetCurrency,
                                           final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates,
                                           final InternalCallContext internalCallContext) throws InvoiceApiException {

        final Map<UUID, List<InvoiceItem>> perSubscriptionConsumableInArrearUsageItems = extractPerSubscriptionExistingConsumableInArrearUsageItems(eventSet.getUsages(), existingInvoices);
        try {
            final List<InvoiceItem> items = Lists.newArrayList();
            final Iterator<BillingEvent> events = eventSet.iterator();

            RawUsageOptimizerResult rawUsageOptimizerResult = null;
            List<BillingEvent> curEvents = Lists.newArrayList();
            UUID curSubscriptionId = null;
            while (events.hasNext()) {
                final BillingEvent event = events.next();
                // Skip events that are posterior to the targetDate
                final LocalDate eventLocalEffectiveDate = new LocalDate(event.getEffectiveDate(), event.getAccount().getTimeZone());
                if (eventLocalEffectiveDate.isAfter(targetDate)) {
                    continue;
                }

                // Optimize to do the usage query only once after we know there are indeed some usage items
                if (rawUsageOptimizerResult == null &&
                    Iterables.any(event.getUsages(), new Predicate<Usage>() {
                        @Override
                        public boolean apply(@Nullable final Usage input) {
                            return (input.getUsageType() == UsageType.CONSUMABLE &&
                                    input.getBillingMode() == BillingMode.IN_ARREAR);
                        }
                    })) {
                    rawUsageOptimizerResult = rawUsageOptimizer.getConsumableInArrearUsage(new LocalDate(event.getEffectiveDate(), account.getTimeZone()), targetDate, Iterables.concat(perSubscriptionConsumableInArrearUsageItems.values()), eventSet.getUsages(), internalCallContext);
                }

                // None of the billing events report any usage (CONSUMABLE/IN_ARREAR) sections
                if (rawUsageOptimizerResult == null) {
                    continue;
                }

                final UUID subscriptionId = event.getSubscription().getId();
                if (curSubscriptionId != null && !curSubscriptionId.equals(subscriptionId)) {
                    final SubscriptionConsumableInArrear subscriptionConsumableInArrear = new SubscriptionConsumableInArrear(invoiceId, curEvents, rawUsageOptimizerResult.getRawUsage(), targetDate, rawUsageOptimizerResult.getRawUsageStartDate());
                    final List<InvoiceItem> consumableInUsageArrearItems = perSubscriptionConsumableInArrearUsageItems.get(curSubscriptionId);

                    final SubscriptionConsumableInArrearItemsAndNextNotificationDate subscriptionResult = subscriptionConsumableInArrear.computeMissingUsageInvoiceItems(consumableInUsageArrearItems != null ? consumableInUsageArrearItems : ImmutableList.<InvoiceItem>of());
                    final List<InvoiceItem> newInArrearUsageItems = subscriptionResult.getInvoiceItems();
                    items.addAll(newInArrearUsageItems);
                    updatePerSubscriptionNextNotificationUsageDate(curSubscriptionId, subscriptionResult.getPerUsageNotificationDates(), BillingMode.IN_ARREAR, perSubscriptionFutureNotificationDates);
                    curEvents = Lists.newArrayList();
                }
                curSubscriptionId = subscriptionId;
                curEvents.add(event);
            }
            if (curSubscriptionId != null) {
                final SubscriptionConsumableInArrear subscriptionConsumableInArrear = new SubscriptionConsumableInArrear(invoiceId, curEvents, rawUsageOptimizerResult.getRawUsage(), targetDate, rawUsageOptimizerResult.getRawUsageStartDate());
                final List<InvoiceItem> consumableInUsageArrearItems = perSubscriptionConsumableInArrearUsageItems.get(curSubscriptionId);

                final SubscriptionConsumableInArrearItemsAndNextNotificationDate subscriptionResult = subscriptionConsumableInArrear.computeMissingUsageInvoiceItems(consumableInUsageArrearItems != null ? consumableInUsageArrearItems : ImmutableList.<InvoiceItem>of());
                final List<InvoiceItem> newInArrearUsageItems = subscriptionResult.getInvoiceItems();
                items.addAll(newInArrearUsageItems);
                updatePerSubscriptionNextNotificationUsageDate(curSubscriptionId, subscriptionResult.getPerUsageNotificationDates(), BillingMode.IN_ARREAR, perSubscriptionFutureNotificationDates);
            }
            return items;

        } catch (CatalogApiException e) {
            throw new InvoiceApiException(e);
        }
    }

    private void updatePerSubscriptionNextNotificationUsageDate(final UUID subscriptionId, final Map<String, LocalDate> nextBillingCycleDates, final BillingMode usageBillingMode, final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates) {
        if (usageBillingMode == BillingMode.IN_ADVANCE) {
            throw new IllegalStateException("Not implemented Yet)");
        }

        SubscriptionFutureNotificationDates subscriptionFutureNotificationDates = perSubscriptionFutureNotificationDates.get(subscriptionId);
        if (subscriptionFutureNotificationDates == null) {
            subscriptionFutureNotificationDates = new SubscriptionFutureNotificationDates(null);
            perSubscriptionFutureNotificationDates.put(subscriptionId, subscriptionFutureNotificationDates);
        }
        for (final String usageName : nextBillingCycleDates.keySet()) {
            subscriptionFutureNotificationDates.updateNextUsageDateIfRequired(usageName, usageBillingMode, nextBillingCycleDates.get(usageName));
        }
    }

    private Map<UUID, List<InvoiceItem>> extractPerSubscriptionExistingConsumableInArrearUsageItems(final Map<String, Usage> knownUsage, @Nullable final List<Invoice> existingInvoices) {

        if (existingInvoices == null || existingInvoices.isEmpty()) {
            return ImmutableMap.of();
        }

        final Map<UUID, List<InvoiceItem>> result = new HashMap<UUID, List<InvoiceItem>>();
        final Iterable<InvoiceItem> usageConsumableInArrearItems = Iterables.concat(Iterables.transform(existingInvoices, new Function<Invoice, Iterable<InvoiceItem>>() {
            @Override
            public Iterable<InvoiceItem> apply(final Invoice input) {

                return Iterables.filter(input.getInvoiceItems(), new Predicate<InvoiceItem>() {
                    @Override
                    public boolean apply(final InvoiceItem input) {
                        if (input.getInvoiceItemType() == InvoiceItemType.USAGE) {
                            final Usage usage = knownUsage.get(input.getUsageName());
                            return usage.getUsageType() == UsageType.CONSUMABLE && usage.getBillingMode() == BillingMode.IN_ARREAR;
                        }
                        return false;
                    }
                });
            }
        }));

        for (InvoiceItem cur : usageConsumableInArrearItems) {
            List<InvoiceItem> perSubscriptionUsageItems = result.get(cur.getSubscriptionId());
            if (perSubscriptionUsageItems == null) {
                perSubscriptionUsageItems = new LinkedList<InvoiceItem>();
                result.put(cur.getSubscriptionId(), perSubscriptionUsageItems);
            }
            perSubscriptionUsageItems.add(cur);
        }
        return result;
    }
}
