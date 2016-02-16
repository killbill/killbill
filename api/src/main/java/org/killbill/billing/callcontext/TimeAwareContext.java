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

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.IllegalInstantException;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

public class TimeAwareContext {

    private final DateTimeZone fixedOffsetTimeZone;
    private final LocalTime referenceTime;

    public TimeAwareContext(@Nullable final DateTimeZone fixedOffsetTimeZone, @Nullable final DateTime referenceDateTime) {
        this.fixedOffsetTimeZone = fixedOffsetTimeZone;
        this.referenceTime = computeReferenceTime(referenceDateTime);
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
    public DateTime toUTCDateTime(final LocalDate localDate) {
        validateContext();

         DateTime targetDateTime;
        try {
            targetDateTime = new DateTime(localDate.getYear(),
                                          localDate.getMonthOfYear(),
                                          localDate.getDayOfMonth(),
                                          getReferenceTime().getHourOfDay(),
                                          getReferenceTime().getMinuteOfHour(),
                                          getReferenceTime().getSecondOfMinute(),
                                          getFixedOffsetTimeZone());
        } catch (final IllegalInstantException e) {
            // DST gap (shouldn't happen when using fixed offset timezones)
            targetDateTime = localDate.toDateTimeAtStartOfDay(getFixedOffsetTimeZone());
        }

        return toUTCDateTime(targetDateTime);
    }

    // Create a LocalDate object using the specified timezone (usually, the one on the account), respecting the offset at the time of the referenceDateTime
    public LocalDate toLocalDate(final DateTime dateTime) {
        validateContext();

        return new LocalDate(dateTime, getFixedOffsetTimeZone());
    }

    private void validateContext() {
        if (getFixedOffsetTimeZone() == null || getReferenceTime() == null) {
            throw new IllegalArgumentException(String.format("Context mis-configured: fixedOffsetTimeZone=%s, referenceTime=%s", getFixedOffsetTimeZone(), getReferenceTime()));
        }
    }

    protected LocalTime computeReferenceTime(@Nullable final DateTime referenceTime) {
        return referenceTime == null ? null : toDateTime(referenceTime, getFixedOffsetTimeZone()).toLocalTime();
    }

    // For convenience, to be overridden in tests

    public DateTimeZone getFixedOffsetTimeZone() {
        return fixedOffsetTimeZone;
    }

    public LocalTime getReferenceTime() {
        return referenceTime;
    }
}
