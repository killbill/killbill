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

import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.ComboHostedPaymentPage;
import org.killbill.billing.client.model.gen.HostedPaymentPageFields;
import org.killbill.billing.client.model.gen.HostedPaymentPageFormDescriptor;
import org.killbill.billing.client.model.gen.PaymentMethod;
import org.killbill.billing.client.model.gen.PaymentMethodPluginDetail;
import org.killbill.billing.client.model.gen.PluginProperty;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestPaymentGateway extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testBuildFormDescriptor() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();

        final HostedPaymentPageFields hppFields = new HostedPaymentPageFields();

        final HostedPaymentPageFormDescriptor hostedPaymentPageFormDescriptor = paymentGatewayApi.buildFormDescriptor(account.getAccountId(), hppFields, null, NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(hostedPaymentPageFormDescriptor.getKbAccountId(), account.getAccountId());
    }

    @Test(groups = "slow")
    public void testComboBuildFormDescriptor() throws Exception {
        final Account account = getAccount();
        account.setAccountId(null);

        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        final PaymentMethod paymentMethod = new PaymentMethod(null, UUID.randomUUID().toString(), null, true, PLUGIN_NAME, info, null);

        final HostedPaymentPageFields hppFields = new HostedPaymentPageFields();

        final ComboHostedPaymentPage comboHostedPaymentPage = new ComboHostedPaymentPage(account, paymentMethod, hppFields, ImmutableList.<PluginProperty>of(), null);

        final HostedPaymentPageFormDescriptor hostedPaymentPageFormDescriptor = paymentGatewayApi.buildComboFormDescriptor(comboHostedPaymentPage, NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertNotNull(hostedPaymentPageFormDescriptor.getKbAccountId());
    }

    @Test(groups = "slow")
    public void testProcessNotification() throws Exception {
        paymentGatewayApi.processNotification(PLUGIN_NAME, "TOTO", NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
    }
}
