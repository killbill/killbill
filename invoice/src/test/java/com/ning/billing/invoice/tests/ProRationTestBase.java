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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.math.BigDecimal;
import java.util.List;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.invoice.model.BillingMode;
import com.ning.billing.invoice.model.InvalidDateSequenceException;
import com.ning.billing.invoice.model.RecurringInvoiceItemData;

public abstract class ProRationTestBase extends InvoicingTestBase {
    protected abstract BillingMode getBillingMode();
    protected abstract BillingPeriod getBillingPeriod();

    protected void testCalculateNumberOfBillingCycles(DateTime startDate, DateTime targetDate, int billingCycleDay, BigDecimal expectedValue) throws InvalidDateSequenceException {
        try {
            BigDecimal numberOfBillingCycles;
            numberOfBillingCycles = calculateNumberOfBillingCycles(startDate, targetDate, billingCycleDay);

            assertEquals(numberOfBillingCycles.compareTo(expectedValue), 0, "Actual: " + numberOfBillingCycles.toString() + "; expected: " + expectedValue.toString());
        } catch (InvalidDateSequenceException idse) {
            throw idse;
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    protected void testCalculateNumberOfBillingCycles(DateTime startDate, DateTime endDate, DateTime targetDate, int billingCycleDay, BigDecimal expectedValue) throws InvalidDateSequenceException {
        try {
            BigDecimal numberOfBillingCycles;
            numberOfBillingCycles = calculateNumberOfBillingCycles(startDate, endDate, targetDate, billingCycleDay);

            assertEquals(numberOfBillingCycles.compareTo(expectedValue), 0);
        } catch (InvalidDateSequenceException idse) {
            throw idse;
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    protected BigDecimal calculateNumberOfBillingCycles(DateTime startDate, DateTime endDate, DateTime targetDate, int billingCycleDay) throws InvalidDateSequenceException {
        List<RecurringInvoiceItemData> items = getBillingMode().calculateInvoiceItemData(startDate, endDate, targetDate, billingCycleDay, getBillingPeriod());

        BigDecimal numberOfBillingCycles = ZERO;
        for (RecurringInvoiceItemData item : items) {
            numberOfBillingCycles = numberOfBillingCycles.add(item.getNumberOfCycles());
        }

        return numberOfBillingCycles.setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
    }

    protected BigDecimal calculateNumberOfBillingCycles(DateTime startDate, DateTime targetDate, int billingCycleDay) throws InvalidDateSequenceException {
        List<RecurringInvoiceItemData> items = getBillingMode().calculateInvoiceItemData(startDate, targetDate, billingCycleDay, getBillingPeriod());

        BigDecimal numberOfBillingCycles = ZERO;
        for (RecurringInvoiceItemData item : items) {
            numberOfBillingCycles = numberOfBillingCycles.add(item.getNumberOfCycles());
        }

        return numberOfBillingCycles.setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
    }
}