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
import java.util.Map;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Query;
import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.exceptions.CallbackFailedException;
import org.skife.jdbi.v2.exceptions.UnableToObtainConnectionException;
import org.skife.jdbi.v2.sqlobject.stringtemplate.StringTemplate3StatementLocator;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.meter.timeline.categories.CategoryIdAndMetric;
import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.chunks.TimelineChunkMapper;
import com.ning.billing.meter.timeline.consumer.TimelineChunkConsumer;
import com.ning.billing.meter.timeline.shutdown.StartTimes;
import com.ning.billing.meter.timeline.sources.SourceIdAndMetricId;
import com.ning.billing.meter.timeline.util.DateTimeUtils;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;

import com.google.common.base.Joiner;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.inject.Inject;

public class DefaultTimelineDao implements TimelineDao {

    private static final Logger log = LoggerFactory.getLogger(DefaultTimelineDao.class);
    private static final Joiner JOINER = Joiner.on(",");

    private final IDBI dbi;
    private final TimelineChunkMapper timelineChunkMapper;
    private final TimelineSqlDao delegate;

    @Inject
    public DefaultTimelineDao(final IDBI dbi) {
        this.dbi = dbi;
        this.timelineChunkMapper = new TimelineChunkMapper();
        this.delegate = dbi.onDemand(TimelineSqlDao.class);
    }

    @Override
    public String getSource(final Integer sourceId, final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getSource(sourceId, context);
    }

    @Override
    public Integer getSourceId(final String source, final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getSourceId(source, context);
    }

    @Override
    public BiMap<Integer, String> getSources(final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        final HashBiMap<Integer, String> accumulator = HashBiMap.create();
        for (final Map<String, Object> metric : delegate.getSources(context)) {
            accumulator.put(Integer.valueOf(metric.get("source_id").toString()), metric.get("source_name").toString());
        }
        return accumulator;
    }

    @Override
    public synchronized int getOrAddSource(final String source, final InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        delegate.begin();
        delegate.addSource(source, context);
        final Integer sourceId = delegate.getSourceId(source, context);
        delegate.commit();

        return sourceId;
    }

    @Override
    public Integer getEventCategoryId(final String eventCategory, final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getEventCategoryId(eventCategory, context);
    }

    @Override
    public String getEventCategory(final Integer eventCategoryId, final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getEventCategory(eventCategoryId, context);
    }

    @Override
    public BiMap<Integer, String> getEventCategories(final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        final HashBiMap<Integer, String> accumulator = HashBiMap.create();
        for (final Map<String, Object> eventCategory : delegate.getEventCategories(context)) {
            accumulator.put(Integer.valueOf(eventCategory.get("event_category_id").toString()), eventCategory.get("event_category").toString());
        }
        return accumulator;
    }

    @Override
    public synchronized int getOrAddEventCategory(final String eventCategory, final InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        delegate.begin();
        delegate.addEventCategory(eventCategory, context);
        final Integer eventCategoryId = delegate.getEventCategoryId(eventCategory, context);
        delegate.commit();

        return eventCategoryId;
    }

    @Override
    public Integer getMetricId(final int eventCategoryId, final String metric, final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getMetricId(eventCategoryId, metric, context);
    }

    @Override
    public CategoryIdAndMetric getCategoryIdAndMetric(final Integer metricId, final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getEventCategoryIdAndMetric(metricId, context);
    }

    @Override
    public BiMap<Integer, CategoryIdAndMetric> getMetrics(final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        final HashBiMap<Integer, CategoryIdAndMetric> accumulator = HashBiMap.create();
        for (final Map<String, Object> metricInfo : delegate.getMetrics(context)) {
            accumulator.put(Integer.valueOf(metricInfo.get("sample_kind_id").toString()),
                            new CategoryIdAndMetric((Integer) metricInfo.get("event_category_id"), metricInfo.get("sample_kind").toString()));
        }
        return accumulator;
    }

    @Override
    public synchronized int getOrAddMetric(final Integer sourceId, final Integer eventCategoryId, final String metric, final InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        delegate.begin();
        delegate.addMetric(eventCategoryId, metric, context);
        final Integer metricId = delegate.getMetricId(eventCategoryId, metric, context);
        delegate.commit();

        return metricId;
    }

    @Override
    public Iterable<Integer> getMetricIdsBySourceId(final Integer sourceId, final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getMetricIdsBySourceId(sourceId, context);
    }

    @Override
    public Iterable<SourceIdAndMetricId> getMetricIdsForAllSources(final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getMetricIdsForAllSources(context);
    }

    @Override
    public Long insertTimelineChunk(final TimelineChunk timelineChunk, final InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        delegate.begin();
        delegate.insertTimelineChunk(timelineChunk, context);
        final long timelineChunkId = delegate.getLastInsertedId(context);
        delegate.commit();
        return timelineChunkId;
    }

    @Override
    public void getSamplesBySourceIdsAndMetricIds(final List<Integer> sourceIdList,
                                                  @Nullable final List<Integer> metricIdList,
                                                  final DateTime startTime,
                                                  final DateTime endTime,
                                                  final TimelineChunkConsumer chunkConsumer,
                                                  final InternalTenantContext context) {
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                handle.setStatementLocator(new StringTemplate3StatementLocator(TimelineSqlDao.class));

                ResultIterator<TimelineChunk> iterator = null;
                try {
                    final Query<Map<String, Object>> query = handle
                            .createQuery("getSamplesBySourceIdsAndMetricIds")
                            .bind("startTime", DateTimeUtils.unixSeconds(startTime))
                            .bind("endTime", DateTimeUtils.unixSeconds(endTime))
                            .define("sourceIds", JOINER.join(sourceIdList));

                    if (metricIdList != null && !metricIdList.isEmpty()) {
                        query.define("metricIds", JOINER.join(metricIdList));
                    }

                    iterator = query
                            .map(timelineChunkMapper)
                            .iterator();

                    while (iterator.hasNext()) {
                        chunkConsumer.processTimelineChunk(iterator.next());
                    }
                    return null;
                } finally {
                    if (iterator != null) {
                        try {
                            iterator.close();
                        } catch (Exception e) {
                            log.error("Exception closing TimelineChunkAndTimes iterator for sourceIds {} and metricIds {}", sourceIdList, metricIdList);
                        }
                    }
                }
            }
        });
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
    public void test(final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        delegate.test(context);
    }

    @Override
    public void bulkInsertSources(final List<String> sources, final InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        delegate.bulkInsertSources(sources.iterator(), context);
    }

    @Override
    public void bulkInsertEventCategories(final List<String> categoryNames, final InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        delegate.bulkInsertEventCategories(categoryNames.iterator(), context);
    }

    @Override
    public void bulkInsertMetrics(final List<CategoryIdAndMetric> categoryAndKinds, final InternalCallContext context) {
        delegate.bulkInsertMetrics(categoryAndKinds.iterator(), context);
    }

    @Override
    public void bulkInsertTimelineChunks(final List<TimelineChunk> timelineChunkList, final InternalCallContext context) {
        delegate.bulkInsertTimelineChunks(timelineChunkList.iterator(), context);
    }
}
