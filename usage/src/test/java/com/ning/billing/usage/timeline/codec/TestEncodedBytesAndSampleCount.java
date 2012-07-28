package com.ning.billing.usage.timeline.codec;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestEncodedBytesAndSampleCount {

    @Test(groups = "fast")
    public void testGetters() throws Exception {
        final byte[] encodedBytes = {0xA, 0xB};
        final int sampleCount = 20;
        final EncodedBytesAndSampleCount encodedBytesAndSampleCount = new EncodedBytesAndSampleCount(encodedBytes, sampleCount);

        Assert.assertEquals(encodedBytesAndSampleCount.getEncodedBytes(), encodedBytes);
        Assert.assertEquals(encodedBytesAndSampleCount.getSampleCount(), sampleCount);
    }

    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final byte[] encodedBytes = {0xA, 0xB};
        final int sampleCount = 20;

        final EncodedBytesAndSampleCount encodedBytesAndSampleCount = new EncodedBytesAndSampleCount(encodedBytes, sampleCount);
        Assert.assertEquals(encodedBytesAndSampleCount, encodedBytesAndSampleCount);

        final EncodedBytesAndSampleCount sameEncodedBytesAndSampleCount = new EncodedBytesAndSampleCount(encodedBytes, sampleCount);
        Assert.assertEquals(sameEncodedBytesAndSampleCount, encodedBytesAndSampleCount);

        final EncodedBytesAndSampleCount otherEncodedBytesAndSampleCount = new EncodedBytesAndSampleCount(encodedBytes, sampleCount + 1);
        Assert.assertNotEquals(otherEncodedBytesAndSampleCount, encodedBytesAndSampleCount);
    }
}
