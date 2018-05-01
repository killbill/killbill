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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.KillBillHttpClient;
import org.killbill.billing.client.RequestOptions;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.Payment;
import org.killbill.billing.client.model.gen.PaymentTransaction;
import org.killbill.billing.client.model.gen.PluginProperty;
import org.killbill.billing.control.plugin.api.OnFailurePaymentControlResult;
import org.killbill.billing.control.plugin.api.OnSuccessPaymentControlResult;
import org.killbill.billing.control.plugin.api.PaymentControlApiException;
import org.killbill.billing.control.plugin.api.PaymentControlContext;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.control.plugin.api.PriorPaymentControlResult;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.payment.retry.DefaultFailureCallResult;
import org.killbill.billing.payment.retry.DefaultOnSuccessPaymentControlResult;
import org.killbill.billing.payment.retry.DefaultPriorPaymentControlResult;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;

public class TestPaymentPluginProperties extends TestJaxrsBase {

    @Inject
    private OSGIServiceRegistration<PaymentControlPluginApi> controlPluginRegistry;

    private PluginPropertiesVerificator mockPaymentControlProviderPlugin;

    public static class PluginPropertiesVerificator implements PaymentControlPluginApi {

        public static final String PLUGIN_NAME = "PLUGIN_PROPERTY_VERIFICATOR";

        private Iterable<org.killbill.billing.payment.api.PluginProperty> expectedProperties;

        public PluginPropertiesVerificator() {
            clearExpectPluginProperties();
        }

        @Override
        public PriorPaymentControlResult priorCall(final PaymentControlContext paymentControlContext, final Iterable<org.killbill.billing.payment.api.PluginProperty> properties) throws PaymentControlApiException {
            assertPluginProperties(properties);
            return new DefaultPriorPaymentControlResult(false);
        }

        @Override
        public OnSuccessPaymentControlResult onSuccessCall(final PaymentControlContext paymentControlContext, final Iterable<org.killbill.billing.payment.api.PluginProperty> properties) throws PaymentControlApiException {
            return new DefaultOnSuccessPaymentControlResult();
        }

        @Override
        public OnFailurePaymentControlResult onFailureCall(final PaymentControlContext paymentControlContext, final Iterable<org.killbill.billing.payment.api.PluginProperty> properties) throws PaymentControlApiException {
            return new DefaultFailureCallResult();
        }

        public void setExpectPluginProperties(final Iterable<org.killbill.billing.payment.api.PluginProperty> expectedProperties) {
            this.expectedProperties = expectedProperties;
        }

        public void clearExpectPluginProperties() {
            this.expectedProperties = ImmutableList.of();
        }

        private void assertPluginProperties(final Iterable<org.killbill.billing.payment.api.PluginProperty> properties) {
            for (org.killbill.billing.payment.api.PluginProperty input : properties) {
                boolean found = false;
                for (org.killbill.billing.payment.api.PluginProperty expect : expectedProperties) {
                    if (expect.getKey().equals(input.getKey()) && expect.getValue().equals(input.getValue())) {
                        found = true;
                        break;
                    }
                }
                Assert.assertTrue(found);
            }

            for (org.killbill.billing.payment.api.PluginProperty expect : expectedProperties) {
                boolean found = false;
                for (org.killbill.billing.payment.api.PluginProperty input : properties) {
                    if (expect.getKey().equals(input.getKey()) && expect.getValue().equals(input.getValue())) {
                        found = true;
                        break;
                    }
                }
                Assert.assertTrue(found);
            }
        }
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        mockPaymentControlProviderPlugin = new PluginPropertiesVerificator();
        controlPluginRegistry.registerService(new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return null;
            }

            @Override
            public String getPluginName() {
                return PluginPropertiesVerificator.PLUGIN_NAME;
            }

