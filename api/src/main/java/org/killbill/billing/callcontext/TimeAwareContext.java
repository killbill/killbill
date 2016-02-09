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

// TODO Cache the accountTimeZone, reference time and clock in the context
public class TimeAwareContext {

    // From JDK to Joda (see http://www.joda.org/joda-time/userguide.html#JDK_Interoperability)
    public DateTime toUTCDateTime(final Date date) {
        return toUTCDateTime(new DateTime(date));
    }

    // Create a DateTime object forcing the time zone to be UTC
    public DateTime toUTCDateTime(final DateTime dateTime) {
        return toDateTime(dateTime, DateTimeZone.UTC);
    }

    // Create a DateTime object using the specified reference time and timezone (usually, the one on the account)
    public DateTime toUTCDateTime(final LocalDate localDate, final DateTime referenceDateTime, final DateTimeZone accountTimeZone) {
        return toUTCDateTime(toDateTime(localDate, referenceDateTime, accountTimeZone));
    }

    // Create a DateTime object using the specified reference time and timezone (usually, the one on the account)
    public DateTime toDateTime(final LocalDate localDate, final DateTime referenceDateTime, final DateTimeZone accountTimeZone) {
        final LocalTime referenceLocalTime = toDateTime(referenceDateTime, accountTimeZone).toLocalTime();

        return new DateTime(localDate.getYear(),
                            localDate.getMonthOfYear(),
                            localDate.getDayOfMonth(),
                            referenceLocalTime.getHourOfDay(),
                            referenceLocalTime.getMinuteOfHour(),
                            referenceLocalTime.getSecondOfMinute(),
                            accountTimeZone);
    }

    // Create a DateTime object using the specified timezone (usually, the one on the account)
    public DateTime toDateTime(final DateTime dateTime, final DateTimeZone accountTimeZone) {
        return dateTime.toDateTime(accountTimeZone);
    }

    // Create a LocalDate object using the specified timezone (usually, the one on the account)
    public LocalDate toLocalDate(final DateTime dateTime, final DateTimeZone accountTimeZone) {
        return new LocalDate(dateTime, accountTimeZone);
    }
}
