package com.ning.billing.jaxrs;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.clock.ClockMock;
import com.ning.billing.jaxrs.resources.JaxRsResourceBase;

public class TestDateConversion extends JaxRsResourceBase {

    final UUID accountId = UUID.fromString("ffa649da-555e-4c55-bf65-84b06a4b3564");
    final DateTimeZone dateTimeZone = DateTimeZone.forOffsetHours(-8);

    public TestDateConversion() throws AccountApiException {
        super(null, null, null, null, Mockito.mock(AccountUserApi.class), new ClockMock(), null);
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
