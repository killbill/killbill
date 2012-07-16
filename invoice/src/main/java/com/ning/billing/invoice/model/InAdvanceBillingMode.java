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

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.ning.billing.catalog.api.BillingPeriod;

import static com.ning.billing.invoice.generator.InvoiceDateUtils.calculateBillingCycleDateOnOrAfter;
import static com.ning.billing.invoice.generator.InvoiceDateUtils.calculateEffectiveEndDate;
import static com.ning.billing.invoice.generator.InvoiceDateUtils.calculateLastBillingCycleDateBefore;
import static com.ning.billing.invoice.generator.InvoiceDateUtils.calculateNumberOfWholeBillingPeriods;
import static com.ning.billing.invoice.generator.InvoiceDateUtils.calculateProRationAfterLastBillingCycleDate;
import static com.ning.billing.invoice.generator.InvoiceDateUtils.calculateProRationBeforeFirstBillingPeriod;

public class InAdvanceBillingMode implements BillingMode {

    @Override
    public List<RecurringInvoiceItemData> calculateInvoiceItemData(final LocalDate startDate, final LocalDate endDate,
                                                                   final LocalDate targetDate, final DateTimeZone accountTimeZone,
                                                                   final int billingCycleDayLocal, final BillingPeriod billingPeriod) throws InvalidDateSequenceException {
        if (endDate == null) {
            return calculateInvoiceItemData(startDate, targetDate, accountTimeZone, billingCycleDayLocal, billingPeriod);
        }

        if (endDate.isBefore(startDate)) {
            throw new InvalidDateSequenceException();
        }
        if (targetDate.isBefore(startDate)) {
            throw new InvalidDateSequenceException();
        }

        final List<RecurringInvoiceItemData> results = new ArrayList<RecurringInvoiceItemData>();

        // beginning from the start date, find the first billing date
        final LocalDate firstBillingCycleDate = calculateBillingCycleDateOnOrAfter(startDate, accountTimeZone, billingCycleDayLocal);

        // add pro-ration item if needed
        if (firstBillingCycleDate.isAfter(startDate)) {
            final BigDecimal leadingProRationPeriods = calculateProRationBeforeFirstBillingPeriod(startDate, firstBillingCycleDate, billingPeriod);
            if (leadingProRationPeriods != null && leadingProRationPeriods.compareTo(BigDecimal.ZERO) > 0) {
                results.add(new RecurringInvoiceItemData(startDate, firstBillingCycleDate, leadingProRationPeriods));
            }
        }

        // add one item per billing period
        final LocalDate effectiveEndDate = calculateEffectiveEndDate(firstBillingCycleDate, targetDate, endDate, billingPeriod);
        final LocalDate lastBillingCycleDate = calculateLastBillingCycleDateBefore(effectiveEndDate, firstBillingCycleDate, billingCycleDayLocal, billingPeriod);
        final int numberOfWholeBillingPeriods = calculateNumberOfWholeBillingPeriods(firstBillingCycleDate, lastBillingCycleDate, billingPeriod);
        final int numberOfMonthsPerBillingPeriod = billingPeriod.getNumberOfMonths();

        for (int i = 0; i < numberOfWholeBillingPeriods; i++) {
            final LocalDate servicePeriodStartDate;
            if (i == 0) {
                servicePeriodStartDate = startDate;
            } else {
                servicePeriodStartDate = firstBillingCycleDate.plusMonths(i * numberOfMonthsPerBillingPeriod);
            }
            results.add(new RecurringInvoiceItemData(servicePeriodStartDate,
                                                     firstBillingCycleDate.plusMonths((i + 1) * numberOfMonthsPerBillingPeriod), BigDecimal.ONE));
        }

        // check to see if a trailing pro-ration amount is needed
        if (effectiveEndDate.isAfter(lastBillingCycleDate)) {
            final BigDecimal trailingProRationPeriods = calculateProRationAfterLastBillingCycleDate(effectiveEndDate, lastBillingCycleDate, billingPeriod);
            if (trailingProRationPeriods.compareTo(BigDecimal.ZERO) > 0) {
                results.add(new RecurringInvoiceItemData(lastBillingCycleDate, effectiveEndDate, trailingProRationPeriods));
            }
        }
        return results;
    }

    @Override
    public List<RecurringInvoiceItemData> calculateInvoiceItemData(final LocalDate startDate,
                                                                   final LocalDate targetDate,
                                                                   final DateTimeZone accountTimeZone,
                                                                   final int billingCycleDayLocal,
                                                                   final BillingPeriod billingPeriod) throws InvalidDateSequenceException {
        final List<RecurringInvoiceItemData> results = new ArrayList<RecurringInvoiceItemData>();

        if (targetDate.isBefore(startDate)) {
            // since the target date is before the start date of the event, this should result in no items being generated
            throw new InvalidDateSequenceException();
        }

        // beginning from the start date, find the first billing date
        final LocalDate firstBillingCycleDate = calculateBillingCycleDateOnOrAfter(startDate, accountTimeZone, billingCycleDayLocal);

        // add pro-ration item if needed
        if (firstBillingCycleDate.isAfter(startDate)) {
            final BigDecimal leadingProRationPeriods = calculateProRationBeforeFirstBillingPeriod(startDate, firstBillingCycleDate, billingPeriod);
            if (leadingProRationPeriods != null && leadingProRationPeriods.compareTo(BigDecimal.ZERO) > 0) {
                results.add(new RecurringInvoiceItemData(startDate, firstBillingCycleDate, leadingProRationPeriods));
            }
        }

        // add one item per billing period
        final LocalDate effectiveEndDate = calculateEffectiveEndDate(firstBillingCycleDate, targetDate, billingPeriod);
        final LocalDate lastBillingCycleDate = calculateLastBillingCycleDateBefore(effectiveEndDate, firstBillingCycleDate, billingCycleDayLocal, billingPeriod);
        final int numberOfWholeBillingPeriods = calculateNumberOfWholeBillingPeriods(firstBillingCycleDate, lastBillingCycleDate, billingPeriod);
        final int numberOfMonthsPerBillingPeriod = billingPeriod.getNumberOfMonths();

        for (int i = 0; i < numberOfWholeBillingPeriods; i++) {
            results.add(new RecurringInvoiceItemData(firstBillingCycleDate.plusMonths(i * numberOfMonthsPerBillingPeriod),
                                                     firstBillingCycleDate.plusMonths((i + 1) * numberOfMonthsPerBillingPeriod), BigDecimal.ONE));
        }

        // check to see if a trailing pro-ration amount is needed
        if (effectiveEndDate.isAfter(lastBillingCycleDate)) {
            final BigDecimal trailingProRationPeriods = calculateProRationAfterLastBillingCycleDate(effectiveEndDate, lastBillingCycleDate, billingPeriod);
            if (trailingProRationPeriods.compareTo(BigDecimal.ZERO) > 0) {
                results.add(new RecurringInvoiceItemData(lastBillingCycleDate, effectiveEndDate, trailingProRationPeriods));
            }
        }

        return results;
    }
}