            @Override
            public String getRegistrationName() {
                return PluginPropertiesVerificator.PLUGIN_NAME;
            }
        }, mockPaymentControlProviderPlugin);
    }

    @AfterMethod(groups = "slow")
    public void tearDown() throws Exception {
        if (hasFailed()) {
            return;
        }

        mockPaymentControlProviderPlugin.clearExpectPluginProperties();
    }

    @Test(groups = "slow")
    public void testWithQueryPropertiesOnly() throws Exception {
        final List<org.killbill.billing.payment.api.PluginProperty> expectProperties = new ArrayList<org.killbill.billing.payment.api.PluginProperty>();

        final Map<String, String> queryProperties = new HashMap<String, String>();
        addProperty("key1", "val1", queryProperties, expectProperties);
        addProperty("key2", "val2", queryProperties, expectProperties);
        addProperty("key3", "val3", queryProperties, expectProperties);
        addProperty("key4", "val4", queryProperties, expectProperties);

        final List<PluginProperty> bodyProperties = new ArrayList<PluginProperty>();

        testInternal(queryProperties, bodyProperties, expectProperties);

    }

    @Test(groups = "slow")
    public void testWithBodyPropertiesOnly() throws Exception {
        final List<org.killbill.billing.payment.api.PluginProperty> expectProperties = new ArrayList<org.killbill.billing.payment.api.PluginProperty>();

        final Map<String, String> queryProperties = new HashMap<String, String>();

        final List<PluginProperty> bodyProperties = new ArrayList<PluginProperty>();
        addProperty("keyXXX1", "valXXXX1", bodyProperties, expectProperties);
        addProperty("keyXXX2", "valXXXX2", bodyProperties, expectProperties);
        addProperty("keyXXX3", "valXXXX3", bodyProperties, expectProperties);

        testInternal(queryProperties, bodyProperties, expectProperties);

    }

    @Test(groups = "slow")
    public void testWithBodyAndQueryProperties() throws Exception {
        final List<org.killbill.billing.payment.api.PluginProperty> expectProperties = new ArrayList<org.killbill.billing.payment.api.PluginProperty>();

        final Map<String, String> queryProperties = new HashMap<String, String>();
        addProperty("key1", "val1", queryProperties, expectProperties);
        addProperty("key2", "val2", queryProperties, expectProperties);
        addProperty("key3", "val3", queryProperties, expectProperties);
        addProperty("key4", "val4", queryProperties, expectProperties);

        final List<PluginProperty> bodyProperties = new ArrayList<PluginProperty>();
        addProperty("keyXXX1", "valXXXX1", bodyProperties, expectProperties);
        addProperty("keyXXX2", "valXXXX2", bodyProperties, expectProperties);
        addProperty("keyXXX3", "valXXXX3", bodyProperties, expectProperties);

        testInternal(queryProperties, bodyProperties, expectProperties);
    }

    private void testInternal(final Map<String, String> queryProperties, final List<PluginProperty> bodyProperties, final List<org.killbill.billing.payment.api.PluginProperty> expectProperties) throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();
        final UUID paymentMethodId = account.getPaymentMethodId();
        final BigDecimal amount = BigDecimal.TEN;

        final String pending = PaymentPluginStatus.PENDING.toString();
        final ImmutableMap<String, String> pluginProperties = ImmutableMap.<String, String>of(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, pending);

        TransactionType transactionType = TransactionType.AUTHORIZE;
        final String paymentExternalKey = UUID.randomUUID().toString();
        final String authTransactionExternalKey = UUID.randomUUID().toString();

        final Payment initialPayment = createVerifyTransaction(account, paymentMethodId, paymentExternalKey, authTransactionExternalKey, transactionType, amount, pluginProperties);

        mockPaymentControlProviderPlugin.setExpectPluginProperties(expectProperties);

        // Complete operation: first, only specify the payment id
        final PaymentTransaction completeTransactionByPaymentId = new PaymentTransaction();
        completeTransactionByPaymentId.setPaymentId(initialPayment.getPaymentId());
        completeTransactionByPaymentId.setProperties(bodyProperties);

        final RequestOptions basicRequestOptions = requestOptions;
        final Multimap<String, String> params = LinkedListMultimap.create(basicRequestOptions.getQueryParams());
        params.putAll(KillBillHttpClient.CONTROL_PLUGIN_NAME, ImmutableList.<String>of(PluginPropertiesVerificator.PLUGIN_NAME));

        final RequestOptions requestOptionsWithParams = basicRequestOptions.extend()
                                                                           .withQueryParams(params).build();

        paymentApi.completeTransaction(initialPayment.getPaymentId(), completeTransactionByPaymentId, NULL_PLUGIN_NAMES, queryProperties, requestOptionsWithParams);

        //Capture the payment
        final PaymentTransaction captureTransaction = new PaymentTransaction();
        captureTransaction.setPaymentId(initialPayment.getPaymentId());
        captureTransaction.setProperties(bodyProperties);
        captureTransaction.setAmount(BigDecimal.TEN);
        captureTransaction.setCurrency(account.getCurrency());
        paymentApi.captureAuthorization(initialPayment.getPaymentId(), captureTransaction, ImmutableList.<String>of(PluginPropertiesVerificator.PLUGIN_NAME), queryProperties, requestOptions);

        //Refund the payment
        final PaymentTransaction refundTransaction = new PaymentTransaction();
        refundTransaction.setPaymentId(initialPayment.getPaymentId());
        refundTransaction.setProperties(bodyProperties);
        refundTransaction.setAmount(BigDecimal.TEN);
        refundTransaction.setCurrency(account.getCurrency());
        paymentApi.refundPayment(initialPayment.getPaymentId(), refundTransaction, ImmutableList.<String>of(PluginPropertiesVerificator.PLUGIN_NAME), queryProperties, requestOptions);
    }

    private Payment createVerifyTransaction(final Account account,
                                            @Nullable final UUID paymentMethodId,
                                            final String paymentExternalKey,
                                            final String transactionExternalKey,
                                            final TransactionType transactionType,
                                            final BigDecimal transactionAmount,
                                            final Map<String, String> pluginProperties) throws KillBillClientException {
        final PaymentTransaction authTransaction = new PaymentTransaction();
        authTransaction.setAmount(transactionAmount);
        authTransaction.setCurrency(account.getCurrency());
        authTransaction.setPaymentExternalKey(paymentExternalKey);
        authTransaction.setTransactionExternalKey(transactionExternalKey);
        authTransaction.setTransactionType(transactionType);
        final Payment payment = accountApi.processPayment(account.getAccountId(), authTransaction, paymentMethodId, NULL_PLUGIN_NAMES, pluginProperties, requestOptions);
        return payment;
    }

    private void addProperty(final String key, final String value, final Map<String, String> dest, final List<org.killbill.billing.payment.api.PluginProperty> expectProperties) {
        dest.put(key, value);
        expectProperties.add(new org.killbill.billing.payment.api.PluginProperty(key, value, false));
    }

    private void addProperty(final String key, final String value, List<PluginProperty> bodyProperties, final List<org.killbill.billing.payment.api.PluginProperty> expectProperties) {
        bodyProperties.add(new PluginProperty(key, value, false));
        expectProperties.add(new org.killbill.billing.payment.api.PluginProperty(key, value, false));
    }
}
