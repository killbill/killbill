/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

public class TestInArrearGreedyBillingIntervalDetail extends InvoiceTestSuiteNoDB {


    @Test(groups = "fast")
    public void testScenarioBillRun_A() {

        // Simulate a bill-run on the first and verify we bill the current period
        // start date aligns with BCD
        final LocalDate targetDate = new LocalDate("2022-12-01");
        final LocalDate start = new LocalDate("2022-11-07");
        final int bcd = 7;

        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, null, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ARREAR, true);
        Assert.assertTrue(billingIntervalDetail.hasSomethingToBill());
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2022-11-07"));
        Assert.assertEquals(billingIntervalDetail.getEffectiveEndDate(), new LocalDate("2022-12-07"));
        Assert.assertEquals(billingIntervalDetail.getNextBillingCycleDate(), new LocalDate("2023-01-07"));
    }


    @Test(groups = "fast")
    public void testScenarioBillRun_B() {

        // Simulate a bill-run on the first and verify we bill the current period and the past periods
        // start date aligns with BCD
        final LocalDate targetDate = new LocalDate("2022-12-01");
        final LocalDate start = new LocalDate("2022-09-07");
        final int bcd = 7;

        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, null, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ARREAR, true);
        Assert.assertTrue(billingIntervalDetail.hasSomethingToBill());
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2022-09-07"));
        Assert.assertEquals(billingIntervalDetail.getEffectiveEndDate(), new LocalDate("2022-12-07"));
        Assert.assertEquals(billingIntervalDetail.getNextBillingCycleDate(), new LocalDate("2023-01-07"));
    }



    @Test(groups = "fast")
    public void testScenarioBillRun_C() {

        // Simulate a bill-run on the startDate and verify we **don't bill** the upcoming period (we don't bill in-advance)
        // start date aligns with BCD
        final LocalDate targetDate = new LocalDate("2022-11-01");
        final LocalDate start = new LocalDate("2022-11-01");
        final int bcd = 1;

        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, null, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ARREAR, true);

        Assert.assertTrue(billingIntervalDetail.hasSomethingToBill());
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2022-11-01"));
        Assert.assertEquals(billingIntervalDetail.getEffectiveEndDate(), new LocalDate("2022-11-01"));
        Assert.assertEquals(billingIntervalDetail.getNextBillingCycleDate(), new LocalDate("2022-12-01"));
    }

    @Test(groups = "fast")
    public void testScenarioBillRun_D() {

        // Simulate a bill-run on the startDate and verify we **don't bill** the upcoming period (we don't bill in-advance)
        // start date aligns with BCD
        final LocalDate targetDate = new LocalDate("2022-11-01");
        final LocalDate start = new LocalDate("2022-09-01");
        final int bcd = 1;

        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, null, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ARREAR, true);

        Assert.assertTrue(billingIntervalDetail.hasSomethingToBill());
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2022-09-01"));
        Assert.assertEquals(billingIntervalDetail.getEffectiveEndDate(), new LocalDate("2022-11-01"));
        Assert.assertEquals(billingIntervalDetail.getNextBillingCycleDate(), new LocalDate("2022-12-01"));
    }


    @Test(groups = "fast")
    public void testScenarioBillRun_E() {

        // Simulate a bill-run on the first and verify we bill the current period
        // start date aligns with BCD
        // endDate prior targetDate
        final LocalDate targetDate = new LocalDate("2022-12-01");
        final LocalDate start = new LocalDate("2022-11-07");
        final LocalDate end = new LocalDate("2022-11-15");
        final int bcd = 7;

        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, end, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ARREAR, true);
        Assert.assertTrue(billingIntervalDetail.hasSomethingToBill());
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2022-11-07"));
        Assert.assertEquals(billingIntervalDetail.getEffectiveEndDate(), new LocalDate("2022-11-15"));
        Assert.assertEquals(billingIntervalDetail.getNextBillingCycleDate(), new LocalDate("2022-12-07"));
    }

    @Test(groups = "fast")
    public void testScenarioBillRun_F() {

        // Simulate a bill-run on the first and verify we bill the current period
        // start date aligns with BCD
        // endDate after targetDate (but prior nextBCD)
        final LocalDate targetDate = new LocalDate("2022-12-01");
        final LocalDate start = new LocalDate("2022-11-07");
        final LocalDate end = new LocalDate("2022-12-02");
        final int bcd = 7;

        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, end, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ARREAR, true);
        Assert.assertTrue(billingIntervalDetail.hasSomethingToBill());
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2022-11-07"));
        Assert.assertEquals(billingIntervalDetail.getEffectiveEndDate(), new LocalDate("2022-12-02"));
        Assert.assertEquals(billingIntervalDetail.getNextBillingCycleDate(), new LocalDate("2022-12-07"));
    }

    @Test(groups = "fast")
    public void testScenarioBillRun_G() {

        // Simulate a bill-run on the first and verify we bill the current period
        // start date aligns with BCD
        // endDate after targetDate and nextBCD
        final LocalDate targetDate = new LocalDate("2022-12-01");
        final LocalDate start = new LocalDate("2022-11-07");
        final LocalDate end = new LocalDate("2022-12-08");
        final int bcd = 7;

        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(start, end, targetDate, bcd, BillingPeriod.MONTHLY, BillingMode.IN_ARREAR, true);
        Assert.assertTrue(billingIntervalDetail.hasSomethingToBill());
        Assert.assertEquals(billingIntervalDetail.getFirstBillingCycleDate(), new LocalDate("2022-11-07"));
        Assert.assertEquals(billingIntervalDetail.getEffectiveEndDate(), new LocalDate("2022-12-07"));
        Assert.assertEquals(billingIntervalDetail.getNextBillingCycleDate(), new LocalDate("2023-01-07"));
    }

}
