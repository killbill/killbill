/*
 * Copyright 2010-2013 Ning, Inc.
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


    /**
     *
     * Called internally to calculate proration or when we recalculate approximate repair amount
     *
     * @param startDate                 start date of the prorated interval
     * @param endDate                   end date of the prorated interval
     * @param previousBillingCycleDate  start date of the period
     * @param nextBillingCycleDate      end date of the period
     * @return
     */
    public static BigDecimal calculateProrationBetweenDates(final LocalDate startDate, final LocalDate endDate, final LocalDate previousBillingCycleDate, final LocalDate nextBillingCycleDate) {
        final int daysBetween = Days.daysBetween(previousBillingCycleDate, nextBillingCycleDate).getDays();
        return calculateProrationBetweenDates(startDate, endDate, daysBetween);
    }

    public static BigDecimal calculateProrationBetweenDates(final LocalDate startDate, final LocalDate endDate, int daysBetween) {
        if (daysBetween <= 0) {
            return BigDecimal.ZERO;
        }

        final BigDecimal daysInPeriod = new BigDecimal(daysBetween);
        final BigDecimal days = new BigDecimal(Days.daysBetween(startDate, endDate).getDays());

        return days.divide(daysInPeriod, 2 * NUMBER_OF_DECIMALS, ROUNDING_METHOD);
    }

    public static BigDecimal calculateProRationBeforeFirstBillingPeriod(final LocalDate startDate, final LocalDate nextBillingCycleDate,
                                                                        final BillingPeriod billingPeriod) {
        final LocalDate previousBillingCycleDate = nextBillingCycleDate.plusMonths(-billingPeriod.getNumberOfMonths());

        return calculateProrationBetweenDates(startDate, nextBillingCycleDate, previousBillingCycleDate, nextBillingCycleDate);
    }

    public static int calculateNumberOfWholeBillingPeriods(final LocalDate startDate, final LocalDate endDate, final BillingPeriod billingPeriod) {
        final int numberOfMonths = Months.monthsBetween(startDate, endDate).getMonths();
        final int numberOfMonthsInPeriod = billingPeriod.getNumberOfMonths();
        return numberOfMonths / numberOfMonthsInPeriod;
    }

    public static BigDecimal calculateProRationAfterLastBillingCycleDate(final LocalDate endDate, final LocalDate previousBillThroughDate,
                                                                         final BillingPeriod billingPeriod) {
        // Note: assumption is that previousBillThroughDate is correctly aligned with the billing cycle day
        final LocalDate nextBillThroughDate = previousBillThroughDate.plusMonths(billingPeriod.getNumberOfMonths());
        return calculateProrationBetweenDates(previousBillThroughDate, endDate, previousBillThroughDate, nextBillThroughDate);
    }
}
