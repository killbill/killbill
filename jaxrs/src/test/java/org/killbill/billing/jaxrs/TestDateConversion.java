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

package org.killbill.billing.jaxrs;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.clock.ClockMock;
import org.killbill.billing.jaxrs.resources.JaxRsResourceBase;

public class TestDateConversion extends JaxRsResourceBase {

    final UUID accountId = UUID.fromString("ffa649da-555e-4c55-bf65-84b06a4b3564");
    final DateTimeZone dateTimeZone = DateTimeZone.forOffsetHours(-8);

    public TestDateConversion() throws AccountApiException {
        super(null, null, null, null, Mockito.mock(AccountUserApi.class), null, new ClockMock(),  null);
        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getTimeZone()).thenReturn(dateTimeZone);
        Mockito.when(accountUserApi.getAccountById(accountId, null)).thenReturn(account);
    }

    @BeforeClass
    public void beforeClass() {
    }

    @Test(groups = "fast")
    public void testDateTimeConversion() {

        final String input = "2013-08-26T06:50:20Z";
        final LocalDate result = toLocalDate(accountId, input, null);
        Assert.assertTrue(result.compareTo(new LocalDate(2013, 8, 25)) == 0);
    }


    @Test(groups = "fast")
    public void testNullConversion() {
        ((ClockMock) clock).setTime(new DateTime("2013-08-26T06:50:20Z"));
        final String input = null;
        final LocalDate result = toLocalDate(accountId, input, null);
        Assert.assertTrue(result.compareTo(new LocalDate(2013, 8, 25)) == 0);
        ((ClockMock) clock).resetDeltaFromReality();
    }

    @Test(groups = "fast")
    public void testLocalDateConversion() {
        final String input = "2013-08-25";
        final LocalDate result = toLocalDate(accountId, input, null);
        Assert.assertTrue(result.compareTo(new LocalDate(2013, 8, 25)) == 0);
    }
}
