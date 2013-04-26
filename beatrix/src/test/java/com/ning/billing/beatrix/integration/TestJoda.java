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

package com.ning.billing.beatrix.integration;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.Months;
import org.joda.time.MutablePeriod;
import org.joda.time.Period;
import org.joda.time.ReadablePeriod;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.testng.annotations.Test;

import com.ning.billing.util.clock.ClockMock;

public class TestJoda {

    protected final DateTimeFormatter DATE_TIME_FORMATTER = ISODateTimeFormat.dateTimeParser();
    @Test
    public void test() throws Exception  {
        String dateInput = new String("2012-05-01T23:17:41.000Z");

        DateTime inputDate = DATE_TIME_FORMATTER.parseDateTime(dateInput);

        System.out.println("(1) inputDate = " + inputDate + ", inputDate.plusMonths(1) = " + inputDate.plusMonths(1));

        System.out.println("(2) inputDate = " + inputDate + ", inputDate.plusMonths(1) = " + inputDate.plus(Months.months(1)));


        final DateTime now  = new DateTime(DateTimeZone.UTC);
        MutablePeriod delta = new MutablePeriod(now, inputDate);

        final DateTime inputDateWithDelta = now.plus(delta);

        ReadablePeriod period = Months.months(1);
        delta.add(period);

        System.out.println("(3) inputDate = " + inputDateWithDelta + ", inputDate.plusMonths(1) = " + inputDateWithDelta.plusMonths(1));
        System.out.println("(4) inputDate = " + inputDateWithDelta + ", inputDate.plusMonths(1) = " + new DateTime(DateTimeZone.UTC).plus(delta));


        period = Months.months(1);
        delta.add(period);

        System.out.println("(5) inputDate = " + inputDateWithDelta + ", inputDate.plusMonths(1) = " + new DateTime(DateTimeZone.UTC).plus(delta));

        Thread.sleep(1000);
    }

    @Test
    public void test2() throws Exception {

        final LocalDate today = new LocalDate(2012, 4, 1);
        ClockMock clock = new ClockMock();
        //clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        System.out.println("(a) inputDate = " + clock.getUTCNow());

        clock.addDays(30);

        System.out.println("(b) inputDate = " + clock.getUTCNow());

        clock.addMonths(1);
        System.out.println("(c) inputDate = " + clock.getUTCNow());

        clock.addMonths(1);
        System.out.println("(d) inputDate = " + clock.getUTCNow());

        System.out.flush();;

        Thread.sleep(1000);

    }

}
