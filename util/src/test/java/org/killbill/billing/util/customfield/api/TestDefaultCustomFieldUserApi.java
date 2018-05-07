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

package org.killbill.billing.util.customfield.api;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.customfield.StringCustomField;
import org.killbill.billing.util.entity.Pagination;
import org.mockito.Mockito;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestDefaultCustomFieldUserApi extends UtilTestSuiteWithEmbeddedDB {

    final UUID accountId = UUID.randomUUID();
    final Long accountRecordId = 19384012L;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        final ImmutableAccountData immutableAccountData = Mockito.mock(ImmutableAccountData.class);
        Mockito.when(immutableAccountInternalApi.getImmutableAccountDataByRecordId(Mockito.<Long>eq(accountRecordId), Mockito.<InternalTenantContext>any())).thenReturn(immutableAccountData);

        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                // Note: we always create an accounts table, see MysqlTestingHelper
                handle.execute("insert into accounts (record_id, id, external_key, email, name, first_name_length, reference_time, time_zone, is_notified_for_invoices, created_date, created_by, updated_date, updated_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                               accountRecordId, accountId.toString(), accountId.toString(), "yo@t.com", "toto", 4, new Date(), "UTC", false, new Date(), "i", new Date(), "j");

                return null;
            }
        });
    }

    @Test(groups = "slow")
    public void testCustomFieldNoId() throws Exception {
        // Verify that when coming from a plugin, the id isn't required
        final CustomField customField = Mockito.mock(CustomField.class);
        Mockito.when(customField.getObjectId()).thenReturn(accountId);
        Mockito.when(customField.getObjectType()).thenReturn(ObjectType.ACCOUNT);
        Mockito.when(customField.getFieldName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(customField.getFieldValue()).thenReturn(UUID.randomUUID().toString());

        eventsListener.pushExpectedEvents(NextEvent.CUSTOM_FIELD);
        customFieldUserApi.addCustomFields(ImmutableList.<CustomField>of(customField), callContext);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testCustomFieldBasic() throws Exception {

        final CustomField customField1 = new StringCustomField("some123", "some 456", ObjectType.ACCOUNT, accountId, callContext.getCreatedDate());
        final CustomField customField2 = new StringCustomField("other123", "other 456", ObjectType.ACCOUNT, accountId, callContext.getCreatedDate());
        eventsListener.pushExpectedEvents(NextEvent.CUSTOM_FIELD, NextEvent.CUSTOM_FIELD);
        customFieldUserApi.addCustomFields(ImmutableList.<CustomField>of(customField1, customField2), callContext);
        assertListenerStatus();

        // Verify operation is indeed transactional, and nothing was inserted
        final CustomField customField3 = new StringCustomField("qrqrq123", "qrqrq 456", ObjectType.ACCOUNT, accountId, callContext.getCreatedDate());
        try {
            customFieldUserApi.addCustomFields(ImmutableList.<CustomField>of(customField3, customField1), callContext);
        } catch (CustomFieldApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.CUSTOM_FIELD_ALREADY_EXISTS.getCode());
        }

        List<CustomField> all = customFieldUserApi.getCustomFieldsForAccount(accountId, callContext);
        Assert.assertEquals(all.size(), 2);

        eventsListener.pushExpectedEvent(NextEvent.CUSTOM_FIELD);
        customFieldUserApi.addCustomFields(ImmutableList.<CustomField>of(customField3), callContext);
        assertListenerStatus();

        all = customFieldUserApi.getCustomFieldsForAccount(accountId, callContext);
        Assert.assertEquals(all.size(), 3);

        eventsListener.pushExpectedEvents(NextEvent.CUSTOM_FIELD, NextEvent.CUSTOM_FIELD);
        customFieldUserApi.removeCustomFields(ImmutableList.of(customField1, customField3), callContext);
        assertListenerStatus();

        all = customFieldUserApi.getCustomFieldsForAccount(accountId, callContext);
        Assert.assertEquals(all.size(), 1);
        Assert.assertEquals(all.get(0).getId(), customField2.getId());
        Assert.assertEquals(all.get(0).getObjectId(), accountId);
        Assert.assertEquals(all.get(0).getObjectType(), ObjectType.ACCOUNT);
        Assert.assertEquals(all.get(0).getFieldName(), customField2.getFieldName());
        Assert.assertEquals(all.get(0).getFieldValue(), customField2.getFieldValue());
    }


    @Test(groups = "slow")
    public void testCustomFieldUpdate() throws Exception {

        final CustomField customField1 = new StringCustomField("gtqre", "value1", ObjectType.ACCOUNT, accountId, callContext.getCreatedDate());
        eventsListener.pushExpectedEvents(NextEvent.CUSTOM_FIELD);
        customFieldUserApi.addCustomFields(ImmutableList.<CustomField>of(customField1), callContext);
        assertListenerStatus();

        final CustomField update1 = new StringCustomField(customField1.getId(), customField1.getFieldName(), "value2", customField1.getObjectType(), customField1.getObjectId(), callContext.getCreatedDate());
        customFieldUserApi.updateCustomFields(ImmutableList.of(update1), callContext);

        List<CustomField> all = customFieldUserApi.getCustomFieldsForAccount(accountId, callContext);
        Assert.assertEquals(all.size(), 1);
        Assert.assertEquals(all.get(0).getId(), update1.getId());
        Assert.assertEquals(all.get(0).getObjectType(), update1.getObjectType());
        Assert.assertEquals(all.get(0).getObjectId(), update1.getObjectId());
        Assert.assertEquals(all.get(0).getFieldName(), update1.getFieldName());
        Assert.assertEquals(all.get(0).getFieldValue(), "value2");

        try {
            customFieldUserApi.updateCustomFields(ImmutableList.<CustomField>of(new StringCustomField("gtqre", "value1", ObjectType.ACCOUNT, accountId, callContext.getCreatedDate())), callContext);
            Assert.fail("Updating custom field should fail");
        } catch (final CustomFieldApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.CUSTOM_FIELD_DOES_NOT_EXISTS_FOR_ID.getCode());
        }

        try {
            customFieldUserApi.updateCustomFields(ImmutableList.<CustomField>of(new StringCustomField(customField1.getId(), "wrongName", "value2", customField1.getObjectType(), customField1.getObjectId(), callContext.getCreatedDate())), callContext);
            Assert.fail("Updating custom field should fail");
        } catch (final CustomFieldApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.CUSTOM_FIELD_INVALID_UPDATE.getCode());
        }

    }


        @Test(groups = "slow")
    public void testSaveCustomFieldWithAccountRecordId() throws Exception {

        checkPagination(0);

        final String cfName = UUID.randomUUID().toString().substring(1, 4);
        final String cfValue = UUID.randomUUID().toString().substring(1, 4);
        final CustomField customField = new StringCustomField(cfName, cfValue, ObjectType.ACCOUNT, accountId, callContext.getCreatedDate());
        eventsListener.pushExpectedEvent(NextEvent.CUSTOM_FIELD);
        customFieldUserApi.addCustomFields(ImmutableList.<CustomField>of(customField), callContext);
        assertListenerStatus();

        checkPagination(1);

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

        eventsListener.pushExpectedEvent(NextEvent.CUSTOM_FIELD);
        customFieldUserApi.removeCustomFields(customFields, callContext);
        assertListenerStatus();
        List<CustomField> remainingCustomFields = customFieldUserApi.getCustomFieldsForObject(accountId, ObjectType.ACCOUNT, callContext);
        Assert.assertEquals(remainingCustomFields.size(), 0);

        checkPagination(0);

        // Add again the custom field
        final CustomField newCustomField = new StringCustomField(cfName, cfValue, ObjectType.ACCOUNT, accountId, callContext.getCreatedDate());

        eventsListener.pushExpectedEvent(NextEvent.CUSTOM_FIELD);
        customFieldUserApi.addCustomFields(ImmutableList.<CustomField>of(newCustomField), callContext);
        assertListenerStatus();
        remainingCustomFields = customFieldUserApi.getCustomFieldsForObject(accountId, ObjectType.ACCOUNT, callContext);
        Assert.assertEquals(remainingCustomFields.size(), 1);

        checkPagination(1);

        // Delete again
        eventsListener.pushExpectedEvent(NextEvent.CUSTOM_FIELD);
        customFieldUserApi.removeCustomFields(remainingCustomFields, callContext);
        assertListenerStatus();
        remainingCustomFields = customFieldUserApi.getCustomFieldsForObject(accountId, ObjectType.ACCOUNT, callContext);
        Assert.assertEquals(remainingCustomFields.size(), 0);

        checkPagination(0);
    }

    private void checkPagination(final long nbRecords) {
        final Pagination<CustomField> foundCustomFields = customFieldUserApi.searchCustomFields("ACCOUNT", 0L, nbRecords + 1L, callContext);
        Assert.assertEquals(foundCustomFields.iterator().hasNext(), nbRecords > 0);
        Assert.assertEquals(foundCustomFields.getMaxNbRecords(), (Long) nbRecords);
        Assert.assertEquals(foundCustomFields.getTotalNbRecords(), (Long) nbRecords);

        final Pagination<CustomField> gotCustomFields = customFieldUserApi.getCustomFields(0L, nbRecords + 1L, callContext);
        Assert.assertEquals(gotCustomFields.iterator().hasNext(), nbRecords > 0);
        Assert.assertEquals(gotCustomFields.getMaxNbRecords(), (Long) nbRecords);
        Assert.assertEquals(gotCustomFields.getTotalNbRecords(), (Long) nbRecords);
    }
}
