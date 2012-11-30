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

import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.meter.timeline.codec.TimesAndSamplesCoder;
import com.ning.billing.meter.timeline.util.DateTimeUtils;

/**
 * jdbi mapper for TimelineChunk
 */
public class TimelineChunkMapper implements ResultSetMapper<TimelineChunk> {

    @Override
    public TimelineChunk map(final int index, final ResultSet rs, final StatementContext ctx) throws SQLException {
        final int chunkId = rs.getInt("record_id");
        final int sourceId = rs.getInt("source_record_id");
        final int metricId = rs.getInt("metric_record_id");
        final int sampleCount = rs.getInt("sample_count");
        final DateTime startTime = DateTimeUtils.dateTimeFromUnixSeconds(rs.getInt("start_time"));
        final DateTime endTime = DateTimeUtils.dateTimeFromUnixSeconds(rs.getInt("end_time"));
        final int aggregationLevel = rs.getInt("aggregation_level");
        final boolean notValid = rs.getInt("not_valid") == 0 ? false : true;
        final boolean dontAggregate = rs.getInt("dont_aggregate") == 0 ? false : true;

        byte[] samplesAndTimes = rs.getBytes("in_row_samples");
        if (rs.wasNull()) {
            final Blob blobSamples = rs.getBlob("blob_samples");
            if (rs.wasNull()) {
                samplesAndTimes = new byte[4];
            } else {
                samplesAndTimes = blobSamples.getBytes(1, (int) blobSamples.length());
            }
        }

        final TimeBytesAndSampleBytes bytesPair = TimesAndSamplesCoder.getTimesBytesAndSampleBytes(samplesAndTimes);
        return new TimelineChunk(chunkId, sourceId, metricId, startTime, endTime, bytesPair, sampleCount,
                                 aggregationLevel, notValid, dontAggregate);
    }
}
