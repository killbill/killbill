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

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.RequestOptions;
import org.killbill.billing.client.model.TenantKeyValues;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.ComboPaymentTransaction;
import org.killbill.billing.client.model.gen.Payment;
import org.killbill.billing.client.model.gen.PaymentMethod;
import org.killbill.billing.client.model.gen.PaymentMethodPluginDetail;
import org.killbill.billing.client.model.gen.PaymentTransaction;
import org.killbill.billing.client.model.gen.PluginProperty;
import org.killbill.billing.client.model.gen.TenantKeyValue;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.tenant.api.TenantKV;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;

public class TestTenantKV extends TestJaxrsBase {

    @Test(groups = "slow", description = "Upload and retrieve a per plugin config")
    public void testPerTenantPluginConfig() throws Exception {
        final String pluginName = "PLUGIN_FOO";

        callbackServlet.pushExpectedEvent(ExtBusEventType.TENANT_CONFIG_CHANGE);
        final String pluginConfig = getResourceBodyString("org/killbill/billing/server/plugin.yml");
        final TenantKeyValue tenantKey0 = tenantApi.uploadPluginConfiguration(pluginName, pluginConfig, requestOptions);
        callbackServlet.assertListenerStatus();
        assertEquals(tenantKey0.getKey(), TenantKV.TenantKey.PLUGIN_CONFIG_ + pluginName);

        final TenantKeyValue tenantKey1 = tenantApi.getPluginConfiguration(pluginName, requestOptions);
        assertEquals(tenantKey1.getKey(), TenantKV.TenantKey.PLUGIN_CONFIG_ + pluginName);
        assertEquals(tenantKey1.getValues().size(), 1);

        tenantApi.deletePluginConfiguration(pluginName, requestOptions);
        final TenantKeyValue tenantKey2 = tenantApi.getPluginConfiguration(pluginName, requestOptions);
        assertEquals(tenantKey2.getKey(), TenantKV.TenantKey.PLUGIN_CONFIG_ + pluginName);
        assertEquals(tenantKey2.getValues().size(), 0);
    }

