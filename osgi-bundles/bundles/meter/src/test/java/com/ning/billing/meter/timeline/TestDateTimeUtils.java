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

package com.ning.billing.meter.timeline;

import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.meter.MeterTestSuiteNoDB;
import com.ning.billing.meter.timeline.util.DateTimeUtils;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;

public class TestDateTimeUtils extends MeterTestSuiteNoDB {

    private final Clock clock = new ClockMock();

    @Test(groups = "fast")
    public void testRoundTrip() throws Exception {
        final DateTime utcNow = clock.getUTCNow();
        final int unixSeconds = DateTimeUtils.unixSeconds(utcNow);
        final DateTime dateTimeFromUnixSeconds = DateTimeUtils.dateTimeFromUnixSeconds(unixSeconds);

        Assert.assertEquals(Seconds.secondsBetween(dateTimeFromUnixSeconds, utcNow).getSeconds(), 0);
    }
}
