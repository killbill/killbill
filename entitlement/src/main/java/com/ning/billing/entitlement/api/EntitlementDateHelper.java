package com.ning.billing.entitlement.api;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.clock.Clock;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.svcapi.account.AccountInternalApi;

public class EntitlementDateHelper {

    private final AccountInternalApi accountApi;
    private final Clock clock;

    public EntitlementDateHelper(final AccountInternalApi accountApi, final Clock clock) {
        this.accountApi = accountApi;
        this.clock = clock;
    }

    public DateTime fromNowAndReferenceTime(final DateTime referenceDateTime, final InternalTenantContext callContext) throws EntitlementApiException {
        try {
            final Account account = accountApi.getAccountByRecordId(callContext.getAccountRecordId(), callContext);
            return fromNowAndReferenceTime(referenceDateTime, account.getTimeZone());
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    public DateTime fromNowAndReferenceTime(final DateTime referenceDateTime, final DateTimeZone accountTimeZone) {
        final LocalDate localDateNowInAccountTimezone = new LocalDate(clock.getUTCNow(), accountTimeZone);
        return fromLocalDateAndReferenceTime(localDateNowInAccountTimezone, referenceDateTime, accountTimeZone);
    }

    public DateTime fromLocalDateAndReferenceTime(final LocalDate requestedDate, final DateTime referenceDateTime, final InternalTenantContext callContext) throws EntitlementApiException {
        try {

            final Account account = accountApi.getAccountByRecordId(callContext.getAccountRecordId(), callContext);
            return fromLocalDateAndReferenceTime(requestedDate, referenceDateTime, account.getTimeZone());
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    public DateTime fromLocalDateAndReferenceTime(final LocalDate requestedDate, final DateTime referenceDateTime, final DateTimeZone accountTimeZone) {
        final LocalDate localDateNowInAccountTimezone = new LocalDate(requestedDate, accountTimeZone);
        // Datetime from local date in account timezone and with given reference time
        final DateTime t1 = localDateNowInAccountTimezone.toDateTime(referenceDateTime.toLocalTime(), accountTimeZone);
        // Datetime converted back in UTC
        final DateTime t2 = new DateTime(t1, DateTimeZone.UTC);
        return t2;
    }



    // STEPH_ENT test
    public boolean isBeforeOrEqualsNow(final DateTime inputDate, final DateTimeZone accountTimeZone) {

        final LocalDate localDateNowInAccountTimezone = new LocalDate(clock.getUTCNow(), accountTimeZone);
        final LocalDate targetDateInAccountTimezone = new LocalDate(inputDate, accountTimeZone);

        return targetDateInAccountTimezone.compareTo(localDateNowInAccountTimezone) <= 0;
    }
}
