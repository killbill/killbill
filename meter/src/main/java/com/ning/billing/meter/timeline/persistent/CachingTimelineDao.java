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

package com.ning.billing.meter.timeline.persistent;

import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.exceptions.CallbackFailedException;
import org.skife.jdbi.v2.exceptions.UnableToObtainConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.meter.timeline.categories.CategoryRecordIdAndMetric;
import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.consumer.TimelineChunkConsumer;
import com.ning.billing.meter.timeline.shutdown.StartTimes;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

public class CachingTimelineDao implements TimelineDao {

    private static final Logger log = LoggerFactory.getLogger(CachingTimelineDao.class);

    private final BiMap<Integer, String> sourcesCache = HashBiMap.<Integer, String>create();
    private final BiMap<Integer, CategoryRecordIdAndMetric> metricsCache = HashBiMap.<Integer, CategoryRecordIdAndMetric>create();
    private final BiMap<Integer, String> eventCategoriesCache = HashBiMap.<Integer, String>create();

    private final TimelineDao delegate;

    public CachingTimelineDao(final TimelineDao delegate) {
        this.delegate = delegate;
    }

    @Override
    public Integer getSourceId(final String source, final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        Integer result =  sourcesCache.inverse().get(source);
        if (result == null) {
            result = delegate.getSourceId(source, context);
            if (result != null) {
                synchronized(sourcesCache) {
                    if (sourcesCache.get(result) == null) {
                        sourcesCache.put(result, source);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public String getSource(final Integer sourceId, final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        String result = sourcesCache.get(sourceId);
        if (result == null) {
            result = delegate.getSource(sourceId, context);
            if (result != null) {
                synchronized (sourcesCache) {
                    if (sourcesCache.get(sourceId) == null) {
                        sourcesCache.put(sourceId, result);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public BiMap<Integer, String> getSources(final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getSources(context);
    }

    @Override
    public int getOrAddSource(final String source, final InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        Integer sourceId = sourcesCache.inverse().get(source);
        if (sourceId == null) {
            sourceId = delegate.getOrAddSource(source, context);
            synchronized (sourcesCache) {
                if (sourcesCache.get(sourceId) == null) {
                    sourcesCache.put(sourceId, source);
                }
            }
        }
        return sourceId;
    }

    @Override
    public Integer getEventCategoryId(final String eventCategory, final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        Integer result = eventCategoriesCache.inverse().get(eventCategory);
        if (result == null) {
            result = delegate.getEventCategoryId(eventCategory, context);
            if (result != null) {
                synchronized (eventCategoriesCache) {
                    if (eventCategoriesCache.get(result) == null) {
                        eventCategoriesCache.put(result, eventCategory);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public String getEventCategory(final Integer eventCategoryId, final InternalTenantContext context) throws UnableToObtainConnectionException {
        String result =  eventCategoriesCache.get(eventCategoryId);
        if (result == null) {
            result = delegate.getEventCategory(eventCategoryId, context);
            if (result != null) {
                synchronized (eventCategoriesCache) {
                    if (eventCategoriesCache.get(eventCategoryId) == null) {
                        eventCategoriesCache.put(eventCategoryId, result);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public BiMap<Integer, String> getEventCategories(final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getEventCategories(context);
    }

    @Override
    public int getOrAddEventCategory(final String eventCategory, final InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        Integer eventCategoryId = eventCategoriesCache.inverse().get(eventCategory);
        if (eventCategoryId == null) {
            eventCategoryId = delegate.getOrAddEventCategory(eventCategory, context);
            synchronized (eventCategoriesCache) {
                if (eventCategoriesCache.get(eventCategoryId) == null) {
                    eventCategoriesCache.put(eventCategoryId, eventCategory);
                }
            }
        }
        return eventCategoryId;
    }

    @Override
    public Integer getMetricId(final int eventCategoryId, final String metric, final InternalTenantContext context) throws UnableToObtainConnectionException {
        Integer result =  metricsCache.inverse().get(new CategoryRecordIdAndMetric(eventCategoryId, metric));
        if (result == null) {
            result = delegate.getMetricId(eventCategoryId, metric, context);
            if (result != null) {
                synchronized (metricsCache) {
                    if (metricsCache.get(result) == null) {
                        metricsCache.put(result, new CategoryRecordIdAndMetric(eventCategoryId, metric));
                    }
                }
            }
        }
        return result;
    }

    @Override
    public CategoryRecordIdAndMetric getCategoryIdAndMetric(final Integer metricId, final InternalTenantContext context) throws UnableToObtainConnectionException {
        CategoryRecordIdAndMetric result =  metricsCache.get(metricId);
        if (result == null) {
            result = delegate.getCategoryIdAndMetric(metricId, context);
            if (result != null) {
                synchronized (metricsCache) {
                    if (metricsCache.get(metricId) == null) {
                        metricsCache.put(metricId, result);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public BiMap<Integer, CategoryRecordIdAndMetric> getMetrics(final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getMetrics(context);
    }

    @Override
    public int getOrAddMetric(final Integer eventCategoryId, final String metric, final InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        final CategoryRecordIdAndMetric categoryRecordIdAndMetric = new CategoryRecordIdAndMetric(eventCategoryId, metric);
        Integer metricId = metricsCache.inverse().get(categoryRecordIdAndMetric);
        if (metricId == null) {
            metricId = delegate.getOrAddMetric(eventCategoryId, metric, context);
            synchronized (metricsCache) {
                if (metricsCache.get(metricId) == null) {
                    metricsCache.put(metricId, categoryRecordIdAndMetric);
                }
            }
        }
        return metricId;
    }

    @Override
    public Long insertTimelineChunk(final TimelineChunk timelineChunk, final InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.insertTimelineChunk(timelineChunk, context);
    }

    @Override
    public void getSamplesBySourceIdsAndMetricIds(final List<Integer> sourceIds, @Nullable final List<Integer> metricIds,
                                                  final DateTime startTime, final DateTime endTime,
                                                  final TimelineChunkConsumer chunkConsumer, final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        delegate.getSamplesBySourceIdsAndMetricIds(sourceIds, metricIds, startTime, endTime, chunkConsumer, context);
    }

    @Override
    public Integer insertLastStartTimes(final StartTimes startTimes, final InternalCallContext context) {
        return delegate.insertLastStartTimes(startTimes, context);
    }

    @Override
    public StartTimes getLastStartTimes(final InternalTenantContext context) {
        return delegate.getLastStartTimes(context);
    }

    @Override
    public void deleteLastStartTimes(final InternalCallContext context) {
        delegate.deleteLastStartTimes(context);
    }

    @Override
    public void bulkInsertTimelineChunks(final List<TimelineChunk> timelineChunkList, final InternalCallContext context) {
        delegate.bulkInsertTimelineChunks(timelineChunkList, context);
    }

    @Override
    public void test(final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        delegate.test(context);
    }
}
