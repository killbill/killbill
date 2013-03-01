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

package com.ning.billing.meter.timeline.metrics;

public class MetricAndId {

    private final String metric;
    private final int metricId;

    public MetricAndId(final String metric, final int metricId) {
        this.metric = metric;
        this.metricId = metricId;
    }

    public String getMetric() {
        return metric;
    }

    public int getMetricId() {
        return metricId;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("MetricAndId");
        sb.append("{metric='").append(metric).append('\'');
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

        final MetricAndId that = (MetricAndId) o;

        if (metricId != that.metricId) {
            return false;
        }
        if (metric != null ? !metric.equals(that.metric) : that.metric != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = metric != null ? metric.hashCode() : 0;
        result = 31 * result + metricId;
        return result;
    }
}
