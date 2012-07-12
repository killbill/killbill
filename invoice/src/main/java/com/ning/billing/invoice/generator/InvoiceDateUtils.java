/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.invoice.generator;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.MutableDateTime;

public class InvoiceDateUtils {
    public static DateTime roundDateTimeToDate(final DateTime input, final DateTimeZone timeZone) {
        if (input == null) {
            return null;
        }
        final DateTime tzAdjustedStartDate = input.toDateTime(timeZone);

        return new DateTime(tzAdjustedStartDate.getYear(), tzAdjustedStartDate.getMonthOfYear(), tzAdjustedStartDate.getDayOfMonth(), 0, 0, timeZone);
    }

    // Note: date has to be in UTC
    public static DateTime calculateBillingCycleDateOnOrAfter(final DateTime date, final int billingCycleDay) {
        final int lastDayOfMonth = date.dayOfMonth().getMaximumValue();

        final MutableDateTime tmp = date.toMutableDateTime();
        if (billingCycleDay > lastDayOfMonth) {
            tmp.setDayOfMonth(lastDayOfMonth);
        } else {
            tmp.setDayOfMonth(billingCycleDay);
        }
        DateTime proposedDate = tmp.toDateTime();

        while (proposedDate.isBefore(date)) {
            proposedDate = proposedDate.plusMonths(1);
        }
        return proposedDate;
    }

    // Note: date has to be in UTC
    public static DateTime calculateBillingCycleDateAfter(final DateTime date, final int billingCycleDay) {
        DateTime proposedDate = calculateBillingCycleDateOnOrAfter(date, billingCycleDay);

        if (date.compareTo(proposedDate) == 0) {
            proposedDate = proposedDate.plusMonths(1);
        }
        return proposedDate;
    }
}
