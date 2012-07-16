/*
 * Copyright 2010-2012 Ning, Inc.
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

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;

public class TestInvoiceDateUtils {

    @Test(groups = "fast")
    public void testProRationAfterLastBillingCycleDate() throws Exception {
        final LocalDate endDate = new LocalDate("2012-06-02");
        final LocalDate previousBillThroughDate = new LocalDate("2012-03-02");
        final BigDecimal proration = InvoiceDateUtils.calculateProRationAfterLastBillingCycleDate(endDate, previousBillThroughDate, BillingPeriod.MONTHLY);
        Assert.assertEquals(proration, new BigDecimal("2.9677"));
    }

    @Test(groups = "fast")
    public void testBeforeBCDWithAfter() throws Exception {
        final LocalDate from = new LocalDate("2012-03-02");
        final LocalDate to = InvoiceDateUtils.calculateBillingCycleDateAfter(from, DateTimeZone.UTC, 3);
        Assert.assertEquals(to, new LocalDate("2012-03-03"));
    }

    @Test(groups = "fast")
    public void testEqualBCDWithAfter() throws Exception {
        final LocalDate from = new LocalDate("2012-03-03");
        final LocalDate to = InvoiceDateUtils.calculateBillingCycleDateAfter(from, DateTimeZone.UTC, 3);
        Assert.assertEquals(to, new LocalDate("2012-04-03"));
    }

    @Test(groups = "fast")
    public void testAfterBCDWithAfter() throws Exception {
        final LocalDate from = new LocalDate("2012-03-04");
        final LocalDate to = InvoiceDateUtils.calculateBillingCycleDateAfter(from, DateTimeZone.UTC, 3);
        Assert.assertEquals(to, new LocalDate("2012-04-03"));
    }

    @Test(groups = "fast")
    public void testBeforeBCDWithOnOrAfter() throws Exception {
        final LocalDate from = new LocalDate("2012-03-02");
        final LocalDate to = InvoiceDateUtils.calculateBillingCycleDateOnOrAfter(from, DateTimeZone.UTC, 3);
        Assert.assertEquals(to, new LocalDate("2012-03-03"));
    }

    @Test(groups = "fast")
    public void testEqualBCDWithOnOrAfter() throws Exception {
        final LocalDate from = new LocalDate("2012-03-03");
        final LocalDate to = InvoiceDateUtils.calculateBillingCycleDateOnOrAfter(from, DateTimeZone.UTC, 3);
        Assert.assertEquals(to, new LocalDate("2012-03-03"));
    }

    @Test(groups = "fast")
    public void testAfterBCDWithOnOrAfter() throws Exception {
        final LocalDate from = new LocalDate("2012-03-04");
        final LocalDate to = InvoiceDateUtils.calculateBillingCycleDateOnOrAfter(from, DateTimeZone.UTC, 3);
        Assert.assertEquals(to, new LocalDate("2012-04-03"));
    }
}
