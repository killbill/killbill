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

package org.killbill.billing.util.dao;

import java.io.IOException;
import java.util.Date;
import java.util.UUID;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;

public class TestNonEntityDao extends UtilTestSuiteWithEmbeddedDB {

    final Long tenantRecordId = 123123123L;
    final UUID tenantId = UUID.fromString("121c59d4-0458-4038-a683-698c9a121c12");

    final UUID accountId = UUID.fromString("a01c59d4-0458-4038-a683-698c9a121c69");
    final Long accountRecordId = 333333L;

    final UUID tagDefinitionId = UUID.fromString("e01c59d4-0458-4038-a683-698c9a121c34");
    final Long tagDefinitionRecordId = 44444444L;

    final UUID tagId = UUID.fromString("123c59d4-0458-4038-a683-698c9a121456");
    final Long tagRecordId = 55555555L;

    @Test(groups = "slow")
    public void testRetrieveRecordIdFromObject() throws IOException {
        insertAccount();

        final Long resultRecordId = nonEntityDao.retrieveRecordIdFromObject(accountId, ObjectType.ACCOUNT, null);
        Assert.assertEquals(resultRecordId, accountRecordId);
    }

    @Test(groups = "slow")
    public void testRetrieveAccountRecordIdFromAccountObject() throws IOException {
        insertAccount();

        final Long resultAccountRecordId = nonEntityDao.retrieveAccountRecordIdFromObject(accountId, ObjectType.ACCOUNT, null);
        Assert.assertEquals(resultAccountRecordId, accountRecordId);
    }

    @Test(groups = "slow")
    public void testRetrieveAccountRecordIdFromTagDefinitionObject() throws IOException {
        insertTagDefinition();

        final Long resultAccountRecordId = nonEntityDao.retrieveAccountRecordIdFromObject(tagDefinitionId, ObjectType.TAG_DEFINITION, null);
        Assert.assertEquals(resultAccountRecordId, null);
    }

    // Not Tag_definition or account which are special
    @Test(groups = "slow")
    public void testRetrieveAccountRecordIdFromOtherObject() throws IOException {
        insertTag();

        final Long resultAccountRecordId = nonEntityDao.retrieveAccountRecordIdFromObject(tagId, ObjectType.TAG, null);
        Assert.assertEquals(resultAccountRecordId, accountRecordId);
    }

    @Test(groups = "slow")
    public void testRetrieveTenantRecordIdFromObject() throws IOException {
        insertAccount();

        final Long resultTenantRecordId = nonEntityDao.retrieveTenantRecordIdFromObject(accountId, ObjectType.ACCOUNT, null);
        Assert.assertEquals(resultTenantRecordId, tenantRecordId);
    }

    @Test(groups = "slow")
    public void testRetrieveTenantRecordIdFromTenantObject() throws IOException {
        insertTenant();

        final Long resultTenantRecordId = nonEntityDao.retrieveTenantRecordIdFromObject(tenantId, ObjectType.TENANT, null);
        Assert.assertEquals(resultTenantRecordId, tenantRecordId);
    }

    private void insertAccount() throws IOException {
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                // Note: we always create an accounts table, see MysqlTestingHelper
                handle.execute("insert into accounts (record_id, id, email, name, first_name_length, is_notified_for_invoices, created_date, created_by, updated_date, updated_by, tenant_record_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                               accountRecordId, accountId.toString(), "zozo@tt.com", "zozo", 4, false, new Date(), "i", new Date(), "j", tenantRecordId);
                return null;
            }
        });
    }

    private void insertHistoryAccount() throws IOException {
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                // Note: we always create an accounts table, see MysqlTestingHelper
                handle.execute("insert into account_history (record_id, id, email, name, first_name_length, is_notified_for_invoices, created_date, created_by, updated_date, updated_by, tenant_record_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                               accountRecordId, accountId.toString(), "zozo@tt.com", "zozo", 4, false, new Date(), "i", new Date(), "j", tenantRecordId);
                return null;
            }
        });
    }

    private void insertTagDefinition() throws IOException {
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                // Note: we always create an accounts table, see MysqlTestingHelper
                handle.execute("insert into tag_definitions (record_id, id, name, description, is_active, created_date, created_by, updated_date, updated_by, tenant_record_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                               tagDefinitionRecordId, tagDefinitionId.toString(), "tagdef", "nothing", true, new Date(), "i", new Date(), "j", 0);
                return null;
            }
        });
    }

    private void insertTag() throws IOException {
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                // Note: we always create an accounts table, see MysqlTestingHelper
                handle.execute("insert into tags (record_id, id, tag_definition_id, object_id, object_type, is_active, created_date, created_by, updated_date, updated_by, account_record_id, tenant_record_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                               tagRecordId, tagId.toString(), tagDefinitionId.toString(), accountId.toString(), "ACCOUNT", true, new Date(), "i", new Date(), "j", accountRecordId, 0);
                return null;
            }
        });
    }

    private void insertTenant() throws IOException {
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                // Note: we always create an accounts table, see MysqlTestingHelper
                handle.execute("insert into tenants (record_id, id, external_key, api_key, api_secret, api_salt, created_date, created_by, updated_date, updated_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                               tenantRecordId, tenantId.toString(), "foo", "key", "secret", "salt", new Date(), "i", new Date(), "j");
                return null;
            }
        });
    }
}
