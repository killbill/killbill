/*
 * Copyright 2020-2026 Equinix, Inc
 * Copyright 2014-2026 The Billing Project, LLC
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

import java.sql.Date;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestSearchQuery extends UtilTestSuiteNoDB {

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/2127")
    public void testDateColumnsAreBoundAsSqlTypes() {
        final SearchQuery searchQuery = new SearchQuery("_q=1&target_date[lte]=2025-08-28&created_date[gte]=2025-08-01T10:30:00.000Z&migrated=true",
                                                        Set.of("target_date", "created_date", "migrated"),
                                                        Map.of("target_date", LocalDate.class,
                                                               "created_date", DateTime.class,
                                                               "migrated", Boolean.class));

        final Map<String, Object> bindMap = searchQuery.getSearchKeysBindMap();
        Assert.assertEquals(bindMap.size(), 3);
        // PostgreSQL requires typed binds in date comparisons ("operator does not exist: date <= character varying")
        Assert.assertEquals(findByColumn(bindMap, "target_date"), Date.valueOf("2025-08-28"));
        Assert.assertEquals(findByColumn(bindMap, "created_date"),
                            new Timestamp(new DateTime(2025, 8, 1, 10, 30, 0, DateTimeZone.UTC).getMillis()));
        Assert.assertEquals(findByColumn(bindMap, "migrated"), Boolean.TRUE);
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/2127")
    public void testUnparseableDateFallsBackToStringBinding() {
        final SearchQuery searchQuery = new SearchQuery("_q=1&target_date[gte]=not-a-date",
                                                        Set.of("target_date"),
                                                        Map.of("target_date", LocalDate.class));

        Assert.assertEquals(findByColumn(searchQuery.getSearchKeysBindMap(), "target_date"), "not-a-date");
    }

    @Test(groups = "fast")
    public void testUndeclaredColumnsRemainStrings() {
        final SearchQuery searchQuery = new SearchQuery("_q=1&external_key=2025-08-28",
                                                        Set.of("external_key"),
                                                        Map.of());

        Assert.assertEquals(findByColumn(searchQuery.getSearchKeysBindMap(), "external_key"), "2025-08-28");
    }

    private static Object findByColumn(final Map<String, Object> bindMap, final String column) {
        return bindMap.entrySet()
                      .stream()
                      .filter(entry -> entry.getKey().startsWith("s_attr_" + column))
                      .map(Map.Entry::getValue)
                      .findFirst()
                      .orElse(null);
    }
}