    @Test(groups = "slow", description = "Upload and retrieve a per plugin payment state machine config")
    public void testPerTenantPluginPaymentStateMachineConfig() throws Exception {
        final RequestOptions requestOptionsForOriginalTenant = requestOptions;

        // Create another tenant - it will have a different state machine
        createTenant(UUID.randomUUID().toString(), UUID.randomUUID().toString(), true);

        // Verify initial state
        final TenantKeyValue emptyTenantKey = tenantApi.getPluginPaymentStateMachineConfig(PLUGIN_NAME, requestOptionsForOriginalTenant);
        assertEquals(emptyTenantKey.getValues().size(), 0);
        final TenantKeyValue emptyTenantKeyOtherTenant = tenantApi.getPluginPaymentStateMachineConfig(PLUGIN_NAME, requestOptions);
        assertEquals(emptyTenantKeyOtherTenant.getValues().size(), 0);

        callbackServlet.pushExpectedEvent(ExtBusEventType.TENANT_CONFIG_CHANGE);
        final String stateMachineConfig = getResourceBodyString("org/killbill/billing/server/SimplePaymentStates.xml");
        final TenantKeyValue tenantKey0 = tenantApi.uploadPluginPaymentStateMachineConfig(PLUGIN_NAME, stateMachineConfig, requestOptions);
        callbackServlet.assertListenerStatus();
        assertEquals(tenantKey0.getKey(), TenantKV.TenantKey.PLUGIN_PAYMENT_STATE_MACHINE_ + PLUGIN_NAME);

        // Verify only the other tenant has the new state machine
        final TenantKeyValue emptyTenantKey1 = tenantApi.getPluginPaymentStateMachineConfig(PLUGIN_NAME, requestOptionsForOriginalTenant);
        assertEquals(emptyTenantKey1.getValues().size(), 0);
        final TenantKeyValue tenantKey1OtherTenant = tenantApi.getPluginPaymentStateMachineConfig(PLUGIN_NAME, requestOptions);
        assertEquals(tenantKey1OtherTenant.getKey(), TenantKV.TenantKey.PLUGIN_PAYMENT_STATE_MACHINE_ + PLUGIN_NAME);
        assertEquals(tenantKey1OtherTenant.getValues().size(), 1);

        // Create an auth in both tenant
        final Payment payment = createComboPaymentTransaction(requestOptionsForOriginalTenant);
        final Payment paymentOtherTenant = createComboPaymentTransaction(requestOptions);

        // Void in the first tenant (allowed by the default state machine)
        callbackServlet.pushExpectedEvent(ExtBusEventType.PAYMENT_SUCCESS);
        paymentApi.voidPayment(payment.getPaymentId(), new PaymentTransaction(), NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptionsForOriginalTenant);
        callbackServlet.assertListenerStatus();
        final Payment voidPayment = paymentApi.getPayment(payment.getPaymentId(), NULL_PLUGIN_PROPERTIES, requestOptionsForOriginalTenant);
        assertEquals(voidPayment.getTransactions().get(0).getStatus(), TransactionStatus.SUCCESS);
        assertEquals(voidPayment.getTransactions().get(1).getStatus(), TransactionStatus.SUCCESS);

        // Void in the other tenant (disallowed)
        try {
            paymentApi.voidPayment(paymentOtherTenant.getPaymentId(), new PaymentTransaction(), NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
            Assert.fail();
        } catch (final KillBillClientException e) {
            assertEquals((int) e.getBillingException().getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
        }
        callbackServlet.assertListenerStatus();

        // Remove the custom state machine
        callbackServlet.pushExpectedEvent(ExtBusEventType.TENANT_CONFIG_DELETION);
        tenantApi.deletePluginPaymentStateMachineConfig(PLUGIN_NAME, requestOptions);
        final TenantKeyValue tenantKey2 = tenantApi.getPluginPaymentStateMachineConfig(PLUGIN_NAME, requestOptions);
        callbackServlet.assertListenerStatus();
        assertEquals(tenantKey2.getKey(), TenantKV.TenantKey.PLUGIN_PAYMENT_STATE_MACHINE_ + PLUGIN_NAME);
        assertEquals(tenantKey2.getValues().size(), 0);

        final AtomicReference<Payment> voidPaymentOtherTenant2Ref = new AtomicReference<Payment>();
        Awaitility.await()
                  .atMost(8, TimeUnit.SECONDS)
                  .pollInterval(Durations.TWO_SECONDS)
                  .until(new Callable<Boolean>() {
                      @Override
                      public Boolean call() {
                          // The void should now go through
                          try {
                              callbackServlet.pushExpectedEvent(ExtBusEventType.PAYMENT_SUCCESS);
                              paymentApi.voidPayment(paymentOtherTenant.getPaymentId(), new PaymentTransaction(), NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
                              final Payment voidPaymentOtherTenant2 = paymentApi.getPayment(paymentOtherTenant.getPaymentId(), NULL_PLUGIN_PROPERTIES, requestOptions);
                              callbackServlet.assertListenerStatus();
                              voidPaymentOtherTenant2Ref.set(voidPaymentOtherTenant2);
                              return voidPaymentOtherTenant2 != null;
                          } catch (final KillBillClientException e) {
                              // Invalidation hasn't happened yet
                              return false;
                          }
                      }
                  });
        assertEquals(voidPaymentOtherTenant2Ref.get().getTransactions().get(0).getStatus(), TransactionStatus.SUCCESS);
        assertEquals(voidPaymentOtherTenant2Ref.get().getTransactions().get(1).getStatus(), TransactionStatus.SUCCESS);
    }

    @Test(groups = "slow", description = "Retrieve per tenant keys and values based on a key prefix")
    public void testAllPluginConfig() throws Exception {
        final String keyPrefix = "dummy_tenant";

        final String tenant1KeyName = "dummy_tenant_1_key";
        final String tenant1Value = "dummy_tenant_1_value";
        final TenantKeyValue tenant1Config = tenantApi.insertUserKeyValue(tenant1KeyName, tenant1Value, requestOptions);

        final String tenant2KeyName = "dummy_tenant_2_key";
        final String tenant2Value = "dummy_tenant_2_value";
        final TenantKeyValue tenant2Config = tenantApi.insertUserKeyValue(tenant2KeyName, tenant2Value, requestOptions);

        final TenantKeyValues tenantKeyValues = tenantApi.getAllPluginConfiguration(keyPrefix, requestOptions);

        Assert.assertFalse(tenantKeyValues.isEmpty());
        Assert.assertSame(tenantKeyValues.size(), 2);
        assertEquals(tenantKeyValues.get(0).getKey(), tenant1Config.getKey());
        assertEquals(tenantKeyValues.get(0).getValues(), tenant1Config.getValues());
        assertEquals(tenantKeyValues.get(1).getKey(), tenant2Config.getKey());
        assertEquals(tenantKeyValues.get(1).getValues(), tenant2Config.getValues());
    }

    private Payment createComboPaymentTransaction(final RequestOptions requestOptions) throws KillBillClientException {
        final Account accountJson = getAccount();
        accountJson.setAccountId(null);

        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        info.setProperties(null);

        final String paymentMethodExternalKey = UUID.randomUUID().toString();
        final PaymentMethod paymentMethodJson = new PaymentMethod(null, paymentMethodExternalKey, null, true, PLUGIN_NAME, info, null);

        final String authTransactionExternalKey = UUID.randomUUID().toString();
        final PaymentTransaction authTransactionJson = new PaymentTransaction();
        authTransactionJson.setAmount(BigDecimal.TEN);
        authTransactionJson.setCurrency(accountJson.getCurrency());
        authTransactionJson.setPaymentExternalKey(UUID.randomUUID().toString());
        authTransactionJson.setTransactionExternalKey(authTransactionExternalKey);
        authTransactionJson.setTransactionType(TransactionType.AUTHORIZE);

        callbackServlet.pushExpectedEvents(ExtBusEventType.ACCOUNT_CREATION, ExtBusEventType.ACCOUNT_CHANGE, ExtBusEventType.PAYMENT_SUCCESS);
        final ComboPaymentTransaction comboAuthorization = new ComboPaymentTransaction(accountJson, paymentMethodJson, authTransactionJson, ImmutableList.<PluginProperty>of(), ImmutableList.<PluginProperty>of(), null);
        final Payment payment = paymentApi.createComboPayment(comboAuthorization, NULL_PLUGIN_NAMES, requestOptions);
        callbackServlet.assertListenerStatus();
        assertEquals(payment.getTransactions().get(0).getStatus(), TransactionStatus.SUCCESS);

        return payment;
    }
}
