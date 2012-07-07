/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.invoice.generator;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Months;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.config.InvoiceConfig;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.billing.BillingModeType;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.model.BillingMode;
import com.ning.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.model.InAdvanceBillingMode;
import com.ning.billing.invoice.model.InvalidDateSequenceException;
import com.ning.billing.invoice.model.InvoicingConfiguration;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.invoice.model.RecurringInvoiceItemData;
import com.ning.billing.invoice.model.RepairAdjInvoiceItem;
import com.ning.billing.junction.api.BillingEventSet;
import com.ning.billing.util.clock.Clock;

public class DefaultInvoiceGenerator implements InvoiceGenerator {
    private static final Logger log = LoggerFactory.getLogger(DefaultInvoiceGenerator.class);
    private static final int ROUNDING_MODE = InvoicingConfiguration.getRoundingMode();
    private static final int NUMBER_OF_DECIMALS = InvoicingConfiguration.getNumberOfDecimals();

    private final Clock clock;
    private final InvoiceConfig config;

    @Inject
    public DefaultInvoiceGenerator(final Clock clock, final InvoiceConfig config) {
        this.clock = clock;
        this.config = config;
    }

    /*
     * adjusts target date to the maximum invoice target date, if future invoices exist
     */
    @Override
    public Invoice generateInvoice(final UUID accountId, @Nullable final BillingEventSet events,
            @Nullable final List<Invoice> existingInvoices,
            DateTime targetDate,
            final Currency targetCurrency) throws InvoiceApiException {
        if ((events == null) || (events.size() == 0) || events.isAccountAutoInvoiceOff()) {
            return null;
        }

        validateTargetDate(targetDate);
        //TODO MDW can use subscription Id - not bundle
        //TODO MDW worry about null sub id

        final List<InvoiceItem> existingItems = new ArrayList<InvoiceItem>();
        if (existingInvoices != null) {
            for (final Invoice invoice : existingInvoices) {
                for (final InvoiceItem item : invoice.getInvoiceItems()) {
                    if (item.getSubscriptionId() == null || // Always include migration invoices, credits etc.
                            !events.getSubscriptionIdsWithAutoInvoiceOff()
                            .contains(item.getSubscriptionId())) { //don't add items with auto_invoice_off tag
                        existingItems.add(item);
                    }
                }
            }

            Collections.sort(existingItems);
        }

        targetDate = adjustTargetDate(existingInvoices, targetDate);

        final Invoice invoice = new DefaultInvoice(accountId, clock.getUTCNow(), targetDate, targetCurrency);
        final UUID invoiceId = invoice.getId();
        final List<InvoiceItem> proposedItems = generateInvoiceItems(invoiceId, accountId, events, targetDate, targetCurrency);

        removeCancellingInvoiceItems(existingItems);
        removeDuplicatedInvoiceItems(proposedItems, existingItems);

        addRepairedItems(existingItems, proposedItems);
        generateCBAForExistingInvoices(accountId, existingInvoices, proposedItems, targetCurrency);
        consumeExistingCredit(invoiceId, accountId, existingItems, proposedItems, targetCurrency);

        if (proposedItems == null || proposedItems.size() == 0) {
            return null;
        } else {
            invoice.addInvoiceItems(proposedItems);

            return invoice;
        }
    }

    void generateCBAForExistingInvoices(final UUID accountId, final List<Invoice> existingInvoices, final List<InvoiceItem> proposedItems, final Currency currency) {
        // determine most accurate invoice balances up to this point
        final Map<UUID, BigDecimal> amountOwedByInvoice = new HashMap<UUID, BigDecimal>();

        if (existingInvoices != null) {
            for (final Invoice invoice : existingInvoices) {
                amountOwedByInvoice.put(invoice.getId(), invoice.getBalance());
            }
        }

        for (final InvoiceItem item : proposedItems) {
            final UUID invoiceId = item.getInvoiceId();
            if (amountOwedByInvoice.containsKey(invoiceId)) {
                amountOwedByInvoice.put(invoiceId, amountOwedByInvoice.get(invoiceId).add(item.getAmount()));
            } else {
                amountOwedByInvoice.put(invoiceId, item.getAmount());
            }
        }

        for (final UUID invoiceId : amountOwedByInvoice.keySet()) {
            final BigDecimal invoiceBalance = amountOwedByInvoice.get(invoiceId);
            if (invoiceBalance.compareTo(BigDecimal.ZERO) < 0) {
                proposedItems.add(new CreditBalanceAdjInvoiceItem(invoiceId, accountId, clock.getUTCNow(), invoiceBalance.negate(), currency));
            }
        }
    }

    void addRepairedItems(final List<InvoiceItem> existingItems, final List<InvoiceItem> proposedItems) {
        for (final InvoiceItem existingItem : existingItems) {
            if (existingItem.getInvoiceItemType() == InvoiceItemType.RECURRING ||
                    existingItem.getInvoiceItemType() == InvoiceItemType.FIXED) {
                final BigDecimal amountNegated = existingItem.getAmount() == null ? null : existingItem.getAmount().negate();
                RepairAdjInvoiceItem repairItem  = new RepairAdjInvoiceItem(existingItem.getInvoiceId(), existingItem.getAccountId(), existingItem.getStartDate(),existingItem.getEndDate(), amountNegated, existingItem.getCurrency(), existingItem.getId());
                proposedItems.add(repairItem);
            }
        }
    }

