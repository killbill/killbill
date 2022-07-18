/*
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.invoice.api.DryRunInfo;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.SubscriptionFutureNotificationDates;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.TrackingRecordId;
import org.killbill.billing.invoice.optimizer.InvoiceOptimizerBase.AccountInvoices;
import org.killbill.billing.invoice.usage.RawUsageOptimizer;
import org.killbill.billing.invoice.usage.RawUsageOptimizer.RawUsageOptimizerResult;
import org.killbill.billing.invoice.usage.SubscriptionUsageInArrear;
import org.killbill.billing.invoice.usage.SubscriptionUsageInArrear.SubscriptionUsageInArrearItemsAndNextNotificationDate;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.util.annotation.VisibleForTesting;
import org.killbill.billing.util.collect.Iterables;
import org.killbill.billing.util.collect.MultiValueHashMap;
import org.killbill.billing.util.collect.MultiValueMap;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.config.definition.InvoiceConfig.UsageDetailMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public InvoiceGeneratorResult generateItems(final ImmutableAccountData account,
                                                final UUID invoiceId,
                                                final BillingEventSet eventSet,
                                                final AccountInvoices existingInvoices,
                                                final LocalDate targetDate,
                                                final Currency targetCurrency,
                                                final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates,
                                                final DryRunInfo dryRunInfo,
                                                final InternalCallContext internalCallContext) throws InvoiceApiException {
        final Map<UUID, List<InvoiceItem>> perSubscriptionInArrearUsageItems = extractPerSubscriptionExistingInArrearUsageItems(eventSet.getUsages(), existingInvoices.getInvoices());
        try {
            // Pretty-print the generated invoice items from the junction events
            final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger = new InvoiceItemGeneratorLogger(invoiceId, account.getId(), "usage", log);
            final UsageDetailMode usageDetailMode = invoiceConfig.getItemResultBehaviorMode(internalCallContext);
            final DateTime minBillingEventDate = getMinBillingEventDate(eventSet, internalCallContext);

            final Set<TrackingRecordId> trackingIds = new HashSet<>();
            final List<InvoiceItem> items = new ArrayList<>();
            final Iterator<BillingEvent> events = eventSet.iterator();

            final boolean isDryRun = dryRunInfo != null;
            RawUsageOptimizerResult rawUsgRes = null;
            List<BillingEvent> curEvents = new ArrayList<>();
            UUID curSubscriptionId = null;
            while (events.hasNext()) {
                final BillingEvent event = events.next();
                // Skip events that are posterior to the targetDate
                final LocalDate eventLocalEffectiveDate = internalCallContext.toLocalDate(event.getEffectiveDate());
                if (eventLocalEffectiveDate.isAfter(targetDate)) {
                    continue;
                }

                // Optimize to do the usage query only once after we know there are indeed some usage items
                if (rawUsgRes == null && event.getUsages().stream().anyMatch(input -> input.getBillingMode() == BillingMode.IN_ARREAR)) {
                    final Iterable<InvoiceItem> existingUsageItems = perSubscriptionInArrearUsageItems.values().stream()
                            .flatMap(Collection::stream)
                            .collect(Collectors.toUnmodifiableList());

                    rawUsgRes = rawUsageOptimizer.getInArrearUsage(minBillingEventDate, targetDate, existingUsageItems, eventSet.getUsages(), dryRunInfo, internalCallContext);

                    // Check existingInvoices#cutoffDate <= rawUsgRes#rawUsageStartDate + 1 P, where P = max{all Periods available} (e.g MONTHLY)
                    // To make it simpler we check existingInvoices#cutoffDate <= rawUsgRes#rawUsageStartDate, and warn if this is not the case
                    // (this mean we push folks to configure their system in such a way that we read (existing invoices) a bit too much as
                    // opposed to not enough, leading to double invoicing.
                    //
                    // Ask Kill Bill team for an optimal configuration based on your use case ;-)
                    if (existingInvoices.getCutoffDate() != null && existingInvoices.getCutoffDate().toDateTimeAtStartOfDay(DateTimeZone.UTC).compareTo(rawUsgRes.getRawUsageStartDate()) > 0) {
                        log.warn("Detected an invoice cuttOff date={}, and usage optimized start date= {} that could lead to some issues", existingInvoices.getCutoffDate(), rawUsgRes.getRawUsageStartDate());
                    }

                }

                // None of the billing events report any usage IN_ARREAR sections
                if (rawUsgRes == null) {
                    continue;
                }



                final UUID subscriptionId = event.getSubscriptionId();
                if (curSubscriptionId != null && !curSubscriptionId.equals(subscriptionId)) {
                    final SubscriptionUsageInArrear subscriptionUsageInArrear = new SubscriptionUsageInArrear(account.getId(), invoiceId, curEvents, rawUsgRes.getRawUsage(), rawUsgRes.getExistingTrackingIds(), targetDate, rawUsgRes.getRawUsageStartDate(), usageDetailMode, invoiceConfig, internalCallContext);
                    final List<InvoiceItem> usageInArrearItems = perSubscriptionInArrearUsageItems.get(curSubscriptionId);

                    final SubscriptionUsageInArrearItemsAndNextNotificationDate subscriptionResult = subscriptionUsageInArrear.computeMissingUsageInvoiceItems(usageInArrearItems != null ? usageInArrearItems : Collections.emptyList(), invoiceItemGeneratorLogger, isDryRun);
                    final List<InvoiceItem> newInArrearUsageItems = subscriptionResult.getInvoiceItems();
                    items.addAll(newInArrearUsageItems);
                    trackingIds.addAll(subscriptionResult.getTrackingIds());

                    updatePerSubscriptionNextNotificationUsageDate(curSubscriptionId, subscriptionResult.getPerUsageNotificationDates(), BillingMode.IN_ARREAR, perSubscriptionFutureNotificationDates);
                    curEvents = new ArrayList<>();
                }
                curSubscriptionId = subscriptionId;
                curEvents.add(event);
            }
            if (curSubscriptionId != null) {
                final SubscriptionUsageInArrear subscriptionUsageInArrear = new SubscriptionUsageInArrear(account.getId(), invoiceId, curEvents, rawUsgRes.getRawUsage(), rawUsgRes.getExistingTrackingIds(), targetDate, rawUsgRes.getRawUsageStartDate(), usageDetailMode, invoiceConfig, internalCallContext);
                final List<InvoiceItem> usageInArrearItems = perSubscriptionInArrearUsageItems.get(curSubscriptionId);

                final SubscriptionUsageInArrearItemsAndNextNotificationDate subscriptionResult = subscriptionUsageInArrear.computeMissingUsageInvoiceItems(usageInArrearItems != null ? usageInArrearItems : Collections.emptyList(), invoiceItemGeneratorLogger, isDryRun);
                final List<InvoiceItem> newInArrearUsageItems = subscriptionResult.getInvoiceItems();
                items.addAll(newInArrearUsageItems);
                trackingIds.addAll(subscriptionResult.getTrackingIds());
                updatePerSubscriptionNextNotificationUsageDate(curSubscriptionId, subscriptionResult.getPerUsageNotificationDates(), BillingMode.IN_ARREAR, perSubscriptionFutureNotificationDates);
            }
            invoiceItemGeneratorLogger.logItems();

            return new InvoiceGeneratorResult(items, trackingIds);
        } catch (final CatalogApiException e) {
            throw new InvoiceApiException(e);
        }
    }

    private DateTime getMinBillingEventDate(final BillingEventSet eventSet, final InternalCallContext internalCallContext) {
        DateTime minDate = null;
        for (final BillingEvent cur : eventSet) {
            if (minDate == null || minDate.compareTo(cur.getEffectiveDate()) > 0) {
                minDate = cur.getEffectiveDate();
            }
        }
        return minDate;
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
        for (final Entry<String, LocalDate> entry : nextBillingCycleDates.entrySet()) {
            subscriptionFutureNotificationDates.updateNextUsageDateIfRequired(entry.getKey(), usageBillingMode, entry.getValue());
        }
    }

    @VisibleForTesting
    Map<UUID, List<InvoiceItem>> extractPerSubscriptionExistingInArrearUsageItems(final Map<String, Usage> knownUsage, @Nullable final Iterable<Invoice> existingInvoices) {
        if (existingInvoices == null || Iterables.isEmpty(existingInvoices)) {
            return Collections.emptyMap();
        }

        final Iterable<InvoiceItem> usageInArrearItems = getUsageInArrearItems(knownUsage, existingInvoices);

        final MultiValueMap<UUID, InvoiceItem> result = new MultiValueHashMap<>();
        for (final InvoiceItem cur : usageInArrearItems) {
            result.putElement(cur.getSubscriptionId(), cur);
        }
        return result;
    }

    @VisibleForTesting
    Iterable<InvoiceItem> getUsageInArrearItems(final Map<String, Usage> knownUsage, final Iterable<Invoice> existingInvoices) {
        return Iterables.toStream(existingInvoices)
                        .map(Invoice::getInvoiceItems)
                        .flatMap(Collection::stream)
                        .filter(input -> {
                            if (input.getInvoiceItemType() == InvoiceItemType.USAGE) {
                                final Usage usage = knownUsage.get(input.getUsageName());
                                return usage != null && usage.getBillingMode() == BillingMode.IN_ARREAR;
                            }
                            return false;
                        })
                        .collect(Collectors.toUnmodifiableList());
    }
}
