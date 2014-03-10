/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.util.timezone;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.testng.annotations.Test;

import org.killbill.billing.util.UtilTestSuiteNoDB;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

//
// There are two categories of tests, one that test the offset calculation and one that calculates
// how to get a DateTime from a LocalDate (in account time zone)
//
// Tests {1, 2, 3} use an account timezone with a negative offset (-8) and tests {A, B, C} use an account timezone with a positive offset (+8)
//
public class TestDateAndTimeZoneContext extends UtilTestSuiteNoDB {

    private final DateTimeFormatter DATE_TIME_FORMATTER = ISODateTimeFormat.dateTimeParser();

    final String effectiveDateTime1 = "2012-01-20T07:30:42.000Z";
    final String effectiveDateTime2 = "2012-01-20T08:00:00.000Z";
    final String effectiveDateTime3 = "2012-01-20T08:45:33.000Z";

    final String effectiveDateTimeA = "2012-01-20T16:30:42.000Z";
    final String effectiveDateTimeB = "2012-01-20T16:00:00.000Z";
    final String effectiveDateTimeC = "2012-01-20T15:30:42.000Z";


    //
    // Take an negative timezone offset and a reference time that is less than the offset (07:30:42 < 8)
    // => to expect a negative offset of one day
    //
    @Test(groups = "fast")
    public void testComputeOffset1() {

        final DateTimeZone timeZone = DateTimeZone.forOffsetHours(-8);
        final DateTime effectiveDateTime = DATE_TIME_FORMATTER.parseDateTime(effectiveDateTime1);

        int offset = DateAndTimeZoneContext.computeOffsetFromUtc(effectiveDateTime, timeZone);
        assertEquals(offset, -1);
    }

    //
    // Take an negative timezone offset and a reference time that is equal than the offset (08:00:00 = 8)
    // => to expect an offset of 0
    //
    @Test(groups = "fast")
    public void testComputeOffset2() {

        final DateTimeZone timeZone = DateTimeZone.forOffsetHours(-8);
        final DateTime effectiveDateTime = DATE_TIME_FORMATTER.parseDateTime(effectiveDateTime2);

        int offset = DateAndTimeZoneContext.computeOffsetFromUtc(effectiveDateTime, timeZone);
        assertEquals(offset, 0);
    }

    //
    // Take an negative timezone offset and a reference time that is greater than the offset (08:45:33 > 8)
    // => to expect an offset of 0
    //
    @Test(groups = "fast")
    public void testComputeOffset3() {

        final DateTimeZone timeZone = DateTimeZone.forOffsetHours(-8);
        final DateTime effectiveDateTime = DATE_TIME_FORMATTER.parseDateTime(effectiveDateTime3);

        int offset = DateAndTimeZoneContext.computeOffsetFromUtc(effectiveDateTime, timeZone);
        assertEquals(offset, 0);
    }

    //
    // Take an positive timezone offset and a reference time that closer to the end of the day than the timezone (16:30:42 + 8 > 24)
    // => to expect a positive offset of one day
    //
    @Test(groups = "fast")
    public void testComputeOffsetA() {

        final DateTimeZone timeZone = DateTimeZone.forOffsetHours(8);
        final DateTime effectiveDateTime = DATE_TIME_FORMATTER.parseDateTime(effectiveDateTimeA);

        int offset = DateAndTimeZoneContext.computeOffsetFromUtc(effectiveDateTime, timeZone);
        assertEquals(offset, 1);
    }

    //
    // Take an positive timezone offset and a reference time that brings us exactly at the end of the day (16:00:00 + 8 = 24)
    // => to expect an offset of 1
    //
    @Test(groups = "fast")
    public void testComputeOffsetB() {

        final DateTimeZone timeZone = DateTimeZone.forOffsetHours(8);
        final DateTime effectiveDateTime = DATE_TIME_FORMATTER.parseDateTime(effectiveDateTimeB);

        int offset = DateAndTimeZoneContext.computeOffsetFromUtc(effectiveDateTime, timeZone);
        assertEquals(offset, 1);
    }

    //
    // Take an positive timezone offset and a reference time that further away to the end of the day  (15:30:42 + 8 < 24)
    // =>  to expect an offset of 0
    //
    @Test(groups = "fast")
    public void testComputeOffsetC() {

        final DateTimeZone timeZone = DateTimeZone.forOffsetHours(8);
        final DateTime effectiveDateTime = DATE_TIME_FORMATTER.parseDateTime(effectiveDateTimeC);

        int offset = DateAndTimeZoneContext.computeOffsetFromUtc(effectiveDateTime, timeZone);
        assertEquals(offset, 0);
    }

