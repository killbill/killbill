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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import com.google.common.base.Splitter;

public class ReportSpecification {

    private final List<String> pivotNamesToExclude = new ArrayList<String>();
    private final List<String> pivotNamesToInclude = new ArrayList<String>();

    private final String rawReportName;

    private String reportName;

    public ReportSpecification(final String rawReportName) {
        this.rawReportName = rawReportName;
        parseRawReportName();
    }

    public List<String> getPivotNamesToExclude() {
        return pivotNamesToExclude;
    }

    public List<String> getPivotNamesToInclude() {
        return pivotNamesToInclude;
    }

    public String getReportName() {
        return reportName;
    }

    private void parseRawReportName() {
        final boolean hasExcludes = rawReportName.contains("!");
        final boolean hasIncludes = rawReportName.contains("$");
        if (hasExcludes && hasIncludes) {
            throw new IllegalArgumentException();
        }

        // rawReportName is in the form payments_per_day!AUD!BRL or payments_per_day$USD$EUR (but not both!)
        final Iterator<String> reportIterator = Splitter.on(Pattern.compile("[\\!\\$]"))
                                                        .trimResults()
                                                        .omitEmptyStrings()
                                                        .split(rawReportName)
                                                        .iterator();
        boolean isFirst = true;
        while (reportIterator.hasNext()) {
            final String piece = reportIterator.next();

            if (isFirst) {
                reportName = piece;
            } else {
                if (hasExcludes) {
                    pivotNamesToExclude.add(piece);
                } else if (hasIncludes) {
                    pivotNamesToInclude.add(piece);
                } else {
                    throw new IllegalArgumentException();
                }
            }

            isFirst = false;
        }
    }
}
