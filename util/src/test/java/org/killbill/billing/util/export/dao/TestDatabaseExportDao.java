/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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
import java.util.Date;
import java.util.UUID;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.api.DatabaseExportOutputStream;
import org.killbill.billing.util.validation.dao.DatabaseSchemaDao;

import com.ning.compress.lzf.LZFEncoder;

public class TestDatabaseExportDao extends UtilTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testExportSimpleData() throws Exception {
        // Empty database
        final String dump = getDump();
        Assert.assertEquals(dump, "");

        final String accountId = UUID.randomUUID().toString();
        final String accountEmail = UUID.randomUUID().toString().substring(0, 4) + '@' + UUID.randomUUID().toString().substring(0, 4);
        final String accountName = UUID.randomUUID().toString().substring(0, 4);
        final int firstNameLength = 4;
        final boolean isNotifiedForInvoices = false;
        final Date createdDate = new Date(12421982000L);
        final String createdBy = UUID.randomUUID().toString().substring(0, 4);
        final Date updatedDate = new Date(382910622000L);
        final String updatedBy = UUID.randomUUID().toString().substring(0, 4);

        final byte[] properties = LZFEncoder.encode(new byte[] { 'c', 'a', 'f', 'e' });
        final String tableNameA = "test_database_export_dao_a";
        final String tableNameB = "test_database_export_dao_b";
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                handle.execute("drop table if exists " + tableNameA);
                handle.execute("create table " + tableNameA + "(record_id serial unique," +
                               "a_column char default 'a'," +
                               "blob_column mediumblob," +
                               "account_record_id bigint /*! unsigned */ not null," +
                               "tenant_record_id bigint /*! unsigned */ not null default 0," +
                               "primary key(record_id));");
                handle.execute("drop table if exists " + tableNameB);
                handle.execute("create table " + tableNameB + "(record_id serial unique," +
                               "b_column char default 'b'," +
                               "account_record_id bigint /*! unsigned */ not null," +
                               "tenant_record_id bigint /*! unsigned */ not null default 0," +
                               "primary key(record_id));");
                handle.execute("insert into " + tableNameA + " (blob_column, account_record_id, tenant_record_id) values (?, ?, ?)",
                               properties, internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId());
                handle.execute("insert into " + tableNameB + " (account_record_id, tenant_record_id) values (?, ?)",
                               internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId());

                // Add row in accounts table
                handle.execute("insert into accounts (record_id, id, email, name, first_name_length, is_notified_for_invoices, created_date, created_by, updated_date, updated_by, tenant_record_id) " +
                               "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                               internalCallContext.getAccountRecordId(), accountId, accountEmail, accountName, firstNameLength, isNotifiedForInvoices, createdDate, createdBy, updatedDate, updatedBy, internalCallContext.getTenantRecordId());
                return null;
            }
        });

        // Verify new dump
        final String newDump = getDump();
        Assert.assertEquals(newDump, "-- accounts record_id|id|external_key|email|name|first_name_length|currency|billing_cycle_day_local|payment_method_id|time_zone|locale|address1|address2|company_name|city|state_or_province|country|postal_code|phone|migrated|is_notified_for_invoices|created_date|created_by|updated_date|updated_by|tenant_record_id\n" +
                                     String.format("%s|%s||%s|%s|%s||||||||||||||false|%s|%s|%s|%s|%s|%s", internalCallContext.getAccountRecordId(), accountId, accountEmail, accountName, firstNameLength,
                                                   isNotifiedForInvoices, "1970-05-24T18:33:02.000+0000", createdBy, "1982-02-18T20:03:42.000+0000", updatedBy, internalCallContext.getTenantRecordId()) + "\n" +
                                     "-- " + tableNameA + " record_id|a_column|blob_column|account_record_id|tenant_record_id\n" +
                                     "1|a|WlYAAARjYWZl|" + internalCallContext.getAccountRecordId() + "|" + internalCallContext.getTenantRecordId() + "\n" +
                                     "-- " + tableNameB + " record_id|b_column|account_record_id|tenant_record_id\n" +
                                     "1|b|" + internalCallContext.getAccountRecordId() + "|" + internalCallContext.getTenantRecordId() + "\n");

    }

    private String getDump() {
        final DatabaseExportOutputStream out = new CSVExportOutputStream(new ByteArrayOutputStream());
        dao.exportDataForAccount(out, internalCallContext);
        return out.toString();
    }
}
