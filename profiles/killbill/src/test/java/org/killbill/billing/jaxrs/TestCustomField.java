/*
 * Copyright 2010-2014 Ning, Inc.
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

package org.killbill.billing.jaxrs;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.ObjectType;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.CustomFields;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.AuditLog;
import org.killbill.billing.client.model.gen.CustomField;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.ChangeType;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestCustomField extends TestJaxrsBase {

    @Test(groups = "slow", description = "Can create/modify/delete custom fields")
    public void testBasicCustomFields() throws Exception {
        final Account account = createAccount();
        final CustomField customField = new CustomField();
        customField.setName("MyName");
        customField.setValue("InitialValue");
        final CustomFields input = new CustomFields();
        input.add(customField);
        accountApi.createAccountCustomFields(account.getAccountId(), input, requestOptions);

        CustomFields allCustomFields = accountApi.getAccountCustomFields(account.getAccountId(), requestOptions);
        Assert.assertEquals(allCustomFields.size(), 1);
        Assert.assertEquals(allCustomFields.get(0).getName(), "MyName");
        Assert.assertEquals(allCustomFields.get(0).getValue(), "InitialValue");

        final CustomField customFieldModified = new CustomField();
        customFieldModified.setCustomFieldId(allCustomFields.get(0).getCustomFieldId());
        customFieldModified.setValue("NewValue");
        input.clear();
        input.add(customFieldModified);
        accountApi.modifyAccountCustomFields(account.getAccountId(), input, requestOptions);

        allCustomFields = accountApi.getAccountCustomFields(account.getAccountId(), requestOptions);
        Assert.assertEquals(allCustomFields.size(), 1);
        Assert.assertEquals(allCustomFields.get(0).getName(), "MyName");
        Assert.assertEquals(allCustomFields.get(0).getValue(), "NewValue");

        accountApi.deleteAccountCustomFields(account.getAccountId(), ImmutableList.<UUID>of(allCustomFields.get(0).getCustomFieldId()), requestOptions);
        allCustomFields = accountApi.getAccountCustomFields(account.getAccountId(), requestOptions);
        Assert.assertEquals(allCustomFields.size(), 0);
    }

    @Test(groups = "slow", description = "Can paginate through all custom fields")
    public void testCustomFieldsPagination() throws Exception {
        final Account account = createAccount();

        final CustomFields input = new CustomFields();

        for (int i = 0; i < 5; i++) {
            final CustomField customField = new CustomField();
            customField.setName(UUID.randomUUID().toString().substring(0, 5));
            customField.setValue(UUID.randomUUID().toString().substring(0, 5));
            input.add(customField);
        }
        accountApi.createAccountCustomFields(account.getAccountId(), input, requestOptions);

        final CustomFields allCustomFields = accountApi.getAccountCustomFields(account.getAccountId(), requestOptions);
        Assert.assertEquals(allCustomFields.size(), 5);

        CustomFields page = customFieldApi.getCustomFields(0L, 1L, AuditLevel.NONE, requestOptions);
        for (int i = 0; i < 5; i++) {
            Assert.assertNotNull(page);
            Assert.assertEquals(page.size(), 1);
            Assert.assertEquals(page.get(0), allCustomFields.get(i));
            page = page.getNext();
        }
        Assert.assertNull(page);

        for (final CustomField customField : allCustomFields) {
            doSearchCustomField(UUID.randomUUID().toString(), null);
            doSearchCustomField(customField.getName(), customField);
            doSearchCustomField(customField.getValue(), customField);
        }

        final CustomFields customFields = customFieldApi.searchCustomFields(ObjectType.ACCOUNT.toString(), requestOptions);
        Assert.assertEquals(customFields.size(), 5);
        Assert.assertEquals(customFields.getPaginationCurrentOffset(), 0);
        Assert.assertEquals(customFields.getPaginationTotalNbRecords(), 5);
        Assert.assertEquals(customFields.getPaginationMaxNbRecords(), 5);

        final CustomFields allAccountCustomFields = accountApi.getAllCustomFields(account.getAccountId(), null, AuditLevel.FULL, requestOptions);
        Assert.assertEquals(allAccountCustomFields.size(), 5);

        final CustomFields allBundleCustomFieldsForAccount = accountApi.getAllCustomFields(account.getAccountId(), ObjectType.ACCOUNT, AuditLevel.FULL, requestOptions);
        Assert.assertEquals(allBundleCustomFieldsForAccount.size(), 5);
    }

    @Test(groups = "slow", description = "retrieve account logs")
    public void testCustomFieldTagAuditLogsWithHistory() throws Exception {
        final Account accountJson = createAccount();
        assertNotNull(accountJson);

        final CustomField customField = new CustomField();
        customField.setName("custom");
        customField.setValue(UUID.randomUUID().toString().substring(0, 5));
        final CustomFields body = new CustomFields();
        body.add(customField);

        CustomFields result = accountApi.createAccountCustomFields(accountJson.getAccountId(), body, requestOptions);

        // get all audit for the account
        final List<AuditLog> auditLogsJson = accountApi.getAccountAuditLogs(accountJson.getAccountId(), requestOptions);
        Assert.assertEquals(auditLogsJson.size(), 2);
        UUID objectId = null;
        for (AuditLog auditLog : auditLogsJson) {
            if (auditLog.getObjectType().equals(ObjectType.CUSTOM_FIELD)) {
                objectId = auditLog.getObjectId();
                break;
            }
        }
        assertNotNull(objectId);
        final List<AuditLog> customFieldAuditLogWithHistory = customFieldApi.getCustomFieldAuditLogsWithHistory(result.get(0).getCustomFieldId(), requestOptions);
        assertEquals(customFieldAuditLogWithHistory.size(), 1);
        assertEquals(customFieldAuditLogWithHistory.get(0).getChangeType(), ChangeType.INSERT.toString());
        assertEquals(customFieldAuditLogWithHistory.get(0).getObjectType(), ObjectType.CUSTOM_FIELD);
        assertEquals(customFieldAuditLogWithHistory.get(0).getObjectId(), objectId);

        final LinkedHashMap history1 = (LinkedHashMap) customFieldAuditLogWithHistory.get(0).getHistory();
        assertNotNull(history1);
        assertEquals(history1.get("fieldName"), "custom");
    }

    private void doSearchCustomField(final String searchKey, @Nullable final CustomField expectedCustomField) throws KillBillClientException {
        final CustomFields customFields = customFieldApi.searchCustomFields(searchKey, requestOptions);
        if (expectedCustomField == null) {
            Assert.assertTrue(customFields.isEmpty());
            Assert.assertEquals(customFields.getPaginationCurrentOffset(), 0);
            Assert.assertEquals(customFields.getPaginationTotalNbRecords(), 0);
            Assert.assertEquals(customFields.getPaginationMaxNbRecords(), 5);
        } else {
            Assert.assertEquals(customFields.size(), 1);
            Assert.assertEquals(customFields.get(0), expectedCustomField);
            Assert.assertEquals(customFields.getPaginationCurrentOffset(), 0);
            Assert.assertEquals(customFields.getPaginationTotalNbRecords(), 1);
            Assert.assertEquals(customFields.getPaginationMaxNbRecords(), 5);
        }
    }
}
