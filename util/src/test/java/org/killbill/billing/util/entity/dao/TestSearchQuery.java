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
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestSearchQuery {

    // Regression test for https://github.com/killbill/killbill/issues/2127.
    // When the search query references a DATE column on PostgreSQL, the bound
    // value must be a LocalDate (not a String), otherwise the database rejects
    // the comparison with "operator does not exist: date <= character varying".
    @Test(groups = "fast")
    public void testLocalDateColumnTypeIsParsed() {
        final SearchQuery searchQuery = new SearchQuery("_q=1&target_date[lte]=2025-08-28",
                                                        Set.of("target_date"),
                                                        Map.of("target_date", LocalDate.class));

        Assert.assertEquals(searchQuery.getSearchAttributes().size(), 1);
        final SearchAttribute attribute = searchQuery.getSearchAttributes().get(0);
        Assert.assertEquals(attribute.getColumn(), "target_date");
        Assert.assertEquals(attribute.getOperator(), SqlOperator.LTE);

        final Object boundValue = searchQuery.getSearchKeysBindMap().get(attribute.getBindingKey());
        Assert.assertTrue(boundValue instanceof LocalDate,
                          "Expected LocalDate but got " + (boundValue == null ? "null" : boundValue.getClass()));
        Assert.assertEquals(boundValue, new LocalDate(2025, 8, 28));
    }

    @Test(groups = "fast")
    public void testDateTimeColumnTypeIsParsed() {
        final SearchQuery searchQuery = new SearchQuery("_q=1&created_date[gte]=2025-08-28T10:15:30Z",
                                                        Set.of("created_date"),
                                                        Map.of("created_date", DateTime.class));

        Assert.assertEquals(searchQuery.getSearchAttributes().size(), 1);
        final SearchAttribute attribute = searchQuery.getSearchAttributes().get(0);
        final Object boundValue = searchQuery.getSearchKeysBindMap().get(attribute.getBindingKey());
        Assert.assertTrue(boundValue instanceof DateTime,
                          "Expected DateTime but got " + (boundValue == null ? "null" : boundValue.getClass()));
    }

    @Test(groups = "fast")
    public void testUnparseableDateFallsBackToString() {
        final SearchQuery searchQuery = new SearchQuery("_q=1&target_date=not-a-date",
                                                        Set.of("target_date"),
                                                        Map.of("target_date", LocalDate.class));

        Assert.assertEquals(searchQuery.getSearchAttributes().size(), 1);
        final SearchAttribute attribute = searchQuery.getSearchAttributes().get(0);
        final Object boundValue = searchQuery.getSearchKeysBindMap().get(attribute.getBindingKey());
        // We fall back to the raw string so that the search clause still surfaces
        // a JDBC-level type error rather than NPE'ing in the SearchQuery itself.
        Assert.assertEquals(boundValue, "not-a-date");
    }

    @Test(groups = "fast")
    public void testStringColumnTypeIsUnchanged() {
        final SearchQuery searchQuery = new SearchQuery("_q=1&status=COMMITTED",
                                                        Set.of("status"),
                                                        Map.of());

        final SearchAttribute attribute = searchQuery.getSearchAttributes().get(0);
        final Object boundValue = searchQuery.getSearchKeysBindMap().get(attribute.getBindingKey());
        Assert.assertEquals(boundValue, "COMMITTED");
    }
}
