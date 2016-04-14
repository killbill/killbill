/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.invoice.generator;

import java.math.BigDecimal;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestInvoiceDateUtils extends InvoiceTestSuiteNoDB {

    @Test(groups = "fast")
    public void testProRationAfterLastBillingCycleDate() throws Exception {
        final LocalDate endDate = new LocalDate("2012-06-02");
        final LocalDate previousBillThroughDate = new LocalDate("2012-03-02");
        final BigDecimal proration = InvoiceDateUtils.calculateProRationAfterLastBillingCycleDate(endDate, previousBillThroughDate, BillingPeriod.MONTHLY);
        Assert.assertEquals(proration, new BigDecimal("2.967741935"));
    }

    @Test(groups = "fast")
    public void testCalculateNbOfBillingPeriods() throws Exception {
        Assert.assertEquals(InvoiceDateUtils.calculateNumberOfWholeBillingPeriods(new LocalDate(2012, 7, 16), new LocalDate(2012, 9, 15), BillingPeriod.MONTHLY), 1);
        Assert.assertEquals(InvoiceDateUtils.calculateNumberOfWholeBillingPeriods(new LocalDate(2012, 7, 16), new LocalDate(2012, 9, 16), BillingPeriod.MONTHLY), 2);
        Assert.assertEquals(InvoiceDateUtils.calculateNumberOfWholeBillingPeriods(new LocalDate(2012, 7, 16), new LocalDate(2012, 9, 17), BillingPeriod.MONTHLY), 2);

        Assert.assertEquals(InvoiceDateUtils.calculateNumberOfWholeBillingPeriods(new LocalDate(2012, 7, 16), new LocalDate(2012, 9, 13), BillingPeriod.THIRTY_DAYS), 1);
        Assert.assertEquals(InvoiceDateUtils.calculateNumberOfWholeBillingPeriods(new LocalDate(2012, 7, 16), new LocalDate(2012, 9, 14), BillingPeriod.THIRTY_DAYS), 2);
        Assert.assertEquals(InvoiceDateUtils.calculateNumberOfWholeBillingPeriods(new LocalDate(2012, 7, 16), new LocalDate(2012, 9, 15), BillingPeriod.THIRTY_DAYS), 2);

        Assert.assertEquals(InvoiceDateUtils.calculateNumberOfWholeBillingPeriods(new LocalDate(2012, 7, 16), new LocalDate(2012, 7, 29), BillingPeriod.WEEKLY), 1);
        Assert.assertEquals(InvoiceDateUtils.calculateNumberOfWholeBillingPeriods(new LocalDate(2012, 7, 16), new LocalDate(2012, 7, 30), BillingPeriod.WEEKLY), 2);
        Assert.assertEquals(InvoiceDateUtils.calculateNumberOfWholeBillingPeriods(new LocalDate(2012, 7, 16), new LocalDate(2012, 7, 31), BillingPeriod.WEEKLY), 2);
    }

    @Test(groups = "fast")
    public void testAdvanceByNPeriods() throws Exception {
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.MONTHLY, 0), new LocalDate(2016, 4, 8));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.MONTHLY, 1), new LocalDate(2016, 5, 8));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.MONTHLY, 2), new LocalDate(2016, 6, 8));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.MONTHLY, 3), new LocalDate(2016, 7, 8));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.MONTHLY, 4), new LocalDate(2016, 8, 8));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.MONTHLY, 5), new LocalDate(2016, 9, 8));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.MONTHLY, 6), new LocalDate(2016, 10, 8));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.MONTHLY, 7), new LocalDate(2016, 11, 8));

        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.THIRTY_DAYS, 0), new LocalDate(2016, 4, 8));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.THIRTY_DAYS, 1), new LocalDate(2016, 5, 8));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.THIRTY_DAYS, 2), new LocalDate(2016, 6, 7));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.THIRTY_DAYS, 3), new LocalDate(2016, 7, 7));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.THIRTY_DAYS, 4), new LocalDate(2016, 8, 6));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.THIRTY_DAYS, 5), new LocalDate(2016, 9, 5));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.THIRTY_DAYS, 6), new LocalDate(2016, 10, 5));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.THIRTY_DAYS, 7), new LocalDate(2016, 11, 4));

        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.WEEKLY, 0), new LocalDate(2016, 4, 8));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.WEEKLY, 1), new LocalDate(2016, 4, 15));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.WEEKLY, 2), new LocalDate(2016, 4, 22));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.WEEKLY, 3), new LocalDate(2016, 4, 29));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.WEEKLY, 4), new LocalDate(2016, 5, 6));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.WEEKLY, 5), new LocalDate(2016, 5, 13));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.WEEKLY, 6), new LocalDate(2016, 5, 20));
        Assert.assertEquals(InvoiceDateUtils.advanceByNPeriods(new LocalDate(2016, 4, 8), BillingPeriod.WEEKLY, 7), new LocalDate(2016, 5, 27));
    }

    @Test(groups = "fast")
    public void testRecedeByNPeriods() throws Exception {
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 11, 8), BillingPeriod.MONTHLY, 7), new LocalDate(2016, 4, 8));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 11, 8), BillingPeriod.MONTHLY, 6), new LocalDate(2016, 5, 8));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 11, 8), BillingPeriod.MONTHLY, 5), new LocalDate(2016, 6, 8));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 11, 8), BillingPeriod.MONTHLY, 4), new LocalDate(2016, 7, 8));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 11, 8), BillingPeriod.MONTHLY, 3), new LocalDate(2016, 8, 8));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 11, 8), BillingPeriod.MONTHLY, 2), new LocalDate(2016, 9, 8));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 11, 8), BillingPeriod.MONTHLY, 1), new LocalDate(2016, 10, 8));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 11, 8), BillingPeriod.MONTHLY, 0), new LocalDate(2016, 11, 8));

        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 11, 4), BillingPeriod.THIRTY_DAYS, 7), new LocalDate(2016, 4, 8));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 11, 4), BillingPeriod.THIRTY_DAYS, 6), new LocalDate(2016, 5, 8));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 11, 4), BillingPeriod.THIRTY_DAYS, 5), new LocalDate(2016, 6, 7));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 11, 4), BillingPeriod.THIRTY_DAYS, 4), new LocalDate(2016, 7, 7));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 11, 4), BillingPeriod.THIRTY_DAYS, 3), new LocalDate(2016, 8, 6));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 11, 4), BillingPeriod.THIRTY_DAYS, 2), new LocalDate(2016, 9, 5));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 11, 4), BillingPeriod.THIRTY_DAYS, 1), new LocalDate(2016, 10, 5));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 11, 4), BillingPeriod.THIRTY_DAYS, 0), new LocalDate(2016, 11, 4));

        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 5, 27), BillingPeriod.WEEKLY, 7), new LocalDate(2016, 4, 8));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 5, 27), BillingPeriod.WEEKLY, 6), new LocalDate(2016, 4, 15));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 5, 27), BillingPeriod.WEEKLY, 5), new LocalDate(2016, 4, 22));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 5, 27), BillingPeriod.WEEKLY, 4), new LocalDate(2016, 4, 29));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 5, 27), BillingPeriod.WEEKLY, 3), new LocalDate(2016, 5, 6));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 5, 27), BillingPeriod.WEEKLY, 2), new LocalDate(2016, 5, 13));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 5, 27), BillingPeriod.WEEKLY, 1), new LocalDate(2016, 5, 20));
        Assert.assertEquals(InvoiceDateUtils.recedeByNPeriods(new LocalDate(2016, 5, 27), BillingPeriod.WEEKLY, 0), new LocalDate(2016, 5, 27));
    }
}
