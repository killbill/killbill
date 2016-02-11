/*
 * Copyright 2016 Groupon, Inc
 * Copyright 2016 The Billing Project, LLC
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

package org.killbill.billing.callcontext;

import java.util.Date;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

// TODO Cache the reference time and clock in the context
public class TimeAwareContext {

    private final DateTimeZone referenceDateTimeZone;

    public TimeAwareContext(final DateTimeZone referenceDateTimeZone) {
        this.referenceDateTimeZone = referenceDateTimeZone;
    }

    /// Generic functions
    /// TODO Move to ClockUtil

    // Create a DateTime object forcing the time zone to be UTC
    protected DateTime toUTCDateTime(final DateTime dateTime) {
        return toDateTime(dateTime, DateTimeZone.UTC);
    }

    // Create a DateTime object using the specified timezone (usually, the one on the account)
    public DateTime toDateTime(final DateTime dateTime, final DateTimeZone accountTimeZone) {
        return dateTime.toDateTime(accountTimeZone);
    }

    /// DateTime <-> LocalDate transformations

    // Create a DateTime object using the specified reference time and timezone (usually, the one on the account)
    public DateTime toUTCDateTime(final LocalDate localDate, final DateTime referenceDateTime) {
        validateContext();

        final DateTimeZone normalizedAccountTimezone = getNormalizedAccountTimezone(referenceDateTime, getReferenceDateTimeZone());

        final LocalTime referenceLocalTime = toDateTime(referenceDateTime, normalizedAccountTimezone).toLocalTime();

        final DateTime targetDateTime = new DateTime(localDate.getYear(),
                                                     localDate.getMonthOfYear(),
                                                     localDate.getDayOfMonth(),
                                                     referenceLocalTime.getHourOfDay(),
                                                     referenceLocalTime.getMinuteOfHour(),
                                                     referenceLocalTime.getSecondOfMinute(),
                                                     normalizedAccountTimezone);

        return toUTCDateTime(targetDateTime);
    }

    // Create a LocalDate object using the specified timezone (usually, the one on the account), respecting the offset at the time of the referenceDateTime
    public LocalDate toLocalDate(final DateTime dateTime, final DateTime referenceDateTime) {
        validateContext();

        final DateTimeZone normalizedAccountTimezone = getNormalizedAccountTimezone(referenceDateTime, getReferenceDateTimeZone());
        return new LocalDate(dateTime, normalizedAccountTimezone);
    }

    private DateTimeZone getNormalizedAccountTimezone(final DateTime referenceDateTime, final DateTimeZone accountTimeZoneUnused) {
        // Check if DST was in effect at the reference date time
        final boolean shouldUseDST = !getReferenceDateTimeZone().isStandardOffset(referenceDateTime.getMillis());
        if (shouldUseDST) {
            return DateTimeZone.forOffsetMillis(getReferenceDateTimeZone().getOffset(referenceDateTime.getMillis()));
        } else {
            return DateTimeZone.forOffsetMillis(getReferenceDateTimeZone().getStandardOffset(referenceDateTime.getMillis()));
        }
    }

    private void validateContext() {
        if (getReferenceDateTimeZone() == null) {
            throw new IllegalArgumentException(String.format("Context mis-configured: getReferenceDateTimeZone()=%s", getReferenceDateTimeZone()));
        }
    }

    // For convenience, to be overridden in tests
    public DateTimeZone getReferenceDateTimeZone() {
        return referenceDateTimeZone;
    }
}
