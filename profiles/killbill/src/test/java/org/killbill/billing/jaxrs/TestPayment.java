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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.RequestOptions;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.ComboPaymentTransaction;
import org.killbill.billing.client.model.InvoicePayments;
import org.killbill.billing.client.model.Payment;
import org.killbill.billing.client.model.PaymentMethod;
import org.killbill.billing.client.model.PaymentMethodPluginDetail;
import org.killbill.billing.client.model.PaymentTransaction;
import org.killbill.billing.client.model.Payments;
import org.killbill.billing.client.model.PluginProperty;
import org.killbill.billing.client.model.TagDefinition;
import org.killbill.billing.client.model.Tags;
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
        mockPaymentProviderPlugin.clear();
    }

    @Test(groups = "slow")
    public void testWithFailedPayment() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod();

        mockPaymentProviderPlugin.makeNextPaymentFailWithError();

        final PaymentTransaction authTransaction = new PaymentTransaction();
        authTransaction.setAmount(BigDecimal.ONE);
        authTransaction.setCurrency(account.getCurrency());
        authTransaction.setTransactionType(TransactionType.AUTHORIZE.name());

        final Payment payment = killBillClient.createPayment(account.getAccountId(), account.getPaymentMethodId(), authTransaction,
                                                             ImmutableMap.<String, String>of(), requestOptions);
        final PaymentTransaction paymentTransaction = payment.getTransactions().get(0);
        assertEquals(paymentTransaction.getStatus(), TransactionStatus.PAYMENT_FAILURE.toString());
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
        authTransaction.setTransactionType(TransactionType.AUTHORIZE.name());

        final RequestOptions requestOptionsWithoutFollowLocation = RequestOptions.builder()
                                                                                 .withCreatedBy(createdBy)
                                                                                 .withReason(reason)
                                                                                 .withComment(comment)
                                                                                 .withFollowLocation(false)
                                                                                 .build();

        try {
            killBillClient.createPayment(account.getAccountId(), account.getPaymentMethodId(), authTransaction,
                                         ImmutableMap.<String, String>of(), requestOptionsWithoutFollowLocation);
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
        authTransaction.setTransactionType(TransactionType.AUTHORIZE.name());
        final Payment payment = killBillClient.createPayment(account.getAccountId(), account.getPaymentMethodId(), authTransaction, ImmutableMap.<String, String>of(), requestOptions);
        final PaymentTransaction paymentTransaction = payment.getTransactions().get(0);
        assertEquals(paymentTransaction.getStatus(), TransactionStatus.PLUGIN_FAILURE.toString());
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
            killBillClient.createPayment(account.getAccountId(), account.getPaymentMethodId(), authTransaction, ImmutableMap.<String, String>of(), requestOptions);
            fail();
        } catch (KillBillClientException e) {
            assertEquals(504, e.getResponse().getStatusCode());
        }
    }

    @Test(groups = "slow")
    public void testWithFailedPaymentAndScheduledAttemptsGetInvoicePayment() throws Exception {
        mockPaymentProviderPlugin.makeNextPaymentFailWithError();
        final Account account = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();
        // Getting Invoice #2 (first after Trial period)
        UUID failedInvoiceId = killBillClient.getInvoicesForAccount(account.getAccountId(), false, false, RequestOptions.empty()).get(1).getInvoiceId();

        HashMultimap<String, String> queryParams = HashMultimap.create();
        queryParams.put("withAttempts", "true");
        RequestOptions inputOptions = RequestOptions.builder()
                                                    .withCreatedBy(createdBy)
                                                    .withReason(reason)
                                                    .withComment(comment)
                                                    .withQueryParams(queryParams).build();

        InvoicePayments invoicePayments = killBillClient.getInvoicePayment(failedInvoiceId, inputOptions);

        Assert.assertEquals(invoicePayments.get(0).getTargetInvoiceId(), failedInvoiceId);
        Assert.assertNotNull(invoicePayments.get(0).getPaymentAttempts());
        Assert.assertEquals(invoicePayments.get(0).getPaymentAttempts().size(), 2);
        Assert.assertEquals(invoicePayments.get(0).getPaymentAttempts().get(0).getStateName(), "RETRIED");
        Assert.assertEquals(invoicePayments.get(0).getPaymentAttempts().get(1).getStateName(), "SCHEDULED");


        // Remove the future notification and check SCHEDULED does not appear any longer
        killBillClient.cancelScheduledPaymentTransaction(null, invoicePayments.get(0).getPaymentAttempts().get(1).getTransactionExternalKey(), inputOptions);
        invoicePayments = killBillClient.getInvoicePayment(failedInvoiceId, inputOptions);
        Assert.assertEquals(invoicePayments.get(0).getPaymentAttempts().size(), 1);
        Assert.assertEquals(invoicePayments.get(0).getPaymentAttempts().get(0).getStateName(), "RETRIED");
    }

    @Test(groups = "slow")
    public void testWithFailedPaymentAndScheduledAttemptsGetPaymentsForAccount() throws Exception {
        mockPaymentProviderPlugin.makeNextPaymentFailWithError();
        final Account account = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        HashMultimap<String, String> queryParams = HashMultimap.create();
        queryParams.put("withAttempts", "true");
        RequestOptions inputOptions = RequestOptions.builder()
                                                    .withCreatedBy(createdBy)
                                                    .withReason(reason)
                                                    .withComment(comment)
                                                    .withQueryParams(queryParams).build();

        Payments payments = killBillClient.getPaymentsForAccount(account.getAccountId(), inputOptions);

        Assert.assertNotNull(payments.get(0).getPaymentAttempts());
        Assert.assertEquals(payments.get(0).getPaymentAttempts().get(0).getStateName(), "RETRIED");
        Assert.assertEquals(payments.get(0).getPaymentAttempts().get(1).getStateName(), "SCHEDULED");
    }

    @Test(groups = "slow")
    public void testWithFailedPaymentAndScheduledAttemptsGetPayments() throws Exception {
        mockPaymentProviderPlugin.makeNextPaymentFailWithError();
        createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        HashMultimap<String, String> queryParams = HashMultimap.create();
        queryParams.put("withAttempts", "true");
        RequestOptions inputOptions = RequestOptions.builder()
                                                    .withCreatedBy(createdBy)
                                                    .withReason(reason)
                                                    .withComment(comment)
                                                    .withQueryParams(queryParams).build();

        Payments payments = killBillClient.getPayments(0L, 100L, null, new HashMap<String, String>(), AuditLevel.NONE, inputOptions);

        Assert.assertNotNull(payments.get(0).getPaymentAttempts());
        Assert.assertEquals(payments.get(0).getPaymentAttempts().get(0).getStateName(), "RETRIED");
        Assert.assertEquals(payments.get(0).getPaymentAttempts().get(1).getStateName(), "SCHEDULED");
    }

    @Test(groups = "slow")
    public void testWithFailedPaymentAndScheduledAttemptsSearchPayments() throws Exception {
        mockPaymentProviderPlugin.makeNextPaymentFailWithError();
        createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        HashMultimap<String, String> queryParams = HashMultimap.create();
        queryParams.put("withAttempts", "true");
        RequestOptions inputOptions = RequestOptions.builder()
                                                    .withCreatedBy(createdBy)
                                                    .withReason(reason)
                                                    .withComment(comment)
                                                    .withQueryParams(queryParams).build();

        Payments payments = killBillClient.searchPayments("", 0L, 100L, AuditLevel.NONE, inputOptions);

        Assert.assertNotNull(payments.get(0).getPaymentAttempts());
        Assert.assertEquals(payments.get(0).getPaymentAttempts().get(0).getStateName(), "RETRIED");
        Assert.assertEquals(payments.get(0).getPaymentAttempts().get(1).getStateName(), "SCHEDULED");
    }

    @Test(groups = "slow")
    public void testWithFailedPaymentAndScheduledAttemptsGetPaymentById() throws Exception {
        mockPaymentProviderPlugin.makeNextPaymentFailWithError();
        createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        Payments payments = killBillClient.searchPayments("", 0L, 100L, AuditLevel.NONE, requestOptions);
        Assert.assertNotNull(payments.get(0));
        Payment payment = killBillClient.getPayment(payments.get(0).getPaymentId(), false, true, ImmutableMap.<String, String>of(), AuditLevel.NONE, requestOptions);

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

        killBillClient.deletePaymentMethod(paymentMethodId, true, false, inputOptions);

        Tags accountTags = killBillClient.getAccountTags(account.getAccountId(), inputOptions);

        Assert.assertNotNull(accountTags);
        Assert.assertEquals(accountTags.get(0).getTagDefinitionName(), "AUTO_PAY_OFF");
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
            final Payment completedPaymentByPaymentId = killBillClient.completePayment(completeTransactionByPaymentId, pluginProperties, requestOptions);
            verifyPayment(account, paymentMethodId, completedPaymentByPaymentId, paymentExternalKey, authTransactionExternalKey, transactionType.toString(), pending, amount, authAmount, BigDecimal.ZERO, BigDecimal.ZERO, 1, paymentNb);

            // Second, only specify the payment external key
            final PaymentTransaction completeTransactionByPaymentExternalKey = new PaymentTransaction();
            completeTransactionByPaymentExternalKey.setPaymentExternalKey(initialPayment.getPaymentExternalKey());
            final Payment completedPaymentByExternalKey = killBillClient.completePayment(completeTransactionByPaymentExternalKey, pluginProperties, requestOptions);
            verifyPayment(account, paymentMethodId, completedPaymentByExternalKey, paymentExternalKey, authTransactionExternalKey, transactionType.toString(), pending, amount, authAmount, BigDecimal.ZERO, BigDecimal.ZERO, 1, paymentNb);

            // Third, specify the payment id and transaction external key
            final PaymentTransaction completeTransactionWithTypeAndKey = new PaymentTransaction();
            completeTransactionWithTypeAndKey.setPaymentId(initialPayment.getPaymentId());
            completeTransactionWithTypeAndKey.setTransactionExternalKey(authPaymentTransaction.getTransactionExternalKey());
            final Payment completedPaymentByTypeAndKey = killBillClient.completePayment(completeTransactionWithTypeAndKey, pluginProperties, requestOptions);
            verifyPayment(account, paymentMethodId, completedPaymentByTypeAndKey, paymentExternalKey, authTransactionExternalKey, transactionType.toString(), pending, amount, authAmount, BigDecimal.ZERO, BigDecimal.ZERO, 1, paymentNb);

            // Finally, specify the payment id and transaction id
            final PaymentTransaction completeTransactionWithTypeAndId = new PaymentTransaction();
            completeTransactionWithTypeAndId.setPaymentId(initialPayment.getPaymentId());
            completeTransactionWithTypeAndId.setTransactionId(authPaymentTransaction.getTransactionId());
            final Payment completedPaymentByTypeAndId = killBillClient.completePayment(completeTransactionWithTypeAndId, pluginProperties, requestOptions);
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
        final Payment completedPaymentByPaymentId = killBillClient.completePayment(completeTransactionByPaymentId, pluginProperties, requestOptions);
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
            killBillClient.completePayment(completeTransactionByPaymentIdAndInvalidTransactionId, pluginProperties, requestOptions);
            fail("Payment completion should fail when invalid transaction id has been provided" );
        } catch (final KillBillClientException expected) {
        }

        final PaymentTransaction completeTransactionByPaymentIdAndTransactionId = new PaymentTransaction();
        completeTransactionByPaymentIdAndTransactionId.setPaymentId(initialPayment.getPaymentId());
        completeTransactionByPaymentIdAndTransactionId.setTransactionId(initialPayment.getTransactions().get(0).getTransactionId());
        final Payment completedPaymentByPaymentId = killBillClient.completePayment(completeTransactionByPaymentIdAndTransactionId, pluginProperties, requestOptions);
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
            killBillClient.completePayment(completeTransactionByPaymentIdAndInvalidTransactionExternalKey, pluginProperties, requestOptions);
            fail("Payment completion should fail when invalid transaction externalKey has been provided" );
        } catch (final KillBillClientException expected) {
        }

        final PaymentTransaction completeTransactionByPaymentIdAndTransactionExternalKey = new PaymentTransaction();
        completeTransactionByPaymentIdAndTransactionExternalKey.setPaymentId(initialPayment.getPaymentId());
        completeTransactionByPaymentIdAndTransactionExternalKey.setTransactionExternalKey(authTransactionExternalKey);
        final Payment completedPaymentByPaymentId = killBillClient.completePayment(completeTransactionByPaymentIdAndTransactionExternalKey, pluginProperties, requestOptions);
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
            killBillClient.completePayment(completeTransactionByPaymentIdAndInvalidTransactionType, pluginProperties, requestOptions);
            fail("Payment completion should fail when invalid transaction type has been provided" );
        } catch (final KillBillClientException expected) {
        }

        final PaymentTransaction completeTransactionByPaymentIdAndTransactionType = new PaymentTransaction();
        completeTransactionByPaymentIdAndTransactionType.setPaymentId(initialPayment.getPaymentId());
        completeTransactionByPaymentIdAndTransactionType.setTransactionType(transactionType.name());
        final Payment completedPaymentByPaymentId = killBillClient.completePayment(completeTransactionByPaymentIdAndTransactionType, pluginProperties, requestOptions);
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
        final Payment completedPaymentByPaymentId = killBillClient.completePayment(completeTransactionWithTypeAndKey, pluginProperties, requestOptions);
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

        // The payment was already completed, it should succeed (no-op)
        final PaymentTransaction completeTransactionByPaymentId = new PaymentTransaction();
        completeTransactionByPaymentId.setPaymentId(initialPayment.getPaymentId());
        killBillClient.completePayment(completeTransactionByPaymentId, pluginProperties, requestOptions);
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
        final Payment refundPayment = killBillClient.refundPayment(refundTransaction, null, pluginProperties, requestOptions);
        verifyPaymentWithPendingRefund(account, paymentMethodId, paymentExternalKey, purchaseTransactionExternalKey, purchaseAmount, refundTransactionExternalKey, refundPayment);


        final PaymentTransaction completeTransactionWithTypeAndKey = new PaymentTransaction();
        completeTransactionWithTypeAndKey.setPaymentId(refundPayment.getPaymentId());
        completeTransactionWithTypeAndKey.setTransactionExternalKey(refundTransactionExternalKey);
        final Payment completedPaymentByTypeAndKey = killBillClient.completePayment(completeTransactionWithTypeAndKey, pluginProperties, requestOptions);
        verifyPaymentWithPendingRefund(account, paymentMethodId, paymentExternalKey, purchaseTransactionExternalKey, purchaseAmount, refundTransactionExternalKey, completedPaymentByTypeAndKey);

        // Also, it should work if we specify the payment id and transaction id
        final PaymentTransaction completeTransactionWithTypeAndId = new PaymentTransaction();
        completeTransactionWithTypeAndId.setPaymentId(refundPayment.getPaymentId());
        completeTransactionWithTypeAndId.setTransactionId(refundPayment.getTransactions().get(1).getTransactionId());
        final Payment completedPaymentByTypeAndId = killBillClient.completePayment(completeTransactionWithTypeAndId, pluginProperties, requestOptions);
        verifyPaymentWithPendingRefund(account, paymentMethodId, paymentExternalKey, purchaseTransactionExternalKey, purchaseAmount, refundTransactionExternalKey, completedPaymentByTypeAndId);
    }

    @Test(groups = "slow")
    public void testComboAuthorization() throws Exception {
        final Account accountJson = getAccount();
        accountJson.setAccountId(null);
        final String paymentExternalKey = UUID.randomUUID().toString();

        final ComboPaymentTransaction comboPaymentTransaction = createComboPaymentTransaction(accountJson, paymentExternalKey);

        final Payment payment = killBillClient.createPayment(comboPaymentTransaction, ImmutableMap.<String, String>of(), requestOptions);
        verifyComboPayment(payment, paymentExternalKey, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, 1, 1);

        // Void payment using externalKey
        final String voidTransactionExternalKey = UUID.randomUUID().toString();
        final Payment voidPayment = killBillClient.voidPayment(null, paymentExternalKey, voidTransactionExternalKey, null, ImmutableMap.<String, String>of(), requestOptions);
        verifyPaymentTransaction(accountJson, voidPayment.getPaymentId(), paymentExternalKey, voidPayment.getTransactions().get(1),
                                 voidTransactionExternalKey, null, "VOID", "SUCCESS");
    }

    @Test(groups = "slow")
    public void testComboAuthorizationAbortedPayment() throws Exception {
        final Account accountJson = getAccount();
        accountJson.setAccountId(null);
        final String paymentExternalKey = UUID.randomUUID().toString();
        final ComboPaymentTransaction comboPaymentTransaction = createComboPaymentTransaction(accountJson, paymentExternalKey);

        mockPaymentControlProviderPlugin.setAborted(true);
        try {
            killBillClient.createPayment(comboPaymentTransaction, Arrays.asList(MockPaymentControlProviderPlugin.PLUGIN_NAME), ImmutableMap.<String, String>of(), requestOptions);
            fail();
        } catch (KillBillClientException e) {
            assertEquals(e.getResponse().getStatusCode(), 422);
        }
        assertFalse(mockPaymentControlProviderPlugin.isOnFailureCallExecuted());
        assertFalse(mockPaymentControlProviderPlugin.isOnSuccessCallExecuted());
    }

    @Test(groups = "slow")
    public void testComboAuthorizationControlPluginException() throws Exception {
        final Account accountJson = getAccount();
        accountJson.setAccountId(null);
        final String paymentExternalKey = UUID.randomUUID().toString();
        final ComboPaymentTransaction comboPaymentTransaction = createComboPaymentTransaction(accountJson, paymentExternalKey);

        mockPaymentControlProviderPlugin.throwsException(new IllegalStateException());
        try {
            killBillClient.createPayment(comboPaymentTransaction, Arrays.asList(MockPaymentControlProviderPlugin.PLUGIN_NAME), ImmutableMap.<String, String>of(), requestOptions);
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

        final Payment payment = killBillClient.createPayment(comboPaymentTransaction, ImmutableMap.<String, String>of(), requestOptions);
        // Client returns null in case of a 404
        Assert.assertNull(payment);
    }

    @Test(groups = "slow")
    public void testGetTagsForPaymentTransaction() throws Exception {
        UUID tagDefinitionId = UUID.randomUUID();
        String tagDefinitionName = "payment-transaction";
        TagDefinition tagDefinition = new TagDefinition(tagDefinitionId, false, tagDefinitionName, "description", null);
        final TagDefinition createdTagDefinition = killBillClient.createTagDefinition(tagDefinition, requestOptions);

        final Account account = createAccountWithDefaultPaymentMethod();
        final String externalPaymentKey = UUID.randomUUID().toString();
        final UUID paymentId = testCreateRetrievePayment(account, null, externalPaymentKey, 1);

        final Payment payment = killBillClient.getPaymentByExternalKey(externalPaymentKey, requestOptions);
        assertEquals(payment.getPaymentId(), paymentId);

        UUID paymentTransactionId = payment.getTransactions().get(0).getTransactionId();
        killBillClient.createPaymentTransactionTag(paymentTransactionId, createdTagDefinition.getId(), requestOptions);

        final Tags paymentTransactionTags = killBillClient.getPaymentTransactionTags(paymentTransactionId, requestOptions);

        Assert.assertNotNull(paymentTransactionTags);
        Assert.assertEquals(paymentTransactionTags.get(0).getTagDefinitionName(), tagDefinitionName);
    }

    @Test(groups = "slow")
    public void testCreateTagForPaymentTransaction() throws Exception {
        UUID tagDefinitionId = UUID.randomUUID();
        String tagDefinitionName = "payment-transaction";
        TagDefinition tagDefinition = new TagDefinition(tagDefinitionId, false, tagDefinitionName, "description", null);
        final TagDefinition createdTagDefinition = killBillClient.createTagDefinition(tagDefinition, requestOptions);

        final Account account = createAccountWithDefaultPaymentMethod();
        final String externalPaymentKey = UUID.randomUUID().toString();
        final UUID paymentId = testCreateRetrievePayment(account, null, externalPaymentKey, 1);

        final Payment payment = killBillClient.getPaymentByExternalKey(externalPaymentKey, requestOptions);
        assertEquals(payment.getPaymentId(), paymentId);

        UUID paymentTransactionId = payment.getTransactions().get(0).getTransactionId();
        final Tags paymentTransactionTag = killBillClient.createPaymentTransactionTag(paymentTransactionId, createdTagDefinition.getId(), requestOptions);

        Assert.assertNotNull(paymentTransactionTag);
        Assert.assertEquals(paymentTransactionTag.get(0).getTagDefinitionName(), tagDefinitionName);
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
        final Payment capturedPayment1 = killBillClient.captureAuthorization(captureTransaction, requestOptions);
        verifyPayment(account, paymentMethodId, capturedPayment1, paymentExternalKey, authTransactionExternalKey, "AUTHORIZE", "SUCCESS",
                      BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, 2, paymentNb);
        verifyPaymentTransaction(account, authPayment.getPaymentId(), paymentExternalKey, capturedPayment1.getTransactions().get(1),
                                 capture1TransactionExternalKey, captureTransaction.getAmount(), "CAPTURE", "SUCCESS");

        // Capture 2
        final String capture2TransactionExternalKey = UUID.randomUUID().toString();
        captureTransaction.setTransactionExternalKey(capture2TransactionExternalKey);
        // captureAuthorization is using externalKey
        captureTransaction.setPaymentId(null);
        final Payment capturedPayment2 = killBillClient.captureAuthorization(captureTransaction, requestOptions);
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
        final Payment refundPayment = killBillClient.refundPayment(refundTransaction, requestOptions);
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
        final Payment payment = killBillClient.createPayment(account.getAccountId(), paymentMethodId, authTransaction, pluginProperties, requestOptions);

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
