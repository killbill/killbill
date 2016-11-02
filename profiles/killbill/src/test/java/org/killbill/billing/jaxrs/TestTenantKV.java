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

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.RequestOptions;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.ComboPaymentTransaction;
import org.killbill.billing.client.model.Payment;
import org.killbill.billing.client.model.PaymentMethod;
import org.killbill.billing.client.model.PaymentMethodPluginDetail;
import org.killbill.billing.client.model.PaymentTransaction;
import org.killbill.billing.client.model.PluginProperty;
import org.killbill.billing.client.model.Tenant;
import org.killbill.billing.client.model.TenantKey;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.tenant.api.TenantKV;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;

public class TestTenantKV extends TestJaxrsBase {

    @Test(groups = "slow", description = "Upload and retrieve a per plugin config")
    public void testPerTenantPluginConfig() throws Exception {
        final String pluginName = "PLUGIN_FOO";

        final String pluginPath = Resources.getResource("plugin.yml").getPath();
        final TenantKey tenantKey0 = killBillClient.registerPluginConfigurationForTenant(pluginName, pluginPath, createdBy, reason, comment);
        Assert.assertEquals(tenantKey0.getKey(), TenantKV.TenantKey.PLUGIN_CONFIG_.toString() + pluginName);

        final TenantKey tenantKey1 = killBillClient.getPluginConfigurationForTenant(pluginName);
        Assert.assertEquals(tenantKey1.getKey(), TenantKV.TenantKey.PLUGIN_CONFIG_.toString() + pluginName);
        Assert.assertEquals(tenantKey1.getValues().size(), 1);

        killBillClient.unregisterPluginConfigurationForTenant(pluginName, createdBy, reason, comment);
        final TenantKey tenantKey2 = killBillClient.getPluginConfigurationForTenant(pluginName);
        Assert.assertEquals(tenantKey2.getKey(), TenantKV.TenantKey.PLUGIN_CONFIG_.toString() + pluginName);
        Assert.assertEquals(tenantKey2.getValues().size(), 0);
    }

