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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.skife.config.TimeSpan;

import com.ning.billing.meter.timeline.TimelineEventHandler;
import com.ning.billing.meter.timeline.categories.CategoryRecordIdAndMetric;
import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.chunks.TimelineChunksViews;
import com.ning.billing.meter.timeline.codec.DefaultSampleCoder;
import com.ning.billing.meter.timeline.codec.SampleCoder;
import com.ning.billing.meter.timeline.codec.TimelineChunkDecoded;
import com.ning.billing.meter.timeline.consumer.CSVConsumer;
import com.ning.billing.meter.timeline.consumer.CSVSampleConsumer;
import com.ning.billing.meter.timeline.consumer.TimelineChunkConsumer;
import com.ning.billing.meter.timeline.filter.DecimatingSampleFilter;
import com.ning.billing.meter.timeline.filter.DecimationMode;
import com.ning.billing.meter.timeline.metrics.SamplesForMetricAndSource;
import com.ning.billing.meter.timeline.persistent.TimelineDao;
import com.ning.billing.util.callcontext.InternalTenantContext;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Strings;

public class JsonSamplesOutputer {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final TimelineEventHandler timelineEventHandler;
    private final TimelineDao timelineDao;
    private final SampleCoder sampleCoder;
    private final InternalTenantContext context;

    public JsonSamplesOutputer(final TimelineEventHandler timelineEventHandler, final TimelineDao timelineDao, final InternalTenantContext context) {
        this.timelineEventHandler = timelineEventHandler;
        this.timelineDao = timelineDao;
        this.sampleCoder = new DefaultSampleCoder();
        this.context = context;
    }

    public void output(final OutputStream output, final List<UUID> bundleIds, final Map<String, Collection<String>> metricsPerCategory,
                       final DateTime startTime, final DateTime endTime) throws IOException {
        // Default - output all data points as CSV
        output(output, bundleIds, metricsPerCategory, DecimationMode.PEAK_PICK, null, false, false, startTime, endTime);
    }

    public void output(final OutputStream output, final List<UUID> bundleIds, final Map<String, Collection<String>> metricsPerCategory,
                       final DecimationMode decimationMode, @Nullable final Integer outputCount, final boolean decodeSamples, final boolean compact,
                       final DateTime startTime, final DateTime endTime) throws IOException {
        // Retrieve the source and metric ids
        final List<Integer> sourceIds = translateBundleIdsToSourceIds(bundleIds);
        final List<Integer> metricIds = translateCategoriesAndMetricNamesToMetricIds(metricsPerCategory);

        // Create the decimating filters, if needed
        final Map<Integer, Map<Integer, DecimatingSampleFilter>> filters = createDecimatingSampleFilters(sourceIds, metricIds, decimationMode, startTime, endTime, outputCount);

        // Setup Jackson
        final ObjectWriter writer;
        if (compact) {
            writer = objectMapper.writerWithView(TimelineChunksViews.Compact.class);
        } else {
            writer = objectMapper.writerWithView(TimelineChunksViews.Loose.class);
        }
        final JsonGenerator generator = objectMapper.getJsonFactory().createJsonGenerator(output);

        generator.writeStartArray();

        // First, return all data stored in the database
        writeJsonForStoredChunks(generator, writer, filters, sourceIds, metricIds, startTime, endTime, decodeSamples);

        // Now return all data in memory
        writeJsonForInMemoryChunks(generator, writer, filters, sourceIds, metricIds, startTime, endTime, decodeSamples);

        generator.writeEndArray();

        generator.flush();
        generator.close();
    }

