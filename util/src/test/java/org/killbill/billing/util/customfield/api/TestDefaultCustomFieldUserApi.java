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

package org.killbill.billing.util.customfield.api;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.ObjectType;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.customfield.StringCustomField;

import com.google.common.collect.ImmutableList;

public class TestDefaultCustomFieldUserApi extends UtilTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testSaveCustomFieldWithAccountRecordId() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final Long accountRecordId = 19384012L;

        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                // Note: we always create an accounts table, see MysqlTestingHelper
                handle.execute("insert into accounts (record_id, id, email, name, first_name_length, is_notified_for_invoices, created_date, created_by, updated_date, updated_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                               accountRecordId, accountId.toString(), "yo@t.com", "toto", 4, false, new Date(), "i", new Date(), "j");

                return null;
            }
        });

        final String cfName = UUID.randomUUID().toString().substring(1, 4);
        final String cfValue = UUID.randomUUID().toString().substring(1, 4);
        final CustomField customField = new StringCustomField(cfName, cfValue, ObjectType.ACCOUNT, accountId, callContext.getCreatedDate());
        eventsListener.pushExpectedEvent(NextEvent.CUSTOM_FIELD);
        customFieldUserApi.addCustomFields(ImmutableList.<CustomField>of(customField), callContext);
        assertListenerStatus();

        // Verify the field was saved
        final List<CustomField> customFields = customFieldUserApi.getCustomFieldsForObject(accountId, ObjectType.ACCOUNT, callContext);
        Assert.assertEquals(customFields.size(), 1);
        Assert.assertEquals(customFields.get(0).getFieldName(), customField.getFieldName());
        Assert.assertEquals(customFields.get(0).getFieldValue(), customField.getFieldValue());
        Assert.assertEquals(customFields.get(0).getObjectId(), customField.getObjectId());
        Assert.assertEquals(customFields.get(0).getObjectType(), customField.getObjectType());
        // Verify the account_record_id was populated
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                final List<Map<String, Object>> values = handle.select("select account_record_id from custom_fields where object_id = ?", accountId.toString());
                Assert.assertEquals(values.size(), 1);
                Assert.assertEquals(values.get(0).keySet().size(), 1);
                Assert.assertEquals(Long.valueOf(values.get(0).get("account_record_id").toString()), accountRecordId);
                return null;
            }
        });

        customFieldUserApi.removeCustomFields(customFields, callContext);
        List<CustomField> remainingCustomFields = customFieldUserApi.getCustomFieldsForObject(accountId, ObjectType.ACCOUNT, callContext);
        Assert.assertEquals(remainingCustomFields.size(), 0);

        // Add again the custom field
        final CustomField newCustomField = new StringCustomField(cfName, cfValue, ObjectType.ACCOUNT, accountId, callContext.getCreatedDate());

        eventsListener.pushExpectedEvent(NextEvent.CUSTOM_FIELD);
        customFieldUserApi.addCustomFields(ImmutableList.<CustomField>of(newCustomField), callContext);
        remainingCustomFields = customFieldUserApi.getCustomFieldsForObject(accountId, ObjectType.ACCOUNT, callContext);
        Assert.assertEquals(remainingCustomFields.size(), 1);

        // Delete again
        customFieldUserApi.removeCustomFields(remainingCustomFields, callContext);
        remainingCustomFields = customFieldUserApi.getCustomFieldsForObject(accountId, ObjectType.ACCOUNT, callContext);
        Assert.assertEquals(remainingCustomFields.size(), 0);

    }
}
