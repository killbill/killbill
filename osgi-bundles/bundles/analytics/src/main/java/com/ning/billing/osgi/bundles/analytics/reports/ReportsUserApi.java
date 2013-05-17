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

package com.ning.billing.osgi.bundles.analytics.reports;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;

import com.ning.billing.osgi.bundles.analytics.dao.BusinessDBIProvider;
import com.ning.billing.osgi.bundles.analytics.json.NamedXYTimeSeries;
import com.ning.billing.osgi.bundles.analytics.json.XY;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;

import com.google.common.collect.Ordering;

public class ReportsUserApi {

    private static final String NO_PIVOT = "____NO_PIVOT____";

    private final IDBI dbi;
    private final ReportsConfiguration reportsConfiguration;

    public ReportsUserApi(final OSGIKillbillDataSource osgiKillbillDataSource,
                          final ReportsConfiguration reportsConfiguration) {
        this.reportsConfiguration = reportsConfiguration;
        dbi = BusinessDBIProvider.get(osgiKillbillDataSource.getDataSource());
    }

    public List<NamedXYTimeSeries> getTimeSeriesDataForReport(final String[] reportNames) {
        return getTimeSeriesDataForReport(reportNames, null, null);
    }

    public List<NamedXYTimeSeries> getTimeSeriesDataForReport(final String[] reportNames, @Nullable final LocalDate startDate, @Nullable final LocalDate endDate) {
        // Mapping of report name -> pivots -> data
        final Map<String, Map<String, List<XY>>> dataForReports = new LinkedHashMap<String, Map<String, List<XY>>>();

        // TODO parallel
        for (final String reportName : reportNames) {
            final String tableName = reportsConfiguration.getTableNameForReport(reportName);
            if (tableName != null) {
                final Map<String, List<XY>> data = getData(tableName);
                dataForReports.put(reportName, data);
            }
        }

        normalizeXValues(dataForReports);
        filterValues(dataForReports, startDate, endDate);

        final List<NamedXYTimeSeries> results = new LinkedList<NamedXYTimeSeries>();
        for (final String reportName : dataForReports.keySet()) {
            // Sort the pivots by name for a consistent display in the dashboard
            for (final String pivotName : Ordering.natural().sortedCopy(dataForReports.get(reportName).keySet())) {
                final String timeSeriesName;
                if (NO_PIVOT.equals(pivotName)) {
                    timeSeriesName = reportsConfiguration.getPrettyNameForReport(reportName);
                } else {
                    timeSeriesName = String.format("%s (%s)", reportsConfiguration.getPrettyNameForReport(reportName), pivotName);
                }

                final List<XY> timeSeries = dataForReports.get(reportName).get(pivotName);
                results.add(new NamedXYTimeSeries(timeSeriesName, timeSeries));
            }
        }
        return results;
    }

    private void filterValues(final Map<String, Map<String, List<XY>>> dataForReports, @Nullable final LocalDate startDate, @Nullable final LocalDate endDate) {
        for (final Map<String, List<XY>> dataForReport : dataForReports.values()) {
            for (final List<XY> dataForPivot : dataForReport.values()) {
                final Iterator<XY> iterator = dataForPivot.iterator();
                while (iterator.hasNext()) {
                    final XY xy = iterator.next();
                    if (startDate != null && new DateTime(xy.getX(), DateTimeZone.UTC).toLocalDate().isBefore(startDate) ||
                        endDate != null && new DateTime(xy.getX(), DateTimeZone.UTC).toLocalDate().isAfter(endDate)) {
                        iterator.remove();
                    }
                }
            }
        }
    }

    private void normalizeXValues(final Map<String, Map<String, List<XY>>> dataForReports) {
        final Set<String> xValues = new HashSet<String>();
        for (final Map<String, List<XY>> dataForReport : dataForReports.values()) {
            for (final List<XY> dataForPivot : dataForReport.values()) {
                for (final XY xy : dataForPivot) {
                    xValues.add(xy.getX());
                }
            }
        }

        for (final Map<String, List<XY>> dataForReport : dataForReports.values()) {
            for (final List<XY> dataForPivot : dataForReport.values()) {
                for (final String x : xValues) {
                    if (!hasX(dataForPivot, x)) {
                        dataForPivot.add(new XY(x, 0));
                    }
                }
            }
        }

        for (final String reportName : dataForReports.keySet()) {
            for (final String pivotName : dataForReports.get(reportName).keySet()) {
                Collections.sort(dataForReports.get(reportName).get(pivotName), new Comparator<XY>() {
                    @Override
                    public int compare(final XY o1, final XY o2) {
                        return new DateTime(o1.getX(), DateTimeZone.UTC).compareTo(new DateTime(o2.getX(), DateTimeZone.UTC));
                    }
                });
            }
        }
    }

    private boolean hasX(final List<XY> values, final String x) {
        for (final XY xy : values) {
            if (xy.getX().equals(x)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, List<XY>> getData(final String tableName) {
        final Map<String, List<XY>> timeSeries = new LinkedHashMap<String, List<XY>>();

        Handle handle = null;
        try {
            handle = dbi.open();
            final List<Map<String, Object>> results = handle.select("select * from " + tableName);
            for (final Map<String, Object> row : results) {
                if (row.get("day") == null || row.get("count") == null) {
                    continue;
                }

                final String date = row.get("day").toString();
                final Float value = Float.valueOf(row.get("count").toString());

                if (row.keySet().size() == 2) {
                    // No pivot
                    if (timeSeries.get(NO_PIVOT) == null) {
                        timeSeries.put(NO_PIVOT, new LinkedList<XY>());
                    }
                    timeSeries.get(NO_PIVOT).add(new XY(date, value));
                } else if (row.get("pivot") != null) {
                    final String pivot = row.get("pivot").toString();
                    if (timeSeries.get(pivot) == null) {
                        timeSeries.put(pivot, new LinkedList<XY>());
                    }
                    timeSeries.get(pivot).add(new XY(date, value));
                }
            }
        } finally {
            if (handle != null) {
                handle.close();
            }
        }

        return timeSeries;
    }
}
