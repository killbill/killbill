/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.entitlement.api;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.GuicyKillbillTestSuiteNoDB;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entitlement.EntitlementTestSuiteNoDB;
import org.killbill.billing.mock.MockAccountBuilder;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

public class TestEntitlementDateHelper extends EntitlementTestSuiteNoDB {

    private EntitlementDateHelper dateHelper;

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        super.beforeMethod();

        dateHelper = new EntitlementDateHelper();
        clock.resetDeltaFromReality();
    }

    @Test(groups = "fast")
    public void testWithAccountInUtc() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate.plusDays(1));

        final DateTime referenceDateTime = new DateTime(2013, 1, 1, 15, 43, 25, 0, DateTimeZone.UTC);
        createAccount(DateTimeZone.UTC, referenceDateTime);

        final DateTime targetDate = dateHelper.fromLocalDateAndReferenceTime(initialDate, clock.getUTCNow(), internalCallContext);
        final DateTime expectedDate = new DateTime(2013, 8, 7, 15, 43, 25, 0, DateTimeZone.UTC);
        Assert.assertEquals(targetDate, expectedDate);
    }

    @Test(groups = "fast")
    public void testWithAccountInUtcMinus8() throws AccountApiException, EntitlementApiException {
        final LocalDate inputDate = new LocalDate(2013, 8, 7);
        // Current time is in the future so we don't go through logic that will default to a Clock.getUTCNow.
        clock.setDay(inputDate.plusDays(3));

        final DateTimeZone timeZoneUtcMinus8 = DateTimeZone.forOffsetHours(-8);
        // We also use a reference time of 1, 28, 10, 0 -> DateTime in accountTimeZone will be (2013, 8, 7, 1, 28, 10)
        final DateTime referenceDateTime = new DateTime(2013, 1, 1, 1, 28, 10, 0, DateTimeZone.UTC);

        createAccount(timeZoneUtcMinus8, referenceDateTime);

        final DateTime targetDate = dateHelper.fromLocalDateAndReferenceTime(inputDate, clock.getUTCNow(), internalCallContext);

        // Things to verify:
        // 1. Verify the resulting DateTime brings us back into the correct LocalDate (in the account timezone)
        Assert.assertEquals(new LocalDate(targetDate, timeZoneUtcMinus8), inputDate);
        // 2. Verify the resulting DateTime has the same reference time as we indicated (in UTC)
        Assert.assertEquals(targetDate.toLocalTime(), referenceDateTime.toLocalTime());

        //
        // To be more specific, we should find a UTC Date, with the exact specified reference time, and with a LocalDate one day
        // ahead because of the 8 hours difference.
        //
        Assert.assertEquals(targetDate, new DateTime(2013, 8, 8, 1, 28, 10, 0, DateTimeZone.UTC));
    }

    @Test(groups = "fast")
    public void testWithAccountInUtcPlus5() throws AccountApiException, EntitlementApiException {
        final LocalDate inputDate = new LocalDate(2013, 8, 7);
        clock.setDay(inputDate.plusDays(1));

        final DateTimeZone timeZoneUtcPlus5 = DateTimeZone.forOffsetHours(+5);
        // We also use a reference time of 20, 28, 10, 0 -> DateTime in accountTimeZone will be (2013, 8, 7, 20, 28, 10)
        final DateTime referenceDateTime = new DateTime(2013, 1, 1, 20, 28, 10, 0, DateTimeZone.UTC);

        createAccount(timeZoneUtcPlus5, referenceDateTime);

        final DateTime targetDate = dateHelper.fromLocalDateAndReferenceTime(inputDate, clock.getUTCNow(), internalCallContext);

        // Things to verify:
        // 1. Verify the resulting DateTime brings us back into the correct LocalDate (in the account timezone)
        Assert.assertEquals(new LocalDate(targetDate, timeZoneUtcPlus5), inputDate);
        // 2. Verify the resulting DateTime has the same reference time as we indicated (in UTC)
        Assert.assertEquals(targetDate.toLocalTime(), referenceDateTime.toLocalTime());

        //
        // To be more specific, we should find a UTC Date, with the exact specified reference time, and with a LocalDate one day
        // ahead because of the 8 hours difference.
        //
        Assert.assertEquals(targetDate, new DateTime(2013, 8, 6, 20, 28, 10, 0, DateTimeZone.UTC));
    }

    @Test(groups = "fast")
    public void testIsBeforeOrEqualsToday() throws AccountApiException {
        clock.setTime(new DateTime(2013, 8, 7, 3, 28, 10, 0, DateTimeZone.UTC));

        final DateTimeZone timeZoneUtcMinus8 = DateTimeZone.forOffsetHours(-8);

        createAccount(timeZoneUtcMinus8, clock.getUTCNow());

        final DateTime inputDateEquals = new DateTime(2013, 8, 6, 23, 28, 10, 0, timeZoneUtcMinus8);
        // Check that our input date is greater than now
        assertTrue(inputDateEquals.compareTo(clock.getUTCNow()) > 0);
        // And yet since the LocalDate match the function returns true
        assertTrue(isBeforeOrEqualsToday(inputDateEquals, timeZoneUtcMinus8, internalCallContext));
    }

    /**
     * Check if the date portion of a date/time is before or equals at now (as returned by the clock).
     *
     * @param inputDate             the fully qualified DateTime
     * @param accountTimeZone       the account timezone
     * @param internalTenantContext the context
     * @return true if the inputDate, once converted into a LocalDate using account timezone is less or equals than today
     */
    private boolean isBeforeOrEqualsToday(final DateTime inputDate, final DateTimeZone accountTimeZone, final InternalTenantContext internalTenantContext) {
        final LocalDate localDateNowInAccountTimezone = clock.getToday(accountTimeZone);
        final LocalDate targetDateInAccountTimezone = internalTenantContext.toLocalDate(inputDate);
        return targetDateInAccountTimezone.compareTo(localDateNowInAccountTimezone) <= 0;
    }

    private void createAccount(final DateTimeZone dateTimeZone, final DateTime referenceDateTime) throws AccountApiException {
        final Account accountData = new MockAccountBuilder().externalKey(UUID.randomUUID().toString())
                                                            .timeZone(dateTimeZone)
                                                            .referenceTime(referenceDateTime)
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