    private List<Integer> translateBundleIdsToSourceIds(final List<UUID> bundleIds) {
        final List<Integer> hostIds = new ArrayList<Integer>(bundleIds.size());
        for (final UUID bundleId : bundleIds) {
            hostIds.add(timelineDao.getSourceId(bundleId.toString(), context));
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

    private Map<Integer, Map<Integer, DecimatingSampleFilter>> createDecimatingSampleFilters(final List<Integer> hostIds, final List<Integer> sampleKindIds, final DecimationMode decimationMode,
                                                                                             final DateTime startTime, final DateTime endTime, final Integer outputCount) {
        final Map<Integer, Map<Integer, DecimatingSampleFilter>> filters = new HashMap<Integer, Map<Integer, DecimatingSampleFilter>>();
        for (final Integer hostId : hostIds) {
            filters.put(hostId, new HashMap<Integer, DecimatingSampleFilter>());
            for (final Integer sampleKindId : sampleKindIds) {
                filters.get(hostId).put(sampleKindId, createDecimatingSampleFilter(outputCount, decimationMode, startTime, endTime));
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
            rangeSampleProcessor = new DecimatingSampleFilter(startTime, endTime, outputCount, new TimeSpan("1s"), decimationMode, new CSVSampleConsumer());
        }

        return rangeSampleProcessor;
    }

    private void writeJsonForStoredChunks(final JsonGenerator generator, final ObjectWriter writer, final Map<Integer, Map<Integer, DecimatingSampleFilter>> filters, final List<Integer> hostIdsList,
                                          final List<Integer> sampleKindIdsList, final DateTime startTime, final DateTime endTime, final boolean decodeSamples) throws IOException {
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
                        writeJsonForChunks(generator, writer, filters, chunksForHostAndSampleKind, decodeSamples);
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
            writeJsonForChunks(generator, writer, filters, chunksForHostAndSampleKind, decodeSamples);
            chunksForHostAndSampleKind.clear();
        }
    }

    private void writeJsonForInMemoryChunks(final JsonGenerator generator, final ObjectWriter writer, final Map<Integer, Map<Integer, DecimatingSampleFilter>> filters, final List<Integer> hostIdsList,
                                            final List<Integer> sampleKindIdsList, @Nullable final DateTime startTime, @Nullable final DateTime endTime, final boolean decodeSamples) throws IOException {

        for (final Integer hostId : hostIdsList) {
            final Collection<? extends TimelineChunk> inMemorySamples;
            try {
                inMemorySamples = timelineEventHandler.getInMemoryTimelineChunks(hostId, sampleKindIdsList, startTime, endTime, context);
            } catch (ExecutionException e) {
                throw new IOException(e);
            }
            writeJsonForChunks(generator, writer, filters, inMemorySamples, decodeSamples);
        }
    }

    private void writeJsonForChunks(final JsonGenerator generator, final ObjectWriter writer, final Map<Integer, Map<Integer, DecimatingSampleFilter>> filters,
                                    final Iterable<? extends TimelineChunk> chunksForHostAndSampleKind, final boolean decodeSamples) throws IOException {
        for (final TimelineChunk chunk : chunksForHostAndSampleKind) {
            if (decodeSamples) {
                writer.writeValue(generator, new TimelineChunkDecoded(chunk, sampleCoder));
            } else {
                final String hostName = timelineDao.getSource(chunk.getSourceId(), context);
                final CategoryRecordIdAndMetric categoryIdAndSampleKind = timelineDao.getCategoryIdAndMetric(chunk.getMetricId(), context);
                final String eventCategory = timelineDao.getEventCategory(categoryIdAndSampleKind.getEventCategoryId(), context);
                final String sampleKind = categoryIdAndSampleKind.getMetric();
                // TODO pass compact form
                final DecimatingSampleFilter filter = filters.get(chunk.getSourceId()).get(chunk.getMetricId());
                // TODO CSV only for now
                final String samples = filter == null ? CSVConsumer.getSamplesAsCSV(sampleCoder, chunk) : CSVConsumer.getSamplesAsCSV(sampleCoder, chunk, filter);

                // Don't write out empty samples
                if (!Strings.isNullOrEmpty(samples)) {
                    generator.writeObject(new SamplesForMetricAndSource(hostName, eventCategory, sampleKind, samples));
                }
            }
        }
    }
}
