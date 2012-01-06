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

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.billing.BillingModeType;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class DefaultInvoiceGenerator implements InvoiceGenerator {
    @Override
    public Invoice generateInvoice(final UUID accountId, final BillingEventSet events, final InvoiceItemList existingItems, final DateTime targetDate, final Currency targetCurrency) {
        if (events == null) {return new DefaultInvoice(accountId, targetDate, targetCurrency);}
        if (events.size() == 0) {return new DefaultInvoice(accountId, targetDate, targetCurrency);}

        DefaultInvoice invoice = new DefaultInvoice(accountId, targetDate, targetCurrency);
        InvoiceItemList currentItems = generateInvoiceItems(events, invoice.getId(), targetDate, targetCurrency);
        InvoiceItemList itemsToPost = reconcileInvoiceItems(invoice.getId(), currentItems, existingItems);
        invoice.add(itemsToPost);

        return invoice;
    }

    private InvoiceItemList reconcileInvoiceItems(final UUID invoiceId, final InvoiceItemList currentInvoiceItems, final InvoiceItemList existingInvoiceItems) {
        InvoiceItemList currentItems = new InvoiceItemList();
        for (InvoiceItem item : currentInvoiceItems) {
            currentItems.add(new DefaultInvoiceItem(item, invoiceId));
        }

        InvoiceItemList existingItems = (InvoiceItemList) existingInvoiceItems.clone();

        Collections.sort(currentItems);
        Collections.sort(existingItems);

        List<InvoiceItem> existingItemsToRemove = new ArrayList<InvoiceItem>();

        for (InvoiceItem currentItem : currentItems) {
            // see if there are any existing items that are covered by the current item
            for (InvoiceItem existingItem : existingItems) {
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
        for (InvoiceItem existingItem : existingItems) {
            currentItems.add(existingItem.asCredit(invoiceId));
        }

        return currentItems;
    }

    private InvoiceItemList generateInvoiceItems(BillingEventSet events, UUID invoiceId, DateTime targetDate, Currency targetCurrency) {
        InvoiceItemList items = new InvoiceItemList();

        // sort events; this relies on the sort order being by subscription id then start date
        Collections.sort(events);

        // for each event, process it either as a terminated event (if there's a subsequent event)
        // ...or as a non-terminated event (if no subsequent event exists)
        for (int i = 0; i < (events.size() - 1); i++) {
            BillingEvent thisEvent = events.get(i);
            BillingEvent nextEvent = events.get(i + 1);

            if (thisEvent.getSubscriptionId() == nextEvent.getSubscriptionId()) {
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

    private void processEvent(UUID invoiceId, BillingEvent event, List<InvoiceItem> items, DateTime targetDate, Currency targetCurrency) {
        BigDecimal rate = event.getPrice(targetCurrency);
        BigDecimal invoiceItemAmount = calculateInvoiceItemAmount(event, targetDate, rate);
        BillingMode billingMode = getBillingMode(event.getBillingMode());
        DateTime billThroughDate = billingMode.calculateEffectiveEndDate(event.getEffectiveDate(), targetDate, event.getBillCycleDay(), event.getBillingPeriod());

        addInvoiceItem(invoiceId, items, event, billThroughDate, invoiceItemAmount, rate, targetCurrency);
    }

    private void processEvents(UUID invoiceId, BillingEvent firstEvent, BillingEvent secondEvent, List<InvoiceItem> items, DateTime targetDate, Currency targetCurrency) {
        BigDecimal rate = firstEvent.getPrice(targetCurrency);
        BigDecimal invoiceItemAmount = calculateInvoiceItemAmount(firstEvent, secondEvent, targetDate, rate);
        BillingMode billingMode = getBillingMode(firstEvent.getBillingMode());
        DateTime billThroughDate = billingMode.calculateEffectiveEndDate(firstEvent.getEffectiveDate(), secondEvent.getEffectiveDate(), targetDate, firstEvent.getBillCycleDay(), firstEvent.getBillingPeriod());

        addInvoiceItem(invoiceId, items, firstEvent, billThroughDate, invoiceItemAmount, rate, targetCurrency);
    }

    private void addInvoiceItem(UUID invoiceId, List<InvoiceItem> items, BillingEvent event, DateTime billThroughDate, BigDecimal amount, BigDecimal rate, Currency currency) {
        if (!(amount.compareTo(BigDecimal.ZERO) == 0)) {
            DefaultInvoiceItem item = new DefaultInvoiceItem(invoiceId, event.getSubscriptionId(), event.getEffectiveDate(), billThroughDate, event.getDescription(), amount, rate, currency);
            items.add(item);
        }
    }

    private BigDecimal calculateInvoiceItemAmount(BillingEvent event, DateTime targetDate, BigDecimal rate){
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

    private BigDecimal calculateInvoiceItemAmount(BillingEvent firstEvent, BillingEvent secondEvent, DateTime targetDate, BigDecimal rate) {
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

    private BillingMode getBillingMode(BillingModeType billingModeType) {
        switch (billingModeType) {
            case IN_ADVANCE:
                return new InAdvanceBillingMode();
            default:
                return null;
        }
    }
}