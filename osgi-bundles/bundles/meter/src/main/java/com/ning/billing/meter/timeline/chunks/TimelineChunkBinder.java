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

package com.ning.billing.meter.timeline.chunks;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.Types;

import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;

import com.ning.billing.meter.timeline.chunks.TimelineChunkBinder.TimelineChunkBinderFactory;
import com.ning.billing.meter.timeline.codec.TimesAndSamplesCoder;
import com.ning.billing.meter.timeline.util.DateTimeUtils;

/**
 * jdbi binder for TimelineChunk
 */
@BindingAnnotation(TimelineChunkBinderFactory.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface TimelineChunkBinder {

    public static class TimelineChunkBinderFactory implements BinderFactory {

        // Maximum size in bytes for a series of timesAndSamples to stay "in row" (stored as VARBINARY).
        // Past this threshold, data is stored as a BLOB.
        private static final int MAX_IN_ROW_BLOB_SIZE = 400;

        public Binder build(final Annotation annotation) {
            return new Binder<TimelineChunkBinder, TimelineChunk>() {
                public void bind(final SQLStatement query, final TimelineChunkBinder binder, final TimelineChunk timelineChunk) {
                    query.bind("sourceRecordId", timelineChunk.getSourceId())
                         .bind("metricRecordId", timelineChunk.getMetricId())
                         .bind("sampleCount", timelineChunk.getSampleCount())
                         .bind("startTime", DateTimeUtils.unixSeconds(timelineChunk.getStartTime()))
                         .bind("endTime", DateTimeUtils.unixSeconds(timelineChunk.getEndTime()))
                         .bind("aggregationLevel", timelineChunk.getAggregationLevel())
                         .bind("notValid", timelineChunk.getNotValid() ? 1 : 0)
                         .bind("dontAggregate", timelineChunk.getDontAggregate() ? 1 : 0);

                    final byte[] times = timelineChunk.getTimeBytesAndSampleBytes().getTimeBytes();
                    final byte[] samples = timelineChunk.getTimeBytesAndSampleBytes().getSampleBytes();
                    final byte[] timesAndSamples = TimesAndSamplesCoder.combineTimesAndSamples(times, samples);
                    if (timelineChunk.getChunkId() == 0) {
                        query.bindNull("chunkId", Types.BIGINT);
                    } else {
                        query.bind("chunkId", timelineChunk.getChunkId());
                    }

                    if (timesAndSamples.length > MAX_IN_ROW_BLOB_SIZE) {
                        query.bindNull("inRowSamples", Types.VARBINARY)
                             .bind("blobSamples", timesAndSamples);
                    } else {
                        query.bind("inRowSamples", timesAndSamples)
                             .bindNull("blobSamples", Types.BLOB);
                    }
                }
            };
        }
    }
}
