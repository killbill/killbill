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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.SubscriptionFutureNotificationDates;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.InvalidDateSequenceException;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.invoice.model.RecurringInvoiceItemData;
import org.killbill.billing.invoice.model.RecurringInvoiceItemDataWithNextBillingCycleDate;
import org.killbill.billing.invoice.tree.AccountItemTree;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.util.currency.KillBillMoney;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import static org.killbill.billing.invoice.generator.InvoiceDateUtils.calculateNumberOfWholeBillingPeriods;
import static org.killbill.billing.invoice.generator.InvoiceDateUtils.calculateProRationAfterLastBillingCycleDate;
import static org.killbill.billing.invoice.generator.InvoiceDateUtils.calculateProRationBeforeFirstBillingPeriod;

public class FixedAndRecurringInvoiceItemGenerator extends InvoiceItemGenerator {

    private static final Logger log = LoggerFactory.getLogger(FixedAndRecurringInvoiceItemGenerator.class);

    public List<InvoiceItem> generateItems(final ImmutableAccountData account, final UUID invoiceId, final BillingEventSet eventSet,
                                           @Nullable final List<Invoice> existingInvoices, final LocalDate targetDate,
                                           final Currency targetCurrency, final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDate,
                                           final InternalCallContext internalCallContext) throws InvoiceApiException {
        final AccountItemTree accountItemTree = new AccountItemTree(account.getId(), invoiceId);
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
        processRecurringBillingEvents(invoiceId, account.getId(), eventSet, targetDate, targetCurrency, proposedItems, perSubscriptionFutureNotificationDate, existingInvoices, internalCallContext);
        processFixedBillingEvents(invoiceId, account.getId(), eventSet, targetDate, targetCurrency, proposedItems, internalCallContext);
        accountItemTree.mergeWithProposedItems(proposedItems);
        return accountItemTree.getResultingItemList();
    }

    private void processRecurringBillingEvents(final UUID invoiceId, final UUID accountId, final BillingEventSet events,
                                               final LocalDate targetDate, final Currency currency, final List<InvoiceItem> proposedItems,
                                               final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDate,
                                               @Nullable final List<Invoice> existingInvoices,
                                               final InternalCallContext internalCallContext) throws InvoiceApiException {
        if (events.isEmpty()) {
            return;
        }

        // Pretty-print the generated invoice items from the junction events
        final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger = new InvoiceItemGeneratorLogger(invoiceId, accountId, "recurring", log);

        final Iterator<BillingEvent> eventIt = events.iterator();
        BillingEvent nextEvent = eventIt.next();
        while (eventIt.hasNext()) {
            final BillingEvent thisEvent = nextEvent;
            nextEvent = eventIt.next();
            if (!events.getSubscriptionIdsWithAutoInvoiceOff().
                    contains(thisEvent.getSubscription().getId())) { // don't consider events for subscriptions that have auto_invoice_off
                final BillingEvent adjustedNextEvent = (thisEvent.getSubscription().getId() == nextEvent.getSubscription().getId()) ? nextEvent : null;
                final List<InvoiceItem> newProposedItems = processRecurringEvent(invoiceId, accountId, thisEvent, adjustedNextEvent, targetDate, currency, invoiceItemGeneratorLogger, events.getRecurringBillingMode(), perSubscriptionFutureNotificationDate, internalCallContext);
                proposedItems.addAll(newProposedItems);
            }
        }
        final List<InvoiceItem> newProposedItems = processRecurringEvent(invoiceId, accountId, nextEvent, null, targetDate, currency, invoiceItemGeneratorLogger, events.getRecurringBillingMode(), perSubscriptionFutureNotificationDate, internalCallContext);

        proposedItems.addAll(newProposedItems);

        invoiceItemGeneratorLogger.logItems();
    }

