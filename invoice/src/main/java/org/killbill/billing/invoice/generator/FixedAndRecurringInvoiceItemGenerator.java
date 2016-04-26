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
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Duration;
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
import org.killbill.billing.util.AccountDateAndTimeZoneContext;
import org.killbill.billing.util.currency.KillBillMoney;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;

import static org.killbill.billing.invoice.generator.InvoiceDateUtils.calculateNumberOfWholeBillingPeriods;
import static org.killbill.billing.invoice.generator.InvoiceDateUtils.calculateProRationAfterLastBillingCycleDate;
import static org.killbill.billing.invoice.generator.InvoiceDateUtils.calculateProRationBeforeFirstBillingPeriod;

public class FixedAndRecurringInvoiceItemGenerator extends InvoiceItemGenerator {

    private static final Logger log = LoggerFactory.getLogger(FixedAndRecurringInvoiceItemGenerator.class);

    @Inject
    public FixedAndRecurringInvoiceItemGenerator() {
    }

    public List<InvoiceItem> generateItems(final ImmutableAccountData account, final UUID invoiceId, final BillingEventSet eventSet,
                                           @Nullable final List<Invoice> existingInvoices, final LocalDate targetDate,
                                           final Currency targetCurrency, Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDate,
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
        processRecurringBillingEvents(invoiceId, account.getId(), eventSet, targetDate, targetCurrency, proposedItems, perSubscriptionFutureNotificationDate, existingInvoices);
        processFixedBillingEvents(invoiceId, account.getId(), eventSet, targetDate, targetCurrency, proposedItems);

        accountItemTree.mergeWithProposedItems(proposedItems);
        return accountItemTree.getResultingItemList();
    }

    private void processRecurringBillingEvents(final UUID invoiceId, final UUID accountId, final BillingEventSet events,
                                               final LocalDate targetDate, final Currency currency, final List<InvoiceItem> proposedItems,
                                               final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDate,
                                               @Nullable final List<Invoice> existingInvoices) throws InvoiceApiException {

        if (events.size() == 0) {
            return;
        }

        // Pretty-print the generated invoice items from the junction events
        final StringBuilder logStringBuilder = new StringBuilder("Proposed Invoice items for invoiceId='")
                .append(invoiceId)
                .append("', accountId='")
                .append(accountId)
                .append("'");

        final Iterator<BillingEvent> eventIt = events.iterator();
        BillingEvent nextEvent = eventIt.next();
        while (eventIt.hasNext()) {
            final BillingEvent thisEvent = nextEvent;
            nextEvent = eventIt.next();
            if (!events.getSubscriptionIdsWithAutoInvoiceOff().
                    contains(thisEvent.getSubscription().getId())) { // don't consider events for subscriptions that have auto_invoice_off
                final BillingEvent adjustedNextEvent = (thisEvent.getSubscription().getId() == nextEvent.getSubscription().getId()) ? nextEvent : null;
                final List<InvoiceItem> newProposedItems = processRecurringEvent(invoiceId, accountId, thisEvent, adjustedNextEvent, targetDate, currency, logStringBuilder, events.getRecurringBillingMode(), perSubscriptionFutureNotificationDate, events.getAccountDateAndTimeZoneContext());
                proposedItems.addAll(newProposedItems);
            }
        }
        final List<InvoiceItem> newProposedItems = processRecurringEvent(invoiceId, accountId, nextEvent, null, targetDate, currency, logStringBuilder, events.getRecurringBillingMode(), perSubscriptionFutureNotificationDate, events.getAccountDateAndTimeZoneContext());
        proposedItems.addAll(newProposedItems);

        log.info(logStringBuilder.toString());

        return;
    }

    @VisibleForTesting
    void processFixedBillingEvents(final UUID invoiceId, final UUID accountId, final BillingEventSet events, final LocalDate targetDate, final Currency currency, final List<InvoiceItem> proposedItems) {

        final AccountDateAndTimeZoneContext dateAndTimeZoneContext = events.getAccountDateAndTimeZoneContext();

        InvoiceItem prevItem = null;

        final Iterator<BillingEvent> eventIt = events.iterator();
        while (eventIt.hasNext()) {
            final BillingEvent thisEvent = eventIt.next();

            final InvoiceItem currentFixedPriceItem = generateFixedPriceItem(invoiceId, accountId, thisEvent, targetDate, currency, dateAndTimeZoneContext);
            if (!isSameDayAndSameSubscription(prevItem, thisEvent, dateAndTimeZoneContext) && prevItem != null) {
                proposedItems.add(prevItem);
            }
            prevItem = currentFixedPriceItem;
        }
        // The last one if not null can always be inserted as there is nothing after to cancel it off.
        if (prevItem != null) {
            proposedItems.add(prevItem);
        }
    }

