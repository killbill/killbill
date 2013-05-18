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

package com.ning.billing.osgi.bundles.analytics.reports.scheduler;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.osgi.service.log.LogService;
import org.skife.jdbi.v2.Call;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;

import com.ning.billing.osgi.bundles.analytics.BusinessExecutor;
import com.ning.billing.osgi.bundles.analytics.dao.BusinessDBIProvider;
import com.ning.billing.osgi.bundles.analytics.reports.ReportConfigurationSection.Frequency;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public class JobsScheduler {

    private final List<ScheduledExecutorService> jobs = new LinkedList<ScheduledExecutorService>();

    private final OSGIKillbillLogService logService;
    private final IDBI dbi;

    public JobsScheduler(final OSGIKillbillLogService logService,
                         final OSGIKillbillDataSource osgiKillbillDataSource) {
        this.logService = logService;
        dbi = BusinessDBIProvider.get(osgiKillbillDataSource.getDataSource());
    }

    public void shutdownNow() {
        for (final ScheduledExecutorService executor : jobs) {
            executor.shutdownNow();
        }
    }

    public void schedule(final String reportName, final String storedProcedureName, final Frequency frequency, final Integer refreshTimeOfTheDayGMT) {
        final ScheduledExecutorService executor = BusinessExecutor.newSingleThreadScheduledExecutor("osgi-analytics-reports-" + reportName);
        jobs.add(executor);

        final StoredProcedureJob command = new StoredProcedureJob(logService, reportName, storedProcedureName, frequency, refreshTimeOfTheDayGMT);
        executor.scheduleAtFixedRate(command, 0, 1, TimeUnit.MINUTES);
    }

    private final class StoredProcedureJob implements Runnable {

        private final AtomicLong lastRun = new AtomicLong(0);

        private final LogService logService;
        private final String reportName;
        private final String storedProcedureName;
        private final Frequency frequency;
        private final Integer refreshTimeOfTheDayGMT;

        private StoredProcedureJob(final LogService logService, final String reportName, final String storedProcedureName,
                                   final Frequency frequency, final Integer refreshTimeOfTheDayGMT) {
            this.logService = logService;
            this.reportName = reportName;
            this.storedProcedureName = storedProcedureName;
            this.frequency = frequency;
            this.refreshTimeOfTheDayGMT = refreshTimeOfTheDayGMT;
        }

        @Override
        public void run() {
            if (!shouldRun()) {
                return;
            }

            logService.log(LogService.LOG_INFO, "Starting job for " + reportName);

            callStoredProcedure(storedProcedureName);
            lastRun.set(System.currentTimeMillis());

            logService.log(LogService.LOG_INFO, "Ending job for " + reportName);
        }

        private boolean shouldRun() {
            if (Frequency.HOURLY.equals(frequency) && (System.currentTimeMillis() - lastRun.get()) >= 3600000) {
                return true;
            } else if (Frequency.DAILY.equals(frequency)) {
                if (refreshTimeOfTheDayGMT == null && (System.currentTimeMillis() - lastRun.get()) >= 86400000) {
                    return true;
                } else if (refreshTimeOfTheDayGMT != null &&
                           new DateTime(DateTimeZone.UTC).getHourOfDay() == refreshTimeOfTheDayGMT &&
                           (System.currentTimeMillis() - lastRun.get()) >= 3600000) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    private void callStoredProcedure(final String storedProcedureName) {
        Handle handle = null;
        try {
            handle = dbi.open();
            final Call call = handle.createCall(storedProcedureName);
            call.invoke();
        } finally {
            if (handle != null) {
                handle.close();
            }
        }
    }
}
