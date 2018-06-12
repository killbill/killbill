/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.jaxrs;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.clock.ClockMock;
import org.killbill.billing.jaxrs.resources.JaxRsResourceBase;
import org.killbill.billing.util.UUIDs;

public class TestDateConversion extends JaxRsResourceBase {

    public TestDateConversion() throws AccountApiException {
        super(null, null, null, null, Mockito.mock(AccountUserApi.class), null, null, null, new ClockMock(), null);
    }

    public UUID setupAccount(DateTimeZone accountTimeZone) throws AccountApiException {
        final UUID accountId = UUIDs.randomUUID();
        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getTimeZone()).thenReturn(accountTimeZone);
        Mockito.when(accountUserApi.getAccountById(accountId, null)).thenReturn(account);
        return accountId;
    }

    @BeforeTest(groups = "fast")
    public void beforeTest() {
        ((ClockMock) clock).resetDeltaFromReality();
    }

    //
    // BASIC Tests to understand how toLocalDate converts different inputs (null, LocalDate, DateTime)
    //

    @Test(groups = "fast")
    public void testNullConversion() throws AccountApiException {
        final String input = null;

        final LocalDate result = toLocalDate(input);
        Assert.assertNull(result);

        final UUID accountId = setupAccount(DateTimeZone.forOffsetHours(-8));
        ((ClockMock) clock).setTime(new DateTime("2013-08-26T06:50:20Z"));
        final LocalDate result2 = toLocalDateDefaultToday(accountId, input, null);
        Assert.assertTrue(result2.compareTo(new LocalDate(2013, 8, 25)) == 0);
        ((ClockMock) clock).resetDeltaFromReality();
    }

    @Test(groups = "fast")
    public void testLocalDateConversion() throws AccountApiException {
        final UUID accountId = setupAccount(DateTimeZone.forOffsetHours(-8));
        final String input = "2013-08-25";
        final LocalDate result = toLocalDate(input);
        Assert.assertTrue(result.compareTo(new LocalDate(2013, 8, 25)) == 0);
    }

    //
    // MOSTLY FOR OUR UNDERSTANDING ON HOW LocalDate and DateTime behave with respect to DateTimeZone
    //

    // Illustrate that specifying a DateTimeZone in a LocalDate when the input 'instant' is a LocalDate has NO effect.
    @Test(groups = "fast")
    public void testTwoLocalDatesWithDifferentTimeZonesAreEquals() throws AccountApiException {

        final String inputDate = "2013-08-25";
        final LocalDate localDate = LocalDate.parse(inputDate, LOCAL_DATE_FORMATTER);
        final LocalDate localDateInUTC = new LocalDate(localDate, DateTimeZone.UTC);
        final LocalDate localDateInAccountTimeZone = new LocalDate(localDate, DateTimeZone.forOffsetHours(-8));
        Assert.assertEquals(localDateInUTC, localDateInAccountTimeZone);
    }

    // Illustrate that specifying a DateTimeZone in a LocalDate when the input 'instant' is a DateTime DOES HAVE an effect.
    @Test(groups = "fast")
    public void testTwoDateTimesConstructedFromDateTimesWithDifferentTimeZonesAreDifferent() throws AccountApiException {

        final DateTime nowUTC = DATE_TIME_FORMATTER.parseDateTime("2015-04-05T05:27:17.547Z");
        final LocalDate localDateInUTC = new LocalDate(nowUTC, DateTimeZone.UTC);
        final LocalDate localDateInAccountTimeZone = new LocalDate(nowUTC, DateTimeZone.forOffsetHours(-8));
        Assert.assertNotEquals(localDateInUTC, localDateInAccountTimeZone);
    }
}
