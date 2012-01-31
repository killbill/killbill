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
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Months;

import java.math.BigDecimal;

public class InAdvanceBillingMode extends BillingModeBase {
    private static final int ROUNDING_METHOD = InvoicingConfiguration.getRoundingMethod();
    private static final int NUMBER_OF_DECIMALS = InvoicingConfiguration.getNumberOfDecimals();

    @Override
    public DateTime calculateEffectiveEndDate(final DateTime startDate, final DateTime targetDate, final int billingCycleDay, final BillingPeriod billingPeriod) {
        DateTime firstBillCycleDate = calculateBillingCycleDateOnOrAfter(startDate, billingCycleDay);
        return calculateBillingCycleDateAfter(targetDate, firstBillCycleDate, billingCycleDay, billingPeriod);
    }

    @Override
    public DateTime calculateEffectiveEndDate(final DateTime startDate, final DateTime endDate, final DateTime targetDate, final int billingCycleDay, final BillingPeriod billingPeriod) {
        DateTime firstBillCycleDate = calculateBillingCycleDateOnOrAfter(startDate, billingCycleDay);
        return calculateEffectiveEndDate(firstBillCycleDate, targetDate, endDate, billingPeriod);
    }

    @Override
    protected BigDecimal calculateNumberOfWholeBillingPeriods(final DateTime startDate, final DateTime endDate, final BillingPeriod billingPeriod) {
        int numberOfMonths = Months.monthsBetween(startDate, endDate).getMonths();
        BigDecimal numberOfMonthsInPeriod = new BigDecimal(billingPeriod.getNumberOfMonths());
        return new BigDecimal(numberOfMonths).divide(numberOfMonthsInPeriod, 0, ROUNDING_METHOD);
    }

    @Override
    protected DateTime calculateBillingCycleDateOnOrAfter(final DateTime date, final int billingCycleDay) {
        int lastDayOfMonth = date.dayOfMonth().getMaximumValue();

        DateTime proposedDate;
        if (billingCycleDay > lastDayOfMonth) {
            proposedDate = new DateTime(date.getYear(), date.getMonthOfYear(), lastDayOfMonth,
                                        date.getHourOfDay(), date.getMinuteOfHour(),
                                        date.getSecondOfMinute(), date.getMillisOfSecond());
        } else {
            proposedDate = new DateTime(date.getYear(), date.getMonthOfYear(), billingCycleDay,
                                        date.getHourOfDay(), date.getMinuteOfHour(),
                                        date.getSecondOfMinute(), date.getMillisOfSecond());
        }

        while (proposedDate.isBefore(date)) {
            proposedDate = proposedDate.plusMonths(1);
        }

        return proposedDate;
    }

    protected DateTime calculateBillingCycleDateAfter(final DateTime date, final DateTime billingCycleDate, final int billingCycleDay, final BillingPeriod billingPeriod) {
        DateTime proposedDate = billingCycleDate;

        while (!proposedDate.isAfter(date)) {
            proposedDate = proposedDate.plusMonths(billingPeriod.getNumberOfMonths());

            if (proposedDate.dayOfMonth().get() != billingCycleDay) {
                int lastDayOfMonth = proposedDate.dayOfMonth().getMaximumValue();

                if (lastDayOfMonth < billingCycleDay) {
                    proposedDate = new DateTime(proposedDate.getYear(), proposedDate.getMonthOfYear(), lastDayOfMonth,
                                                proposedDate.getHourOfDay(), proposedDate.getMinuteOfHour(),
                                                proposedDate.getSecondOfMinute(), proposedDate.getMillisOfSecond());
                } else {
                    proposedDate = new DateTime(proposedDate.getYear(), proposedDate.getMonthOfYear(), billingCycleDay,
                                                proposedDate.getHourOfDay(), proposedDate.getMinuteOfHour(),
                                                proposedDate.getSecondOfMinute(), proposedDate.getMillisOfSecond());
                }
            }
        }

        return proposedDate;
    }