    @VisibleForTesting
    void processFixedBillingEvents(final UUID invoiceId, final UUID accountId, final BillingEventSet events, final LocalDate targetDate,
                                   final Currency currency, final List<InvoiceItem> proposedItems, final InternalCallContext internalCallContext) throws InvoiceApiException {
        if (events.isEmpty()) {
            return;
        }

        InvoiceItem prevItem = null;

        // Pretty-print the generated invoice items from the junction events
        final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger = new InvoiceItemGeneratorLogger(invoiceId, accountId, "fixed", log);

        final Iterator<BillingEvent> eventIt = events.iterator();
        while (eventIt.hasNext()) {
            final BillingEvent thisEvent = eventIt.next();

            final InvoiceItem currentFixedPriceItem = generateFixedPriceItem(invoiceId, accountId, thisEvent, targetDate, currency, invoiceItemGeneratorLogger, internalCallContext);
            if (!isSameDayAndSameSubscription(prevItem, thisEvent, internalCallContext) && prevItem != null) {
                proposedItems.add(prevItem);
            }
            prevItem = currentFixedPriceItem;
        }
        // The last one if not null can always be inserted as there is nothing after to cancel it off.
        if (prevItem != null) {
            proposedItems.add(prevItem);
        }

        invoiceItemGeneratorLogger.logItems();
    }

    @VisibleForTesting
    boolean isSameDayAndSameSubscription(final InvoiceItem prevComputedFixedItem, final BillingEvent currentBillingEvent, final InternalCallContext internalCallContext) {
        final LocalDate curLocalEffectiveDate = internalCallContext.toLocalDate(currentBillingEvent.getEffectiveDate());
        if (prevComputedFixedItem != null && /* If we have computed a previous item */
            prevComputedFixedItem.getStartDate().compareTo(curLocalEffectiveDate) == 0 && /* The current billing event happens at the same date */
            prevComputedFixedItem.getSubscriptionId().compareTo(currentBillingEvent.getSubscription().getId()) == 0 /* The current billing event happens for the same subscription */) {
            return true;
        } else {
            return false;
        }
    }

    // Turn a set of events into a list of invoice items. Note that the dates on the invoice items will be rounded (granularity of a day)
    private List<InvoiceItem> processRecurringEvent(final UUID invoiceId, final UUID accountId, final BillingEvent thisEvent, @Nullable final BillingEvent nextEvent,
                                                    final LocalDate targetDate, final Currency currency,
                                                    final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger, final BillingMode billingMode,
                                                    final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDate,
                                                    final InternalCallContext internalCallContext) throws InvoiceApiException {

        try {
            final List<InvoiceItem> items = new ArrayList<InvoiceItem>();

            // For FIXEDTERM phases we need to stop when the specified duration has been reached
            final LocalDate maxEndDate = thisEvent.getPlanPhase().getPhaseType() == PhaseType.FIXEDTERM ?
                                         thisEvent.getPlanPhase().getDuration().addToLocalDate(internalCallContext.toLocalDate(thisEvent.getEffectiveDate())) :
                                         null;

            // Handle recurring items
            final BillingPeriod billingPeriod = thisEvent.getBillingPeriod();
            if (billingPeriod != BillingPeriod.NO_BILLING_PERIOD) {
                final LocalDate startDate = internalCallContext.toLocalDate(thisEvent.getEffectiveDate());

                if (!startDate.isAfter(targetDate)) {
                    final LocalDate endDate = (nextEvent == null) ? null : internalCallContext.toLocalDate(nextEvent.getEffectiveDate());

                    final int billCycleDayLocal = thisEvent.getBillCycleDayLocal();

                    final RecurringInvoiceItemDataWithNextBillingCycleDate itemDataWithNextBillingCycleDate;
                    try {
                        itemDataWithNextBillingCycleDate = generateInvoiceItemData(startDate, endDate, targetDate, billCycleDayLocal, billingPeriod, billingMode);
                    } catch (final InvalidDateSequenceException e) {
                        throw new InvoiceApiException(ErrorCode.INVOICE_INVALID_DATE_SEQUENCE, startDate, endDate, targetDate);
                    }
                    for (final RecurringInvoiceItemData itemDatum : itemDataWithNextBillingCycleDate.getItemData()) {

                        // Stop if there a maxEndDate and we have reached it
                        if (maxEndDate != null && maxEndDate.compareTo(itemDatum.getEndDate()) < 0) {
                            break;
                        }
                        final BigDecimal rate = thisEvent.getRecurringPrice(internalCallContext.toUTCDateTime(itemDatum.getStartDate()));
                        if (rate != null) {
                            final BigDecimal amount = KillBillMoney.of(itemDatum.getNumberOfCycles().multiply(rate), currency);
                            final RecurringInvoiceItem recurringItem = new RecurringInvoiceItem(invoiceId,
                                                                                                accountId,
                                                                                                thisEvent.getSubscription().getBundleId(),
                                                                                                thisEvent.getSubscription().getId(),
                                                                                                thisEvent.getPlan().getName(),
                                                                                                thisEvent.getPlanPhase().getName(),
                                                                                                itemDatum.getStartDate(),
                                                                                                itemDatum.getEndDate(),
                                                                                                amount, rate, currency);
                            items.add(recurringItem);
                        }
                    }
                    updatePerSubscriptionNextNotificationDate(thisEvent.getSubscription().getId(), itemDataWithNextBillingCycleDate.getNextBillingCycleDate(), items, billingMode,
                                                              perSubscriptionFutureNotificationDate);
                }
            }

            // For debugging purposes
            invoiceItemGeneratorLogger.append(thisEvent, items);

            return items;
        } catch (final CatalogApiException e) {
            throw new InvoiceApiException(e);
        }
    }

