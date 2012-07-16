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

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.BillCycleDay;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.Subscription;

public class TestBillCycleDayCalculator {

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
