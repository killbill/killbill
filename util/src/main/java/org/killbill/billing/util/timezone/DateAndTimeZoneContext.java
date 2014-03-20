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

package org.killbill.billing.util.timezone;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import org.killbill.clock.Clock;

/**
 * Used by entitlement and invoice to calculate:
 * - a LocalDate from DateTime and the timeZone set on the account
 * - A DateTime from a LocalDate and the referenceTime attached to the account.
 */
public final class DateAndTimeZoneContext {

    private final LocalTime referenceTime;
    private final int offsetFromUtc;
    private final DateTimeZone accountTimeZone;
    private final Clock clock;

    public DateAndTimeZoneContext(final DateTime effectiveDateTime, final DateTimeZone accountTimeZone, final Clock clock) {
        this.clock = clock;
        this.referenceTime = effectiveDateTime != null ? effectiveDateTime.toLocalTime() : null;
        this.accountTimeZone = accountTimeZone;
        this.offsetFromUtc = computeOffsetFromUtc(effectiveDateTime, accountTimeZone);
    }

    static int computeOffsetFromUtc(final DateTime effectiveDateTime, final DateTimeZone accountTimeZone) {
        final LocalDate localDateInAccountTimeZone = new LocalDate(effectiveDateTime, accountTimeZone);
        final LocalDate localDateInUTC = new LocalDate(effectiveDateTime, DateTimeZone.UTC);
        return Days.daysBetween(localDateInUTC, localDateInAccountTimeZone).getDays();
    }

    public LocalDate computeTargetDate(final DateTime targetDateTime) {
        return new LocalDate(targetDateTime, accountTimeZone);
    }


    public DateTime computeUTCDateTimeFromLocalDate(final LocalDate invoiceItemEndDate) {
        //
        // Since we create the targetDate for next invoice using the date from the notificationQ, we need to make sure
        // that this datetime once transformed into a LocalDate points to the correct day.
        //
        // All we need to do is figure if the transformation from DateTime (point in time) to LocalDate (date in account time zone)
        // changed the day; if so, when we recompute a UTC date from LocalDate (date in account time zone), we can simply chose a reference
        // time and apply the offset backward to end up on the right day
        //
        // We use clock.getUTCNow() to get the offset with account timezone but that may not be correct
        // when we transition from standard time and daylight saving time. We could end up with a result
        // that is slightly in advance and therefore results in a null invoice.
        // We will fix that by re-inserting ourselves in the notificationQ if we detect that there is no invoice
        // and yet the subscription is recurring and not cancelled.
        //
        return invoiceItemEndDate.toDateTime(referenceTime, DateTimeZone.UTC).plusDays(-offsetFromUtc);
    }

    public DateTime computeUTCDateTimeFromNow() {
        final LocalDate now = computeTargetDate(clock.getUTCNow());
        return computeUTCDateTimeFromLocalDate(now);
    }

}
