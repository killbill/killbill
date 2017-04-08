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

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.killbill.billing.invoice.generator.InvoiceDateUtils.calculateNumberOfWholeBillingPeriods;

public class TestInAdvanceBillingIntervalDetail extends InvoiceTestSuiteNoDB {

    /*
     *
     *         Start         BCD    END_MONTH
     * |---------|------------|-------|
     *
     */
    @Test(groups = "fast")
    public void testCalculateFirstBillingCycleDate1() throws Exception {
        final LocalDate from = new LocalDate("2012-01-16");
        final LocalDate to = null;
        final LocalDate targetDate = new LocalDate();
        final int bcd = 17;

        final BillingIntervalDetail annualBillingIntervalDetail = new BillingIntervalDetail(from, to, targetDate, bcd, BillingPeriod.ANNUAL, BillingMode.IN_ADVANCE);
        Assert.assertEquals(annualBillingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-01-17"));

        final BillingIntervalDetail monthlyBillingIntervalDetail = new BillingIntervalDetail(from, to, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ADVANCE);
        Assert.assertEquals(monthlyBillingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-01-17"));

        final BillingIntervalDetail thirtyDaysBillingIntervalDetail = new BillingIntervalDetail(from, to, targetDate, bcd, BillingPeriod.THIRTY_DAYS, BillingMode.IN_ADVANCE);
        Assert.assertEquals(thirtyDaysBillingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-01-16"));
    }

    /*
     *
     *         Start             END_MONTH    BCD
     * |---------|-------------------| - - - -|
     *
     */
    @Test(groups = "fast")
    public void testCalculateFirstBillingCycleDate2() throws Exception {
        final LocalDate from = new LocalDate("2012-02-16");
        final LocalDate to = null;
        final LocalDate targetDate = new LocalDate();
        final int bcd = 30;

        final BillingIntervalDetail annualBillingIntervalDetail = new BillingIntervalDetail(from, to, targetDate, bcd, BillingPeriod.ANNUAL, BillingMode.IN_ADVANCE);
        Assert.assertEquals(annualBillingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-02-29"));

        final BillingIntervalDetail monthlyBillingIntervalDetail = new BillingIntervalDetail(from, to, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ADVANCE);
        Assert.assertEquals(monthlyBillingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-02-29"));

        final BillingIntervalDetail thirtyDaysBillingIntervalDetail = new BillingIntervalDetail(from, to, targetDate, bcd, BillingPeriod.THIRTY_DAYS, BillingMode.IN_ADVANCE);
        Assert.assertEquals(thirtyDaysBillingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-02-16"));
    }

    /*
     * Here the interesting part is that BCD is prior start
     *
     *                                      Start
     *                              BCD     END_MONTH
     * |----------------------------|--------|
     *
     */
    @Test(groups = "fast")
    public void testCalculateFirstBillingCycleDate3() throws Exception {
        final LocalDate from = new LocalDate("2012-01-31");
        final LocalDate to = null;
        final LocalDate targetDate = new LocalDate();
        final int bcd = 30;

        final BillingIntervalDetail annualBillingIntervalDetail = new BillingIntervalDetail(from, to, targetDate, bcd, BillingPeriod.ANNUAL, BillingMode.IN_ADVANCE);
        Assert.assertEquals(annualBillingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2013-01-30"));

        final BillingIntervalDetail monthlyBillingIntervalDetail = new BillingIntervalDetail(from, to, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ADVANCE);
        Assert.assertEquals(monthlyBillingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-02-29"));

        final BillingIntervalDetail thirtyDaysBillingIntervalDetail = new BillingIntervalDetail(from, to, targetDate, bcd, BillingPeriod.THIRTY_DAYS, BillingMode.IN_ADVANCE);
        Assert.assertEquals(thirtyDaysBillingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-01-31"));
    }

    /*
     *
     *         BCD                 Start      END_MONTH
     * |---------|-------------------|-----------|
     *
     */
    @Test(groups = "fast")
    public void testCalculateFirstBillingCycleDate4() throws Exception {
        final LocalDate from = new LocalDate("2012-02-16");
        final LocalDate to = null;
        final LocalDate targetDate = new LocalDate();
        final int bcd = 14;

        final BillingIntervalDetail annualBillingIntervalDetail = new BillingIntervalDetail(from, to, targetDate, bcd, BillingPeriod.ANNUAL, BillingMode.IN_ADVANCE);
        Assert.assertEquals(annualBillingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2013-02-14"));

        final BillingIntervalDetail monthlyBillingIntervalDetail = new BillingIntervalDetail(from, to, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ADVANCE);
        Assert.assertEquals(monthlyBillingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-03-14"));

        final BillingIntervalDetail thirtyDaysBillingIntervalDetail = new BillingIntervalDetail(from, to, targetDate, bcd, BillingPeriod.THIRTY_DAYS, BillingMode.IN_ADVANCE);
        Assert.assertEquals(thirtyDaysBillingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-02-16"));
    }

    @Test(groups = "fast")
    public void testNextBCDShouldNotBeInThePast() throws Exception {
        final LocalDate from = new LocalDate("2012-07-16");
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(from, null, new LocalDate(), 15, BillingPeriod.MONTHLY, BillingMode.IN_ADVANCE);
        final LocalDate to = billingIntervalDetail.getFirstBillingCycleDate();
        Assert.assertEquals(to, new LocalDate("2012-08-15"));
    }

    @Test(groups = "fast")
    public void testBeforeBCDWithOnOrAfter() throws Exception {
        final LocalDate from = new LocalDate("2012-03-02");
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(from, null, new LocalDate(), 3, BillingPeriod.MONTHLY, BillingMode.IN_ADVANCE);
        final LocalDate to = billingIntervalDetail.getFirstBillingCycleDate();
        Assert.assertEquals(to, new LocalDate("2012-03-03"));
    }

    @Test(groups = "fast")
    public void testEqualBCDWithOnOrAfter() throws Exception {
        final LocalDate from = new LocalDate("2012-03-03");
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(from, null, new LocalDate(), 3, BillingPeriod.MONTHLY, BillingMode.IN_ADVANCE);
        final LocalDate to = billingIntervalDetail.getFirstBillingCycleDate();
        Assert.assertEquals(to, new LocalDate("2012-03-03"));
    }

    @Test(groups = "fast")
    public void testAfterBCDWithOnOrAfter() throws Exception {
        final LocalDate from = new LocalDate("2012-03-04");
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(from, null, new LocalDate(), 3, BillingPeriod.MONTHLY, BillingMode.IN_ADVANCE);
        final LocalDate to = billingIntervalDetail.getFirstBillingCycleDate();
        Assert.assertEquals(to, new LocalDate("2012-04-03"));
    }

    @Test(groups = "fast")
    public void testEffectiveEndDate() throws Exception {
        final LocalDate firstBCD = new LocalDate(2012, 7, 16);
        final LocalDate targetDate = new LocalDate(2012, 8, 16);
        final BillingPeriod billingPeriod = BillingPeriod.MONTHLY;

        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(firstBCD, null, targetDate, 16, billingPeriod, BillingMode.IN_ADVANCE);
        final LocalDate effectiveEndDate = billingIntervalDetail.getEffectiveEndDate();
        Assert.assertEquals(effectiveEndDate, new LocalDate(2012, 9, 16));
    }

    @Test(groups = "fast")
    public void testLastBCD() throws Exception {
        final LocalDate start = new LocalDate(2012, 7, 16);
        final LocalDate endDate = new LocalDate(2012, 9, 15); // so we get effectiveEndDate on 9/15
        final LocalDate targetDate = new LocalDate(2012, 8, 16);

        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, endDate, targetDate, 16, BillingPeriod.MONTHLY, BillingMode.IN_ADVANCE);
        final LocalDate lastBCD = billingIntervalDetail.getLastBillingCycleDate();
        Assert.assertEquals(lastBCD, new LocalDate(2012, 8, 16));
    }

    @Test(groups = "fast")
    public void testLastBCDShouldNotBeBeforePreviousBCD() throws Exception {
        final LocalDate start = new LocalDate("2012-07-16");
        final int bcdLocal = 15;

        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, null, start, bcdLocal, BillingPeriod.MONTHLY, BillingMode.IN_ADVANCE);
        final LocalDate lastBCD = billingIntervalDetail.getLastBillingCycleDate();
        Assert.assertEquals(lastBCD, new LocalDate("2012-08-15"));
    }

    @Test(groups = "fast")
    public void testBCD31StartingWith30DayMonth() throws Exception {
        final LocalDate start = new LocalDate("2012-04-30");
        final LocalDate targetDate = new LocalDate("2012-04-30");
        final LocalDate end = null;
        final int bcdLocal = 31;

        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, end, targetDate, bcdLocal, BillingPeriod.MONTHLY, BillingMode.IN_ADVANCE);
        final LocalDate effectiveEndDate = billingIntervalDetail.getEffectiveEndDate();
        Assert.assertEquals(effectiveEndDate, new LocalDate("2012-05-31"));
    }


    @Test(groups = "fast", description= "See https://github.com/killbill/killbill/issues/127#issuecomment-292445089")
    public void testWithBCDLargerThanEndMonth() throws Exception {
        final LocalDate startDate = new LocalDate("2017-01-31");
        final LocalDate endDate = null;
        final LocalDate targetDate = new LocalDate("2017-03-31");
        int BCD = 31;
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(startDate, endDate, targetDate, BCD, BillingPeriod.MONTHLY, BillingMode.IN_ADVANCE);

        final LocalDate firstBillingCycleDate = billingIntervalDetail.getFirstBillingCycleDate();
        final LocalDate lastBillingCycleDate = billingIntervalDetail.getLastBillingCycleDate();

        final int numberOfWholeBillingPeriods = calculateNumberOfWholeBillingPeriods(firstBillingCycleDate, lastBillingCycleDate, BillingPeriod.MONTHLY);
        Assert.assertEquals(numberOfWholeBillingPeriods, 3);
    }

}
