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

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;

/**
 * Instances of this class represent timeline sequences read from the database
 * for a single source and single metric.  The samples are held in a byte
 * array.
 */
public class TimelineChunk {

    @JsonProperty
    @JsonView(TimelineChunksViews.Base.class)
    private final long chunkId;
    @JsonProperty
    @JsonView(TimelineChunksViews.Base.class)
    private final int sourceId;
    @JsonProperty
    @JsonView(TimelineChunksViews.Base.class)
    private final int metricId;
    @JsonProperty
    @JsonView(TimelineChunksViews.Compact.class)
    private final DateTime startTime;
    @JsonProperty
    @JsonView(TimelineChunksViews.Compact.class)
    private final DateTime endTime;
    @JsonProperty
    @JsonView(TimelineChunksViews.Compact.class)
    private final TimeBytesAndSampleBytes timeBytesAndSampleBytes;
    @JsonProperty
    @JsonView(TimelineChunksViews.Compact.class)
    private final int sampleCount;
    @JsonProperty
    @JsonView(TimelineChunksViews.Compact.class)
    private final int aggregationLevel;
    @JsonProperty
    @JsonView(TimelineChunksViews.Compact.class)
    private final boolean notValid;
    @JsonProperty
    @JsonView(TimelineChunksViews.Compact.class)
    private final boolean dontAggregate;

    public TimelineChunk(final long chunkId, final int sourceId, final int metricId, final DateTime startTime, final DateTime endTime,
                         final byte[] times, final byte[] samples, final int sampleCount) {
        this(chunkId, sourceId, metricId, startTime, endTime, times, samples, sampleCount, 0, false, false);
    }

    public TimelineChunk(final long chunkId, final int sourceId, final int metricId, final DateTime startTime, final DateTime endTime,
                         final byte[] times, final byte[] samples, final int sampleCount, final int aggregationLevel, final boolean notValid, final boolean dontAggregate) {
        this(chunkId, sourceId, metricId, startTime, endTime, new TimeBytesAndSampleBytes(times, samples), sampleCount,
             aggregationLevel, notValid, dontAggregate);
    }

    public TimelineChunk(final long chunkId, final int sourceId, final int metricId, final DateTime startTime, final DateTime endTime,
                         final TimeBytesAndSampleBytes timeBytesAndSampleBytes, final int sampleCount, final int aggregationLevel, final boolean notValid, final boolean dontAggregate) {
        this.chunkId = chunkId;
        this.sourceId = sourceId;
        this.metricId = metricId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.timeBytesAndSampleBytes = timeBytesAndSampleBytes;
        this.sampleCount = sampleCount;
        this.aggregationLevel = aggregationLevel;
        this.notValid = notValid;
        this.dontAggregate = dontAggregate;
    }

    public TimelineChunk(final long chunkId, final TimelineChunk other) {
        this(chunkId, other.getSourceId(), other.getMetricId(), other.getStartTime(), other.getEndTime(), other.getTimeBytesAndSampleBytes(),
             other.getSampleCount(), other.getAggregationLevel(), other.getNotValid(), other.getDontAggregate());
    }

    public long getChunkId() {
        return chunkId;
    }

    public int getSourceId() {
        return sourceId;
    }

    public int getMetricId() {
        return metricId;
    }

    public DateTime getStartTime() {
        return startTime;
    }

    public DateTime getEndTime() {
        return endTime;
    }

    public TimeBytesAndSampleBytes getTimeBytesAndSampleBytes() {
        return timeBytesAndSampleBytes;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public int getAggregationLevel() {
        return aggregationLevel;
    }

    public boolean getNotValid() {
        return notValid;
    }

    public boolean getDontAggregate() {
        return dontAggregate;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TimelineChunk");
        sb.append("{chunkId=").append(chunkId);
        sb.append(", sourceId=").append(sourceId);
        sb.append(", metricId=").append(metricId);
        sb.append(", startTime=").append(startTime);
        sb.append(", endTime=").append(endTime);
        sb.append(", timeBytesAndSampleBytes=").append(timeBytesAndSampleBytes);
        sb.append(", sampleCount=").append(sampleCount);
        sb.append(", aggregationLevel=").append(aggregationLevel);
        sb.append(", notValid=").append(notValid);
        sb.append(", dontAggregate=").append(dontAggregate);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final TimelineChunk that = (TimelineChunk) o;

        if (aggregationLevel != that.aggregationLevel) {
            return false;
        }
        if (chunkId != that.chunkId) {
            return false;
        }
        if (dontAggregate != that.dontAggregate) {
            return false;
        }
        if (metricId != that.metricId) {
            return false;
        }
        if (notValid != that.notValid) {
            return false;
        }
        if (sampleCount != that.sampleCount) {
            return false;
        }
        if (sourceId != that.sourceId) {
            return false;
        }
        if (endTime != null ? !endTime.equals(that.endTime) : that.endTime != null) {
            return false;
        }
        if (startTime != null ? !startTime.equals(that.startTime) : that.startTime != null) {
            return false;
        }
        if (timeBytesAndSampleBytes != null ? !timeBytesAndSampleBytes.equals(that.timeBytesAndSampleBytes) : that.timeBytesAndSampleBytes != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = (int) (chunkId ^ (chunkId >>> 32));
        result = 31 * result + sourceId;
        result = 31 * result + metricId;
        result = 31 * result + (startTime != null ? startTime.hashCode() : 0);
        result = 31 * result + (endTime != null ? endTime.hashCode() : 0);
        result = 31 * result + (timeBytesAndSampleBytes != null ? timeBytesAndSampleBytes.hashCode() : 0);
        result = 31 * result + sampleCount;
        result = 31 * result + aggregationLevel;
        result = 31 * result + (notValid ? 1 : 0);
        result = 31 * result + (dontAggregate ? 1 : 0);
        return result;
    }
}
