/*
 * Copyright 2010-2012 Ning, Inc.
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

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.Months;
import org.joda.time.MutableDateTime;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.invoice.model.InvoicingConfiguration;

public class InvoiceDateUtils {

    private static final int ROUNDING_METHOD = InvoicingConfiguration.getRoundingMode();
    private static final int NUMBER_OF_DECIMALS = InvoicingConfiguration.getNumberOfDecimals();

    public static BigDecimal calculateProRationBeforeFirstBillingPeriod(final LocalDate startDate, final LocalDate nextBillingCycleDate,
                                                                        final BillingPeriod billingPeriod) {
        final LocalDate previousBillingCycleDate = nextBillingCycleDate.plusMonths(-billingPeriod.getNumberOfMonths());

        final int daysBetween = Days.daysBetween(previousBillingCycleDate, nextBillingCycleDate).getDays();
        if (daysBetween <= 0) {
            return BigDecimal.ZERO;
        }

        final BigDecimal daysInPeriod = new BigDecimal(daysBetween);
        final BigDecimal days = new BigDecimal(Days.daysBetween(startDate, nextBillingCycleDate).getDays());

        return days.divide(daysInPeriod, 2 * NUMBER_OF_DECIMALS, ROUNDING_METHOD);
    }

    public static int calculateNumberOfWholeBillingPeriods(final LocalDate startDate, final LocalDate endDate, final BillingPeriod billingPeriod) {
        final int numberOfMonths = Months.monthsBetween(startDate, endDate).getMonths();
        final int numberOfMonthsInPeriod = billingPeriod.getNumberOfMonths();
        return numberOfMonths / numberOfMonthsInPeriod;
    }

    public static LocalDate calculateLastBillingCycleDateBefore(final LocalDate date, final LocalDate previousBillCycleDate,
                                                                final int billingCycleDay, final BillingPeriod billingPeriod) {
        LocalDate proposedDate = previousBillCycleDate;

        int numberOfPeriods = 0;
        while (!proposedDate.isAfter(date)) {
            proposedDate = previousBillCycleDate.plusMonths(numberOfPeriods * billingPeriod.getNumberOfMonths());
            numberOfPeriods += 1;
        }

        proposedDate = proposedDate.plusMonths(-billingPeriod.getNumberOfMonths());

        if (proposedDate.dayOfMonth().get() < billingCycleDay) {
            final int lastDayOfTheMonth = proposedDate.dayOfMonth().getMaximumValue();
            if (lastDayOfTheMonth < billingCycleDay) {
                return new LocalDate(proposedDate.getYear(), proposedDate.getMonthOfYear(), lastDayOfTheMonth);
            } else {
                return new LocalDate(proposedDate.getYear(), proposedDate.getMonthOfYear(), billingCycleDay);
            }
        } else {
            return proposedDate;
        }
    }

    public static LocalDate calculateEffectiveEndDate(final LocalDate billCycleDate, final LocalDate targetDate,
                                                      final BillingPeriod billingPeriod) {
        if (targetDate.isBefore(billCycleDate)) {
            return billCycleDate;
        }

        final int numberOfMonthsInPeriod = billingPeriod.getNumberOfMonths();
        int numberOfPeriods = 0;
        LocalDate proposedDate = billCycleDate;

        while (!proposedDate.isAfter(targetDate)) {
            proposedDate = billCycleDate.plusMonths(numberOfPeriods * numberOfMonthsInPeriod);
            numberOfPeriods += 1;
        }

        return proposedDate;
    }

    public static LocalDate calculateEffectiveEndDate(final LocalDate billCycleDate, final LocalDate targetDate,
                                                      final LocalDate endDate, final BillingPeriod billingPeriod) {
        if (targetDate.isBefore(endDate)) {
            if (targetDate.isBefore(billCycleDate)) {
                return billCycleDate;
            }

            final int numberOfMonthsInPeriod = billingPeriod.getNumberOfMonths();
            int numberOfPeriods = 0;
            LocalDate proposedDate = billCycleDate;

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

    public static BigDecimal calculateProRationAfterLastBillingCycleDate(final LocalDate endDate, final LocalDate previousBillThroughDate,
                                                                         final BillingPeriod billingPeriod) {
        // Note: assumption is that previousBillThroughDate is correctly aligned with the billing cycle day
        final LocalDate nextBillThroughDate = previousBillThroughDate.plusMonths(billingPeriod.getNumberOfMonths());
        final BigDecimal daysInPeriod = new BigDecimal(Days.daysBetween(previousBillThroughDate, nextBillThroughDate).getDays());

        final BigDecimal days = new BigDecimal(Days.daysBetween(previousBillThroughDate, endDate).getDays());

        return days.divide(daysInPeriod, 2 * NUMBER_OF_DECIMALS, ROUNDING_METHOD);
    }

    public static LocalDate calculateBillingCycleDateOnOrAfter(final LocalDate date, final DateTimeZone accountTimeZone,
                                                               final int billingCycleDay) {
        // We go back to DateTime here. Since the BCD was computed in UTC, use UTC here as well
        final DateTime tmp = new DateTime(date.toDateTimeAtStartOfDay(), DateTimeZone.UTC);
        final DateTime proposedDateTime = calculateBillingCycleDateOnOrAfter(tmp, billingCycleDay);

        final LocalDate proposedLocalDate = new LocalDate(proposedDateTime, accountTimeZone);
        if (proposedLocalDate.isBefore(date)) {
            return proposedLocalDate.plusMonths(1);
        } else {
            return proposedLocalDate;
        }
    }

    public static LocalDate calculateBillingCycleDateAfter(final LocalDate date, final DateTimeZone accountTimeZone,
                                                           final int billingCycleDay) {
        // We go back to DateTime here. Since the BCD was computed in UTC, use UTC here as well
        final DateTime tmp = new DateTime(date.toDateTimeAtStartOfDay(), DateTimeZone.UTC);
        final DateTime proposedDateTime = calculateBillingCycleDateAfter(tmp, billingCycleDay);

        final LocalDate proposedLocalDate = new LocalDate(proposedDateTime, accountTimeZone);
        if (proposedLocalDate.isBefore(date)) {
            return proposedLocalDate.plusMonths(1);
        } else {
            return proposedLocalDate;
        }
    }

    // Note: date has to be in UTC
    public static DateTime calculateBillingCycleDateOnOrAfter(final DateTime date, final int billingCycleDay) {
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

    // Note: date has to be in UTC
    public static DateTime calculateBillingCycleDateAfter(final DateTime date, final int billingCycleDay) {
        DateTime proposedDate = calculateBillingCycleDateOnOrAfter(date, billingCycleDay);
        if (date.compareTo(proposedDate) == 0) {
            proposedDate = proposedDate.plusMonths(1);
        }

        return proposedDate;
    }
}
