/*
 * Copyright 2010-2013 Ning, Inc.
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.joda.time.Months;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.SubscriptionFutureNotificationDates;
import org.killbill.billing.invoice.model.BillingModeGenerator;
import org.killbill.billing.invoice.model.DefaultBillingModeGenerator;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.InvalidDateSequenceException;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.invoice.model.RecurringInvoiceItemData;
import org.killbill.billing.invoice.model.RecurringInvoiceItemDataWithNextBillingCycleDate;
import org.killbill.billing.invoice.tree.AccountItemTree;
import org.killbill.billing.invoice.usage.RawUsageOptimizer;
import org.killbill.billing.invoice.usage.RawUsageOptimizer.RawUsageOptimizerResult;
import org.killbill.billing.invoice.usage.SubscriptionConsumableInArrear;
import org.killbill.billing.invoice.usage.SubscriptionConsumableInArrear.SubscriptionConsumableInArrearItemsAndNextNotificationDate;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.util.config.InvoiceConfig;
import org.killbill.billing.util.currency.KillBillMoney;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

public class DefaultInvoiceGenerator implements InvoiceGenerator {

    private static final Logger log = LoggerFactory.getLogger(DefaultInvoiceGenerator.class);

    private final Clock clock;
    private final InvoiceConfig config;
    private final RawUsageOptimizer rawUsageOptimizer;
    final BillingModeGenerator billingModeGenerator;

    @Inject
    public DefaultInvoiceGenerator(final Clock clock, final InvoiceConfig config, final RawUsageOptimizer rawUsageOptimizer) {
        this.clock = clock;
        this.config = config;
        this.rawUsageOptimizer = rawUsageOptimizer;
        this.billingModeGenerator = new DefaultBillingModeGenerator();
    }

    /*
     * adjusts target date to the maximum invoice target date, if future invoices exist
     */
    @Override
    public InvoiceWithMetadata generateInvoice(final Account account, @Nullable final BillingEventSet events,
                                               @Nullable final List<Invoice> existingInvoices,
                                               final LocalDate targetDate,
                                               final Currency targetCurrency, final InternalCallContext context) throws InvoiceApiException {
        if ((events == null) || (events.size() == 0) || events.isAccountAutoInvoiceOff()) {
            return new InvoiceWithMetadata(null, ImmutableMap.<UUID, SubscriptionFutureNotificationDates>of());
        }

        validateTargetDate(targetDate);
        final LocalDate adjustedTargetDate = adjustTargetDate(existingInvoices, targetDate);

        final Invoice invoice = new DefaultInvoice(account.getId(), new LocalDate(clock.getUTCNow(), account.getTimeZone()), adjustedTargetDate, targetCurrency);
        final UUID invoiceId = invoice.getId();
        final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates = new HashMap<UUID, SubscriptionFutureNotificationDates>();

        final List<InvoiceItem> fixedAndRecurringItems = generateFixedAndRecurringInvoiceItems(account.getId(), invoiceId, events, existingInvoices, adjustedTargetDate, targetCurrency, perSubscriptionFutureNotificationDates);
        invoice.addInvoiceItems(fixedAndRecurringItems);

        final List<InvoiceItem> usageItems = generateUsageConsumableInArrearItems(account, invoiceId, events, existingInvoices, targetDate, perSubscriptionFutureNotificationDates, context);
        invoice.addInvoiceItems(usageItems);

        return new InvoiceWithMetadata(invoice.getInvoiceItems().isEmpty() ? null : invoice, perSubscriptionFutureNotificationDates);
    }

    private List<InvoiceItem> generateUsageConsumableInArrearItems(final Account account,
                                                                   final UUID invoiceId, final BillingEventSet eventSet,
                                                                   @Nullable final List<Invoice> existingInvoices, final LocalDate targetDate,
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

    private List<InvoiceItem> generateFixedAndRecurringInvoiceItems(final UUID accountId, final UUID invoiceId, final BillingEventSet eventSet,
                                                                    @Nullable final List<Invoice> existingInvoices, final LocalDate targetDate,
                                                                    final Currency targetCurrency, Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDate) throws InvoiceApiException {
        final AccountItemTree accountItemTree = new AccountItemTree(accountId, invoiceId);
        if (existingInvoices != null) {
            for (final Invoice invoice : existingInvoices) {
                for (final InvoiceItem item : invoice.getInvoiceItems()) {
                    if (item.getSubscriptionId() == null || // Always include migration invoices, credits, external charges etc.
                        !eventSet.getSubscriptionIdsWithAutoInvoiceOff()
                                 .contains(item.getSubscriptionId())) { //don't add items with auto_invoice_off tag
                        accountItemTree.addExistingItem(item);
                    }
                }
            }
        }

        // Generate list of proposed invoice items based on billing events from junction-- proposed items are ALL items since beginning of time
        final List<InvoiceItem> proposedItems = new ArrayList<InvoiceItem>();
        processRecurringBillingEvents(invoiceId, accountId, eventSet, targetDate, targetCurrency, proposedItems, perSubscriptionFutureNotificationDate);
        processFixedBillingEvents(invoiceId, accountId, eventSet, targetDate, targetCurrency, proposedItems);

        accountItemTree.mergeWithProposedItems(proposedItems);
        return accountItemTree.getResultingItemList();
    }

    private void validateTargetDate(final LocalDate targetDate) throws InvoiceApiException {
        final int maximumNumberOfMonths = config.getNumberOfMonthsInFuture();

        if (Months.monthsBetween(clock.getUTCToday(), targetDate).getMonths() > maximumNumberOfMonths) {
            throw new InvoiceApiException(ErrorCode.INVOICE_TARGET_DATE_TOO_FAR_IN_THE_FUTURE, targetDate.toString());
        }
    }

    private LocalDate adjustTargetDate(final List<Invoice> existingInvoices, final LocalDate targetDate) {
        if (existingInvoices == null) {
            return targetDate;
        }

        LocalDate maxDate = targetDate;

        for (final Invoice invoice : existingInvoices) {
            if (invoice.getTargetDate().isAfter(maxDate)) {
                maxDate = invoice.getTargetDate();
            }
        }
        return maxDate;
    }

    private List<InvoiceItem> processRecurringBillingEvents(final UUID invoiceId, final UUID accountId, final BillingEventSet events,
                                                            final LocalDate targetDate, final Currency currency, final List<InvoiceItem> proposedItems,
                                                            final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDate) throws InvoiceApiException {

        if (events.size() == 0) {
            return proposedItems;
        }

        // Pretty-print the generated invoice items from the junction events
        final StringBuilder logStringBuilder = new StringBuilder("Proposed Invoice items for invoiceId ")
                .append(invoiceId)
                .append(" and accountId ")
                .append(accountId);

        final Iterator<BillingEvent> eventIt = events.iterator();
        BillingEvent nextEvent = eventIt.next();
        while (eventIt.hasNext()) {
            final BillingEvent thisEvent = nextEvent;
            nextEvent = eventIt.next();
            if (!events.getSubscriptionIdsWithAutoInvoiceOff().
                    contains(thisEvent.getSubscription().getId())) { // don't consider events for subscriptions that have auto_invoice_off
                final BillingEvent adjustedNextEvent = (thisEvent.getSubscription().getId() == nextEvent.getSubscription().getId()) ? nextEvent : null;
                final List<InvoiceItem> newProposedItems = processRecurringEvent(invoiceId, accountId, thisEvent, adjustedNextEvent, targetDate, currency, logStringBuilder, events.getRecurringBillingMode(), perSubscriptionFutureNotificationDate);
                proposedItems.addAll(newProposedItems);
            }
        }
        final List<InvoiceItem> newProposedItems = processRecurringEvent(invoiceId, accountId, nextEvent, null, targetDate, currency, logStringBuilder, events.getRecurringBillingMode(), perSubscriptionFutureNotificationDate);
        proposedItems.addAll(newProposedItems);

        log.info(logStringBuilder.toString());

        return proposedItems;
    }

    private List<InvoiceItem> processFixedBillingEvents(final UUID invoiceId, final UUID accountId, final BillingEventSet events, final LocalDate targetDate, final Currency currency, final List<InvoiceItem> proposedItems) {
        final Iterator<BillingEvent> eventIt = events.iterator();
        while (eventIt.hasNext()) {
            final BillingEvent thisEvent = eventIt.next();

            final InvoiceItem fixedPriceInvoiceItem = generateFixedPriceItem(invoiceId, accountId, thisEvent, targetDate, currency);
            if (fixedPriceInvoiceItem != null) {
                proposedItems.add(fixedPriceInvoiceItem);
            }
        }
        return proposedItems;
    }

    // Turn a set of events into a list of invoice items. Note that the dates on the invoice items will be rounded (granularity of a day)
    private List<InvoiceItem> processRecurringEvent(final UUID invoiceId, final UUID accountId, final BillingEvent thisEvent, @Nullable final BillingEvent nextEvent,
                                                    final LocalDate targetDate, final Currency currency,
                                                    final StringBuilder logStringBuilder, final BillingMode billingMode,
                                                    final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDate) throws InvoiceApiException {
        final List<InvoiceItem> items = new ArrayList<InvoiceItem>();

        // Handle recurring items
        final BillingPeriod billingPeriod = thisEvent.getBillingPeriod();
        if (billingPeriod != BillingPeriod.NO_BILLING_PERIOD) {
            final LocalDate startDate = new LocalDate(thisEvent.getEffectiveDate(), thisEvent.getTimeZone());

            if (!startDate.isAfter(targetDate)) {
                final LocalDate endDate = (nextEvent == null) ? null : new LocalDate(nextEvent.getEffectiveDate(), nextEvent.getTimeZone());

                final int billCycleDayLocal = thisEvent.getBillCycleDayLocal();

                final RecurringInvoiceItemDataWithNextBillingCycleDate itemDataWithNextBillingCycleDate;
                try {
                    itemDataWithNextBillingCycleDate = billingModeGenerator.generateInvoiceItemData(startDate, endDate, targetDate, billCycleDayLocal, billingPeriod, billingMode);
                } catch (InvalidDateSequenceException e) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_INVALID_DATE_SEQUENCE, startDate, endDate, targetDate);
                }

                for (final RecurringInvoiceItemData itemDatum : itemDataWithNextBillingCycleDate.getItemData()) {
                    final BigDecimal rate = thisEvent.getRecurringPrice();

                    if (rate != null) {
                        final BigDecimal amount = KillBillMoney.of(itemDatum.getNumberOfCycles().multiply(rate), currency);

                        final RecurringInvoiceItem recurringItem = new RecurringInvoiceItem(invoiceId,
                                                                                            accountId,
                                                                                            thisEvent.getSubscription().getBundleId(),
                                                                                            thisEvent.getSubscription().getId(),
                                                                                            thisEvent.getPlan().getName(),
                                                                                            thisEvent.getPlanPhase().getName(),
                                                                                            itemDatum.getStartDate(), itemDatum.getEndDate(),
                                                                                            amount, rate, currency);
                        items.add(recurringItem);
                    }
                }
                updatePerSubscriptionNextNotificationDate(thisEvent.getSubscription().getId(), itemDataWithNextBillingCycleDate.getNextBillingCycleDate(), items, billingMode, perSubscriptionFutureNotificationDate);

            }
        }

        // For debugging purposes
        logStringBuilder.append("\n")
                        .append(thisEvent);
        for (final InvoiceItem item : items) {
            logStringBuilder.append("\n\t")
                            .append(item);
        }
        return items;
    }

    private void updatePerSubscriptionNextNotificationDate(final UUID subscriptionId, final LocalDate nextBillingCycleDate, final List<InvoiceItem> newProposedItems, final BillingMode billingMode, final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates) {

        LocalDate nextNotificationDate = null;
        switch (billingMode) {
            case IN_ADVANCE:
                for (final InvoiceItem item : newProposedItems) {
                    if ((item.getEndDate() != null) &&
                        (item.getAmount() == null ||
                         item.getAmount().compareTo(BigDecimal.ZERO) >= 0)) {
                        if (nextNotificationDate == null) {
                            nextNotificationDate = item.getEndDate();
                        } else {
                            nextNotificationDate = nextNotificationDate.compareTo(item.getEndDate()) > 0 ? nextNotificationDate : item.getEndDate();
                        }
                    }
                }
                break;
            case IN_ARREAR:
                nextNotificationDate = nextBillingCycleDate;
                break;
            default:
                throw new IllegalStateException("Unrecognized billing mode " + billingMode);
        }
        if (nextNotificationDate != null) {
            SubscriptionFutureNotificationDates subscriptionFutureNotificationDates = perSubscriptionFutureNotificationDates.get(subscriptionId);
            if (subscriptionFutureNotificationDates == null) {
                subscriptionFutureNotificationDates = new SubscriptionFutureNotificationDates(billingMode);
                perSubscriptionFutureNotificationDates.put(subscriptionId, subscriptionFutureNotificationDates);
            }
            subscriptionFutureNotificationDates.updateNextRecurringDateIfRequired(nextNotificationDate);

        }
    }

    private InvoiceItem generateFixedPriceItem(final UUID invoiceId, final UUID accountId, final BillingEvent thisEvent,
                                               final LocalDate targetDate, final Currency currency) {
        final LocalDate roundedStartDate = new LocalDate(thisEvent.getEffectiveDate(), thisEvent.getTimeZone());

        if (roundedStartDate.isAfter(targetDate)) {
            return null;
        } else {
            final BigDecimal fixedPrice = thisEvent.getFixedPrice();

            if (fixedPrice != null) {
                return new FixedPriceInvoiceItem(invoiceId, accountId, thisEvent.getSubscription().getBundleId(),
                                                 thisEvent.getSubscription().getId(),
                                                 thisEvent.getPlan().getName(), thisEvent.getPlanPhase().getName(),
                                                 roundedStartDate, fixedPrice, currency);
            } else {
                return null;
            }
        }
    }
}
