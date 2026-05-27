/*
 * Copyright 2020-2024 Equinix, Inc
 * Copyright 2014-2024 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.util.entity.dao;

import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestSearchQuery {

    // Regression for https://github.com/killbill/killbill/issues/2127:
    // before the fix the value of a date column was bound as a String, which made
    // PostgreSQL refuse "date <= varchar". The column-type map must produce a
    // LocalDate-typed bind value so the JDBC driver issues a typed DATE bind.
    @Test(groups = "fast")
    public void testDateColumnIsBoundAsLocalDate() {
        final SearchQuery searchQuery = new SearchQuery("_q=1&target_date[lte]=2025-08-28",
                                                        Set.of("target_date"),
                                                        Map.of("target_date", LocalDate.class));
        Assert.assertEquals(searchQuery.getSearchAttributes().size(), 1);
        final SearchAttribute attr = searchQuery.getSearchAttributes().get(0);
        Assert.assertEquals(attr.getColumn(), "target_date");
        Assert.assertEquals(attr.getOperator(), SqlOperator.LTE);

        final Object bound = searchQuery.getSearchKeysBindMap().get(attr.getBindingKey());
        Assert.assertTrue(bound instanceof LocalDate, "Expected LocalDate but got " + (bound == null ? "null" : bound.getClass()));
        Assert.assertEquals(bound, new LocalDate(2025, 8, 28));
    }

    @Test(groups = "fast")
    public void testDateTimeColumnFromIsoDateTime() {
        final SearchQuery searchQuery = new SearchQuery("_q=1&created_date[gte]=2025-08-28T12:34:56Z",
                                                        Set.of("created_date"),
                                                        Map.of("created_date", DateTime.class));
        Assert.assertEquals(searchQuery.getSearchAttributes().size(), 1);
        final SearchAttribute attr = searchQuery.getSearchAttributes().get(0);
        Assert.assertEquals(attr.getOperator(), SqlOperator.GTE);

        final Object bound = searchQuery.getSearchKeysBindMap().get(attr.getBindingKey());
        Assert.assertTrue(bound instanceof DateTime, "Expected DateTime but got " + (bound == null ? "null" : bound.getClass()));
        Assert.assertEquals(((DateTime) bound).withZone(DateTimeZone.UTC),
                            new DateTime(2025, 8, 28, 12, 34, 56, DateTimeZone.UTC));
    }

    @Test(groups = "fast")
    public void testDateTimeColumnFallsBackToDateOnlyValue() {
        // The user may filter a datetime column with a date-only value
        final SearchQuery searchQuery = new SearchQuery("_q=1&created_date[lte]=2025-08-28",
                                                        Set.of("created_date"),
                                                        Map.of("created_date", DateTime.class));
        final SearchAttribute attr = searchQuery.getSearchAttributes().get(0);
        final Object bound = searchQuery.getSearchKeysBindMap().get(attr.getBindingKey());
        Assert.assertTrue(bound instanceof DateTime, "Expected DateTime but got " + (bound == null ? "null" : bound.getClass()));
    }

    @Test(groups = "fast")
    public void testBooleanColumnStillBoundAsBoolean() {
        // Existing behaviour should not regress
        final SearchQuery searchQuery = new SearchQuery("_q=1&migrated=true",
                                                        Set.of("migrated"),
                                                        Map.of("migrated", Boolean.class));
        final SearchAttribute attr = searchQuery.getSearchAttributes().get(0);
        final Object bound = searchQuery.getSearchKeysBindMap().get(attr.getBindingKey());
        Assert.assertEquals(bound, Boolean.TRUE);
    }

    @Test(groups = "fast")
    public void testUntypedColumnStillBoundAsString() {
        final SearchQuery searchQuery = new SearchQuery("_q=1&currency=USD",
                                                        Set.of("currency"),
                                                        Map.of());
        final SearchAttribute attr = searchQuery.getSearchAttributes().get(0);
        final Object bound = searchQuery.getSearchKeysBindMap().get(attr.getBindingKey());
        Assert.assertEquals(bound, "USD");
    }
}
