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
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Query;
import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.exceptions.CallbackFailedException;
import org.skife.jdbi.v2.exceptions.UnableToObtainConnectionException;
import org.skife.jdbi.v2.sqlobject.stringtemplate.StringTemplate3StatementLocator;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.meter.timeline.categories.CategoryRecordIdAndMetric;
import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.chunks.TimelineChunkMapper;
import com.ning.billing.meter.timeline.consumer.TimelineChunkConsumer;
import com.ning.billing.meter.timeline.shutdown.StartTimes;
import com.ning.billing.meter.timeline.sources.SourceRecordIdAndMetricRecordId;
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
        return delegate.getSourceName(sourceId, context);
    }

    @Override
    public Integer getSourceId(final String source, final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getSourceRecordId(source, context);
    }

    @Override
    public BiMap<Integer, String> getSources(final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        final HashBiMap<Integer, String> accumulator = HashBiMap.create();
        for (final Map<String, Object> metric : delegate.getSources(context)) {
            accumulator.put(Integer.valueOf(metric.get("record_id").toString()), metric.get("source").toString());
        }
        return accumulator;
    }

    @Override
    public int getOrAddSource(final String source, final InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException {

        final Integer result = delegate.inTransaction(new Transaction<Integer, TimelineSqlDao>() {

            @Override
            public Integer inTransaction(final TimelineSqlDao transactional, final TransactionStatus status) throws Exception {
                return getOrAddWithRetry(new Callable<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        Integer sourceId = transactional.getSourceRecordId(source, context);
                        if (sourceId == null) {
                            transactional.addSource(source, context);
                            sourceId = transactional.getSourceRecordId(source, context);
                        }
                        return sourceId;
                    }
                });
            }
        });
        return result;
    }

    @Override
    public Integer getEventCategoryId(final String eventCategory, final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getCategoryRecordId(eventCategory, context);
    }

    @Override
    public String getEventCategory(final Integer eventCategoryId, final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getCategory(eventCategoryId, context);
    }

    @Override
    public BiMap<Integer, String> getEventCategories(final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        final HashBiMap<Integer, String> accumulator = HashBiMap.create();
        for (final Map<String, Object> eventCategory : delegate.getCategories(context)) {
            accumulator.put(Integer.valueOf(eventCategory.get("record_id").toString()), eventCategory.get("category").toString());
        }
        return accumulator;
    }

    @Override
    public int getOrAddEventCategory(final String eventCategory, final InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException {

        final Integer result = delegate.inTransaction(new Transaction<Integer, TimelineSqlDao>() {

            @Override
            public Integer inTransaction(final TimelineSqlDao transactional, final TransactionStatus status) throws Exception {
                return getOrAddWithRetry(new Callable<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        Integer eventCategoryId = transactional.getCategoryRecordId(eventCategory, context);
                        if (eventCategoryId == null) {
                            transactional.addCategory(eventCategory, context);
                            eventCategoryId = transactional.getCategoryRecordId(eventCategory, context);
                        }
                        return eventCategoryId;
                    }
                });
            }
        });
        return result;
    }


    @Override
    public Integer getMetricId(final int eventCategoryId, final String metric, final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getMetricRecordId(eventCategoryId, metric, context);
    }

    @Override
    public CategoryRecordIdAndMetric getCategoryIdAndMetric(final Integer metricId, final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        return delegate.getCategoryRecordIdAndMetric(metricId, context);
    }

    @Override
    public BiMap<Integer, CategoryRecordIdAndMetric> getMetrics(final InternalTenantContext context) throws UnableToObtainConnectionException, CallbackFailedException {
        final HashBiMap<Integer, CategoryRecordIdAndMetric> accumulator = HashBiMap.create();
        for (final Map<String, Object> metricInfo : delegate.getMetrics(context)) {
            accumulator.put(Integer.valueOf(metricInfo.get("record_id").toString()),
                            new CategoryRecordIdAndMetric((Integer) metricInfo.get("category_record_id"), metricInfo.get("metric").toString()));
        }
        return accumulator;
    }

    @Override
    public synchronized int getOrAddMetric(final Integer eventCategoryId, final String metric, final InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException {

        final Integer result = delegate.inTransaction(new Transaction<Integer, TimelineSqlDao>() {

            @Override
            public Integer inTransaction(final TimelineSqlDao transactional, final TransactionStatus status) throws Exception {
                return getOrAddWithRetry(new Callable<Integer>() {
                    @Override
                    public Integer call() throws Exception {
                        Integer metricId = transactional.getMetricRecordId(eventCategoryId, metric, context);
                        if (metricId == null) {
                            transactional.addMetric(eventCategoryId, metric, context);
                            metricId = transactional.getMetricRecordId(eventCategoryId, metric, context);
                        }
                        return metricId;
                    }
                });
            }
        });
        return result;
    }

    @Override
    public Long insertTimelineChunk(final TimelineChunk timelineChunk, final InternalCallContext context) throws UnableToObtainConnectionException, CallbackFailedException {

        final Long result = delegate.inTransaction(new Transaction<Long, TimelineSqlDao>() {
            @Override
            public Long inTransaction(final TimelineSqlDao transactional, final TransactionStatus status) throws Exception {
                transactional.insertTimelineChunk(timelineChunk, context);
                final long timelineChunkId = transactional.getLastInsertedRecordId(context);
                return timelineChunkId;
            }
        });
        return result;
    }

    @Override
    public void getSamplesBySourceIdsAndMetricIds(final List<Integer> sourceIdList,
                                                  @Nullable final List<Integer> metricIdList,
                                                  final DateTime startTime,
                                                  final DateTime endTime,
                                                  final TimelineChunkConsumer chunkConsumer,
                                                  final InternalTenantContext context) {
        if (sourceIdList.size() == 0) {
            return;
        }

        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                handle.setStatementLocator(new StringTemplate3StatementLocator(TimelineSqlDao.class));

                ResultIterator<TimelineChunk> iterator = null;
                try {
                    final Query<Map<String, Object>> query = handle
                            .createQuery("getSamplesBySourceRecordIdsAndMetricRecordIds")
                            .bind("startTime", DateTimeUtils.unixSeconds(startTime))
                            .bind("endTime", DateTimeUtils.unixSeconds(endTime))
                            .bind("tenantRecordId", context.getTenantRecordId())
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
    public void bulkInsertTimelineChunks(final List<TimelineChunk> timelineChunkList, final InternalCallContext context) {
        delegate.bulkInsertTimelineChunks(timelineChunkList.iterator(), context);
    }

    private <T> T getOrAddWithRetry(final Callable<T> task) throws Exception {
        int retry = 1;
        Exception lastException = null;
        do {
            try {
                return task.call();
            } catch (Exception e) {
                //
                // If we have two transaction that occurs at the time and try to insert
                // both the same key, one of the transaction will rollbacl and that code will retry
                // and (should) succeed because this time key exists and caller will first do a get.
                //
                lastException = e;
            }
        } while (retry-- > 0);
        throw lastException;
    }

}
