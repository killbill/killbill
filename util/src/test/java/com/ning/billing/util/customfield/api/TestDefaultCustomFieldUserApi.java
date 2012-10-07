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

package com.ning.billing.util.customfield.api;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.StringCustomField;
import com.ning.billing.util.customfield.dao.AuditedCustomFieldDao;
import com.ning.billing.util.customfield.dao.CustomFieldDao;
import com.ning.billing.util.dao.ObjectType;

import com.google.common.collect.ImmutableList;

public class TestDefaultCustomFieldUserApi extends UtilTestSuiteWithEmbeddedDB {

    private DefaultCustomFieldUserApi customFieldUserApi;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final InternalCallContextFactory internalCallContextFactory = new InternalCallContextFactory(getMysqlTestingHelper().getDBI(), new ClockMock());
        final CustomFieldDao customFieldDao = new AuditedCustomFieldDao(getMysqlTestingHelper().getDBI());
        customFieldUserApi = new DefaultCustomFieldUserApi(internalCallContextFactory, customFieldDao);
    }

    @Test(groups = "slow")
    public void testSaveCustomFieldWithAccountRecordId() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final Long accountRecordId = 19384012L;

        getMysqlTestingHelper().getDBI().withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                // Note: we always create an accounts table, see MysqlTestingHelper
                handle.execute("insert into accounts (record_id, id, email, name, first_name_length, is_notified_for_invoices, created_date, created_by, updated_date, updated_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        accountRecordId, accountId.toString(), "yo@t.com", "toto", 4, false, new Date(), "i", new Date(), "j");

                return null;
            }
        });

        final CustomField customField = new StringCustomField(UUID.randomUUID().toString().substring(1, 4), UUID.randomUUID().toString().substring(1, 4));
        customFieldUserApi.saveCustomFields(accountId, ObjectType.ACCOUNT, ImmutableList.<CustomField>of(customField), callContext);

        // Verify the field was saved
        final Map<String, CustomField> customFields = customFieldUserApi.getCustomFields(accountId, ObjectType.ACCOUNT, callContext);
        Assert.assertEquals(customFields.keySet().size(), 1);
        Assert.assertEquals(customFields.get(customField.getName()), customField);
        // Verify the account_record_id was populated
        getMysqlTestingHelper().getDBI().withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                final List<Map<String, Object>> values = handle.select("select account_record_id from custom_fields where object_id = ?", accountId.toString());
                Assert.assertEquals(values.size(), 1);
                Assert.assertEquals(values.get(0).keySet().size(), 1);
                Assert.assertEquals(values.get(0).get("account_record_id"), accountRecordId);
                return null;
            }
        });
    }
}
