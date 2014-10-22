/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.junction.plumbing.billing;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.junction.JunctionTestSuiteNoDB;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;

public class TestBillCycleDayCalculator extends JunctionTestSuiteNoDB {

    @Test(groups = "fast")
    public void testCalculateBCDForAOWithBPCancelledBundleAligned() throws Exception {
        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final DateTime bpStartDateUTC = new DateTime(2012, 7, 16, 21, 0, 0, DateTimeZone.UTC);
        final int expectedBCDUTC = 16;

        // Create a Bundle associated with a subscription
        final SubscriptionBaseBundle bundle = Mockito.mock(SubscriptionBaseBundle.class);
        final SubscriptionBase subscription = Mockito.mock(SubscriptionBase.class);
        Mockito.when(subscription.getStartDate()).thenReturn(bpStartDateUTC);

        // subscription.getCurrentPlan() will return null as expected (cancelled BP)
        Mockito.when(subscriptionInternalApi.getBaseSubscription(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(subscription);

        // Create a the base plan associated with that subscription
        final Plan plan = Mockito.mock(Plan.class);
        Mockito.when(plan.dateOfFirstRecurringNonZeroCharge(bpStartDateUTC, null)).thenReturn(bpStartDateUTC);
        final Catalog catalog = Mockito.mock(Catalog.class);
        Mockito.when(catalog.findPlan(Mockito.anyString(), Mockito.<DateTime>any(), Mockito.<DateTime>any())).thenReturn(plan);
        Mockito.when(subscription.getLastActivePlan()).thenReturn(plan);

        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getTimeZone()).thenReturn(accountTimeZone);
        final Integer billCycleDayLocal = billCycleDayCalculator.calculateBcdForAlignment(BillingAlignment.BUNDLE, bundle.getId(), subscription,
                                                                                          account, catalog, null, internalCallContext);

        Assert.assertEquals(billCycleDayLocal, (Integer) expectedBCDUTC);
    }

    @Test(groups = "fast")
    public void testCalculateBCDWithTimeZoneHST() throws Exception {
        final DateTimeZone accountTimeZone = DateTimeZone.forID("HST");
        final DateTime startDateUTC = new DateTime("2012-07-16T21:17:03.000Z", DateTimeZone.UTC);
        final int bcdLocal = 16;

        verifyBCDCalculation(accountTimeZone, startDateUTC, bcdLocal);
    }

    @Test(groups = "fast")
    public void testCalculateBCDWithTimeZoneCEST() throws Exception {
        final DateTimeZone accountTimeZone = DateTimeZone.forID("Europe/Paris");
        final DateTime startDateUTC = new DateTime("2012-07-16T21:17:03.000Z", DateTimeZone.UTC);
        final int bcdLocal = 16;

        verifyBCDCalculation(accountTimeZone, startDateUTC, bcdLocal);
    }

    @Test(groups = "fast")
    public void testCalculateBCDWithTimeZoneUTC() throws Exception {
        final DateTimeZone accountTimeZone = DateTimeZone.UTC;
        final DateTime startDateUTC = new DateTime("2012-07-16T21:17:03.000Z", DateTimeZone.UTC);
        final int bcdLocal = 16;

        verifyBCDCalculation(accountTimeZone, startDateUTC, bcdLocal);
    }

    @Test(groups = "fast")
    public void testCalculateBCDWithTimeZoneEEST() throws Exception {
        final DateTimeZone accountTimeZone = DateTimeZone.forID("+0300");
        final DateTime startDateUTC = new DateTime("2012-07-16T21:17:03.000Z", DateTimeZone.UTC);
        final int bcdLocal = 17;

        verifyBCDCalculation(accountTimeZone, startDateUTC, bcdLocal);
    }

    @Test(groups = "fast")
    public void testCalculateBCDWithTimeZoneJST() throws Exception {
        final DateTimeZone accountTimeZone = DateTimeZone.forID("Asia/Tokyo");
        final DateTime startDateUTC = new DateTime("2012-07-16T21:17:03.000Z", DateTimeZone.UTC);
        final int bcdLocal = 17;

        verifyBCDCalculation(accountTimeZone, startDateUTC, bcdLocal);
    }

    @Test(groups = "fast")
    public void testCalculateBCDWithSubscriptionDateNotInUTC() throws Exception {
        // Test to verify the computations don't rely implicitly on UTC
        final DateTimeZone accountTimeZone = DateTimeZone.forID("Asia/Tokyo");
        final DateTime startDate = new DateTime("2012-07-16T21:17:03.000Z", DateTimeZone.forID("HST"));
        final int bcdLocal = 17;

        verifyBCDCalculation(accountTimeZone, startDate, bcdLocal);
    }

    private void verifyBCDCalculation(final DateTimeZone accountTimeZone, final DateTime startDateUTC, final int bcdLocal) throws AccountApiException, CatalogApiException {
        final BillCycleDayCalculator billCycleDayCalculator = new BillCycleDayCalculator(Mockito.mock(CatalogService.class), Mockito.mock(SubscriptionBaseInternalApi.class));

        final SubscriptionBase subscription = Mockito.mock(SubscriptionBase.class);
        Mockito.when(subscription.getStartDate()).thenReturn(startDateUTC);

        final Plan plan = Mockito.mock(Plan.class);
        Mockito.when(plan.dateOfFirstRecurringNonZeroCharge(startDateUTC, null)).thenReturn(startDateUTC);

        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getTimeZone()).thenReturn(accountTimeZone);

        final Integer bcd = billCycleDayCalculator.calculateBcdFromSubscription(subscription, plan, account, Mockito.mock(Catalog.class), internalCallContext);
        Assert.assertEquals(bcd, (Integer) bcdLocal);
    }
}
