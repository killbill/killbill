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

import java.io.IOException;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.samples.SampleBase;

/**
 * This class represents a sequence of values for a single attribute,
 * e.g., "TP99 Response Time", for one source and one specific time range,
 * as the object is being accumulated.  It is not used to represent
 * past timeline sequences; they are held in TimelineChunk objects.
 * <p/>
 * It accumulates samples in a byte array object. Readers can call
 * getEncodedSamples() at any time to get the latest data.
 */
public class TimelineChunkAccumulator extends SampleAccumulator {

    private static final Logger log = LoggerFactory.getLogger(TimelineChunkAccumulator.class);
    private final int sourceId;
    private final int metricId;

    public TimelineChunkAccumulator(final int sourceId, final int metricId, final SampleCoder sampleCoder) {
        super(sampleCoder);
        this.sourceId = sourceId;
        this.metricId = metricId;
    }

    private TimelineChunkAccumulator(final int sourceId, final int metricId, final byte[] bytes, final SampleBase lastSample, final int sampleCount, final SampleCoder sampleCoder) throws IOException {
        super(bytes, lastSample, sampleCount, sampleCoder);
        this.sourceId = sourceId;
        this.metricId = metricId;
    }

    public TimelineChunkAccumulator deepCopy() throws IOException {
        return new TimelineChunkAccumulator(sourceId, metricId, getByteStream().toByteArray(), getLastSample(), getSampleCount(), sampleCoder);
    }

    /**
     * This method grabs the current encoded form, and resets the accumulator
     */
    public synchronized TimelineChunk extractTimelineChunkAndReset(final DateTime startTime, final DateTime endTime, final byte[] timeBytes) {
        // Extract the chunk
        final byte[] sampleBytes = getEncodedSamples().getEncodedBytes();
        log.debug("Creating TimelineChunk for metricId {}, sampleCount {}", metricId, getSampleCount());
        final TimelineChunk chunk = new TimelineChunk(0, sourceId, metricId, startTime, endTime, timeBytes, sampleBytes, getSampleCount());

        // Reset this current accumulator
        reset();

        return chunk;
    }

    public int getSourceId() {
        return sourceId;
    }

    public int getMetricId() {
        return metricId;
    }
}
