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
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.killbill.clock.ClockUtil;

public class TimeAwareContext {

    private final DateTimeZone fixedOffsetTimeZone;
    private final DateTime referenceDateTime;
    private final LocalTime referenceLocalTime;

    public TimeAwareContext(@Nullable final DateTimeZone fixedOffsetTimeZone, @Nullable final DateTime referenceDateTime) {
        this.fixedOffsetTimeZone = fixedOffsetTimeZone;
        this.referenceDateTime = referenceDateTime;
        this.referenceLocalTime = computeReferenceTime(referenceDateTime);
    }

    public DateTime toUTCDateTime(final LocalDate localDate) {
        validateContext();

        return ClockUtil.toUTCDateTime(localDate, getReferenceLocalTime(), getFixedOffsetTimeZone());
    }

    public LocalDate toLocalDate(final DateTime dateTime) {
        validateContext();

        return ClockUtil.toLocalDate(dateTime, getFixedOffsetTimeZone());
    }

    private void validateContext() {
        if (getFixedOffsetTimeZone() == null || getReferenceLocalTime() == null) {
            throw new IllegalArgumentException(String.format("Context mis-configured: fixedOffsetTimeZone=%s, referenceLocalTime=%s", getFixedOffsetTimeZone(), getReferenceLocalTime()));
        }
    }

    DateTime getReferenceDateTime() {
        return referenceDateTime;
    }

    // For convenience (used in tests)

    //@VisibleForTesting
    protected LocalTime computeReferenceTime(@Nullable final DateTime referenceTime) {
        return referenceTime == null ? null : ClockUtil.toDateTime(referenceTime, getFixedOffsetTimeZone()).toLocalTime();
    }

    //@VisibleForTesting
    public DateTimeZone getFixedOffsetTimeZone() {
        return fixedOffsetTimeZone;
    }

    //@VisibleForTesting
    public LocalTime getReferenceLocalTime() {
        return referenceLocalTime;
    }
}