    void consumeExistingCredit(final UUID invoiceId, final UUID accountId, final List<InvoiceItem> existingItems,
            final List<InvoiceItem> proposedItems, final Currency targetCurrency) {
        BigDecimal totalUnusedCreditAmount = BigDecimal.ZERO;
        BigDecimal totalAmountOwed = BigDecimal.ZERO;

        for (final InvoiceItem item : existingItems) {
            if (item.getInvoiceItemType() == InvoiceItemType.CBA_ADJ) {
                totalUnusedCreditAmount = totalUnusedCreditAmount.add(item.getAmount());
            }
        }

        for (final InvoiceItem item : proposedItems) {
            if (item.getInvoiceItemType() == InvoiceItemType.CBA_ADJ) {
                totalUnusedCreditAmount = totalUnusedCreditAmount.add(item.getAmount());
            } else if (item.getInvoiceId().equals(invoiceId)) {
                totalAmountOwed = totalAmountOwed.add(item.getAmount());
            }
        }


        BigDecimal creditAmount = BigDecimal.ZERO;
        if (totalUnusedCreditAmount.compareTo(BigDecimal.ZERO) > 0) {
            if (totalAmountOwed.abs().compareTo(totalUnusedCreditAmount.abs()) > 0) {
                creditAmount = totalUnusedCreditAmount.negate();
            } else {
                creditAmount = totalAmountOwed.negate();
            }
        }

        if (creditAmount.compareTo(BigDecimal.ZERO) < 0) {
            proposedItems.add(new CreditBalanceAdjInvoiceItem(invoiceId, accountId, clock.getUTCNow(), creditAmount, targetCurrency));
        }
    }

    void validateTargetDate(final DateTime targetDate) throws InvoiceApiException {
        final int maximumNumberOfMonths = config.getNumberOfMonthsInFuture();

        if (Months.monthsBetween(clock.getUTCNow(), targetDate).getMonths() > maximumNumberOfMonths) {
            throw new InvoiceApiException(ErrorCode.INVOICE_TARGET_DATE_TOO_FAR_IN_THE_FUTURE, targetDate.toString());
        }
    }

    DateTime adjustTargetDate(final List<Invoice> existingInvoices, final DateTime targetDate) {
        if (existingInvoices == null) {
            return targetDate;
        }

        DateTime maxDate = targetDate;

        for (final Invoice invoice : existingInvoices) {
            if (invoice.getTargetDate().isAfter(maxDate)) {
                maxDate = invoice.getTargetDate();
            }
        }

        return maxDate;
    }

    /*
     * removes all matching items from both submitted collections
     */
    void removeDuplicatedInvoiceItems(final List<InvoiceItem> proposedItems,
            final List<InvoiceItem> existingInvoiceItems) {
        final Iterator<InvoiceItem> proposedItemIterator = proposedItems.iterator();
        while (proposedItemIterator.hasNext()) {
            final InvoiceItem proposedItem = proposedItemIterator.next();

            final Iterator<InvoiceItem> existingItemIterator = existingInvoiceItems.iterator();
            while (existingItemIterator.hasNext()) {
                final InvoiceItem existingItem = existingItemIterator.next();
                if (existingItem.equals(proposedItem)) {
                    existingItemIterator.remove();
                    proposedItemIterator.remove();
                }
            }
        }
    }

    void removeCancellingInvoiceItems(final List<InvoiceItem> items) {
        final List<UUID> itemsToRemove = new ArrayList<UUID>();

        for (final InvoiceItem item1 : items) {
            if (item1.getInvoiceItemType() == InvoiceItemType.REPAIR_ADJ) {
                final RepairAdjInvoiceItem repairItem = (RepairAdjInvoiceItem) item1;
                itemsToRemove.add(repairItem.getId());
                itemsToRemove.add(repairItem.getLinkedItemId());
            }
        }

        final Iterator<InvoiceItem> iterator = items.iterator();
        while (iterator.hasNext()) {
            final InvoiceItem item = iterator.next();
            if (itemsToRemove.contains(item.getId())) {
                iterator.remove();
            }
        }
    }

