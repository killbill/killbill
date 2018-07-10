/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.client.model.CustomFields;
import org.killbill.billing.client.model.PaymentMethods;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.AuditLog;
import org.killbill.billing.client.model.gen.CustomField;
import org.killbill.billing.client.model.gen.PaymentMethod;
import org.killbill.billing.client.model.gen.PluginProperty;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.ChangeType;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestPaymentMethod extends TestJaxrsBase {

    @Test(groups = "slow", description = "Create/retrieve by externalKey")
    public void testGePaymentMethodsByKey() throws Exception {

        final Account accountJson = createAccountWithDefaultPaymentMethod("foo");

        final PaymentMethod paymentMethodJson1 = paymentMethodApi.getPaymentMethodByKey("foo", NULL_PLUGIN_PROPERTIES, requestOptions);

        final PaymentMethod paymentMethodJson2 = paymentMethodApi.getPaymentMethod(accountJson.getPaymentMethodId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(paymentMethodJson1, paymentMethodJson2);

        final PaymentMethod paymentMethodJson3 = paymentMethodApi.getPaymentMethodByKey("doesnotexist", NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertNull(paymentMethodJson3);
    }

    @Test(groups = "slow", description = "Can search payment methods")
    public void testSearchPaymentMethods() throws Exception {
        // Search random key
        Assert.assertEquals(paymentMethodApi.searchPaymentMethods(UUID.randomUUID().toString(), null, NULL_PLUGIN_PROPERTIES, requestOptions).size(), 0);
        Assert.assertEquals(paymentMethodApi.searchPaymentMethods(UUID.randomUUID().toString(), PLUGIN_NAME, NULL_PLUGIN_PROPERTIES, requestOptions).size(), 0);

        // Create a payment method
        final List<PluginProperty> pmProperties = new ArrayList<PluginProperty>();
        pmProperties.add(new PluginProperty("CC_NAME", "Bozo", false));
        pmProperties.add(new PluginProperty("CC_CITY", "SF", false));
        pmProperties.add(new PluginProperty("CC_LAST_4", "4365", false));
        pmProperties.add(new PluginProperty("CC_STATE", "CA", false));
        pmProperties.add(new PluginProperty("CC_COUNTRY", "Zimbawe", false));

        final Account accountJson = createAccountWithDefaultPaymentMethod(UUID.randomUUID().toString(), pmProperties);
        final PaymentMethod paymentMethodJson = paymentMethodApi.getPaymentMethod(accountJson.getPaymentMethodId(), false, true, NULL_PLUGIN_PROPERTIES, AuditLevel.NONE, requestOptions);

        // Search random key again
        Assert.assertEquals(paymentMethodApi.searchPaymentMethods(UUID.randomUUID().toString(), null, NULL_PLUGIN_PROPERTIES, requestOptions).size(), 0);
        Assert.assertEquals(paymentMethodApi.searchPaymentMethods(UUID.randomUUID().toString(), PLUGIN_NAME, NULL_PLUGIN_PROPERTIES, requestOptions).size(), 0);

        // Last 4
        doSearch("4365", paymentMethodJson);
        // Name
        doSearch("Bozo", paymentMethodJson);
        // City
        doSearch("SF", paymentMethodJson);
        // State
        doSearch("CA", paymentMethodJson);
        // Country
        doSearch("Zimbawe", paymentMethodJson);
    }

    @Test(groups = "slow", description = "Can paginate through all payment methods")
    public void testPaymentMethodsPagination() throws Exception {
        for (int i = 0; i < 5; i++) {
            createAccountWithDefaultPaymentMethod();
        }

        final PaymentMethods allPaymentMethods = paymentMethodApi.getPaymentMethods(null, NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(allPaymentMethods.size(), 5);

        PaymentMethods page = paymentMethodApi.getPaymentMethods(0L, 1L, null, false, NULL_PLUGIN_PROPERTIES, AuditLevel.NONE, requestOptions);
        for (int i = 0; i < 5; i++) {
            Assert.assertNotNull(page);
            Assert.assertEquals(page.size(), 1);
            Assert.assertEquals(page.get(0), allPaymentMethods.get(i));
            page = page.getNext();
        }
        Assert.assertNull(page);
    }

    @Test(groups = "slow", description = "Can create, retrieve and delete custom fields")
    public void testPaymentMethodCustomFields() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();
        final UUID paymentMethodId = account.getPaymentMethodId();

        final CustomField customField = new CustomField();
        customField.setObjectId(paymentMethodId);
        customField.setObjectType(ObjectType.PAYMENT_METHOD);
        customField.setName("testKey");
        customField.setValue("testValue");
        final CustomFields body = new CustomFields();
        body.add(customField);

        // Create custom field
        final CustomFields createdCustomFields = paymentMethodApi.createPaymentMethodCustomFields(paymentMethodId, body, requestOptions);
        Assert.assertEquals(createdCustomFields.size(), 1);
        final CustomField createdCustomField = createdCustomFields.get(0);
        Assert.assertEquals(createdCustomField.getName(), "testKey");
        Assert.assertEquals(createdCustomField.getValue(), "testValue");
        Assert.assertEquals(createdCustomField.getObjectId(), paymentMethodId);
        Assert.assertEquals(createdCustomField.getObjectType(), ObjectType.PAYMENT_METHOD);

        // Retrieve custom field
        final CustomFields retrievedCustomFields = paymentMethodApi.getPaymentMethodCustomFields(paymentMethodId, requestOptions);
        Assert.assertEquals(retrievedCustomFields.size(), 1);
        final CustomField retrievedCustomField = retrievedCustomFields.get(0);
        Assert.assertEquals(retrievedCustomField.getName(), "testKey");
        Assert.assertEquals(retrievedCustomField.getValue(), "testValue");
        Assert.assertEquals(retrievedCustomField.getObjectId(), paymentMethodId);
        Assert.assertEquals(retrievedCustomField.getObjectType(), ObjectType.PAYMENT_METHOD);

        // Delete custom field
        paymentMethodApi.deletePaymentMethodCustomFields(paymentMethodId, Collections.<UUID>singletonList(createdCustomField.getCustomFieldId()), requestOptions);
        final CustomFields deletedCustomFields = paymentMethodApi.getPaymentMethodCustomFields(paymentMethodId, requestOptions);
        Assert.assertEquals(deletedCustomFields.size(), 0);
    }

    @Test(groups = "slow", description = "retrieve account logs")
    public void testPaymentMethodAuditLogsWithHistory() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();
        assertNotNull(account);
        final UUID paymentMethodId = account.getPaymentMethodId();
        final List<AuditLog> paymentMethodAuditLogWithHistory = paymentMethodApi.getPaymentMethodAuditLogsWithHistory(paymentMethodId, requestOptions);
        assertEquals(paymentMethodAuditLogWithHistory.size(), 1);
        assertEquals(paymentMethodAuditLogWithHistory.get(0).getChangeType(), ChangeType.INSERT.toString());
        assertEquals(paymentMethodAuditLogWithHistory.get(0).getObjectType(), ObjectType.PAYMENT_METHOD);
        assertEquals(paymentMethodAuditLogWithHistory.get(0).getObjectId(), paymentMethodId);

        final LinkedHashMap history1 = (LinkedHashMap) paymentMethodAuditLogWithHistory.get(0).getHistory();
        assertNotNull(history1);
        assertEquals(history1.get("accountId"), account.getAccountId().toString());
    }

    private void doSearch(final String searchKey, final PaymentMethod paymentMethodJson) throws Exception {
        final List<PaymentMethod> results1 = paymentMethodApi.searchPaymentMethods(searchKey, 0L, 100L, null, true, NULL_PLUGIN_PROPERTIES,  AuditLevel.NONE,  requestOptions);
        Assert.assertEquals(results1.size(), 1);
        Assert.assertEquals(results1.get(0), paymentMethodJson);

        final List<PaymentMethod> results2 = paymentMethodApi.searchPaymentMethods(searchKey, 0L, 100L, PLUGIN_NAME, true, NULL_PLUGIN_PROPERTIES, AuditLevel.NONE, requestOptions);
        Assert.assertEquals(results2.size(), 1);
        Assert.assertEquals(results2.get(0), paymentMethodJson);
    }
}