    @Test(groups = "fast")
    public void testComputeUTCDateTimeFromLocalDate1() {

        final DateTime effectiveDateTime = DATE_TIME_FORMATTER.parseDateTime(effectiveDateTime1);

        final DateTimeZone timeZone = DateTimeZone.forOffsetHours(-8);
        final DateAndTimeZoneContext dateContext = new DateAndTimeZoneContext(effectiveDateTime, timeZone, clock);

        final LocalDate endDate = new LocalDate(2013, 01, 19);
        final DateTime endDateTimeInUTC = dateContext.computeUTCDateTimeFromLocalDate(endDate);
        assertTrue(endDateTimeInUTC.compareTo(effectiveDateTime.plusYears(1)) == 0);
    }


    @Test(groups = "fast")
    public void testComputeUTCDateTimeFromLocalDate2() {

        final DateTime effectiveDateTime = DATE_TIME_FORMATTER.parseDateTime(effectiveDateTime2);

        final DateTimeZone timeZone = DateTimeZone.forOffsetHours(-8);
        final DateAndTimeZoneContext dateContext = new DateAndTimeZoneContext(effectiveDateTime, timeZone, clock);

        final LocalDate endDate = new LocalDate(2013, 01, 20);
        final DateTime endDateTimeInUTC = dateContext.computeUTCDateTimeFromLocalDate(endDate);
        assertTrue(endDateTimeInUTC.compareTo(effectiveDateTime.plusYears(1)) == 0);
    }


    @Test(groups = "fast")
    public void testComputeUTCDateTimeFromLocalDate3() {

        final DateTime effectiveDateTime = DATE_TIME_FORMATTER.parseDateTime(effectiveDateTime3);

        final DateTimeZone timeZone = DateTimeZone.forOffsetHours(-8);
        final DateAndTimeZoneContext dateContext = new DateAndTimeZoneContext(effectiveDateTime, timeZone, clock);

        final LocalDate endDate = new LocalDate(2013, 01, 20);
        final DateTime endDateTimeInUTC = dateContext.computeUTCDateTimeFromLocalDate(endDate);
        assertTrue(endDateTimeInUTC.compareTo(effectiveDateTime.plusYears(1)) == 0);
    }

    @Test(groups = "fast")
    public void testComputeUTCDateTimeFromLocalDateA() {

        final DateTime effectiveDateTime = DATE_TIME_FORMATTER.parseDateTime(effectiveDateTimeA);

        final DateTimeZone timeZone = DateTimeZone.forOffsetHours(8);
        final DateAndTimeZoneContext dateContext = new DateAndTimeZoneContext(effectiveDateTime, timeZone, clock);

        final LocalDate endDate = new LocalDate(2013, 01, 21);
        final DateTime endDateTimeInUTC = dateContext.computeUTCDateTimeFromLocalDate(endDate);
        assertTrue(endDateTimeInUTC.compareTo(effectiveDateTime.plusYears(1)) == 0);
    }

    @Test(groups = "fast")
    public void testComputeUTCDateTimeFromLocalDateB() {

        final DateTime effectiveDateTime = DATE_TIME_FORMATTER.parseDateTime(effectiveDateTimeB);

        final DateTimeZone timeZone = DateTimeZone.forOffsetHours(8);
        final DateAndTimeZoneContext dateContext = new DateAndTimeZoneContext(effectiveDateTime, timeZone, clock);

        final LocalDate endDate = new LocalDate(2013, 01, 21);
        final DateTime endDateTimeInUTC = dateContext.computeUTCDateTimeFromLocalDate(endDate);
        assertTrue(endDateTimeInUTC.compareTo(effectiveDateTime.plusYears(1)) == 0);
    }

    @Test(groups = "fast")
    public void testComputeUTCDateTimeFromLocalDateC() {

        final DateTime effectiveDateTime = DATE_TIME_FORMATTER.parseDateTime(effectiveDateTimeC);

        final DateTimeZone timeZone = DateTimeZone.forOffsetHours(8);
        final DateAndTimeZoneContext dateContext = new DateAndTimeZoneContext(effectiveDateTime, timeZone, clock);

        final LocalDate endDate = new LocalDate(2013, 01, 20);
        final DateTime endDateTimeInUTC = dateContext.computeUTCDateTimeFromLocalDate(endDate);
        assertTrue(endDateTimeInUTC.compareTo(effectiveDateTime.plusYears(1)) == 0);
    }
}
