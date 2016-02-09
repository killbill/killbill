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

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.clock.Clock;

public class EntitlementDateHelper {

    private final AccountInternalApi accountApi;
    private final Clock clock;

    public EntitlementDateHelper(final AccountInternalApi accountApi, final Clock clock) {
        this.accountApi = accountApi;
        this.clock = clock;
    }

    public DateTime fromLocalDateAndReferenceTime(final LocalDate requestedDate, final DateTime referenceDateTime, final InternalTenantContext callContext) throws EntitlementApiException {
        final ImmutableAccountData account;

        try {
            account = accountApi.getImmutableAccountDataByRecordId(callContext.getAccountRecordId(), callContext);
        } catch (final AccountApiException e) {
            throw new EntitlementApiException(e);
        }

        // If the input date overlaps with the present, we return NOW.
        final Interval interval = requestedDate.toInterval(account.getTimeZone());
        if (interval.contains(clock.getUTCNow())) {
            return clock.getUTCNow();
        }

        return callContext.toUTCDateTime(requestedDate, referenceDateTime, account.getTimeZone());
    }

    /**
     * Check if the date portion of a date/time is before or equals at now (as returned by the clock).
     *
     * @param inputDate             the fully qualified DateTime
     * @param accountTimeZone       the account timezone
     * @param internalTenantContext the context
     * @return true if the inputDate, once converted into a LocalDate using account timezone is less or equals than today
     */
    // TODO Move to ClockUtils
    public boolean isBeforeOrEqualsToday(final DateTime inputDate, final DateTimeZone accountTimeZone, final InternalTenantContext internalTenantContext) {
        final LocalDate localDateNowInAccountTimezone = clock.getToday(accountTimeZone);
        final LocalDate targetDateInAccountTimezone = internalTenantContext.toLocalDate(inputDate, accountTimeZone);
        return targetDateInAccountTimezone.compareTo(localDateNowInAccountTimezone) <= 0;
    }
}
