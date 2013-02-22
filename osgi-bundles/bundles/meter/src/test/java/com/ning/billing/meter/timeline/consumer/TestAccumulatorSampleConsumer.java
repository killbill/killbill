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

package com.ning.billing.meter.timeline.consumer;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.meter.MeterTestSuiteNoDB;
import com.ning.billing.meter.api.TimeAggregationMode;
import com.ning.billing.meter.timeline.samples.SampleOpcode;
import com.ning.billing.util.clock.ClockMock;

public class TestAccumulatorSampleConsumer extends MeterTestSuiteNoDB {

    private final ClockMock clock = new ClockMock();

    @Test(groups = "fast")
    public void testDailyAggregation() throws Exception {
        clock.setTime(new DateTime(2012, 12, 1, 12, 40, DateTimeZone.UTC));
        final DateTime start = clock.getUTCNow();

        final AccumulatorSampleConsumer sampleConsumer = new AccumulatorSampleConsumer(TimeAggregationMode.DAYS, new CSVSampleProcessor());

        // 5 for day 1
        sampleConsumer.processOneSample(start, SampleOpcode.DOUBLE, (double) 1);
        sampleConsumer.processOneSample(start.plusHours(4), SampleOpcode.DOUBLE, (double) 4);
        // 1 for day 2
        sampleConsumer.processOneSample(start.plusDays(1), SampleOpcode.DOUBLE, (double) 1);
        // 10 and 20 for day 3 (with different opcode)
        sampleConsumer.processOneSample(start.plusDays(2), SampleOpcode.DOUBLE, (double) 10);
        sampleConsumer.processOneSample(start.plusDays(2), SampleOpcode.INT, 20);

        Assert.assertEquals(sampleConsumer.flush(), "1354320000,5.0,1354406400,1.0,1354492800,10.0,1354492800,20.0");
    }
}
