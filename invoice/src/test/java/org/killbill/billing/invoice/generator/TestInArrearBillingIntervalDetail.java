/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

public class TestInArrearBillingIntervalDetail extends InvoiceTestSuiteNoDB {


    /*
     *           TD
     * BCD      Start
     * |---------|-----------------
     *
     */
    @Test(groups = "fast")
    public void testScenarioBCDBeforeStart1() throws Exception {
        final LocalDate start = new LocalDate("2012-01-16");
        final LocalDate targetDate = new LocalDate("2012-01-16");
        final int bcd = 13;
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, null, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ARREAR);

        Assert.assertFalse(billingIntervalDetail.hasSomethingToBill());
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-02-13"));
        Assert.assertNull(billingIntervalDetail.getEffectiveEndDate());
        Assert.assertEquals(billingIntervalDetail.getNextBillingCycleDate(), new LocalDate("2012-02-13"));
    }

    /*
     *
     * BCD      Start       TD = (next BCD)
     * |---------|----------|-------
     *
     */
    @Test(groups = "fast")
    public void testScenarioBCDBeforeStart2() throws Exception {
        final LocalDate start = new LocalDate("2012-01-16");
        final LocalDate targetDate = new LocalDate("2012-02-13");
        final int bcd = 13;
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, null, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ARREAR);

        Assert.assertTrue(billingIntervalDetail.hasSomethingToBill());
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-02-13"));
        Assert.assertEquals(billingIntervalDetail.getEffectiveEndDate(), new LocalDate("2012-02-13"));
        Assert.assertEquals(billingIntervalDetail.getNextBillingCycleDate(), new LocalDate("2012-03-13"));
    }


    /*
     * BCD
     * Start     TD
     * |---------|-----------------
     *
     */
    @Test(groups = "fast")
    public void testScenarioBCDAEqualsStart1() throws Exception {
        final LocalDate start = new LocalDate("2012-01-16");
        final LocalDate targetDate = new LocalDate("2012-01-19");
        final int bcd = 16;
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, null, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ARREAR);

        Assert.assertTrue(billingIntervalDetail.hasSomethingToBill());
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-01-16"));
        Assert.assertEquals(billingIntervalDetail.getEffectiveEndDate(), new LocalDate("2012-01-16"));
        Assert.assertEquals(billingIntervalDetail.getNextBillingCycleDate(), new LocalDate("2012-02-16"));
    }


    /*
     *
     * Start     TD         BCD
     * |---------|----------|-------
     *
     */
    @Test(groups = "fast")
    public void testScenarioBCDAfterStart1() throws Exception {
        final LocalDate start = new LocalDate("2012-01-16");
        final LocalDate targetDate = new LocalDate("2012-01-19");
        final int bcd = 25;
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, null, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ARREAR);

        Assert.assertFalse(billingIntervalDetail.hasSomethingToBill());
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-01-25"));
        Assert.assertNull(billingIntervalDetail.getEffectiveEndDate());
        Assert.assertEquals(billingIntervalDetail.getNextBillingCycleDate(), new LocalDate("2012-01-25"));
    }


    /*
     *                       TD
     * Start    End          BCD
     * |---------|------------|-------
     *
     */
    @Test(groups = "fast")
    public void testScenarioBCDAfterStart2() throws Exception {
        final LocalDate start = new LocalDate("2012-01-16");
        final LocalDate end = new LocalDate("2012-01-19");
        final LocalDate targetDate = new LocalDate("2012-01-25");
        final int bcd = 25;
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, end, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ARREAR);

        Assert.assertTrue(billingIntervalDetail.hasSomethingToBill());
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-01-25"));
        Assert.assertEquals(billingIntervalDetail.getEffectiveEndDate(), end);
        // STEPH maybe we should change because we actually don't want a notification
        Assert.assertEquals(billingIntervalDetail.getNextBillingCycleDate(), new LocalDate("2012-01-25"));
    }


    /*
     *                     TD
     * Start              BCD
     * |--------------------|-------
     *
     */
    @Test(groups = "fast")
    public void testScenarioBCDAfterStart3() throws Exception {
        final LocalDate start = new LocalDate("2012-01-16");
        final LocalDate targetDate = new LocalDate("2012-01-25");
        final int bcd = 25;
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, null, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ARREAR);

        Assert.assertTrue(billingIntervalDetail.hasSomethingToBill());
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-01-25"));
        Assert.assertEquals(billingIntervalDetail.getEffectiveEndDate(), new LocalDate("2012-01-25"));
        Assert.assertEquals(billingIntervalDetail.getNextBillingCycleDate(), new LocalDate("2012-02-25"));
    }


    /*
     *
     * Start   BCD   end   TD  next BCD
     * |-------|------|----|------|---
     *
     */
    @Test(groups = "fast")
    public void testScenarioEndDateBetweenPeriod1() throws Exception {
        final LocalDate start = new LocalDate("2012-01-16");
        final LocalDate end = new LocalDate("2012-01-20");
        final LocalDate targetDate = new LocalDate("2012-01-25");
        final int bcd = 18;
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, end, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ARREAR);

        Assert.assertTrue(billingIntervalDetail.hasSomethingToBill());
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-01-18"));
        Assert.assertEquals(billingIntervalDetail.getEffectiveEndDate(), new LocalDate("2012-01-20"));
        Assert.assertEquals(billingIntervalDetail.getNextBillingCycleDate(), new LocalDate("2012-02-18"));
    }

    /*
     *
     * Start   BCD       TD  next BCD
     * |-------|----------|------|---
     *
     */
    @Test(groups = "fast")
    public void testScenarioEndDateBetweenPeriod2() throws Exception {
        final LocalDate start = new LocalDate("2012-01-16");
        final LocalDate targetDate = new LocalDate("2012-01-25");
        final int bcd = 18;
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, null, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ARREAR);

        Assert.assertTrue(billingIntervalDetail.hasSomethingToBill());
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-01-18"));
        Assert.assertEquals(billingIntervalDetail.getEffectiveEndDate(), new LocalDate("2012-01-18"));
        Assert.assertEquals(billingIntervalDetail.getNextBillingCycleDate(), new LocalDate("2012-02-18"));
    }

    /*
     *
     * Start   BCD       TD    end   next BCD
     * |-------|----------|----|------|---
     *
     */
    @Test(groups = "fast")
    public void testScenarioEndDateBetweenPeriod3() throws Exception {
        final LocalDate start = new LocalDate("2012-01-16");
        final LocalDate end = new LocalDate("2012-01-28");
        final LocalDate targetDate = new LocalDate("2012-01-25");
        final int bcd = 18;
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, end, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ARREAR);

        Assert.assertTrue(billingIntervalDetail.hasSomethingToBill());
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-01-18"));
        Assert.assertEquals(billingIntervalDetail.getEffectiveEndDate(), new LocalDate("2012-01-18"));
        Assert.assertEquals(billingIntervalDetail.getNextBillingCycleDate(), new LocalDate("2012-02-18"));
    }

    /*
     *                              TD
     * Start   BCD              next BCD
     * |-------|--------------------|---
     *
     */
    @Test(groups = "fast")
    public void testScenarioTargetDateOnNextBCD1() throws Exception {
        final LocalDate start = new LocalDate("2012-01-16");
        final LocalDate targetDate = new LocalDate("2012-02-18");
        final int bcd = 18;
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, null, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ARREAR);

        Assert.assertTrue(billingIntervalDetail.hasSomethingToBill());
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-01-18"));
        Assert.assertEquals(billingIntervalDetail.getEffectiveEndDate(), new LocalDate("2012-02-18"));
        Assert.assertEquals(billingIntervalDetail.getNextBillingCycleDate(), new LocalDate("2012-03-18"));
    }

    /*
     *                              TD
     * Start   BCD          end    next BCD
     * |-------|-------------|-------|---
     *
     */
    @Test(groups = "fast")
    public void testScenarioTargetDateOnNextBCD2() throws Exception {
        final LocalDate start = new LocalDate("2012-01-16");
        final LocalDate end = new LocalDate("2012-02-16");
        final LocalDate targetDate = new LocalDate("2012-02-18");
        final int bcd = 18;
        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, end, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ARREAR);

        Assert.assertTrue(billingIntervalDetail.hasSomethingToBill());
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2012-01-18"));
        Assert.assertEquals(billingIntervalDetail.getEffectiveEndDate(), new LocalDate("2012-02-16"));
        Assert.assertEquals(billingIntervalDetail.getNextBillingCycleDate(), new LocalDate("2012-02-18"));
    }
}
