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
import java.util.List;
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
import com.ning.billing.invoice.api.BillingEventSet;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;

public class DefaultInvoiceGenerator implements InvoiceGenerator {
    private static final Logger log = LoggerFactory.getLogger(DefaultInvoiceGenerator.class);

    @Override
    public Invoice generateInvoice(final UUID accountId, final BillingEventSet events,
                                   final InvoiceItemList existingItems, final DateTime targetDate,
                                   final Currency targetCurrency) {
        if (events == null) {return new DefaultInvoice(accountId, targetDate, targetCurrency);}
        if (events.size() == 0) {return new DefaultInvoice(accountId, targetDate, targetCurrency);}

        DefaultInvoice invoice = new DefaultInvoice(accountId, targetDate, targetCurrency);
        InvoiceItemList currentItems = generateInvoiceItems(events, invoice.getId(), targetDate, targetCurrency);
        InvoiceItemList itemsToPost = reconcileInvoiceItems(invoice.getId(), currentItems, existingItems);

        invoice.addInvoiceItems(itemsToPost);

        return invoice;
    }

    private InvoiceItemList reconcileInvoiceItems(final UUID invoiceId, final InvoiceItemList currentInvoiceItems,
                                                  final InvoiceItemList existingInvoiceItems) {
        InvoiceItemList currentItems = new InvoiceItemList();
        for (final InvoiceItem item : currentInvoiceItems) {
            currentItems.add(new DefaultInvoiceItem(item, invoiceId));
        }

        InvoiceItemList existingItems = (InvoiceItemList) existingInvoiceItems.clone();

        Collections.sort(currentItems);
        Collections.sort(existingItems);

        List<InvoiceItem> existingItemsToRemove = new ArrayList<InvoiceItem>();

        for (final InvoiceItem currentItem : currentItems) {
            // see if there are any existing items that are covered by the current item
            for (final InvoiceItem existingItem : existingItems) {
                if (currentItem.duplicates(existingItem)) {
                    currentItem.subtract(existingItem);
                    existingItemsToRemove.add(existingItem);
                }
            }
        }

        existingItems.removeAll(existingItemsToRemove);

        // remove cancelling pairs of invoice items
        existingItems.removeCancellingPairs();

        // remove zero-dollar invoice items
        currentItems.removeZeroDollarItems();

        // add existing items that aren't covered by current items as credit items
        for (final InvoiceItem existingItem : existingItems) {
            currentItems.add(existingItem.asCredit(invoiceId));
        }

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

    private void processEvent(final UUID invoiceId, final BillingEvent event, final List<InvoiceItem> items,
                              final DateTime targetDate, final Currency targetCurrency) {
    	try {
    		//TODO: Jeff getPrice() -> getRecurringPrice()
    		BigDecimal rate = event.getRecurringPrice(targetCurrency);
    		BigDecimal invoiceItemAmount = calculateInvoiceItemAmount(event, targetDate, rate);
    		BillingMode billingMode = getBillingMode(event.getBillingMode());
    		DateTime billThroughDate = billingMode.calculateEffectiveEndDate(event.getEffectiveDate(), targetDate, event.getBillCycleDay(), event.getBillingPeriod());

    		addInvoiceItem(invoiceId, items, event, billThroughDate, invoiceItemAmount, rate, targetCurrency);
    	} catch (CatalogApiException e) {
            log.error(String.format("Encountered a catalog error processing invoice %s for billing event on date %s", 
                    invoiceId.toString(), 
                    ISODateTimeFormat.basicDateTime().print(event.getEffectiveDate())), e);
        }
    }

    private void processEvents(final UUID invoiceId, final BillingEvent firstEvent, final BillingEvent secondEvent,
                               final List<InvoiceItem> items, final DateTime targetDate, final Currency targetCurrency) {
    	//TODO: Jeff getPrice() -> getRecurringPrice()
    	try {
    		BigDecimal rate = firstEvent.getRecurringPrice(targetCurrency);
    		BigDecimal invoiceItemAmount = calculateInvoiceItemAmount(firstEvent, secondEvent, targetDate, rate);
    		BillingMode billingMode = getBillingMode(firstEvent.getBillingMode());
    		DateTime billThroughDate = billingMode.calculateEffectiveEndDate(firstEvent.getEffectiveDate(), secondEvent.getEffectiveDate(), targetDate, firstEvent.getBillCycleDay(), firstEvent.getBillingPeriod());

    		addInvoiceItem(invoiceId, items, firstEvent, billThroughDate, invoiceItemAmount, rate, targetCurrency);
    	} catch (CatalogApiException e) {
    		log.error(String.format("Encountered a catalog error processing invoice %s for billing event on date %s", 
                    invoiceId.toString(), 
                    ISODateTimeFormat.basicDateTime().print(firstEvent.getEffectiveDate())), e);
        }
    }

    private void addInvoiceItem(final UUID invoiceId, final List<InvoiceItem> items, final BillingEvent event,
                                final DateTime billThroughDate, final BigDecimal amount, final BigDecimal rate,
                                final Currency currency) {
        if (!(amount.compareTo(BigDecimal.ZERO) == 0)) {
            DefaultInvoiceItem item = new DefaultInvoiceItem(invoiceId, event.getSubscription().getId(), event.getEffectiveDate(), billThroughDate, event.getDescription(), amount, rate, currency);
            items.add(item);
        }
    }

    private BigDecimal calculateInvoiceItemAmount(final BillingEvent event, final DateTime targetDate,
                                                  final BigDecimal rate){
        BillingMode billingMode = getBillingMode(event.getBillingMode());
        DateTime startDate = event.getEffectiveDate();
        int billingCycleDay = event.getBillCycleDay();
        BillingPeriod billingPeriod = event.getBillingPeriod();

        try {
            BigDecimal numberOfBillingCycles;
            numberOfBillingCycles = billingMode.calculateNumberOfBillingCycles(startDate, targetDate, billingCycleDay, billingPeriod);
            return numberOfBillingCycles.multiply(rate);
        } catch (InvalidDateSequenceException e) {
            // TODO: Jeff -- log issue
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal calculateInvoiceItemAmount(final BillingEvent firstEvent, final BillingEvent secondEvent,
                                                  final DateTime targetDate, final BigDecimal rate) {
        BillingMode billingMode = getBillingMode(firstEvent.getBillingMode());
        DateTime startDate = firstEvent.getEffectiveDate();
        int billingCycleDay = firstEvent.getBillCycleDay();
        BillingPeriod billingPeriod = firstEvent.getBillingPeriod();

        DateTime endDate = secondEvent.getEffectiveDate();

        try {
            BigDecimal numberOfBillingCycles;
            numberOfBillingCycles = billingMode.calculateNumberOfBillingCycles(startDate, endDate, targetDate, billingCycleDay, billingPeriod);
            return numberOfBillingCycles.multiply(rate);
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