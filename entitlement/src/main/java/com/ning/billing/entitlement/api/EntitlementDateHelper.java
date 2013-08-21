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

package com.ning.billing.entitlement.api;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.clock.Clock;
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

        //
        // Ok, in the case of a LocalDate of today we expect any chnage to be immediate, so we check that DateTime returned is not in the future
        // (which means that reference time might not be honored, but this is not very important).
        //
        return adjustDateTimeToNotBeInFutureIfLocaDateIsToday(t2);
    }

    private final DateTime adjustDateTimeToNotBeInFutureIfLocaDateIsToday(final DateTime inputUtc) {
        // If the LocalDate is TODAY but after adding the reference time we end up in the future, we correct it to be NOW,
        // so change occurs immediately
        if (isBeforeOrEqualsToday(inputUtc, DateTimeZone.UTC) && inputUtc.compareTo(clock.getUTCNow()) > 0) {
            return clock.getUTCNow();
        } else {
            return inputUtc;
        }
    }

    /**
     *
     * @param inputDate       the fully qualified DateTime
     * @param accountTimeZone the acount timezone
     * @return true if the inputDate, once converted into a LocalDate using account timezone is less or equals than today
     */
    public boolean isBeforeOrEqualsToday(final DateTime inputDate, final DateTimeZone accountTimeZone) {

        final LocalDate localDateNowInAccountTimezone = new LocalDate(clock.getUTCNow(), accountTimeZone);
        final LocalDate targetDateInAccountTimezone = new LocalDate(inputDate, accountTimeZone);

        return targetDateInAccountTimezone.compareTo(localDateNowInAccountTimezone) <= 0;
    }
}