    @Override
    protected DateTime calculateLastBillingCycleDateBefore(final DateTime date, final DateTime previousBillCycleDate, final int billingCycleDay, final BillingPeriod billingPeriod) {
        DateTime proposedDate = previousBillCycleDate;
        proposedDate = proposedDate.plusMonths(billingPeriod.getNumberOfMonths());

        if (!proposedDate.isBefore(date)) {return previousBillCycleDate;}

        while (proposedDate.isBefore(date)) {
            proposedDate = proposedDate.plusMonths(billingPeriod.getNumberOfMonths());
        }

        proposedDate = proposedDate.plusMonths(-billingPeriod.getNumberOfMonths());

        if (proposedDate.dayOfMonth().get() < billingCycleDay) {
            int lastDayOfTheMonth = proposedDate.dayOfMonth().getMaximumValue();
            if (lastDayOfTheMonth < billingCycleDay) {
                return new DateTime(proposedDate.getYear(), proposedDate.getMonthOfYear(), lastDayOfTheMonth,
                                    proposedDate.getHourOfDay(), proposedDate.getMinuteOfHour(),
                                    proposedDate.getSecondOfMinute(), proposedDate.getMillisOfSecond());
            } else {
                return new DateTime(proposedDate.getYear(), proposedDate.getMonthOfYear(), billingCycleDay,
                                    proposedDate.getHourOfDay(), proposedDate.getMinuteOfHour(),
                                    proposedDate.getSecondOfMinute(), proposedDate.getMillisOfSecond());
            }
        } else {
            return proposedDate;
        }
    }

    @Override
    protected BigDecimal calculateProRationBeforeFirstBillingPeriod(final DateTime startDate, final int billingCycleDay, final BillingPeriod billingPeriod) {
        DateTime nextBillingCycleDate = calculateBillingCycleDateOnOrAfter(startDate, billingCycleDay);
        DateTime previousBillingCycleDate = nextBillingCycleDate.plusMonths(-billingPeriod.getNumberOfMonths());

        int daysBetween = Days.daysBetween(previousBillingCycleDate, nextBillingCycleDate).getDays();
        if (daysBetween == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal daysInPeriod = new BigDecimal(daysBetween);
        BigDecimal days = new BigDecimal(Days.daysBetween(startDate, nextBillingCycleDate).getDays());

        return days.divide(daysInPeriod, NUMBER_OF_DECIMALS, ROUNDING_METHOD);
    }

    @Override
    protected BigDecimal calculateProRationAfterLastBillingCycleDate(final DateTime endDate, final DateTime previousBillThroughDate, final BillingPeriod billingPeriod) {
        // note: assumption is that previousBillThroughDate is correctly aligned with the billing cycle day
        DateTime nextBillThroughDate = previousBillThroughDate.plusMonths(billingPeriod.getNumberOfMonths());
        BigDecimal daysInPeriod = new BigDecimal(Days.daysBetween(previousBillThroughDate, nextBillThroughDate).getDays());

        BigDecimal days = new BigDecimal(Days.daysBetween(previousBillThroughDate, endDate).getDays());

        return days.divide(daysInPeriod, NUMBER_OF_DECIMALS, ROUNDING_METHOD);
    }

    @Override
    protected DateTime calculateEffectiveEndDate(DateTime billCycleDate, DateTime targetDate, DateTime endDate, BillingPeriod billingPeriod) {
        if (targetDate.isBefore(endDate)) {
            if (targetDate.isBefore(billCycleDate)) {
                return billCycleDate;
            }

            int numberOfMonthsInPeriod = billingPeriod.getNumberOfMonths();
            DateTime startOfPeriod = billCycleDate;
            DateTime startOfNextPeriod = billCycleDate.plusMonths(numberOfMonthsInPeriod);

            while (isNotBetween(targetDate, startOfPeriod, startOfNextPeriod)) {
                startOfPeriod = startOfNextPeriod;
                startOfNextPeriod = startOfPeriod.plusMonths(numberOfMonthsInPeriod);
            }

            // the current period includes the target date
            // check to see whether the end date truncates the period
            if (endDate.isBefore(startOfNextPeriod)) {
                return endDate;
            } else {
                return startOfNextPeriod;
            }
        } else {
            return endDate;
        }
    }
}