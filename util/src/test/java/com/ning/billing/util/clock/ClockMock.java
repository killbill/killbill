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

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.Months;
import org.joda.time.MutablePeriod;
import org.joda.time.Period;
import org.joda.time.ReadablePeriod;
import org.joda.time.Weeks;
import org.joda.time.Years;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.TimeUnit;

public class ClockMock implements Clock {
    
    private MutablePeriod delta = new MutablePeriod();
    private static final Logger log = LoggerFactory.getLogger(ClockMock.class);

     
    @Override
    public synchronized DateTime getNow(DateTimeZone tz) {
        return getUTCNow().toDateTime(tz);
    }

    @Override
    public synchronized DateTime getUTCNow() {
        return truncate(adjust(now()));
    }
    
    private DateTime adjust(DateTime now) {
        return now.plus(delta);
    }

    public synchronized void setTime(DateTime time) {
        DateTime prev = getUTCNow();
        delta = new MutablePeriod(now(), time);
        logChange(prev);
    }
    
    public synchronized void addDays(int days) {
        adjustTo(Days.days(days));
    }
    
    public synchronized void addWeeks(int weeks) {
        adjustTo(Weeks.weeks(weeks));
    }
    
    public synchronized void addMonths(int months) {
        adjustTo(Months.months(months));
    }
    
    public synchronized void addYears(int years) {
        adjustTo(Years.years(years));
    }
    
    public synchronized void reset() {
        delta = new MutablePeriod();
    }
    
    @Override
    public String toString() {
        return getUTCNow().toString();
    }
    
    private void adjustTo(ReadablePeriod period) {
        DateTime prev = getUTCNow();
        delta.add(period);
        logChange(prev);
    }
    
    private void logChange(DateTime prev) {     
        DateTime now = getUTCNow();
        log.info(String.format("            ************      ADJUSTING CLOCK FROM %s to %s     ********************", prev, now));
    }
    
    private DateTime now() {
        return new DateTime(DateTimeZone.UTC);
    }

    private DateTime truncate(DateTime time) {
        return time.minus(time.getMillisOfSecond());
    }
   
    //
    //Backward compatibility stuff
    //
    public synchronized void setDeltaFromReality(Duration duration, long epsilon) {
        DateTime prev = getUTCNow();
        delta.addMillis((int)epsilon);
        addDeltaFromReality(duration);
        logChange(prev);
        
    }

    public synchronized void addDeltaFromReality(Duration delta) {
        adjustTo(periodFromDuration(delta));
    }

    public synchronized void setDeltaFromReality(long delta) {
        adjustTo(new Period(delta));
    }

    public synchronized void addDeltaFromReality(long delta) {
        adjustTo(new Period(delta));
    }

    public synchronized void resetDeltaFromReality() {
        reset();
    }
    
    public ReadablePeriod periodFromDuration(Duration duration) {
        if (duration.getUnit() != TimeUnit.UNLIMITED) {return new Period();}

        switch (duration.getUnit()) {
            case DAYS:
                return Days.days(duration.getNumber());
            case MONTHS:
                return Months.months(duration.getNumber());
            case YEARS:
                return Years.years(duration.getNumber());
            case UNLIMITED:
                return Years.years(100);
           default:
                return new Period();
        }
    }
    

}
