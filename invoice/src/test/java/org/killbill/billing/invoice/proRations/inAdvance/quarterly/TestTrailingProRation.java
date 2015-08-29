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

public class TestTrailingProRation extends ProRationInAdvanceTestBase {

    @Override
    protected BillingPeriod getBillingPeriod() {
        return BillingPeriod.QUARTERLY;
    }

    @Test(groups = "fast")
    public void testTargetDateOnStartDate() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2010, 6, 17);
        final LocalDate endDate = invoiceUtil.buildDate(2010, 9, 25);
        final LocalDate targetDate = invoiceUtil.buildDate(2010, 6, 17);

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 17, ONE);
    }

    @Test(groups = "fast")
    public void testTargetDateInFirstBillingPeriod() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2010, 6, 17);
        final LocalDate endDate = invoiceUtil.buildDate(2010, 9, 25);
        final LocalDate targetDate = invoiceUtil.buildDate(2010, 6, 20);

        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 17, ONE);
    }

    @Test(groups = "fast")
    public void testTargetDateAtEndOfFirstBillingCycle() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2010, 6, 17);
        final LocalDate endDate = invoiceUtil.buildDate(2010, 9, 25);
        final LocalDate targetDate = invoiceUtil.buildDate(2010, 9, 17);

        final BigDecimal expectedValue = ONE.add(EIGHT.divide(NINETY_ONE, KillBillMoney.ROUNDING_METHOD));
        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 17, expectedValue);
    }

    @Test(groups = "fast")
    public void testTargetDateInProRationPeriod() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2010, 6, 17);
        final LocalDate endDate = invoiceUtil.buildDate(2010, 9, 25);
        final LocalDate targetDate = invoiceUtil.buildDate(2010, 9, 18);

        final BigDecimal expectedValue = ONE.add(EIGHT.divide(NINETY_ONE, KillBillMoney.ROUNDING_METHOD));
        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 17, expectedValue);
    }

    @Test(groups = "fast")
    public void testTargetDateOnEndDate() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2010, 6, 17);
        final LocalDate endDate = invoiceUtil.buildDate(2010, 9, 25);

        final BigDecimal expectedValue = ONE.add(EIGHT.divide(NINETY_ONE, KillBillMoney.ROUNDING_METHOD));
        testCalculateNumberOfBillingCycles(startDate, endDate, endDate, 17, expectedValue);
    }

    @Test(groups = "fast")
    public void testTargetDateAfterEndDate() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2010, 6, 17);
        final LocalDate endDate = invoiceUtil.buildDate(2010, 9, 25);
        final LocalDate targetDate = invoiceUtil.buildDate(2010, 9, 30);

        final BigDecimal expectedValue = ONE.add(EIGHT.divide(NINETY_ONE, KillBillMoney.ROUNDING_METHOD));
        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 17, expectedValue);
    }
}
