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

import com.ning.billing.catalog.api.Duration;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

// STEPH should really be in tests but not accessible from other sub modules
public class OldClockMock extends DefaultClock {

    private static final Logger log = LoggerFactory.getLogger(OldClockMock.class);

    private enum DeltaType {
        DELTA_NONE,
        DELTA_DURATION,
        DELTA_ABS
    }

    private long deltaFromRealityMs;
    private List<Duration> deltaFromRealityDuration;
    private long deltaFromRealityDurationEpsilon;
    private DeltaType deltaType;

    public OldClockMock() {
        deltaType = DeltaType.DELTA_NONE;
        deltaFromRealityMs = 0;
        deltaFromRealityDurationEpsilon = 0;
        deltaFromRealityDuration = null;
    }

    @Override
    public synchronized DateTime getNow(DateTimeZone tz) {
        return adjust(super.getNow(tz));
    }

    @Override
    public synchronized DateTime getUTCNow() {
        return getNow(DateTimeZone.UTC);
    }

    private void logClockAdjustment(DateTime prev, DateTime next) {
        log.info(String.format("            ************      ADJUSTING CLOCK FROM %s to %s     ********************", prev, next));
    }

    public synchronized void setDeltaFromReality(Duration delta, long epsilon) {
        DateTime prev = getUTCNow();
        deltaType = DeltaType.DELTA_DURATION;
        deltaFromRealityDuration = new ArrayList<Duration>();
        deltaFromRealityDuration.add(delta);
        deltaFromRealityDurationEpsilon = epsilon;
        deltaFromRealityMs = 0;
        logClockAdjustment(prev, getUTCNow());
    }

    public synchronized void addDeltaFromReality(Duration delta) {
        DateTime prev = getUTCNow();
        if (deltaType != DeltaType.DELTA_DURATION) {
            throw new RuntimeException("ClockMock should be set with type DELTA_DURATION");
        }
        deltaFromRealityDuration.add(delta);
        logClockAdjustment(prev, getUTCNow());
    }

    public synchronized void setDeltaFromReality(long delta) {
        DateTime prev = getUTCNow();
        deltaType = DeltaType.DELTA_ABS;
        deltaFromRealityDuration = null;
        deltaFromRealityDurationEpsilon = 0;
        deltaFromRealityMs = delta;
        logClockAdjustment(prev, getUTCNow());
    }

    public synchronized void addDeltaFromReality(long delta) {
        DateTime prev = getUTCNow();
        if (deltaType != DeltaType.DELTA_ABS) {
            throw new RuntimeException("ClockMock should be set with type DELTA_ABS");
        }
        deltaFromRealityDuration = null;
        deltaFromRealityDurationEpsilon = 0;
        deltaFromRealityMs += delta;
        logClockAdjustment(prev, getUTCNow());
    }

    public synchronized void resetDeltaFromReality() {
        deltaType = DeltaType.DELTA_NONE;
        deltaFromRealityDuration = null;
        deltaFromRealityDurationEpsilon = 0;
        deltaFromRealityMs = 0;
    }

    private DateTime adjust(DateTime realNow) {
        switch(deltaType) {
            case DELTA_NONE:
                return realNow;
            case DELTA_ABS:
                return adjustFromAbsolute(realNow);
            case DELTA_DURATION:
                return adjustFromDuration(realNow);
            default:
                return null;
        }
    }

    private DateTime adjustFromDuration(DateTime input) {

        DateTime result = input;
        for (Duration cur : deltaFromRealityDuration) {
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
                throw new RuntimeException("ClockMock is adjusting an unlimited time period");
            }
        }
        if (deltaFromRealityDurationEpsilon != 0) {
            result = result.plus(deltaFromRealityDurationEpsilon);
        }
        return result;
    }

    private DateTime adjustFromAbsolute(DateTime input) {
        return truncateMs(input.plus(deltaFromRealityMs));
    }

    @Override
    public String toString() {
        return getUTCNow().toString();
    }

    
}
