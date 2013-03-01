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

import java.util.HashMap;
import java.util.Map;

import org.joda.time.DateTime;

import com.ning.billing.meter.timeline.samples.ScalarSample;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableMap;

/**
 * Instances of this class represent samples sent from one source and one
 * category, e.g., JVM, representing one point in time.
 */
@SuppressWarnings("unchecked")
public class SourceSamplesForTimestamp {

    private static final String KEY_SOURCE = "H";
    private static final String KEY_CATEGORY = "V";
    private static final String KEY_TIMESTAMP = "T";
    private static final String KEY_SAMPLES = "S";

    private final Integer sourceId;
    private final String category;
    private final DateTime timestamp;
    // A map from sample id to sample value for that timestamp
    private final Map<Integer, ScalarSample> samples;

    public SourceSamplesForTimestamp(final int sourceId, final String category, final DateTime timestamp) {
        this(sourceId, category, timestamp, new HashMap<Integer, ScalarSample>());
    }

    @JsonCreator
    public SourceSamplesForTimestamp(@JsonProperty(KEY_SOURCE) final Integer sourceId, @JsonProperty(KEY_CATEGORY) final String category,
                                     @JsonProperty(KEY_TIMESTAMP) final DateTime timestamp, @JsonProperty(KEY_SAMPLES) final Map<Integer, ScalarSample> samples) {
        this.sourceId = sourceId;
        this.category = category;
        this.timestamp = timestamp;
        this.samples = samples;
    }

    public int getSourceId() {
        return sourceId;
    }

    public String getCategory() {
        return category;
    }

    public DateTime getTimestamp() {
        return timestamp;
    }

    public Map<Integer, ScalarSample> getSamples() {
        return samples;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SourceSamplesForTimestamp");
        sb.append("{category='").append(category).append('\'');
        sb.append(", sourceId=").append(sourceId);
        sb.append(", timestamp=").append(timestamp);
        sb.append(", samples=").append(samples);
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

        final SourceSamplesForTimestamp that = (SourceSamplesForTimestamp) o;

        if (category != null ? !category.equals(that.category) : that.category != null) {
            return false;
        }
        if (samples != null ? !samples.equals(that.samples) : that.samples != null) {
            return false;
        }
        if (sourceId != null ? !sourceId.equals(that.sourceId) : that.sourceId != null) {
            return false;
        }
        if (timestamp != null ? !timestamp.equals(that.timestamp) : that.timestamp != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = sourceId != null ? sourceId.hashCode() : 0;
        result = 31 * result + (category != null ? category.hashCode() : 0);
        result = 31 * result + (timestamp != null ? timestamp.hashCode() : 0);
        result = 31 * result + (samples != null ? samples.hashCode() : 0);
        return result;
    }

    @JsonValue
    public Map<String, Object> toMap() {
        return ImmutableMap.of(KEY_SOURCE, sourceId, KEY_CATEGORY, category, KEY_TIMESTAMP, timestamp, KEY_SAMPLES, samples);
    }
}
