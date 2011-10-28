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

package com.ning.billing.invoice.tests.inAdvance.annual;

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
        return BillingPeriod.ANNUAL;
    }

    @Test
    public void testSinglePlan_PrecedingProRation() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2011, 1, 31);
        DateTime targetDate = buildDateTime(2011, 2, 24);

        BigDecimal expectedValue = ONE.add(FIFTEEN.divide(THREE_HUNDRED_AND_SIXTY_FIVE, NUMBER_OF_DECIMALS, ROUNDING_METHOD));
        testCalculateNumberOfBillingCycles(startDate, targetDate, 15, expectedValue);
    }

    @Test
    public void testSinglePlan_PrecedingProRation_CrossingYearBoundary() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2010, 12, 15);
        DateTime targetDate = buildDateTime(2011, 1, 13);

        BigDecimal expectedValue = ONE.add(TWENTY.divide(THREE_HUNDRED_AND_SIXTY_FIVE, NUMBER_OF_DECIMALS, ROUNDING_METHOD));
        testCalculateNumberOfBillingCycles(startDate, targetDate, 4, expectedValue);
    }

    @Test(enabled = false)
    public void testSinglePlanDoubleProRation() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2011, 1, 10);
        DateTime endDate = buildDateTime(2012, 3, 4);
        DateTime targetDate = buildDateTime(2012, 4, 5);

        BigDecimal expectedValue = BigDecimal.ZERO;
        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 17, expectedValue);
    }
}
