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

package com.ning.billing.invoice.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.InternationalPrice;
import com.ning.billing.entitlement.api.billing.BillingModeType;
import com.ning.billing.invoice.api.InvoiceApiException;
import org.joda.time.DateTime;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;

import javax.annotation.Nullable;

public class DefaultInvoiceGenerator implements InvoiceGenerator {
    private static final int ROUNDING_MODE = InvoicingConfiguration.getRoundingMode();
    private static final int NUMBER_OF_DECIMALS = InvoicingConfiguration.getNumberOfDecimals();
    //private static final Logger log = LoggerFactory.getLogger(DefaultInvoiceGenerator.class);

    @Override
    public Invoice generateInvoice(final UUID accountId, final BillingEventSet events,
                                   @Nullable final List<InvoiceItem> items, final DateTime targetDate,
                                   final Currency targetCurrency) throws InvoiceApiException {
        if ((events == null) || (events.size() == 0)) {
            return null;
        }

        Collections.sort(events);

        List<InvoiceItem> existingItems = new ArrayList<InvoiceItem>();
        if (items != null) {
            existingItems = new ArrayList<InvoiceItem>(items);
            Collections.sort(existingItems);
        }

        DefaultInvoice invoice = new DefaultInvoice(accountId, targetDate, targetCurrency);
        UUID invoiceId = invoice.getId();
        List<InvoiceItem> proposedItems = generateInvoiceItems(invoiceId, events, targetDate, targetCurrency);

        if (existingItems != null) {
            removeCancellingInvoiceItems(existingItems);
            removeDuplicatedInvoiceItems(proposedItems, existingItems);

            for (InvoiceItem existingItem : existingItems) {
                if (existingItem instanceof RecurringInvoiceItem) {
                    RecurringInvoiceItem recurringItem = (RecurringInvoiceItem) existingItem;
                    proposedItems.add(recurringItem.asCredit());
                }
            }
        }

        if (proposedItems == null || proposedItems.size()  == 0) {
            return null;
        } else {
            invoice.addInvoiceItems(proposedItems);
            return invoice;
        }
    }

   /*
    * removes all matching items from both submitted collections
    */
    private void removeDuplicatedInvoiceItems(final List<InvoiceItem> proposedItems,
                                              final List<InvoiceItem> existingInvoiceItems) {
        Iterator<InvoiceItem> proposedItemIterator = proposedItems.iterator();
        while (proposedItemIterator.hasNext()) {
            InvoiceItem proposedItem = proposedItemIterator.next();

            Iterator<InvoiceItem> existingItemIterator = existingInvoiceItems.iterator();
            while (existingItemIterator.hasNext()) {
                InvoiceItem existingItem = existingItemIterator.next();
                if (existingItem.equals(proposedItem)) {
                    existingItemIterator.remove();
                    proposedItemIterator.remove();
                }
            }
//            if (existingInvoiceItems.contains(proposedItem)) {
//                existingInvoiceItems.remove(proposedItem);
//                proposedItemIterator.remove();
//            }
        }
    }

    private void removeCancellingInvoiceItems(final List<InvoiceItem> items) {
        List<UUID> itemsToRemove = new ArrayList<UUID>();

        for (InvoiceItem item1 : items) {
            if (item1 instanceof RecurringInvoiceItem) {
                RecurringInvoiceItem recurringInvoiceItem = (RecurringInvoiceItem) item1;
                if (recurringInvoiceItem.reversesItem()) {
                    itemsToRemove.add(recurringInvoiceItem.getId());
                    itemsToRemove.add(recurringInvoiceItem.getReversedItemId());
                }
            }
        }

        Iterator<InvoiceItem> iterator = items.iterator();
        while (iterator.hasNext()) {
            InvoiceItem item = iterator.next();
            if (itemsToRemove.contains(item.getId())) {
                iterator.remove();
            }
        }
    }

    private List<InvoiceItem> generateInvoiceItems(final UUID invoiceId, final BillingEventSet events,
                                                   final DateTime targetDate, final Currency currency) throws InvoiceApiException {
        List<InvoiceItem> items = new ArrayList<InvoiceItem>();

        for (int i = 0; i < events.size(); i++) {
            BillingEvent thisEvent = events.get(i);
            BillingEvent nextEvent = events.isLast(thisEvent) ? null : events.get(i + 1);
            if (nextEvent != null) {
                nextEvent = (thisEvent.getSubscription().getId() == nextEvent.getSubscription().getId()) ? nextEvent : null;
            }

            items.addAll(processEvents(invoiceId, thisEvent, nextEvent, targetDate, currency));
        }

        return items;
    }

