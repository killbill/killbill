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

package com.ning.billing.meter.timeline.consumer.filter;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.skife.config.TimeSpan;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.meter.MeterTestSuiteNoDB;
import com.ning.billing.meter.api.DecimationMode;
import com.ning.billing.meter.timeline.consumer.TimeRangeSampleProcessor;
import com.ning.billing.meter.timeline.samples.SampleOpcode;

public class TestDecimatingFilter extends MeterTestSuiteNoDB {

    @Test(groups = "fast")
    public void testBasicFilterOperations() throws Exception {
        final List<Double> outputs = new ArrayList<Double>();
        final long millisStart = System.currentTimeMillis() - 2000 * 100;

        final DecimatingSampleFilter filter = new DecimatingSampleFilter(new DateTime(millisStart), new DateTime(millisStart + 2000 * 100), 25, 100, new TimeSpan("2s"), DecimationMode.PEAK_PICK,
                                                                         new TimeRangeSampleProcessor() {

                                                                             @Override
                                                                             public void processOneSample(final DateTime time, final SampleOpcode opcode, final Object value) {
                                                                                 outputs.add((Double) value);
                                                                             }
                                                                         });
        for (int i = 0; i < 100; i++) {
            // Make the value go up for 4 samples; then down for 4 samples, between 10.0 and 40.0
            final int index = (i % 8) + 1;
            double value = 0;
            if (index <= 4) {
                value = 10.0 * index;
            } else {
                value = (8 - (index - 1)) * 10;
            }
            //System.out.printf("For i %d, index %d, adding value %f\n", i, index, value);
            filter.processOneSample(new DateTime(millisStart + 2000 * i), SampleOpcode.DOUBLE, value);
        }
        int index = 0;
        for (final Double value : outputs) {
            //System.out.printf("index %d, value %f\n", index++, (double)((Double)value));
            if ((index & 1) == 0) {
                Assert.assertEquals(value, 40.0);
            } else {
                Assert.assertEquals(value, 10.0);
            }
            index++;
        }
    }

    /**
     * This test has sample count of 21, and output count of 6, so there are 5.8 samples per output point
     *
     * @throws Exception
     */
    @Test(groups = "fast")
    public void testFilterWithNonAlignedSampleCounts() throws Exception {
        final List<Double> outputs = new ArrayList<Double>();
        final long millisStart = System.currentTimeMillis() - 2000 * 21;

        final DecimatingSampleFilter filter = new DecimatingSampleFilter(new DateTime(millisStart), new DateTime(millisStart + 2000 * 21), 6, 21, new TimeSpan("2s"), DecimationMode.PEAK_PICK,
                                                                         new TimeRangeSampleProcessor() {

                                                                             @Override
                                                                             public void processOneSample(final DateTime time, final SampleOpcode opcode, final Object value) {
                                                                                 outputs.add((double) ((Double) value));
                                                                             }
                                                                         });
        for (int i = 0; i < 21; i++) {
            // Make the value go up for 6 samples; then down for 6 samples, between 10.0 and 60.0
            final int index = (i % 6) + 1;
            double value = 0;
            if (index <= 3) {
                value = 10.0 * index;
            } else {
                value = (6 - (index - 1)) * 10;
            }
            //System.out.printf("For i %d, index %d, adding value %f\n", i, index, value);
            filter.processOneSample(new DateTime(millisStart + 2000 * i), SampleOpcode.DOUBLE, value);
        }
        Assert.assertEquals(outputs.size(), 5);
        final double[] expectedValues = new double[]{30.0, 20.0, 30.0, 30.0, 10.0};
        for (int i = 0; i < 5; i++) {
            final double value = outputs.get(i);
            final double expectedValue = expectedValues[i];
            //System.out.printf("index %d, value returned %f, value expected %f\n", i, value, expectedValue);
            Assert.assertEquals(value, expectedValue);
        }
    }
}
