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

package org.killbill.billing.invoice.proRations.inAdvance.annual;

import static org.killbill.billing.invoice.TestInvoiceHelper.*;

import java.math.BigDecimal;

import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.invoice.model.InvalidDateSequenceException;
import org.killbill.billing.invoice.proRations.inAdvance.ProRationInAdvanceTestBase;
import org.killbill.billing.util.currency.KillBillMoney;

public class TestLeadingProRation extends ProRationInAdvanceTestBase {

    @Override
    protected BillingPeriod getBillingPeriod() {
        return BillingPeriod.ANNUAL;
    }

    @Test(groups = "fast")
    public void testLeadingProRation_Evergreen_TargetDateOnStartDate() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 2, 1);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THREE_HUNDRED_AND_SIXTY_FIVE, KillBillMoney.ROUNDING_METHOD);

        testCalculateNumberOfBillingCycles(startDate, targetDate, 13, expectedValue);
    }

    @Test(groups = "fast")
    public void testLeadingProRation_Evergreen_TargetDateInProRationPeriod() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 2, 4);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THREE_HUNDRED_AND_SIXTY_FIVE, KillBillMoney.ROUNDING_METHOD);

        testCalculateNumberOfBillingCycles(startDate, targetDate, 13, expectedValue);
    }

    @Test(groups = "fast")
    public void testLeadingProRation_Evergreen_TargetDateOnFirstBillingDate() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 2, 13);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THREE_HUNDRED_AND_SIXTY_FIVE, KillBillMoney.ROUNDING_METHOD).add(ONE);

        testCalculateNumberOfBillingCycles(startDate, targetDate, 13, expectedValue);
    }

    @Test(groups = "fast")
    public void testLeadingProRation_Evergreen_TargetDateAfterFirstBillingPeriod() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 4, 13);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THREE_HUNDRED_AND_SIXTY_FIVE, KillBillMoney.ROUNDING_METHOD).add(ONE);

        testCalculateNumberOfBillingCycles(startDate, targetDate, 13, expectedValue);
    }

    @Test(groups = "fast")
    public void testLeadingProRation_WithEndDate_TargetDateOnStartDate() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 2, 1);
        final LocalDate endDate = invoiceUtil.buildDate(2012, 2, 13);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THREE_HUNDRED_AND_SIXTY_FIVE, KillBillMoney.ROUNDING_METHOD);

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 13, expectedValue);
    }

    @Test(groups = "fast")
    public void testLeadingProRation_WithEndDate_TargetDateInProRationPeriod() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 2, 4);
        final LocalDate endDate = invoiceUtil.buildDate(2012, 2, 13);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THREE_HUNDRED_AND_SIXTY_FIVE, KillBillMoney.ROUNDING_METHOD);

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 13, expectedValue);
    }

    @Test(groups = "fast")
    public void testLeadingProRation_WithEndDate_TargetDateOnFirstBillingDate() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 2, 13);
        final LocalDate endDate = invoiceUtil.buildDate(2012, 2, 13);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THREE_HUNDRED_AND_SIXTY_FIVE, KillBillMoney.ROUNDING_METHOD).add(ONE);

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 13, expectedValue);
    }

    @Test(groups = "fast")
    public void testLeadingProRation_WithEndDate_TargetDateInFinalBillingPeriod() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 4, 10);
        final LocalDate endDate = invoiceUtil.buildDate(2012, 2, 13);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THREE_HUNDRED_AND_SIXTY_FIVE, KillBillMoney.ROUNDING_METHOD).add(ONE);

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 13, expectedValue);
    }

    @Test(groups = "fast")
    public void testLeadingProRation_WithEndDate_TargetDateOnEndDate() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2012, 2, 13);
        final LocalDate endDate = invoiceUtil.buildDate(2012, 2, 13);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THREE_HUNDRED_AND_SIXTY_FIVE, KillBillMoney.ROUNDING_METHOD).add(ONE);

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 13, expectedValue);
    }

    @Test(groups = "fast")
    public void testLeadingProRation_WithEndDate_TargetDateAfterEndDate() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 2, 1);
        final LocalDate targetDate = invoiceUtil.buildDate(2012, 4, 10);
        final LocalDate endDate = invoiceUtil.buildDate(2012, 2, 13);

        final BigDecimal expectedValue;
        expectedValue = TWELVE.divide(THREE_HUNDRED_AND_SIXTY_FIVE, KillBillMoney.ROUNDING_METHOD).add(ONE);

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 13, expectedValue);
    }
}
