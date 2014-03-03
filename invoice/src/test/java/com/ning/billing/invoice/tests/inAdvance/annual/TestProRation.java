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

package com.ning.billing.invoice.tests.inAdvance.annual;

import static com.ning.billing.invoice.TestInvoiceHelper.*;

import java.math.BigDecimal;

import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.invoice.model.InvalidDateSequenceException;
import com.ning.billing.invoice.tests.inAdvance.ProRationInAdvanceTestBase;
import com.ning.billing.util.currency.KillBillMoney;

public class TestProRation extends ProRationInAdvanceTestBase {

    @Override
    protected BillingPeriod getBillingPeriod() {
        return BillingPeriod.ANNUAL;
    }

    @Test(groups = "fast")
    public void testSinglePlan_PrecedingProRation() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 31);
        final LocalDate targetDate = invoiceUtil.buildDate(2011, 2, 24);

        // THREE_HUNDRED_AND_FOURTY_NINE is number of days between startDate and expected first billing cycle date (2012, 1, 15);
        final BigDecimal expectedValue = THREE_HUNDRED_AND_FOURTY_NINE.divide(THREE_HUNDRED_AND_SIXTY_FIVE, KillBillMoney.ROUNDING_METHOD);
        testCalculateNumberOfBillingCycles(startDate, targetDate, 15, expectedValue);
    }

    @Test(groups = "fast")
    public void testSinglePlan_PrecedingProRation_CrossingYearBoundary() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2010, 12, 15);
        final LocalDate targetDate = invoiceUtil.buildDate(2012, 1, 13);

        // THREE_HUNDRED_AND_FOURTY_NINE is number of days between startDate and expected first billing cycle date (2011, 12, 4);
        final BigDecimal expectedValue = ONE.add(THREE_HUNDRED_AND_FIFTY_FOUR.divide(THREE_HUNDRED_AND_SIXTY_FIVE, KillBillMoney.ROUNDING_METHOD));
        testCalculateNumberOfBillingCycles(startDate, targetDate, 4, expectedValue);
    }

    // TODO Test fails, needs to be investigated
    @Test(groups = "fast", enabled = false)
    public void testSinglePlanDoubleProRation() throws InvalidDateSequenceException {
        final LocalDate startDate = invoiceUtil.buildDate(2011, 1, 10);
        final LocalDate endDate = invoiceUtil.buildDate(2012, 3, 4);
        final LocalDate targetDate = invoiceUtil.buildDate(2012, 4, 5);

        final BigDecimal expectedValue = BigDecimal.ZERO;
        testCalculateNumberOfBillingCycles(startDate, endDate, targetDate, 17, expectedValue);
    }
}
