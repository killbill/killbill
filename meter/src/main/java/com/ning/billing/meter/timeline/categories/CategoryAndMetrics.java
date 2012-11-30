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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CategoryAndMetrics implements Comparable<CategoryAndMetrics> {

    @JsonProperty
    private final String eventCategory;
    @JsonProperty
    private final Set<String> metrics = new HashSet<String>();

    public CategoryAndMetrics(final String eventCategory) {
        this.eventCategory = eventCategory;
    }

    @JsonCreator
    public CategoryAndMetrics(@JsonProperty("eventCategory") final String eventCategory, @JsonProperty("metrics") final List<String> metrics) {
        this.eventCategory = eventCategory;
        this.metrics.addAll(metrics);
    }

    public void addMetric(final String metric) {
        metrics.add(metric);
    }

    public String getEventCategory() {
        return eventCategory;
    }

    public Set<String> getMetrics() {
        return metrics;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("CategoryAndMetrics");
        sb.append("{eventCategory='").append(eventCategory).append('\'');
        sb.append(", metrics=").append(metrics);
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

        final CategoryAndMetrics that = (CategoryAndMetrics) o;

        if (!eventCategory.equals(that.eventCategory)) {
            return false;
        }
        if (!metrics.equals(that.metrics)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = eventCategory.hashCode();
        result = 31 * result + metrics.hashCode();
        return result;
    }

    @Override
    public int compareTo(final CategoryAndMetrics o) {
        final int categoryComparison = eventCategory.compareTo(o.getEventCategory());
        if (categoryComparison != 0) {
            return categoryComparison;
        } else {
            if (metrics.size() > o.getMetrics().size()) {
                return 1;
            } else if (metrics.size() < o.getMetrics().size()) {
                return -1;
            } else {
                return 0;
            }
        }
    }
}
