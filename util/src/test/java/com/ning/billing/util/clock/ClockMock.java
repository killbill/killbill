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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.TimeUnit;

public class ClockMock implements Clock {
    private static final Logger log = LoggerFactory.getLogger(ClockMock.class);
    DateTime currentTime = new DateTime(DateTimeZone.UTC);
     
    @Override
    public synchronized DateTime getNow(DateTimeZone tz) {
        return getUTCNow().toDateTime(tz);
    }

    @Override
    public synchronized DateTime getUTCNow() {
        try {
        return currentTime.plus(epsilon).minus(currentTime.getMillisOfSecond());
        } catch(RuntimeException e) {
            throw e;
        }
    }
    
    public synchronized void setTime(DateTime time) {
        adjustTo(time.toDateTime(DateTimeZone.UTC));
   }
    
    public synchronized void addDays(int days) {
        adjustTo(currentTime.plusDays(days));
    }
    
    public synchronized void addWeeks(int weeks) {
        adjustTo(currentTime.plusWeeks(weeks));
    }
    
    public synchronized void addMonths(int months) {
        adjustTo(currentTime.plusMonths(months));
    }
    
    public synchronized void addYears(int years) {
        adjustTo(currentTime.plusMonths(years));
    }
    
    public synchronized void reset() {
        adjustTo(new DateTime(DateTimeZone.UTC));
    }
    
    @Override
    public String toString() {
        return getUTCNow().toString();
    }
    
    private void adjustTo(DateTime newTime) {
        if(newTime == null) {
            log.error("Attempting to adjust clock to a null value");
            newTime = new DateTime(DateTimeZone.UTC);
        }
        logClockAdjustment(currentTime, newTime);
        currentTime = newTime;
    }
    
    private void logClockAdjustment(DateTime prev, DateTime next) {
        log.info(String.format("            ************      ADJUSTING CLOCK FROM %s to %s     ********************", prev, next));
    }

    //
    //Backward compatibility stuff
    //
    long epsilon = 0;
    
    public synchronized void setDeltaFromReality(Duration delta, long epsilon) {
        this.epsilon = epsilon;
        addDeltaFromReality(delta);
    }

    public synchronized void addDeltaFromReality(Duration delta) {
        adjustTo(addDurationToDateTime(delta,currentTime));
    }

    public synchronized void setDeltaFromReality(long delta) {
        adjustTo(currentTime.plus(delta));
    }

    public synchronized void addDeltaFromReality(long delta) {
        adjustTo(currentTime.plus(delta));
    }

    public synchronized void resetDeltaFromReality() {
        reset();
    }
    
    public DateTime addDurationToDateTime(Duration duration, DateTime dateTime) {
        if (duration.getUnit() != TimeUnit.UNLIMITED) {return dateTime;}

        switch (duration.getUnit()) {
            case DAYS:
                return dateTime.plusDays(duration.getNumber());
            case MONTHS:
                return dateTime.plusMonths(duration.getNumber());
            case YEARS:
                return dateTime.plusYears(duration.getNumber());
            case UNLIMITED:
                return dateTime.plusYears(100);
            default:
                return dateTime;
        }
    }
    

}
