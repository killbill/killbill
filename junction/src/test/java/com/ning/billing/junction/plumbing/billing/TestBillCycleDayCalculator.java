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

package com.ning.billing.junction.plumbing.billing;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.BillCycleDay;
import com.ning.billing.catalog.api.BillingAlignment;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.entitlement.api.user.EffectiveSubscriptionEvent;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;

public class TestBillCycleDayCalculator {

    @Test(groups = "fast")
    public void testCalculateBCDForAOWithBPCancelledBundleAligned() throws Exception {
        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final DateTime bpStartDateUTC = new DateTime(2012, 7, 16, 21, 0, 0, DateTimeZone.UTC);
        final int expectedBCDUTC = 16;

        // Create the calculator
        final CatalogService catalogService = Mockito.mock(CatalogService.class);
        final EntitlementUserApi entitlementUserApi = Mockito.mock(EntitlementUserApi.class);
        final BillCycleDayCalculator billCycleDayCalculator = new BillCycleDayCalculator(catalogService, entitlementUserApi);

        // Create a Bundle associated with a subscription
        final SubscriptionBundle bundle = Mockito.mock(SubscriptionBundle.class);
        final EffectiveSubscriptionEvent previousTransition = Mockito.mock(EffectiveSubscriptionEvent.class);
        final Subscription subscription = Mockito.mock(Subscription.class);
        Mockito.when(subscription.getStartDate()).thenReturn(bpStartDateUTC);
        Mockito.when(subscription.getPreviousTransition()).thenReturn(previousTransition);
        // subscription.getCurrentPlan() will return null as expected (cancelled BP)
        Mockito.when(entitlementUserApi.getBaseSubscription(Mockito.<UUID>any())).thenReturn(subscription);

        // Create a the base plan associated with that subscription
        final Plan plan = Mockito.mock(Plan.class);
        Mockito.when(plan.dateOfFirstRecurringNonZeroCharge(bpStartDateUTC)).thenReturn(bpStartDateUTC);
        final Catalog catalog = Mockito.mock(Catalog.class);
        Mockito.when(catalog.findPlan(Mockito.anyString(), Mockito.<DateTime>any(), Mockito.<DateTime>any())).thenReturn(plan);

        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getTimeZone()).thenReturn(accountTimeZone);
        final BillCycleDay billCycleDay = billCycleDayCalculator.calculateBcdForAlignment(BillingAlignment.BUNDLE, bundle, subscription,
                                                                                          account, catalog, null);

        Assert.assertEquals(billCycleDay.getDayOfMonthUTC(), expectedBCDUTC);
    }

    @Test(groups = "fast")
    public void testCalculateBCDWithTimeZoneHST() throws Exception {
        final DateTimeZone accountTimeZone = DateTimeZone.forID("HST");
        final DateTime startDateUTC = new DateTime("2012-07-16T21:17:03.000Z", DateTimeZone.UTC);
        final int bcdUTC = 16;
        final int bcdLocal = 16;

        verifyBCDCalculation(accountTimeZone, startDateUTC, bcdUTC, bcdLocal);
    }

    @Test(groups = "fast")
    public void testCalculateBCDWithTimeZoneCEST() throws Exception {
        final DateTimeZone accountTimeZone = DateTimeZone.forID("Europe/Paris");
        final DateTime startDateUTC = new DateTime("2012-07-16T21:17:03.000Z", DateTimeZone.UTC);
        final int bcdUTC = 16;
        final int bcdLocal = 16;

        verifyBCDCalculation(accountTimeZone, startDateUTC, bcdUTC, bcdLocal);
    }

    @Test(groups = "fast")
    public void testCalculateBCDWithTimeZoneUTC() throws Exception {
        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final DateTime startDateUTC = new DateTime("2012-07-16T21:17:03.000Z", DateTimeZone.UTC);
        final int bcdUTC = 16;
        final int bcdLocal = 16;

        verifyBCDCalculation(accountTimeZone, startDateUTC, bcdUTC, bcdLocal);
    }

    @Test(groups = "fast")
    public void testCalculateBCDWithTimeZoneEEST() throws Exception {
        final DateTimeZone accountTimeZone = DateTimeZone.forID("+0300");
        final DateTime startDateUTC = new DateTime("2012-07-16T21:17:03.000Z", DateTimeZone.UTC);
        final int bcdUTC = 16;
        final int bcdLocal = 17;

        verifyBCDCalculation(accountTimeZone, startDateUTC, bcdUTC, bcdLocal);
    }

    @Test(groups = "fast")
    public void testCalculateBCDWithTimeZoneJST() throws Exception {
        final DateTimeZone accountTimeZone = DateTimeZone.forID("Asia/Tokyo");
        final DateTime startDateUTC = new DateTime("2012-07-16T21:17:03.000Z", DateTimeZone.UTC);
        final int bcdUTC = 16;
        final int bcdLocal = 17;

        verifyBCDCalculation(accountTimeZone, startDateUTC, bcdUTC, bcdLocal);
    }

    @Test(groups = "fast")
    public void testCalculateBCDWithSubscriptionDateNotInUTC() throws Exception {
        // Test to verify the computations don't rely implicitly on UTC
        final DateTimeZone accountTimeZone = DateTimeZone.forID("Asia/Tokyo");
        final DateTime startDate = new DateTime("2012-07-16T21:17:03.000Z", DateTimeZone.forID("HST"));
        final int bcdUTC = 16;
        final int bcdLocal = 17;

        verifyBCDCalculation(accountTimeZone, startDate, bcdUTC, bcdLocal);
    }

    private void verifyBCDCalculation(final DateTimeZone accountTimeZone, final DateTime startDateUTC, final int bcdUTC, final int bcdLocal) throws AccountApiException {
        final BillCycleDayCalculator billCycleDayCalculator = new BillCycleDayCalculator(Mockito.mock(CatalogService.class), Mockito.mock(EntitlementUserApi.class));

        final Subscription subscription = Mockito.mock(Subscription.class);
        Mockito.when(subscription.getStartDate()).thenReturn(startDateUTC);

        final Plan plan = Mockito.mock(Plan.class);
        Mockito.when(plan.dateOfFirstRecurringNonZeroCharge(startDateUTC)).thenReturn(startDateUTC);

        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getTimeZone()).thenReturn(accountTimeZone);

        final BillCycleDay bcd = billCycleDayCalculator.calculateBcdFromSubscription(subscription, plan, account);
        Assert.assertEquals(bcd.getDayOfMonthUTC(), bcdUTC);
        Assert.assertEquals(bcd.getDayOfMonthLocal(), bcdLocal);
    }
}
