package com.ning.billing.usage.timeline.samples;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestRepeatSample {

    @Test(groups = "fast")
    public void testGetters() throws Exception {
        final int repeatCount = 5;
        final ScalarSample<Short> scalarSample = new ScalarSample<Short>(SampleOpcode.SHORT, (short) 12);
        final RepeatSample<Short> repeatSample = new RepeatSample<Short>(repeatCount, scalarSample);

        Assert.assertEquals(repeatSample.getRepeatCount(), repeatCount);
        Assert.assertEquals(repeatSample.getSampleRepeated(), scalarSample);
        Assert.assertEquals(repeatSample.getOpcode().name(), SampleOpcode.REPEAT_BYTE.name());
    }

    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final int repeatCount = 5;
        final ScalarSample<Short> scalarSample = new ScalarSample<Short>(SampleOpcode.SHORT, (short) 12);

        final RepeatSample<Short> repeatSample = new RepeatSample<Short>(repeatCount, scalarSample);
        Assert.assertEquals(repeatSample, repeatSample);

        final RepeatSample<Short> sameRepeatSample = new RepeatSample<Short>(repeatCount, scalarSample);
        Assert.assertEquals(sameRepeatSample, repeatSample);

        final RepeatSample<Short> otherRepeatSample = new RepeatSample<Short>(repeatCount + 1, scalarSample);
        Assert.assertNotEquals(otherRepeatSample, repeatSample);
    }
}
