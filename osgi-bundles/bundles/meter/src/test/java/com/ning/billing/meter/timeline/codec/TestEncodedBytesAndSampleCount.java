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

package com.ning.billing.meter.timeline.codec;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.meter.MeterTestSuiteNoDB;

public class TestEncodedBytesAndSampleCount extends MeterTestSuiteNoDB {

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
