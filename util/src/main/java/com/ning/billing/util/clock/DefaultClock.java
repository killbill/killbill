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

import com.ning.billing.catalog.api.IDuration;

public class DefaultClock implements Clock {

    @Override
    public DateTime getNow(DateTimeZone tz) {
       DateTime result = new DateTime(tz);
       return truncateMs(result);
    }

    @Override
    public DateTime getUTCNow() {
        return getNow(DateTimeZone.UTC);
    }


    public static DateTime truncateMs(DateTime input) {
        return input.minus(input.getMillisOfSecond());
    }

    public static DateTime addDuration(DateTime input, List<IDuration> durations) {

        DateTime result = input;
        for (IDuration cur : durations) {
            switch (cur.getUnit()) {
            case DAYS:
                result = result.plusDays(cur.getNumber());
                break;

            case MONTHS:
                result = result.plusMonths(cur.getNumber());
                break;

            case YEARS:
                result = result.plusYears(cur.getNumber());
                break;
            case UNLIMITED:
            default:
                throw new RuntimeException("Trying to move to unlimited time period");
            }
        }
        return result;
    }

    public static DateTime addDuration(DateTime input, IDuration duration) {
        List<IDuration> list = new ArrayList<IDuration>();
        list.add(duration);
        return addDuration(input, list);
    }
}
