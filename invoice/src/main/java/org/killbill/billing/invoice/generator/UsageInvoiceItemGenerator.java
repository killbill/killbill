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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
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
import org.killbill.billing.invoice.usage.RawUsageOptimizer.RawUsageResult;
import org.killbill.billing.invoice.usage.SubscriptionUsageInArrear;
import org.killbill.billing.invoice.usage.SubscriptionUsageInArrear.SubscriptionUsageInArrearItemsAndNextNotificationDate;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.config.definition.InvoiceConfig.UsageDetailMode;
import org.killbill.commons.utils.Preconditions;
import org.killbill.commons.utils.annotation.VisibleForTesting;
import org.killbill.commons.utils.collect.Iterables;
import org.killbill.commons.utils.collect.MultiValueHashMap;
import org.killbill.commons.utils.collect.MultiValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UsageInvoiceItemGenerator extends InvoiceItemGenerator {


    private static final String USAGE_TRANSITIONS = "USAGE_TRANSITIONS";

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
                                                final Iterable<PluginProperty> pluginProperties,
                                                final InternalCallContext internalCallContext) throws InvoiceApiException {
        // Trivial case
        if (eventSet.isEmpty()) {
            return new InvoiceGeneratorResult(Collections.emptyList(), Collections.emptySet());
        }


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
            final List<SubscriptionUsageInArrear> subsUsageInArrear = new ArrayList<>();
            final List<BillingEvent> curEvents = new ArrayList<>();

            final Iterable<InvoiceItem> existingUsageItems = perSubscriptionInArrearUsageItems.values().stream()
                                                                                              .flatMap(Collection::stream)
                                                                                              .collect(Collectors.toUnmodifiableList());
            final DateTime optimizedUsageStartDate = rawUsageOptimizer.getOptimizedStartDate(minBillingEventDate, targetDate, existingUsageItems, eventSet.getUsages(),  internalCallContext);

            UUID curSubscriptionId = null;
            boolean curSubscriptionHasUsage = false;
            while (events.hasNext()) {
                final BillingEvent event = events.next();
                // Skip events that are posterior to the targetDate
                final LocalDate eventLocalEffectiveDate = internalCallContext.toLocalDate(event.getEffectiveDate());
                if (eventLocalEffectiveDate.isAfter(targetDate)) {
                    continue;
                }


                final UUID subscriptionId = event.getSubscriptionId();
                if (curSubscriptionId != null &&
                    !curSubscriptionId.equals(subscriptionId)) {
                    if (curSubscriptionHasUsage) {
                        final SubscriptionUsageInArrear subscriptionUsageInArrear = new SubscriptionUsageInArrear(curSubscriptionId, account.getId(), invoiceId, curEvents, targetDate, optimizedUsageStartDate, usageDetailMode, invoiceConfig, internalCallContext);
                        subsUsageInArrear.add(subscriptionUsageInArrear);
                    }
                    curEvents.clear();
                    curSubscriptionHasUsage = false;
                }
                // Track for each event if there is any usage in arrear for current subscription
                curSubscriptionHasUsage = curSubscriptionHasUsage || event.getUsages().stream().anyMatch(input -> input.getBillingMode() == BillingMode.IN_ARREAR);
                curSubscriptionId = subscriptionId;
                curEvents.add(event);
            }
            if (curSubscriptionId != null && curSubscriptionHasUsage) {
                final SubscriptionUsageInArrear subscriptionUsageInArrear = new SubscriptionUsageInArrear(curSubscriptionId, account.getId(), invoiceId, curEvents, targetDate, optimizedUsageStartDate, usageDetailMode, invoiceConfig, internalCallContext);
                subsUsageInArrear.add(subscriptionUsageInArrear);
            }
            // Reset variables for cleanliness - won't be used anymore
            curSubscriptionId = null;
            curSubscriptionHasUsage = false;
            curEvents.clear();

            // Bail early if there is no usage in arrear
            if (subsUsageInArrear.isEmpty()) {
                return new InvoiceGeneratorResult(Collections.emptyList(), Collections.emptySet());
            }

            // Add the USAGE_TRANSITIONS property to the list prior to calling the usage plugin - if any
            final Map<Map.Entry, Set<DateTime>> transitionTimesMap = subsUsageInArrear.stream()
                    .flatMap(sub -> sub.getUsageIntervals().stream()
                            .map(interval -> new AbstractMap.SimpleEntry<>(
                                    // Use Map.Entry so this is available from the plugin
                                    new AbstractMap.SimpleEntry<>(sub.getSubscriptionId(),
                                            String.join(",", interval.getUnitTypes())),
                                    new HashSet<>(interval.getTransitionTimes()))))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (existing, replacement) -> {
                                existing.addAll(replacement);
                                return existing;
                            }));

            // Ensure dates are ordered ascending
            transitionTimesMap.replaceAll((key, value) -> value.stream()
                    .sorted()
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
            final LinkedList<PluginProperty> pluginPropertiesWithUsage = new LinkedList<PluginProperty>();
            pluginPropertiesWithUsage.addAll(Iterables.toStream(pluginProperties).collect(Collectors.toCollection(LinkedList::new)));
            pluginPropertiesWithUsage.add(new PluginProperty(USAGE_TRANSITIONS, transitionTimesMap, false));

            Preconditions.checkNotNull(optimizedUsageStartDate, "start should not be null");
            final RawUsageResult rawUsgRes = rawUsageOptimizer.getInArrearUsage(optimizedUsageStartDate, targetDate, dryRunInfo, pluginPropertiesWithUsage, internalCallContext);

            // Check existingInvoices#cutoffDate <= rawUsgRes#rawUsageStartDate + 1 P, where P = max{all Periods available} (e.g MONTHLY)
            // To make it simpler we check existingInvoices#cutoffDate <= rawUsgRes#rawUsageStartDate, and warn if this is not the case
            // (this mean we push folks to configure their system in such a way that we read (existing invoices) a bit too much as
            // opposed to not enough, leading to double invoicing.
            //
            // Ask Kill Bill team for an optimal configuration based on your use case ;-)
            if (existingInvoices.getCutoffDate() != null && existingInvoices.getCutoffDate().toDateTimeAtStartOfDay(DateTimeZone.UTC).compareTo(optimizedUsageStartDate) > 0) {
                log.warn("Detected an invoice cuttOff date={}, and usage optimized start date= {} that could lead to some issues", existingInvoices.getCutoffDate(), optimizedUsageStartDate);
            }


            for (SubscriptionUsageInArrear sub : subsUsageInArrear) {
                final List<InvoiceItem> usageInArrearItems = perSubscriptionInArrearUsageItems.get(sub.getSubscriptionId());
                final SubscriptionUsageInArrearItemsAndNextNotificationDate subscriptionResult = sub.computeMissingUsageInvoiceItems(usageInArrearItems != null ? usageInArrearItems : Collections.emptyList(),
                                                                                                                                                           rawUsgRes.getRawUsage(),
                                                                                                                                                           rawUsgRes.getExistingTrackingIds(),                                                                                                                                      invoiceItemGeneratorLogger, isDryRun);
                final List<InvoiceItem> newInArrearUsageItems = subscriptionResult.getInvoiceItems();
                items.addAll(newInArrearUsageItems);
                trackingIds.addAll(subscriptionResult.getTrackingIds());
                updatePerSubscriptionNextNotificationUsageDate(sub.getSubscriptionId(), subscriptionResult.getPerUsageNotificationDates(), BillingMode.IN_ARREAR, perSubscriptionFutureNotificationDates);
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
