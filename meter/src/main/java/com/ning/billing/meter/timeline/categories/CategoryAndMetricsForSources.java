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

package com.ning.billing.meter.timeline.categories;

import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CategoryAndMetricsForSources implements Comparable<CategoryAndMetricsForSources> {

    @JsonProperty
    private final CategoryAndMetrics categoryAndMetrics;
    @JsonProperty
    private final Set<String> sources;

    public CategoryAndMetricsForSources(final String eventCategory) {
        this(new CategoryAndMetrics(eventCategory), new TreeSet<String>());
    }

    @JsonCreator
    public CategoryAndMetricsForSources(@JsonProperty("categoryAndMetrics") final CategoryAndMetrics categoryAndMetrics, @JsonProperty("sources") final Set<String> sources) {
        this.categoryAndMetrics = categoryAndMetrics;
        this.sources = sources;
    }

    public void add(final String metric, final String source) {
        categoryAndMetrics.addMetric(metric);
        sources.add(source);
    }

    public CategoryAndMetrics getCategoryAndMetrics() {
        return categoryAndMetrics;
    }

    public Set<String> getSources() {
        return sources;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("CategoryAndMetricsForSources");
        sb.append("{categoryAndMetrics=").append(categoryAndMetrics);
        sb.append(", sources=").append(sources);
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

        final CategoryAndMetricsForSources that = (CategoryAndMetricsForSources) o;

        if (categoryAndMetrics != null ? !categoryAndMetrics.equals(that.categoryAndMetrics) : that.categoryAndMetrics != null) {
            return false;
        }
        if (sources != null ? !sources.equals(that.sources) : that.sources != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = categoryAndMetrics != null ? categoryAndMetrics.hashCode() : 0;
        result = 31 * result + (sources != null ? sources.hashCode() : 0);
        return result;
    }

    @Override
    public int compareTo(final CategoryAndMetricsForSources o) {
        return categoryAndMetrics.compareTo(o.getCategoryAndMetrics());
    }
}
