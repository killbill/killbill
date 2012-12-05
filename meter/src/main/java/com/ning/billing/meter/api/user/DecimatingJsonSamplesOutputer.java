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
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.skife.config.TimeSpan;

import com.ning.billing.meter.api.DecimationMode;
import com.ning.billing.meter.timeline.TimelineEventHandler;
import com.ning.billing.meter.timeline.categories.CategoryRecordIdAndMetric;
import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.consumer.CSVConsumer;
import com.ning.billing.meter.timeline.consumer.CSVSampleProcessor;
import com.ning.billing.meter.timeline.consumer.TimeRangeSampleProcessor;
import com.ning.billing.meter.timeline.consumer.filter.DecimatingSampleFilter;
import com.ning.billing.meter.timeline.metrics.SamplesForMetricAndSource;
import com.ning.billing.meter.timeline.persistent.TimelineDao;
import com.ning.billing.util.callcontext.InternalTenantContext;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.base.Strings;

public class DecimatingJsonSamplesOutputer extends JsonSamplesOutputer {

    private final Integer outputCount;
    private final DecimationMode decimationMode;

    private Map<Integer, Map<Integer, DecimatingSampleFilter>> filters;

    public DecimatingJsonSamplesOutputer(final DecimationMode decimationMode, @Nullable final Integer outputCount,
                                         final TimelineEventHandler timelineEventHandler, final TimelineDao timelineDao, final InternalTenantContext context) {
        super(timelineEventHandler, timelineDao, context);
        this.outputCount = outputCount;
        this.decimationMode = decimationMode;
    }

    @Override
    protected void output(final OutputStream output, final List<Integer> sourceIds, final List<Integer> metricIds, final DateTime startTime, final DateTime endTime) throws IOException {
        // Create the decimating filters
        filters = createDecimatingSampleFilters(sourceIds, metricIds, decimationMode, startTime, endTime, outputCount);

        super.output(output, sourceIds, metricIds, startTime, endTime);
    }

    @Override
    protected void writeJsonForChunks(final JsonGenerator generator, final Collection<? extends TimelineChunk> chunksForSourceAndMetric) throws IOException {
        for (final TimelineChunk chunk : chunksForSourceAndMetric) {
            final String source = timelineDao.getSource(chunk.getSourceId(), context);
            final CategoryRecordIdAndMetric categoryIdAndMetric = timelineDao.getCategoryIdAndMetric(chunk.getMetricId(), context);
            final String eventCategory = timelineDao.getEventCategory(categoryIdAndMetric.getEventCategoryId(), context);
            final String metric = categoryIdAndMetric.getMetric();
            final TimeRangeSampleProcessor filter = filters.get(chunk.getSourceId()).get(chunk.getMetricId());

            final String samples = filter == null ? CSVConsumer.getSamplesAsCSV(sampleCoder, chunk) : CSVConsumer.getSamplesAsCSV(sampleCoder, chunk, filter);

            // Don't write out empty samples
            if (!Strings.isNullOrEmpty(samples)) {
                generator.writeObject(new SamplesForMetricAndSource(source, eventCategory, metric, samples));
            }
        }
    }

    private Map<Integer, Map<Integer, DecimatingSampleFilter>> createDecimatingSampleFilters(final List<Integer> sourceIds, final List<Integer> metricIds, final DecimationMode decimationMode,
                                                                                             final DateTime startTime, final DateTime endTime, final Integer outputCount) {
        final Map<Integer, Map<Integer, DecimatingSampleFilter>> filters = new HashMap<Integer, Map<Integer, DecimatingSampleFilter>>();
        for (final Integer sourceId : sourceIds) {
            filters.put(sourceId, new HashMap<Integer, DecimatingSampleFilter>());
            for (final Integer metric : metricIds) {
                filters.get(sourceId).put(metric, createDecimatingSampleFilter(outputCount, decimationMode, startTime, endTime));
            }
        }
        return filters;
    }

    private DecimatingSampleFilter createDecimatingSampleFilter(final Integer outputCount, final DecimationMode decimationMode, final DateTime startTime, final DateTime endTime) {
        final DecimatingSampleFilter rangeSampleProcessor;
        if (outputCount == null) {
            rangeSampleProcessor = null;
        } else {
            // TODO Fix the polling interval
            rangeSampleProcessor = new DecimatingSampleFilter(startTime, endTime, outputCount, new TimeSpan("1s"), decimationMode, new CSVSampleProcessor());
        }

        return rangeSampleProcessor;
    }
}
