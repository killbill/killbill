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


import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.codec.SampleCoder;
import com.ning.billing.meter.timeline.samples.SampleOpcode;
import com.ning.billing.meter.timeline.times.TimelineCursor;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TimelineChunkDecoded {

    private static final Logger log = LoggerFactory.getLogger(TimelineChunkDecoded.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final TimelineChunk chunk;
    private final SampleCoder sampleCoder;

    public TimelineChunkDecoded(final TimelineChunk chunk, final SampleCoder sampleCoder) {
        this.chunk = chunk;
        this.sampleCoder = sampleCoder;
    }

    @JsonValue
    @Override
    public String toString() {
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final JsonGenerator generator = objectMapper.getJsonFactory().createJsonGenerator(out);
            generator.writeStartObject();

            generator.writeFieldName("metric");
            generator.writeNumber(chunk.getMetricId());

            generator.writeFieldName("decodedSamples");
            generator.writeString(getDecodedSamples());

            generator.writeEndObject();
            generator.close();
            return out.toString();
        } catch (IOException e) {
            log.error("IOException in toString()", e);
        }

        return null;
    }

    private String getDecodedSamples() throws IOException {
        final DecodedSampleOutputProcessor processor = new DecodedSampleOutputProcessor();
        sampleCoder.scan(chunk, processor);
        return processor.getDecodedSamples();
    }

    private static final class DecodedSampleOutputProcessor implements SampleProcessor {

        final StringBuilder builder = new StringBuilder();

        @Override
        public void processSamples(final TimelineCursor timeCursor, final int sampleCount, final SampleOpcode opcode, final Object value) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            final DateTime timestamp = timeCursor.getNextTime();
            builder.append("at ").append(timestamp.toString("yyyy-MM-dd HH:mm:ss")).append(" ");
            if (sampleCount > 1) {
                builder.append(sampleCount).append(" of ");
            }
            builder.append(opcode.name().toLowerCase());
            switch (opcode) {
                case NULL:
                case DOUBLE_ZERO:
                case INT_ZERO:
                    break;
                default:
                    builder.append("(").append(String.valueOf(value)).append(")");
                    break;
            }
        }

        public String getDecodedSamples() {
            return builder.toString();
        }
    }
}
