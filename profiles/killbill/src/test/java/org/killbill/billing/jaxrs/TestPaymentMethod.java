/*
 * Copyright 2010-2013 Ning, Inc.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.CustomField;
import org.killbill.billing.client.model.CustomFields;
import org.killbill.billing.client.model.PaymentMethod;
import org.killbill.billing.client.model.PaymentMethods;
import org.killbill.billing.client.model.PluginProperty;
import org.killbill.billing.util.api.AuditLevel;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestPaymentMethod extends TestJaxrsBase {

    @Test(groups = "slow", description = "Create/retrieve by externalKey")
    public void testGePaymentMethodsByKey() throws Exception {

        final Account accountJson = createAccountWithDefaultPaymentMethod("foo");

        final PaymentMethod paymentMethodJson1 = killBillClient.getPaymentMethodByKey("foo", true);

        final PaymentMethod paymentMethodJson2 = killBillClient.getPaymentMethod(accountJson.getPaymentMethodId(), true);
        assertEquals(paymentMethodJson1, paymentMethodJson2);

        final PaymentMethod paymentMethodJson3 = killBillClient.getPaymentMethodByKey("doesnotexist", true);
        Assert.assertNull(paymentMethodJson3);
    }

    @Test(groups = "slow", description = "Can search payment methods")
    public void testSearchPaymentMethods() throws Exception {
        // Search random key
        assertEquals(killBillClient.searchPaymentMethodsByKey(UUID.randomUUID().toString()).size(), 0);
        assertEquals(killBillClient.searchPaymentMethodsByKeyAndPlugin(UUID.randomUUID().toString(), PLUGIN_NAME).size(), 0);

        // Create a payment method
        final List<PluginProperty> pmProperties = new ArrayList<PluginProperty>();
        pmProperties.add(new PluginProperty("CC_NAME", "Bozo", false));
        pmProperties.add(new PluginProperty("CC_CITY", "SF", false));
        pmProperties.add(new PluginProperty("CC_LAST_4", "4365", false));
        pmProperties.add(new PluginProperty("CC_STATE", "CA", false));
        pmProperties.add(new PluginProperty("CC_COUNTRY", "Zimbawe", false));

        final Account accountJson = createAccountWithDefaultPaymentMethod(UUID.randomUUID().toString(), pmProperties);
        final PaymentMethod paymentMethodJson = killBillClient.getPaymentMethod(accountJson.getPaymentMethodId(), true);

        // Search random key again
        assertEquals(killBillClient.searchPaymentMethodsByKey(UUID.randomUUID().toString()).size(), 0);
        assertEquals(killBillClient.searchPaymentMethodsByKeyAndPlugin(UUID.randomUUID().toString(), PLUGIN_NAME).size(), 0);

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

        final PaymentMethods allPaymentMethods = killBillClient.getPaymentMethods();
        assertEquals(allPaymentMethods.size(), 5);

        PaymentMethods page = killBillClient.getPaymentMethods(0L, 1L);
        for (int i = 0; i < 5; i++) {
            Assert.assertNotNull(page);
            assertEquals(page.size(), 1);
            assertEquals(page.get(0), allPaymentMethods.get(i));
            page = page.getNext();
        }
        Assert.assertNull(page);
    }

    @Test(groups = "slow", description = "Can create, retrieve and delete custom fields")
    public void testPaymentMethodCustomFields() throws Exception {
        Account account = createAccountWithDefaultPaymentMethod();
        UUID paymentMethodId = account.getPaymentMethodId();

        // create custom field
        CustomField customField = createCustomFieldJson(paymentMethodId, ObjectType.PAYMENT_METHOD, "testKey", "testValue");
        CustomFields createdCustomFields = killBillClient.createPaymentMethodCustomField(paymentMethodId,customField, createdBy, reason, comment);
        assertEquals(createdCustomFields.size(), 1);
        CustomField createdCustomField = createdCustomFields.get(0);
        assertEquals(createdCustomField.getName(), "testKey");
        assertEquals(createdCustomField.getValue(), "testValue");
        assertEquals(createdCustomField.getObjectId(), paymentMethodId);
        assertEquals(createdCustomField.getObjectType(), ObjectType.PAYMENT_METHOD);

        // retrieve custom field
        CustomFields retrievedCustomFields = killBillClient.getPaymentMethodCustomFields(paymentMethodId, AuditLevel.NONE);
        assertEquals(retrievedCustomFields.size(), 1);
        CustomField retrievedCustomField = retrievedCustomFields.get(0);
        assertEquals(retrievedCustomField.getName(), "testKey");
        assertEquals(retrievedCustomField.getValue(), "testValue");
        assertEquals(retrievedCustomField.getObjectId(), paymentMethodId);
        assertEquals(retrievedCustomField.getObjectType(), ObjectType.PAYMENT_METHOD);

        // delete custom field
        killBillClient.deletePaymentMethodCustomFields(paymentMethodId, Arrays.asList(createdCustomField.getCustomFieldId()), createdBy, reason, comment);
        CustomFields deletedCustomFields = killBillClient.getPaymentMethodCustomFields(paymentMethodId, AuditLevel.NONE);
        assertEquals(deletedCustomFields.size(), 0);
    }

    private CustomField createCustomFieldJson(final UUID objectId, final ObjectType objectType, final String name, final String value) {
        return new CustomField() {
            {
                setObjectId(objectId);
                setObjectType(objectType);
                setName(name);
                setValue(value);
            }
        };
    }

    private CustomField createCustomField(final UUID paymentMethodId, final String name, final String value) throws KillBillClientException {
        CustomField customField = createCustomFieldJson(paymentMethodId, ObjectType.PAYMENT_METHOD, name, value);
        CustomFields customFields = killBillClient.createPaymentMethodCustomField(paymentMethodId,customField, createdBy, reason, comment);
        assertEquals(customFields.size(), 1);
        return customFields.get(0);
    }

    private void doSearch(final String searchKey, final PaymentMethod paymentMethodJson) throws Exception {
        final List<PaymentMethod> results1 = killBillClient.searchPaymentMethodsByKey(searchKey, true);
        assertEquals(results1.size(), 1);
        assertEquals(results1.get(0), paymentMethodJson);

        final List<PaymentMethod> results2 = killBillClient.searchPaymentMethodsByKeyAndPlugin(searchKey, PLUGIN_NAME);
        assertEquals(results2.size(), 1);
        assertEquals(results2.get(0), paymentMethodJson);
    }
}
