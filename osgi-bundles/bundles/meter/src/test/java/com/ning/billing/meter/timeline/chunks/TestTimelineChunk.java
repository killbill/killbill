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

package com.ning.billing.meter.timeline.chunks;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.meter.MeterTestSuiteNoDB;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;

public class TestTimelineChunk extends MeterTestSuiteNoDB {

    private final Clock clock = new ClockMock();

    @Test(groups = "fast")
    public void testGetters() throws Exception {
        final long chunkId = 0L;
        final int sourceId = 1;
        final int metricId = 2;
        final DateTime startTime = clock.getUTCNow();
        final DateTime endTime = startTime.plusDays(2);
        final byte[] timeBytes = new byte[]{0x1, 0x2, 0x3};
        final byte[] sampleBytes = new byte[]{0xA, 0xB, 0xC};
        final TimelineChunk timelineChunk = new TimelineChunk(chunkId, sourceId, metricId, startTime, endTime, timeBytes, sampleBytes, timeBytes.length);

        Assert.assertEquals(timelineChunk.getChunkId(), chunkId);
        Assert.assertEquals(timelineChunk.getSourceId(), sourceId);
        Assert.assertEquals(timelineChunk.getMetricId(), metricId);
        Assert.assertEquals(timelineChunk.getStartTime(), startTime);
        Assert.assertEquals(timelineChunk.getEndTime(), endTime);
        Assert.assertEquals(timelineChunk.getTimeBytesAndSampleBytes().getTimeBytes(), timeBytes);
        Assert.assertEquals(timelineChunk.getTimeBytesAndSampleBytes().getSampleBytes(), sampleBytes);
        Assert.assertEquals(timelineChunk.getAggregationLevel(), 0);
        Assert.assertFalse(timelineChunk.getNotValid());
        Assert.assertFalse(timelineChunk.getDontAggregate());
    }

    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final long chunkId = 0L;
        final int sourceId = 1;
        final int metricId = 2;
        final DateTime startTime = clock.getUTCNow();
        final DateTime endTime = startTime.plusDays(2);
        final byte[] timeBytes = new byte[]{0x1, 0x2, 0x3};
        final byte[] sampleBytes = new byte[]{0xA, 0xB, 0xC};

        final TimelineChunk timelineChunk = new TimelineChunk(chunkId, sourceId, metricId, startTime, endTime, timeBytes, sampleBytes, timeBytes.length);
        Assert.assertEquals(timelineChunk, timelineChunk);

        final TimelineChunk sameTimelineChunk = new TimelineChunk(chunkId, sourceId, metricId, startTime, endTime, timeBytes, sampleBytes, timeBytes.length);
        Assert.assertEquals(sameTimelineChunk, timelineChunk);

        final TimelineChunk otherTimelineChunk = new TimelineChunk(sourceId, sourceId, metricId, startTime, endTime, timeBytes, sampleBytes, timeBytes.length);
        Assert.assertNotEquals(otherTimelineChunk, timelineChunk);
    }
}
