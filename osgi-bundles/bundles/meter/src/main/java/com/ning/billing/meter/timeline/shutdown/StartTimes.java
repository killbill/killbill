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

package com.ning.billing.meter.timeline.shutdown;

import java.util.HashMap;
import java.util.Map;

import org.joda.time.DateTime;

/**
 * This class is used solely as a Json mapping class when saving timelines in a database
 * blob on shutdown, and restoring them on startup.
 * <p/>
 * The Map<Integer, Map<Integer, DateTime>> maps from sourceId to eventCategoryId to startTime.
 */
public class StartTimes {

    private final DateTime timeInserted;
    private final Map<Integer, Map<Integer, DateTime>> startTimesMap;
    private DateTime minStartTime;

    public StartTimes(final DateTime timeInserted, final Map<Integer, Map<Integer, DateTime>> startTimesMap) {
        this.timeInserted = timeInserted;
        this.startTimesMap = startTimesMap;
        DateTime minDateTime = new DateTime(Long.MAX_VALUE);
        for (final Map<Integer, DateTime> categoryMap : startTimesMap.values()) {
            for (final DateTime startTime : categoryMap.values()) {
                if (minDateTime.isAfter(startTime)) {
                    minDateTime = startTime;
                }
            }
        }
        this.minStartTime = minDateTime;
    }

    public StartTimes() {
        this.timeInserted = new DateTime();
        minStartTime = new DateTime(Long.MAX_VALUE);
        this.startTimesMap = new HashMap<Integer, Map<Integer, DateTime>>();
    }

    public void addTime(final int sourceId, final int categoryId, final DateTime dateTime) {
        Map<Integer, DateTime> sourceTimes = startTimesMap.get(sourceId);
        if (sourceTimes == null) {
            sourceTimes = new HashMap<Integer, DateTime>();
            startTimesMap.put(sourceId, sourceTimes);
        }
        sourceTimes.put(categoryId, dateTime);
        if (dateTime.isBefore(minStartTime)) {
            minStartTime = dateTime;
        }
    }

    public DateTime getStartTimeForSourceIdAndCategoryId(final int sourceId, final int categoryId) {
        final Map<Integer, DateTime> sourceTimes = startTimesMap.get(sourceId);
        if (sourceTimes != null) {
            return sourceTimes.get(categoryId);
        } else {
            return null;
        }
    }

    public Map<Integer, Map<Integer, DateTime>> getStartTimesMap() {
        return startTimesMap;
    }

    public DateTime getTimeInserted() {
        return timeInserted;
    }

    public DateTime getMinStartTime() {
        return minStartTime;
    }
}
