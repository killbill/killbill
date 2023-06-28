/*
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

package org.killbill.billing.util.bcd;

import java.util.HashMap;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.GuicyKillbillTestSuiteNoDB;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.mock.MockAccountBuilder;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestBillCycleDayCalculator extends UtilTestSuiteNoDB {


    @Test(groups = "fast")
    public void testCalculateBCDForAOWithBPCancelledBundleAligned() throws Exception {
        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final DateTime bpStartDateUTC = new DateTime(2012, 7, 16, 21, 0, 0, DateTimeZone.UTC);
        final int expectedBCDUTC = 16;

        // Create a Bundle associated with a subscription
        final SubscriptionBaseBundle bundle = Mockito.mock(SubscriptionBaseBundle.class);
        final SubscriptionBase subscription = Mockito.mock(SubscriptionBase.class);
        Mockito.when(subscription.getStartDate()).thenReturn(bpStartDateUTC);

        // Create a the base plan associated with that subscription
        final Plan plan = Mockito.mock(Plan.class);
        Mockito.when(subscription.getLastActivePlan()).thenReturn(plan);
        Mockito.when(subscription.getDateOfFirstRecurringNonZeroCharge()).thenReturn(bpStartDateUTC);

        final ImmutableAccountData account = Mockito.mock(ImmutableAccountData.class);
        Mockito.when(account.getTimeZone()).thenReturn(accountTimeZone);
        final Integer billCycleDayLocal = BillCycleDayCalculator.calculateBcdForAlignment(new HashMap<UUID, Integer>(), subscription, subscription, BillingAlignment.BUNDLE, internalCallContext, 0);

        Assert.assertEquals(billCycleDayLocal, (Integer) expectedBCDUTC);
    }

    @Test(groups = "fast")
    public void testCalculateBCDWithTimeZoneHST() throws Exception {
        final DateTimeZone accountTimeZone = DateTimeZone.forID("HST");
        final DateTime startDateUTC = new DateTime("2012-07-16T21:17:03.000Z", DateTimeZone.UTC);
        final int bcdLocal = 16;

        createAccountAndRefreshTimeAwareContext(accountTimeZone, startDateUTC);

        verifyBCDCalculation(accountTimeZone, startDateUTC, bcdLocal);
    }

    @Test(groups = "fast")
    public void testCalculateBCDWithTimeZoneCEST() throws Exception {
        final DateTimeZone accountTimeZone = DateTimeZone.forID("Europe/Paris");
        final DateTime startDateUTC = new DateTime("2012-07-16T21:17:03.000Z", DateTimeZone.UTC);
        final int bcdLocal = 16;

        createAccountAndRefreshTimeAwareContext(accountTimeZone, startDateUTC);

        verifyBCDCalculation(accountTimeZone, startDateUTC, bcdLocal);
    }

    @Test(groups = "fast")
    public void testCalculateBCDWithTimeZoneUTC() throws Exception {
        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final DateTime startDateUTC = new DateTime("2012-07-16T21:17:03.000Z", DateTimeZone.UTC);
        final int bcdLocal = 16;

        createAccountAndRefreshTimeAwareContext(accountTimeZone, startDateUTC);

        verifyBCDCalculation(accountTimeZone, startDateUTC, bcdLocal);
    }

    @Test(groups = "fast")
    public void testCalculateBCDWithTimeZoneEEST() throws Exception {
        final DateTimeZone accountTimeZone = DateTimeZone.forID("+0300");
        final DateTime startDateUTC = new DateTime("2012-07-16T21:17:03.000Z", DateTimeZone.UTC);
        final int bcdLocal = 17;

        createAccountAndRefreshTimeAwareContext(accountTimeZone, startDateUTC);

        verifyBCDCalculation(accountTimeZone, startDateUTC, bcdLocal);
    }

    @Test(groups = "fast")
    public void testCalculateBCDWithTimeZoneJST() throws Exception {
        final DateTimeZone accountTimeZone = DateTimeZone.forID("Asia/Tokyo");
        final DateTime startDateUTC = new DateTime("2012-07-16T21:17:03.000Z", DateTimeZone.UTC);
        final int bcdLocal = 17;

        createAccountAndRefreshTimeAwareContext(accountTimeZone, startDateUTC);

        verifyBCDCalculation(accountTimeZone, startDateUTC, bcdLocal);
    }

    @Test(groups = "fast")
    public void testCalculateBCDWithSubscriptionDateNotInUTC() throws Exception {
        // Test to verify the computations don't rely implicitly on UTC
        final DateTimeZone accountTimeZone = DateTimeZone.forID("Asia/Tokyo");
        final DateTime startDate = new DateTime("2012-07-16T21:17:03.000Z", DateTimeZone.forID("HST"));
        final int bcdLocal = 17;

        createAccountAndRefreshTimeAwareContext(accountTimeZone, startDate);

        verifyBCDCalculation(accountTimeZone, startDate, bcdLocal);
    }

    @Test(groups = "fast")
    public void testAlignProposedBillCycleDate1() {
        final DateTime proposedDate = new DateTime(2022, 7, 19, 17, 28, 0, DateTimeZone.UTC);
        final BillingPeriod billingPeriod = BillingPeriod.MONTHLY;
        // BCD > proposed day of the month
        final int bcd = 23;

        final LocalDate result = BillCycleDayCalculator.alignProposedBillCycleDate(proposedDate, bcd, billingPeriod, internalCallContext);
        Assert.assertEquals(result, new LocalDate(2022, 7, 23));
    }

    @Test(groups = "fast")
    public void testAlignProposedBillCycleDate2() {
        final DateTime proposedDate = new DateTime(2022, 7, 19, 17, 28, 0, DateTimeZone.UTC);
        final BillingPeriod billingPeriod = BillingPeriod.MONTHLY;
        // BCD < proposed day of the month
        final int bcd = 17;

        final LocalDate result = BillCycleDayCalculator.alignProposedBillCycleDate(proposedDate, bcd, billingPeriod, internalCallContext);
        Assert.assertEquals(result, new LocalDate(2022, 7, 17));
    }

    @Test(groups = "fast")
    public void testAlignProposedBillCycleDate3() {
        final DateTime proposedDate = new DateTime(2022, 2, 19, 17, 28, 0, DateTimeZone.UTC);
        final BillingPeriod billingPeriod = BillingPeriod.MONTHLY;
        // BCD > last day of the month
        final int bcd = 31;

        final LocalDate result = BillCycleDayCalculator.alignProposedBillCycleDate(proposedDate, bcd, billingPeriod, internalCallContext);
        Assert.assertEquals(result, new LocalDate(2022, 2, 28));
    }

    @Test(groups = "fast")
    public void testAlignProposedNextBillCycleDate1() {
        final DateTime proposedDate = new DateTime(2022, 7, 19, 17, 28, 0, DateTimeZone.UTC);
        final BillingPeriod billingPeriod = BillingPeriod.MONTHLY;
        // BCD > proposed day of the month
        final int bcd = 23;

        final LocalDate result = BillCycleDayCalculator.alignProposedNextBillCycleDate(null, proposedDate, bcd, billingPeriod, internalCallContext);
        Assert.assertEquals(result, new LocalDate(2022, 7, 23));
    }

    @Test(groups = "fast")
    public void testAlignProposedNextBillCycleDate2() {
        final DateTime proposedDate = new DateTime(2022, 7, 19, 17, 28, 0, DateTimeZone.UTC);
        final BillingPeriod billingPeriod = BillingPeriod.MONTHLY;
        // BCD < proposed day of the month
        final int bcd = 17;

        final LocalDate result = BillCycleDayCalculator.alignProposedNextBillCycleDate(null, proposedDate, bcd, billingPeriod, internalCallContext);
        Assert.assertEquals(result, new LocalDate(2022, 8, 17));
    }

    @Test(groups = "fast")
    public void testAlignProposedNextBillCycleDate3() {
        final DateTime proposedDate = new DateTime(2022, 2, 19, 17, 28, 0, DateTimeZone.UTC);
        final BillingPeriod billingPeriod = BillingPeriod.MONTHLY;
        // BCD > last day of the month
        final int bcd = 31;

        final LocalDate result = BillCycleDayCalculator.alignProposedNextBillCycleDate(null, proposedDate, bcd, billingPeriod, internalCallContext);
        Assert.assertEquals(result, new LocalDate(2022, 2, 28));
    }





    private void verifyBCDCalculation(final DateTimeZone accountTimeZone, final DateTime startDateUTC, final int bcdLocal) throws AccountApiException, CatalogApiException {
        final SubscriptionBase subscription = Mockito.mock(SubscriptionBase.class);
        Mockito.when(subscription.getStartDate()).thenReturn(startDateUTC);
        Mockito.when(subscription.getDateOfFirstRecurringNonZeroCharge()).thenReturn(startDateUTC);

        final ImmutableAccountData account = Mockito.mock(ImmutableAccountData.class);
        Mockito.when(account.getTimeZone()).thenReturn(accountTimeZone);

        final Integer bcd = BillCycleDayCalculator.calculateBcdForAlignment(new HashMap<UUID, Integer>(), subscription, subscription, BillingAlignment.SUBSCRIPTION, internalCallContext, 0);
        Assert.assertEquals(bcd, (Integer) bcdLocal);
    }

    private void createAccountAndRefreshTimeAwareContext(final DateTimeZone dateTimeZone, final DateTime referenceDateTime) throws AccountApiException {
        final Account accountData = new MockAccountBuilder().externalKey(UUID.randomUUID().toString())
                                                            .timeZone(dateTimeZone)
                                                            .createdDate(referenceDateTime)
                                                            .build();

        GuicyKillbillTestSuiteNoDB.createMockAccount(accountData,
                                                     accountUserApi,
                                                     accountInternalApi,
                                                     immutableAccountInternalApi,
                                                     nonEntityDao,
                                                     clock,
                                                     internalCallContextFactory,
                                                     callContext,
                                                     internalCallContext);
    }
}
