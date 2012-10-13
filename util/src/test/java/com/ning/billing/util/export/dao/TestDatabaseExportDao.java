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

package com.ning.billing.util.export.dao;

import java.io.ByteArrayOutputStream;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;
import com.ning.billing.util.api.DatabaseExportOutputStream;
import com.ning.billing.util.validation.dao.DatabaseSchemaDao;

public class TestDatabaseExportDao extends UtilTestSuiteWithEmbeddedDB {

    private DatabaseExportDao dao;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final DatabaseSchemaDao databaseSchemaDao = new DatabaseSchemaDao(getMysqlTestingHelper().getDBI());
        dao = new DatabaseExportDao(databaseSchemaDao, getMysqlTestingHelper().getDBI());
    }

    @Test(groups = "slow")
    public void testExportSimpleData() throws Exception {
        // Empty database
        final String dump = getDump();
        Assert.assertEquals(dump, "");

        final String tableNameA = "test_database_export_dao_a";
        final String tableNameB = "test_database_export_dao_b";
        getMysqlTestingHelper().getDBI().withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                handle.execute("create table " + tableNameA + "(record_id int(11) unsigned not null auto_increment," +
                               "a_column char default 'a'," +
                               "account_record_id int(11) unsigned not null," +
                               "tenant_record_id int(11) unsigned default 0," +
                               "primary key(record_id)) engine=innodb;");
                handle.execute("create table " + tableNameB + "(record_id int(11) unsigned not null auto_increment," +
                               "b_column char default 'b'," +
                               "account_record_id int(11) unsigned not null," +
                               "tenant_record_id int(11) unsigned default 0," +
                               "primary key(record_id)) engine=innodb;");
                handle.execute("insert into " + tableNameA + " (account_record_id, tenant_record_id) values (?, ?)",
                               internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId());
                handle.execute("insert into " + tableNameB + " (account_record_id, tenant_record_id) values (?, ?)",
                               internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId());
                return null;
            }
        });

        // Verify new dump
        final String newDump = getDump();
        Assert.assertEquals(newDump, "-- " + tableNameA + " record_id,a_column,account_record_id,tenant_record_id\n" +
                                     "1,a," + internalCallContext.getAccountRecordId() + "," + internalCallContext.getTenantRecordId() + "\n" +
                                     "-- " + tableNameB + " record_id,b_column,account_record_id,tenant_record_id\n" +
                                     "1,b," + internalCallContext.getAccountRecordId() + "," + internalCallContext.getTenantRecordId() + "\n");
    }

    private String getDump() {
        final DatabaseExportOutputStream out = new CSVExportOutputStream(new ByteArrayOutputStream());
        dao.exportDataForAccount(out, internalCallContext);
        return out.toString();
    }
}
