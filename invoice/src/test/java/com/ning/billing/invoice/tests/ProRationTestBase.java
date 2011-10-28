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

package com.ning.billing.invoice.tests;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.invoice.model.IBillingMode;
import com.ning.billing.invoice.model.InvalidDateSequenceException;
import org.joda.time.DateTime;

import java.math.BigDecimal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public abstract class ProRationTestBase extends InvoicingTestBase{
    protected abstract IBillingMode getBillingMode();
    protected abstract BillingPeriod getBillingPeriod();

    protected void testCalculateNumberOfBillingCycles(DateTime startDate, DateTime targetDate, int billingCycleDay, BigDecimal expectedValue) throws InvalidDateSequenceException {
        try {
            BigDecimal numberOfBillingCycles;
            numberOfBillingCycles = getBillingMode().calculateNumberOfBillingCycles(startDate, targetDate, billingCycleDay, getBillingPeriod());

            assertEquals(numberOfBillingCycles, expectedValue);
        } catch (InvalidDateSequenceException idse) {
            throw idse;
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    protected void testCalculateNumberOfBillingCycles(DateTime startDate, DateTime endDate, DateTime targetDate, int billingCycleDay, BigDecimal expectedValue) throws InvalidDateSequenceException {
        try {
            BigDecimal numberOfBillingCycles;
            numberOfBillingCycles = getBillingMode().calculateNumberOfBillingCycles(startDate, endDate, targetDate, billingCycleDay, getBillingPeriod());

            assertEquals(numberOfBillingCycles, expectedValue);
        } catch (InvalidDateSequenceException idse) {
            throw idse;
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }
}