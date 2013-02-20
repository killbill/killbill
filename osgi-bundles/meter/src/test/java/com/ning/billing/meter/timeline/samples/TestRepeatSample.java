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

package com.ning.billing.meter.timeline.samples;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.meter.MeterTestSuiteNoDB;

public class TestRepeatSample extends MeterTestSuiteNoDB {

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
