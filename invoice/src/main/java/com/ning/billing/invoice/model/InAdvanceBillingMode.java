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

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Months;
import org.joda.time.MutableDateTime;

import com.ning.billing.catalog.api.BillingPeriod;

public class InAdvanceBillingMode implements BillingMode {
    private static final int ROUNDING_METHOD = InvoicingConfiguration.getRoundingMode();
    private static final int NUMBER_OF_DECIMALS = InvoicingConfiguration.getNumberOfDecimals();

    @Override
    public List<RecurringInvoiceItemData> calculateInvoiceItemData(final DateTime startDate, final DateTime endDate,
                                                                   final DateTime targetDate, final int billingCycleDay,
                                                                   final BillingPeriod billingPeriod) throws InvalidDateSequenceException {
        if (endDate == null) {
            return calculateInvoiceItemData(startDate, targetDate, billingCycleDay, billingPeriod);
        }

        if (endDate.isBefore(startDate)) {
            throw new InvalidDateSequenceException();
        }
        if (targetDate.isBefore(startDate)) {
            throw new InvalidDateSequenceException();
        }

        final List<RecurringInvoiceItemData> results = new ArrayList<RecurringInvoiceItemData>();

        // beginning from the start date, find the first billing date
        final DateTime firstBillingCycleDate = calculateBillingCycleDateOnOrAfter(startDate, billingCycleDay);

        // add pro-ration item if needed
        if (firstBillingCycleDate.isAfter(startDate)) {
            final BigDecimal leadingProRationPeriods = calculateProRationBeforeFirstBillingPeriod(startDate, firstBillingCycleDate, billingPeriod);
            if (leadingProRationPeriods != null && leadingProRationPeriods.compareTo(BigDecimal.ZERO) > 0) {
                results.add(new RecurringInvoiceItemData(startDate, firstBillingCycleDate, leadingProRationPeriods));
            }
        }

        // add one item per billing period
        final DateTime effectiveEndDate = calculateEffectiveEndDate(firstBillingCycleDate, targetDate, endDate, billingPeriod);
        final DateTime lastBillingCycleDate = calculateLastBillingCycleDateBefore(effectiveEndDate, firstBillingCycleDate, billingCycleDay, billingPeriod);
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

    @Override
    public List<RecurringInvoiceItemData> calculateInvoiceItemData(final DateTime startDate,
                                                                   final DateTime targetDate, final int billingCycleDay,
                                                                   final BillingPeriod billingPeriod) throws InvalidDateSequenceException {
        final List<RecurringInvoiceItemData> results = new ArrayList<RecurringInvoiceItemData>();

        if (targetDate.isBefore(startDate)) {
            // since the target date is before the start date of the event, this should result in no items being generated
            throw new InvalidDateSequenceException();
        }

        // beginning from the start date, find the first billing date
        final DateTime firstBillingCycleDate = calculateBillingCycleDateOnOrAfter(startDate, billingCycleDay);

        // add pro-ration item if needed
        if (firstBillingCycleDate.isAfter(startDate)) {
            final BigDecimal leadingProRationPeriods = calculateProRationBeforeFirstBillingPeriod(startDate, firstBillingCycleDate, billingPeriod);
            if (leadingProRationPeriods != null && leadingProRationPeriods.compareTo(BigDecimal.ZERO) > 0) {
                results.add(new RecurringInvoiceItemData(startDate, firstBillingCycleDate, leadingProRationPeriods));
            }
        }

        // add one item per billing period
        final DateTime effectiveEndDate = calculateEffectiveEndDate(firstBillingCycleDate, targetDate, billingPeriod);
        final DateTime lastBillingCycleDate = calculateLastBillingCycleDateBefore(effectiveEndDate, firstBillingCycleDate, billingCycleDay, billingPeriod);
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

    private DateTime calculateBillingCycleDateOnOrAfter(final DateTime date, final int billingCycleDay) {
        final int lastDayOfMonth = date.dayOfMonth().getMaximumValue();

        final MutableDateTime tmp = date.toMutableDateTime();
        if (billingCycleDay > lastDayOfMonth) {
            tmp.setDayOfMonth(lastDayOfMonth);
        } else {
            tmp.setDayOfMonth(billingCycleDay);
        }
        DateTime proposedDate = tmp.toDateTime();

        while (proposedDate.isBefore(date)) {
            proposedDate = proposedDate.plusMonths(1);
        }
        return proposedDate;
    }

    private BigDecimal calculateProRationBeforeFirstBillingPeriod(final DateTime startDate, final DateTime nextBillingCycleDate, final BillingPeriod billingPeriod) {
        final DateTime previousBillingCycleDate = nextBillingCycleDate.plusMonths(-billingPeriod.getNumberOfMonths());

        final int daysBetween = Days.daysBetween(previousBillingCycleDate, nextBillingCycleDate).getDays();
        if (daysBetween <= 0) {
            return BigDecimal.ZERO;
        }

        final BigDecimal daysInPeriod = new BigDecimal(daysBetween);
        final BigDecimal days = new BigDecimal(Days.daysBetween(startDate, nextBillingCycleDate).getDays());

        return days.divide(daysInPeriod, 2 * NUMBER_OF_DECIMALS, ROUNDING_METHOD);
    }

    private int calculateNumberOfWholeBillingPeriods(final DateTime startDate, final DateTime endDate, final BillingPeriod billingPeriod) {
        final int numberOfMonths = Months.monthsBetween(startDate, endDate).getMonths();
        final int numberOfMonthsInPeriod = billingPeriod.getNumberOfMonths();
        return numberOfMonths / numberOfMonthsInPeriod;
    }

    private DateTime calculateEffectiveEndDate(final DateTime billCycleDate, final DateTime targetDate, final DateTime endDate, final BillingPeriod billingPeriod) {
        if (targetDate.isBefore(endDate)) {
            if (targetDate.isBefore(billCycleDate)) {
                return billCycleDate;
            }

            final int numberOfMonthsInPeriod = billingPeriod.getNumberOfMonths();
            int numberOfPeriods = 0;
            DateTime proposedDate = billCycleDate;

            while (!proposedDate.isAfter(targetDate)) {
                proposedDate = billCycleDate.plusMonths(numberOfPeriods * numberOfMonthsInPeriod);
                numberOfPeriods += 1;
            }

            // the current period includes the target date
            // check to see whether the end date truncates the period
            if (endDate.isBefore(proposedDate)) {
                return endDate;
            } else {
                return proposedDate;
            }
        } else {
            return endDate;
        }
    }

    private DateTime calculateEffectiveEndDate(final DateTime billCycleDate, final DateTime targetDate, final BillingPeriod billingPeriod) {
        if (targetDate.isBefore(billCycleDate)) {
            return billCycleDate;
        }

        final int numberOfMonthsInPeriod = billingPeriod.getNumberOfMonths();
        int numberOfPeriods = 0;
        DateTime proposedDate = billCycleDate;

        while (!proposedDate.isAfter(targetDate)) {
            proposedDate = billCycleDate.plusMonths(numberOfPeriods * numberOfMonthsInPeriod);
            numberOfPeriods += 1;
        }

        return proposedDate;
    }

    private DateTime calculateLastBillingCycleDateBefore(final DateTime date, final DateTime previousBillCycleDate, final int billingCycleDay, final BillingPeriod billingPeriod) {
        DateTime proposedDate = previousBillCycleDate;

        int numberOfPeriods = 0;
        while (!proposedDate.isAfter(date)) {
            proposedDate = previousBillCycleDate.plusMonths(numberOfPeriods * billingPeriod.getNumberOfMonths());
            numberOfPeriods += 1;
        }

        proposedDate = proposedDate.plusMonths(-billingPeriod.getNumberOfMonths());

        if (proposedDate.dayOfMonth().get() < billingCycleDay) {
            final int lastDayOfTheMonth = proposedDate.dayOfMonth().getMaximumValue();
            if (lastDayOfTheMonth < billingCycleDay) {
                return new MutableDateTime(proposedDate).dayOfMonth().set(lastDayOfTheMonth).toDateTime();
            } else {
                return new MutableDateTime(proposedDate).dayOfMonth().set(billingCycleDay).toDateTime();
            }
        } else {
            return proposedDate;
        }
    }

    private BigDecimal calculateProRationAfterLastBillingCycleDate(final DateTime endDate, final DateTime previousBillThroughDate, final BillingPeriod billingPeriod) {
        // note: assumption is that previousBillThroughDate is correctly aligned with the billing cycle day
        final DateTime nextBillThroughDate = previousBillThroughDate.plusMonths(billingPeriod.getNumberOfMonths());
        final BigDecimal daysInPeriod = new BigDecimal(Days.daysBetween(previousBillThroughDate, nextBillThroughDate).getDays());

        final BigDecimal days = new BigDecimal(Days.daysBetween(previousBillThroughDate, endDate).getDays());

        return days.divide(daysInPeriod, 2 * NUMBER_OF_DECIMALS, ROUNDING_METHOD);
    }
}
