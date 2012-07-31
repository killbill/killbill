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

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.skife.jdbi.v2.DefaultMapper;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.BatchChunkSize;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;

import com.ning.billing.usage.timeline.categories.CategoryIdAndMetric;
import com.ning.billing.usage.timeline.categories.CategoryIdAndMetricBinder;
import com.ning.billing.usage.timeline.categories.CategoryIdAndMetricMapper;
import com.ning.billing.usage.timeline.chunks.TimelineChunk;
import com.ning.billing.usage.timeline.chunks.TimelineChunkBinder;
import com.ning.billing.usage.timeline.shutdown.StartTimes;
import com.ning.billing.usage.timeline.shutdown.StartTimesBinder;
import com.ning.billing.usage.timeline.shutdown.StartTimesMapper;
import com.ning.billing.usage.timeline.sources.SourceIdAndMetricId;
import com.ning.billing.usage.timeline.sources.SourceIdAndMetricIdMapper;

@ExternalizedSqlViaStringTemplate3()
@RegisterMapper({CategoryIdAndMetricMapper.class, StartTimesMapper.class, SourceIdAndMetricIdMapper.class})
public interface TimelineSqlDao extends Transactional<TimelineSqlDao>, Transmogrifier {

    @SqlQuery
    Integer getSourceId(@Bind("sourceName") final String source);

    @SqlQuery
    String getSource(@Bind("sourceId") final Integer sourceId);

    @SqlQuery
    @Mapper(DefaultMapper.class)
    List<Map<String, Object>> getSources();

    @SqlUpdate
    void addSource(@Bind("sourceName") final String source);

    @SqlBatch
    @BatchChunkSize(1000)
    void bulkInsertSources(@Bind("sourceName") Iterator<String> sourcesIterator);

    @SqlQuery
    Integer getEventCategoryId(@Bind("eventCategory") final String eventCategory);

    @SqlQuery
    String getEventCategory(@Bind("eventCategoryId") final Integer eventCategoryId);

    @SqlUpdate
    void addEventCategory(@Bind("eventCategory") final String eventCategory);

    @SqlBatch
    @BatchChunkSize(1000)
    void bulkInsertEventCategories(@Bind("eventCategory") Iterator<String> cateogoryNames);

    @SqlQuery
    Iterable<Integer> getMetricIdsBySourceId(@Bind("sourceId") final Integer sourceId);

    @SqlQuery
    Iterable<SourceIdAndMetricId> getMetricIdsForAllSources();

    @SqlQuery
    Integer getMetricId(@Bind("eventCategoryId") final int eventCategoryId, @Bind("metric") final String metric);

    @SqlQuery
    CategoryIdAndMetric getEventCategoryIdAndMetric(@Bind("metricId") final Integer metricId);

    @SqlUpdate
    void addMetric(@Bind("eventCategoryId") final int eventCategoryId, @Bind("metric") final String metric);

    @SqlBatch
    @BatchChunkSize(1000)
    void bulkInsertMetrics(@CategoryIdAndMetricBinder Iterator<CategoryIdAndMetric> categoriesAndMetrics);

    @SqlQuery
    @Mapper(DefaultMapper.class)
    List<Map<String, Object>> getEventCategories();

    @SqlQuery
    @Mapper(DefaultMapper.class)
    List<Map<String, Object>> getMetrics();

    @SqlQuery
    int getLastInsertedId();

    @SqlQuery
    long getHighestTimelineChunkId();

    @SqlUpdate
    void insertTimelineChunk(@TimelineChunkBinder final TimelineChunk timelineChunk);

    @SqlBatch
    @BatchChunkSize(1000)
    void bulkInsertTimelineChunks(@TimelineChunkBinder Iterator<TimelineChunk> chunkIterator);

    @SqlUpdate
    Integer insertLastStartTimes(@StartTimesBinder final StartTimes startTimes);

    @SqlQuery
    StartTimes getLastStartTimes();

    @SqlUpdate
    void deleteLastStartTimes();

    @SqlUpdate
    void test();
}
