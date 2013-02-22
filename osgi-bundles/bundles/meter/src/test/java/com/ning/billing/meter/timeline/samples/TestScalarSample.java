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

public class TestScalarSample extends MeterTestSuiteNoDB {

    @Test(groups = "fast")
    public void testGetters() throws Exception {
        final SampleOpcode opcode = SampleOpcode.SHORT;
        final short value = (short) 5;
        final ScalarSample<Short> scalarSample = new ScalarSample<Short>(opcode, value);

        Assert.assertEquals(scalarSample.getOpcode(), opcode);
        Assert.assertEquals((short) scalarSample.getSampleValue(), value);
    }

    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final SampleOpcode opcode = SampleOpcode.SHORT;
        final short value = (short) 5;

        final ScalarSample<Short> scalarSample = new ScalarSample<Short>(opcode, value);
        Assert.assertEquals(scalarSample, scalarSample);

        final ScalarSample<Short> sameScalarSample = new ScalarSample<Short>(opcode, value);
        Assert.assertEquals(sameScalarSample, scalarSample);

        final ScalarSample<Short> otherScalarSample = new ScalarSample<Short>(opcode, (short) (value + 1));
        Assert.assertNotEquals(otherScalarSample, scalarSample);
    }

    @Test(groups = "fast")
    public void testFromObject() throws Exception {
        verifyFromObject(null, 0.0, null, SampleOpcode.NULL);

        verifyFromObject((byte) 1, (double) 1, (byte) 1, SampleOpcode.BYTE);

        verifyFromObject((short) 128, (double) 128, (short) 128, SampleOpcode.SHORT);
        verifyFromObject(32767, (double) 32767, (short) 32767, SampleOpcode.SHORT);

        verifyFromObject(32768, (double) 32768, 32768, SampleOpcode.INT);
        verifyFromObject((long) 32767, (double) 32767, (short) 32767, SampleOpcode.SHORT);
        verifyFromObject((long) 32768, (double) 32768, 32768, SampleOpcode.INT);

        verifyFromObject(2147483648L, (double) 2147483648L, 2147483648L, SampleOpcode.LONG);

        verifyFromObject((float) 1, 1, (float) 1, SampleOpcode.FLOAT);

        verifyFromObject(12.24, 12.24, 12.24, SampleOpcode.DOUBLE);
    }

    private void verifyFromObject(final Object value, final double expectedDoubleValue, final Object expectedSampleValue, final SampleOpcode expectedSampleOpcode) {
        final ScalarSample scalarSample = ScalarSample.fromObject(value);
        Assert.assertEquals(scalarSample.getOpcode(), expectedSampleOpcode);
        Assert.assertEquals(scalarSample.getSampleValue(), expectedSampleValue);
        Assert.assertEquals(scalarSample.getDoubleValue(), expectedDoubleValue);
    }
}
