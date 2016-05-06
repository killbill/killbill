/*
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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.ComboPaymentTransaction;
import org.killbill.billing.client.model.Payment;
import org.killbill.billing.client.model.PaymentMethod;
import org.killbill.billing.client.model.PaymentMethodPluginDetail;
import org.killbill.billing.client.model.PaymentTransaction;
import org.killbill.billing.client.model.Payments;
import org.killbill.billing.client.model.PluginProperty;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.provider.MockPaymentControlProviderPlugin;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestPayment extends TestJaxrsBase {

    @Inject
    protected OSGIServiceRegistration<PaymentPluginApi> registry;
    @Inject
    private OSGIServiceRegistration<PaymentControlPluginApi> controlPluginRegistry;

    private MockPaymentProviderPlugin mockPaymentProviderPlugin;
    private MockPaymentControlProviderPlugin mockPaymentControlProviderPlugin;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        mockPaymentProviderPlugin = (MockPaymentProviderPlugin) registry.getServiceForName(PLUGIN_NAME);
        mockPaymentProviderPlugin.clear();

        mockPaymentControlProviderPlugin = new MockPaymentControlProviderPlugin();
        controlPluginRegistry.registerService(new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return null;
            }

            @Override
            public String getPluginName() {
                return MockPaymentControlProviderPlugin.PLUGIN_NAME;
            }

            @Override
            public String getRegistrationName() {
                return MockPaymentControlProviderPlugin.PLUGIN_NAME;
            }
        }, mockPaymentControlProviderPlugin);
    }

    @Test(groups = "slow")
    public void testWithFailedPayment() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();

        mockPaymentProviderPlugin.makeNextPaymentFailWithError();

        final PaymentTransaction authTransaction = new PaymentTransaction();
        authTransaction.setAmount(BigDecimal.ONE);
        authTransaction.setCurrency(account.getCurrency());
        authTransaction.setTransactionType(TransactionType.AUTHORIZE.name());
        try {
            killBillClient.createPayment(account.getAccountId(), account.getPaymentMethodId(), authTransaction, ImmutableMap.<String, String>of(), createdBy, reason, comment);
            fail();
        } catch (KillBillClientException e) {
            assertEquals(402, e.getResponse().getStatusCode());
        }
    }

    @Test(groups = "slow")
    public void testWithCanceledPayment() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();

        mockPaymentProviderPlugin.makeNextPaymentFailWithCancellation();

        final PaymentTransaction authTransaction = new PaymentTransaction();
        authTransaction.setAmount(BigDecimal.ONE);
        authTransaction.setCurrency(account.getCurrency());
        authTransaction.setTransactionType(TransactionType.AUTHORIZE.name());
        try {
            killBillClient.createPayment(account.getAccountId(), account.getPaymentMethodId(), authTransaction, ImmutableMap.<String, String>of(), createdBy, reason, comment);
            fail();
        } catch (KillBillClientException e) {
            assertEquals(502, e.getResponse().getStatusCode());
        }
    }

    @Test(groups = "slow")
    public void testWithTimeoutPayment() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();

        mockPaymentProviderPlugin.makePluginWaitSomeMilliseconds(10000);

        final PaymentTransaction authTransaction = new PaymentTransaction();
        authTransaction.setAmount(BigDecimal.ONE);
        authTransaction.setCurrency(account.getCurrency());
        authTransaction.setTransactionType(TransactionType.AUTHORIZE.name());
        try {
            killBillClient.createPayment(account.getAccountId(), account.getPaymentMethodId(), authTransaction, ImmutableMap.<String, String>of(), createdBy, reason, comment);
            fail();
        } catch (KillBillClientException e) {
            assertEquals(504, e.getResponse().getStatusCode());
        }
    }

    @Test(groups = "slow")
    public void testCreateRetrievePayment() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();
        final String externalPaymentKey = UUID.randomUUID().toString();
        final UUID paymentId = testCreateRetrievePayment(account, null, externalPaymentKey, 1);

        final Payment payment = killBillClient.getPaymentByExternalKey(externalPaymentKey);
        assertEquals(payment.getPaymentId(), paymentId);

        final PaymentMethod paymentMethodJson = new PaymentMethod(null, UUID.randomUUID().toString(), account.getAccountId(), false, PLUGIN_NAME, new PaymentMethodPluginDetail());
        final PaymentMethod nonDefaultPaymentMethod = killBillClient.createPaymentMethod(paymentMethodJson, createdBy, reason, comment);
        testCreateRetrievePayment(account, nonDefaultPaymentMethod.getPaymentMethodId(), UUID.randomUUID().toString(), 2);
    }

    @Test(groups = "slow")
    public void testCompletionForInitialTransaction() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();
        final UUID paymentMethodId = account.getPaymentMethodId();
        final BigDecimal amount = BigDecimal.TEN;

        final String pending = PaymentPluginStatus.PENDING.toString();
        final ImmutableMap<String, String> pluginProperties = ImmutableMap.<String, String>of(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, pending);

        int paymentNb = 0;
        for (final TransactionType transactionType : ImmutableList.<TransactionType>of(TransactionType.AUTHORIZE, TransactionType.PURCHASE, TransactionType.CREDIT)) {
            final BigDecimal authAmount = BigDecimal.ZERO;
            final String paymentExternalKey = UUID.randomUUID().toString();
            final String authTransactionExternalKey = UUID.randomUUID().toString();
            paymentNb++;

            final Payment initialPayment = createVerifyTransaction(account, paymentMethodId, paymentExternalKey, authTransactionExternalKey, transactionType, pending, amount, authAmount, pluginProperties, paymentNb);
            final PaymentTransaction authPaymentTransaction = initialPayment.getTransactions().get(0);

            // Complete operation: first, only specify the payment id
            final PaymentTransaction completeTransactionByPaymentId = new PaymentTransaction();
            completeTransactionByPaymentId.setPaymentId(initialPayment.getPaymentId());
            final Payment completedPaymentByPaymentId = killBillClient.completePayment(completeTransactionByPaymentId, pluginProperties, createdBy, reason, comment);
            verifyPayment(account, paymentMethodId, completedPaymentByPaymentId, paymentExternalKey, authTransactionExternalKey, transactionType.toString(), pending, amount, authAmount, BigDecimal.ZERO, BigDecimal.ZERO, 1, paymentNb);

            // Second, only specify the payment external key
            final PaymentTransaction completeTransactionByPaymentExternalKey = new PaymentTransaction();
            completeTransactionByPaymentExternalKey.setPaymentExternalKey(initialPayment.getPaymentExternalKey());
            final Payment completedPaymentByExternalKey = killBillClient.completePayment(completeTransactionByPaymentExternalKey, pluginProperties, createdBy, reason, comment);
            verifyPayment(account, paymentMethodId, completedPaymentByExternalKey, paymentExternalKey, authTransactionExternalKey, transactionType.toString(), pending, amount, authAmount, BigDecimal.ZERO, BigDecimal.ZERO, 1, paymentNb);

            // Third, specify the payment id and transaction external key
            final PaymentTransaction completeTransactionWithTypeAndKey = new PaymentTransaction();
            completeTransactionWithTypeAndKey.setPaymentId(initialPayment.getPaymentId());
            completeTransactionWithTypeAndKey.setTransactionExternalKey(authPaymentTransaction.getTransactionExternalKey());
            final Payment completedPaymentByTypeAndKey = killBillClient.completePayment(completeTransactionWithTypeAndKey, pluginProperties, createdBy, reason, comment);
            verifyPayment(account, paymentMethodId, completedPaymentByTypeAndKey, paymentExternalKey, authTransactionExternalKey, transactionType.toString(), pending, amount, authAmount, BigDecimal.ZERO, BigDecimal.ZERO, 1, paymentNb);

            // Finally, specify the payment id and transaction id
            final PaymentTransaction completeTransactionWithTypeAndId = new PaymentTransaction();
            completeTransactionWithTypeAndId.setPaymentId(initialPayment.getPaymentId());
            completeTransactionWithTypeAndId.setTransactionId(authPaymentTransaction.getTransactionId());
            final Payment completedPaymentByTypeAndId = killBillClient.completePayment(completeTransactionWithTypeAndId, pluginProperties, createdBy, reason, comment);
            verifyPayment(account, paymentMethodId, completedPaymentByTypeAndId, paymentExternalKey, authTransactionExternalKey, transactionType.toString(), pending, amount, authAmount, BigDecimal.ZERO, BigDecimal.ZERO, 1, paymentNb);
        }
    }

    @Test(groups = "slow")
    public void testAuthorizeCompletionUsingPaymentId() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();
        final UUID paymentMethodId = account.getPaymentMethodId();
        final BigDecimal amount = BigDecimal.TEN;

        final String pending = PaymentPluginStatus.PENDING.toString();
        final ImmutableMap<String, String> pendingPluginProperties = ImmutableMap.<String, String>of(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, pending);

        final ImmutableMap<String, String> pluginProperties = ImmutableMap.of();

        TransactionType transactionType = TransactionType.AUTHORIZE;
        final String paymentExternalKey = UUID.randomUUID().toString();
        final String authTransactionExternalKey = UUID.randomUUID().toString();

        final Payment initialPayment = createVerifyTransaction(account, paymentMethodId, paymentExternalKey, authTransactionExternalKey, transactionType, pending, amount, BigDecimal.ZERO, pendingPluginProperties, 1);

        // Complete operation: first, only specify the payment id
        final PaymentTransaction completeTransactionByPaymentId = new PaymentTransaction();
        completeTransactionByPaymentId.setPaymentId(initialPayment.getPaymentId());
        final Payment completedPaymentByPaymentId = killBillClient.completePayment(completeTransactionByPaymentId, pluginProperties, createdBy, reason, comment);
        verifyPayment(account, paymentMethodId, completedPaymentByPaymentId, paymentExternalKey, authTransactionExternalKey, transactionType.toString(), TransactionStatus.SUCCESS.name(), amount, amount, BigDecimal.ZERO, BigDecimal.ZERO, 1, 1);
    }


    @Test(groups = "slow")
    public void testAuthorizeCompletionUsingPaymentIdAndTransactionId() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();
        final UUID paymentMethodId = account.getPaymentMethodId();
        final BigDecimal amount = BigDecimal.TEN;

        final String pending = PaymentPluginStatus.PENDING.toString();
        final ImmutableMap<String, String> pendingPluginProperties = ImmutableMap.<String, String>of(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, pending);

        final ImmutableMap<String, String> pluginProperties = ImmutableMap.of();

        TransactionType transactionType = TransactionType.AUTHORIZE;
        final String paymentExternalKey = UUID.randomUUID().toString();
        final String authTransactionExternalKey = UUID.randomUUID().toString();

        final Payment initialPayment = createVerifyTransaction(account, paymentMethodId, paymentExternalKey, authTransactionExternalKey, transactionType, pending, amount, BigDecimal.ZERO, pendingPluginProperties, 1);


        final PaymentTransaction completeTransactionByPaymentIdAndInvalidTransactionId = new PaymentTransaction();
        completeTransactionByPaymentIdAndInvalidTransactionId.setPaymentId(initialPayment.getPaymentId());
        completeTransactionByPaymentIdAndInvalidTransactionId.setTransactionId(UUID.randomUUID());
        try {
            killBillClient.completePayment(completeTransactionByPaymentIdAndInvalidTransactionId, pluginProperties, createdBy, reason, comment);
            fail("Payment completion should fail when invalid transaction id has been provided" );
        } catch (final KillBillClientException expected) {
        }

        final PaymentTransaction completeTransactionByPaymentIdAndTransactionId = new PaymentTransaction();
        completeTransactionByPaymentIdAndTransactionId.setPaymentId(initialPayment.getPaymentId());
        completeTransactionByPaymentIdAndTransactionId.setTransactionId(initialPayment.getTransactions().get(0).getTransactionId());
        final Payment completedPaymentByPaymentId = killBillClient.completePayment(completeTransactionByPaymentIdAndTransactionId, pluginProperties, createdBy, reason, comment);
        verifyPayment(account, paymentMethodId, completedPaymentByPaymentId, paymentExternalKey, authTransactionExternalKey, transactionType.toString(), TransactionStatus.SUCCESS.name(), amount, amount, BigDecimal.ZERO, BigDecimal.ZERO, 1, 1);
    }

    @Test(groups = "slow")
    public void testAuthorizeCompletionUsingPaymentIdAndTransactionExternalKey() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();
        final UUID paymentMethodId = account.getPaymentMethodId();
        final BigDecimal amount = BigDecimal.TEN;

        final String pending = PaymentPluginStatus.PENDING.toString();
        final ImmutableMap<String, String> pendingPluginProperties = ImmutableMap.<String, String>of(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, pending);

        final ImmutableMap<String, String> pluginProperties = ImmutableMap.of();

        TransactionType transactionType = TransactionType.AUTHORIZE;
        final String paymentExternalKey = UUID.randomUUID().toString();
        final String authTransactionExternalKey = UUID.randomUUID().toString();

        final Payment initialPayment = createVerifyTransaction(account, paymentMethodId, paymentExternalKey, authTransactionExternalKey, transactionType, pending, amount, BigDecimal.ZERO, pendingPluginProperties, 1);

        final PaymentTransaction completeTransactionByPaymentIdAndInvalidTransactionExternalKey = new PaymentTransaction();
        completeTransactionByPaymentIdAndInvalidTransactionExternalKey.setPaymentId(initialPayment.getPaymentId());
        completeTransactionByPaymentIdAndInvalidTransactionExternalKey.setTransactionExternalKey("bozo");
        try {
            killBillClient.completePayment(completeTransactionByPaymentIdAndInvalidTransactionExternalKey, pluginProperties, createdBy, reason, comment);
            fail("Payment completion should fail when invalid transaction externalKey has been provided" );
        } catch (final KillBillClientException expected) {
        }

        final PaymentTransaction completeTransactionByPaymentIdAndTransactionExternalKey = new PaymentTransaction();
        completeTransactionByPaymentIdAndTransactionExternalKey.setPaymentId(initialPayment.getPaymentId());
        completeTransactionByPaymentIdAndTransactionExternalKey.setTransactionExternalKey(authTransactionExternalKey);
        final Payment completedPaymentByPaymentId = killBillClient.completePayment(completeTransactionByPaymentIdAndTransactionExternalKey, pluginProperties, createdBy, reason, comment);
        verifyPayment(account, paymentMethodId, completedPaymentByPaymentId, paymentExternalKey, authTransactionExternalKey, transactionType.toString(), TransactionStatus.SUCCESS.name(), amount, amount, BigDecimal.ZERO, BigDecimal.ZERO, 1, 1);
    }


    @Test(groups = "slow")
    public void testAuthorizeCompletionUsingPaymentIdAndTransactionType() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();
        final UUID paymentMethodId = account.getPaymentMethodId();
        final BigDecimal amount = BigDecimal.TEN;

        final String pending = PaymentPluginStatus.PENDING.toString();
        final ImmutableMap<String, String> pendingPluginProperties = ImmutableMap.<String, String>of(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, pending);

        final ImmutableMap<String, String> pluginProperties = ImmutableMap.of();

        TransactionType transactionType = TransactionType.AUTHORIZE;
        final String paymentExternalKey = UUID.randomUUID().toString();
        final String authTransactionExternalKey = UUID.randomUUID().toString();

        final Payment initialPayment = createVerifyTransaction(account, paymentMethodId, paymentExternalKey, authTransactionExternalKey, transactionType, pending, amount, BigDecimal.ZERO, pendingPluginProperties, 1);


        final PaymentTransaction completeTransactionByPaymentIdAndInvalidTransactionType = new PaymentTransaction();
        completeTransactionByPaymentIdAndInvalidTransactionType.setPaymentId(initialPayment.getPaymentId());
        completeTransactionByPaymentIdAndInvalidTransactionType.setTransactionType(TransactionType.CAPTURE.name());
        try {
            killBillClient.completePayment(completeTransactionByPaymentIdAndInvalidTransactionType, pluginProperties, createdBy, reason, comment);
            fail("Payment completion should fail when invalid transaction type has been provided" );
        } catch (final KillBillClientException expected) {
        }

        final PaymentTransaction completeTransactionByPaymentIdAndTransactionType = new PaymentTransaction();
        completeTransactionByPaymentIdAndTransactionType.setPaymentId(initialPayment.getPaymentId());
        completeTransactionByPaymentIdAndTransactionType.setTransactionType(transactionType.name());
        final Payment completedPaymentByPaymentId = killBillClient.completePayment(completeTransactionByPaymentIdAndTransactionType, pluginProperties, createdBy, reason, comment);
        verifyPayment(account, paymentMethodId, completedPaymentByPaymentId, paymentExternalKey, authTransactionExternalKey, transactionType.toString(), TransactionStatus.SUCCESS.name(), amount, amount, BigDecimal.ZERO, BigDecimal.ZERO, 1, 1);
    }

    @Test(groups = "slow")
    public void testAuthorizeCompletionUsingExternalKey() throws Exception {

        final Account account = createAccountWithDefaultPaymentMethod();
        final UUID paymentMethodId = account.getPaymentMethodId();
        final BigDecimal amount = BigDecimal.TEN;

        final String pending = PaymentPluginStatus.PENDING.toString();
        final ImmutableMap<String, String> pendingPluginProperties = ImmutableMap.<String, String>of(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, pending);

        final ImmutableMap<String, String> pluginProperties = ImmutableMap.of();

        TransactionType transactionType = TransactionType.AUTHORIZE;
        final String paymentExternalKey = UUID.randomUUID().toString();
        final String authTransactionExternalKey = UUID.randomUUID().toString();

        final Payment initialPayment = createVerifyTransaction(account, paymentMethodId, paymentExternalKey, authTransactionExternalKey, transactionType, pending, amount, BigDecimal.ZERO, pendingPluginProperties, 1);

        final PaymentTransaction completeTransactionWithTypeAndKey = new PaymentTransaction();
        completeTransactionWithTypeAndKey.setPaymentId(initialPayment.getPaymentId());
        completeTransactionWithTypeAndKey.setTransactionExternalKey(authTransactionExternalKey);
        final Payment completedPaymentByPaymentId = killBillClient.completePayment(completeTransactionWithTypeAndKey, pluginProperties, createdBy, reason, comment);
        verifyPayment(account, paymentMethodId, completedPaymentByPaymentId, paymentExternalKey, authTransactionExternalKey, transactionType.toString(), TransactionStatus.SUCCESS.name(), amount, amount, BigDecimal.ZERO, BigDecimal.ZERO, 1, 1);
    }


    @Test(groups = "slow")
    public void testAuthorizeInvalidCompletionUsingPaymentId() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();
        final UUID paymentMethodId = account.getPaymentMethodId();
        final BigDecimal amount = BigDecimal.TEN;

        final ImmutableMap<String, String> pluginProperties = ImmutableMap.of();

        TransactionType transactionType = TransactionType.AUTHORIZE;
        final String paymentExternalKey = UUID.randomUUID().toString();
        final String authTransactionExternalKey = UUID.randomUUID().toString();

        final Payment initialPayment = createVerifyTransaction(account, paymentMethodId, paymentExternalKey, authTransactionExternalKey, transactionType, TransactionStatus.SUCCESS.name(), amount, amount, pluginProperties, 1);

        // The payment was already completed
        final PaymentTransaction completeTransactionByPaymentId = new PaymentTransaction();
        completeTransactionByPaymentId.setPaymentId(initialPayment.getPaymentId());
        try {
            killBillClient.completePayment(completeTransactionByPaymentId, pluginProperties, createdBy, reason, comment);
            fail("Completion should not succeed, there is no PENDING payment transaction");
        } catch (final KillBillClientException expected) {
            // Invalid parameter paymentId: XXXX
        }
    }


    @Test(groups = "slow")
    public void testCompletionForSubsequentTransaction() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();
        final UUID paymentMethodId = account.getPaymentMethodId();
        final String paymentExternalKey = UUID.randomUUID().toString();
        final String purchaseTransactionExternalKey = UUID.randomUUID().toString();
        final BigDecimal purchaseAmount = BigDecimal.TEN;

        // Create a successful purchase
        final Payment authPayment = createVerifyTransaction(account, paymentMethodId, paymentExternalKey, purchaseTransactionExternalKey, TransactionType.PURCHASE,
                                                            "SUCCESS", purchaseAmount, BigDecimal.ZERO, ImmutableMap.<String, String>of(), 1);

        final String pending = PaymentPluginStatus.PENDING.toString();
        final ImmutableMap<String, String> pluginProperties = ImmutableMap.<String, String>of(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, pending);

        // Trigger a pending refund
        final String refundTransactionExternalKey = UUID.randomUUID().toString();
        final PaymentTransaction refundTransaction = new PaymentTransaction();
        refundTransaction.setPaymentId(authPayment.getPaymentId());
        refundTransaction.setTransactionExternalKey(refundTransactionExternalKey);
        refundTransaction.setAmount(purchaseAmount);
        refundTransaction.setCurrency(authPayment.getCurrency());
        final Payment refundPayment = killBillClient.refundPayment(refundTransaction, null, pluginProperties, createdBy, reason, comment);
        verifyPaymentWithPendingRefund(account, paymentMethodId, paymentExternalKey, purchaseTransactionExternalKey, purchaseAmount, refundTransactionExternalKey, refundPayment);


        final PaymentTransaction completeTransactionWithTypeAndKey = new PaymentTransaction();
        completeTransactionWithTypeAndKey.setPaymentId(refundPayment.getPaymentId());
        completeTransactionWithTypeAndKey.setTransactionExternalKey(refundTransactionExternalKey);
        final Payment completedPaymentByTypeAndKey = killBillClient.completePayment(completeTransactionWithTypeAndKey, pluginProperties, createdBy, reason, comment);
        verifyPaymentWithPendingRefund(account, paymentMethodId, paymentExternalKey, purchaseTransactionExternalKey, purchaseAmount, refundTransactionExternalKey, completedPaymentByTypeAndKey);

        // Also, it should work if we specify the payment id and transaction id
        final PaymentTransaction completeTransactionWithTypeAndId = new PaymentTransaction();
        completeTransactionWithTypeAndId.setPaymentId(refundPayment.getPaymentId());
        completeTransactionWithTypeAndId.setTransactionId(refundPayment.getTransactions().get(1).getTransactionId());
        final Payment completedPaymentByTypeAndId = killBillClient.completePayment(completeTransactionWithTypeAndId, pluginProperties, createdBy, reason, comment);
        verifyPaymentWithPendingRefund(account, paymentMethodId, paymentExternalKey, purchaseTransactionExternalKey, purchaseAmount, refundTransactionExternalKey, completedPaymentByTypeAndId);
    }

    @Test(groups = "slow")
    public void testComboAuthorization() throws Exception {
        final Account accountJson = getAccount();
        accountJson.setAccountId(null);
        final String paymentExternalKey = UUID.randomUUID().toString();

        final ComboPaymentTransaction comboPaymentTransaction = createComboPaymentTransaction(accountJson, paymentExternalKey);

        final Payment payment = killBillClient.createPayment(comboPaymentTransaction, ImmutableMap.<String, String>of(), createdBy, reason, comment);
        verifyComboPayment(payment, paymentExternalKey, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, 1, 1);

        // Void payment using externalKey
        final String voidTransactionExternalKey = UUID.randomUUID().toString();
        final Payment voidPayment = killBillClient.voidPayment(null, paymentExternalKey, voidTransactionExternalKey, null, ImmutableMap.<String, String>of(), createdBy, reason, comment);
        verifyPaymentTransaction(accountJson, voidPayment.getPaymentId(), paymentExternalKey, voidPayment.getTransactions().get(1),
                                 voidTransactionExternalKey, null, "VOID", "SUCCESS");
    }

    @Test(groups = "slow")
    public void testComboAuthorizationControlPluginException() throws Exception {
        final Account accountJson = getAccount();
        accountJson.setAccountId(null);
        final String paymentExternalKey = UUID.randomUUID().toString();
        final ComboPaymentTransaction comboPaymentTransaction = createComboPaymentTransaction(accountJson, paymentExternalKey);

        mockPaymentControlProviderPlugin.throwsException(new IllegalStateException());
        try {
            killBillClient.createPayment(comboPaymentTransaction, Arrays.asList(MockPaymentControlProviderPlugin.PLUGIN_NAME), ImmutableMap.<String, String>of(), createdBy, reason, comment);
            fail();
        } catch (KillBillClientException e) {
            assertEquals(e.getResponse().getStatusCode(), 500);
        }
    }

    private ComboPaymentTransaction createComboPaymentTransaction(final Account accountJson, final String paymentExternalKey) {
        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        info.setProperties(null);

        final String paymentMethodExternalKey = UUID.randomUUID().toString();
        final PaymentMethod paymentMethodJson = new PaymentMethod(null, paymentMethodExternalKey, null, true, PLUGIN_NAME, info);

        final String authTransactionExternalKey = UUID.randomUUID().toString();
        final PaymentTransaction authTransactionJson = new PaymentTransaction();
        authTransactionJson.setAmount(BigDecimal.TEN);
        authTransactionJson.setCurrency(accountJson.getCurrency());
        authTransactionJson.setPaymentExternalKey(paymentExternalKey);
        authTransactionJson.setTransactionExternalKey(authTransactionExternalKey);
        authTransactionJson.setTransactionType("AUTHORIZE");

        return new ComboPaymentTransaction(accountJson, paymentMethodJson, authTransactionJson, ImmutableList.<PluginProperty>of(), ImmutableList.<PluginProperty>of());
    }

    @Test(groups = "slow")
    public void testComboAuthorizationInvalidPaymentMethod() throws Exception {
        final Account accountJson = getAccount();
        accountJson.setAccountId(null);

        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        info.setProperties(null);

        final UUID paymentMethodId = UUID.randomUUID();
        final PaymentMethod paymentMethodJson = new PaymentMethod(paymentMethodId, null, null, true, PLUGIN_NAME, info);

        final ComboPaymentTransaction comboPaymentTransaction = new ComboPaymentTransaction(accountJson, paymentMethodJson, null, ImmutableList.<PluginProperty>of(), ImmutableList.<PluginProperty>of());

        final Payment payment = killBillClient.createPayment(comboPaymentTransaction, ImmutableMap.<String, String>of(), createdBy, reason, comment);
        // Client returns null in case of a 404
        Assert.assertNull(payment);
    }

    private UUID testCreateRetrievePayment(final Account account, @Nullable final UUID paymentMethodId,
                                           final String paymentExternalKey, final int paymentNb) throws Exception {
        // Authorization
        final String authTransactionExternalKey = UUID.randomUUID().toString();
        final Payment authPayment = createVerifyTransaction(account, paymentMethodId, paymentExternalKey, authTransactionExternalKey, TransactionType.AUTHORIZE,
                                                            "SUCCESS", BigDecimal.TEN, BigDecimal.TEN, ImmutableMap.<String, String>of(), paymentNb);

        // Capture 1
        final String capture1TransactionExternalKey = UUID.randomUUID().toString();
        final PaymentTransaction captureTransaction = new PaymentTransaction();
        captureTransaction.setPaymentId(authPayment.getPaymentId());
        captureTransaction.setAmount(BigDecimal.ONE);
        captureTransaction.setCurrency(account.getCurrency());
        captureTransaction.setPaymentExternalKey(paymentExternalKey);
        captureTransaction.setTransactionExternalKey(capture1TransactionExternalKey);
        // captureAuthorization is using paymentId
        final Payment capturedPayment1 = killBillClient.captureAuthorization(captureTransaction, createdBy, reason, comment);
        verifyPayment(account, paymentMethodId, capturedPayment1, paymentExternalKey, authTransactionExternalKey, "AUTHORIZE", "SUCCESS",
                      BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, 2, paymentNb);
        verifyPaymentTransaction(account, authPayment.getPaymentId(), paymentExternalKey, capturedPayment1.getTransactions().get(1),
                                 capture1TransactionExternalKey, captureTransaction.getAmount(), "CAPTURE", "SUCCESS");

        // Capture 2
        final String capture2TransactionExternalKey = UUID.randomUUID().toString();
        captureTransaction.setTransactionExternalKey(capture2TransactionExternalKey);
        // captureAuthorization is using externalKey
        captureTransaction.setPaymentId(null);
        final Payment capturedPayment2 = killBillClient.captureAuthorization(captureTransaction, createdBy, reason, comment);
        verifyPayment(account, paymentMethodId, capturedPayment2, paymentExternalKey, authTransactionExternalKey, "AUTHORIZE", "SUCCESS",
                      BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("2"), BigDecimal.ZERO, 3, paymentNb);
        verifyPaymentTransaction(account, authPayment.getPaymentId(), paymentExternalKey, capturedPayment2.getTransactions().get(2),
                                 capture2TransactionExternalKey, captureTransaction.getAmount(), "CAPTURE", "SUCCESS");

        // Refund
        final String refundTransactionExternalKey = UUID.randomUUID().toString();
        final PaymentTransaction refundTransaction = new PaymentTransaction();
        refundTransaction.setPaymentId(authPayment.getPaymentId());
        refundTransaction.setAmount(new BigDecimal("2"));
        refundTransaction.setCurrency(account.getCurrency());
        refundTransaction.setPaymentExternalKey(paymentExternalKey);
        refundTransaction.setTransactionExternalKey(refundTransactionExternalKey);
        final Payment refundPayment = killBillClient.refundPayment(refundTransaction, createdBy, reason, comment);
        verifyPayment(account, paymentMethodId, refundPayment, paymentExternalKey, authTransactionExternalKey, "AUTHORIZE", "SUCCESS",
                      BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("2"), new BigDecimal("2"), 4, paymentNb);
        verifyPaymentTransaction(account, authPayment.getPaymentId(), paymentExternalKey, refundPayment.getTransactions().get(3),
                                 refundTransactionExternalKey, refundTransaction.getAmount(), "REFUND", "SUCCESS");

        return authPayment.getPaymentId();
    }

    private Payment createVerifyTransaction(final Account account,
                                            @Nullable final UUID paymentMethodId,
                                            final String paymentExternalKey,
                                            final String transactionExternalKey,
                                            final TransactionType transactionType,
                                            final String transactionStatus,
                                            final BigDecimal transactionAmount,
                                            final BigDecimal authAmount,
                                            final Map<String, String> pluginProperties,
                                            final int paymentNb) throws KillBillClientException {
        final PaymentTransaction authTransaction = new PaymentTransaction();
        authTransaction.setAmount(transactionAmount);
        authTransaction.setCurrency(account.getCurrency());
        authTransaction.setPaymentExternalKey(paymentExternalKey);
        authTransaction.setTransactionExternalKey(transactionExternalKey);
        authTransaction.setTransactionType(transactionType.toString());
        final Payment payment = killBillClient.createPayment(account.getAccountId(), paymentMethodId, authTransaction, pluginProperties, createdBy, reason, comment);

        verifyPayment(account, paymentMethodId, payment, paymentExternalKey, transactionExternalKey, transactionType.toString(), transactionStatus, transactionAmount, authAmount, BigDecimal.ZERO, BigDecimal.ZERO, 1, paymentNb);

        return payment;
    }

    private void verifyComboPayment(final Payment payment,
                                    final String paymentExternalKey,
                                    final BigDecimal authAmount,
                                    final BigDecimal capturedAmount,
                                    final BigDecimal refundedAmount,
                                    final int nbTransactions,
                                    final int paymentNb) throws KillBillClientException {
        Assert.assertNotNull(payment.getPaymentNumber());
        assertEquals(payment.getPaymentExternalKey(), paymentExternalKey);
        assertEquals(payment.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment.getCapturedAmount().compareTo(capturedAmount), 0);
        assertEquals(payment.getRefundedAmount().compareTo(refundedAmount), 0);
        assertEquals(payment.getTransactions().size(), nbTransactions);

        final Payments Payments = killBillClient.getPayments();
        assertEquals(Payments.size(), paymentNb);
        assertEquals(Payments.get(paymentNb - 1), payment);
    }

    private void verifyPayment(final Account account,
                               @Nullable final UUID paymentMethodId,
                               final Payment payment,
                               final String paymentExternalKey,
                               final String firstTransactionExternalKey,
                               final String firstTransactionType,
                               final String firstTransactionStatus,
                               final BigDecimal firstTransactionAmount,
                               final BigDecimal paymentAuthAmount,
                               final BigDecimal capturedAmount,
                               final BigDecimal refundedAmount,
                               final int nbTransactions,
                               final int paymentNb) throws KillBillClientException {
        verifyPaymentNoTransaction(account, paymentMethodId, payment, paymentExternalKey, paymentAuthAmount, capturedAmount, refundedAmount, nbTransactions, paymentNb);
        verifyPaymentTransaction(account, payment.getPaymentId(), paymentExternalKey, payment.getTransactions().get(0), firstTransactionExternalKey, firstTransactionAmount, firstTransactionType, firstTransactionStatus);
    }

    private void verifyPaymentNoTransaction(final Account account,
                                            @Nullable final UUID paymentMethodId,
                                            final Payment payment,
                                            final String paymentExternalKey,
                                            final BigDecimal authAmount,
                                            final BigDecimal capturedAmount,
                                            final BigDecimal refundedAmount,
                                            final int nbTransactions,
                                            final int paymentNb) throws KillBillClientException {
        assertEquals(payment.getAccountId(), account.getAccountId());
        assertEquals(payment.getPaymentMethodId(), MoreObjects.firstNonNull(paymentMethodId, account.getPaymentMethodId()));
        Assert.assertNotNull(payment.getPaymentId());
        Assert.assertNotNull(payment.getPaymentNumber());
        assertEquals(payment.getPaymentExternalKey(), paymentExternalKey);
        assertEquals(payment.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment.getCapturedAmount().compareTo(capturedAmount), 0);
        assertEquals(payment.getRefundedAmount().compareTo(refundedAmount), 0);
        assertEquals(payment.getCurrency(), account.getCurrency());
        assertEquals(payment.getTransactions().size(), nbTransactions);

        final Payments Payments = killBillClient.getPayments();
        assertEquals(Payments.size(), paymentNb);
        assertEquals(Payments.get(paymentNb - 1), payment);

        final Payment retrievedPayment = killBillClient.getPayment(payment.getPaymentId());
        assertEquals(retrievedPayment, payment);

        final Payments paymentsForAccount = killBillClient.getPaymentsForAccount(account.getAccountId());
        assertEquals(paymentsForAccount.size(), paymentNb);
        assertEquals(paymentsForAccount.get(paymentNb - 1), payment);
    }

    private void verifyPaymentTransaction(final Account account,
                                          final UUID paymentId,
                                          final String paymentExternalKey,
                                          final PaymentTransaction paymentTransaction,
                                          final String transactionExternalKey,
                                          @Nullable final BigDecimal amount,
                                          final String transactionType,
                                          final String transactionStatus) {
        assertEquals(paymentTransaction.getPaymentId(), paymentId);
        Assert.assertNotNull(paymentTransaction.getTransactionId());
        assertEquals(paymentTransaction.getTransactionType(), transactionType);
        assertEquals(paymentTransaction.getStatus(), transactionStatus);
        if (amount == null) {
            Assert.assertNull(paymentTransaction.getAmount());
            Assert.assertNull(paymentTransaction.getCurrency());
        } else {
            assertEquals(paymentTransaction.getAmount().compareTo(amount), 0);
            assertEquals(paymentTransaction.getCurrency(), account.getCurrency());
        }
        assertEquals(paymentTransaction.getTransactionExternalKey(), transactionExternalKey);
        assertEquals(paymentTransaction.getPaymentExternalKey(), paymentExternalKey);
    }

    private void verifyPaymentWithPendingRefund(final Account account, final UUID paymentMethodId, final String paymentExternalKey, final String authTransactionExternalKey, final BigDecimal purchaseAmount, final String refundTransactionExternalKey, final Payment refundPayment) throws KillBillClientException {
        verifyPayment(account, paymentMethodId, refundPayment, paymentExternalKey, authTransactionExternalKey, "PURCHASE", "SUCCESS", purchaseAmount, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 2, 1);
        verifyPaymentTransaction(account, refundPayment.getPaymentId(), paymentExternalKey, refundPayment.getTransactions().get(1), refundTransactionExternalKey, purchaseAmount, "REFUND", "PENDING");
    }
}
