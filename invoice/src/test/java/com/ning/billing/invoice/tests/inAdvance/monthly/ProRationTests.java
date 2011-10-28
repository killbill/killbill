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

package com.ning.billing.invoice.tests.inAdvance.monthly;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.invoice.model.InvalidDateSequenceException;
import com.ning.billing.invoice.tests.inAdvance.ProRationInAdvanceTestBase;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import java.math.BigDecimal;

@Test(groups = {"invoicing", "proRation"})
public class ProRationTests extends ProRationInAdvanceTestBase {
    @Override
    protected BillingPeriod getBillingPeriod() {
        return BillingPeriod.MONTHLY;
    }

    @Test
    public void testSinglePlan_WithPhaseChange() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2011, 2, 10);
        DateTime phaseChangeDate = buildDateTime(2011, 2, 24);
        DateTime targetDate = buildDateTime(2011, 3, 6);

        testCalculateNumberOfBillingCycles(startDate, phaseChangeDate, targetDate, 10, ONE_HALF);
        testCalculateNumberOfBillingCycles(phaseChangeDate, targetDate, 10, ONE_HALF);
    }

    @Test
    public void testSinglePlan_WithPhaseChange_BeforeBillCycleDay() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2011, 2, 3);
        DateTime phaseChangeDate = buildDateTime(2011, 2, 17);
        DateTime targetDate = buildDateTime(2011, 3, 1);

        testCalculateNumberOfBillingCycles(startDate, phaseChangeDate, targetDate, 3, ONE_HALF);
        testCalculateNumberOfBillingCycles(phaseChangeDate, targetDate, 3, ONE_HALF);
    }

    @Test
    public void testSinglePlan_WithPhaseChange_OnBillCycleDay() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2011, 2, 3);
        DateTime phaseChangeDate = buildDateTime(2011, 2, 17);
        DateTime targetDate = buildDateTime(2011, 3, 3);

        testCalculateNumberOfBillingCycles(startDate, phaseChangeDate, targetDate, 3, ONE_HALF);
        testCalculateNumberOfBillingCycles(phaseChangeDate, targetDate, 3, ONE_AND_A_HALF);
    }

    @Test
    public void testSinglePlan_WithPhaseChange_AfterBillCycleDay() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2011, 2, 3);
        DateTime phaseChangeDate = buildDateTime(2011, 2, 17);
        DateTime targetDate = buildDateTime(2011, 3, 4);

        testCalculateNumberOfBillingCycles(startDate, phaseChangeDate, targetDate, 3, ONE_HALF);
        testCalculateNumberOfBillingCycles(phaseChangeDate, targetDate, 3, ONE_AND_A_HALF);
    }

    @Test
    public void testPlanChange_WithChangeOfBillCycleDayToLaterDay() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2011, 2, 1);
        DateTime planChangeDate = buildDateTime(2011, 2, 15);
        DateTime targetDate = buildDateTime(2011, 3, 1);

        testCalculateNumberOfBillingCycles(startDate, planChangeDate, targetDate, 1, ONE_HALF);
        testCalculateNumberOfBillingCycles(planChangeDate, targetDate, 15, ONE);
    }

    @Test
    public void testPlanChange_WithChangeOfBillCycleDayToEarlierDay() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2011, 2, 20);
        DateTime planChangeDate = buildDateTime(2011, 3, 6);
        DateTime targetDate = buildDateTime(2011, 3, 9);

        testCalculateNumberOfBillingCycles(startDate, planChangeDate, targetDate, 20, ONE_HALF);
        testCalculateNumberOfBillingCycles(planChangeDate, targetDate, 6, ONE);
    }

    @Test
    public void testSinglePlan_CrossingYearBoundary() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2010, 12, 15);
        DateTime targetDate = buildDateTime(2011, 1, 16);

        testCalculateNumberOfBillingCycles(startDate, targetDate, 15, TWO);
    }

    @Test
    public void testSinglePlan_LeapYear_StartingMidFebruary() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2012, 2, 15);
        DateTime targetDate = buildDateTime(2012, 3, 15);

        testCalculateNumberOfBillingCycles(startDate, targetDate, 15, TWO);
    }

    @Test
    public void testSinglePlan_LeapYear_StartingBeforeFebruary() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2012, 1, 15);
        DateTime targetDate = buildDateTime(2012, 2, 3);

        testCalculateNumberOfBillingCycles(startDate, targetDate, 15, ONE);
    }

    @Test
    public void testSinglePlan_LeapYear_IncludingAllOfFebruary() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2012, 1, 30);
        DateTime targetDate = buildDateTime(2012, 3, 1);

        testCalculateNumberOfBillingCycles(startDate, targetDate, 30, TWO);
    }

    @Test
    public void testSinglePlan_ChangeBCDTo31() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2011, 2, 1);
        DateTime planChangeDate = buildDateTime(2011, 2, 14);
        DateTime targetDate = buildDateTime(2011, 3, 1);

        BigDecimal expectedValue;

        expectedValue = THIRTEEN.divide(TWENTY_EIGHT, NUMBER_OF_DECIMALS, ROUNDING_METHOD);
        testCalculateNumberOfBillingCycles(startDate, planChangeDate, targetDate, 1, expectedValue);

        expectedValue = ONE.add(FOURTEEN.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD));
        testCalculateNumberOfBillingCycles(planChangeDate, targetDate, 31, expectedValue);
    }

    @Test
    public void testSinglePlan_ChangeBCD() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2011, 2, 1);
        DateTime planChangeDate = buildDateTime(2011, 2, 14);
        DateTime targetDate = buildDateTime(2011, 3, 1);

        BigDecimal expectedValue;

        expectedValue = THIRTEEN.divide(TWENTY_EIGHT, NUMBER_OF_DECIMALS, ROUNDING_METHOD);
        testCalculateNumberOfBillingCycles(startDate, planChangeDate, targetDate, 1, expectedValue);

        expectedValue = ONE.add(THIRTEEN.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD));
        testCalculateNumberOfBillingCycles(planChangeDate, targetDate, 27, expectedValue);
    }

    @Test
    public void testSinglePlan_LeapYearFebruaryProRation() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2012, 2, 1);
        DateTime endDate = buildDateTime(2012, 2, 15);
        DateTime targetDate = buildDateTime(2012, 2, 19);

        BigDecimal expectedValue;
        expectedValue = FOURTEEN.divide(TWENTY_NINE, NUMBER_OF_DECIMALS, ROUNDING_METHOD);

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 1, expectedValue);
    }

    @Test
    public void testPlanChange_BeforeBillingDay() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2011, 2, 7);
        DateTime changeDate = buildDateTime(2011, 2, 15);
        DateTime targetDate = buildDateTime(2011, 4, 21);
        
        BigDecimal expectedValue;

        expectedValue = EIGHT.divide(TWENTY_EIGHT, NUMBER_OF_DECIMALS, ROUNDING_METHOD);
        testCalculateNumberOfBillingCycles(startDate, changeDate, targetDate, 7, expectedValue);

        testCalculateNumberOfBillingCycles(changeDate, targetDate, 15, THREE);
    }

    @Test
    public void testPlanChange_OnBillingDay() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2011, 2, 7);
        DateTime changeDate = buildDateTime(2011, 3, 7);
        DateTime targetDate = buildDateTime(2011, 4, 21);

        testCalculateNumberOfBillingCycles(startDate, changeDate, targetDate, 7, ONE);

        BigDecimal expectedValue;
        expectedValue = EIGHT.divide(TWENTY_EIGHT, NUMBER_OF_DECIMALS, ROUNDING_METHOD).add(TWO);
        testCalculateNumberOfBillingCycles(changeDate, targetDate, 15, expectedValue);
    }

    @Test
    public void testPlanChange_AfterBillingDay() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2011, 2, 7);
        DateTime changeDate = buildDateTime(2011, 3, 10);
        DateTime targetDate = buildDateTime(2011, 4, 21);

        BigDecimal expectedValue;

        expectedValue = BigDecimal.ONE.add(THREE.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD));
        testCalculateNumberOfBillingCycles(startDate, changeDate, targetDate, 7, expectedValue);

        expectedValue = FIVE.divide(TWENTY_EIGHT, NUMBER_OF_DECIMALS, ROUNDING_METHOD).add(TWO);
        testCalculateNumberOfBillingCycles(changeDate, targetDate, 15, expectedValue);
    }

    @Test
    public void testPlanChange_DoubleProRation() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2011, 1, 31);
        DateTime planChangeDate = buildDateTime(2011, 3, 10);
        DateTime targetDate = buildDateTime(2011, 4, 21);

        BigDecimal expectedValue;
        expectedValue = SEVEN.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD);
        expectedValue = expectedValue.add(ONE);
        expectedValue = expectedValue.add(THREE.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD));
        testCalculateNumberOfBillingCycles(startDate, planChangeDate, targetDate, 7, expectedValue);

        expectedValue = FIVE.divide(TWENTY_EIGHT, NUMBER_OF_DECIMALS, ROUNDING_METHOD).add(TWO);  
        testCalculateNumberOfBillingCycles(planChangeDate, targetDate, 15, expectedValue);
    }

    @Test
    public void testStartTargetEnd() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2010, 12, 15);
        DateTime targetDate = buildDateTime(2011, 3, 15);
        DateTime endDate = buildDateTime(2011, 3, 17);

        BigDecimal expectedValue = THREE.add(TWO.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD));
        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 15, expectedValue);
    }
}