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

public class TimeAwareContext {

    // From JDK to Joda (see http://www.joda.org/joda-time/userguide.html#JDK_Interoperability)
    public DateTime toUTCDateTime(final Date date) {
        return toUTCDateTime(new DateTime(date));
    }

    // Create a DateTime object forcing the time zone to be UTC
    public DateTime toUTCDateTime(final DateTime dateTime) {
        return dateTime.toDateTime(DateTimeZone.UTC);
    }

    // Create a LocalDate object using the specified timezone (usually, the one on the account)
    // TODO Should we cache the accountTimeZone in the context?
    public LocalDate toLocalDate(final DateTime effectiveDate, final DateTimeZone accountTimeZone) {
        return new LocalDate(effectiveDate, accountTimeZone);
    }
}
