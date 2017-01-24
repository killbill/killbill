/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.Payments;
import org.killbill.billing.client.model.Tenant;
import org.killbill.billing.client.model.TenantKey;
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
import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;

public class TestPerTenantConfig extends TestJaxrsBase {

    @Inject
    protected OSGIServiceRegistration<PaymentPluginApi> registry;

    private MockPaymentProviderPlugin mockPaymentProviderPlugin;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        mockPaymentProviderPlugin = (MockPaymentProviderPlugin) registry.getServiceForName(PLUGIN_NAME);
    }

    @AfterMethod(groups = "slow")
    public void tearDown() throws Exception {
        mockPaymentProviderPlugin.clear();
    }

    @Test(groups = "slow")
    public void testFailedPaymentWithPerTenantRetryConfig() throws Exception {
        // Create the tenant
        final String apiKeyTenant1 = "tenantSuperTuned";
        final String apiSecretTenant1 = "2367$$ffr79";
        loginTenant(apiKeyTenant1, apiSecretTenant1);
        final Tenant tenant1 = new Tenant();
        tenant1.setApiKey(apiKeyTenant1);
        tenant1.setApiSecret(apiSecretTenant1);
        killBillClient.createTenant(tenant1, createdBy, reason, comment);

        // Configure our plugin to fail
        mockPaymentProviderPlugin.makeAllInvoicesFailWithError(true);

        // Upload the config
        final ObjectMapper mapper = new ObjectMapper();
        final HashMap<String, String> perTenantProperties = new HashMap<String, String>();
        perTenantProperties.put("org.killbill.payment.retry.days", "1,1,1");
        final String perTenantConfig = mapper.writeValueAsString(perTenantProperties);

        final TenantKey tenantKey = killBillClient.postConfigurationPropertiesForTenant(perTenantConfig, requestOptions);

        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        final Payments payments = killBillClient.getPaymentsForAccount(accountJson.getAccountId());
        Assert.assertEquals(payments.size(), 1);
        Assert.assertEquals(payments.get(0).getTransactions().size(), 1);

        //
        // Because we have specified a retry interval of one day we should see the new attempt after moving clock 1 day (and not 8 days which is default)
        //

        //
        // Now unregister special per tenant config and we the first retry occurs one day after (and still fails), it now sets a retry date of 8 days
        //
        killBillClient.unregisterConfigurationForTenant(requestOptions);
        // org.killbill.tenant.broadcast.rate has been set to 1s
        crappyWaitForLackOfProperSynchonization(2000);

        clock.addDays(1);

        Awaitility.await()
                  .atMost(4, TimeUnit.SECONDS)
                  .pollInterval(Duration.ONE_SECOND)
                  .until(new Callable<Boolean>() {
                      @Override
                      public Boolean call() throws Exception {

                          return killBillClient.getPaymentsForAccount(accountJson.getAccountId()).get(0).getTransactions().size() == 2;
                      }
                  });
        final Payments payments2 = killBillClient.getPaymentsForAccount(accountJson.getAccountId());
        Assert.assertEquals(payments2.size(), 1);
        Assert.assertEquals(payments2.get(0).getTransactions().size(), 2);
        Assert.assertEquals(payments2.get(0).getTransactions().get(0).getStatus(), TransactionStatus.PAYMENT_FAILURE.name());
        Assert.assertEquals(payments2.get(0).getTransactions().get(1).getStatus(), TransactionStatus.PAYMENT_FAILURE.name());

        clock.addDays(1);
        crappyWaitForLackOfProperSynchonization(3000);

        // No retry with default config
        final Payments payments3 = killBillClient.getPaymentsForAccount(accountJson.getAccountId());
        Assert.assertEquals(payments3.size(), 1);
        Assert.assertEquals(payments3.get(0).getTransactions().size(), 2);

        mockPaymentProviderPlugin.makeAllInvoicesFailWithError(false);
        clock.addDays(7);

        Awaitility.await()
                  .atMost(4, TimeUnit.SECONDS)
                  .pollInterval(Duration.ONE_SECOND)
                  .until(new Callable<Boolean>() {
                      @Override
                      public Boolean call() throws Exception {
                          return killBillClient.getPaymentsForAccount(accountJson.getAccountId()).get(0).getTransactions().size() == 3;
                      }
                  });
        final Payments payments4 = killBillClient.getPaymentsForAccount(accountJson.getAccountId());
        Assert.assertEquals(payments4.size(), 1);
        Assert.assertEquals(payments4.get(0).getTransactions().size(), 3);
        Assert.assertEquals(payments4.get(0).getTransactions().get(0).getStatus(), TransactionStatus.PAYMENT_FAILURE.name());
        Assert.assertEquals(payments4.get(0).getTransactions().get(1).getStatus(), TransactionStatus.PAYMENT_FAILURE.name());
        Assert.assertEquals(payments4.get(0).getTransactions().get(2).getStatus(), TransactionStatus.SUCCESS.name());
    }
}
