package com.ning.billing.usage.timeline.samples;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestNullSample {

    @Test(groups = "fast")
    public void testConstructor() throws Exception {
        final NullSample sample = new NullSample();

        Assert.assertEquals(sample.getOpcode(), SampleOpcode.NULL);
        Assert.assertNull(sample.getSampleValue());
    }
}
