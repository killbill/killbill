/*
 * Copyright 2020-2024 Equinix, Inc
 * Copyright 2014-2024 The Billing Project, LLC
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
import java.util.HashMap;
import java.util.Map;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestInvoiceDateUtilsWithFixedProration extends InvoiceTestSuiteNoDB {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.invoice.proration.fixed.days", "30");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "fast")
    public void testProRationAfterLastBillingCycleDate() throws Exception {
        LocalDate endDate = new LocalDate("2023-06-15");
        LocalDate previousBillThroughDate = new LocalDate("2023-05-23");
        BigDecimal proration = InvoiceDateUtils.calculateProRationAfterLastBillingCycleDate(endDate, previousBillThroughDate, BillingPeriod.MONTHLY, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(proration, new BigDecimal("0.733333333"));

        endDate = new LocalDate("2023-07-15");
        previousBillThroughDate = new LocalDate("2023-06-23");
        proration = InvoiceDateUtils.calculateProRationAfterLastBillingCycleDate(endDate, previousBillThroughDate, BillingPeriod.MONTHLY, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(proration, new BigDecimal("0.733333333"));

        //Feb
        endDate = new LocalDate("2023-03-15");
        previousBillThroughDate = new LocalDate("2023-02-23");
        proration = InvoiceDateUtils.calculateProRationAfterLastBillingCycleDate(endDate, previousBillThroughDate, BillingPeriod.MONTHLY, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(proration, new BigDecimal("0.733333333"));

        //Feb with leap year
        endDate = new LocalDate("2024-03-15");
        previousBillThroughDate = new LocalDate("2024-02-23");
        proration = InvoiceDateUtils.calculateProRationAfterLastBillingCycleDate(endDate, previousBillThroughDate, BillingPeriod.MONTHLY, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(proration, new BigDecimal("0.733333333"));

    }

    @Test(groups = "fast")
    public void testProRationBeforeFirstBillingPeriod1() throws Exception {
        LocalDate startDate = new LocalDate("2023-05-23");
        LocalDate nextBillingCycleDate = new LocalDate("2023-06-15");
        BigDecimal proration = InvoiceDateUtils.calculateProRationBeforeFirstBillingPeriod(startDate, nextBillingCycleDate, BillingPeriod.MONTHLY, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(proration, new BigDecimal("0.733333333"));

        startDate = new LocalDate("2023-06-23");
        nextBillingCycleDate = new LocalDate("2023-07-15");
        proration = InvoiceDateUtils.calculateProRationBeforeFirstBillingPeriod(startDate, nextBillingCycleDate, BillingPeriod.MONTHLY, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(proration, new BigDecimal("0.733333333"));

        //Feb
        startDate = new LocalDate("2023-02-23");
        nextBillingCycleDate = new LocalDate("2023-03-15");
        proration = InvoiceDateUtils.calculateProRationBeforeFirstBillingPeriod(startDate, nextBillingCycleDate, BillingPeriod.MONTHLY, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(proration, new BigDecimal("0.733333333"));

        //Feb with leap year
        startDate = new LocalDate("2024-02-23");
        nextBillingCycleDate = new LocalDate("2024-03-15");
        proration = InvoiceDateUtils.calculateProRationBeforeFirstBillingPeriod(startDate, nextBillingCycleDate, BillingPeriod.MONTHLY, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(proration, new BigDecimal("0.733333333"));

    }

    @Test(groups = "fast")
    public void testProRationBeforeFirstBillingPeriod2() throws Exception {
        LocalDate startDate = new LocalDate("2023-05-15");
        LocalDate nextBillingCycleDate = new LocalDate("2023-05-23");
        BigDecimal proration = InvoiceDateUtils.calculateProRationBeforeFirstBillingPeriod(startDate, nextBillingCycleDate, BillingPeriod.MONTHLY, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(proration, new BigDecimal("0.266666667"));

        startDate = new LocalDate("2023-06-15");
        nextBillingCycleDate = new LocalDate("2023-06-23");
        proration = InvoiceDateUtils.calculateProRationBeforeFirstBillingPeriod(startDate, nextBillingCycleDate, BillingPeriod.MONTHLY, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(proration, new BigDecimal("0.266666667"));
    }

    @Test(groups = "fast")
    public void testDaysBetweenWithFixedDaysInMonthForSameMonth() {
        LocalDate startDate = new LocalDate("2023-05-15");
        LocalDate endDate = new LocalDate("2023-05-23");
        int days = InvoiceDateUtils.daysBetweenWithFixedDaysInMonth(startDate, endDate, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(days, 8);

        startDate = new LocalDate("2023-06-15");
        endDate = new LocalDate("2023-06-23");
        days = InvoiceDateUtils.daysBetweenWithFixedDaysInMonth(startDate, endDate, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(days, 8);

        //Feb
        startDate = new LocalDate("2023-02-15");
        endDate = new LocalDate("2023-02-23");
        days = InvoiceDateUtils.daysBetweenWithFixedDaysInMonth(startDate, endDate, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(days, 8);

        //Feb wih leap year
        startDate = new LocalDate("2024-02-15");
        endDate = new LocalDate("2024-02-23");
        days = InvoiceDateUtils.daysBetweenWithFixedDaysInMonth(startDate, endDate, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(days, 8);
    }

    @Test(groups = "fast")
    public void testDaysBetweenWithFixedDaysInMonthForDifferentMonths() {
        LocalDate startDate = new LocalDate("2023-05-23");
        LocalDate endDate = new LocalDate("2023-06-15");
        int days = InvoiceDateUtils.daysBetweenWithFixedDaysInMonth(startDate, endDate, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(days, 22);

        startDate = new LocalDate("2023-06-23");
        endDate = new LocalDate("2023-07-15");
        days = InvoiceDateUtils.daysBetweenWithFixedDaysInMonth(startDate, endDate, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(days, 22);

        //Feb
        startDate = new LocalDate("2023-02-23");
        endDate = new LocalDate("2023-03-15");
        days = InvoiceDateUtils.daysBetweenWithFixedDaysInMonth(startDate, endDate, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(days, 22);

        //Feb wih leap year
        startDate = new LocalDate("2024-02-23");
        endDate = new LocalDate("2024-03-15");
        days = InvoiceDateUtils.daysBetweenWithFixedDaysInMonth(startDate, endDate, invoiceConfig.getProrationFixedDays());
        Assert.assertEquals(days, 22);
    }

    @Test(groups = "fast")
    public void testDaysBetweenWithFixedDaysInMonth28() {
        final int fixedDaysInMonth = 28;

        LocalDate startDate = new LocalDate("2023-05-23");
        LocalDate endDate = new LocalDate("2023-06-15");
        int days = InvoiceDateUtils.daysBetweenWithFixedDaysInMonth(startDate, endDate, fixedDaysInMonth);
        Assert.assertEquals(days, 20);

        startDate = new LocalDate("2023-06-23");
        endDate = new LocalDate("2023-07-15");
        days = InvoiceDateUtils.daysBetweenWithFixedDaysInMonth(startDate, endDate, fixedDaysInMonth);
        Assert.assertEquals(days, 20);

        //Feb
        startDate = new LocalDate("2023-02-23");
        endDate = new LocalDate("2023-03-15");
        days = InvoiceDateUtils.daysBetweenWithFixedDaysInMonth(startDate, endDate, fixedDaysInMonth);
        Assert.assertEquals(days, 20);

        //Feb wih leap year
        startDate = new LocalDate("2024-02-23");
        endDate = new LocalDate("2024-03-15");
        days = InvoiceDateUtils.daysBetweenWithFixedDaysInMonth(startDate, endDate, fixedDaysInMonth);
        Assert.assertEquals(days, 20);

    }

}
