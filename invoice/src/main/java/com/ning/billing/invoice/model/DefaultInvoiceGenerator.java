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
import java.util.Collections;
import java.util.Iterator;
import java.util.UUID;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.billing.BillingModeType;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;

import javax.annotation.Nullable;

public class DefaultInvoiceGenerator implements InvoiceGenerator {
    private static final Logger log = LoggerFactory.getLogger(DefaultInvoiceGenerator.class);

    @Override
    public Invoice generateInvoice(final UUID accountId, final BillingEventSet events,
                                   @Nullable final InvoiceItemList existingItems, final DateTime targetDate,
                                   final Currency targetCurrency) {
        if (events == null) {
            return null;
        }

        if (events.size() == 0) {
            return null;
        }

        DefaultInvoice invoice = new DefaultInvoice(accountId, targetDate, targetCurrency);
        InvoiceItemList currentItems = generateInvoiceItems(events, invoice.getId(), targetDate, targetCurrency);
        InvoiceItemList itemsToPost = reconcileInvoiceItems(invoice.getId(), currentItems, existingItems);

        if (itemsToPost.size() == 0) {
            return null;
        } else {
            invoice.addInvoiceItems(itemsToPost);
            return invoice;
        }
    }

    private InvoiceItemList reconcileInvoiceItems(final UUID invoiceId, final InvoiceItemList currentInvoiceItems,
                                                  final InvoiceItemList existingInvoiceItems) {
        if ((existingInvoiceItems == null) || (existingInvoiceItems.size() == 0)) {
            return currentInvoiceItems;
        }

        InvoiceItemList currentItems = new InvoiceItemList();
        for (final InvoiceItem item : currentInvoiceItems) {
            currentItems.add(new DefaultInvoiceItem(item, invoiceId));
        }

        InvoiceItemList existingItems = (InvoiceItemList) existingInvoiceItems.clone();

        Collections.sort(currentItems);
        Collections.sort(existingItems);

        for (final InvoiceItem currentItem : currentItems) {
            Iterator<InvoiceItem> it = existingItems.iterator();

            // see if there are any existing items that are covered by the current item
            while (it.hasNext()) {
                InvoiceItem existingItem = it.next();
                if (currentItem.duplicates(existingItem)) {
                    currentItem.subtract(existingItem);
                    it.remove();
                }
            }
        }

        // remove cancelling pairs of invoice items
        existingItems.removeCancellingPairs();

        // add existing items that aren't covered by current items as credit items
        for (final InvoiceItem existingItem : existingItems) {
            currentItems.add(existingItem.asCredit(existingItem.getInvoiceId()));
        }

        currentItems.cleanupDuplicatedItems();

        return currentItems;
    }

    private InvoiceItemList generateInvoiceItems(final BillingEventSet events, final UUID invoiceId,
                                                 final DateTime targetDate, final Currency targetCurrency) {
        InvoiceItemList items = new InvoiceItemList();

        // sort events; this relies on the sort order being by subscription id then start date
        Collections.sort(events);

        // for each event, process it either as a terminated event (if there's a subsequent event)
        // ...or as a non-terminated event (if no subsequent event exists)
        for (int i = 0; i < (events.size() - 1); i++) {
            BillingEvent thisEvent = events.get(i);
            BillingEvent nextEvent = events.get(i + 1);

            if (thisEvent.getSubscription().getId() == nextEvent.getSubscription().getId()) {
                processEvents(invoiceId, thisEvent, nextEvent, items, targetDate, targetCurrency);
            } else {
                processEvent(invoiceId, thisEvent, items, targetDate, targetCurrency);
            }
        }

        // process the last item in the event set
        if (events.size() > 0) {
            processEvent(invoiceId, events.getLast(), items, targetDate, targetCurrency);
        }

        return items;
    }

    private void processEvent(final UUID invoiceId, final BillingEvent event, final InvoiceItemList items,
                              final DateTime targetDate, final Currency targetCurrency) {
    	try {
            BigDecimal recurringRate = event.getRecurringPrice() == null ? null : event.getRecurringPrice().getPrice(targetCurrency);
            BigDecimal fixedPrice = event.getFixedPrice() == null ? null : event.getFixedPrice().getPrice(targetCurrency);

    		BigDecimal numberOfBillingPeriods;
            BigDecimal recurringAmount = null;

            if (recurringRate != null) {
                numberOfBillingPeriods = calculateNumberOfBillingPeriods(event, targetDate);
                recurringAmount = numberOfBillingPeriods.multiply(recurringRate);
            }

            BillingMode billingMode = getBillingMode(event.getBillingMode());
            DateTime billThroughDate = billingMode.calculateEffectiveEndDate(event.getEffectiveDate(), targetDate, event.getBillCycleDay(), event.getBillingPeriod());
            if ((event.getBillingPeriod() == BillingPeriod.NO_BILLING_PERIOD) || (!billThroughDate.isAfter(targetDate.plusMonths(event.getBillingPeriod().getNumberOfMonths())))) {
                BigDecimal effectiveFixedPrice = items.hasInvoiceItemForPhase(event.getPlanPhase().getName()) ? null : fixedPrice;
                addInvoiceItem(invoiceId, items, event, billThroughDate, recurringAmount, recurringRate, effectiveFixedPrice, targetCurrency);
            }
    	} catch (CatalogApiException e) {
            log.error(String.format("Encountered a catalog error processing invoice %s for billing event on date %s", 
                    invoiceId.toString(), 
                    ISODateTimeFormat.basicDateTime().print(event.getEffectiveDate())), e);
        }
    }

