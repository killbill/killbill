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

package com.ning.billing.usage.timeline.persistent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.exceptions.CallbackFailedException;
import org.skife.jdbi.v2.exceptions.UnableToObtainConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.usage.timeline.categories.CategoryIdAndMetric;
import com.ning.billing.usage.timeline.chunks.TimelineChunk;
import com.ning.billing.usage.timeline.chunks.TimelineChunkConsumer;
import com.ning.billing.usage.timeline.shutdown.StartTimes;
import com.ning.billing.usage.timeline.sources.SourceIdAndMetricId;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableList;

public class CachingTimelineDao implements TimelineDao {

    private static final Logger log = LoggerFactory.getLogger(CachingTimelineDao.class);

    private final BiMap<Integer, String> sourcesCache;
    private final Map<Integer, Set<Integer>> sourceIdsMetricIdsCache;
    private final BiMap<Integer, CategoryIdAndMetric> metricsCache;
    private final BiMap<Integer, String> eventCategoriesCache;

    private final TimelineDao delegate;

    public CachingTimelineDao(final TimelineDao delegate) {
        this.delegate = delegate;
        sourcesCache = delegate.getSources();
        metricsCache = delegate.getMetrics();
        eventCategoriesCache = delegate.getEventCategories();
        sourceIdsMetricIdsCache = new HashMap<Integer, Set<Integer>>();
        for (final SourceIdAndMetricId both : delegate.getMetricIdsForAllSources()) {
            final int sourceId = both.getSourceId();
            final int metricId = both.getMetricId();
            Set<Integer> metricIds = sourceIdsMetricIdsCache.get(sourceId);
            if (metricIds == null) {
                metricIds = new HashSet<Integer>();
                sourceIdsMetricIdsCache.put(sourceId, metricIds);
            }
            metricIds.add(metricId);
        }
    }

    @Override
    public Integer getSourceId(final String source) throws UnableToObtainConnectionException, CallbackFailedException {
        return sourcesCache.inverse().get(source);
    }

    @Override
    public String getSource(final Integer sourceId) throws UnableToObtainConnectionException, CallbackFailedException {
        return sourcesCache.get(sourceId);
    }

    @Override
    public BiMap<Integer, String> getSources() throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getSources();
    }

    @Override
    public synchronized int getOrAddSource(final String source) throws UnableToObtainConnectionException, CallbackFailedException {
        Integer sourceId = sourcesCache.inverse().get(source);
        if (sourceId == null) {
            sourceId = delegate.getOrAddSource(source);
            sourcesCache.put(sourceId, source);
        }

        return sourceId;
    }

    @Override
    public Integer getEventCategoryId(final String eventCategory) throws UnableToObtainConnectionException, CallbackFailedException {
        return eventCategoriesCache.inverse().get(eventCategory);
    }

    @Override
    public String getEventCategory(final Integer eventCategoryId) throws UnableToObtainConnectionException {
        return eventCategoriesCache.get(eventCategoryId);
    }

    @Override
    public BiMap<Integer, String> getEventCategories() throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getEventCategories();
    }

    @Override
    public int getOrAddEventCategory(final String eventCategory) throws UnableToObtainConnectionException, CallbackFailedException {
        Integer eventCategoryId = eventCategoriesCache.inverse().get(eventCategory);
        if (eventCategoryId == null) {
            eventCategoryId = delegate.getOrAddEventCategory(eventCategory);
            eventCategoriesCache.put(eventCategoryId, eventCategory);
        }
        return eventCategoryId;
    }

    @Override
    public Integer getMetricId(final int eventCategoryId, final String metric) throws UnableToObtainConnectionException {
        return metricsCache.inverse().get(new CategoryIdAndMetric(eventCategoryId, metric));
    }

    @Override
    public CategoryIdAndMetric getCategoryIdAndMetric(final Integer metricId) throws UnableToObtainConnectionException {
        return metricsCache.get(metricId);
    }

    @Override
    public BiMap<Integer, CategoryIdAndMetric> getMetrics() throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getMetrics();
    }

    @Override
    public synchronized int getOrAddMetric(final Integer sourceId, final Integer eventCategoryId, final String metric) throws UnableToObtainConnectionException, CallbackFailedException {
        final CategoryIdAndMetric categoryIdAndMetric = new CategoryIdAndMetric(eventCategoryId, metric);
        Integer metricId = metricsCache.inverse().get(categoryIdAndMetric);
        if (metricId == null) {
            metricId = delegate.getOrAddMetric(sourceId, eventCategoryId, metric);
            metricsCache.put(metricId, categoryIdAndMetric);
        }
        if (sourceId != null) {
            Set<Integer> metricIds = sourceIdsMetricIdsCache.get(sourceId);
            if (metricIds == null) {
                metricIds = new HashSet<Integer>();
                sourceIdsMetricIdsCache.put(sourceId, metricIds);
            }
            metricIds.add(metricId);
        }
        return metricId;
    }

    @Override
    public Iterable<Integer> getMetricIdsBySourceId(final Integer sourceId) throws UnableToObtainConnectionException, CallbackFailedException {
        return ImmutableList.copyOf(sourceIdsMetricIdsCache.get(sourceId));
    }

    @Override
    public Iterable<SourceIdAndMetricId> getMetricIdsForAllSources() throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getMetricIdsForAllSources();
    }


    @Override
    public Long insertTimelineChunk(final TimelineChunk timelineChunk) throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.insertTimelineChunk(timelineChunk);
    }

    @Override
    public void getSamplesBySourceIdsAndMetricIds(final List<Integer> sourceIds, @Nullable final List<Integer> metricIds,
                                                  final DateTime startTime, final DateTime endTime, final TimelineChunkConsumer chunkConsumer) throws UnableToObtainConnectionException, CallbackFailedException {
        delegate.getSamplesBySourceIdsAndMetricIds(sourceIds, metricIds, startTime, endTime, chunkConsumer);
    }

    @Override
    public Integer insertLastStartTimes(final StartTimes startTimes) {
        return delegate.insertLastStartTimes(startTimes);
    }

    @Override
    public StartTimes getLastStartTimes() {
        return delegate.getLastStartTimes();
    }

    @Override
    public void deleteLastStartTimes() {
        delegate.deleteLastStartTimes();
    }

    @Override
    public void bulkInsertEventCategories(final List<String> categoryNames) throws UnableToObtainConnectionException, CallbackFailedException {
        delegate.bulkInsertEventCategories(categoryNames);
    }

    @Override
    public void bulkInsertSources(final List<String> sources) throws UnableToObtainConnectionException, CallbackFailedException {
        delegate.bulkInsertSources(sources);
    }

    @Override
    public void bulkInsertMetrics(final List<CategoryIdAndMetric> categoryAndKinds) {
        delegate.bulkInsertMetrics(categoryAndKinds);
    }

    @Override
    public void bulkInsertTimelineChunks(final List<TimelineChunk> timelineChunkList) {
        delegate.bulkInsertTimelineChunks(timelineChunkList);
    }

    @Override
    public void test() throws UnableToObtainConnectionException, CallbackFailedException {
        delegate.test();
    }
}
