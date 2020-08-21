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

package org.killbill.billing.util.dao;

import java.io.IOException;
import java.util.Date;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.Update;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.skife.jdbi.v2.util.LongMapper;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestNonEntityDao extends UtilTestSuiteWithEmbeddedDB {

    private final UUID tenantId = UUID.fromString("121c59d4-0458-4038-a683-698c9a121c12");
    private Long tenantRecordId;

    private final UUID accountId = UUID.fromString("a01c59d4-0458-4038-a683-698c9a121c69");
    private Long accountRecordId;

    private final UUID tagDefinitionId = UUID.fromString("e01c59d4-0458-4038-a683-698c9a121c34");

    private final UUID tagId = UUID.fromString("123c59d4-0458-4038-a683-698c9a121456");

    @BeforeMethod(groups = "slow")
    public void setUp() {
        if (hasFailed()) {
            return;
        }

        tenantRecordId = internalCallContext.getTenantRecordId();
    }

    @Test(groups = "slow")
    public void testRetrieveRecordIdFromObject() throws IOException {
        accountRecordId = generateAccountRecordId(accountId);

        final Long resultRecordId = nonEntityDao.retrieveRecordIdFromObject(accountId, ObjectType.ACCOUNT, null);
        Assert.assertEquals(resultRecordId, accountRecordId);
    }

    @Test(groups = "slow")
    public void testRetrieveAccountRecordIdFromAccountObject() throws IOException {
        accountRecordId = generateAccountRecordId(accountId);

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
        accountRecordId = generateAccountRecordId(accountId);

        final Long resultTenantRecordId = nonEntityDao.retrieveTenantRecordIdFromObject(accountId, ObjectType.ACCOUNT, null);
        Assert.assertEquals(resultTenantRecordId, tenantRecordId);
    }

    @Test(groups = "slow")
    public void testRetrieveTenantRecordIdFromTenantObject() throws IOException {
        insertTenant();

        final Long resultTenantRecordId = nonEntityDao.retrieveTenantRecordIdFromObject(tenantId, ObjectType.TENANT, null);
        Assert.assertEquals(resultTenantRecordId, tenantRecordId);
    }

    private void insertTagDefinition() throws IOException {
        dbi.withHandle(new HandleCallback<Long>() {
            @Override
            public Long withHandle(final Handle handle) throws Exception {
                // Note: we always create an accounts table, see MysqlTestingHelper
                return executeAndReturnGeneratedKeys(handle,
                                                     "insert into tag_definitions (id, name, description, is_active, created_date, created_by, updated_date, updated_by, tenant_record_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                                     tagDefinitionId.toString(), "tagdef", "nothing", true, new Date(), "i", new Date(), "j", 0);
            }
        });
    }

    private void insertTag() throws IOException {
        dbi.withHandle(new HandleCallback<Long>() {
            @Override
            public Long withHandle(final Handle handle) throws Exception {
                // Note: we always create an accounts table, see MysqlTestingHelper
                return executeAndReturnGeneratedKeys(handle,
                                                     "insert into tags (id, tag_definition_id, object_id, object_type, is_active, created_date, created_by, updated_date, updated_by, account_record_id, tenant_record_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                                     tagId.toString(), tagDefinitionId.toString(), accountId.toString(), "ACCOUNT", true, new Date(), "i", new Date(), "j", accountRecordId, 0);
            }
        });
    }

    private void insertTenant() throws IOException {
        tenantRecordId = dbi.withHandle(new HandleCallback<Long>() {
            @Override
            public Long withHandle(final Handle handle) throws Exception {
                // Note: we always create an accounts table, see MysqlTestingHelper
                return executeAndReturnGeneratedKeys(handle,
                                                     "insert into tenants (id, external_key, api_key, api_secret, api_salt, created_date, created_by, updated_date, updated_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                                     tenantId.toString(), "foo", "key", "secret", "salt", new Date(), "i", new Date(), "j");
            }
        });
    }

    private Long executeAndReturnGeneratedKeys(final Handle handle, final String sql, final Object... args) {
        final Update stmt = handle.createStatement(sql);
        int position = 0;
        for (final Object arg : args) {
            stmt.bind(position++, arg);
        }
        return stmt.executeAndReturnGeneratedKeys(new LongMapper(), "record_id").first();
    }
}
