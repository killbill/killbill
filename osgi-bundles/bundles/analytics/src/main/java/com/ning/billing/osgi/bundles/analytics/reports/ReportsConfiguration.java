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

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.osgi.service.log.LogService;

import com.ning.billing.osgi.bundles.analytics.reports.scheduler.JobsScheduler;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.google.common.base.Objects;

public class ReportsConfiguration {

    private static final String REPORTS_CONFIGURATION_FILE_PATH = System.getProperty("com.ning.billing.osgi.bundles.analytics.reports.configuration");

    private final Map<String, ReportConfigurationSection> configurationPerReport = new LinkedHashMap<String, ReportConfigurationSection>();

    private final OSGIKillbillLogService logService;
    private final JobsScheduler scheduler;

    public ReportsConfiguration(final OSGIKillbillLogService logService, final JobsScheduler scheduler) {
        this.logService = logService;
        this.scheduler = scheduler;
    }

    public void initialize() {
        try {
            parseConfigurationFile();
        } catch (IOException e) {
            logService.log(LogService.LOG_WARNING, "Error during initialization", e);
        }
    }

    public String getTableNameForReport(final String reportName) {
        if (configurationPerReport.get(reportName) != null) {
            return configurationPerReport.get(reportName).getTableName();
        } else {
            return null;
        }
    }

    public String getPrettyNameForReport(final String reportName) {
        if (configurationPerReport.get(reportName) != null) {
            return Objects.firstNonNull(configurationPerReport.get(reportName).getPrettyName(), reportName);
        } else {
            return reportName;
        }
    }

    private void parseConfigurationFile() throws IOException {
        if (REPORTS_CONFIGURATION_FILE_PATH == null) {
            return;
        }

        final File configurationFile = new File(REPORTS_CONFIGURATION_FILE_PATH);
        //noinspection MismatchedQueryAndUpdateOfCollection
        final Ini ini = new Ini(configurationFile);
        for (final String reportName : ini.keySet()) {
            final Section section = ini.get(reportName);
            Thread.currentThread().setContextClassLoader(ReportsConfiguration.class.getClassLoader());
            final ReportConfigurationSection reportConfigurationSection = section.as(ReportConfigurationSection.class);

            if (reportConfigurationSection.getFrequency() != null && reportConfigurationSection.getStoredProcedureName() != null) {
                scheduler.schedule(reportName,
                                   reportConfigurationSection.getStoredProcedureName(),
                                   reportConfigurationSection.getFrequency(),
                                   reportConfigurationSection.getRefreshTimeOfTheDayGMT());
            }

            configurationPerReport.put(reportName, reportConfigurationSection);
        }
    }
}
