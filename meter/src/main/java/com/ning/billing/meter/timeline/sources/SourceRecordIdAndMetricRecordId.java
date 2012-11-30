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

package com.ning.billing.meter.timeline.sources;

public class SourceRecordIdAndMetricRecordId {

    private final int sourceId;
    private final int metricId;

    public SourceRecordIdAndMetricRecordId(final int sourceId, final int metricId) {
        this.sourceId = sourceId;
        this.metricId = metricId;
    }

    public int getSourceId() {
        return sourceId;
    }

    public int getMetricId() {
        return metricId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SourceRecordIdAndMetricRecordId");
        sb.append("{sourceId=").append(sourceId);
        sb.append(", metricId=").append(metricId);
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

        final SourceRecordIdAndMetricRecordId that = (SourceRecordIdAndMetricRecordId) o;

        if (metricId != that.metricId) {
            return false;
        }
        if (sourceId != that.sourceId) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = sourceId;
        result = 31 * result + metricId;
        return result;
    }
}
