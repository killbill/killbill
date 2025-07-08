/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.invoice.proRations.inAdvance;

import static org.killbill.billing.invoice.TestInvoiceHelper.*;

import java.math.BigDecimal;

import org.joda.time.LocalDate;
import org.killbill.billing.util.bcd.BillCycleDayCalculator;
import org.testng.annotations.Test;

import org.killbill.billing.invoice.model.InvalidDateSequenceException;
import org.killbill.billing.util.currency.KillBillMoney;

public abstract class GenericProRationTestBase extends ProRationInAdvanceTestBase {

    /**
     * used for testing cancellation in less than a single billing period
     *
     * @return BigDecimal the number of days in the billing period beginning 2011/1/1
     */
    protected abstract BigDecimal getDaysInTestPeriod();

    @Test(groups = "fast")
    public void testSinglePlan_OnStartDate() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 15);

        testCalculateNumberOfBillingCycles(startDate, startDate, 15, ONE);
    }

    @Test(groups = "fast")
    public void testSinglePlan_LessThanOnePeriod() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 15);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 3, 1);

        testCalculateNumberOfBillingCycles(startDate, targetDate, 15, ONE);
    }

    @Test(groups = "fast")
    public void testSinglePlan_OnePeriodLessADayAfterStart() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 15);
        final LocalDate targetDate = startDate.plus(getBillingPeriod().getPeriod()).plusDays(-1);

        testCalculateNumberOfBillingCycles(startDate, targetDate, 15, ONE);
    }

    @Test(groups = "fast")
    public void testSinglePlan_ExactlyOnePeriodAfterStart() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 15);
        final LocalDate targetDate = startDate.plus(getBillingPeriod().getPeriod());

        testCalculateNumberOfBillingCycles(startDate, targetDate, 15, TWO);
    }

    @Test(groups = "fast")
    public void testSinglePlan_SlightlyMoreThanOnePeriodAfterStart() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 15);
        final LocalDate targetDate = startDate.plus(getBillingPeriod().getPeriod()).plusDays(1);

        testCalculateNumberOfBillingCycles(startDate, targetDate, 15, TWO);
    }

    @Test(groups = "fast")
    public void testSinglePlan_CrossingYearBoundary() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 12, 15);
        final LocalDate oneCycleLater = startDate.plus(getBillingPeriod().getPeriod());

        // test just before the billing cycle day
        testCalculateNumberOfBillingCycles(startDate, oneCycleLater.plusDays(-1), 15, ONE);

        // test on the billing cycle day
        testCalculateNumberOfBillingCycles(startDate, oneCycleLater, 15, TWO);

        // test just after the billing cycle day
        testCalculateNumberOfBillingCycles(startDate, oneCycleLater.plusDays(1), 15, TWO);
    }

    @Test(groups = "fast")
    public void testSinglePlan_StartingMidFebruary() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 15);
        final LocalDate targetDate = startDate.plus(getBillingPeriod().getPeriod());

        testCalculateNumberOfBillingCycles(startDate, targetDate, 15, TWO);
    }

    @Test(groups = "fast")
    public void testSinglePlan_StartingMidFebruaryOfLeapYear() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2012, 2, 15);
        final LocalDate targetDate = startDate.plus(getBillingPeriod().getPeriod());

        testCalculateNumberOfBillingCycles(startDate, targetDate, 15, TWO);
    }

    @Test(groups = "fast")
    public void testSinglePlan_MovingForwardThroughTime() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 31);
        BigDecimal expectedValue = ONE;

        for (int i = 1; i <= 12; i++) {
            LocalDate oneCycleLater = startDate;
            for (int j = 0; j < i; j++) {
                oneCycleLater = oneCycleLater.plus(getBillingPeriod().getPeriod());
                // The correct alignment needs to take into consideration the BCD - i.e. if we start on 31 and end up on a month with 31 days, we should end on the 31st
                oneCycleLater = BillCycleDayCalculator.alignProposedBillCycleDate(oneCycleLater, 31, getBillingPeriod());
            }
            // test just before the billing cycle day
            testCalculateNumberOfBillingCycles(startDate, oneCycleLater.plusDays(-1), 31, expectedValue);

            expectedValue = expectedValue.add(ONE);

            // test on the billing cycle day
            testCalculateNumberOfBillingCycles(startDate, oneCycleLater, 31, expectedValue);

            // test just after the billing cycle day
            testCalculateNumberOfBillingCycles(startDate, oneCycleLater.plusDays(1), 31, expectedValue);
        }
    }

    // tests for cancellation in less than one period, beginning Jan 1
    @Test(groups = "fast")
    public void testCancelledBeforeOnePeriod_TargetDateInStartDate() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 1, 1);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 1, 15);

        final BigDecimal expectedValue = FOURTEEN.divide(getDaysInTestPeriod(), KillBillMoney.ROUNDING_METHOD);
        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 1, expectedValue);
    }

    @Test(groups = "fast")
    public void testCancelledBeforeOnePeriod_TargetDateInSubscriptionPeriod() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 1, 7);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 1, 15);

        final BigDecimal expectedValue = FOURTEEN.divide(getDaysInTestPeriod(), KillBillMoney.ROUNDING_METHOD);
        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 1, expectedValue);
    }

    @Test(groups = "fast")
    public void testCancelledBeforeOnePeriod_TargetDateOnEndDate() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 1, 15);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 1, 15);

        final BigDecimal expectedValue = FOURTEEN.divide(getDaysInTestPeriod(), KillBillMoney.ROUNDING_METHOD);
        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 1, expectedValue);
    }

    @Test(groups = "fast")
    public void testCancelledBeforeOnePeriod_TargetDateAfterEndDateButInFirstBillingPeriod() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 1, 17);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 1, 15);

        final BigDecimal expectedValue = FOURTEEN.divide(getDaysInTestPeriod(), KillBillMoney.ROUNDING_METHOD);
        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 1, expectedValue);
    }

    @Test(groups = "fast")
    public void testCancelledBeforeOnePeriod_TargetDateAtEndOfFirstBillingPeriod() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 2, 1);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 1, 15);

        final BigDecimal expectedValue = FOURTEEN.divide(getDaysInTestPeriod(), KillBillMoney.ROUNDING_METHOD);
        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 1, expectedValue);
    }

    @Test(groups = "fast")
    public void testCancelledBeforeOnePeriod_TargetDateAfterFirstBillingPeriod() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 4, 5);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 1, 15);

        final BigDecimal expectedValue = FOURTEEN.divide(getDaysInTestPeriod(), KillBillMoney.ROUNDING_METHOD);
        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 1, expectedValue);
    }
}
