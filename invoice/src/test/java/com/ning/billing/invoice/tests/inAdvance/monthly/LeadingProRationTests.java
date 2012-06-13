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

import java.math.BigDecimal;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.invoice.model.InvalidDateSequenceException;
import com.ning.billing.invoice.tests.inAdvance.ProRationInAdvanceTestBase;

@Test(groups = {"fast", "invoicing", "proRation"})
public class LeadingProRationTests extends ProRationInAdvanceTestBase {
    @Override
    protected BillingPeriod getBillingPeriod() {
        return BillingPeriod.MONTHLY;
    }

    @Test
    public void testLeadingProRation_Evergreen_TargetDateOnStartDate() throws InvalidDateSequenceException {
        final DateTime startDate = buildDateTime(2011, 2, 1);
        final DateTime targetDate = buildDateTime(2011, 2, 1);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD);

        testCalculateNumberOfBillingCycles(startDate, targetDate, 13, expectedValue);
    }

    @Test
    public void testLeadingProRation_Evergreen_TargetDateInProRationPeriod() throws InvalidDateSequenceException {
        final DateTime startDate = buildDateTime(2011, 2, 1);
        final DateTime targetDate = buildDateTime(2011, 2, 4);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD);

        testCalculateNumberOfBillingCycles(startDate, targetDate, 13, expectedValue);
    }

    @Test
    public void testLeadingProRation_Evergreen_TargetDateOnFirstBillingDate() throws InvalidDateSequenceException {
        final DateTime startDate = buildDateTime(2011, 2, 1);
        final DateTime targetDate = buildDateTime(2011, 2, 13);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD).add(ONE);

        testCalculateNumberOfBillingCycles(startDate, targetDate, 13, expectedValue);
    }

    @Test
    public void testLeadingProRation_Evergreen_TargetDateAfterFirstBillingPeriod() throws InvalidDateSequenceException {
        final DateTime startDate = buildDateTime(2011, 2, 1);
        final DateTime targetDate = buildDateTime(2011, 4, 13);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD).add(THREE);

        testCalculateNumberOfBillingCycles(startDate, targetDate, 13, expectedValue);
    }

    @Test
    public void testLeadingProRation_WithEndDate_TargetDateOnStartDate() throws InvalidDateSequenceException {
        final DateTime startDate = buildDateTime(2011, 2, 1);
        final DateTime targetDate = buildDateTime(2011, 2, 1);
        final DateTime endDate = buildDateTime(2011, 4, 13);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD);

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 13, expectedValue);
    }

    @Test
    public void testLeadingProRation_WithEndDate_TargetDateInProRationPeriod() throws InvalidDateSequenceException {
        final DateTime startDate = buildDateTime(2011, 2, 1);
        final DateTime targetDate = buildDateTime(2011, 2, 4);
        final DateTime endDate = buildDateTime(2011, 4, 13);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD);

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 13, expectedValue);
    }

    @Test
    public void testLeadingProRation_WithEndDate_TargetDateOnFirstBillingDate() throws InvalidDateSequenceException {
        final DateTime startDate = buildDateTime(2011, 2, 1);
        final DateTime targetDate = buildDateTime(2011, 2, 13);
        final DateTime endDate = buildDateTime(2011, 4, 13);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD).add(ONE);

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 13, expectedValue);
    }

    @Test
    public void testLeadingProRation_WithEndDate_TargetDateInFinalBillingPeriod() throws InvalidDateSequenceException {
        final DateTime startDate = buildDateTime(2011, 2, 1);
        final DateTime targetDate = buildDateTime(2011, 4, 10);
        final DateTime endDate = buildDateTime(2011, 4, 13);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD).add(TWO);

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 13, expectedValue);
    }

    @Test
    public void testLeadingProRation_WithEndDate_TargetDateOnEndDate() throws InvalidDateSequenceException {
        final DateTime startDate = buildDateTime(2011, 2, 1);
        final DateTime targetDate = buildDateTime(2011, 4, 13);
        final DateTime endDate = buildDateTime(2011, 4, 13);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD).add(TWO);

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 13, expectedValue);
    }

    @Test
    public void testLeadingProRation_WithEndDate_TargetDateAfterEndDate() throws InvalidDateSequenceException {
        final DateTime startDate = buildDateTime(2011, 2, 1);
        final DateTime targetDate = buildDateTime(2011, 4, 10);
        final DateTime endDate = buildDateTime(2011, 4, 13);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THIRTY_ONE, NUMBER_OF_DECIMALS, ROUNDING_METHOD).add(TWO);

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 13, expectedValue);
    }
}
