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

package com.ning.billing.invoice.tests.inAdvance;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.invoice.model.BillingMode;
import com.ning.billing.invoice.model.InAdvanceBillingMode;
import com.ning.billing.invoice.model.InvalidDateSequenceException;
import com.ning.billing.invoice.tests.ProRationTestBase;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import java.math.BigDecimal;

import static org.testng.Assert.assertEquals;

@Test(groups = {"fast", "invoicing", "proRation"})
public class ValidationProRationTests extends ProRationTestBase {
    protected BillingPeriod getBillingPeriod() {
        return BillingPeriod.MONTHLY;
    }

    @Override
    protected BillingMode getBillingMode() {
        return new InAdvanceBillingMode();
    }

    protected BigDecimal calculateNumberOfBillingCycles(DateTime startDate, DateTime targetDate, int billingCycleDay) throws InvalidDateSequenceException {
        return getBillingMode().calculateNumberOfBillingCycles(startDate, targetDate, billingCycleDay, getBillingPeriod());
    }

    protected BigDecimal calculateNumberOfBillingCycles(DateTime startDate, DateTime endDate, DateTime targetDate, int billingCycleDay) throws InvalidDateSequenceException {
        return getBillingMode().calculateNumberOfBillingCycles(startDate, endDate, targetDate, billingCycleDay, getBillingPeriod());
    }

    @Test(expectedExceptions = InvalidDateSequenceException.class)
    public void testTargetStartEnd() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2011, 1, 30);
        DateTime endDate = buildDateTime(2011, 3, 15);
        DateTime targetDate = buildDateTime(2011, 1, 15);

        calculateNumberOfBillingCycles(startDate, endDate, targetDate, 15);
    }

    @Test(expectedExceptions = InvalidDateSequenceException.class)
    public void testTargetEndStart() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2011, 4, 30);
        DateTime endDate = buildDateTime(2011, 3, 15);
        DateTime targetDate = buildDateTime(2011, 2, 15);

        calculateNumberOfBillingCycles(startDate, endDate, targetDate, 15);
    }

    @Test(expectedExceptions = InvalidDateSequenceException.class)
    public void testEndTargetStart() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2011, 3, 30);
        DateTime endDate = buildDateTime(2011, 1, 15);
        DateTime targetDate = buildDateTime(2011, 2, 15);

        calculateNumberOfBillingCycles(startDate, endDate, targetDate, 15);
    }

    @Test(expectedExceptions = InvalidDateSequenceException.class)
    public void testEndStartTarget() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2011, 1, 30);
        DateTime endDate = buildDateTime(2011, 1, 15);
        DateTime targetDate = buildDateTime(2011, 2, 15);

        calculateNumberOfBillingCycles(startDate, endDate, targetDate, 15);
    }

    @Test(expectedExceptions = InvalidDateSequenceException.class)
    public void testTargetStart() throws InvalidDateSequenceException {
        DateTime startDate = buildDateTime(2011, 4, 30);
        DateTime targetDate = buildDateTime(2011, 2, 15);

        calculateNumberOfBillingCycles(startDate, targetDate, 15);
    }

    @Test
    public void testBigDecimalTruncation() {
        BigDecimal value = new BigDecimal("1.3349573498567");
        BigDecimal truncated = value.setScale(0, BigDecimal.ROUND_DOWN).setScale(NUMBER_OF_DECIMALS);

        assertEquals(truncated, ONE);
    }
}

