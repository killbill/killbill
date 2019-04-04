/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.util.export.dao;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.killbill.billing.util.api.ColumnInfo;
import org.killbill.billing.util.validation.DefaultColumnInfo;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class TestCSVExportOutputStream extends UtilTestSuiteNoDB {

    @Test(groups = "fast")
    public void testSimpleGenerator() throws Exception {
        final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        final CSVExportOutputStream out = new CSVExportOutputStream(delegate);

        // Create the schema
        final String tableName = UUID.randomUUID().toString();
        out.newTable(tableName,
                     ImmutableList.<ColumnInfo>of(
                             new DefaultColumnInfo(tableName, "first_name", 0L, 0L, true, 0L, "varchar"),
                             new DefaultColumnInfo(tableName, "last_name", 0L, 0L, true, 0L, "varchar"),
                             new DefaultColumnInfo(tableName, "age", 0L, 0L, true, 0L, "tinyint"))
                    );

        // Write some data
        out.write(ImmutableMap.<String, Object>of("first_name", "jean",
                                                  "last_name", "dupond",
                                                  "age", 35));
        // Don't assume "ordering"
        out.write(ImmutableMap.<String, Object>of("last_name", "dujardin",
                                                  "first_name", "jack",
                                                  "age", 40));
        out.write(ImmutableMap.<String, Object>of("age", 12,
                                                  "first_name", "pierre",
                                                  "last_name", "schmitt"));
        // Verify the numeric parsing
        out.write(ImmutableMap.<String, Object>of("first_name", "stephane",
                                                  "last_name", "dupont",
                                                  "age", "30"));

        // Verify special characters
        out.write(ImmutableMap.<String, Object>of("first_name", "Jørgen",
                                                  "last_name", "Jensen",
                                                  "age", 31));
        out.write(ImmutableMap.<String, Object>of("first_name", "a|B",
                                                  "last_name", "c||5",
                                                  "age", 44));
        out.write(ImmutableMap.<String, Object>of("first_name", "q\nw",
                                                  "last_name", "e\n\n.",
                                                  "age", 1));

        Assert.assertEquals(delegate.toString("UTF-8"), "-- " + tableName + " first_name|last_name|age\n" +
                                                        "jean|dupond|35\n" +
                                                        "jack|dujardin|40\n" +
                                                        "pierre|schmitt|12\n" +
                                                        "stephane|dupont|30\n" +
                                                        "Jørgen|Jensen|31\n" +
                                                        "a\\N{VERTICAL LINE}B|c\\N{VERTICAL LINE}\\N{VERTICAL LINE}5|44\n" +
                                                        "q\\N{LINE FEED}w|e\\N{LINE FEED}\\N{LINE FEED}.|1\n");
    }
}
