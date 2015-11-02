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
import java.util.List;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.CustomField;
import org.killbill.billing.client.model.CustomFields;
import org.killbill.billing.client.model.PaymentMethod;
import org.killbill.billing.client.model.PaymentMethods;
import org.killbill.billing.client.model.PluginProperty;
import org.killbill.billing.util.api.AuditLevel;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestPaymentMethod extends TestJaxrsBase {

    @Test(groups = "slow", description = "Create/retrieve by externalKey")
    public void testGePaymentMethodsByKey() throws Exception {

        final Account accountJson = createAccountWithDefaultPaymentMethod("foo");

        final PaymentMethod paymentMethodJson1 = killBillClient.getPaymentMethodByKey("foo", true);

        final PaymentMethod paymentMethodJson2 = killBillClient.getPaymentMethod(accountJson.getPaymentMethodId(), true);
        Assert.assertEquals(paymentMethodJson1, paymentMethodJson2);

        final PaymentMethod paymentMethodJson3 = killBillClient.getPaymentMethodByKey("doesnotexist", true);
        Assert.assertNull(paymentMethodJson3);
    }

    @Test(groups = "slow", description = "Can search payment methods")
    public void testSearchPaymentMethods() throws Exception {
        // Search random key
        Assert.assertEquals(killBillClient.searchPaymentMethodsByKey(UUID.randomUUID().toString()).size(), 0);
        Assert.assertEquals(killBillClient.searchPaymentMethodsByKeyAndPlugin(UUID.randomUUID().toString(), PLUGIN_NAME).size(), 0);

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
        Assert.assertEquals(killBillClient.searchPaymentMethodsByKey(UUID.randomUUID().toString()).size(), 0);
        Assert.assertEquals(killBillClient.searchPaymentMethodsByKeyAndPlugin(UUID.randomUUID().toString(), PLUGIN_NAME).size(), 0);

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
        Assert.assertEquals(allPaymentMethods.size(), 5);

        PaymentMethods page = killBillClient.getPaymentMethods(0L, 1L);
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

        // Create custom field
        final CustomFields createdCustomFields = killBillClient.createPaymentMethodCustomField(paymentMethodId, customField, createdBy, reason, comment);
        Assert.assertEquals(createdCustomFields.size(), 1);
        final CustomField createdCustomField = createdCustomFields.get(0);
        Assert.assertEquals(createdCustomField.getName(), "testKey");
        Assert.assertEquals(createdCustomField.getValue(), "testValue");
        Assert.assertEquals(createdCustomField.getObjectId(), paymentMethodId);
        Assert.assertEquals(createdCustomField.getObjectType(), ObjectType.PAYMENT_METHOD);

        // Retrieve custom field
        final CustomFields retrievedCustomFields = killBillClient.getPaymentMethodCustomFields(paymentMethodId, AuditLevel.NONE);
        Assert.assertEquals(retrievedCustomFields.size(), 1);
        final CustomField retrievedCustomField = retrievedCustomFields.get(0);
        Assert.assertEquals(retrievedCustomField.getName(), "testKey");
        Assert.assertEquals(retrievedCustomField.getValue(), "testValue");
        Assert.assertEquals(retrievedCustomField.getObjectId(), paymentMethodId);
        Assert.assertEquals(retrievedCustomField.getObjectType(), ObjectType.PAYMENT_METHOD);

        // Delete custom field
        killBillClient.deletePaymentMethodCustomFields(paymentMethodId, Collections.<UUID>singletonList(createdCustomField.getCustomFieldId()), createdBy, reason, comment);
        final CustomFields deletedCustomFields = killBillClient.getPaymentMethodCustomFields(paymentMethodId, AuditLevel.NONE);
        Assert.assertEquals(deletedCustomFields.size(), 0);
    }

    private void doSearch(final String searchKey, final PaymentMethod paymentMethodJson) throws Exception {
        final List<PaymentMethod> results1 = killBillClient.searchPaymentMethodsByKey(searchKey, true);
        Assert.assertEquals(results1.size(), 1);
        Assert.assertEquals(results1.get(0), paymentMethodJson);

        final List<PaymentMethod> results2 = killBillClient.searchPaymentMethodsByKeyAndPlugin(searchKey, PLUGIN_NAME);
        Assert.assertEquals(results2.size(), 1);
        Assert.assertEquals(results2.get(0), paymentMethodJson);
    }
}
