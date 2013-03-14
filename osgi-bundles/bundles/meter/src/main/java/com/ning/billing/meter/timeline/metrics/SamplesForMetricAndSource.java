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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SamplesForMetricAndSource {

    @JsonProperty
    private final String sourceName;

    @JsonProperty
    private final String eventCategory;

    @JsonProperty
    private final String metric;

    @JsonProperty
    private final String samples;

    @JsonCreator
    public SamplesForMetricAndSource(@JsonProperty("sourceName") final String sourceName, @JsonProperty("eventCategory") final String eventCategory,
                                     @JsonProperty("metric") final String metric, @JsonProperty("samples") final String samples) {
        this.sourceName = sourceName;
        this.eventCategory = eventCategory;
        this.metric = metric;
        this.samples = samples;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getEventCategory() {
        return eventCategory;
    }

    public String getMetric() {
        return metric;
    }

    public String getSamples() {
        return samples;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SamplesForMetricAndSource");
        sb.append("{eventCategory='").append(eventCategory).append('\'');
        sb.append(", sourceName='").append(sourceName).append('\'');
        sb.append(", metric='").append(metric).append('\'');
        sb.append(", samples='").append(samples).append('\'');
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

        final SamplesForMetricAndSource that = (SamplesForMetricAndSource) o;

        if (!eventCategory.equals(that.eventCategory)) {
            return false;
        }
        if (!sourceName.equals(that.sourceName)) {
            return false;
        }
        if (!metric.equals(that.metric)) {
            return false;
        }
        if (!samples.equals(that.samples)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = sourceName.hashCode();
        result = 31 * result + eventCategory.hashCode();
        result = 31 * result + metric.hashCode();
        result = 31 * result + samples.hashCode();
        return result;
    }
}
