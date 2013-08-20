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

package com.ning.billing.util.timezone;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import com.ning.billing.clock.Clock;

/**
 * Used by entitlement and invoice to calculate:
 * - a LocalDate from DateTime and the timeZone set on the account
 * - A DateTime from a LocalDate and the referenceTime attached to the account.
 */
public final class DateAndTimeZoneContext {

    private final LocalTime referenceTime;
    private final DateTimeZone accountTimeZone;
    private final Clock clock;

    public DateAndTimeZoneContext(final DateTime effectiveDateTime, final DateTimeZone accountTimeZone, final Clock clock) {
        this.clock = clock;
        this.referenceTime = effectiveDateTime != null ? effectiveDateTime.toLocalTime() : null;
        this.accountTimeZone = accountTimeZone;
    }

    public LocalDate computeTargetDate(final DateTime targetDateTime) {
        return new LocalDate(targetDateTime, accountTimeZone);
    }


    public DateTime computeUTCDateTimeFromLocalDate(final LocalDate invoiceItemEndDate) {
        //
        // Since we create the targetDate for next invoice using the date from the notificationQ, we need to make sure
        // that this datetime once transformed into a LocalDate points to the correct day.
        //
        // e.g If accountTimeZone is -8 and we want to invoice on the 16, with a toDateTimeAtCurrentTime = 00:00:23,
        // we will generate a datetime that is 16T08:00:23 => LocalDate in that timeZone stays on the 16.
        //
        //
        // We use clock.getUTCNow() to get the offset with account timezone but that may not be correct
        // when we transition from standard time and daylight saving time. We could end up with a result
        // that is slightly in advance and therefore results in a null invoice.
        // We will fix that by re-inserting ourselves in the notificationQ if we detect that there is no invoice
        // and yet the subscription is recurring and not cancelled.
        //
        final int utcOffest = accountTimeZone.getOffset(clock.getUTCNow());
        final int localToUTCOffest = -1 * utcOffest;
        return invoiceItemEndDate.toDateTime(referenceTime, DateTimeZone.UTC).plusMillis(localToUTCOffest);
    }

    public DateTime computeUTCDateTimeFromNow() {
        final LocalDate now = computeTargetDate(clock.getUTCNow());
        return computeUTCDateTimeFromLocalDate(now);
    }

}
