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

package com.ning.billing.invoice.model;

import java.math.BigDecimal;
import java.util.List;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;

public class TestInAdvanceBillingMode {

    @Test(groups = "fast")
    public void testCalculateSimpleInvoiceItemWithBCDBeforeStartDay() throws Exception {
        final InAdvanceBillingMode billingMode = new InAdvanceBillingMode();
        final LocalDate startDate = new LocalDate(2012, 7, 16);
        final LocalDate endDate = new LocalDate(2012, 8, 16);
        final LocalDate targetDate = new LocalDate(2012, 7, 16);
        final DateTimeZone dateTimeZone = DateTimeZone.forID("Pacific/Pitcairn");
        final int billingCycleDayLocal = 15;
        final BillingPeriod billingPeriod = BillingPeriod.MONTHLY;
        final LocalDate servicePeriodEndDate = new LocalDate(2012, 8, 15);

        verifyInvoiceItems(billingMode, startDate, endDate, targetDate, dateTimeZone, billingCycleDayLocal, billingPeriod, servicePeriodEndDate);
    }

    @Test(groups = "fast")
    public void testCalculateSimpleInvoiceItemWithBCDEqualsStartDay() throws Exception {
        final InAdvanceBillingMode billingMode = new InAdvanceBillingMode();
        final LocalDate startDate = new LocalDate(2012, 7, 16);
        final LocalDate endDate = new LocalDate(2012, 8, 16);
        final LocalDate targetDate = new LocalDate(2012, 7, 16);
        final DateTimeZone dateTimeZone = DateTimeZone.forID("Pacific/Pitcairn");
        final int billingCycleDayLocal = 16;
        final BillingPeriod billingPeriod = BillingPeriod.MONTHLY;
        final LocalDate servicePeriodEndDate = new LocalDate(2012, 8, 16);

        verifyInvoiceItems(billingMode, startDate, endDate, targetDate, dateTimeZone, billingCycleDayLocal, billingPeriod, servicePeriodEndDate);
    }

    @Test(groups = "fast")
    public void testCalculateSimpleInvoiceItemWithBCDAfterStartDay() throws Exception {
        final InAdvanceBillingMode billingMode = new InAdvanceBillingMode();
        final LocalDate startDate = new LocalDate(2012, 7, 16);
        final LocalDate endDate = new LocalDate(2012, 8, 16);
        final LocalDate targetDate = new LocalDate(2012, 7, 16);
        final DateTimeZone dateTimeZone = DateTimeZone.forID("Pacific/Pitcairn");
        final int billingCycleDayLocal = 17;
        final BillingPeriod billingPeriod = BillingPeriod.MONTHLY;
        final LocalDate servicePeriodEndDate = new LocalDate(2012, 7, 17);

        verifyInvoiceItems(billingMode, startDate, endDate, targetDate, dateTimeZone, billingCycleDayLocal, billingPeriod, servicePeriodEndDate);
    }

    private void verifyInvoiceItems(final InAdvanceBillingMode billingMode, final LocalDate startDate, final LocalDate endDate, final LocalDate targetDate,
                                    final DateTimeZone dateTimeZone, final int billingCycleDayLocal, final BillingPeriod billingPeriod, final LocalDate servicePeriodEndDate) throws InvalidDateSequenceException {
        final List<RecurringInvoiceItemData> invoiceItems = billingMode.calculateInvoiceItemData(startDate, endDate, targetDate, dateTimeZone, billingCycleDayLocal, billingPeriod);
        Assert.assertEquals(invoiceItems.size(), 1);
        Assert.assertEquals(invoiceItems.get(0).getStartDate(), startDate);
        Assert.assertEquals(invoiceItems.get(0).getEndDate(), servicePeriodEndDate);
        Assert.assertTrue(invoiceItems.get(0).getNumberOfCycles().compareTo(BigDecimal.ONE) <= 0);
    }
}
