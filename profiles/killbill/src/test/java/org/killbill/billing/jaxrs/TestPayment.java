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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.RequestOptions;
import org.killbill.billing.client.model.InvoicePayments;
import org.killbill.billing.client.model.Payments;
import org.killbill.billing.client.model.Tags;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.AuditLog;
import org.killbill.billing.client.model.gen.ComboPaymentTransaction;
import org.killbill.billing.client.model.gen.Payment;
import org.killbill.billing.client.model.gen.PaymentMethod;
import org.killbill.billing.client.model.gen.PaymentMethodPluginDetail;
import org.killbill.billing.client.model.gen.PaymentTransaction;
import org.killbill.billing.client.model.gen.PluginProperty;
import org.killbill.billing.client.model.gen.TagDefinition;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.provider.MockPaymentControlProviderPlugin;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.ChangeType;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.MoreObjects;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
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
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        mockPaymentProviderPlugin = (MockPaymentProviderPlugin) registry.getServiceForName(PLUGIN_NAME);

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

    @AfterMethod(groups = "slow")
    public void tearDown() throws Exception {
        if (hasFailed()) {
            return;
        }

        mockPaymentProviderPlugin.clear();
    }

    @Test(groups = "slow")
    public void testWithTransactionEffectiveDate() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();

        final PaymentTransaction authTransaction = new PaymentTransaction();
        authTransaction.setAmount(BigDecimal.ONE);
        authTransaction.setCurrency(account.getCurrency());
        authTransaction.setTransactionType(TransactionType.AUTHORIZE);
        final DateTime effectiveDate = new DateTime(2018, 9, 4, 3, 5, 35);
        authTransaction.setEffectiveDate(effectiveDate);

        final Payment payment = accountApi.processPayment(account.getAccountId(), authTransaction, account.getPaymentMethodId(),
                                                          NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
        final PaymentTransaction paymentTransaction = payment.getTransactions().get(0);
        assertTrue(paymentTransaction.getEffectiveDate().compareTo(effectiveDate) == 0);
    }

    @Test(groups = "slow")
    public void testWithFailedPayment() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();

        mockPaymentProviderPlugin.makeNextPaymentFailWithError();

        final PaymentTransaction authTransaction = new PaymentTransaction();
        authTransaction.setAmount(BigDecimal.ONE);
        authTransaction.setCurrency(account.getCurrency());
        authTransaction.setTransactionType(TransactionType.AUTHORIZE);

        final Payment payment = accountApi.processPayment(account.getAccountId(), authTransaction, account.getPaymentMethodId(),
                                                          NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
        final PaymentTransaction paymentTransaction = payment.getTransactions().get(0);
        assertEquals(paymentTransaction.getStatus(), TransactionStatus.PAYMENT_FAILURE);
        assertEquals(paymentTransaction.getGatewayErrorCode(), MockPaymentProviderPlugin.GATEWAY_ERROR_CODE);
        assertEquals(paymentTransaction.getGatewayErrorMsg(), MockPaymentProviderPlugin.GATEWAY_ERROR);
    }

    @Test(groups = "slow")
    public void testWithFailedPaymentAndWithoutFollowLocation() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();

        mockPaymentProviderPlugin.makeNextPaymentFailWithError();

        final PaymentTransaction authTransaction = new PaymentTransaction();
        authTransaction.setAmount(BigDecimal.ONE);
        authTransaction.setCurrency(account.getCurrency());
        authTransaction.setTransactionType(TransactionType.AUTHORIZE);

        final RequestOptions requestOptionsWithoutFollowLocation = RequestOptions.builder()
                                                                                 .withCreatedBy(createdBy)
                                                                                 .withReason(reason)
                                                                                 .withComment(comment)
                                                                                 .withFollowLocation(false)
                                                                                 .build();

        try {
            accountApi.processPayment(account.getAccountId(), authTransaction, account.getPaymentMethodId(),
                                      NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptionsWithoutFollowLocation);
            fail();
        } catch (final KillBillClientException e) {
            assertEquals(e.getResponse().getStatusCode(), 402);
            assertEquals(e.getBillingException().getMessage(), "Payment decline by gateway. Error message: gatewayError");
        }
    }

    @Test(groups = "slow")
    public void testWithCanceledPayment() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();

        mockPaymentProviderPlugin.makeNextPaymentFailWithCancellation();

        final PaymentTransaction authTransaction = new PaymentTransaction();
        authTransaction.setAmount(BigDecimal.ONE);
        authTransaction.setCurrency(account.getCurrency());
        authTransaction.setTransactionType(TransactionType.AUTHORIZE);

        final Payment payment = accountApi.processPayment(account.getAccountId(), authTransaction, account.getPaymentMethodId(),
                                                          NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
        final PaymentTransaction paymentTransaction = payment.getTransactions().get(0);
        assertEquals(paymentTransaction.getStatus(), TransactionStatus.PLUGIN_FAILURE);
    }

    @Test(groups = "slow")
    public void testWithTimeoutPayment() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();

        mockPaymentProviderPlugin.makePluginWaitSomeMilliseconds(10000);

        final PaymentTransaction authTransaction = new PaymentTransaction();
        authTransaction.setAmount(BigDecimal.ONE);
        authTransaction.setCurrency(account.getCurrency());
        authTransaction.setTransactionType(TransactionType.AUTHORIZE);
        try {
            accountApi.processPayment(account.getAccountId(), authTransaction, account.getPaymentMethodId(),
                                      NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
            fail();
        } catch (KillBillClientException e) {
            assertEquals(504, e.getResponse().getStatusCode());
        }
    }

    @Test(groups = "slow")
    public void testWithFailedPaymentAndScheduledAttemptsGetInvoicePayment() throws Exception {
        mockPaymentProviderPlugin.makeNextPaymentFailWithError();
        final Account account = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice(false);
        // Getting Invoice #2 (first after Trial period)
        UUID failedInvoiceId = accountApi.getInvoicesForAccount(account.getAccountId(), RequestOptions.empty()).get(1).getInvoiceId();

        HashMultimap<String, String> queryParams = HashMultimap.create();
        queryParams.put("withAttempts", "true");
        RequestOptions inputOptions = RequestOptions.builder()
                                                    .withCreatedBy(createdBy)
                                                    .withReason(reason)
                                                    .withComment(comment)
                                                    .withQueryParams(queryParams).build();

        InvoicePayments invoicePayments = invoiceApi.getPaymentsForInvoice(failedInvoiceId, inputOptions);

        Assert.assertEquals(invoicePayments.get(0).getTargetInvoiceId(), failedInvoiceId);
        Assert.assertNotNull(invoicePayments.get(0).getPaymentAttempts());
        Assert.assertEquals(invoicePayments.get(0).getPaymentAttempts().size(), 2);
        Assert.assertEquals(invoicePayments.get(0).getPaymentAttempts().get(0).getStateName(), "RETRIED");
        Assert.assertEquals(invoicePayments.get(0).getPaymentAttempts().get(1).getStateName(), "SCHEDULED");

        // Remove the future notification and check SCHEDULED does not appear any longer
        paymentApi.cancelScheduledPaymentTransactionByExternalKey(invoicePayments.get(0).getPaymentAttempts().get(1).getTransactionExternalKey(), inputOptions);
        invoicePayments = invoiceApi.getPaymentsForInvoice(failedInvoiceId, inputOptions);
        Assert.assertEquals(invoicePayments.get(0).getPaymentAttempts().size(), 1);
        Assert.assertEquals(invoicePayments.get(0).getPaymentAttempts().get(0).getStateName(), "RETRIED");
    }

    @Test(groups = "slow")
    public void testWithFailedPaymentAndScheduledAttemptsGetPaymentsForAccount() throws Exception {
        mockPaymentProviderPlugin.makeNextPaymentFailWithError();
        final Account account = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice(false);

        HashMultimap<String, String> queryParams = HashMultimap.create();
        queryParams.put("withAttempts", "true");
        RequestOptions inputOptions = RequestOptions.builder()
                                                    .withCreatedBy(createdBy)
                                                    .withReason(reason)
                                                    .withComment(comment)
                                                    .withQueryParams(queryParams).build();

        Payments payments = accountApi.getPaymentsForAccount(account.getAccountId(), NULL_PLUGIN_PROPERTIES, inputOptions);

        Assert.assertNotNull(payments.get(0).getPaymentAttempts());
        Assert.assertEquals(payments.get(0).getPaymentAttempts().get(0).getStateName(), "RETRIED");
        Assert.assertEquals(payments.get(0).getPaymentAttempts().get(1).getStateName(), "SCHEDULED");
    }

    @Test(groups = "slow")
    public void testWithFailedPaymentAndScheduledAttemptsGetPayments() throws Exception {
        mockPaymentProviderPlugin.makeNextPaymentFailWithError();
        createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice(false);

        HashMultimap<String, String> queryParams = HashMultimap.create();
        queryParams.put("withAttempts", "true");
        RequestOptions inputOptions = RequestOptions.builder()
                                                    .withCreatedBy(createdBy)
                                                    .withReason(reason)
                                                    .withComment(comment)
                                                    .withQueryParams(queryParams).build();

        Payments payments = paymentApi.getPayments(0L, 100L, null, false, true, NULL_PLUGIN_PROPERTIES, AuditLevel.NONE, inputOptions);

        Assert.assertNotNull(payments.get(0).getPaymentAttempts());
        Assert.assertEquals(payments.get(0).getPaymentAttempts().get(0).getStateName(), "RETRIED");
        Assert.assertEquals(payments.get(0).getPaymentAttempts().get(1).getStateName(), "SCHEDULED");
    }

    @Test(groups = "slow")
    public void testWithFailedPaymentAndScheduledAttemptsSearchPayments() throws Exception {
        mockPaymentProviderPlugin.makeNextPaymentFailWithError();
        createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice(false);

        HashMultimap<String, String> queryParams = HashMultimap.create();
        queryParams.put("withAttempts", "true");
        RequestOptions inputOptions = RequestOptions.builder()
                                                    .withCreatedBy(createdBy)
                                                    .withReason(reason)
                                                    .withComment(comment)
                                                    .withQueryParams(queryParams).build();

        Payments payments = paymentApi.searchPayments("", 0L, 100L, false, true, null, NULL_PLUGIN_PROPERTIES, AuditLevel.NONE, inputOptions);

        Assert.assertNotNull(payments.get(0).getPaymentAttempts());
        Assert.assertEquals(payments.get(0).getPaymentAttempts().get(0).getStateName(), "RETRIED");
        Assert.assertEquals(payments.get(0).getPaymentAttempts().get(1).getStateName(), "SCHEDULED");
    }

    @Test(groups = "slow")
    public void testWithFailedPaymentAndScheduledAttemptsGetPaymentById() throws Exception {
        mockPaymentProviderPlugin.makeNextPaymentFailWithError();
        createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice(false);

        Payments payments = paymentApi.searchPayments("", 0L, 100L, false, true, null, NULL_PLUGIN_PROPERTIES, AuditLevel.NONE, requestOptions);
        Assert.assertNotNull(payments.get(0));
        Payment payment = paymentApi.getPayment(payments.get(0).getPaymentId(), false, true, NULL_PLUGIN_PROPERTIES, AuditLevel.NONE, requestOptions);

        Assert.assertNotNull(payment.getPaymentAttempts());
        Assert.assertEquals(payment.getPaymentAttempts().get(0).getStateName(), "RETRIED");
        Assert.assertEquals(payment.getPaymentAttempts().get(1).getStateName(), "SCHEDULED");
    }

    @Test(groups = "slow")
    public void testDeletePaymentMethodWithAutoPayOff() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();
        final UUID paymentMethodId = account.getPaymentMethodId();

        RequestOptions inputOptions = RequestOptions.builder()
                                                    .withCreatedBy(createdBy)
                                                    .withReason(reason)
                                                    .withComment(comment).build();

        paymentMethodApi.deletePaymentMethod(paymentMethodId, true, false, NULL_PLUGIN_PROPERTIES, inputOptions);

        Tags accountTags = accountApi.getAccountTags(account.getAccountId(), inputOptions);

        Assert.assertNotNull(accountTags);
        Assert.assertEquals(accountTags.get(0).getTagDefinitionName(), "AUTO_PAY_OFF");
    }

    @Test(groups = "slow")
    public void testCreateRetrievePayment() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();
        final String externalPaymentKey = UUID.randomUUID().toString();
        final UUID paymentId = testCreateRetrievePayment(account, null, externalPaymentKey, 1);

        final Payment payment = paymentApi.getPaymentByExternalKey(externalPaymentKey, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(payment.getPaymentId(), paymentId);

        final PaymentMethod paymentMethodJson = new PaymentMethod(null, UUID.randomUUID().toString(), account.getAccountId(), false, PLUGIN_NAME, new PaymentMethodPluginDetail(), null);
        final PaymentMethod nonDefaultPaymentMethod = accountApi.createPaymentMethod(account.getAccountId(), paymentMethodJson, NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
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

            final Payment initialPayment = createVerifyTransaction(account, paymentMethodId, paymentExternalKey, authTransactionExternalKey, transactionType, TransactionStatus.PENDING, amount, authAmount, pluginProperties, paymentNb);
            final PaymentTransaction authPaymentTransaction = initialPayment.getTransactions().get(0);

            // Complete operation: first, only specify the payment id
            final PaymentTransaction completeTransactionByPaymentId = new PaymentTransaction();
            completeTransactionByPaymentId.setPaymentId(initialPayment.getPaymentId());
            paymentApi.completeTransaction(initialPayment.getPaymentId(), completeTransactionByPaymentId, NULL_PLUGIN_NAMES, pluginProperties, requestOptions);
            final Payment completedPaymentByPaymentId = paymentApi.getPayment(initialPayment.getPaymentId(), pluginProperties, requestOptions);
            verifyPayment(account, paymentMethodId, completedPaymentByPaymentId, paymentExternalKey, authTransactionExternalKey, transactionType, TransactionStatus.PENDING, amount, authAmount, BigDecimal.ZERO, BigDecimal.ZERO, 1, paymentNb);

            // Second, only specify the payment external key
            final PaymentTransaction completeTransactionByPaymentExternalKey = new PaymentTransaction();
            completeTransactionByPaymentExternalKey.setPaymentExternalKey(initialPayment.getPaymentExternalKey());
            paymentApi.completeTransactionByExternalKey(completeTransactionByPaymentExternalKey, NULL_PLUGIN_NAMES, pluginProperties, requestOptions);
            final Payment completedPaymentByExternalKey = paymentApi.getPayment(initialPayment.getPaymentId(), pluginProperties, requestOptions);
            verifyPayment(account, paymentMethodId, completedPaymentByExternalKey, paymentExternalKey, authTransactionExternalKey, transactionType, TransactionStatus.PENDING, amount, authAmount, BigDecimal.ZERO, BigDecimal.ZERO, 1, paymentNb);

            // Third, specify the payment id and transaction external key
            final PaymentTransaction completeTransactionWithTypeAndKey = new PaymentTransaction();
            completeTransactionWithTypeAndKey.setPaymentExternalKey(paymentExternalKey);
            completeTransactionWithTypeAndKey.setTransactionExternalKey(authPaymentTransaction.getTransactionExternalKey());
            paymentApi.completeTransactionByExternalKey(completeTransactionWithTypeAndKey, NULL_PLUGIN_NAMES, pluginProperties, requestOptions);
            final Payment completedPaymentByTypeAndKey = paymentApi.getPayment(initialPayment.getPaymentId(), pluginProperties, requestOptions);
            verifyPayment(account, paymentMethodId, completedPaymentByTypeAndKey, paymentExternalKey, authTransactionExternalKey, transactionType, TransactionStatus.PENDING, amount, authAmount, BigDecimal.ZERO, BigDecimal.ZERO, 1, paymentNb);

            // Finally, specify the payment id and transaction id
            final PaymentTransaction completeTransactionWithTypeAndId = new PaymentTransaction();
            completeTransactionWithTypeAndId.setPaymentId(initialPayment.getPaymentId());
            completeTransactionWithTypeAndId.setTransactionId(authPaymentTransaction.getTransactionId());
            paymentApi.completeTransaction(initialPayment.getPaymentId(), completeTransactionWithTypeAndId, NULL_PLUGIN_NAMES, pluginProperties, requestOptions);
            final Payment completedPaymentByTypeAndId = paymentApi.getPayment(initialPayment.getPaymentId(), pluginProperties, requestOptions);
            verifyPayment(account, paymentMethodId, completedPaymentByTypeAndId, paymentExternalKey, authTransactionExternalKey, transactionType, TransactionStatus.PENDING, amount, authAmount, BigDecimal.ZERO, BigDecimal.ZERO, 1, paymentNb);
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

        final Payment initialPayment = createVerifyTransaction(account, paymentMethodId, paymentExternalKey, authTransactionExternalKey, transactionType, TransactionStatus.PENDING, amount, BigDecimal.ZERO, pendingPluginProperties, 1);

        // Complete operation: first, only specify the payment id
        final PaymentTransaction completeTransactionByPaymentId = new PaymentTransaction();
        completeTransactionByPaymentId.setPaymentId(initialPayment.getPaymentId());
        paymentApi.completeTransaction(initialPayment.getPaymentId(), completeTransactionByPaymentId, NULL_PLUGIN_NAMES, pluginProperties, requestOptions);
        final Payment completedPaymentByPaymentId = paymentApi.getPayment(initialPayment.getPaymentId(), pluginProperties, requestOptions);
        verifyPayment(account, paymentMethodId, completedPaymentByPaymentId, paymentExternalKey, authTransactionExternalKey, transactionType, TransactionStatus.SUCCESS, amount, amount, BigDecimal.ZERO, BigDecimal.ZERO, 1, 1);
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

        final Payment initialPayment = createVerifyTransaction(account, paymentMethodId, paymentExternalKey, authTransactionExternalKey, transactionType, TransactionStatus.PENDING, amount, BigDecimal.ZERO, pendingPluginProperties, 1);

        final PaymentTransaction completeTransactionByPaymentIdAndInvalidTransactionId = new PaymentTransaction();
        completeTransactionByPaymentIdAndInvalidTransactionId.setPaymentId(initialPayment.getPaymentId());
        completeTransactionByPaymentIdAndInvalidTransactionId.setTransactionId(UUID.randomUUID());
        try {
            paymentApi.completeTransaction(initialPayment.getPaymentId(), completeTransactionByPaymentIdAndInvalidTransactionId, NULL_PLUGIN_NAMES, pluginProperties, requestOptions);
            fail("Payment completion should fail when invalid transaction id has been provided");
        } catch (final KillBillClientException expected) {
        }

        final PaymentTransaction completeTransactionByPaymentIdAndTransactionId = new PaymentTransaction();
        completeTransactionByPaymentIdAndTransactionId.setPaymentId(initialPayment.getPaymentId());
        completeTransactionByPaymentIdAndTransactionId.setTransactionId(initialPayment.getTransactions().get(0).getTransactionId());
        paymentApi.completeTransaction(initialPayment.getPaymentId(), completeTransactionByPaymentIdAndTransactionId, NULL_PLUGIN_NAMES, pluginProperties, requestOptions);
        final Payment completedPaymentByPaymentId = paymentApi.getPayment(initialPayment.getPaymentId(), pluginProperties, requestOptions);
        verifyPayment(account, paymentMethodId, completedPaymentByPaymentId, paymentExternalKey, authTransactionExternalKey, transactionType, TransactionStatus.SUCCESS, amount, amount, BigDecimal.ZERO, BigDecimal.ZERO, 1, 1);
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

        final Payment initialPayment = createVerifyTransaction(account, paymentMethodId, paymentExternalKey, authTransactionExternalKey, transactionType, TransactionStatus.PENDING, amount, BigDecimal.ZERO, pendingPluginProperties, 1);

        final PaymentTransaction completeTransactionByPaymentIdAndInvalidTransactionExternalKey = new PaymentTransaction();
        completeTransactionByPaymentIdAndInvalidTransactionExternalKey.setPaymentId(initialPayment.getPaymentId());
        completeTransactionByPaymentIdAndInvalidTransactionExternalKey.setTransactionExternalKey("bozo");
        try {
            paymentApi.completeTransaction(initialPayment.getPaymentId(), completeTransactionByPaymentIdAndInvalidTransactionExternalKey, NULL_PLUGIN_NAMES, pluginProperties, requestOptions);
            fail("Payment completion should fail when invalid transaction externalKey has been provided");
        } catch (final KillBillClientException expected) {
        }

        final PaymentTransaction completeTransactionByPaymentIdAndTransactionExternalKey = new PaymentTransaction();
        completeTransactionByPaymentIdAndTransactionExternalKey.setPaymentExternalKey(paymentExternalKey);
        completeTransactionByPaymentIdAndTransactionExternalKey.setTransactionExternalKey(authTransactionExternalKey);
        paymentApi.completeTransactionByExternalKey(completeTransactionByPaymentIdAndTransactionExternalKey, NULL_PLUGIN_NAMES, pluginProperties, requestOptions);
        final Payment completedPaymentByPaymentId = paymentApi.getPayment(initialPayment.getPaymentId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        verifyPayment(account, paymentMethodId, completedPaymentByPaymentId, paymentExternalKey, authTransactionExternalKey, transactionType, TransactionStatus.SUCCESS, amount, amount, BigDecimal.ZERO, BigDecimal.ZERO, 1, 1);
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

        final Payment initialPayment = createVerifyTransaction(account, paymentMethodId, paymentExternalKey, authTransactionExternalKey, transactionType, TransactionStatus.PENDING, amount, BigDecimal.ZERO, pendingPluginProperties, 1);

        final PaymentTransaction completeTransactionByPaymentIdAndInvalidTransactionType = new PaymentTransaction();
        completeTransactionByPaymentIdAndInvalidTransactionType.setPaymentId(initialPayment.getPaymentId());
        completeTransactionByPaymentIdAndInvalidTransactionType.setTransactionType(TransactionType.CAPTURE);
        try {
            paymentApi.completeTransaction(initialPayment.getPaymentId(), completeTransactionByPaymentIdAndInvalidTransactionType,NULL_PLUGIN_NAMES, pluginProperties, requestOptions);
            fail("Payment completion should fail when invalid transaction type has been provided");
        } catch (final KillBillClientException expected) {
        }

        final PaymentTransaction completeTransactionByPaymentIdAndTransactionType = new PaymentTransaction();
        completeTransactionByPaymentIdAndTransactionType.setPaymentId(initialPayment.getPaymentId());
        completeTransactionByPaymentIdAndTransactionType.setTransactionType(transactionType);
        paymentApi.completeTransaction(initialPayment.getPaymentId(), completeTransactionByPaymentIdAndTransactionType, NULL_PLUGIN_NAMES, pluginProperties, requestOptions);
        final Payment completedPaymentByPaymentId = paymentApi.getPayment(initialPayment.getPaymentId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        verifyPayment(account, paymentMethodId, completedPaymentByPaymentId, paymentExternalKey, authTransactionExternalKey, transactionType, TransactionStatus.SUCCESS, amount, amount, BigDecimal.ZERO, BigDecimal.ZERO, 1, 1);
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

        final Payment initialPayment = createVerifyTransaction(account, paymentMethodId, paymentExternalKey, authTransactionExternalKey, transactionType, TransactionStatus.PENDING, amount, BigDecimal.ZERO, pendingPluginProperties, 1);

        final PaymentTransaction completeTransactionWithTypeAndKey = new PaymentTransaction();
        completeTransactionWithTypeAndKey.setPaymentExternalKey(paymentExternalKey);
        completeTransactionWithTypeAndKey.setTransactionExternalKey(authTransactionExternalKey);
        paymentApi.completeTransactionByExternalKey(completeTransactionWithTypeAndKey, NULL_PLUGIN_NAMES, pluginProperties, requestOptions);
        final Payment completedPaymentByPaymentId = paymentApi.getPayment(initialPayment.getPaymentId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        verifyPayment(account, paymentMethodId, completedPaymentByPaymentId, paymentExternalKey, authTransactionExternalKey, transactionType, TransactionStatus.SUCCESS, amount, amount, BigDecimal.ZERO, BigDecimal.ZERO, 1, 1);
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

        final Payment initialPayment = createVerifyTransaction(account, paymentMethodId, paymentExternalKey, authTransactionExternalKey, transactionType, TransactionStatus.SUCCESS, amount, amount, pluginProperties, 1);

        // The payment was already completed, it should succeed (no-op)
        final PaymentTransaction completeTransactionByPaymentId = new PaymentTransaction();
        completeTransactionByPaymentId.setPaymentId(initialPayment.getPaymentId());
        paymentApi.completeTransaction(initialPayment.getPaymentId(), completeTransactionByPaymentId, NULL_PLUGIN_NAMES, pluginProperties, requestOptions);
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
                                                            TransactionStatus.SUCCESS, purchaseAmount, BigDecimal.ZERO, ImmutableMap.<String, String>of(), 1);

        final String pending = PaymentPluginStatus.PENDING.toString();
        final ImmutableMap<String, String> pluginProperties = ImmutableMap.<String, String>of(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, pending);

        // Trigger a pending refund
        final String refundTransactionExternalKey = UUID.randomUUID().toString();
        final PaymentTransaction refundTransaction = new PaymentTransaction();
        refundTransaction.setPaymentId(authPayment.getPaymentId());
        refundTransaction.setTransactionExternalKey(refundTransactionExternalKey);
        refundTransaction.setAmount(purchaseAmount);
        refundTransaction.setCurrency(authPayment.getCurrency());
        final Payment refundPayment = paymentApi.refundPayment(authPayment.getPaymentId(), refundTransaction, NULL_PLUGIN_NAMES, pluginProperties, requestOptions);
        verifyPaymentWithPendingRefund(account, paymentMethodId, paymentExternalKey, purchaseTransactionExternalKey, purchaseAmount, refundTransactionExternalKey, refundPayment);

        final PaymentTransaction completeTransactionWithTypeAndKey = new PaymentTransaction();
        completeTransactionWithTypeAndKey.setPaymentExternalKey(paymentExternalKey);
        completeTransactionWithTypeAndKey.setTransactionExternalKey(refundTransactionExternalKey);
        paymentApi.completeTransactionByExternalKey(completeTransactionWithTypeAndKey, NULL_PLUGIN_NAMES, pluginProperties, requestOptions);
        final Payment completedPaymentByTypeAndKey = paymentApi.getPayment(refundPayment.getPaymentId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        verifyPaymentWithPendingRefund(account, paymentMethodId, paymentExternalKey, purchaseTransactionExternalKey, purchaseAmount, refundTransactionExternalKey, completedPaymentByTypeAndKey);

        // Also, it should work if we specify the payment id and transaction id
        final PaymentTransaction completeTransactionWithTypeAndId = new PaymentTransaction();
        completeTransactionWithTypeAndId.setPaymentId(refundPayment.getPaymentId());
        completeTransactionWithTypeAndId.setTransactionId(refundPayment.getTransactions().get(1).getTransactionId());
        paymentApi.completeTransaction(refundPayment.getPaymentId(), completeTransactionWithTypeAndId, NULL_PLUGIN_NAMES, pluginProperties, requestOptions);
        final Payment completedPaymentByTypeAndId = paymentApi.getPayment(refundPayment.getPaymentId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        verifyPaymentWithPendingRefund(account, paymentMethodId, paymentExternalKey, purchaseTransactionExternalKey, purchaseAmount, refundTransactionExternalKey, completedPaymentByTypeAndId);
    }

    @Test(groups = "slow")
    public void testComboAuthorization() throws Exception {
        final Account accountJson = getAccount();
        accountJson.setAccountId(null);
        final String paymentExternalKey = UUID.randomUUID().toString();

        final ComboPaymentTransaction comboPaymentTransaction = createComboPaymentTransaction(accountJson, paymentExternalKey);

        final Payment payment = paymentApi.createComboPayment(comboPaymentTransaction, NULL_PLUGIN_NAMES, requestOptions);
        verifyComboPayment(payment, paymentExternalKey, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, 1, 1);

        // Void payment using externalKey
        final String voidTransactionExternalKey = UUID.randomUUID().toString();
        PaymentTransaction voidTransaction = new PaymentTransaction();
        voidTransaction.setTransactionExternalKey(voidTransactionExternalKey);
        voidTransaction.setPaymentExternalKey(paymentExternalKey);
        paymentApi.voidPaymentByExternalKey(voidTransaction, NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
        final Payment voidPayment = paymentApi.getPayment(payment.getPaymentId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        verifyPaymentTransaction(accountJson, voidPayment.getPaymentId(), paymentExternalKey, voidPayment.getTransactions().get(1),
                                 voidTransactionExternalKey, null, TransactionType.VOID, TransactionStatus.SUCCESS);
    }

    @Test(groups = "slow")
    public void testComboAuthorizationAbortedPayment() throws Exception {
        final Account accountJson = getAccount();
        accountJson.setAccountId(null);
        final String paymentExternalKey = UUID.randomUUID().toString();
        final ComboPaymentTransaction comboPaymentTransaction = createComboPaymentTransaction(accountJson, paymentExternalKey);

        mockPaymentControlProviderPlugin.setAborted(true);
        try {
            paymentApi.createComboPayment(comboPaymentTransaction, Arrays.asList(MockPaymentControlProviderPlugin.PLUGIN_NAME), requestOptions);
            fail();
        } catch (KillBillClientException e) {
            assertEquals(e.getResponse().getStatusCode(), 422);
        }
        assertFalse(mockPaymentControlProviderPlugin.isOnFailureCallExecuted());
        assertFalse(mockPaymentControlProviderPlugin.isOnSuccessCallExecuted());
    }

    @Test(groups = "slow")
    public void testComboAuthorizationControlWithNullPaymentMethodId() throws Exception {
        final Account accountJson = createAccount();

        // Non-default payment method
        final PaymentMethod paymentMethod = createPaymentMethod(accountJson, false);
        mockPaymentControlProviderPlugin.setAdjustedPaymentMethodId(paymentMethod.getPaymentMethodId());

        accountJson.setAccountId(null);
        final String paymentExternalKey = UUID.randomUUID().toString();

        final PaymentTransaction authTransactionJson = new PaymentTransaction();
        authTransactionJson.setPaymentExternalKey(paymentExternalKey);
        authTransactionJson.setAmount(BigDecimal.TEN);
        authTransactionJson.setTransactionType(TransactionType.AUTHORIZE);
        final ComboPaymentTransaction comboPaymentTransaction = new ComboPaymentTransaction();
        comboPaymentTransaction.setAccount(accountJson);
        comboPaymentTransaction.setTransaction(authTransactionJson);
        comboPaymentTransaction.setTransaction(authTransactionJson);

        final Payment payment = paymentApi.createComboPayment(comboPaymentTransaction, ImmutableList.<String>of(MockPaymentControlProviderPlugin.PLUGIN_NAME), requestOptions);
        verifyComboPayment(payment, paymentExternalKey, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, 1, 1);

        assertEquals(paymentApi.getPayment(payment.getPaymentId(), false, true, ImmutableMap.<String, String>of(), AuditLevel.NONE, requestOptions).getPaymentAttempts().size(), 1);
    }

    @Test(groups = "slow")
    public void testComboAuthorizationControlPluginException() throws Exception {
        final Account accountJson = getAccount();
        accountJson.setAccountId(null);
        final String paymentExternalKey = UUID.randomUUID().toString();
        final ComboPaymentTransaction comboPaymentTransaction = createComboPaymentTransaction(accountJson, paymentExternalKey);

        mockPaymentControlProviderPlugin.throwsException(new IllegalStateException());
        try {
            paymentApi.createComboPayment(comboPaymentTransaction, Arrays.asList(MockPaymentControlProviderPlugin.PLUGIN_NAME), requestOptions);
            fail();
        } catch (KillBillClientException e) {
            assertEquals(e.getResponse().getStatusCode(), 500);
        }
    }

    private ComboPaymentTransaction createComboPaymentTransaction(final Account accountJson, final String paymentExternalKey) {
        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        info.setProperties(null);

        final String paymentMethodExternalKey = UUID.randomUUID().toString();
        final PaymentMethod paymentMethodJson = new PaymentMethod();
        paymentMethodJson.setExternalKey(paymentMethodExternalKey);
        paymentMethodJson.setPluginName(PLUGIN_NAME);
        paymentMethodJson.setPluginInfo(info);

        final String authTransactionExternalKey = UUID.randomUUID().toString();
        final PaymentTransaction authTransactionJson = new PaymentTransaction();
        authTransactionJson.setAmount(BigDecimal.TEN);
        authTransactionJson.setCurrency(accountJson.getCurrency());
        authTransactionJson.setPaymentExternalKey(paymentExternalKey);
        authTransactionJson.setTransactionExternalKey(authTransactionExternalKey);
        authTransactionJson.setTransactionType(TransactionType.AUTHORIZE);

        return new ComboPaymentTransaction(accountJson, paymentMethodJson, authTransactionJson, ImmutableList.<PluginProperty>of(), ImmutableList.<PluginProperty>of(), null);
    }

    @Test(groups = "slow")
    public void testComboAuthorizationInvalidPaymentMethod() throws Exception {
        final Account accountJson = getAccount();
        accountJson.setAccountId(null);

        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        info.setProperties(null);

        final UUID paymentMethodId = UUID.randomUUID();
        final PaymentMethod paymentMethodJson = new PaymentMethod();
        paymentMethodJson.setPluginName(PLUGIN_NAME);
        paymentMethodJson.setPluginInfo(info);
        paymentMethodJson.setIsDefault(true);
        paymentMethodJson.setPaymentMethodId(paymentMethodId);

        final ComboPaymentTransaction comboPaymentTransaction = new ComboPaymentTransaction(accountJson, paymentMethodJson, new PaymentTransaction(), ImmutableList.<PluginProperty>of(), ImmutableList.<PluginProperty>of(), null);

        final Payment payment = paymentApi.createComboPayment(comboPaymentTransaction, NULL_PLUGIN_NAMES, requestOptions);
        // Client returns null in case of a 404
        Assert.assertNull(payment);
    }

    @Test(groups = "slow")
    public void testGetTagsForPaymentTransaction() throws Exception {
        UUID tagDefinitionId = UUID.randomUUID();
        String tagDefinitionName = "payment-transaction";
        TagDefinition tagDefinition = new TagDefinition(tagDefinitionId, false, tagDefinitionName, "description", ImmutableList.<ObjectType>of(ObjectType.TRANSACTION), null);
        final TagDefinition createdTagDefinition = tagDefinitionApi.createTagDefinition(tagDefinition, requestOptions);

        final Account account = createAccountWithDefaultPaymentMethod();
        final String externalPaymentKey = UUID.randomUUID().toString();
        final UUID paymentId = testCreateRetrievePayment(account, null, externalPaymentKey, 1);

        final Payment payment = paymentApi.getPaymentByExternalKey(externalPaymentKey, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(payment.getPaymentId(), paymentId);

        UUID paymentTransactionId = payment.getTransactions().get(0).getTransactionId();
        paymentTransactionApi.createTransactionTags(paymentTransactionId, ImmutableList.<UUID>of(createdTagDefinition.getId()), requestOptions);

        final Tags paymentTransactionTags = paymentTransactionApi.getTransactionTags(paymentTransactionId, requestOptions);

        Assert.assertNotNull(paymentTransactionTags);
        Assert.assertEquals(paymentTransactionTags.get(0).getTagDefinitionName(), tagDefinitionName);
    }

    @Test(groups = "slow")
    public void testCreateTagForPaymentTransaction() throws Exception {
        UUID tagDefinitionId = UUID.randomUUID();
        String tagDefinitionName = "payment-transaction";
        TagDefinition tagDefinition = new TagDefinition(tagDefinitionId, false, tagDefinitionName, "description", ImmutableList.<ObjectType>of(ObjectType.TRANSACTION), null);
        final TagDefinition createdTagDefinition = tagDefinitionApi.createTagDefinition(tagDefinition, requestOptions);

        final Account account = createAccountWithDefaultPaymentMethod();
        final String externalPaymentKey = UUID.randomUUID().toString();
        final UUID paymentId = testCreateRetrievePayment(account, null, externalPaymentKey, 1);

        final Payment payment = paymentApi.getPaymentByExternalKey(externalPaymentKey, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(payment.getPaymentId(), paymentId);

        UUID paymentTransactionId = payment.getTransactions().get(0).getTransactionId();
        final Tags paymentTransactionTag = paymentTransactionApi.createTransactionTags(paymentTransactionId, ImmutableList.<UUID>of(createdTagDefinition.getId()), requestOptions);

        Assert.assertNotNull(paymentTransactionTag);
        Assert.assertEquals(paymentTransactionTag.get(0).getTagDefinitionName(), tagDefinitionName);
    }

    @Test(groups = "slow", description = "retrieve account logs")
    public void testPaymentAuditLogsWithHistory() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();
        final String externalPaymentKey = UUID.randomUUID().toString();
        final UUID paymentId = testCreateRetrievePayment(account, null, externalPaymentKey, 1);
        final Payment payment = paymentApi.getPaymentByExternalKey(externalPaymentKey, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(payment.getPaymentId(), paymentId);

        final List<AuditLog> paymentAuditLogWithHistory = paymentApi.getPaymentAuditLogsWithHistory(paymentId, requestOptions);
        assertEquals(paymentAuditLogWithHistory.size(), 8);
        assertEquals(paymentAuditLogWithHistory.get(0).getChangeType(), ChangeType.INSERT.toString());
        assertEquals(paymentAuditLogWithHistory.get(0).getObjectType(), ObjectType.PAYMENT);
        assertEquals(paymentAuditLogWithHistory.get(0).getObjectId(), paymentId);

        final LinkedHashMap history1 = (LinkedHashMap) paymentAuditLogWithHistory.get(0).getHistory();
        assertNotNull(history1);
        assertEquals(history1.get("stateName"), null );
        final LinkedHashMap history2 = (LinkedHashMap) paymentAuditLogWithHistory.get(1).getHistory();
        assertNotNull(history2);
        assertEquals(history2.get("stateName"), "AUTH_SUCCESS" );
        final LinkedHashMap history3 = (LinkedHashMap) paymentAuditLogWithHistory.get(2).getHistory();
        assertNotNull(history3);
        assertEquals(history3.get("stateName"), "AUTH_SUCCESS" );
        final LinkedHashMap history4 = (LinkedHashMap) paymentAuditLogWithHistory.get(3).getHistory();
        assertNotNull(history4);
        assertEquals(history4.get("stateName"), "CAPTURE_SUCCESS" );
        final LinkedHashMap history5 = (LinkedHashMap) paymentAuditLogWithHistory.get(4).getHistory();
        assertNotNull(history5);
        assertEquals(history5.get("stateName"), "CAPTURE_SUCCESS" );
        final LinkedHashMap history6 = (LinkedHashMap) paymentAuditLogWithHistory.get(5).getHistory();
        assertNotNull(history6);
        assertEquals(history6.get("stateName"), "CAPTURE_SUCCESS" );
        final LinkedHashMap history7 = (LinkedHashMap) paymentAuditLogWithHistory.get(6).getHistory();
        assertNotNull(history7);
        assertEquals(history7.get("stateName"), "CAPTURE_SUCCESS" );
        final LinkedHashMap history8 = (LinkedHashMap) paymentAuditLogWithHistory.get(7).getHistory();
        assertNotNull(history8);
        assertEquals(history8.get("stateName"), "REFUND_SUCCESS" );

    }

    private UUID testCreateRetrievePayment(final Account account, @Nullable final UUID paymentMethodId,
                                           final String paymentExternalKey, final int paymentNb) throws Exception {
        // Authorization
        final String authTransactionExternalKey = UUID.randomUUID().toString();
        final Payment authPayment = createVerifyTransaction(account, paymentMethodId, paymentExternalKey, authTransactionExternalKey, TransactionType.AUTHORIZE,
                                                            TransactionStatus.SUCCESS, BigDecimal.TEN, BigDecimal.TEN, ImmutableMap.<String, String>of(), paymentNb);

        // Capture 1
        final String capture1TransactionExternalKey = UUID.randomUUID().toString();
        final PaymentTransaction captureTransaction = new PaymentTransaction();
        captureTransaction.setPaymentId(authPayment.getPaymentId());
        captureTransaction.setAmount(BigDecimal.ONE);
        captureTransaction.setCurrency(account.getCurrency());
        captureTransaction.setPaymentExternalKey(paymentExternalKey);
        captureTransaction.setTransactionExternalKey(capture1TransactionExternalKey);
        // captureAuthorization is using paymentId
        final Payment capturedPayment1 = paymentApi.captureAuthorization(authPayment.getPaymentId(), captureTransaction, NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
        verifyPayment(account, paymentMethodId, capturedPayment1, paymentExternalKey, authTransactionExternalKey, TransactionType.AUTHORIZE, TransactionStatus.SUCCESS,
                      BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, 2, paymentNb);
        verifyPaymentTransaction(account, authPayment.getPaymentId(), paymentExternalKey, capturedPayment1.getTransactions().get(1),
                                 capture1TransactionExternalKey, captureTransaction.getAmount(), TransactionType.CAPTURE, TransactionStatus.SUCCESS);

        // Capture 2
        final String capture2TransactionExternalKey = UUID.randomUUID().toString();
        captureTransaction.setTransactionExternalKey(capture2TransactionExternalKey);
        // captureAuthorization is using externalKey
        captureTransaction.setPaymentId(null);
        final Payment capturedPayment2 = paymentApi.captureAuthorizationByExternalKey(captureTransaction, NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
        verifyPayment(account, paymentMethodId, capturedPayment2, paymentExternalKey, authTransactionExternalKey, TransactionType.AUTHORIZE, TransactionStatus.SUCCESS,
                      BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("2"), BigDecimal.ZERO, 3, paymentNb);
        verifyPaymentTransaction(account, authPayment.getPaymentId(), paymentExternalKey, capturedPayment2.getTransactions().get(2),
                                 capture2TransactionExternalKey, captureTransaction.getAmount(), TransactionType.CAPTURE, TransactionStatus.SUCCESS);

        // Refund
        final String refundTransactionExternalKey = UUID.randomUUID().toString();
        final PaymentTransaction refundTransaction = new PaymentTransaction();
        refundTransaction.setPaymentId(authPayment.getPaymentId());
        refundTransaction.setAmount(new BigDecimal("2"));
        refundTransaction.setCurrency(account.getCurrency());
        refundTransaction.setPaymentExternalKey(paymentExternalKey);
        refundTransaction.setTransactionExternalKey(refundTransactionExternalKey);
        final Payment refundPayment = paymentApi.refundPayment(authPayment.getPaymentId(), refundTransaction, NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
        verifyPayment(account, paymentMethodId, refundPayment, paymentExternalKey, authTransactionExternalKey, TransactionType.AUTHORIZE, TransactionStatus.SUCCESS,
                      BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("2"), new BigDecimal("2"), 4, paymentNb);
        verifyPaymentTransaction(account, authPayment.getPaymentId(), paymentExternalKey, refundPayment.getTransactions().get(3),
                                 refundTransactionExternalKey, refundTransaction.getAmount(), TransactionType.REFUND, TransactionStatus.SUCCESS);

        return authPayment.getPaymentId();
    }

    private Payment createVerifyTransaction(final Account account,
                                            @Nullable final UUID paymentMethodId,
                                            final String paymentExternalKey,
                                            final String transactionExternalKey,
                                            final TransactionType transactionType,
                                            final TransactionStatus transactionStatus,
                                            final BigDecimal transactionAmount,
                                            final BigDecimal authAmount,
                                            final Map<String, String> pluginProperties,
                                            final int paymentNb) throws KillBillClientException {
        final PaymentTransaction authTransaction = new PaymentTransaction();
        authTransaction.setAmount(transactionAmount);
        authTransaction.setCurrency(account.getCurrency());
        authTransaction.setPaymentExternalKey(paymentExternalKey);
        authTransaction.setTransactionExternalKey(transactionExternalKey);
        authTransaction.setTransactionType(transactionType);
        final Payment payment = accountApi.processPayment(account.getAccountId(), authTransaction, paymentMethodId, NULL_PLUGIN_NAMES, pluginProperties, requestOptions);

        verifyPayment(account, paymentMethodId, payment, paymentExternalKey, transactionExternalKey, transactionType, transactionStatus, transactionAmount, authAmount, BigDecimal.ZERO, BigDecimal.ZERO, 1, paymentNb);

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

        final Payments Payments = paymentApi.getPayments(null, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(Payments.size(), paymentNb);
        assertEquals(Payments.get(paymentNb - 1), payment);
    }

    private void verifyPayment(final Account account,
                               @Nullable final UUID paymentMethodId,
                               final Payment payment,
                               final String paymentExternalKey,
                               final String firstTransactionExternalKey,
                               final TransactionType firstTransactionType,
                               final TransactionStatus firstTransactionStatus,
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

        final Payments Payments = paymentApi.getPayments(null, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(Payments.size(), paymentNb);
        assertEquals(Payments.get(paymentNb - 1), payment);

        final Payment retrievedPayment = paymentApi.getPayment(payment.getPaymentId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(retrievedPayment, payment);

        final Payments paymentsForAccount = accountApi.getPaymentsForAccount(account.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(paymentsForAccount.size(), paymentNb);
        assertEquals(paymentsForAccount.get(paymentNb - 1), payment);
    }

    private void verifyPaymentTransaction(final Account account,
                                          final UUID paymentId,
                                          final String paymentExternalKey,
                                          final PaymentTransaction paymentTransaction,
                                          final String transactionExternalKey,
                                          @Nullable final BigDecimal amount,
                                          final TransactionType transactionType,
                                          final TransactionStatus transactionStatus) {
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
        verifyPayment(account, paymentMethodId, refundPayment, paymentExternalKey, authTransactionExternalKey, TransactionType.PURCHASE, TransactionStatus.SUCCESS, purchaseAmount, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 2, 1);
        verifyPaymentTransaction(account, refundPayment.getPaymentId(), paymentExternalKey, refundPayment.getTransactions().get(1), refundTransactionExternalKey, purchaseAmount, TransactionType.REFUND, TransactionStatus.PENDING);
    }
}
