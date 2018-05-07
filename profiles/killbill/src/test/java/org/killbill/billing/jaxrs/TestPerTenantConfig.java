/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import java.util.HashMap;

import org.killbill.billing.client.model.Payments;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.TenantKeyValue;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.util.jackson.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.inject.Inject;

public class TestPerTenantConfig extends TestJaxrsBase {

    @Inject
    protected OSGIServiceRegistration<PaymentPluginApi> registry;

    private MockPaymentProviderPlugin mockPaymentProviderPlugin;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        mockPaymentProviderPlugin = (MockPaymentProviderPlugin) registry.getServiceForName(PLUGIN_NAME);
    }

    @AfterMethod(groups = "slow")
    public void tearDown() throws Exception {
        if (hasFailed()) {
            return;
        }

        mockPaymentProviderPlugin.clear();
    }

    @Test(groups = "slow")
    public void testFailedPaymentWithPerTenantRetryConfig() throws Exception {
        // Create the tenant
        createTenant("tenantSuperTuned", "2367$$ffr79", true);

        // Configure our plugin to fail
        mockPaymentProviderPlugin.makeAllInvoicesFailWithError(true);

        // Upload the config
        final ObjectMapper mapper = new ObjectMapper();
        final HashMap<String, String> perTenantProperties = new HashMap<String, String>();
        perTenantProperties.put("org.killbill.payment.retry.days", "1,1,1");
        final String perTenantConfig = mapper.writeValueAsString(perTenantProperties);

        callbackServlet.pushExpectedEvent(ExtBusEventType.TENANT_CONFIG_CHANGE);
        final TenantKeyValue tenantKey = tenantApi.uploadPerTenantConfiguration(perTenantConfig, requestOptions);
        callbackServlet.assertListenerStatus();

        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice(false);

        final Payments payments = accountApi.getPaymentsForAccount(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(payments.size(), 1);
        Assert.assertEquals(payments.get(0).getTransactions().size(), 1);

        //
        // Because we have specified a retry interval of one day we should see the new attempt after moving clock 1 day (and not 8 days which is default)
        //

        //
        // Now unregister special per tenant config and we the first retry occurs one day after (and still fails), it now sets a retry date of 8 days
        //
        callbackServlet.pushExpectedEvents(ExtBusEventType.TENANT_CONFIG_DELETION);
        tenantApi.deletePerTenantConfiguration(requestOptions);
        callbackServlet.assertListenerStatus();

        callbackServlet.pushExpectedEvents(ExtBusEventType.INVOICE_PAYMENT_FAILED, ExtBusEventType.PAYMENT_FAILED);
        clock.addDays(1);
        callbackServlet.assertListenerStatus();

        final Payments payments2 = accountApi.getPaymentsForAccount(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(payments2.size(), 1);
        Assert.assertEquals(payments2.get(0).getTransactions().size(), 2);
        Assert.assertEquals(payments2.get(0).getTransactions().get(0).getStatus(), TransactionStatus.PAYMENT_FAILURE);
        Assert.assertEquals(payments2.get(0).getTransactions().get(1).getStatus(), TransactionStatus.PAYMENT_FAILURE);

        clock.addDays(1);
        callbackServlet.assertListenerStatus();

        // No retry with default config
        final Payments payments3 = accountApi.getPaymentsForAccount(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(payments3.size(), 1);
        Assert.assertEquals(payments3.get(0).getTransactions().size(), 2);

        mockPaymentProviderPlugin.makeAllInvoicesFailWithError(false);
        callbackServlet.pushExpectedEvents(ExtBusEventType.INVOICE_PAYMENT_SUCCESS, ExtBusEventType.PAYMENT_SUCCESS);
        clock.addDays(7);
        callbackServlet.assertListenerStatus();

        final Payments payments4 = accountApi.getPaymentsForAccount(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(payments4.size(), 1);
        Assert.assertEquals(payments4.get(0).getTransactions().size(), 3);
        Assert.assertEquals(payments4.get(0).getTransactions().get(0).getStatus(), TransactionStatus.PAYMENT_FAILURE);
        Assert.assertEquals(payments4.get(0).getTransactions().get(1).getStatus(), TransactionStatus.PAYMENT_FAILURE);
        Assert.assertEquals(payments4.get(0).getTransactions().get(2).getStatus(), TransactionStatus.SUCCESS);
    }
}