    private List<InvoiceItem> processEvents(final UUID invoiceId, final BillingEvent thisEvent, final BillingEvent nextEvent,
                                            final DateTime targetDate, final Currency currency) throws InvoiceApiException {
        List<InvoiceItem> items = new ArrayList<InvoiceItem>();
        InvoiceItem fixedPriceInvoiceItem = generateFixedPriceItem(invoiceId, thisEvent, targetDate, currency);
        if (fixedPriceInvoiceItem != null) {
            items.add(fixedPriceInvoiceItem);
        }

        BillingPeriod billingPeriod = thisEvent.getBillingPeriod();
        if (billingPeriod != BillingPeriod.NO_BILLING_PERIOD) {
            BillingMode billingMode = instantiateBillingMode(thisEvent.getBillingMode());
            DateTime startDate = thisEvent.getEffectiveDate();
            if (!startDate.isAfter(targetDate)) {
                DateTime endDate = (nextEvent == null) ? null : nextEvent.getEffectiveDate();
                int billCycleDay = thisEvent.getBillCycleDay();

                List<RecurringInvoiceItemData> itemData;
                try {
                    itemData = billingMode.calculateInvoiceItemData(startDate, endDate, targetDate, billCycleDay, billingPeriod);
                } catch (InvalidDateSequenceException e) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_INVALID_DATE_SEQUENCE, startDate, endDate, targetDate);
                }

                for (RecurringInvoiceItemData itemDatum : itemData) {
                    InternationalPrice price = thisEvent.getRecurringPrice();
                    if (price != null) {
                        BigDecimal rate;

                        try {
                            rate = thisEvent.getRecurringPrice().getPrice(currency);
                        } catch (CatalogApiException e) {
                            throw new InvoiceApiException(e, ErrorCode.CAT_NO_PRICE_FOR_CURRENCY, currency.toString());
                        }

                        BigDecimal amount = itemDatum.getNumberOfCycles().multiply(rate).setScale(NUMBER_OF_DECIMALS, ROUNDING_MODE);

                        RecurringInvoiceItem recurringItem = new RecurringInvoiceItem(invoiceId, thisEvent.getSubscription().getId(),
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

    private BillingMode instantiateBillingMode(BillingModeType billingMode) {
        switch (billingMode) {
            case IN_ADVANCE:
                return new InAdvanceBillingMode();
            default:
                throw new UnsupportedOperationException();
        }
    }

    private InvoiceItem generateFixedPriceItem(final UUID invoiceId, final BillingEvent thisEvent,
                                               final DateTime targetDate, final Currency currency) throws InvoiceApiException {
        if (thisEvent.getEffectiveDate().isAfter(targetDate)) {
            return null;
        } else {
            FixedPriceInvoiceItem fixedPriceInvoiceItem = null;

            if (thisEvent.getFixedPrice() != null) {
                try {
                    Duration duration = thisEvent.getPlanPhase().getDuration();
                    DateTime endDate = duration.addToDateTime(thisEvent.getEffectiveDate());
                    BigDecimal fixedPrice = thisEvent.getFixedPrice().getPrice(currency);
                    fixedPriceInvoiceItem = new FixedPriceInvoiceItem(invoiceId, thisEvent.getSubscription().getId(),
                                                                      thisEvent.getPlan().getName(), thisEvent.getPlanPhase().getName(),
                                                                      thisEvent.getEffectiveDate(), endDate, fixedPrice, currency);
                } catch (CatalogApiException e) {
                    throw new InvoiceApiException(e, ErrorCode.CAT_NO_PRICE_FOR_CURRENCY, currency.toString());
                }
            }

            return fixedPriceInvoiceItem;
        }
    }

//    // assumption: startDate is in the user's time zone
//    private DateTime calculateSegmentEndDate(final DateTime startDate, final DateTime nextEndDate,
//                                             final int billCycleDay, final BillingPeriod billingPeriod) {
//        int dayOfMonth = startDate.getDayOfMonth();
//        int maxDayOfMonth = startDate.dayOfMonth().getMaximumValue();
//
//        DateTime nextBillingDate;
//
//        // if the start date is not on the bill cycle day, move it to the nearest following date that works
//        if ((billCycleDay > maxDayOfMonth) || (dayOfMonth == billCycleDay)) {
//            nextBillingDate = startDate.plusMonths(billingPeriod.getNumberOfMonths());
//        } else {
//            MutableDateTime proposedDate = startDate.toMutableDateTime();
//
//            if (dayOfMonth < billCycleDay) {
//                // move the end date forward to the bill cycle date (same month)
//                int effectiveBillCycleDay = (billCycleDay > maxDayOfMonth) ? maxDayOfMonth : billCycleDay;
//                nextBillingDate = proposedDate.dayOfMonth().set(effectiveBillCycleDay).toDateTime();
//            } else {
//                // go to the next month
//                proposedDate = proposedDate.monthOfYear().add(1);
//                maxDayOfMonth = proposedDate.dayOfMonth().getMaximumValue();
//                int effectiveBillCycleDay = (billCycleDay > maxDayOfMonth) ? maxDayOfMonth : billCycleDay;
//                nextBillingDate = proposedDate.dayOfMonth().set(effectiveBillCycleDay).toDateTime();
//            }
//        }
//
//        return nextBillingDate.isAfter(nextEndDate) ? nextEndDate : nextBillingDate;
//    }
}