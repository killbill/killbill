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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.meter.MeterTestSuiteNoDB;
import com.ning.billing.meter.timeline.consumer.TimeRangeSampleProcessor;
import com.ning.billing.meter.timeline.samples.RepeatSample;
import com.ning.billing.meter.timeline.samples.SampleOpcode;
import com.ning.billing.meter.timeline.samples.ScalarSample;
import com.ning.billing.meter.timeline.times.DefaultTimelineCoder;
import com.ning.billing.meter.timeline.times.DefaultTimelineCursor;
import com.ning.billing.meter.timeline.times.TimelineCoder;
import com.ning.billing.meter.timeline.times.TimelineCursor;
import com.ning.billing.meter.timeline.util.DateTimeUtils;
import com.ning.billing.meter.timeline.util.Hex;

import com.google.common.collect.ImmutableList;

public class TestSampleCoder extends MeterTestSuiteNoDB {

    private static final TimelineCoder timelineCoder = new DefaultTimelineCoder();
    private static final DateTimeFormatter dateFormatter = ISODateTimeFormat.dateTime().withZone(DateTimeZone.UTC);
    private static final SampleCoder sampleCoder = new DefaultSampleCoder();

    @Test(groups = "fast")
    public void testScan() throws Exception {
        final DateTime startTime = new DateTime(DateTimeZone.UTC);
        final DateTime endTime = startTime.plusSeconds(5);
        final List<DateTime> dateTimes = ImmutableList.<DateTime>of(startTime.plusSeconds(1), startTime.plusSeconds(2), startTime.plusSeconds(3), startTime.plusSeconds(4));
        final byte[] compressedTimes = timelineCoder.compressDateTimes(dateTimes);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
        final ScalarSample<Short> sample = new ScalarSample<Short>(SampleOpcode.SHORT, (short) 4);
        sampleCoder.encodeSample(dataOutputStream, sample);
        sampleCoder.encodeSample(dataOutputStream, new RepeatSample<Short>(3, sample));
        dataOutputStream.close();

        sampleCoder.scan(outputStream.toByteArray(), compressedTimes, dateTimes.size(), new TimeRangeSampleProcessor(startTime, endTime) {
            @Override
            public void processOneSample(final DateTime time, final SampleOpcode opcode, final Object value) {
                Assert.assertTrue(time.isAfter(startTime));
                Assert.assertTrue(time.isBefore(endTime));
                Assert.assertEquals(Short.valueOf(value.toString()), sample.getSampleValue());
            }
        });
    }

    @Test(groups = "fast")
    public void testTimeRangeSampleProcessor() throws Exception {
        final DateTime startTime = new DateTime(dateFormatter.parseDateTime("2012-03-23T17:35:11.000Z"));
        final DateTime endTime = new DateTime(dateFormatter.parseDateTime("2012-03-23T17:35:17.000Z"));
        final int sampleCount = 2;

        final List<DateTime> dateTimes = ImmutableList.<DateTime>of(startTime, endTime);
        final byte[] compressedTimes = timelineCoder.compressDateTimes(dateTimes);
        final TimelineCursor cursor = new DefaultTimelineCursor(compressedTimes, sampleCount);
        Assert.assertEquals(cursor.getNextTime(), startTime);
        Assert.assertEquals(cursor.getNextTime(), endTime);

        // 2 x the value 12: REPEAT_BYTE, SHORT, 2, SHORT, 12 (2 bytes)
        final byte[] samples = new byte[]{(byte) 0xff, 2, 2, 0, 12};

        final AtomicInteger samplesCount = new AtomicInteger(0);
        sampleCoder.scan(samples, compressedTimes, sampleCount, new TimeRangeSampleProcessor(startTime, endTime) {
            @Override
            public void processOneSample(final DateTime time, final SampleOpcode opcode, final Object value) {
                if (samplesCount.get() == 0) {
                    Assert.assertEquals(DateTimeUtils.unixSeconds(time), DateTimeUtils.unixSeconds(startTime));
                } else {
                    Assert.assertEquals(DateTimeUtils.unixSeconds(time), DateTimeUtils.unixSeconds(endTime));
                }
                samplesCount.incrementAndGet();
            }
        });
        Assert.assertEquals(samplesCount.get(), sampleCount);
    }

