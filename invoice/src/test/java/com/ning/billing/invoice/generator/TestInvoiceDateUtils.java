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

package com.ning.billing.invoice.generator;

import java.math.BigDecimal;

import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.invoice.InvoiceTestSuiteNoDB;

public class TestInvoiceDateUtils extends InvoiceTestSuiteNoDB {

    @Test(groups = "fast")
    public void testProRationAfterLastBillingCycleDate() throws Exception {
        final LocalDate endDate = new LocalDate("2012-06-02");
        final LocalDate previousBillThroughDate = new LocalDate("2012-03-02");
        final BigDecimal proration = InvoiceDateUtils.calculateProRationAfterLastBillingCycleDate(endDate, previousBillThroughDate, BillingPeriod.MONTHLY);
        Assert.assertEquals(proration, new BigDecimal("2.9677"));
    }

    @Test(groups = "fast")
    public void testCalculateNbOfBillingPeriods() throws Exception {
        final LocalDate firstBCD = new LocalDate(2012, 7, 16);
        final LocalDate lastBCD = new LocalDate(2012, 9, 16);
        final BillingPeriod billingPeriod = BillingPeriod.MONTHLY;
        final int numberOfWholeBillingPeriods = InvoiceDateUtils.calculateNumberOfWholeBillingPeriods(firstBCD, lastBCD, billingPeriod);
        Assert.assertEquals(numberOfWholeBillingPeriods, 2);
    }
}
