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

package org.killbill.billing.invoice.proRations.inAdvance.quarterly;

import static org.killbill.billing.invoice.TestInvoiceHelper.*;

import java.math.BigDecimal;

import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.invoice.model.InvalidDateSequenceException;
import org.killbill.billing.invoice.proRations.inAdvance.ProRationInAdvanceTestBase;
import org.killbill.billing.util.currency.KillBillMoney;

public class TestDoubleProRation extends ProRationInAdvanceTestBase {

    @Override
    protected BillingPeriod getBillingPeriod() {
        return BillingPeriod.QUARTERLY;
    }

    @Test(groups = "fast")
    public void testDoubleProRation_TargetDateOnStartDate() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 1, 1);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 4, 27);

        BigDecimal expectedValue = FOURTEEN.divide(NINETY_TWO, KillBillMoney.ROUNDING_METHOD);
        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 15, expectedValue);
    }

    @Test(groups = "fast")
    public void testDoubleProRation_TargetDateInFirstProRationPeriod() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 1, 7);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 4, 27);

        BigDecimal expectedValue = FOURTEEN.divide(NINETY_TWO, KillBillMoney.ROUNDING_METHOD);
        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 15, expectedValue);
    }

    @Test(groups = "fast")
    public void testDoubleProRation_TargetDateOnFirstBillingCycleDate() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 1, 15);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 4, 27);

        BigDecimal expectedValue = ONE.add(FOURTEEN.divide(NINETY_TWO, KillBillMoney.ROUNDING_METHOD));
        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 15, expectedValue);
    }

    @Test(groups = "fast")
    public void testDoubleProRation_TargetDateInFullBillingPeriod() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 1, 22);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 4, 27);

        BigDecimal expectedValue;
        expectedValue = FOURTEEN.divide(NINETY_TWO, KillBillMoney.ROUNDING_METHOD);
        expectedValue = expectedValue.add(ONE);

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 15, expectedValue);
    }

    @Test(groups = "fast")
    public void testDoubleProRation_TargetDateOnSecondBillingCycleDate() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 4, 15);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 4, 27);

        BigDecimal expectedValue;
        expectedValue = FOURTEEN.divide(NINETY_TWO, KillBillMoney.ROUNDING_METHOD);
        expectedValue = expectedValue.add(ONE);
        expectedValue = expectedValue.add(TWELVE.divide(NINETY_ONE, KillBillMoney.ROUNDING_METHOD));

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 15, expectedValue);
    }

    @Test(groups = "fast")
    public void testDoubleProRation_TargetDateInSecondProRationPeriod() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 4, 26);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 4, 27);

        BigDecimal expectedValue;
        expectedValue = FOURTEEN.divide(NINETY_TWO, KillBillMoney.ROUNDING_METHOD);
        expectedValue = expectedValue.add(ONE);
        expectedValue = expectedValue.add(TWELVE.divide(NINETY_ONE, KillBillMoney.ROUNDING_METHOD));

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 15, expectedValue);
    }

    @Test(groups = "fast")
    public void testDoubleProRation_TargetDateOnEndDate() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 4, 27);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 4, 27);

        BigDecimal expectedValue;
        expectedValue = FOURTEEN.divide(NINETY_TWO, KillBillMoney.ROUNDING_METHOD);
        expectedValue = expectedValue.add(ONE);
        expectedValue = expectedValue.add(TWELVE.divide(NINETY_ONE, KillBillMoney.ROUNDING_METHOD));

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 15, expectedValue);
    }

    @Test(groups = "fast")
    public void testDoubleProRation_TargetDateAfterEndDate() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 5, 7);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 4, 27);

        BigDecimal expectedValue;
        expectedValue = FOURTEEN.divide(NINETY_TWO, KillBillMoney.ROUNDING_METHOD);
        expectedValue = expectedValue.add(ONE);
        expectedValue = expectedValue.add(TWELVE.divide(NINETY_ONE, KillBillMoney.ROUNDING_METHOD));

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 15, expectedValue);
    }

    @Test(groups = "fast")
    public void testDoubleProRationWithMultiplePeriods_TargetDateInSecondFullBillingPeriod() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 6, 26);
        final LocalDate endDate = invoiceUtil.buildDate(2011, 8, 27);

        BigDecimal expectedValue;
        expectedValue = FOURTEEN.divide(NINETY_TWO, KillBillMoney.ROUNDING_METHOD);
        expectedValue = expectedValue.add(TWO);

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 15, expectedValue);
    }
}
