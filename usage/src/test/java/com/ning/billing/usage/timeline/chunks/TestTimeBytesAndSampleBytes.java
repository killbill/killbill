package com.ning.billing.usage.timeline.chunks;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestTimeBytesAndSampleBytes {

    @Test(groups = "fast")
    public void testGetters() throws Exception {
        final byte[] timeBytes = new byte[]{0x1, 0x2, 0x3};
        final byte[] sampleBytes = new byte[]{0xA, 0xB, 0xC};
        final TimeBytesAndSampleBytes timeBytesAndSampleBytes = new TimeBytesAndSampleBytes(timeBytes, sampleBytes);

        Assert.assertEquals(timeBytesAndSampleBytes.getTimeBytes(), timeBytes);
        Assert.assertEquals(timeBytesAndSampleBytes.getSampleBytes(), sampleBytes);
    }

    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final byte[] timeBytes = new byte[]{0x1, 0x2, 0x3};
        final byte[] sampleBytes = new byte[]{0xA, 0xB, 0xC};

        final TimeBytesAndSampleBytes timeBytesAndSampleBytes = new TimeBytesAndSampleBytes(timeBytes, sampleBytes);
        Assert.assertEquals(timeBytesAndSampleBytes, timeBytesAndSampleBytes);

        final TimeBytesAndSampleBytes sameTimeBytesAndSampleBytes = new TimeBytesAndSampleBytes(timeBytes, sampleBytes);
        Assert.assertEquals(sameTimeBytesAndSampleBytes, timeBytesAndSampleBytes);

        final TimeBytesAndSampleBytes otherTimeBytesAndSampleBytes = new TimeBytesAndSampleBytes(sampleBytes, timeBytes);
        Assert.assertNotEquals(otherTimeBytesAndSampleBytes, timeBytesAndSampleBytes);
    }
}
