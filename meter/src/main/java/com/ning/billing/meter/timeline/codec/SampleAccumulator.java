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
import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.meter.timeline.samples.NullSample;
import com.ning.billing.meter.timeline.samples.RepeatSample;
import com.ning.billing.meter.timeline.samples.SampleBase;
import com.ning.billing.meter.timeline.samples.SampleOpcode;
import com.ning.billing.meter.timeline.samples.ScalarSample;

/**
 * Accumulator of samples. Samples are compressed using a SampleCoder.
 */
public class SampleAccumulator {

    private static final Logger log = LoggerFactory.getLogger(SampleAccumulator.class);
    private static final int DEFAULT_CHUNK_BYTE_ARRAY_SIZE = 100;

    private ByteArrayOutputStream byteStream;
    private DataOutputStream outputStream;
    private int sampleCount;
    private SampleBase lastSample;
    protected final SampleCoder sampleCoder;

    public SampleAccumulator(final SampleCoder sampleCoder) {
        this.sampleCoder = sampleCoder;
        reset();
    }

    public SampleAccumulator(final byte[] bytes, final SampleBase lastSample, final int sampleCount, final SampleCoder sampleCoder) throws IOException {
        reset();
        this.byteStream.write(bytes);
        this.lastSample = lastSample;
        this.sampleCount = sampleCount;
        this.sampleCoder = sampleCoder;
    }

    public void addSampleList(final List<ScalarSample> samples) {
        for (final ScalarSample sample : samples) {
            addSample(sample);
        }
    }

    public synchronized void addSample(final ScalarSample sample) {
        if (lastSample == null) {
            lastSample = sample;
        } else {
            final SampleOpcode lastOpcode = lastSample.getOpcode();
            final SampleOpcode sampleOpcode = sample.getOpcode();
            if (lastSample instanceof RepeatSample) {
                final RepeatSample repeatSample = (RepeatSample) lastSample;
                final ScalarSample sampleRepeated = repeatSample.getSampleRepeated();
                if (sampleRepeated.getOpcode() == sampleOpcode &&
                        (sampleOpcode.getNoArgs() || ScalarSample.sameSampleValues(sampleRepeated.getSampleValue(), sample.getSampleValue())) &&
                        repeatSample.getRepeatCount() < RepeatSample.MAX_SHORT_REPEAT_COUNT) {
                    // We can just increment the count in the repeat instance
                    repeatSample.incrementRepeatCount();
                } else {
                    // A non-matching repeat - just add it
                    addLastSample();
                    lastSample = sample;
                }
            } else {
                final ScalarSample lastScalarSample = (ScalarSample) lastSample;
                if (sampleOpcode == lastOpcode &&
                        (sampleOpcode.getNoArgs() || ScalarSample.sameSampleValues(sample.getSampleValue(), lastScalarSample.getSampleValue()))) {
                    // Replace lastSample with repeat group
                    lastSample = new RepeatSample(2, lastScalarSample);
                } else {
                    addLastSample();
                    lastSample = sample;
                }
            }
        }
        // In all cases, we got 1 more sample
        sampleCount++;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    protected ByteArrayOutputStream getByteStream() {
        return byteStream;
    }

    protected SampleBase getLastSample() {
        return lastSample;
    }

    /**
     * The log scanner can safely call this method, and know that the byte
     * array will always end in a complete sample
     *
     * @return an instance containing the bytes and the counts of samples
     */
    public synchronized EncodedBytesAndSampleCount getEncodedSamples() {
        if (lastSample != null) {
            sampleCoder.encodeSample(outputStream, lastSample);
            lastSample = null;
        }
        try {
            outputStream.flush();
            return new EncodedBytesAndSampleCount(byteStream.toByteArray(), sampleCount);
        } catch (IOException e) {
            log.error("In getEncodedSamples, IOException flushing outputStream", e);
            // Do no harm - - this at least won't corrupt the encoding
            return new EncodedBytesAndSampleCount(new byte[0], 0);
        }
    }

    private synchronized void addLastSample() {
        if (lastSample != null) {
            sampleCoder.encodeSample(outputStream, lastSample);
            lastSample = null;
        }
    }

    public synchronized void reset() {
        byteStream = new ByteArrayOutputStream(DEFAULT_CHUNK_BYTE_ARRAY_SIZE);
        outputStream = new DataOutputStream(byteStream);
        lastSample = null;
        sampleCount = 0;
    }

    public synchronized void addPlaceholder(final int repeatCount) {
        if (repeatCount > 0) {
            addLastSample();
            lastSample = new RepeatSample<Void>(repeatCount, new NullSample());
            sampleCount += repeatCount;
        }
    }
}