    @SuppressWarnings("unchecked")
    @Test(groups = "fast")
    public void testCombineSampleBytes() throws Exception {
        final ScalarSample[] samplesToChoose = new ScalarSample[]{new ScalarSample(SampleOpcode.DOUBLE, 2.0),
                                                                  new ScalarSample(SampleOpcode.DOUBLE, 1.0),
                                                                  new ScalarSample(SampleOpcode.INT_ZERO, 0)};
        final int[] repetitions = new int[]{1, 2, 3, 4, 5, 240, 250, 300};
        final Random rand = new Random(0);
        int count = 0;
        final TimelineChunkAccumulator accum = new TimelineChunkAccumulator(0, 0, sampleCoder);
        final List<ScalarSample> samples = new ArrayList<ScalarSample>();
        for (int i = 0; i < 20; i++) {
            final ScalarSample sample = samplesToChoose[rand.nextInt(samplesToChoose.length)];
            final int repetition = repetitions[rand.nextInt(repetitions.length)];
            for (int r = 0; r < repetition; r++) {
                samples.add(sample);
                accum.addSample(sample);
                count++;
            }
        }
        final byte[] sampleBytes = sampleCoder.compressSamples(samples);
        final byte[] accumBytes = accum.getEncodedSamples().getEncodedBytes();
        Assert.assertEquals(accumBytes, sampleBytes);
        final List<ScalarSample> restoredSamples = sampleCoder.decompressSamples(sampleBytes);
        Assert.assertEquals(restoredSamples.size(), samples.size());
        for (int i = 0; i < count; i++) {
            Assert.assertEquals(restoredSamples.get(i), samples.get(i));
        }
        for (int fragmentLength = 2; fragmentLength < count / 2; fragmentLength++) {
            final List<byte[]> fragments = new ArrayList<byte[]>();
            final int fragmentCount = (int) Math.ceil((double) count / (double) fragmentLength);
            for (int fragCounter = 0; fragCounter < fragmentCount; fragCounter++) {
                final int fragIndex = fragCounter * fragmentLength;
                final List<ScalarSample> fragment = samples.subList(fragIndex, Math.min(count, fragIndex + fragmentLength));
                fragments.add(sampleCoder.compressSamples(fragment));
            }
            final byte[] combined = sampleCoder.combineSampleBytes(fragments);
            final List<ScalarSample> restored = sampleCoder.decompressSamples(combined);
            Assert.assertEquals(restored.size(), samples.size());
            for (int i = 0; i < count; i++) {
                Assert.assertEquals(restored.get(i), samples.get(i));
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test(groups = "fast")
    public void testCombineMoreThan65KSamples() throws Exception {
        final int count = 0;
        final TimelineChunkAccumulator accum = new TimelineChunkAccumulator(0, 0, sampleCoder);
        final List<ScalarSample> samples = new ArrayList<ScalarSample>();
        final ScalarSample sample1 = new ScalarSample(SampleOpcode.BYTE, (byte) 1);
        final ScalarSample sample2 = new ScalarSample(SampleOpcode.BYTE, (byte) 2);
        for (int i = 0; i < 20; i++) {
            samples.add(sample1);
            accum.addSample(sample1);
        }
        for (int i = 0; i < 0xFFFF + 100; i++) {
            samples.add(sample2);
            accum.addSample(sample2);
        }
        final byte[] sampleBytes = sampleCoder.compressSamples(samples);
        final String hex = new String(Hex.encodeHex(sampleBytes));
        // Here are the compressed samples: ff140101feffff0102ff640102
        // Translation:
        // [ff 14 01 01] means repeat 20 times BYTE value 1
        // [fe ff ff 01 02] means repeat 65525 times BYTE value 2
        // [ff 64 01 02] means repeat 100 times BYTE value 2
        Assert.assertEquals(sampleBytes, Hex.decodeHex("ff140101feffff0102ff640102".toCharArray()));
        final List<ScalarSample> restoredSamples = sampleCoder.decompressSamples(sampleBytes);
        Assert.assertEquals(restoredSamples.size(), samples.size());
        for (int i = 0; i < count; i++) {
            Assert.assertEquals(restoredSamples.get(i), samples.get(i));
        }
    }

    /*
     * I saw an error in combineSampleBytes:
     * java.lang.ClassCastException: java.lang.Double cannot be cast to java.lang.Short
     * These were the inputs:
     * [11, 44, 74, -1, 2, 15, 11, 40, 68, -1, 2, 15]
     * meaning half-float-for-double; repeat 2 times double zero; half-float-for-double; repeat 2 time double zero
     * [11, 44, 68, -1, 3, 15, 11, 40, 68]
     * meaning meaning half-float-for-double; repeat 3 times double zero; half-float-for-double
     * [-1, 3, 15, 11, 40, 68, -1, 2, 15, 11, 40, 68]
     * meaning repeat 3 times double-zero; half-float-for-double; repeat 2 times double zero; half-float-for-double
     * [-1, 2, 11, 40, 68, -1, 3, 15, 11, 40, 68, 15]
     * meaning repeat 2 times half-float-for-double; repeat 3 times double-zero; half-float-for-double; double zero
     */
    @SuppressWarnings("unchecked")
    @Test(groups = "fast")
    public void testCombineError() throws Exception {
        final byte[] b1 = new byte[]{11, 44, 74, -1, 2, 15, 11, 40, 68, -1, 2, 15};
        final byte[] b2 = new byte[]{11, 44, 68, -1, 3, 15, 11, 40, 68};
        final byte[] b3 = new byte[]{-1, 3, 15, 11, 40, 68, -1, 2, 15, 11, 40, 68};
        final byte[] b4 = new byte[]{-1, 2, 11, 40, 68, -1, 3, 15, 11, 40, 68, 15};
        final List<byte[]> parts = new ArrayList<byte[]>();
        parts.add(b1);
        parts.add(b2);
        parts.add(b3);
        parts.add(b4);
        final byte[] combinedBytes = sampleCoder.combineSampleBytes(parts);
        final List<ScalarSample> samples = sampleCoder.decompressSamples(combinedBytes);
        Assert.assertEquals(samples.size(), 25);
    }
}