    List<InvoiceItem> generateInvoiceItems(final UUID invoiceId, final UUID accountId, final BillingEventSet events,
            final DateTime targetDate, final Currency currency) throws InvoiceApiException {
        final List<InvoiceItem> items = new ArrayList<InvoiceItem>();

        if (events.size() == 0) {
            return items;
        }

        final Iterator<BillingEvent> eventIt = events.iterator();

        BillingEvent nextEvent = eventIt.next();
        while (eventIt.hasNext()) {
            final BillingEvent thisEvent = nextEvent;
            nextEvent = eventIt.next();
            if (!events.getSubscriptionIdsWithAutoInvoiceOff().
                    contains(thisEvent.getSubscription().getId())) { // don't consider events for subscriptions that have auto_invoice_off
                final BillingEvent adjustedNextEvent = (thisEvent.getSubscription().getId() == nextEvent.getSubscription().getId()) ? nextEvent : null;
                items.addAll(processEvents(invoiceId, accountId, thisEvent, adjustedNextEvent, targetDate, currency));
            }
        }
        items.addAll(processEvents(invoiceId, accountId, nextEvent, null, targetDate, currency));

        // The above should reproduce the semantics of the code below using iterator instead of list.
        //
        //        for (int i = 0; i < events.size(); i++) {
        //            BillingEvent thisEvent = events.get(i);
        //            BillingEvent nextEvent = events.isLast(thisEvent) ? null : events.get(i + 1);
        //            if (nextEvent != null) {
        //                nextEvent = (thisEvent.getSubscription().getId() == nextEvent.getSubscription().getId()) ? nextEvent : null;
        //            }
        //
        //            items.addAll(processEvents(invoiceId, accountId, thisEvent, nextEvent, targetDate, currency));
        //        }

        return items;
    }


    private DateTime roundDateTimeToDate(final DateTime input, final DateTimeZone timeZone) {
        if (input == null) {
            return null;
        }
        final DateTime tzAdjustedStartDate = input.toDateTime(timeZone);
        final DateTime roundedStartDate = new DateTime(tzAdjustedStartDate.getYear(), tzAdjustedStartDate.getMonthOfYear(), tzAdjustedStartDate.getDayOfMonth(), 0, 0, timeZone);
        return roundedStartDate;
    }

    List<InvoiceItem> processEvents(final UUID invoiceId, final UUID accountId, final BillingEvent thisEvent, @Nullable final BillingEvent nextEvent,
            final DateTime targetDate, final Currency currency) throws InvoiceApiException {
        final List<InvoiceItem> items = new ArrayList<InvoiceItem>();
        final InvoiceItem fixedPriceInvoiceItem = generateFixedPriceItem(invoiceId, accountId, thisEvent, targetDate, currency);
        if (fixedPriceInvoiceItem != null) {
            items.add(fixedPriceInvoiceItem);
        }

        final BillingPeriod billingPeriod = thisEvent.getBillingPeriod();
        if (billingPeriod != BillingPeriod.NO_BILLING_PERIOD) {
            final BillingMode billingMode = instantiateBillingMode(thisEvent.getBillingMode());
            // Invoice granularity is day; (if not some comparison might fail)
            final DateTime startDate = thisEvent.getEffectiveDate();
            final DateTime roundedStartDate = roundDateTimeToDate(startDate, thisEvent.getTimeZone());
            final DateTime roundedTargetDate = roundDateTimeToDate(targetDate, thisEvent.getTimeZone());

            log.info(String.format("start = %s, rounded = %s, target = %s, in = %s", startDate, roundedStartDate, targetDate, (!roundedStartDate.isAfter(targetDate)) ? "in" : "out"));
            if (!roundedStartDate.isAfter(targetDate)) {
                final DateTime endDate = (nextEvent == null) ? null : nextEvent.getEffectiveDate();

                final DateTime roundedEndDate = roundDateTimeToDate(endDate, thisEvent.getTimeZone());
                final int billCycleDay = thisEvent.getBillCycleDay();

                final List<RecurringInvoiceItemData> itemData;
                try {
                    itemData = billingMode.calculateInvoiceItemData(roundedStartDate, roundedEndDate, roundedTargetDate, billCycleDay, billingPeriod);
                } catch (InvalidDateSequenceException e) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_INVALID_DATE_SEQUENCE, startDate, endDate, targetDate);
                }

                for (final RecurringInvoiceItemData itemDatum : itemData) {
                    final BigDecimal rate = thisEvent.getRecurringPrice();

                    if (rate != null) {
                        final BigDecimal amount = itemDatum.getNumberOfCycles().multiply(rate).setScale(NUMBER_OF_DECIMALS, ROUNDING_MODE);

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
            }
        }

        return items;
    }

    private BillingMode instantiateBillingMode(final BillingModeType billingMode) {
        switch (billingMode) {
        case IN_ADVANCE:
            return new InAdvanceBillingMode();
        default:
            throw new UnsupportedOperationException();
        }
    }

    InvoiceItem generateFixedPriceItem(final UUID invoiceId, final UUID accountId, final BillingEvent thisEvent,
            final DateTime targetDate, final Currency currency) {
        if (thisEvent.getEffectiveDate().isAfter(targetDate)) {
            return null;
        } else {
            final BigDecimal fixedPrice = thisEvent.getFixedPrice();

            if (fixedPrice != null) {
                final Duration duration = thisEvent.getPlanPhase().getDuration();
                final DateTime endDate = duration.addToDateTime(thisEvent.getEffectiveDate());

                return new FixedPriceInvoiceItem(invoiceId, accountId, thisEvent.getSubscription().getBundleId(),
                        thisEvent.getSubscription().getId(),
                        thisEvent.getPlan().getName(), thisEvent.getPlanPhase().getName(),
                        thisEvent.getEffectiveDate(), endDate, fixedPrice, currency);
            } else {
                return null;
            }
        }
    }
}
