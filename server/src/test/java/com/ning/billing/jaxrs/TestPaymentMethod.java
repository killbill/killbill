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

package com.ning.billing.jaxrs;

import java.util.List;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.client.model.Account;
import com.ning.billing.client.model.PaymentMethod;

public class TestPaymentMethod extends TestJaxrsBase {

    @Test(groups = "slow", description = "Can search payment methods")
    public void testSearchPaymentMethods() throws Exception {
        // Search random key
        Assert.assertEquals(killBillClient.searchPaymentMethodsByKey(UUID.randomUUID().toString()).size(), 0);
        Assert.assertEquals(killBillClient.searchPaymentMethodsByKeyAndPlugin(UUID.randomUUID().toString(), PLUGIN_NAME).size(), 0);

        // Create a payment method
        final Account accountJson = createAccountWithDefaultPaymentMethod();
        final PaymentMethod paymentMethodJson = killBillClient.getPaymentMethod(accountJson.getPaymentMethodId(), true);

        // Search random key again
        Assert.assertEquals(killBillClient.searchPaymentMethodsByKey(UUID.randomUUID().toString()).size(), 0);
        Assert.assertEquals(killBillClient.searchPaymentMethodsByKeyAndPlugin(UUID.randomUUID().toString(), PLUGIN_NAME).size(), 0);

        // Make sure we can search the test plugin
        // Values are hardcoded in TestPaymentMethodPluginBase and the search logic is in MockPaymentProviderPlugin
        doSearch("Foo", paymentMethodJson);
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

    private void doSearch(final String searchKey, final PaymentMethod paymentMethodJson) throws Exception {
        final List<PaymentMethod> results1 = killBillClient.searchPaymentMethodsByKey(searchKey);
        Assert.assertEquals(results1.size(), 1);
        Assert.assertEquals(results1.get(0), paymentMethodJson);

        final List<PaymentMethod> results2 = killBillClient.searchPaymentMethodsByKeyAndPlugin(searchKey, PLUGIN_NAME);
        Assert.assertEquals(results2.size(), 1);
        Assert.assertEquals(results2.get(0), paymentMethodJson);
    }
}
