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

package com.ning.billing.meter.timeline.consumer;

import java.io.IOException;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.codec.SampleCoder;

public class CSVConsumer {

    private CSVConsumer() {}

    public static String getSamplesAsCSV(final SampleCoder sampleCoder, final TimelineChunk chunk) throws IOException {
        return getSamplesAsCSV(sampleCoder, chunk, null, null);
    }

    public static String getSamplesAsCSV(final SampleCoder sampleCoder, final TimelineChunk chunk, @Nullable final DateTime startTime, @Nullable final DateTime endTime) throws IOException {
        final CSVSampleProcessor processor = new CSVSampleProcessor(startTime, endTime);
        return getSamplesAsCSV(sampleCoder, chunk, processor);
    }

    public static String getSamplesAsCSV(final SampleCoder sampleCoder, final TimelineChunk chunk, final TimeRangeSampleProcessor rangeSampleProcessor) throws IOException {
        sampleCoder.scan(chunk, rangeSampleProcessor);
        return rangeSampleProcessor.toString();
    }
}
