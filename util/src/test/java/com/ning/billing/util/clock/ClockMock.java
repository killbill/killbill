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
import org.joda.time.LocalDate;
import org.joda.time.Months;
import org.joda.time.Period;
import org.joda.time.ReadablePeriod;
import org.joda.time.Weeks;
import org.joda.time.Years;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClockMock implements Clock {

    private static final Logger log = LoggerFactory.getLogger(ClockMock.class);

    private DateTime mockDateTime;
    private long initialDeltaMillis;

    public ClockMock() {
        reset();
    }

    @Override
    public synchronized DateTime getNow(final DateTimeZone tz) {
        return getUTCNow().toDateTime(tz);
    }

    @Override
    public synchronized DateTime getUTCNow() {
        return truncate(mockDateTime.plus(System.currentTimeMillis() - initialDeltaMillis));
    }

    @Override
    public LocalDate getUTCToday() {
        return getToday(DateTimeZone.UTC);
    }

    @Override
    public LocalDate getToday(final DateTimeZone timeZone) {
        return new LocalDate(getUTCNow(), timeZone);
    }

    @Override
    public String toString() {
        return getUTCNow().toString();
    }

    public synchronized void addDays(final int days) {
        adjustTo(Days.days(days));
    }

    public synchronized void addWeeks(final int weeks) {
        adjustTo(Weeks.weeks(weeks));
    }

    public synchronized void addMonths(final int months) {
        adjustTo(Months.months(months));
    }

    public synchronized void addYears(final int years) {
        adjustTo(Years.years(years));
    }

    public synchronized void setDeltaFromReality(final long delta) {
        // The name of the method is misleading - don't reset it here
        addDeltaFromReality(delta);
    }

    public synchronized void addDeltaFromReality(final long delta) {
        adjustTo(new Period(delta));
    }

    public synchronized void setDay(final LocalDate date) {
        setTime(date.toDateTimeAtStartOfDay(DateTimeZone.UTC));
    }

    public synchronized void setTime(final DateTime time) {
        final DateTime prev = getUTCNow();
        reset(time);
        logChange(prev);
    }

    public synchronized void resetDeltaFromReality() {
        reset();
    }

    private synchronized void reset() {
        reset(realNow());
    }

    private void reset(final DateTime time) {
        mockDateTime = time;
        initialDeltaMillis = System.currentTimeMillis();
    }

    private void adjustTo(final ReadablePeriod period) {
        final DateTime prev = getUTCNow();
        mockDateTime = mockDateTime.plus(period);
        logChange(prev);
    }

    private void logChange(final DateTime prev) {
        final DateTime now = getUTCNow();
        log.info(String.format("            ************      ADJUSTING CLOCK FROM %s to %s     ********************", prev, now));
    }

    private DateTime truncate(final DateTime time) {
        return time.minus(time.getMillisOfSecond());
    }

    private DateTime realNow() {
        return new DateTime(DateTimeZone.UTC);
    }
}
