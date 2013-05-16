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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.LocalDate;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;

import com.ning.billing.osgi.bundles.analytics.dao.BusinessDBIProvider;
import com.ning.billing.osgi.bundles.analytics.json.NamedXYTimeSeries;
import com.ning.billing.osgi.bundles.analytics.json.XY;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;

public class ReportsUserApi {

    private final IDBI dbi;
    private final ReportsConfiguration reportsConfiguration;

    public ReportsUserApi(final OSGIKillbillDataSource osgiKillbillDataSource,
                          final ReportsConfiguration reportsConfiguration) {
        this.reportsConfiguration = reportsConfiguration;
        dbi = BusinessDBIProvider.get(osgiKillbillDataSource.getDataSource());
    }

    public List<NamedXYTimeSeries> getTimeSeriesDataForReport(final String[] reportNames) {
        final Map<String, List<XY>> dataForReports = new LinkedHashMap<String, List<XY>>();

        // TODO parallel
        for (final String reportName : reportNames) {
            final String tableName = reportsConfiguration.getTableNameForReport(reportName);
            if (tableName != null) {
                final List<XY> data = getData(tableName);
                dataForReports.put(reportName, data);
            }
        }

        normalizeXValues(dataForReports);

        final List<NamedXYTimeSeries> results = new LinkedList<NamedXYTimeSeries>();
        for (final String reportName : dataForReports.keySet()) {
            results.add(new NamedXYTimeSeries(reportsConfiguration.getPrettyNameForReport(reportName), dataForReports.get(reportName)));
        }
        return results;
    }

    private void normalizeXValues(final Map<String, List<XY>> dataForReports) {
        final Set<String> xValues = new HashSet<String>();
        for (final List<XY> dataForReport : dataForReports.values()) {
            for (final XY xy : dataForReport) {
                xValues.add(xy.getX());
            }
        }

        for (final List<XY> dataForReport : dataForReports.values()) {
            for (final String x : xValues) {
                if (!hasX(dataForReport, x)) {
                    dataForReport.add(new XY(x, 0));
                }
            }
        }

        for (final String reportName : dataForReports.keySet()) {
            Collections.sort(dataForReports.get(reportName), new Comparator<XY>() {
                @Override
                public int compare(final XY o1, final XY o2) {
                    return new LocalDate(o1.getX()).compareTo(new LocalDate(o2.getX()));
                }
            });
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

    private List<XY> getData(final String tableName) {
        final List<XY> timeSeries = new LinkedList<XY>();

        Handle handle = null;
        try {
            handle = dbi.open();
            final List<Map<String, Object>> results = handle.select("select day, count from " + tableName);
            for (final Map<String, Object> row : results) {
                if (row.get("day") == null || row.get("count") == null) {
                    continue;
                }

                final String date = row.get("day").toString();
                final Float value = Float.valueOf(row.get("count").toString());
                timeSeries.add(new XY(date, value));
            }
        } finally {
            if (handle != null) {
                handle.close();
            }
        }

        return timeSeries;
    }
}
