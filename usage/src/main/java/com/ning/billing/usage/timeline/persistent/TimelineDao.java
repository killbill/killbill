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

import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.exceptions.CallbackFailedException;
import org.skife.jdbi.v2.exceptions.UnableToObtainConnectionException;

import com.ning.billing.usage.timeline.categories.CategoryIdAndMetric;
import com.ning.billing.usage.timeline.chunks.TimelineChunk;
import com.ning.billing.usage.timeline.consumer.TimelineChunkConsumer;
import com.ning.billing.usage.timeline.shutdown.StartTimes;
import com.ning.billing.usage.timeline.sources.SourceIdAndMetricId;

import com.google.common.collect.BiMap;

public interface TimelineDao {

    // Sources table

    Integer getSourceId(String source) throws UnableToObtainConnectionException, CallbackFailedException;

    String getSource(Integer sourceId) throws UnableToObtainConnectionException, CallbackFailedException;

    BiMap<Integer, String> getSources() throws UnableToObtainConnectionException, CallbackFailedException;

    int getOrAddSource(String source) throws UnableToObtainConnectionException, CallbackFailedException;

    // Event categories table

    Integer getEventCategoryId(String eventCategory) throws UnableToObtainConnectionException, CallbackFailedException;

    String getEventCategory(Integer eventCategoryId) throws UnableToObtainConnectionException, CallbackFailedException;

    BiMap<Integer, String> getEventCategories() throws UnableToObtainConnectionException, CallbackFailedException;

    int getOrAddEventCategory(String eventCategory) throws UnableToObtainConnectionException, CallbackFailedException;

    // Sample kinds table

    Integer getMetricId(int eventCategory, String metric) throws UnableToObtainConnectionException, CallbackFailedException;

    CategoryIdAndMetric getCategoryIdAndMetric(Integer metricId) throws UnableToObtainConnectionException, CallbackFailedException;

    BiMap<Integer, CategoryIdAndMetric> getMetrics() throws UnableToObtainConnectionException, CallbackFailedException;

    int getOrAddMetric(Integer sourceId, Integer eventCategoryId, String metric) throws UnableToObtainConnectionException, CallbackFailedException;

    Iterable<Integer> getMetricIdsBySourceId(Integer sourceId) throws UnableToObtainConnectionException, CallbackFailedException;

    Iterable<SourceIdAndMetricId> getMetricIdsForAllSources() throws UnableToObtainConnectionException, CallbackFailedException;

    // Timelines tables

    Long insertTimelineChunk(TimelineChunk timelineChunk) throws UnableToObtainConnectionException, CallbackFailedException;

    void getSamplesBySourceIdsAndMetricIds(List<Integer> sourceIds,
                                           @Nullable List<Integer> metricIds,
                                           DateTime startTime,
                                           DateTime endTime,
                                           TimelineChunkConsumer chunkConsumer) throws UnableToObtainConnectionException, CallbackFailedException;

    Integer insertLastStartTimes(StartTimes startTimes);

    StartTimes getLastStartTimes();

    void deleteLastStartTimes();

    void bulkInsertSources(final List<String> sources) throws UnableToObtainConnectionException, CallbackFailedException;

    void bulkInsertEventCategories(final List<String> categoryNames) throws UnableToObtainConnectionException, CallbackFailedException;

    void bulkInsertMetrics(final List<CategoryIdAndMetric> categoryAndKinds);

    void bulkInsertTimelineChunks(final List<TimelineChunk> timelineChunkList);

    void test() throws UnableToObtainConnectionException, CallbackFailedException;
}
