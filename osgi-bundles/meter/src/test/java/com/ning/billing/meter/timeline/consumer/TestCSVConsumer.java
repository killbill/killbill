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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.meter.MeterTestSuiteNoDB;
import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.codec.DefaultSampleCoder;
import com.ning.billing.meter.timeline.codec.SampleCoder;
import com.ning.billing.meter.timeline.samples.SampleOpcode;
import com.ning.billing.meter.timeline.samples.ScalarSample;
import com.ning.billing.meter.timeline.times.DefaultTimelineCoder;
import com.ning.billing.meter.timeline.times.TimelineCoder;

public class TestCSVConsumer extends MeterTestSuiteNoDB {

    private static final int HOST_ID = 1242;
    private static final int SAMPLE_KIND_ID = 12;
    private static final int CHUNK_ID = 30;
    private static final TimelineCoder timelineCoder = new DefaultTimelineCoder();
    private static final SampleCoder sampleCoder = new DefaultSampleCoder();

    @Test(groups = "fast")
    public void testToString() throws Exception {
        final int sampleCount = 3;

        final DateTime startTime = new DateTime("2012-01-16T21:23:58.000Z", DateTimeZone.UTC);
        final List<DateTime> dateTimes = new ArrayList<DateTime>();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final DataOutputStream stream = new DataOutputStream(out);

        for (int i = 0; i < sampleCount; i++) {
            sampleCoder.encodeSample(stream, new ScalarSample<Long>(SampleOpcode.LONG, 12345L + i));
            dateTimes.add(startTime.plusSeconds(1 + i));
        }

        final DateTime endTime = dateTimes.get(dateTimes.size() - 1);
        final byte[] times = timelineCoder.compressDateTimes(dateTimes);
        final TimelineChunk timelineChunk = new TimelineChunk(CHUNK_ID, HOST_ID, SAMPLE_KIND_ID, startTime, endTime, times, out.toByteArray(), sampleCount);

        // Test CSV filtering
        Assert.assertEquals(CSVConsumer.getSamplesAsCSV(sampleCoder, timelineChunk), "1326749039,12345,1326749040,12346,1326749041,12347");
        Assert.assertEquals(CSVConsumer.getSamplesAsCSV(sampleCoder, timelineChunk, null, null), "1326749039,12345,1326749040,12346,1326749041,12347");
        Assert.assertEquals(CSVConsumer.getSamplesAsCSV(sampleCoder, timelineChunk, startTime, null), "1326749039,12345,1326749040,12346,1326749041,12347");
        Assert.assertEquals(CSVConsumer.getSamplesAsCSV(sampleCoder, timelineChunk, null, startTime.plusSeconds(sampleCount)), "1326749039,12345,1326749040,12346,1326749041,12347");
        Assert.assertEquals(CSVConsumer.getSamplesAsCSV(sampleCoder, timelineChunk, startTime.plusSeconds(1), startTime.plusSeconds(sampleCount)), "1326749039,12345,1326749040,12346,1326749041,12347");
        Assert.assertEquals(CSVConsumer.getSamplesAsCSV(sampleCoder, timelineChunk, startTime.plusSeconds(2), startTime.plusSeconds(sampleCount)), "1326749040,12346,1326749041,12347");
        Assert.assertEquals(CSVConsumer.getSamplesAsCSV(sampleCoder, timelineChunk, startTime.plusSeconds(3), startTime.plusSeconds(sampleCount)), "1326749041,12347");
        Assert.assertEquals(CSVConsumer.getSamplesAsCSV(sampleCoder, timelineChunk, startTime.plusSeconds(4), startTime.plusSeconds(sampleCount)), "");
        // Buggy start date
        Assert.assertEquals(CSVConsumer.getSamplesAsCSV(sampleCoder, timelineChunk, startTime.plusSeconds(10), startTime.plusSeconds(sampleCount)), "");
        // Buggy end date
        Assert.assertEquals(CSVConsumer.getSamplesAsCSV(sampleCoder, timelineChunk, startTime, startTime.minusSeconds(1)), "");
    }
}
