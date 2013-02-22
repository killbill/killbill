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

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.skife.jdbi.v2.DefaultMapper;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.BatchChunkSize;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.stringtemplate.UseStringTemplate3StatementLocator;

import com.ning.billing.meter.timeline.categories.CategoryIdAndMetricMapper;
import com.ning.billing.meter.timeline.categories.CategoryRecordIdAndMetric;
import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.chunks.TimelineChunkBinder;
import com.ning.billing.meter.timeline.shutdown.StartTimes;
import com.ning.billing.meter.timeline.shutdown.StartTimesBinder;
import com.ning.billing.meter.timeline.shutdown.StartTimesMapper;
import com.ning.billing.meter.timeline.sources.SourceIdAndMetricIdMapper;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;

@UseStringTemplate3StatementLocator()
@RegisterMapper({CategoryIdAndMetricMapper.class, StartTimesMapper.class, SourceIdAndMetricIdMapper.class, DefaultMapper.class})
public interface TimelineSqlDao extends Transactional<TimelineSqlDao> {

    @SqlQuery
    Integer getSourceRecordId(@Bind("source") final String source,
                              @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    String getSourceName(@Bind("recordId") final Integer sourceRecordId,
                         @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    List<Map<String, Object>> getSources(@InternalTenantContextBinder final InternalTenantContext context);

    @SqlUpdate
    void addSource(@Bind("source") final String source,
                   @InternalTenantContextBinder final InternalCallContext context);

    @SqlQuery
    Integer getCategoryRecordId(@Bind("category") final String category,
                                @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    String getCategory(@Bind("recordId") final Integer categoryRecordId,
                       @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    List<Map<String, Object>> getCategories(@InternalTenantContextBinder final InternalTenantContext context);

    @SqlUpdate
    void addCategory(@Bind("category") final String category,
                     @InternalTenantContextBinder final InternalCallContext context);

    @SqlQuery
    Integer getMetricRecordId(@Bind("categoryRecordId") final int categoryRecordId,
                              @Bind("metric") final String metric,
                              @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    Iterable<Integer> getMetricRecordIdsBySourceRecordId(@Bind("sourceRecordId") final Integer sourceRecordId,
                                                         @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    CategoryRecordIdAndMetric getCategoryRecordIdAndMetric(@Bind("recordId") final Integer metricRecordId,
                                                           @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    String getMetric(@Bind("recordId") final Integer metricRecordId,
                     @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    List<Map<String, Object>> getMetrics(@InternalTenantContextBinder final InternalTenantContext context);

    @SqlUpdate
    void addMetric(@Bind("categoryRecordId") final int categoryRecordId,
                   @Bind("metric") final String metric,
                   @InternalTenantContextBinder final InternalCallContext context);

    @SqlQuery
    int getLastInsertedRecordId(@InternalTenantContextBinder final InternalTenantContext context);

    @SqlUpdate
    void insertTimelineChunk(@TimelineChunkBinder final TimelineChunk timelineChunk,
                             @InternalTenantContextBinder final InternalCallContext context);

    @SqlBatch
    @BatchChunkSize(1000)
    void bulkInsertTimelineChunks(@TimelineChunkBinder Iterator<TimelineChunk> chunkIterator,
                                  @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    Integer insertLastStartTimes(@StartTimesBinder final StartTimes startTimes,
                                 @InternalTenantContextBinder final InternalCallContext context);

    @SqlQuery
    StartTimes getLastStartTimes(@InternalTenantContextBinder final InternalTenantContext context);

    @SqlUpdate
    void deleteLastStartTimes(@InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    void test(@InternalTenantContextBinder final InternalTenantContext context);
}
