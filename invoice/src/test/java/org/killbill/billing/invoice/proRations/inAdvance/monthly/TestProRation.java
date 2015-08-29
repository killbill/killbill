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

package org.killbill.billing.invoice.proRations.inAdvance.monthly;

import java.math.BigDecimal;

import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.invoice.model.InvalidDateSequenceException;
import org.killbill.billing.invoice.proRations.inAdvance.ProRationInAdvanceTestBase;
import org.killbill.billing.util.currency.KillBillMoney;

import static org.killbill.billing.invoice.TestInvoiceHelper.EIGHT;
import static org.killbill.billing.invoice.TestInvoiceHelper.FIVE;
import static org.killbill.billing.invoice.TestInvoiceHelper.FOURTEEN;
import static org.killbill.billing.invoice.TestInvoiceHelper.ONE;
import static org.killbill.billing.invoice.TestInvoiceHelper.ONE_AND_A_HALF;
import static org.killbill.billing.invoice.TestInvoiceHelper.ONE_HALF;
import static org.killbill.billing.invoice.TestInvoiceHelper.SEVEN;
import static org.killbill.billing.invoice.TestInvoiceHelper.THIRTEEN;
import static org.killbill.billing.invoice.TestInvoiceHelper.THIRTY_ONE;
import static org.killbill.billing.invoice.TestInvoiceHelper.THREE;
import static org.killbill.billing.invoice.TestInvoiceHelper.TWENTY_EIGHT;
import static org.killbill.billing.invoice.TestInvoiceHelper.TWENTY_NINE;
import static org.killbill.billing.invoice.TestInvoiceHelper.TWO;

public class TestProRation extends ProRationInAdvanceTestBase {

    @Override
    protected BillingPeriod getBillingPeriod() {
        return BillingPeriod.MONTHLY;
    }

