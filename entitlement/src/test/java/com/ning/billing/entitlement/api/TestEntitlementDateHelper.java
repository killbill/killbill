package com.ning.billing.entitlement.api;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.entitlement.EntitlementTestSuiteNoDB;
import com.ning.billing.util.callcontext.InternalTenantContext;

public class TestEntitlementDateHelper extends EntitlementTestSuiteNoDB {

    private Account account;
    private EntitlementDateHelper dateHelper;

    @BeforeClass(groups = "fast")
    public void beforeMethod() throws Exception {
        super.beforeClass();


        account = Mockito.mock(Account.class);
        Mockito.when(accountInternalApi.getAccountByRecordId(Mockito.anyLong(), Mockito.<InternalTenantContext>any())).thenReturn(account);
        dateHelper = new EntitlementDateHelper(accountInternalApi, clock);
    }

    @Test(groups = "fast")
    public void testWithAccountInUtc() throws EntitlementApiException {

        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        Mockito.when(account.getTimeZone()).thenReturn(DateTimeZone.UTC);

        final DateTime refererenceDateTime = new DateTime(2013, 1, 1, 15, 43, 25, 0, DateTimeZone.UTC);
        final DateTime targetDate = dateHelper.fromNowAndReferenceTime(refererenceDateTime, internalCallContext);
        final DateTime expectedDate = new DateTime(2013, 8, 7, 15, 43, 25, 0, DateTimeZone.UTC);
        Assert.assertEquals(targetDate, expectedDate);
    }


    @Test(groups = "fast")
    public void testWithAccountInUtcMinus8() throws EntitlementApiException {

        // We start with a time only 6:43:25 < 8 -> localTime in accountTimeZone will be 2013, 8, 6
        clock.setTime(new DateTime(2013, 8, 7, 6, 43, 25, 0, DateTimeZone.UTC));

        final DateTimeZone timeZoneUtcMinus8 = DateTimeZone.forOffsetHours(-8);
        Mockito.when(account.getTimeZone()).thenReturn(timeZoneUtcMinus8);

        // We also use a reference time of 1, 28, 10, 0 -> DateTime in accountTimeZone will be (2013, 8, 6, 1, 28, 10)
        final DateTime refererenceDateTime = new DateTime(2013, 1, 1, 1, 28, 10, 0, DateTimeZone.UTC);
        final DateTime targetDate = dateHelper.fromNowAndReferenceTime(refererenceDateTime, internalCallContext);

        // And so that datetime in UTC becomes expectedDate below
        final DateTime expectedDate = new DateTime(2013, 8, 6, 9, 28, 10, 0, DateTimeZone.UTC);
        Assert.assertEquals(targetDate, expectedDate);
    }



    @Test(groups = "fast")
    public void testWithAccountInUtcPlus5() throws EntitlementApiException {

        final LocalDate inputDate = new LocalDate(2013, 8, 7);

        final DateTimeZone timeZoneUtcMinus8 = DateTimeZone.forOffsetHours(+5);
        Mockito.when(account.getTimeZone()).thenReturn(timeZoneUtcMinus8);

        // We also use a reference time of 20, 28, 10, 0 -> DateTime in accountTimeZone will be (2013, 8, 7, 20, 28, 10)
        final DateTime refererenceDateTime = new DateTime(2013, 1, 1, 20, 28, 10, 0, DateTimeZone.UTC);
        final DateTime targetDate = dateHelper.fromLocalDateAndReferenceTime(inputDate, refererenceDateTime, internalCallContext);

        // And so that datetime in UTC becomes expectedDate below
        final DateTime expectedDate = new DateTime(2013, 8, 7, 15, 28, 10, 0, DateTimeZone.UTC);
        Assert.assertEquals(targetDate, expectedDate);
    }

}
