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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CategoryIdAndMetric {

    private final int eventCategoryId;
    private final String metric;

    public CategoryIdAndMetric(final int eventCategoryId, final String metric) {
        this.eventCategoryId = eventCategoryId;
        this.metric = metric;
    }

    public int getEventCategoryId() {
        return eventCategoryId;
    }

    public String getMetric() {
        return metric;
    }

    @Override
    public boolean equals(final Object other) {
        if (other == null || !(other instanceof CategoryIdAndMetric)) {
            return false;
        } else {
            final CategoryIdAndMetric typedOther = (CategoryIdAndMetric) other;
            return eventCategoryId == typedOther.getEventCategoryId() && metric.equals(typedOther.getMetric());
        }
    }

    @Override
    public int hashCode() {
        return eventCategoryId ^ metric.hashCode();
    }

    @Override
    public String toString() {
        return String.format("EventCategoryIdAndMetric(eventCategoryId %d, metric %s)", eventCategoryId, metric);
    }

    public static List<String> extractMetrics(final Collection<CategoryIdAndMetric> categoryIdsAndMetrics) {
        final List<String> metrics = new ArrayList<String>();
        for (final CategoryIdAndMetric categoryIdAndMetric : categoryIdsAndMetrics) {
            metrics.add(categoryIdAndMetric.getMetric());
        }
        return metrics;
    }
}