    @Test(groups = "fast")
    public void testSinglePlan_WithPhaseChange() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 10);
        final LocalDate phaseChangeDate = invoiceUtil.buildDate(2011, 2, 24);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 3, 6);

        testCalculateNumberOfBillingCycles(startDate, phaseChangeDate, targetDate, 10, ONE_HALF);
        testCalculateNumberOfBillingCycles(phaseChangeDate, targetDate, 10, ONE_HALF);
    }

    @Test(groups = "fast")
    public void testSinglePlan_WithPhaseChange_BeforeBillCycleDay() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 3);
        final LocalDate phaseChangeDate = invoiceUtil.buildDate(2011, 2, 17);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 3, 1);

        testCalculateNumberOfBillingCycles(startDate, phaseChangeDate, targetDate, 3, ONE_HALF);
        testCalculateNumberOfBillingCycles(phaseChangeDate, targetDate, 3, ONE_HALF);
    }

    @Test(groups = "fast")
    public void testSinglePlan_WithPhaseChange_OnBillCycleDay() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 3);
        final LocalDate phaseChangeDate = invoiceUtil.buildDate(2011, 2, 17);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 3, 3);

        testCalculateNumberOfBillingCycles(startDate, phaseChangeDate, targetDate, 3, ONE_HALF);
        testCalculateNumberOfBillingCycles(phaseChangeDate, targetDate, 3, ONE_AND_A_HALF);
    }

    @Test(groups = "fast")
    public void testSinglePlan_WithPhaseChange_AfterBillCycleDay() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 3);
        final LocalDate phaseChangeDate = invoiceUtil.buildDate(2011, 2, 17);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 3, 4);

        testCalculateNumberOfBillingCycles(startDate, phaseChangeDate, targetDate, 3, ONE_HALF);
        testCalculateNumberOfBillingCycles(phaseChangeDate, targetDate, 3, ONE_AND_A_HALF);
    }

    @Test(groups = "fast")
    public void testPlanChange_WithChangeOfBillCycleDayToLaterDay() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 1);
        final LocalDate planChangeDate = invoiceUtil.buildDate(2011, 2, 15);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 3, 1);

        testCalculateNumberOfBillingCycles(startDate, planChangeDate, targetDate, 1, ONE_HALF);
        testCalculateNumberOfBillingCycles(planChangeDate, targetDate, 15, ONE);
    }

    @Test(groups = "fast")
    public void testPlanChange_WithChangeOfBillCycleDayToEarlierDay() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 20);
        final LocalDate planChangeDate = invoiceUtil.buildDate(2011, 3, 6);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 3, 9);

        testCalculateNumberOfBillingCycles(startDate, planChangeDate, targetDate, 20, ONE_HALF);
        testCalculateNumberOfBillingCycles(planChangeDate, targetDate, 6, ONE);
    }

    @Test(groups = "fast")
    public void testSinglePlan_CrossingYearBoundary() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2010, 12, 15);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 1, 16);

        testCalculateNumberOfBillingCycles(startDate, targetDate, 15, TWO);
    }

    @Test(groups = "fast")
    public void testSinglePlan_LeapYear_StartingMidFebruary() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2012, 2, 15);
        final LocalDate targetDate = invoiceUtil.buildDate(2012, 3, 15);

        testCalculateNumberOfBillingCycles(startDate, targetDate, 15, TWO);
    }

    @Test(groups = "fast")
    public void testSinglePlan_LeapYear_StartingBeforeFebruary() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2012, 1, 15);
        final LocalDate targetDate = invoiceUtil.buildDate(2012, 2, 3);

        testCalculateNumberOfBillingCycles(startDate, targetDate, 15, ONE);
    }

    @Test(groups = "fast")
    public void testSinglePlan_LeapYear_IncludingAllOfFebruary() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2012, 1, 30);
        final LocalDate targetDate = invoiceUtil.buildDate(2012, 3, 1);

        testCalculateNumberOfBillingCycles(startDate, targetDate, 30, TWO);
    }

    @Test(groups = "fast")
    public void testSinglePlan_ChangeBCDTo31() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 1);
        final LocalDate planChangeDate = invoiceUtil.buildDate(2011, 2, 14);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 3, 1);

        BigDecimal expectedValue;

        expectedValue = THIRTEEN.divide(TWENTY_EIGHT, KillBillMoney.ROUNDING_METHOD);
        testCalculateNumberOfBillingCycles(startDate, planChangeDate, targetDate, 1, expectedValue);

        expectedValue = ONE.add(FOURTEEN.divide(THIRTY_ONE, KillBillMoney.ROUNDING_METHOD));
        testCalculateNumberOfBillingCycles(planChangeDate, targetDate, 31, expectedValue);
    }

    @Test(groups = "fast")
    public void testSinglePlan_ChangeBCD() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 1);
        final LocalDate planChangeDate = invoiceUtil.buildDate(2011, 2, 14);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 3, 1);

        BigDecimal expectedValue;

        expectedValue = THIRTEEN.divide(TWENTY_EIGHT, KillBillMoney.ROUNDING_METHOD);
        testCalculateNumberOfBillingCycles(startDate, planChangeDate, targetDate, 1, expectedValue);

        expectedValue = ONE.add(THIRTEEN.divide(THIRTY_ONE, KillBillMoney.ROUNDING_METHOD));
        testCalculateNumberOfBillingCycles(planChangeDate, targetDate, 27, expectedValue);
    }

    @Test(groups = "fast")
    public void testSinglePlan_LeapYearFebruaryProRation() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2012, 2, 1);
        final LocalDate endDate = invoiceUtil.buildDate(2012, 2, 15);
        final LocalDate targetDate = invoiceUtil.buildDate(2012, 2, 19);

        final BigDecimal expectedValue;
        expectedValue = FOURTEEN.divide(TWENTY_NINE, KillBillMoney.ROUNDING_METHOD);

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 1, expectedValue);
    }

    @Test(groups = "fast")
    public void testPlanChange_BeforeBillingDay() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 7);
        final LocalDate changeDate = invoiceUtil.buildDate(2011, 2, 15);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 4, 21);

        final BigDecimal expectedValue;

        expectedValue = EIGHT.divide(TWENTY_EIGHT, KillBillMoney.ROUNDING_METHOD);
        testCalculateNumberOfBillingCycles(startDate, changeDate, targetDate, 7, expectedValue);

        testCalculateNumberOfBillingCycles(changeDate, targetDate, 15, THREE);
    }

    @Test(groups = "fast")
    public void testPlanChange_OnBillingDay() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 7);
        final LocalDate changeDate = invoiceUtil.buildDate(2011, 3, 7);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 4, 21);

        testCalculateNumberOfBillingCycles(startDate, changeDate, targetDate, 7, ONE);

        final BigDecimal expectedValue;
        expectedValue = EIGHT.divide(TWENTY_EIGHT, KillBillMoney.ROUNDING_METHOD).add(TWO);
        testCalculateNumberOfBillingCycles(changeDate, targetDate, 15, expectedValue);
    }

    @Test(groups = "fast")
    public void testPlanChange_AfterBillingDay() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 7);
        final LocalDate changeDate = invoiceUtil.buildDate(2011, 3, 10);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 4, 21);

        BigDecimal expectedValue;

        expectedValue = BigDecimal.ONE.add(THREE.divide(THIRTY_ONE, KillBillMoney.ROUNDING_METHOD));
        testCalculateNumberOfBillingCycles(startDate, changeDate, targetDate, 7, expectedValue);

        expectedValue = FIVE.divide(TWENTY_EIGHT, KillBillMoney.ROUNDING_METHOD).add(TWO);
        testCalculateNumberOfBillingCycles(changeDate, targetDate, 15, expectedValue);
    }

    @Test(groups = "fast")
    public void testPlanChange_DoubleProRation() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 31);
        final LocalDate planChangeDate = invoiceUtil.buildDate(2011, 3, 10);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 4, 21);

        BigDecimal expectedValue;
        expectedValue = SEVEN.divide(THIRTY_ONE, KillBillMoney.ROUNDING_METHOD);
        expectedValue = expectedValue.add(ONE);
        expectedValue = expectedValue.add(THREE.divide(THIRTY_ONE, KillBillMoney.ROUNDING_METHOD));
        testCalculateNumberOfBillingCycles(startDate, planChangeDate, targetDate, 7, expectedValue);

        expectedValue = FIVE.divide(TWENTY_EIGHT, KillBillMoney.ROUNDING_METHOD).add(TWO);
        testCalculateNumberOfBillingCycles(planChangeDate, targetDate, 15, expectedValue);
    }

    @Test(groups = "fast")
    public void testStartTargetEnd() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2010, 12, 15);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 3, 15);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 3, 17);

        final BigDecimal expectedValue = THREE.add(TWO.divide(THIRTY_ONE, KillBillMoney.ROUNDING_METHOD));
        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 15, expectedValue);
    }
}
