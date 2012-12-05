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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.ning.billing.meter.timeline.TimelineEventHandler;
import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.codec.DefaultSampleCoder;
import com.ning.billing.meter.timeline.codec.SampleCoder;
import com.ning.billing.meter.timeline.consumer.TimelineChunkConsumer;
import com.ning.billing.meter.timeline.persistent.TimelineDao;
import com.ning.billing.util.callcontext.InternalTenantContext;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class JsonSamplesOutputer {

    protected static final ObjectMapper objectMapper = new ObjectMapper();

    protected final TimelineEventHandler timelineEventHandler;
    protected final TimelineDao timelineDao;
    protected final SampleCoder sampleCoder;
    protected final InternalTenantContext context;

    public JsonSamplesOutputer(final TimelineEventHandler timelineEventHandler, final TimelineDao timelineDao, final InternalTenantContext context) {
        this.timelineEventHandler = timelineEventHandler;
        this.timelineDao = timelineDao;
        this.sampleCoder = new DefaultSampleCoder();
        this.context = context;
    }

    protected abstract void writeJsonForChunks(final JsonGenerator generator, final Collection<? extends TimelineChunk> chunksForSourceAndMetric) throws IOException;

    public void output(final OutputStream output, final List<String> sources, final Map<String, Collection<String>> metricsPerCategory,
                       final DateTime startTime, final DateTime endTime) throws IOException {
        // Retrieve the source and metric ids
        final List<Integer> sourceIds = translateSourcesToSourceIds(sources);
        final List<Integer> metricIds = translateCategoriesAndMetricNamesToMetricIds(metricsPerCategory);
        output(output, sourceIds, metricIds, startTime, endTime);
    }

    protected void output(final OutputStream output, final List<Integer> sourceIds, final List<Integer> metricIds,
                          final DateTime startTime, final DateTime endTime) throws IOException {
        // Setup Jackson
        final JsonGenerator generator = objectMapper.getJsonFactory().createJsonGenerator(output);

        generator.writeStartArray();

        // First, return all data stored in the database
        writeJsonForStoredChunks(generator, sourceIds, metricIds, startTime, endTime);

        // Now return all data in memory
        writeJsonForInMemoryChunks(generator, sourceIds, metricIds, startTime, endTime);

        // Allow implementers to flush their buffers
        writeRemainingData(generator);

        generator.writeEndArray();

        generator.flush();
        generator.close();
    }

    protected void writeRemainingData(final JsonGenerator generator) throws IOException {
        // No-op
    }

    private List<Integer> translateSourcesToSourceIds(final List<String> sources) {
        final List<Integer> hostIds = new ArrayList<Integer>(sources.size());
        for (final String source : sources) {
            final Integer sourceId = timelineDao.getSourceId(source, context);
            if (sourceId == null) {
                // Ignore
                continue;
            }

            hostIds.add(sourceId);
        }

        return hostIds;
    }

    private List<Integer> translateCategoriesAndMetricNamesToMetricIds(final Map<String, Collection<String>> metricsPerCategory) {
        final List<Integer> metricIds = new ArrayList<Integer>(metricsPerCategory.keySet().size());
        for (final String category : metricsPerCategory.keySet()) {
            final Integer categoryId = timelineDao.getEventCategoryId(category, context);
            if (categoryId == null) {
                // Ignore
                continue;
            }

            for (final String metricName : metricsPerCategory.get(category)) {
                final Integer sampleKindId = timelineDao.getMetricId(categoryId, metricName, context);
                if (sampleKindId == null) {
                    // Ignore
                    continue;
                }

                metricIds.add(sampleKindId);
            }
        }

        return metricIds;
    }

    private void writeJsonForStoredChunks(final JsonGenerator generator, final List<Integer> hostIdsList, final List<Integer> sampleKindIdsList,
                                          final DateTime startTime, final DateTime endTime) throws IOException {
        final AtomicReference<Integer> lastHostId = new AtomicReference<Integer>(null);
        final AtomicReference<Integer> lastSampleKindId = new AtomicReference<Integer>(null);
        final List<TimelineChunk> chunksForHostAndSampleKind = new ArrayList<TimelineChunk>();

        timelineDao.getSamplesBySourceIdsAndMetricIds(hostIdsList, sampleKindIdsList, startTime, endTime, new TimelineChunkConsumer() {
            @Override
            public void processTimelineChunk(final TimelineChunk chunks) {
                final Integer previousHostId = lastHostId.get();
                final Integer previousSampleKindId = lastSampleKindId.get();
                final Integer currentHostId = chunks.getSourceId();
                final Integer currentSampleKindId = chunks.getMetricId();

                chunksForHostAndSampleKind.add(chunks);
                if (previousHostId != null && (!previousHostId.equals(currentHostId) || !previousSampleKindId.equals(currentSampleKindId))) {
                    try {
                        writeJsonForChunks(generator, chunksForHostAndSampleKind);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    chunksForHostAndSampleKind.clear();
                }

                lastHostId.set(currentHostId);
                lastSampleKindId.set(currentSampleKindId);
            }
        }, context);

        if (chunksForHostAndSampleKind.size() > 0) {
            writeJsonForChunks(generator, chunksForHostAndSampleKind);
            chunksForHostAndSampleKind.clear();
        }
    }

    private void writeJsonForInMemoryChunks(final JsonGenerator generator, final List<Integer> hostIdsList, final List<Integer> sampleKindIdsList,
                                            @Nullable final DateTime startTime, @Nullable final DateTime endTime) throws IOException {

        for (final Integer hostId : hostIdsList) {
            final Collection<? extends TimelineChunk> inMemorySamples;
            try {
                inMemorySamples = timelineEventHandler.getInMemoryTimelineChunks(hostId, sampleKindIdsList, startTime, endTime, context);
            } catch (ExecutionException e) {
                throw new IOException(e);
            }
            writeJsonForChunks(generator, inMemorySamples);
        }
    }
}
