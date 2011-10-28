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
import com.ning.billing.entitlement.api.billing.BillingMode;
import com.ning.billing.entitlement.api.billing.IBillingEvent;
import com.ning.billing.invoice.api.BillingEventSet;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.Collections;

public class DefaultInvoiceGenerator implements IInvoiceGenerator {
    @Override
    public Invoice generateInvoice(BillingEventSet events) {
        if (events == null) {return new Invoice();}
        if (events.size() == 0) {return new Invoice();}

        Currency targetCurrency = events.getTargetCurrency();
        Invoice invoice = new Invoice(targetCurrency);
        DateTime targetDate = events.getTargetDate();

        // sort events; this relies on the sort order being by subscription id then start date
        Collections.sort(events);

        // for each event, process it either as a terminated event (if there's a subsequent event)
        // ...or as a non-terminated event (if no subsequent event exists)
        for (int i = 0; i < (events.size() - 1); i++) {
            IBillingEvent thisEvent = events.get(i);
            IBillingEvent nextEvent = events.get(i + 1);

            if (thisEvent.getSubscriptionId() == nextEvent.getSubscriptionId()) {
                processEvents(thisEvent, nextEvent, invoice, targetDate, targetCurrency);
            } else {
                processEvent(thisEvent, invoice, targetDate, targetCurrency);
            }
        }

        // process the last item in the event set
        processEvent(events.getLast(), invoice, targetDate, targetCurrency);

        return invoice;
    }

    private void processEvent(IBillingEvent event, Invoice invoice, DateTime targetDate, Currency targetCurrency) {
        BigDecimal rate = event.getPrice(targetCurrency);
        BigDecimal invoiceItemAmount = calculateInvoiceItemAmount(event, targetDate, rate);

        addInvoiceItem(invoice, invoiceItemAmount);
    }

    private void processEvents(IBillingEvent firstEvent, IBillingEvent secondEvent, Invoice invoice, DateTime targetDate, Currency targetCurrency) {
        BigDecimal rate = firstEvent.getPrice(targetCurrency);
        BigDecimal invoiceItemAmount = calculateInvoiceItemAmount(firstEvent, secondEvent, targetDate, rate);

        addInvoiceItem(invoice, invoiceItemAmount);
    }

    private void addInvoiceItem(Invoice invoice, BigDecimal amount) {
        if (!amount.equals(BigDecimal.ZERO)) {
            InvoiceItem item = new InvoiceItem(amount);
            invoice.add(item);
        }
    }

    private BigDecimal calculateInvoiceItemAmount(IBillingEvent event, DateTime targetDate, BigDecimal rate){
        IBillingMode billingMode = getBillingMode(event.getBillingMode());
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

    private BigDecimal calculateInvoiceItemAmount(IBillingEvent firstEvent, IBillingEvent secondEvent, DateTime targetDate, BigDecimal rate) {
        IBillingMode billingMode = getBillingMode(firstEvent.getBillingMode());
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

    private IBillingMode getBillingMode(BillingMode billingMode) {
        return new InAdvanceBillingMode();
    }
}