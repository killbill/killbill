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
import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.catalog.api.BillingPeriod;

import static com.ning.billing.invoice.generator.InvoiceDateUtils.calculateBillingCycleDateOnOrAfter;
import static com.ning.billing.invoice.generator.InvoiceDateUtils.calculateEffectiveEndDate;
import static com.ning.billing.invoice.generator.InvoiceDateUtils.calculateLastBillingCycleDateBefore;
import static com.ning.billing.invoice.generator.InvoiceDateUtils.calculateNumberOfWholeBillingPeriods;
import static com.ning.billing.invoice.generator.InvoiceDateUtils.calculateProRationAfterLastBillingCycleDate;
import static com.ning.billing.invoice.generator.InvoiceDateUtils.calculateProRationBeforeFirstBillingPeriod;

public class InAdvanceBillingMode implements BillingMode {

    private static final Logger log = LoggerFactory.getLogger(InAdvanceBillingMode.class);

    @Override
    public List<RecurringInvoiceItemData> calculateInvoiceItemData(final LocalDate startDate, @Nullable final LocalDate endDate,
                                                                   final LocalDate targetDate,
                                                                   final int billingCycleDayLocal, final BillingPeriod billingPeriod) throws InvalidDateSequenceException {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new InvalidDateSequenceException();
        }
        if (targetDate.isBefore(startDate)) {
            throw new InvalidDateSequenceException();
        }

        final List<RecurringInvoiceItemData> results = new ArrayList<RecurringInvoiceItemData>();

        final LocalDate firstBillingCycleDate = calculateBillingCycleDateOnOrAfter(startDate, billingCycleDayLocal);

        // We are not billing for less than a day (we could...)
        if (endDate != null && endDate.equals(startDate)) {
            return results;
        }
        //
        // If there is an endDate and that endDate is before our first coming firstBillingCycleDate, all we have to do
        // is to charge for that period
        //
        if (endDate != null && !endDate.isAfter(firstBillingCycleDate)) {
            final BigDecimal leadingProRationPeriods = calculateProRationBeforeFirstBillingPeriod(startDate, endDate, billingPeriod);
            final RecurringInvoiceItemData itemData = new RecurringInvoiceItemData(startDate, endDate, leadingProRationPeriods);
            results.add(itemData);
            return results;
        }

        //
        // Leading proration if
        // i) The first firstBillingCycleDate is strictly after our start date AND
        // ii) The endDate is is not null and is strictly after our firstBillingCycleDate (previous check)
        //
        if (firstBillingCycleDate.isAfter(startDate)) {
            final BigDecimal leadingProRationPeriods = calculateProRationBeforeFirstBillingPeriod(startDate, firstBillingCycleDate, billingPeriod);
            if (leadingProRationPeriods != null && leadingProRationPeriods.compareTo(BigDecimal.ZERO) > 0) {
                // Not common - add info in the logs for debugging purposes
                final RecurringInvoiceItemData itemData = new RecurringInvoiceItemData(startDate, firstBillingCycleDate, leadingProRationPeriods);
                log.info("Adding pro-ration: {}", itemData);
                results.add(itemData);
            }
        }

        //
        // Calculate the effectiveEndDate from the firstBillingCycleDate:
        // - If endDate != null and targetDate is after endDate => this is the endDate and will lead to a trailing pro-ration
        // - If not, this is the last billingCycleDate calculation right after the targetDate
        //
        final LocalDate effectiveEndDate;
        if (endDate != null) {
            effectiveEndDate = calculateEffectiveEndDate(firstBillingCycleDate, targetDate, endDate, billingPeriod);
        } else {
            effectiveEndDate = calculateEffectiveEndDate(firstBillingCycleDate, targetDate, billingPeriod);
        }

        //
        // Based on what we calculated previously, code recompute one more time the numberOfWholeBillingPeriods
        //
        final LocalDate lastBillingCycleDate = calculateLastBillingCycleDateBefore(effectiveEndDate, firstBillingCycleDate, billingCycleDayLocal, billingPeriod);
        final int numberOfWholeBillingPeriods = calculateNumberOfWholeBillingPeriods(firstBillingCycleDate, lastBillingCycleDate, billingPeriod);
        final int numberOfMonthsPerBillingPeriod = billingPeriod.getNumberOfMonths();

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
            final LocalDate servicePeriodEndDate = firstBillingCycleDate.plusMonths((i + 1) * numberOfMonthsPerBillingPeriod);

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
        return results;
    }
}
