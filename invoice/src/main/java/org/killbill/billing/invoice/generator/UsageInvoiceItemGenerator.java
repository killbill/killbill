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

package org.killbill.billing.invoice.generator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.SubscriptionFutureNotificationDates;
import org.killbill.billing.invoice.usage.RawUsageOptimizer;
import org.killbill.billing.invoice.usage.RawUsageOptimizer.RawUsageOptimizerResult;
import org.killbill.billing.invoice.usage.SubscriptionUsageInArrear;
import org.killbill.billing.invoice.usage.SubscriptionUsageInArrear.SubscriptionUsageInArrearItemsAndNextNotificationDate;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.config.definition.InvoiceConfig.UsageDetailMode;
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
    private final InvoiceConfig invoiceConfig;

    @Inject
    public UsageInvoiceItemGenerator(final RawUsageOptimizer rawUsageOptimizer, final InvoiceConfig invoiceConfig) {
        this.rawUsageOptimizer = rawUsageOptimizer;
        this.invoiceConfig = invoiceConfig;
    }

    @Override
    public List<InvoiceItem> generateItems(final ImmutableAccountData account,
                                           final UUID invoiceId,
                                           final BillingEventSet eventSet,
                                           @Nullable final Iterable<Invoice> existingInvoices,
                                           final LocalDate targetDate,
                                           final Currency targetCurrency,
                                           final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates,
                                           final InternalCallContext internalCallContext) throws InvoiceApiException {
        final Map<UUID, List<InvoiceItem>> perSubscriptionInArrearUsageItems = extractPerSubscriptionExistingInArrearUsageItems(eventSet.getUsages(), existingInvoices);
        try {
            // Pretty-print the generated invoice items from the junction events
            final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger = new InvoiceItemGeneratorLogger(invoiceId, account.getId(), "usage", log);
            final UsageDetailMode usageDetailMode = invoiceConfig.getItemResultBehaviorMode(internalCallContext);
            final LocalDate minBillingEventDate = getMinBillingEventDate(eventSet, internalCallContext);

            final List<InvoiceItem> items = Lists.newArrayList();
            final Iterator<BillingEvent> events = eventSet.iterator();

            RawUsageOptimizerResult rawUsageOptimizerResult = null;
            List<BillingEvent> curEvents = Lists.newArrayList();
            UUID curSubscriptionId = null;
            while (events.hasNext()) {
                final BillingEvent event = events.next();
                // Skip events that are posterior to the targetDate
                final LocalDate eventLocalEffectiveDate = internalCallContext.toLocalDate(event.getEffectiveDate());
                if (eventLocalEffectiveDate.isAfter(targetDate)) {
                    continue;
                }

                // Optimize to do the usage query only once after we know there are indeed some usage items
                if (rawUsageOptimizerResult == null &&
                    Iterables.any(event.getUsages(), new Predicate<Usage>() {
                        @Override
                        public boolean apply(@Nullable final Usage input) {
                            return input.getBillingMode() == BillingMode.IN_ARREAR;
                        }
                    })) {
                    rawUsageOptimizerResult = rawUsageOptimizer.getInArrearUsage(minBillingEventDate, targetDate, Iterables.concat(perSubscriptionInArrearUsageItems.values()), eventSet.getUsages(), internalCallContext);
                }

                // None of the billing events report any usage IN_ARREAR sections
                if (rawUsageOptimizerResult == null) {
                    continue;
                }

                final UUID subscriptionId = event.getSubscription().getId();
                if (curSubscriptionId != null && !curSubscriptionId.equals(subscriptionId)) {
                    final SubscriptionUsageInArrear subscriptionUsageInArrear = new SubscriptionUsageInArrear(account.getId(), invoiceId, curEvents, rawUsageOptimizerResult.getRawUsage(), targetDate, rawUsageOptimizerResult.getRawUsageStartDate(), usageDetailMode, internalCallContext);
                    final List<InvoiceItem> usageInArrearItems = perSubscriptionInArrearUsageItems.get(curSubscriptionId);

                    final SubscriptionUsageInArrearItemsAndNextNotificationDate subscriptionResult = subscriptionUsageInArrear.computeMissingUsageInvoiceItems(usageInArrearItems != null ? usageInArrearItems : ImmutableList.<InvoiceItem>of(), invoiceItemGeneratorLogger);
                    final List<InvoiceItem> newInArrearUsageItems = subscriptionResult.getInvoiceItems();
                    items.addAll(newInArrearUsageItems);
                    updatePerSubscriptionNextNotificationUsageDate(curSubscriptionId, subscriptionResult.getPerUsageNotificationDates(), BillingMode.IN_ARREAR, perSubscriptionFutureNotificationDates);
                    curEvents = Lists.newArrayList();
                }
                curSubscriptionId = subscriptionId;
                curEvents.add(event);
            }
            if (curSubscriptionId != null) {
                final SubscriptionUsageInArrear subscriptionUsageInArrear = new SubscriptionUsageInArrear(account.getId(), invoiceId, curEvents, rawUsageOptimizerResult.getRawUsage(), targetDate, rawUsageOptimizerResult.getRawUsageStartDate(), usageDetailMode, internalCallContext);
                final List<InvoiceItem> usageInArrearItems = perSubscriptionInArrearUsageItems.get(curSubscriptionId);

                final SubscriptionUsageInArrearItemsAndNextNotificationDate subscriptionResult = subscriptionUsageInArrear.computeMissingUsageInvoiceItems(usageInArrearItems != null ? usageInArrearItems : ImmutableList.<InvoiceItem>of(), invoiceItemGeneratorLogger);
                final List<InvoiceItem> newInArrearUsageItems = subscriptionResult.getInvoiceItems();
                items.addAll(newInArrearUsageItems);
                updatePerSubscriptionNextNotificationUsageDate(curSubscriptionId, subscriptionResult.getPerUsageNotificationDates(), BillingMode.IN_ARREAR, perSubscriptionFutureNotificationDates);
            }

            invoiceItemGeneratorLogger.logItems();

            return items;
        } catch (final CatalogApiException e) {
            throw new InvoiceApiException(e);
        }
    }

    private LocalDate getMinBillingEventDate(final BillingEventSet eventSet, final InternalCallContext internalCallContext) {
        DateTime minDate = null;
        for (final BillingEvent cur : eventSet) {
            if (minDate == null || minDate.compareTo(cur.getEffectiveDate()) > 0) {
                minDate = cur.getEffectiveDate();
            }
        }
        return internalCallContext.toLocalDate(minDate);
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

    private Map<UUID, List<InvoiceItem>> extractPerSubscriptionExistingInArrearUsageItems(final Map<String, Usage> knownUsage, @Nullable final Iterable<Invoice> existingInvoices) {
        if (existingInvoices == null || Iterables.isEmpty(existingInvoices)) {
            return ImmutableMap.of();
        }

        final Map<UUID, List<InvoiceItem>> result = new HashMap<UUID, List<InvoiceItem>>();
        final Iterable<InvoiceItem> usageInArrearItems = Iterables.concat(Iterables.transform(existingInvoices, new Function<Invoice, Iterable<InvoiceItem>>() {
            @Override
            public Iterable<InvoiceItem> apply(final Invoice input) {

                return Iterables.filter(input.getInvoiceItems(), new Predicate<InvoiceItem>() {
                    @Override
                    public boolean apply(final InvoiceItem input) {
                        if (input.getInvoiceItemType() == InvoiceItemType.USAGE) {
                            final Usage usage = knownUsage.get(input.getUsageName());
                            return usage.getBillingMode() == BillingMode.IN_ARREAR;
                        }
                        return false;
                    }
                });
            }
        }));

        for (final InvoiceItem cur : usageInArrearItems) {
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
