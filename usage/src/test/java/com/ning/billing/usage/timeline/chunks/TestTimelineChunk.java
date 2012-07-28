package com.ning.billing.usage.timeline.chunks;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;

public class TestTimelineChunk {

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
