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

package org.killbill.billing.invoice.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestRecurringInArrear extends InvoiceTestSuiteNoDB {

    private static final DateTimeZone TIMEZONE = DateTimeZone.forID("Pacific/Pitcairn");
    private static final BillingPeriod BILLING_PERIOD = BillingPeriod.MONTHLY;

    @Test(groups = "fast")
    public void testItemShouldNotStartInThePast() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 7, 16);
        final LocalDate endDate = new LocalDate(2012, 7, 16);
        final LocalDate targetDate = new LocalDate(2012, 7, 16);
        final int billingCycleDayLocal = 15;

        final LinkedHashMap<LocalDate, LocalDate> expectedDates = new LinkedHashMap<LocalDate, LocalDate>();
        verifyInvoiceItems(startDate, endDate, targetDate, billingCycleDayLocal, BILLING_PERIOD, expectedDates);
    }

    @Test(groups = "fast")
    public void testCalculateSimpleInvoiceItemWithNoEndDate() throws Exception {
        final LocalDate startDate = new LocalDate(new DateTime("2012-07-17T02:25:33.000Z", DateTimeZone.UTC), TIMEZONE);
        final LocalDate endDate = null;
        final LocalDate targetDate = new LocalDate(2012, 7, 16);
        final int billingCycleDayLocal = 15;

        final LinkedHashMap<LocalDate, LocalDate> expectedDates = new LinkedHashMap<LocalDate, LocalDate>();
        verifyInvoiceItems(startDate, endDate, targetDate, billingCycleDayLocal, BILLING_PERIOD, expectedDates);
    }

    @Test(groups = "fast")
    public void testCalculateSimpleInvoiceItemWithBCDBeforeStartDay() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 7, 16);
        final LocalDate endDate = new LocalDate(2012, 8, 16);
        final LocalDate targetDate = new LocalDate(2012, 7, 16);
        final int billingCycleDayLocal = 15;

        final LinkedHashMap<LocalDate, LocalDate> expectedDates = new LinkedHashMap<LocalDate, LocalDate>();
        verifyInvoiceItems(startDate, endDate, targetDate, billingCycleDayLocal, BILLING_PERIOD, expectedDates);

        final LocalDate targetDate2 = new LocalDate(2012, 8, 15);
        expectedDates.put(new LocalDate(2012, 7, 16), new LocalDate(2012, 8, 15));
        verifyInvoiceItems(startDate, endDate, targetDate2, billingCycleDayLocal, BILLING_PERIOD, expectedDates);
    }

    @Test(groups = "fast")
    public void testCalculateSimpleInvoiceItemWithBCDEqualsStartDay() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 7, 16);
        final LocalDate endDate = new LocalDate(2012, 8, 16);
        final LocalDate targetDate = new LocalDate(2012, 7, 16);
        final int billingCycleDayLocal = 16;

        final LinkedHashMap<LocalDate, LocalDate> expectedDates = new LinkedHashMap<LocalDate, LocalDate>();
        verifyInvoiceItems(startDate, endDate, targetDate, billingCycleDayLocal, BILLING_PERIOD, expectedDates);

        expectedDates.put(new LocalDate(2012, 7, 16), new LocalDate(2012, 8, 16));
        final LocalDate targetDate2 = new LocalDate(2012, 8, 16);
        verifyInvoiceItems(startDate, endDate, targetDate2, billingCycleDayLocal, BILLING_PERIOD, expectedDates);

    }

    @Test(groups = "fast")
    public void testCalculateSimpleInvoiceItemWithBCDAfterStartDay() throws Exception {
        final LocalDate startDate = new LocalDate(2012, 7, 16);
        final LocalDate endDate = new LocalDate(2012, 8, 16);
        final LocalDate targetDate = new LocalDate(2012, 7, 16);
        final int billingCycleDayLocal = 17;

        final LinkedHashMap<LocalDate, LocalDate> expectedDates = new LinkedHashMap<LocalDate, LocalDate>();
        verifyInvoiceItems(startDate, endDate, targetDate, billingCycleDayLocal, BILLING_PERIOD, expectedDates);

        final LocalDate targetDate2 = new LocalDate(2012, 7, 17);
        expectedDates.put(new LocalDate(2012, 7, 16), new LocalDate(2012, 7, 17));
        verifyInvoiceItems(startDate, endDate, targetDate2, billingCycleDayLocal, BILLING_PERIOD, expectedDates);
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

        verifyInvoiceItems(startDate, endDate, targetDate, billingCycleDayLocal, BILLING_PERIOD, expectedDates);
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

        verifyInvoiceItems(startDate, endDate, targetDate, billingCycleDayLocal, BILLING_PERIOD, expectedDates);
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

        verifyInvoiceItems(startDate, endDate, targetDate, billingCycleDayLocal, BILLING_PERIOD, expectedDates);
    }

    @Test(groups = "fast", description = "See https://github.com/killbill/killbill/issues/1907")
    public void testWithUpcomingChangeFromBeginingOfPeriod() throws Exception {
        final LocalDate startDate = new LocalDate(2023, 8, 11);
        final LocalDate endDate = new LocalDate(2023, 8, 20);
        final LocalDate targetDate = new LocalDate(2023, 8, 20);
        final int billingCycleDayLocal = 11;

        final LinkedHashMap<LocalDate, LocalDate> expectedDates = new LinkedHashMap<LocalDate, LocalDate>();
        expectedDates.put(new LocalDate(2023, 8, 11), new LocalDate(2023, 8, 20));
        verifyInvoiceItems(startDate, endDate, targetDate, billingCycleDayLocal, BILLING_PERIOD, expectedDates);
    }


    @Test(groups = "fast", description = "See https://github.com/killbill/killbill/issues/1907")
    public void testWithUpcomingChangeAfterBeginingOfPeriod() throws Exception {
        final LocalDate startDate = new LocalDate(2023, 8, 15);
        final LocalDate endDate = new LocalDate(2023, 8, 20);
        final LocalDate targetDate = new LocalDate(2023, 8, 20);
        final int billingCycleDayLocal = 11;

        final LinkedHashMap<LocalDate, LocalDate> expectedDates = new LinkedHashMap<LocalDate, LocalDate>();
        expectedDates.put(new LocalDate(2023, 8, 15), new LocalDate(2023, 8, 20));
        verifyInvoiceItems(startDate, endDate, targetDate, billingCycleDayLocal, BILLING_PERIOD, expectedDates);
    }

    @Test(groups = "fast", description="https://github.com/killbill/killbill/issues/2133")
    public void testWithBCDEndOfMonthPriorTargetDate() throws Exception {
        final LocalDate startDate = new LocalDate(2025, 3, 31);
        final LocalDate endDate = null;
        final LocalDate targetDate = new LocalDate(2025, 5, 30);
        final int billingCycleDayLocal = 31;

        final LinkedHashMap<LocalDate, LocalDate> expectedDates = new LinkedHashMap<LocalDate, LocalDate>();
        expectedDates.put(new LocalDate(2025, 3, 31), new LocalDate(2025, 4, 30));
        verifyInvoiceItems(startDate, endDate, targetDate, billingCycleDayLocal, BILLING_PERIOD, expectedDates);
    }


    private void verifyInvoiceItems(final LocalDate startDate, final LocalDate endDate, final LocalDate targetDate,
                                    final int billingCycleDayLocal, final BillingPeriod billingPeriod,
                                    final LinkedHashMap<LocalDate, LocalDate> expectedDates) throws InvalidDateSequenceException {

        final RecurringInvoiceItemDataWithNextBillingCycleDate invoiceItemsWithDates = fixedAndRecurringInvoiceItemGenerator.generateInvoiceItemData(startDate, endDate, targetDate, billingCycleDayLocal, billingPeriod, BillingMode.IN_ARREAR, null);
        final List<RecurringInvoiceItemData> invoiceItems = invoiceItemsWithDates.getItemData();

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
