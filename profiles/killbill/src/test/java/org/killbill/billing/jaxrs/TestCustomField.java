/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.ObjectType;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.CustomField;
import org.killbill.billing.client.model.CustomFields;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestCustomField extends TestJaxrsBase {

    @Test(groups = "slow", description = "Can paginate through all custom fields")
    public void testCustomFieldsPagination() throws Exception {
        final Account account = createAccount();
        for (int i = 0; i < 5; i++) {
            final CustomField customField = new CustomField();
            customField.setName(UUID.randomUUID().toString().substring(0, 5));
            customField.setValue(UUID.randomUUID().toString().substring(0, 5));
            killBillClient.createAccountCustomField(account.getAccountId(), customField, createdBy, reason, comment);
        }

        final CustomFields allCustomFields = killBillClient.getCustomFields();
        Assert.assertEquals(allCustomFields.size(), 5);

        CustomFields page = killBillClient.getCustomFields(0L, 1L);
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

        final CustomFields customFields = killBillClient.searchCustomFields(ObjectType.ACCOUNT.toString());
        Assert.assertEquals(customFields.size(), 5);
        Assert.assertEquals(customFields.getPaginationCurrentOffset(), 0);
        Assert.assertEquals(customFields.getPaginationTotalNbRecords(), 5);
        Assert.assertEquals(customFields.getPaginationMaxNbRecords(), 5);
    }

    private void doSearchCustomField(final String searchKey, @Nullable final CustomField expectedCustomField) throws KillBillClientException {
        final CustomFields customFields = killBillClient.searchCustomFields(searchKey);
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