    private void processEvents(final UUID invoiceId, final BillingEvent firstEvent, final BillingEvent secondEvent,
                               final InvoiceItemList items, final DateTime targetDate, final Currency targetCurrency) {
    	try {
            BigDecimal recurringRate = firstEvent.getRecurringPrice() == null ? null : firstEvent.getRecurringPrice().getPrice(targetCurrency);
            BigDecimal fixedPrice = firstEvent.getFixedPrice() == null ? null : firstEvent.getFixedPrice().getPrice(targetCurrency);

            BigDecimal numberOfBillingPeriods;
            BigDecimal recurringAmount = null;

            if (recurringRate != null) {
                numberOfBillingPeriods = calculateNumberOfBillingPeriods(firstEvent, secondEvent, targetDate);
                recurringAmount = numberOfBillingPeriods.multiply(recurringRate);
            }

            BillingMode billingMode = getBillingMode(firstEvent.getBillingMode());
            DateTime billThroughDate = billingMode.calculateEffectiveEndDate(firstEvent.getEffectiveDate(), secondEvent.getEffectiveDate(), targetDate, firstEvent.getBillCycleDay(), firstEvent.getBillingPeriod());

            BigDecimal effectiveFixedPrice = items.hasInvoiceItemForPhase(firstEvent.getPlanPhase().getName()) ? null : fixedPrice;
            addInvoiceItem(invoiceId, items, firstEvent, billThroughDate, recurringAmount, recurringRate, effectiveFixedPrice, targetCurrency);
    	} catch (CatalogApiException e) {
    		log.error(String.format("Encountered a catalog error processing invoice %s for billing event on date %s",
                    invoiceId.toString(),
                    ISODateTimeFormat.basicDateTime().print(firstEvent.getEffectiveDate())), e);
        }
    }

    private void addInvoiceItem(final UUID invoiceId, final InvoiceItemList items, final BillingEvent event,
                                final DateTime billThroughDate, final BigDecimal amount, final BigDecimal rate,
                                final BigDecimal fixedAmount, final Currency currency) {
        DefaultInvoiceItem item = new DefaultInvoiceItem(invoiceId, event.getSubscription().getId(),
                                  event.getPlan().getName(), event.getPlanPhase().getName(),  event.getEffectiveDate(),
                                  billThroughDate, amount, rate, fixedAmount, currency);
        items.add(item);
    }

    private BigDecimal calculateNumberOfBillingPeriods(final BillingEvent event, final DateTime targetDate){
        BillingMode billingMode = getBillingMode(event.getBillingMode());
        DateTime startDate = event.getEffectiveDate();
        int billingCycleDay = event.getBillCycleDay();
        BillingPeriod billingPeriod = event.getBillingPeriod();

        try {
            return billingMode.calculateNumberOfBillingCycles(startDate, targetDate, billingCycleDay, billingPeriod);
        } catch (InvalidDateSequenceException e) {
            // TODO: Jeff -- log issue
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal calculateNumberOfBillingPeriods(final BillingEvent firstEvent, final BillingEvent secondEvent,
                                                  final DateTime targetDate) {
        BillingMode billingMode = getBillingMode(firstEvent.getBillingMode());
        DateTime startDate = firstEvent.getEffectiveDate();
        int billingCycleDay = firstEvent.getBillCycleDay();
        BillingPeriod billingPeriod = firstEvent.getBillingPeriod();

        DateTime endDate = secondEvent.getEffectiveDate();

        try {
            return billingMode.calculateNumberOfBillingCycles(startDate, endDate, targetDate, billingCycleDay, billingPeriod);
        } catch (InvalidDateSequenceException e) {
            // TODO: Jeff -- log issue
            return BigDecimal.ZERO;
        }
    }

    private BillingMode getBillingMode(final BillingModeType billingModeType) {
        switch (billingModeType) {
            case IN_ADVANCE:
                return new InAdvanceBillingMode();
            default:
                return null;
        }
    }
}