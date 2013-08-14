package com.ning.billing.entitlement.api;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.clock.Clock;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.timezone.DateAndTimeZoneContext;

public class EntitlementDateHelper {

    private final AccountInternalApi accountApi;
    private final Clock clock;

    public EntitlementDateHelper(final AccountInternalApi accountApi, final Clock clock) {
        this.accountApi = accountApi;
        this.clock = clock;
    }

    /**
     * Returns a DateTime that is equals or beforeNow and whose LocalDate using the account timeZone is the one provided
     * <p/>
     * Relies on the subscriptionStartDate for the reference time
     *
     * @param requestedDate
     * @param referenceDateTime
     * @param callContext
     * @return
     * @throws EntitlementApiException
     */
    public DateTime fromLocalDateAndReferenceTime(final LocalDate requestedDate, final DateTime referenceDateTime, final InternalCallContext callContext) throws EntitlementApiException {
        try {
            /*
            final Account account = accountApi.getAccountByRecordId(callContext.getAccountRecordId(), callContext);
            final DateAndTimeZoneContext timeZoneContext = new DateAndTimeZoneContext(subscriptionStartDate, account.getTimeZone(), clock);
            final DateTime computedTime = timeZoneContext.computeUTCDateTimeFromLocalDate(requestedDate);

            return computedTime;
            */

            final Account account = accountApi.getAccountByRecordId(callContext.getAccountRecordId(), callContext);
            final LocalDate localDateNowInAccountTimezone = new LocalDate(requestedDate, account.getTimeZone());

            // Datetime from local date in account timezone and with given reference time
            final DateTime t1 = localDateNowInAccountTimezone.toDateTime(referenceDateTime.toLocalTime(), account.getTimeZone());
            // Datetime converted back in UTC
            final DateTime t2 = new DateTime(t1, DateTimeZone.UTC);
            return t2;

        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    public DateTime fromNowAndReferenceTime(final DateTime referenceDateTime, final InternalCallContext callContext) throws EntitlementApiException {
        try {
            final Account account = accountApi.getAccountByRecordId(callContext.getAccountRecordId(), callContext);
            final LocalDate localDateNowInAccountTimezone = new LocalDate(clock.getUTCNow(), account.getTimeZone());
            // Datetime from local date in account timezone and with given reference time
            final DateTime t1 = localDateNowInAccountTimezone.toDateTime(referenceDateTime.toLocalTime(), account.getTimeZone());
            // Datetime converted back in UTC
            final DateTime t2 = new DateTime(t1, DateTimeZone.UTC);
            return t2;
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }
    }
}
