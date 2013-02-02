/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.analytics.api;

import java.util.LinkedList;
import java.util.List;

import org.joda.time.LocalDate;

import com.ning.billing.analytics.dao.TimeSeriesTuple;

public class DefaultTimeSeriesData implements TimeSeriesData {

    private final List<LocalDate> dates;
    private final List<Double> values;

    public DefaultTimeSeriesData(final List<TimeSeriesTuple> dataOverTime) {
        // We assume dataOverTime is sorted by time
        dates = new LinkedList<LocalDate>();
        values = new LinkedList<Double>();
        for (final TimeSeriesTuple data : dataOverTime) {
            dates.add(data.getLocalDate());
            values.add(data.getValue());
        }
    }

    @Override
    public List<LocalDate> getDates() {
        return dates;
    }

    @Override
    public List<Double> getValues() {
        return values;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultTimeSeriesData");
        sb.append("{dates=").append(dates);
        sb.append(", values=").append(values);
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

        final DefaultTimeSeriesData that = (DefaultTimeSeriesData) o;

        if (dates != null ? !dates.equals(that.dates) : that.dates != null) {
            return false;
        }
        if (values != null ? !values.equals(that.values) : that.values != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = dates != null ? dates.hashCode() : 0;
        result = 31 * result + (values != null ? values.hashCode() : 0);
        return result;
    }
}
