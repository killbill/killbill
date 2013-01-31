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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.joda.time.Months;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
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
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.config.InvoiceConfig;
import com.ning.billing.util.svcapi.junction.BillingEvent;
import com.ning.billing.util.svcapi.junction.BillingEventSet;
import com.ning.billing.util.svcapi.junction.BillingModeType;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

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
                                   final LocalDate targetDate,
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
                    if (item.getSubscriptionId() == null || // Always include migration invoices, credits, external charges etc.
                        !events.getSubscriptionIdsWithAutoInvoiceOff()
                               .contains(item.getSubscriptionId())) { //don't add items with auto_invoice_off tag
                        existingItems.add(item);
                    }
                }
            }

            Collections.sort(existingItems);
        }

        final LocalDate adjustedTargetDate = adjustTargetDate(existingInvoices, targetDate);

        final Invoice invoice = new DefaultInvoice(accountId, clock.getUTCToday(), adjustedTargetDate, targetCurrency);
        final UUID invoiceId = invoice.getId();
        final List<InvoiceItem> proposedItems = generateInvoiceItems(invoiceId, accountId, events, adjustedTargetDate, targetCurrency);

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

    void generateCBAForExistingInvoices(final UUID accountId, final List<Invoice> existingInvoices,
                                        final List<InvoiceItem> proposedItems, final Currency currency) {
        // Determine most accurate invoice balances up to this point
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
                final LocalDate creditDate = clock.getUTCToday();
                final CreditBalanceAdjInvoiceItem creditInvoiceItem = new CreditBalanceAdjInvoiceItem(invoiceId, accountId, creditDate, invoiceBalance.negate(), currency);
                proposedItems.add(creditInvoiceItem);
            }
        }
    }

    void addRepairedItems(final List<InvoiceItem> existingItems, final List<InvoiceItem> proposedItems) {
        for (final InvoiceItem existingItem : existingItems) {
            if (existingItem.getInvoiceItemType() == InvoiceItemType.RECURRING ||
                existingItem.getInvoiceItemType() == InvoiceItemType.FIXED) {
                final BigDecimal existingAdjustedPositiveAmount = getAdjustedPositiveAmount(existingItems, existingItem.getId());
                final BigDecimal amountNegated = existingItem.getAmount() == null ? null : existingItem.getAmount().subtract(existingAdjustedPositiveAmount).negate();
                if (amountNegated.compareTo(BigDecimal.ZERO) < 0) {
                    final RepairAdjInvoiceItem repairItem = new RepairAdjInvoiceItem(existingItem.getInvoiceId(), existingItem.getAccountId(), existingItem.getStartDate(), existingItem.getEndDate(), amountNegated, existingItem.getCurrency(), existingItem.getId());
                    proposedItems.add(repairItem);
                }
            }
        }

    }

    // We check to see if there are any adjustments that point to the item we are trying to repair
    // If we did any CREDIT_ADJ or REFUND_ADJ, then we unfortunately we can't know what is the intent
    // was as it applies to the full Invoice, so we ignore it. That might result in an extra positive CBA
    // that would have to be corrected manually. This is the best we can do, and administrators should always
    // use ITEM_ADJUSTEMNT rather than CREDIT_ADJ or REFUND_ADJ when possible.
    //
    BigDecimal getAdjustedPositiveAmount(final List<InvoiceItem> existingItems, final UUID linkedItemId) {
        BigDecimal totalAdjustedOnItem = BigDecimal.ZERO;
        final Collection<InvoiceItem> invoiceItems = Collections2.filter(existingItems, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem item) {
                return item.getInvoiceItemType() == InvoiceItemType.ITEM_ADJ &&
                       item.getLinkedItemId() != null && item.getLinkedItemId().equals(linkedItemId);
            }
        });

        for (final InvoiceItem invoiceItem : invoiceItems) {
            totalAdjustedOnItem = totalAdjustedOnItem.add(invoiceItem.getAmount());
        }
        return totalAdjustedOnItem.negate();
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
            final LocalDate creditDate = clock.getUTCToday();
            final CreditBalanceAdjInvoiceItem creditInvoiceItem = new CreditBalanceAdjInvoiceItem(invoiceId, accountId, creditDate, creditAmount, targetCurrency);
            proposedItems.add(creditInvoiceItem);
        }
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

    /*
     * Removes all matching items from both submitted collections
     */
    void removeDuplicatedInvoiceItems(final List<InvoiceItem> proposedItems,
                                      final List<InvoiceItem> existingInvoiceItems) {
        // We can't just use sets here as order matters (we want to keep duplicated in existingInvoiceItems)
        final Iterator<InvoiceItem> proposedItemIterator = proposedItems.iterator();
        while (proposedItemIterator.hasNext()) {
            final InvoiceItem proposedItem = proposedItemIterator.next();

            final Iterator<InvoiceItem> existingItemIterator = existingInvoiceItems.iterator();
            while (existingItemIterator.hasNext()) {
                final InvoiceItem existingItem = existingItemIterator.next();
                if (existingItem.equals(proposedItem)) {
                    existingItemIterator.remove();
                    proposedItemIterator.remove();
                    break;
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

    private List<InvoiceItem> generateInvoiceItems(final UUID invoiceId, final UUID accountId, final BillingEventSet events,
                                                   final LocalDate targetDate, final Currency currency) throws InvoiceApiException {
        final List<InvoiceItem> items = new ArrayList<InvoiceItem>();

        if (events.size() == 0) {
            return items;
        }

        // Pretty-print the generated invoice items from the junction events
        final StringBuilder logStringBuilder = new StringBuilder("Invoice items generated for invoiceId ")
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
                items.addAll(processEvents(invoiceId, accountId, thisEvent, adjustedNextEvent, targetDate, currency, logStringBuilder));
            }
        }
        items.addAll(processEvents(invoiceId, accountId, nextEvent, null, targetDate, currency, logStringBuilder));

        log.info(logStringBuilder.toString());

        return items;
    }

    // Turn a set of events into a list of invoice items. Note that the dates on the invoice items will be rounded (granularity of a day)
    private List<InvoiceItem> processEvents(final UUID invoiceId, final UUID accountId, final BillingEvent thisEvent, @Nullable final BillingEvent nextEvent,
                                            final LocalDate targetDate, final Currency currency,
                                            final StringBuilder logStringBuilder) throws InvoiceApiException {
        final List<InvoiceItem> items = new ArrayList<InvoiceItem>();

        // Handle fixed price items
        final InvoiceItem fixedPriceInvoiceItem = generateFixedPriceItem(invoiceId, accountId, thisEvent, targetDate, currency);
        if (fixedPriceInvoiceItem != null) {
            items.add(fixedPriceInvoiceItem);
        }

        // Handle recurring items
        final BillingPeriod billingPeriod = thisEvent.getBillingPeriod();
        if (billingPeriod != BillingPeriod.NO_BILLING_PERIOD) {
            final BillingMode billingMode = instantiateBillingMode(thisEvent.getBillingMode());
            final LocalDate startDate = new LocalDate(thisEvent.getEffectiveDate(), thisEvent.getTimeZone());

            if (!startDate.isAfter(targetDate)) {
                final LocalDate endDate = (nextEvent == null) ? null : new LocalDate(nextEvent.getEffectiveDate(), nextEvent.getTimeZone());

                final int billCycleDayLocal = thisEvent.getBillCycleDay().getDayOfMonthLocal();

                final List<RecurringInvoiceItemData> itemData;
                try {
                    itemData = billingMode.calculateInvoiceItemData(startDate, endDate, targetDate, billCycleDayLocal, billingPeriod);
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

        // For debugging purposes
        logStringBuilder.append("\n")
                        .append(thisEvent);
        for (final InvoiceItem item : items) {
            logStringBuilder.append("\n\t")
                            .append(item);
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
