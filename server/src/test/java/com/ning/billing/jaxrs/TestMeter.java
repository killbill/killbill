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

package com.ning.billing.jaxrs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;

public class TestMeter extends TestJaxrsBase {

    private final Random rand = new Random();
    private final UUID bundleId = UUID.randomUUID();
    private final String category = "PageView";
    private final String visitor1 = "pierre";
    private final String visitor2 = "stephane";
    private final int nbVisits = 20;

    private final Ordering<DateTime> dateTimeOrdering = new Ordering<DateTime>() {

        @Override
        public int compare(final DateTime left, final DateTime right) {
            return left.compareTo(right);
        }
    };

    @Test(groups = "slow")
    public void testRecordPageViews() throws Exception {
        // Record a bunch of random visits by two visitors
        final DateTime start = clock.getUTCNow();
        final List<DateTime> visits = generatePageViews(nbVisits, start);
        final DateTime end = dateTimeOrdering.max(visits);

        // Verify the visits recorded
        final List<Map<String, Object>> meteringAggregateUsage = getMeteringAggregateUsage(bundleId, category, start, end);
        final List<DateTime> visitsFound = new ArrayList<DateTime>();
        for (final Map<String, Object> oneUsage : meteringAggregateUsage) {
            Assert.assertEquals(oneUsage.get("sourceName"), bundleId.toString());
            Assert.assertEquals(oneUsage.get("eventCategory"), category);
            Assert.assertEquals(oneUsage.get("metric"), "__AGGREGATE__");

            // Retrieve the timestamps
            final ImmutableList<String> samples = ImmutableList.<String>copyOf(Splitter.on(",").split((String) oneUsage.get("samples")));
            for (int i = 0; i < samples.size(); i++) {
                visitsFound.add(new DateTime(Long.valueOf(samples.get(i)) * 1000, DateTimeZone.UTC));
                i++;
            }
        }

        Assert.assertEquals(dateTimeOrdering.immutableSortedCopy(visitsFound), dateTimeOrdering.immutableSortedCopy(visits));
    }

    private List<DateTime> generatePageViews(final int nbVisits, final DateTime start) throws IOException {
        DateTime lastVisit = start;
        final List<DateTime> visits = new ArrayList<DateTime>();
        for (int i = 0; i < nbVisits; i++) {
            DateTime visitDate = lastVisit.plusSeconds(i);
            recordMeteringUsage(bundleId, category, visitor1, visitDate);
            visits.add(visitDate);

            visitDate = visitDate.plusSeconds(1);
            recordMeteringUsage(bundleId, category, visitor2, visitDate);
            visits.add(visitDate);

            lastVisit = visitDate;
        }

        return visits;
    }
}
