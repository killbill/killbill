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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

import com.ning.billing.meter.timeline.chunks.TimeBytesAndSampleBytes;
import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.util.Hex;

public class TimesAndSamplesCoder {

    public static int getSizeOfTimeBytes(final byte[] timesAndSamples) {
        final DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(timesAndSamples));
        try {
            return inputStream.readInt();
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Exception reading timeByteCount in TimelineChunkMapper.map() for timesAndSamples %s",
                                                          new String(Hex.encodeHex(timesAndSamples))), e);
        }
    }

    public static int getEncodedLength(final TimelineChunk chunk) {
        return 4 + chunk.getTimeBytesAndSampleBytes().getTimeBytes().length +
                chunk.getTimeBytesAndSampleBytes().getSampleBytes().length;
    }

    public static byte[] getTimeBytes(final byte[] timesAndSamples) {
        final int timeByteCount = getSizeOfTimeBytes(timesAndSamples);
        return Arrays.copyOfRange(timesAndSamples, 4, 4 + timeByteCount);
    }

    public static byte[] getSampleBytes(final byte[] timesAndSamples) {
        final int timeByteCount = getSizeOfTimeBytes(timesAndSamples);
        return Arrays.copyOfRange(timesAndSamples, 4 + timeByteCount, timesAndSamples.length);
    }

    public static TimeBytesAndSampleBytes getTimesBytesAndSampleBytes(final byte[] timesAndSamples) {
        final int timeByteCount = getSizeOfTimeBytes(timesAndSamples);
        final byte[] timeBytes = Arrays.copyOfRange(timesAndSamples, 4, 4 + timeByteCount);
        final byte[] sampleBytes = Arrays.copyOfRange(timesAndSamples, 4 + timeByteCount, timesAndSamples.length);
        return new TimeBytesAndSampleBytes(timeBytes, sampleBytes);
    }

    public static byte[] combineTimesAndSamples(final byte[] times, final byte[] samples) {
        final int totalSamplesSize = 4 + times.length + samples.length;
        final ByteArrayOutputStream baStream = new ByteArrayOutputStream(totalSamplesSize);
        final DataOutputStream outputStream = new DataOutputStream(baStream);
        try {
            outputStream.writeInt(times.length);
            outputStream.write(times);
            outputStream.write(samples);
            outputStream.flush();
            return baStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(String.format("Exception reading timeByteCount in TimelineChunkMapper.map() for times %s, samples %s",
                                                          new String(Hex.encodeHex(times)), new String(Hex.encodeHex(samples))), e);
        }
    }
}