    private void updatePerSubscriptionNextNotificationDate(final UUID subscriptionId, final LocalDate nextBillingCycleDate, final List<InvoiceItem> newProposedItems, final BillingMode billingMode,
                                                           final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates) {

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

    public RecurringInvoiceItemDataWithNextBillingCycleDate generateInvoiceItemData(final LocalDate startDate, @Nullable final LocalDate endDate,
                                                                                    final LocalDate targetDate,
                                                                                    final int billingCycleDayLocal,
                                                                                    final BillingPeriod billingPeriod,
                                                                                    final BillingMode billingMode) throws InvalidDateSequenceException {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new InvalidDateSequenceException();
        }
        if (targetDate.isBefore(startDate)) {
            throw new InvalidDateSequenceException();
        }

        final List<RecurringInvoiceItemData> results = new ArrayList<RecurringInvoiceItemData>();

        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(startDate, endDate, targetDate, billingCycleDayLocal, billingPeriod, billingMode);

        // We are not billing for less than a day
        if (!billingIntervalDetail.hasSomethingToBill()) {
            return new RecurringInvoiceItemDataWithNextBillingCycleDate(results, billingIntervalDetail);
        }
        //
        // If there is an endDate and that endDate is before our first coming firstBillingCycleDate, all we have to do
        // is to charge for that period
        //
        if (endDate != null && !endDate.isAfter(billingIntervalDetail.getFirstBillingCycleDate())) {
            final BigDecimal leadingProRationPeriods = calculateProRationBeforeFirstBillingPeriod(startDate, endDate, billingPeriod);
            final RecurringInvoiceItemData itemData = new RecurringInvoiceItemData(startDate, endDate, leadingProRationPeriods);
            results.add(itemData);
            return new RecurringInvoiceItemDataWithNextBillingCycleDate(results, billingIntervalDetail);
        }

        //
        // Leading proration if
        // i) The first firstBillingCycleDate is strictly after our start date AND
        // ii) The endDate is is not null and is strictly after our firstBillingCycleDate (previous check)
        //
        if (billingIntervalDetail.getFirstBillingCycleDate().isAfter(startDate)) {
            final BigDecimal leadingProRationPeriods = calculateProRationBeforeFirstBillingPeriod(startDate, billingIntervalDetail.getFirstBillingCycleDate(), billingPeriod);
            if (leadingProRationPeriods != null && leadingProRationPeriods.compareTo(BigDecimal.ZERO) > 0) {
                // Not common - add info in the logs for debugging purposes
                final RecurringInvoiceItemData itemData = new RecurringInvoiceItemData(startDate, billingIntervalDetail.getFirstBillingCycleDate(), leadingProRationPeriods);
                log.info("Adding pro-ration: {}", itemData);
                results.add(itemData);
            }
        }

        //
        // Calculate the effectiveEndDate from the firstBillingCycleDate:
        // - If endDate != null and targetDate is after endDate => this is the endDate and will lead to a trailing pro-ration
        // - If not, this is the last billingCycleDate calculation right after the targetDate
        //
        final LocalDate effectiveEndDate = billingIntervalDetail.getEffectiveEndDate();

        //
        // Based on what we calculated previously, code recompute one more time the numberOfWholeBillingPeriods
        //
        final LocalDate lastBillingCycleDate = billingIntervalDetail.getLastBillingCycleDate();
        final int numberOfWholeBillingPeriods = calculateNumberOfWholeBillingPeriods(billingIntervalDetail.getFirstBillingCycleDate(), lastBillingCycleDate, billingPeriod);

        for (int i = 0; i < numberOfWholeBillingPeriods; i++) {
            final LocalDate servicePeriodStartDate;
            if (!results.isEmpty()) {
                // Make sure the periods align, especially with the pro-ration calculations above
                servicePeriodStartDate = results.get(results.size() - 1).getEndDate();
            } else if (i == 0) {
                // Use the specified start date
                servicePeriodStartDate = startDate;
            } else {
                throw new IllegalStateException("We should at least have one invoice item!");
            }

            // Make sure to align the end date with the BCD
            final LocalDate servicePeriodEndDate = billingIntervalDetail.getFutureBillingDateFor(i + 1);
            results.add(new RecurringInvoiceItemData(servicePeriodStartDate, servicePeriodEndDate, BigDecimal.ONE));
        }

        //
        // Now we check if indeed we need a trailing proration and add that incomplete item
        //
        if (effectiveEndDate.isAfter(lastBillingCycleDate)) {
            final BigDecimal trailingProRationPeriods = calculateProRationAfterLastBillingCycleDate(effectiveEndDate, lastBillingCycleDate, billingPeriod);
            if (trailingProRationPeriods.compareTo(BigDecimal.ZERO) > 0) {
                // Not common - add info in the logs for debugging purposes
                final RecurringInvoiceItemData itemData = new RecurringInvoiceItemData(lastBillingCycleDate, effectiveEndDate, trailingProRationPeriods);
                log.info("Adding trailing pro-ration: {}", itemData);
                results.add(itemData);
            }
        }
        return new RecurringInvoiceItemDataWithNextBillingCycleDate(results, billingIntervalDetail);
    }

    private InvoiceItem generateFixedPriceItem(final UUID invoiceId, final UUID accountId, final BillingEvent thisEvent,
                                               final LocalDate targetDate, final Currency currency,
                                               final InvoiceItemGeneratorLogger invoiceItemGeneratorLogger, final InternalCallContext internalCallContext) throws InvoiceApiException {
        final LocalDate roundedStartDate = internalCallContext.toLocalDate(thisEvent.getEffectiveDate());
        if (roundedStartDate.isAfter(targetDate)) {
            return null;
        } else {
            final BigDecimal fixedPrice = thisEvent.getFixedPrice();

            if (fixedPrice != null) {
                final FixedPriceInvoiceItem fixedPriceInvoiceItem = new FixedPriceInvoiceItem(invoiceId, accountId, thisEvent.getSubscription().getBundleId(),
                                                                                              thisEvent.getSubscription().getId(),
                                                                                              thisEvent.getPlan().getName(), thisEvent.getPlanPhase().getName(),
                                                                                              roundedStartDate, fixedPrice, currency);

                // For debugging purposes
                invoiceItemGeneratorLogger.append(thisEvent, fixedPriceInvoiceItem);

                return fixedPriceInvoiceItem;
            } else {
                return null;
            }
        }
    }
}