    @VisibleForTesting
    boolean isSameDayAndSameSubscription(final InvoiceItem prevComputedFixedItem, final BillingEvent currentBillingEvent, final AccountDateAndTimeZoneContext dateAndTimeZoneContext) {
        final LocalDate curLocalEffectiveDate = dateAndTimeZoneContext.computeLocalDateFromFixedAccountOffset(currentBillingEvent.getEffectiveDate());
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
                                                    final StringBuilder logStringBuilder, final BillingMode billingMode,
                                                    final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDate,
                                                    final AccountDateAndTimeZoneContext dateAndTimeZoneContext) throws InvoiceApiException {
        final List<InvoiceItem> items = new ArrayList<InvoiceItem>();

        // For FIXEDTERM phases we need to stop when the specified duration has been reached
        final LocalDate maxEndDate = thisEvent.getPlanPhase().getPhaseType() == PhaseType.FIXEDTERM ?
                                     computeMaxEndDateForFixedTermPlanPhase(thisEvent, dateAndTimeZoneContext) :
                                     null;

        // Handle recurring items
        final BillingPeriod billingPeriod = thisEvent.getBillingPeriod();
        if (billingPeriod != BillingPeriod.NO_BILLING_PERIOD) {
            final LocalDate startDate = dateAndTimeZoneContext.computeLocalDateFromFixedAccountOffset(thisEvent.getEffectiveDate());

            if (!startDate.isAfter(targetDate)) {
                final LocalDate endDate = (nextEvent == null) ? null : dateAndTimeZoneContext.computeLocalDateFromFixedAccountOffset(nextEvent.getEffectiveDate());

                final int billCycleDayLocal = thisEvent.getBillCycleDayLocal();

                final RecurringInvoiceItemDataWithNextBillingCycleDate itemDataWithNextBillingCycleDate;
                try {
                    itemDataWithNextBillingCycleDate = generateInvoiceItemData(startDate, endDate, targetDate, billCycleDayLocal, billingPeriod, billingMode);
                } catch (InvalidDateSequenceException e) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_INVALID_DATE_SEQUENCE, startDate, endDate, targetDate);
                }

                for (final RecurringInvoiceItemData itemDatum : itemDataWithNextBillingCycleDate.getItemData()) {

                    // Stop if there a maxEndDate and we have reached it
                    if (maxEndDate != null && maxEndDate.compareTo(itemDatum.getEndDate()) < 0) {
                        break;
                    }
                    final BigDecimal rate = thisEvent.getRecurringPrice();
                    if (rate != null) {
                        final BigDecimal amount = itemDatum.getNumberOfCycles().multiply(rate);
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

    //
    // Go through invoices in reverse order and for each invoice extract a possible item for that subscription and the phaseName associated with this billing event
    // Computes the startDate for that first one seen in that uninterrupted sequence and then add the FIXEDTERM duration to compute the max endDate
    //
    private LocalDate computeMaxEndDateForFixedTermPlanPhase(final BillingEvent thisEvent, final AccountDateAndTimeZoneContext dateAndTimeZoneContext) {
        final LocalDate eventEffectiveDate = dateAndTimeZoneContext.computeLocalDateFromFixedAccountOffset(thisEvent.getEffectiveDate());
        return addDurationToLocalDate(eventEffectiveDate, thisEvent.getPlanPhase().getDuration());
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
            if (results.size() > 0) {
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
                                               final LocalDate targetDate, final Currency currency, final AccountDateAndTimeZoneContext dateAndTimeZoneContext) {
        final LocalDate roundedStartDate = dateAndTimeZoneContext.computeLocalDateFromFixedAccountOffset(thisEvent.getEffectiveDate());
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

    // That code should belong to Duration/DefaultDuration but requires a change api (not possible for 0.16.3, but will be moreved in 0.17.0)
    private LocalDate addDurationToLocalDate(@Nullable final LocalDate inputDate, final Duration duration) {

        if (inputDate == null) {
            return inputDate;
        }

        switch (duration.getUnit()) {
            case DAYS:
                return inputDate.plusDays(duration.getNumber());
            case MONTHS:
                return inputDate.plusMonths(duration.getNumber());
            case YEARS:
                return inputDate.plusYears(duration.getNumber());
            case UNLIMITED:
                return inputDate.plusYears(100);
            default:
                throw new IllegalStateException("Unknwon duration " + duration.getUnit());
        }
    }

}
