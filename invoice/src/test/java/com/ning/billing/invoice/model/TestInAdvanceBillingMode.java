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
import java.util.LinkedHashMap;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;

public class TestInAdvanceBillingMode {

    private static final DateTimeZone TIMEZONE = DateTimeZone.forID("Pacific/Pitcairn");
    private static final BillingPeriod BILLING_PERIOD = BillingPeriod.MONTHLY;

    @Test(groups = "fast")
    public void testItemShouldNotStartInThePast() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 7, 16);
        final LocalDate endDate = new LocalDate(2012, 7, 16);
        final LocalDate targetDate = new LocalDate(2012, 7, 16);
        final int billingCycleDayLocal = 15;

        final LinkedHashMap<LocalDate, LocalDate> expectedDates = new LinkedHashMap<LocalDate, LocalDate>();
        verifyInvoiceItems(startDate, endDate, targetDate, TIMEZONE, billingCycleDayLocal, BILLING_PERIOD, expectedDates);
    }

    @Test(groups = "fast")
    public void testCalculateSimpleInvoiceItemWithNoEndDate() throws Exception {
        final LocalDate startDate = new LocalDate(new DateTime("2012-07-17T02:25:33.000Z", DateTimeZone.UTC), TIMEZONE);
        final LocalDate endDate = null;
        final LocalDate targetDate = new LocalDate(2012, 7, 16);
        final int billingCycleDayLocal = 15;

        final LinkedHashMap<LocalDate, LocalDate> expectedDates = new LinkedHashMap<LocalDate, LocalDate>();
        expectedDates.put(new LocalDate(2012, 7, 16), new LocalDate(2012, 8, 15));

        verifyInvoiceItems(startDate, endDate, targetDate, TIMEZONE, billingCycleDayLocal, BILLING_PERIOD, expectedDates);
    }

    @Test(groups = "fast")
    public void testCalculateSimpleInvoiceItemWithBCDBeforeStartDay() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 7, 16);
        final LocalDate endDate = new LocalDate(2012, 8, 16);
        final LocalDate targetDate = new LocalDate(2012, 7, 16);
        final int billingCycleDayLocal = 15;

        final LinkedHashMap<LocalDate, LocalDate> expectedDates = new LinkedHashMap<LocalDate, LocalDate>();
        expectedDates.put(new LocalDate(2012, 7, 16), new LocalDate(2012, 8, 15));

        verifyInvoiceItems(startDate, endDate, targetDate, TIMEZONE, billingCycleDayLocal, BILLING_PERIOD, expectedDates);
    }

    @Test(groups = "fast")
    public void testCalculateSimpleInvoiceItemWithBCDEqualsStartDay() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 7, 16);
        final LocalDate endDate = new LocalDate(2012, 8, 16);
        final LocalDate targetDate = new LocalDate(2012, 7, 16);
        final int billingCycleDayLocal = 16;

        final LinkedHashMap<LocalDate, LocalDate> expectedDates = new LinkedHashMap<LocalDate, LocalDate>();
        expectedDates.put(new LocalDate(2012, 7, 16), new LocalDate(2012, 8, 16));

        verifyInvoiceItems(startDate, endDate, targetDate, TIMEZONE, billingCycleDayLocal, BILLING_PERIOD, expectedDates);
    }

    @Test(groups = "fast")
    public void testCalculateSimpleInvoiceItemWithBCDAfterStartDay() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 7, 16);
        final LocalDate endDate = new LocalDate(2012, 8, 16);
        final LocalDate targetDate = new LocalDate(2012, 7, 16);
        final int billingCycleDayLocal = 17;

        final LinkedHashMap<LocalDate, LocalDate> expectedDates = new LinkedHashMap<LocalDate, LocalDate>();
        expectedDates.put(new LocalDate(2012, 7, 16), new LocalDate(2012, 7, 17));

        verifyInvoiceItems(startDate, endDate, targetDate, TIMEZONE, billingCycleDayLocal, BILLING_PERIOD, expectedDates);
    }

    @Test(groups = "fast")
    public void testCalculateSimpleInvoiceItemWithBCDBeforeStartDayWithTargetDateIn3Months() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 7, 16);
        final LocalDate endDate = null;
        final LocalDate targetDate = new LocalDate(2012, 10, 16);
        final int billingCycleDayLocal = 15;

        final LinkedHashMap<LocalDate, LocalDate> expectedDates = new LinkedHashMap<LocalDate, LocalDate>();
        expectedDates.put(new LocalDate(2012, 7, 16), new LocalDate(2012, 8, 15));
        expectedDates.put(new LocalDate(2012, 8, 15), new LocalDate(2012, 9, 15));
        expectedDates.put(new LocalDate(2012, 9, 15), new LocalDate(2012, 10, 15));
        expectedDates.put(new LocalDate(2012, 10, 15), new LocalDate(2012, 11, 15));

        verifyInvoiceItems(startDate, endDate, targetDate, TIMEZONE, billingCycleDayLocal, BILLING_PERIOD, expectedDates);
    }

    @Test(groups = "fast")
    public void testCalculateSimpleInvoiceItemWithBCDEqualsStartDayWithTargetDateIn3Months() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 7, 16);
        final LocalDate endDate = null;
        final LocalDate targetDate = new LocalDate(2012, 10, 16);
        final int billingCycleDayLocal = 16;

        final LinkedHashMap<LocalDate, LocalDate> expectedDates = new LinkedHashMap<LocalDate, LocalDate>();
        expectedDates.put(new LocalDate(2012, 7, 16), new LocalDate(2012, 8, 16));
        expectedDates.put(new LocalDate(2012, 8, 16), new LocalDate(2012, 9, 16));
        expectedDates.put(new LocalDate(2012, 9, 16), new LocalDate(2012, 10, 16));
        expectedDates.put(new LocalDate(2012, 10, 16), new LocalDate(2012, 11, 16));

        verifyInvoiceItems(startDate, endDate, targetDate, TIMEZONE, billingCycleDayLocal, BILLING_PERIOD, expectedDates);
    }

    @Test(groups = "fast")
    public void testCalculateSimpleInvoiceItemWithBCDAfterStartDayWithTargetDateIn3Months() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 7, 16);
        final LocalDate endDate = null;
        final LocalDate targetDate = new LocalDate(2012, 10, 16);
        final int billingCycleDayLocal = 17;

        final LinkedHashMap<LocalDate, LocalDate> expectedDates = new LinkedHashMap<LocalDate, LocalDate>();
        expectedDates.put(new LocalDate(2012, 7, 16), new LocalDate(2012, 7, 17));
        expectedDates.put(new LocalDate(2012, 7, 17), new LocalDate(2012, 8, 17));
        expectedDates.put(new LocalDate(2012, 8, 17), new LocalDate(2012, 9, 17));
        expectedDates.put(new LocalDate(2012, 9, 17), new LocalDate(2012, 10, 17));

        verifyInvoiceItems(startDate, endDate, targetDate, TIMEZONE, billingCycleDayLocal, BILLING_PERIOD, expectedDates);
    }

    private void verifyInvoiceItems(final LocalDate startDate, final LocalDate endDate, final LocalDate targetDate,
                                    final DateTimeZone dateTimeZone, final int billingCycleDayLocal, final BillingPeriod billingPeriod,
                                    final LinkedHashMap<LocalDate, LocalDate> expectedDates) throws InvalidDateSequenceException {
        final InAdvanceBillingMode billingMode = new InAdvanceBillingMode();

        final List<RecurringInvoiceItemData> invoiceItems = billingMode.calculateInvoiceItemData(startDate, endDate, targetDate, billingCycleDayLocal, billingPeriod);

        int i = 0;
        for (final LocalDate periodStartDate : expectedDates.keySet()) {
            Assert.assertEquals(invoiceItems.get(i).getStartDate(), periodStartDate);
            Assert.assertEquals(invoiceItems.get(i).getEndDate(), expectedDates.get(periodStartDate));
            Assert.assertTrue(invoiceItems.get(0).getNumberOfCycles().compareTo(BigDecimal.ONE) <= 0);
            i++;
        }
        Assert.assertEquals(invoiceItems.size(), i);
    }
}
