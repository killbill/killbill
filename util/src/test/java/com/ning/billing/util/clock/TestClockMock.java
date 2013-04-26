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

package com.ning.billing.util.clock;

import java.util.concurrent.Callable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.util.UtilTestSuiteNoDB;

import com.jayway.awaitility.Awaitility;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class TestClockMock extends UtilTestSuiteNoDB {

    @Test(groups = "fast")
    public void testBasicClockOperations() throws Exception {
        final ClockMock clock = new ClockMock();

        final DateTime startingTime = new DateTime(DateTimeZone.UTC);
        // Lame, but required due to the truncation magic
        Awaitility.await().atMost(999, MILLISECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return clock.getUTCNow().isAfter(startingTime);
            }
        });

        clock.setTime(new DateTime(2012, 5, 1, 1, 2, 3, DateTimeZone.UTC));
        Assert.assertEquals(clock.getUTCToday(), new LocalDate(2012, 5, 1));
        final DateTime utcNowAfterSetTime = clock.getUTCNow();
        Assert.assertEquals(utcNowAfterSetTime.getHourOfDay(), 1);
        Assert.assertEquals(utcNowAfterSetTime.getMinuteOfHour(), 2);
        Assert.assertEquals(utcNowAfterSetTime.getSecondOfMinute(), 3);

        clock.addDays(1);
        Assert.assertEquals(clock.getUTCToday(), new LocalDate(2012, 5, 2));

        clock.addMonths(1);
        Assert.assertEquals(clock.getUTCToday(), new LocalDate(2012, 6, 2));

        clock.addYears(1);
        Assert.assertEquals(clock.getUTCToday(), new LocalDate(2013, 6, 2));

        clock.setDay(new LocalDate(2045, 12, 12));
        Assert.assertEquals(clock.getUTCToday(), new LocalDate(2045, 12, 12));

        clock.resetDeltaFromReality();
        Assert.assertTrue(clock.getUTCNow().isAfter(startingTime));
        Assert.assertTrue(clock.getUTCNow().isBefore(startingTime.plusMinutes(1)));
    }
}
