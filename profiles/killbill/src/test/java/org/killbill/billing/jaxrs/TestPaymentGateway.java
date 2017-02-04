/*
 * Copyright 2015 Groupon, Inc
 * Copyright 2015 The Billing Project, LLC
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

import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.ComboHostedPaymentPage;
import org.killbill.billing.client.model.HostedPaymentPageFields;
import org.killbill.billing.client.model.HostedPaymentPageFormDescriptor;
import org.killbill.billing.client.model.PaymentMethod;
import org.killbill.billing.client.model.PaymentMethodPluginDetail;
import org.killbill.billing.client.model.PluginProperty;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.http.client.Response;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class TestPaymentGateway extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testBuildFormDescriptor() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();

        final HostedPaymentPageFields hppFields = new HostedPaymentPageFields();

        final HostedPaymentPageFormDescriptor hostedPaymentPageFormDescriptor = killBillClient.buildFormDescriptor(hppFields, account.getAccountId(), null, ImmutableMap.<String, String>of(), requestOptions);
        Assert.assertEquals(hostedPaymentPageFormDescriptor.getKbAccountId(), account.getAccountId());
    }

    @Test(groups = "slow")
    public void testComboBuildFormDescriptor() throws Exception {
        final Account account = getAccount();
        account.setAccountId(null);

        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        final PaymentMethod paymentMethod = new PaymentMethod(null, UUID.randomUUID().toString(), null, true, PLUGIN_NAME, info);

        final HostedPaymentPageFields hppFields = new HostedPaymentPageFields();

        final ComboHostedPaymentPage comboHostedPaymentPage = new ComboHostedPaymentPage(account, paymentMethod, ImmutableList.<PluginProperty>of(), hppFields);

        final HostedPaymentPageFormDescriptor hostedPaymentPageFormDescriptor = killBillClient.buildFormDescriptor(comboHostedPaymentPage, ImmutableMap.<String, String>of(), requestOptions);
        Assert.assertNotNull(hostedPaymentPageFormDescriptor.getKbAccountId());
    }

    @Test(groups = "slow")
    public void testProcessNotification() throws Exception {
        final Response response = killBillClient.processNotification("TOTO", PLUGIN_NAME, ImmutableMap.<String, String>of(), createdBy, reason, comment);
        Assert.assertEquals(response.getStatusCode(), 200);
    }
}
