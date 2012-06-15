/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.util.clock;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.catalog.api.Duration;

public class DefaultClock implements Clock {

    @Override
    public DateTime getNow(final DateTimeZone tz) {
        final DateTime result = new DateTime(tz);
        return truncateMs(result);
    }

    @Override
    public DateTime getUTCNow() {
        return getNow(DateTimeZone.UTC);
    }

    public static DateTime toUTCDateTime(final DateTime input) {
        if (input == null) {
            return null;
        }
        final DateTime result = input.toDateTime(DateTimeZone.UTC);
        return truncateMs(result);
    }

    public static DateTime truncateMs(final DateTime input) {
        return input.minus(input.getMillisOfSecond());
    }


    public static DateTime addOrRemoveDuration(final DateTime input, final List<Duration> durations, final boolean add) {
        DateTime result = input;
        for (final Duration cur : durations) {
            switch (cur.getUnit()) {
                case DAYS:
                    result = add ? result.plusDays(cur.getNumber()) : result.minusDays(cur.getNumber());
                    break;

                case MONTHS:
                    result = add ? result.plusMonths(cur.getNumber()) : result.minusMonths(cur.getNumber());
                    break;

                case YEARS:
                    result = add ? result.plusYears(cur.getNumber()) : result.minusYears(cur.getNumber());
                    break;
                case UNLIMITED:
                default:
                    throw new RuntimeException("Trying to move to unlimited time period");
            }
        }
        return result;
    }

    public static DateTime addDuration(final DateTime input, final List<Duration> durations) {
        return addOrRemoveDuration(input, durations, true);
    }

    public static DateTime removeDuration(final DateTime input, final List<Duration> durations) {
        return addOrRemoveDuration(input, durations, false);
    }

    public static DateTime addDuration(final DateTime input, final Duration duration) {
        final List<Duration> list = new ArrayList<Duration>();
        list.add(duration);
        return addOrRemoveDuration(input, list, true);
    }

    public static DateTime removeDuration(final DateTime input, final Duration duration) {
        final List<Duration> list = new ArrayList<Duration>();
        list.add(duration);
        return addOrRemoveDuration(input, list, false);
    }
}
