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

package org.killbill.billing.entitlement.api;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entitlement.EntitlementTestSuiteNoDB;

import static org.testng.Assert.assertTrue;

public class TestEntitlementDateHelper extends EntitlementTestSuiteNoDB {

    private Account account;
    private EntitlementDateHelper dateHelper;

    @BeforeClass(groups = "fast")
    public void beforeMethod() throws Exception {
        super.beforeClass();


        account = Mockito.mock(Account.class);
        Mockito.when(accountInternalApi.getAccountByRecordId(Mockito.anyLong(), Mockito.<InternalTenantContext>any())).thenReturn(account);
        dateHelper = new EntitlementDateHelper(accountInternalApi, clock);
        clock.resetDeltaFromReality();;
    }

    @Test(groups = "fast")
    public void testWithAccountInUtc() throws EntitlementApiException {

        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate.plusDays(1));

        Mockito.when(account.getTimeZone()).thenReturn(DateTimeZone.UTC);

        final DateTime refererenceDateTime = new DateTime(2013, 1, 1, 15, 43, 25, 0, DateTimeZone.UTC);
        final DateTime targetDate = dateHelper.fromLocalDateAndReferenceTime(initialDate, refererenceDateTime, internalCallContext);
        final DateTime expectedDate = new DateTime(2013, 8, 7, 15, 43, 25, 0, DateTimeZone.UTC);
        Assert.assertEquals(targetDate, expectedDate);
    }


    @Test(groups = "fast")
    public void testWithAccountInUtcMinus8() throws EntitlementApiException {

        final LocalDate inputDate = new LocalDate(2013, 8, 7);
        clock.setDay(inputDate.plusDays(3));

        final DateTimeZone timeZoneUtcMinus8 = DateTimeZone.forOffsetHours(-8);
        Mockito.when(account.getTimeZone()).thenReturn(timeZoneUtcMinus8);

        // We also use a reference time of 1, 28, 10, 0 -> DateTime in accountTimeZone will be (2013, 8, 7, 1, 28, 10)
        final DateTime refererenceDateTime = new DateTime(2013, 1, 1, 1, 28, 10, 0, DateTimeZone.UTC);
        final DateTime targetDate = dateHelper.fromLocalDateAndReferenceTime(inputDate, refererenceDateTime, internalCallContext);

        // And so that datetime in UTC becomes expectedDate below
        final DateTime expectedDate = new DateTime(2013, 8, 8, 1, 28, 10, 0, DateTimeZone.UTC);
        Assert.assertEquals(targetDate, expectedDate);

        Assert.assertEquals(targetDate.toDateTime(timeZoneUtcMinus8).toLocalDate(), inputDate);
    }


    @Test(groups = "fast")
    public void test2WithAccountInUtcMinus8() throws EntitlementApiException {

        final DateTime initialNow = new DateTime(2013, 8, 22,22, 07, 01, 0, DateTimeZone.UTC);
        clock.setTime(initialNow);

        final LocalDate inputDate = new LocalDate(2013, 8, 22);

        final DateTimeZone timeZoneUtcMinus8 = DateTimeZone.forOffsetHours(-8);
        Mockito.when(account.getTimeZone()).thenReturn(timeZoneUtcMinus8);

        // We also use a reference time of 16, 48, 0 -> DateTime in UTC will be (2013, 8, 23, 00, 48, 0) which:
        // * is greater than now
        // * with a inputLocalDate in the account timezone which is today
        //
        // => Code will round to now to not end up in the future
        //
        final DateTime refererenceDateTime = new DateTime(2013, 8, 22, 16, 48, 0, DateTimeZone.UTC);
        final DateTime targetDate = dateHelper.fromLocalDateAndReferenceTime(inputDate, refererenceDateTime, internalCallContext);

        final DateTime now = clock.getUTCNow();
        Assert.assertTrue(initialNow.compareTo(targetDate) <= 0);
        Assert.assertTrue(targetDate.compareTo(now) <= 0);
    }



    @Test(groups = "fast")
    public void testWithAccountInUtcPlus5() throws EntitlementApiException {

        final LocalDate inputDate = new LocalDate(2013, 8, 7);
        clock.setDay(inputDate.plusDays(1));

        final DateTimeZone timeZoneUtcPlus5 = DateTimeZone.forOffsetHours(+5);
        Mockito.when(account.getTimeZone()).thenReturn(timeZoneUtcPlus5);

        // We also use a reference time of 20, 28, 10, 0 -> DateTime in accountTimeZone will be (2013, 8, 7, 20, 28, 10)
        final DateTime refererenceDateTime = new DateTime(2013, 1, 1, 20, 28, 10, 0, DateTimeZone.UTC);
        final DateTime targetDate = dateHelper.fromLocalDateAndReferenceTime(inputDate, refererenceDateTime, internalCallContext);

        // And so that datetime in UTC becomes expectedDate below
        final DateTime expectedDate = new DateTime(2013, 8, 6, 20, 28, 10, 0, DateTimeZone.UTC);
        Assert.assertEquals(targetDate, expectedDate);

        Assert.assertEquals(targetDate.toDateTime(timeZoneUtcPlus5).toLocalDate(), inputDate);

    }

    @Test(groups = "fast")
    public void testIsBeforeOrEqualsToday() {

        clock.setTime(new DateTime(2013, 8, 7, 3, 28, 10, 0, DateTimeZone.UTC));
        final DateTimeZone timeZoneUtcMinus8 = DateTimeZone.forOffsetHours(-8);


        final DateTime inputDateEquals = new DateTime(2013, 8, 6, 23, 28, 10, 0, timeZoneUtcMinus8);
        // Check that our input date is greater than now
        assertTrue(inputDateEquals.compareTo(clock.getUTCNow()) > 0);
        // And yet since the LocalDate match the function returns true
        assertTrue(dateHelper.isBeforeOrEqualsToday(inputDateEquals, timeZoneUtcMinus8));
    }
}
