/*
 * Copyright 2020-2025 Equinix, Inc
 * Copyright 2014-2025 The Billing Project, LLC
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

import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.api.DatabaseExportOutputStream;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.testng.annotations.BeforeMethod;

public class TestDatabaseExportDaoBase extends UtilTestSuiteWithEmbeddedDB {

    protected final String tableNameA = "test_database_export_dao_a";
    protected final String tableNameB = "test_database_export_dao_b";
    protected final String tableNameC = "aviate_catalog_a";

    @BeforeMethod
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        dropTables();
    }

    protected void dropTables() {

        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                handle.execute("drop table if exists " + tableNameA);
                handle.execute("drop table if exists " + tableNameB);
                handle.execute("drop table if exists " + tableNameC);
                return null;
            }
        });

    }

    protected String getDump(final UUID accountId, final UUID tenantId) {
        final DatabaseExportOutputStream out = new CSVExportOutputStream(new ByteArrayOutputStream());
        dao.exportDataForAccount(out, accountId, tenantId, internalCallContext);
        return out.toString();
    }
}