    @Test(groups = "slow", description = "Upload and retrieve a per plugin payment state machine config")
    public void testPerTenantPluginPaymentStateMachineConfig() throws Exception {
        // Create another tenant - it will have a different state machine
        final Tenant otherTenantWithDifferentStateMachine = new Tenant();
        otherTenantWithDifferentStateMachine.setApiKey(UUID.randomUUID().toString());
        otherTenantWithDifferentStateMachine.setApiSecret(UUID.randomUUID().toString());
        killBillClient.createTenant(otherTenantWithDifferentStateMachine, true, requestOptions);
        final RequestOptions requestOptionsOtherTenant = requestOptions.extend()
                                                                       .withTenantApiKey(otherTenantWithDifferentStateMachine.getApiKey())
                                                                       .withTenantApiSecret(otherTenantWithDifferentStateMachine.getApiSecret())
                                                                       .build();

        // Verify initial state
        final TenantKey emptyTenantKey = killBillClient.getPluginPaymentStateMachineConfigurationForTenant(PLUGIN_NAME, requestOptions);
        Assert.assertEquals(emptyTenantKey.getValues().size(), 0);
        final TenantKey emptyTenantKeyOtherTenant = killBillClient.getPluginPaymentStateMachineConfigurationForTenant(PLUGIN_NAME, requestOptionsOtherTenant);
        Assert.assertEquals(emptyTenantKeyOtherTenant.getValues().size(), 0);

        final String stateMachineConfigPath = Resources.getResource("SimplePaymentStates.xml").getPath();
        final TenantKey tenantKey0 = killBillClient.registerPluginPaymentStateMachineConfigurationForTenant(PLUGIN_NAME, stateMachineConfigPath, requestOptionsOtherTenant);
        Assert.assertEquals(tenantKey0.getKey(), TenantKV.TenantKey.PLUGIN_PAYMENT_STATE_MACHINE_.toString() + PLUGIN_NAME);

        // Verify only the other tenant has the new state machine
        final TenantKey emptyTenantKey1 = killBillClient.getPluginPaymentStateMachineConfigurationForTenant(PLUGIN_NAME, requestOptions);
        Assert.assertEquals(emptyTenantKey1.getValues().size(), 0);
        final TenantKey tenantKey1OtherTenant = killBillClient.getPluginPaymentStateMachineConfigurationForTenant(PLUGIN_NAME, requestOptionsOtherTenant);
        Assert.assertEquals(tenantKey1OtherTenant.getKey(), TenantKV.TenantKey.PLUGIN_PAYMENT_STATE_MACHINE_.toString() + PLUGIN_NAME);
        Assert.assertEquals(tenantKey1OtherTenant.getValues().size(), 1);

        // Create an auth in both tenant
        final Payment payment = createComboPaymentTransaction(requestOptions);
        final Payment paymentOtherTenant = createComboPaymentTransaction(requestOptionsOtherTenant);

        // Void in the first tenant (allowed by the default state machine)
        final Payment voidPayment = killBillClient.voidPayment(payment.getPaymentId(), payment.getPaymentExternalKey(), UUID.randomUUID().toString(), ImmutableList.<String>of(), ImmutableMap.<String, String>of(), requestOptions);
        Assert.assertEquals(voidPayment.getTransactions().get(0).getStatus(), TransactionStatus.SUCCESS.toString());
        Assert.assertEquals(voidPayment.getTransactions().get(1).getStatus(), TransactionStatus.SUCCESS.toString());

        // Void in the other tenant (disallowed)
        try {
            killBillClient.voidPayment(paymentOtherTenant.getPaymentId(), paymentOtherTenant.getPaymentExternalKey(), UUID.randomUUID().toString(), ImmutableList.<String>of(), ImmutableMap.<String, String>of(), requestOptionsOtherTenant);
            Assert.fail();
        } catch (final KillBillClientException e) {
            Assert.assertEquals((int) e.getBillingException().getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
        }

        // Remove the custom state machine
        killBillClient.unregisterPluginPaymentStateMachineConfigurationForTenant(PLUGIN_NAME, requestOptionsOtherTenant);
        final TenantKey tenantKey2 = killBillClient.getPluginPaymentStateMachineConfigurationForTenant(PLUGIN_NAME, requestOptionsOtherTenant);
        Assert.assertEquals(tenantKey2.getKey(), TenantKV.TenantKey.PLUGIN_PAYMENT_STATE_MACHINE_.toString() + PLUGIN_NAME);
        Assert.assertEquals(tenantKey2.getValues().size(), 0);

        final AtomicReference<Payment> voidPaymentOtherTenant2Ref = new AtomicReference<Payment>();
        Awaitility.await()
                  .atMost(8, TimeUnit.SECONDS)
                  .pollInterval(Duration.TWO_SECONDS)
                  .until(new Callable<Boolean>() {
                      @Override
                      public Boolean call() throws Exception {
                          // The void should now go through
                          try {
                              final Payment voidPaymentOtherTenant2 = killBillClient.voidPayment(paymentOtherTenant.getPaymentId(), paymentOtherTenant.getPaymentExternalKey(), UUID.randomUUID().toString(), ImmutableList.<String>of(), ImmutableMap.<String, String>of(), requestOptionsOtherTenant);
                              voidPaymentOtherTenant2Ref.set(voidPaymentOtherTenant2);
                              return voidPaymentOtherTenant2 != null;
                          } catch (final KillBillClientException e) {
                              // Invalidation hasn't happened yet
                              return false;
                          }
                      }
                  });
        Assert.assertEquals(voidPaymentOtherTenant2Ref.get().getTransactions().get(0).getStatus(), TransactionStatus.SUCCESS.toString());
        Assert.assertEquals(voidPaymentOtherTenant2Ref.get().getTransactions().get(1).getStatus(), TransactionStatus.SUCCESS.toString());
    }

    private Payment createComboPaymentTransaction(final RequestOptions requestOptions) throws KillBillClientException {
        final Account accountJson = getAccount();
        accountJson.setAccountId(null);

        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        info.setProperties(null);

        final String paymentMethodExternalKey = UUID.randomUUID().toString();
        final PaymentMethod paymentMethodJson = new PaymentMethod(null, paymentMethodExternalKey, null, true, PLUGIN_NAME, info);

        final String authTransactionExternalKey = UUID.randomUUID().toString();
        final PaymentTransaction authTransactionJson = new PaymentTransaction();
        authTransactionJson.setAmount(BigDecimal.TEN);
        authTransactionJson.setCurrency(accountJson.getCurrency());
        authTransactionJson.setPaymentExternalKey(UUID.randomUUID().toString());
        authTransactionJson.setTransactionExternalKey(authTransactionExternalKey);
        authTransactionJson.setTransactionType("AUTHORIZE");

        final ComboPaymentTransaction comboAuthorization = new ComboPaymentTransaction(accountJson, paymentMethodJson, authTransactionJson, ImmutableList.<PluginProperty>of(), ImmutableList.<PluginProperty>of());
        final Payment payment = killBillClient.createPayment(comboAuthorization, ImmutableMap.<String, String>of(), requestOptions);
        Assert.assertEquals(payment.getTransactions().get(0).getStatus(), TransactionStatus.SUCCESS.toString());

        return payment;
    }
}
