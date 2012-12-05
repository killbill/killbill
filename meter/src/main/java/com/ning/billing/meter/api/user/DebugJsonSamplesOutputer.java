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

package com.ning.billing.meter.api.user;

import java.io.IOException;
import java.util.Collection;

import com.ning.billing.meter.timeline.TimelineEventHandler;
import com.ning.billing.meter.timeline.categories.CategoryRecordIdAndMetric;
import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.chunks.TimelineChunksViews;
import com.ning.billing.meter.timeline.consumer.CSVConsumer;
import com.ning.billing.meter.timeline.consumer.TimelineChunkDecoded;
import com.ning.billing.meter.timeline.metrics.SamplesForMetricAndSource;
import com.ning.billing.meter.timeline.persistent.TimelineDao;
import com.ning.billing.util.callcontext.InternalTenantContext;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Strings;

public class DebugJsonSamplesOutputer extends JsonSamplesOutputer {

    private final boolean withBinaryData;
    private final ObjectWriter writer;

    public DebugJsonSamplesOutputer(final boolean withBinaryData, final TimelineEventHandler timelineEventHandler,
                                    final TimelineDao timelineDao, final InternalTenantContext context) {
        super(timelineEventHandler, timelineDao, context);
        this.withBinaryData = withBinaryData;
        if (withBinaryData) {
            this.writer = objectMapper.writerWithView(TimelineChunksViews.Compact.class);
        } else {
            this.writer = objectMapper.writerWithView(TimelineChunksViews.Loose.class);
        }
    }

    @Override
    protected void writeJsonForChunks(final JsonGenerator generator, final Collection<? extends TimelineChunk> chunksForSourceAndMetric) throws IOException {
        for (final TimelineChunk chunk : chunksForSourceAndMetric) {
            if (withBinaryData) {
                writer.writeValue(generator, new TimelineChunkDecoded(chunk, sampleCoder));
            } else {
                final String source = timelineDao.getSource(chunk.getSourceId(), context);
                final CategoryRecordIdAndMetric categoryIdAndMetric = timelineDao.getCategoryIdAndMetric(chunk.getMetricId(), context);
                final String category = timelineDao.getEventCategory(categoryIdAndMetric.getEventCategoryId(), context);
                final String metric = categoryIdAndMetric.getMetric();
                final String samples = CSVConsumer.getSamplesAsCSV(sampleCoder, chunk);

                // Don't write out empty samples
                if (!Strings.isNullOrEmpty(samples)) {
                    generator.writeObject(new SamplesForMetricAndSource(source, category, metric, samples));
                }
            }
        }
    }
}
