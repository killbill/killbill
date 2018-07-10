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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.control.plugin.api.OnFailurePaymentControlResult;
import org.killbill.billing.control.plugin.api.OnSuccessPaymentControlResult;
import org.killbill.billing.control.plugin.api.PaymentControlApiException;
import org.killbill.billing.control.plugin.api.PaymentControlContext;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.control.plugin.api.PriorPaymentControlResult;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.provider.DefaultNoOpPaymentMethodPlugin;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.payment.retry.DefaultFailureCallResult;
import org.killbill.billing.payment.retry.DefaultOnSuccessPaymentControlResult;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class TestPaymentApiWithControl extends PaymentTestSuiteWithEmbeddedDB {

    private static final PaymentOptions PAYMENT_OPTIONS = new PaymentOptions() {
        @Override
        public boolean isExternalPayment() {
            return false;
        }

        @Override
        public List<String> getPaymentControlPluginNames() {
            return ImmutableList.of(TestPaymentControlPluginApi.PLUGIN_NAME);
        }
    };

    @Inject
    private OSGIServiceRegistration<PaymentControlPluginApi> controlPluginRegistry;

    private Account account;
    private TestPaymentControlPluginApi testPaymentControlPluginApi;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        account = testHelper.createTestAccount("bobo@gmail.com", true);

        testPaymentControlPluginApi = new TestPaymentControlPluginApi();
        controlPluginRegistry.registerService(new OSGIServiceDescriptor() {
                                                  @Override
                                                  public String getPluginSymbolicName() {
                                                      return null;
                                                  }

                                                  @Override
                                                  public String getPluginName() {
                                                      return TestPaymentControlPluginApi.PLUGIN_NAME;
                                                  }

                                                  @Override
                                                  public String getRegistrationName() {
                                                      return TestPaymentControlPluginApi.PLUGIN_NAME;
                                                  }
                                              },
                                              testPaymentControlPluginApi);
    }

    // Verify Payment control API can be used to change the paymentMethodId on the fly and this is reflected in the created Payment.
    @Test(groups = "slow")
    public void testCreateAuthWithControl() throws PaymentApiException {
        final PaymentMethodPlugin paymentMethodInfo = new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), false, null);
        final UUID newPaymentMethodId = paymentApi.addPaymentMethod(account, null, MockPaymentProviderPlugin.PLUGIN_NAME, false, paymentMethodInfo, ImmutableList.<PluginProperty>of(), callContext);
        testPaymentControlPluginApi.setNewPaymentMethodId(newPaymentMethodId);

        final Payment payment = paymentApi.createAuthorizationWithPaymentControl(account, account.getPaymentMethodId(), null, BigDecimal.TEN, Currency.USD, null,null, UUID.randomUUID().toString(),
                                                                                 ImmutableList.<PluginProperty>of(), PAYMENT_OPTIONS, callContext);
        Assert.assertEquals(payment.getPaymentMethodId(), newPaymentMethodId);

        verifyOnSuccess(payment.getId(),
                        payment.getExternalKey(),
                        payment.getTransactions().get(0).getId(),
                        payment.getTransactions().get(0).getExternalKey(),
                        BigDecimal.TEN,
                        Currency.USD);
    }

    @Test(groups = "slow")
    public void testCreateAuthPendingWithControlCompleteWithControl() throws PaymentApiException {
        final String paymentTransactionExternalKey = UUID.randomUUID().toString();
        final BigDecimal requestedAmount = BigDecimal.TEN;
        final Iterable<PluginProperty> pendingPluginProperties = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.PENDING, false));

        Payment payment = paymentApi.createAuthorizationWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null,UUID.randomUUID().toString(),
                                                                           paymentTransactionExternalKey, pendingPluginProperties, PAYMENT_OPTIONS, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());

        verifyOnSuccess(payment.getId(),
                        payment.getExternalKey(),
                        payment.getTransactions().get(0).getId(),
                        payment.getTransactions().get(0).getExternalKey(),
                        requestedAmount,
                        Currency.USD);

        payment = paymentApi.createAuthorizationWithPaymentControl(account, payment.getPaymentMethodId(), payment.getId(), requestedAmount, payment.getCurrency(), null,payment.getExternalKey(),
                                                                   payment.getTransactions().get(0).getExternalKey(), ImmutableList.<PluginProperty>of(), PAYMENT_OPTIONS, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertEquals(payment.getTransactions().get(0).getExternalKey(), paymentTransactionExternalKey);

        verifyPriorAndOnSuccess(payment.getId(),
                                payment.getExternalKey(),
                                payment.getTransactions().get(0).getId(),
                                payment.getTransactions().get(0).getExternalKey(),
                                requestedAmount,
                                Currency.USD);
    }

    @Test(groups = "slow")
    public void testCreateAuthUnknownWithControlCompleteWithControl() throws PaymentApiException {
        final String paymentTransactionExternalKey = UUID.randomUUID().toString();
        final BigDecimal requestedAmount = BigDecimal.TEN;
        final Iterable<PluginProperty> pendingPluginProperties = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.UNDEFINED, false));

        Payment payment = paymentApi.createAuthorizationWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null,UUID.randomUUID().toString(),
                                                                           paymentTransactionExternalKey, pendingPluginProperties, PAYMENT_OPTIONS, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());

        verifyOnFailure(payment.getId(),
                        payment.getExternalKey(),
                        payment.getTransactions().get(0).getId(),
                        payment.getTransactions().get(0).getExternalKey(),
                        BigDecimal.ZERO,
                        Currency.USD);

        try {
            payment = paymentApi.createAuthorizationWithPaymentControl(account, payment.getPaymentMethodId(), payment.getId(), requestedAmount, payment.getCurrency(), null,payment.getExternalKey(),
                                                                       payment.getTransactions().get(0).getExternalKey(), ImmutableList.<PluginProperty>of(), PAYMENT_OPTIONS, callContext);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
        }
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.UNKNOWN);
        Assert.assertEquals(payment.getTransactions().get(0).getExternalKey(), paymentTransactionExternalKey);

        verifyPriorAndOnFailure(payment.getId(),
                                payment.getExternalKey(),
                                payment.getTransactions().get(0).getId(),
                                payment.getTransactions().get(0).getExternalKey(),
                                BigDecimal.ZERO,
                                Currency.USD);
    }

    @Test(groups = "slow")
    public void testCreateAuthSuccessCapturePendingWithControlCompleteWithControl() throws PaymentApiException {
        final BigDecimal requestedAmount = BigDecimal.TEN;

        Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null,UUID.randomUUID().toString(),
                                                         UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());

        final String paymentTransactionExternalKey = UUID.randomUUID().toString();
        final Iterable<PluginProperty> pendingPluginProperties = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.PENDING, false));

        payment = paymentApi.createCaptureWithPaymentControl(account, payment.getId(), requestedAmount, payment.getCurrency(), null,paymentTransactionExternalKey,
                                                             pendingPluginProperties, PAYMENT_OPTIONS, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 2);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(1)).getAttemptId());
        Assert.assertEquals(payment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.PENDING);
        Assert.assertEquals(payment.getTransactions().get(1).getExternalKey(), paymentTransactionExternalKey);

        verifyOnSuccessForFollowOnTransaction(payment.getId(),
                                              payment.getExternalKey(),
                                              payment.getTransactions().get(1).getId(),
                                              payment.getTransactions().get(1).getExternalKey(),
                                              requestedAmount,
                                              Currency.USD);

        payment = paymentApi.createCaptureWithPaymentControl(account, payment.getId(), requestedAmount, payment.getCurrency(), null,paymentTransactionExternalKey,
                                                             ImmutableList.<PluginProperty>of(), PAYMENT_OPTIONS, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getTransactions().size(), 2);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(1)).getAttemptId());
        Assert.assertEquals(payment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(payment.getTransactions().get(1).getExternalKey(), paymentTransactionExternalKey);

        verifyPriorAndOnSuccess(payment.getId(),
                                payment.getExternalKey(),
                                payment.getTransactions().get(1).getId(),
                                payment.getTransactions().get(1).getExternalKey(),
                                requestedAmount,
                                Currency.USD);
    }

    @Test(groups = "slow")
    public void testCreateAuthSuccessCaptureUnknownWithControlCompleteWithControl() throws PaymentApiException {
        final BigDecimal requestedAmount = BigDecimal.TEN;

        Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null,UUID.randomUUID().toString(),
                                                         UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());

        final String paymentTransactionExternalKey = UUID.randomUUID().toString();
        final Iterable<PluginProperty> pendingPluginProperties = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.UNDEFINED, false));

        payment = paymentApi.createCaptureWithPaymentControl(account, payment.getId(), requestedAmount, payment.getCurrency(), null,paymentTransactionExternalKey,
                                                             pendingPluginProperties, PAYMENT_OPTIONS, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 2);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(1)).getAttemptId());
        Assert.assertEquals(payment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.UNKNOWN);
        Assert.assertEquals(payment.getTransactions().get(1).getExternalKey(), paymentTransactionExternalKey);

        verifyOnFailureForFollowOnTransaction(payment.getId(),
                                              payment.getExternalKey(),
                                              payment.getTransactions().get(1).getId(),
                                              payment.getTransactions().get(1).getExternalKey(),
                                              BigDecimal.ZERO,
                                              Currency.USD);

        try {
            payment = paymentApi.createCaptureWithPaymentControl(account, payment.getId(), requestedAmount, payment.getCurrency(), null,paymentTransactionExternalKey,
                                                                 pendingPluginProperties, PAYMENT_OPTIONS, callContext);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
        }
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 2);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(1)).getAttemptId());
        Assert.assertEquals(payment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.UNKNOWN);
        Assert.assertEquals(payment.getTransactions().get(1).getExternalKey(), paymentTransactionExternalKey);

        verifyPriorAndOnFailure(payment.getId(),
                                payment.getExternalKey(),
                                payment.getTransactions().get(1).getId(),
                                payment.getTransactions().get(1).getExternalKey(),
                                BigDecimal.ZERO,
                                Currency.USD);
    }

    @Test(groups = "slow")
    public void testCreateAuthPendingWithControlCompleteNoControl() throws PaymentApiException {
        final String paymentTransactionExternalKey = UUID.randomUUID().toString();
        final BigDecimal requestedAmount = BigDecimal.TEN;
        final Iterable<PluginProperty> pendingPluginProperties = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.PENDING, false));

        Payment payment = paymentApi.createAuthorizationWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null,UUID.randomUUID().toString(),
                                                                           paymentTransactionExternalKey, pendingPluginProperties, PAYMENT_OPTIONS, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());

        verifyOnSuccess(payment.getId(),
                        payment.getExternalKey(),
                        payment.getTransactions().get(0).getId(),
                        payment.getTransactions().get(0).getExternalKey(),
                        requestedAmount,
                        Currency.USD);

        payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), payment.getId(), requestedAmount, payment.getCurrency(), null,payment.getExternalKey(),
                                                 payment.getTransactions().get(0).getExternalKey(), ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertEquals(payment.getTransactions().get(0).getExternalKey(), paymentTransactionExternalKey);
    }

    @Test(groups = "slow")
    public void testCreateAuthUnknownWithControlCompleteNoControl() throws PaymentApiException {
        final String paymentTransactionExternalKey = UUID.randomUUID().toString();
        final BigDecimal requestedAmount = BigDecimal.TEN;
        final Iterable<PluginProperty> pendingPluginProperties = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.UNDEFINED, false));

        Payment payment = paymentApi.createAuthorizationWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null,UUID.randomUUID().toString(),
                                                                           paymentTransactionExternalKey, pendingPluginProperties, PAYMENT_OPTIONS, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());

        verifyOnFailure(payment.getId(),
                        payment.getExternalKey(),
                        payment.getTransactions().get(0).getId(),
                        payment.getTransactions().get(0).getExternalKey(),
                        BigDecimal.ZERO,
                        Currency.USD);

        try {
            payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), payment.getId(), requestedAmount, payment.getCurrency(), null,payment.getExternalKey(),
                                                     payment.getTransactions().get(0).getExternalKey(), ImmutableList.<PluginProperty>of(), callContext);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
        }
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.UNKNOWN);
        Assert.assertEquals(payment.getTransactions().get(0).getExternalKey(), paymentTransactionExternalKey);
    }

    @Test(groups = "slow")
    public void testCreateAuthSuccessCapturePendingWithControlCompleteNoControl() throws PaymentApiException {
        final BigDecimal requestedAmount = BigDecimal.TEN;

        Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null,UUID.randomUUID().toString(),
                                                         UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());

        final String paymentTransactionExternalKey = UUID.randomUUID().toString();
        final Iterable<PluginProperty> pendingPluginProperties = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.PENDING, false));

        payment = paymentApi.createCaptureWithPaymentControl(account, payment.getId(), requestedAmount, payment.getCurrency(), null,paymentTransactionExternalKey,
                                                             pendingPluginProperties, PAYMENT_OPTIONS, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 2);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(1)).getAttemptId());
        Assert.assertEquals(payment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.PENDING);
        Assert.assertEquals(payment.getTransactions().get(1).getExternalKey(), paymentTransactionExternalKey);

        verifyOnSuccessForFollowOnTransaction(payment.getId(),
                                              payment.getExternalKey(),
                                              payment.getTransactions().get(1).getId(),
                                              payment.getTransactions().get(1).getExternalKey(),
                                              requestedAmount,
                                              Currency.USD);

        payment = paymentApi.createCapture(account, payment.getId(), requestedAmount, payment.getCurrency(), null,paymentTransactionExternalKey, ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getTransactions().size(), 2);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(1)).getAttemptId());
        Assert.assertEquals(payment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(payment.getTransactions().get(1).getExternalKey(), paymentTransactionExternalKey);
    }

    @Test(groups = "slow")
    public void testCreateAuthSuccessCaptureUnknownWithControlCompleteNoControl() throws PaymentApiException {
        final BigDecimal requestedAmount = BigDecimal.TEN;

        Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null,UUID.randomUUID().toString(),
                                                         UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());

        final String paymentTransactionExternalKey = UUID.randomUUID().toString();
        final Iterable<PluginProperty> pendingPluginProperties = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.UNDEFINED, false));

        payment = paymentApi.createCaptureWithPaymentControl(account, payment.getId(), requestedAmount, payment.getCurrency(), null,paymentTransactionExternalKey,
                                                             pendingPluginProperties, PAYMENT_OPTIONS, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 2);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(1)).getAttemptId());
        Assert.assertEquals(payment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.UNKNOWN);
        Assert.assertEquals(payment.getTransactions().get(1).getExternalKey(), paymentTransactionExternalKey);

        verifyOnFailureForFollowOnTransaction(payment.getId(),
                                              payment.getExternalKey(),
                                              payment.getTransactions().get(1).getId(),
                                              payment.getTransactions().get(1).getExternalKey(),
                                              BigDecimal.ZERO,
                                              Currency.USD);

        try {
            payment = paymentApi.createCapture(account, payment.getId(), requestedAmount, payment.getCurrency(), null,paymentTransactionExternalKey, ImmutableList.<PluginProperty>of(), callContext);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
        }
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 2);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(1)).getAttemptId());
        Assert.assertEquals(payment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.UNKNOWN);
        Assert.assertEquals(payment.getTransactions().get(1).getExternalKey(), paymentTransactionExternalKey);
    }

    @Test(groups = "slow")
    public void testCreateAuthPendingNoControlCompleteWithControl() throws PaymentApiException {
        final String paymentTransactionExternalKey = UUID.randomUUID().toString();
        final BigDecimal requestedAmount = BigDecimal.TEN;
        final Iterable<PluginProperty> pendingPluginProperties = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.PENDING, false));

        Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null,UUID.randomUUID().toString(),
                                                         paymentTransactionExternalKey, pendingPluginProperties, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());

        payment = paymentApi.createAuthorizationWithPaymentControl(account, payment.getPaymentMethodId(), payment.getId(), requestedAmount, payment.getCurrency(), null,payment.getExternalKey(),
                                                                   payment.getTransactions().get(0).getExternalKey(), ImmutableList.<PluginProperty>of(), PAYMENT_OPTIONS, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertEquals(payment.getTransactions().get(0).getExternalKey(), paymentTransactionExternalKey);

        verifyPriorAndOnSuccess(payment.getId(),
                                payment.getExternalKey(),
                                payment.getTransactions().get(0).getId(),
                                payment.getTransactions().get(0).getExternalKey(),
                                requestedAmount,
                                Currency.USD);
    }

    @Test(groups = "slow")
    public void testCreateAuthUnknownNoControlCompleteWithControl() throws PaymentApiException {
        final String paymentTransactionExternalKey = UUID.randomUUID().toString();
        final BigDecimal requestedAmount = BigDecimal.TEN;
        final Iterable<PluginProperty> pendingPluginProperties = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.UNDEFINED, false));

        Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null,UUID.randomUUID().toString(),
                                                         paymentTransactionExternalKey, pendingPluginProperties, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());

        try {
            payment = paymentApi.createAuthorizationWithPaymentControl(account, payment.getPaymentMethodId(), payment.getId(), requestedAmount, payment.getCurrency(), null,payment.getExternalKey(),
                                                                       payment.getTransactions().get(0).getExternalKey(), ImmutableList.<PluginProperty>of(), PAYMENT_OPTIONS, callContext);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
        }
        Assert.assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.UNKNOWN);
        Assert.assertEquals(payment.getTransactions().get(0).getExternalKey(), paymentTransactionExternalKey);

        verifyPriorAndOnFailure(payment.getId(),
                                payment.getExternalKey(),
                                payment.getTransactions().get(0).getId(),
                                payment.getTransactions().get(0).getExternalKey(),
                                BigDecimal.ZERO,
                                Currency.USD);
    }

    @Test(groups = "slow")
    public void testCreateAuthSuccessCapturePendingNoControlCompleteWithControl() throws PaymentApiException {
        final BigDecimal requestedAmount = BigDecimal.TEN;

        Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null,UUID.randomUUID().toString(),
                                                         UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());

        final String paymentTransactionExternalKey = UUID.randomUUID().toString();
        final Iterable<PluginProperty> pendingPluginProperties = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.PENDING, false));

        payment = paymentApi.createCapture(account, payment.getId(), requestedAmount, payment.getCurrency(), null,paymentTransactionExternalKey, pendingPluginProperties, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 2);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(1)).getAttemptId());
        Assert.assertEquals(payment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.PENDING);
        Assert.assertEquals(payment.getTransactions().get(1).getExternalKey(), paymentTransactionExternalKey);

        payment = paymentApi.createCaptureWithPaymentControl(account, payment.getId(), requestedAmount, payment.getCurrency(), null,paymentTransactionExternalKey,
                                                             ImmutableList.<PluginProperty>of(), PAYMENT_OPTIONS, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getTransactions().size(), 2);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(1)).getAttemptId());
        Assert.assertEquals(payment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(payment.getTransactions().get(1).getExternalKey(), paymentTransactionExternalKey);

        verifyPriorAndOnSuccess(payment.getId(),
                                payment.getExternalKey(),
                                payment.getTransactions().get(1).getId(),
                                payment.getTransactions().get(1).getExternalKey(),
                                requestedAmount,
                                Currency.USD);
    }

    @Test(groups = "slow")
    public void testCreateAuthSuccessCaptureUnknownNoControlCompleteWithControl() throws PaymentApiException {
        final BigDecimal requestedAmount = BigDecimal.TEN;

        Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null,UUID.randomUUID().toString(),
                                                         UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());

        final String paymentTransactionExternalKey = UUID.randomUUID().toString();
        final Iterable<PluginProperty> pendingPluginProperties = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.UNDEFINED, false));

        payment = paymentApi.createCapture(account, payment.getId(), requestedAmount, payment.getCurrency(), null,paymentTransactionExternalKey, pendingPluginProperties, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 2);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(1)).getAttemptId());
        Assert.assertEquals(payment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.UNKNOWN);
        Assert.assertEquals(payment.getTransactions().get(1).getExternalKey(), paymentTransactionExternalKey);

        try {
            payment = paymentApi.createCaptureWithPaymentControl(account, payment.getId(), requestedAmount, payment.getCurrency(), null,paymentTransactionExternalKey,
                                                                 ImmutableList.<PluginProperty>of(), PAYMENT_OPTIONS, callContext);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
        }
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 2);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(1)).getAttemptId());
        Assert.assertEquals(payment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.UNKNOWN);
        Assert.assertEquals(payment.getTransactions().get(1).getExternalKey(), paymentTransactionExternalKey);

        verifyPriorAndOnFailure(payment.getId(),
                                payment.getExternalKey(),
                                payment.getTransactions().get(1).getId(),
                                payment.getTransactions().get(1).getExternalKey(),
                                BigDecimal.ZERO,
                                Currency.USD);
    }

    @Test(groups = "slow")
    public void testCreateAuthWithControlCaptureNoControl() throws PaymentApiException {
        final BigDecimal requestedAmount = BigDecimal.TEN;

        Payment payment = paymentApi.createAuthorizationWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null,UUID.randomUUID().toString(),
                                                                           UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), PAYMENT_OPTIONS, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());

        verifyOnSuccess(payment.getId(),
                        payment.getExternalKey(),
                        payment.getTransactions().get(0).getId(),
                        payment.getTransactions().get(0).getExternalKey(),
                        requestedAmount,
                        Currency.USD);

        payment = paymentApi.createCapture(account, payment.getId(), payment.getAuthAmount(), payment.getCurrency(), null,UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getTransactions().size(), 2);
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(1)).getAttemptId());
    }

    @Test(groups = "slow")
    public void testCreateAuthNoControlCaptureWithControl() throws PaymentApiException {
        final BigDecimal requestedAmount = BigDecimal.TEN;

        Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null,UUID.randomUUID().toString(),
                                                         UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());

        payment = paymentApi.createCaptureWithPaymentControl(account, payment.getId(), payment.getAuthAmount(), payment.getCurrency(), null,UUID.randomUUID().toString(),
                                                             ImmutableList.<PluginProperty>of(), PAYMENT_OPTIONS, callContext);
        Assert.assertEquals(payment.getAuthAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getCapturedAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getTransactions().size(), 2);
        Assert.assertNull(((DefaultPaymentTransaction) payment.getTransactions().get(0)).getAttemptId());
        Assert.assertNotNull(((DefaultPaymentTransaction) payment.getTransactions().get(1)).getAttemptId());

        verifyOnSuccessForFollowOnTransaction(payment.getId(),
                                              payment.getExternalKey(),
                                              payment.getTransactions().get(1).getId(),
                                              payment.getTransactions().get(1).getExternalKey(),
                                              requestedAmount,
                                              Currency.USD);
    }

    @Test(groups = "slow")
    public void testAddPaymentMethodWithControl() throws PaymentApiException {
        final PaymentMethodPlugin paymentMethodInfo = new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), false, null);
        testPaymentControlPluginApi.setNewPaymentMethodName(MockPaymentProviderPlugin.PLUGIN_NAME);
        final UUID newPaymentMethodId = paymentApi.addPaymentMethodWithPaymentControl(account, null, "SomeDummyValueToBeChanged", false, paymentMethodInfo, ImmutableList.<PluginProperty>of(), PAYMENT_OPTIONS, callContext);


        final PaymentMethod paymentMethod = paymentApi.getPaymentMethodById(newPaymentMethodId, false, false, ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(paymentMethod.getPluginName(), MockPaymentProviderPlugin.PLUGIN_NAME);

        final Payment payment = paymentApi.createAuthorizationWithPaymentControl(account, newPaymentMethodId, null, BigDecimal.TEN, Currency.USD, null,UUID.randomUUID().toString(),
                                                                                 UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), PAYMENT_OPTIONS, callContext);
        Assert.assertEquals(payment.getPaymentMethodId(), newPaymentMethodId);

        verifyOnSuccess(payment.getId(),
                        payment.getExternalKey(),
                        payment.getTransactions().get(0).getId(),
                        payment.getTransactions().get(0).getExternalKey(),
                        BigDecimal.TEN,
                        Currency.USD);
    }

    private void verifyPriorAndOnSuccess(final UUID paymentId,
                                         final String paymentExternalKey,
                                         final UUID paymentTransactionId,
                                         final String paymentTransactionExternalKey,
                                         final BigDecimal processAmount,
                                         final Currency processedCurrency) {
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallPaymentId(), paymentId);
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallPaymentExternalKey(), paymentExternalKey);
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallTransactionId(), paymentTransactionId);
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallTransactionExternalKey(), paymentTransactionExternalKey);
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallProcessedAmount().compareTo(processAmount), 0);
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallProcessedCurrency(), processedCurrency);

        Assert.assertEquals(testPaymentControlPluginApi.getActualOnSuccessCallPaymentId(), paymentId);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnSuccessCallPaymentExternalKey(), paymentExternalKey);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnSuccessCallTransactionId(), paymentTransactionId);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnSuccessCallTransactionExternalKey(), paymentTransactionExternalKey);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnSuccessCallProcessedAmount().compareTo(processAmount), 0);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnSuccessCallProcessedCurrency(), processedCurrency);

        Assert.assertNull(testPaymentControlPluginApi.getActualOnFailureCallPaymentId());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnFailureCallPaymentExternalKey());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnFailureCallTransactionId());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnFailureCallTransactionExternalKey());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnFailureCallProcessedAmount());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnFailureCallProcessedCurrency());

        testPaymentControlPluginApi.resetActualValues();
    }

    private void verifyOnSuccess(final UUID paymentId,
                                 final String paymentExternalKey,
                                 final UUID paymentTransactionId,
                                 final String paymentTransactionExternalKey,
                                 final BigDecimal processAmount,
                                 final Currency processedCurrency) {
        Assert.assertNull(testPaymentControlPluginApi.getActualPriorCallPaymentId());
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallPaymentExternalKey(), paymentExternalKey);
        Assert.assertNull(testPaymentControlPluginApi.getActualPriorCallTransactionId());
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallTransactionExternalKey(), paymentTransactionExternalKey);
        Assert.assertNull(testPaymentControlPluginApi.getActualPriorCallProcessedAmount());
        Assert.assertNull(testPaymentControlPluginApi.getActualPriorCallProcessedCurrency());

        Assert.assertEquals(testPaymentControlPluginApi.getActualOnSuccessCallPaymentId(), paymentId);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnSuccessCallPaymentExternalKey(), paymentExternalKey);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnSuccessCallTransactionId(), paymentTransactionId);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnSuccessCallTransactionExternalKey(), paymentTransactionExternalKey);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnSuccessCallProcessedAmount().compareTo(processAmount), 0);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnSuccessCallProcessedCurrency(), processedCurrency);

        Assert.assertNull(testPaymentControlPluginApi.getActualOnFailureCallPaymentId());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnFailureCallPaymentExternalKey());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnFailureCallTransactionId());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnFailureCallTransactionExternalKey());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnFailureCallProcessedAmount());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnFailureCallProcessedCurrency());

        testPaymentControlPluginApi.resetActualValues();
    }

    private void verifyOnSuccessForFollowOnTransaction(final UUID paymentId,
                                                       final String paymentExternalKey,
                                                       final UUID paymentTransactionId,
                                                       final String paymentTransactionExternalKey,
                                                       final BigDecimal processAmount,
                                                       final Currency processedCurrency) {
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallPaymentId(), paymentId);
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallPaymentExternalKey(), paymentExternalKey);
        Assert.assertNull(testPaymentControlPluginApi.getActualPriorCallTransactionId());
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallTransactionExternalKey(), paymentTransactionExternalKey);
        Assert.assertNull(testPaymentControlPluginApi.getActualPriorCallProcessedAmount());
        Assert.assertNull(testPaymentControlPluginApi.getActualPriorCallProcessedCurrency());

        Assert.assertEquals(testPaymentControlPluginApi.getActualOnSuccessCallPaymentId(), paymentId);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnSuccessCallPaymentExternalKey(), paymentExternalKey);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnSuccessCallTransactionId(), paymentTransactionId);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnSuccessCallTransactionExternalKey(), paymentTransactionExternalKey);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnSuccessCallProcessedAmount().compareTo(processAmount), 0);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnSuccessCallProcessedCurrency(), processedCurrency);

        Assert.assertNull(testPaymentControlPluginApi.getActualOnFailureCallPaymentId());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnFailureCallPaymentExternalKey());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnFailureCallTransactionId());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnFailureCallTransactionExternalKey());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnFailureCallProcessedAmount());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnFailureCallProcessedCurrency());

        testPaymentControlPluginApi.resetActualValues();
    }

    private void verifyPriorAndOnFailure(final UUID paymentId,
                                         final String paymentExternalKey,
                                         final UUID paymentTransactionId,
                                         final String paymentTransactionExternalKey,
                                         final BigDecimal processAmount,
                                         final Currency processedCurrency) {
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallPaymentId(), paymentId);
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallPaymentExternalKey(), paymentExternalKey);
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallTransactionId(), paymentTransactionId);
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallTransactionExternalKey(), paymentTransactionExternalKey);
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallProcessedAmount().compareTo(processAmount), 0);
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallProcessedCurrency(), processedCurrency);

        Assert.assertNull(testPaymentControlPluginApi.getActualOnSuccessCallPaymentId());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnSuccessCallPaymentExternalKey());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnSuccessCallTransactionId());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnSuccessCallTransactionExternalKey());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnSuccessCallProcessedAmount());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnSuccessCallProcessedCurrency());

        Assert.assertEquals(testPaymentControlPluginApi.getActualOnFailureCallPaymentId(), paymentId);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnFailureCallPaymentExternalKey(), paymentExternalKey);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnFailureCallTransactionId(), paymentTransactionId);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnFailureCallTransactionExternalKey(), paymentTransactionExternalKey);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnFailureCallProcessedAmount().compareTo(processAmount), 0);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnFailureCallProcessedCurrency(), processedCurrency);

        testPaymentControlPluginApi.resetActualValues();
    }

    private void verifyOnFailure(final UUID paymentId,
                                 final String paymentExternalKey,
                                 final UUID paymentTransactionId,
                                 final String paymentTransactionExternalKey,
                                 final BigDecimal processAmount,
                                 final Currency processedCurrency) {
        Assert.assertNull(testPaymentControlPluginApi.getActualPriorCallPaymentId());
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallPaymentExternalKey(), paymentExternalKey);
        Assert.assertNull(testPaymentControlPluginApi.getActualPriorCallTransactionId());
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallTransactionExternalKey(), paymentTransactionExternalKey);
        Assert.assertNull(testPaymentControlPluginApi.getActualPriorCallProcessedAmount());
        Assert.assertNull(testPaymentControlPluginApi.getActualPriorCallProcessedCurrency());

        Assert.assertNull(testPaymentControlPluginApi.getActualOnSuccessCallPaymentId());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnSuccessCallPaymentExternalKey());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnSuccessCallTransactionId());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnSuccessCallTransactionExternalKey());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnSuccessCallProcessedAmount());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnSuccessCallProcessedCurrency());

        Assert.assertEquals(testPaymentControlPluginApi.getActualOnFailureCallPaymentId(), paymentId);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnFailureCallPaymentExternalKey(), paymentExternalKey);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnFailureCallTransactionId(), paymentTransactionId);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnFailureCallTransactionExternalKey(), paymentTransactionExternalKey);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnFailureCallProcessedAmount().compareTo(processAmount), 0);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnFailureCallProcessedCurrency(), processedCurrency);

        testPaymentControlPluginApi.resetActualValues();
    }

    private void verifyOnFailureForFollowOnTransaction(final UUID paymentId,
                                                       final String paymentExternalKey,
                                                       final UUID paymentTransactionId,
                                                       final String paymentTransactionExternalKey,
                                                       final BigDecimal processAmount,
                                                       final Currency processedCurrency) {
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallPaymentId(), paymentId);
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallPaymentExternalKey(), paymentExternalKey);
        Assert.assertNull(testPaymentControlPluginApi.getActualPriorCallTransactionId());
        Assert.assertEquals(testPaymentControlPluginApi.getActualPriorCallTransactionExternalKey(), paymentTransactionExternalKey);
        Assert.assertNull(testPaymentControlPluginApi.getActualPriorCallProcessedAmount());
        Assert.assertNull(testPaymentControlPluginApi.getActualPriorCallProcessedCurrency());

        Assert.assertNull(testPaymentControlPluginApi.getActualOnSuccessCallPaymentId());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnSuccessCallPaymentExternalKey());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnSuccessCallTransactionId());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnSuccessCallTransactionExternalKey());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnSuccessCallProcessedAmount());
        Assert.assertNull(testPaymentControlPluginApi.getActualOnSuccessCallProcessedCurrency());

        Assert.assertEquals(testPaymentControlPluginApi.getActualOnFailureCallPaymentId(), paymentId);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnFailureCallPaymentExternalKey(), paymentExternalKey);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnFailureCallTransactionId(), paymentTransactionId);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnFailureCallTransactionExternalKey(), paymentTransactionExternalKey);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnFailureCallProcessedAmount().compareTo(processAmount), 0);
        Assert.assertEquals(testPaymentControlPluginApi.getActualOnFailureCallProcessedCurrency(), processedCurrency);

        testPaymentControlPluginApi.resetActualValues();
    }

    public static class TestPaymentControlPluginApi implements PaymentControlPluginApi {

        public static final String PLUGIN_NAME = "TEST_CONTROL_API_PLUGIN_NAME";

        private UUID newPaymentMethodId;
        private String newPaymentMethodName;

        private UUID actualPriorCallPaymentId;
        private String actualPriorCallPaymentExternalKey;
        private UUID actualPriorCallTransactionId;
        private String actualPriorCallTransactionExternalKey;
        private BigDecimal actualPriorCallProcessedAmount;
        private Currency actualPriorCallProcessedCurrency;

        private UUID actualOnSuccessCallPaymentId;
        private String actualOnSuccessCallPaymentExternalKey;
        private UUID actualOnSuccessCallTransactionId;
        private String actualOnSuccessCallTransactionExternalKey;
        private BigDecimal actualOnSuccessCallProcessedAmount;
        private Currency actualOnSuccessCallProcessedCurrency;

        private UUID actualOnFailureCallPaymentId;
        private String actualOnFailureCallPaymentExternalKey;
        private UUID actualOnFailureCallTransactionId;
        private String actualOnFailureCallTransactionExternalKey;
        private BigDecimal actualOnFailureCallProcessedAmount;
        private Currency actualOnFailureCallProcessedCurrency;

        public void setNewPaymentMethodId(final UUID newPaymentMethodId) {
            this.newPaymentMethodId = newPaymentMethodId;
        }

        public void setNewPaymentMethodName(final String newPaymentMethodName) {
            this.newPaymentMethodName = newPaymentMethodName;
        }

        public UUID getActualPriorCallPaymentId() {
            return actualPriorCallPaymentId;
        }

        public String getActualPriorCallPaymentExternalKey() {
            return actualPriorCallPaymentExternalKey;
        }

        public UUID getActualPriorCallTransactionId() {
            return actualPriorCallTransactionId;
        }

        public String getActualPriorCallTransactionExternalKey() {
            return actualPriorCallTransactionExternalKey;
        }

        public BigDecimal getActualPriorCallProcessedAmount() {
            return actualPriorCallProcessedAmount;
        }

        public Currency getActualPriorCallProcessedCurrency() {
            return actualPriorCallProcessedCurrency;
        }

        public UUID getActualOnSuccessCallPaymentId() {
            return actualOnSuccessCallPaymentId;
        }

        public String getActualOnSuccessCallPaymentExternalKey() {
            return actualOnSuccessCallPaymentExternalKey;
        }

        public UUID getActualOnSuccessCallTransactionId() {
            return actualOnSuccessCallTransactionId;
        }

        public String getActualOnSuccessCallTransactionExternalKey() {
            return actualOnSuccessCallTransactionExternalKey;
        }

        public BigDecimal getActualOnSuccessCallProcessedAmount() {
            return actualOnSuccessCallProcessedAmount;
        }

        public Currency getActualOnSuccessCallProcessedCurrency() {
            return actualOnSuccessCallProcessedCurrency;
        }

        public UUID getActualOnFailureCallPaymentId() {
            return actualOnFailureCallPaymentId;
        }

        public String getActualOnFailureCallPaymentExternalKey() {
            return actualOnFailureCallPaymentExternalKey;
        }

        public UUID getActualOnFailureCallTransactionId() {
            return actualOnFailureCallTransactionId;
        }

        public String getActualOnFailureCallTransactionExternalKey() {
            return actualOnFailureCallTransactionExternalKey;
        }

        public BigDecimal getActualOnFailureCallProcessedAmount() {
            return actualOnFailureCallProcessedAmount;
        }

        public Currency getActualOnFailureCallProcessedCurrency() {
            return actualOnFailureCallProcessedCurrency;
        }

        @Override
        public PriorPaymentControlResult priorCall(final PaymentControlContext context, final Iterable<PluginProperty> properties) throws PaymentControlApiException {
            actualPriorCallPaymentId = context.getPaymentId();
            actualPriorCallPaymentExternalKey = context.getPaymentExternalKey();
            actualPriorCallTransactionId = context.getTransactionId();
            actualPriorCallTransactionExternalKey = context.getTransactionExternalKey();
            actualPriorCallProcessedAmount = context.getProcessedAmount();
            actualPriorCallProcessedCurrency = context.getProcessedCurrency();

            return new PriorPaymentControlResult() {
                @Override
                public boolean isAborted() {
                    return false;
                }

                @Override
                public BigDecimal getAdjustedAmount() {
                    return null;
                }

                @Override
                public Currency getAdjustedCurrency() {
                    return null;
                }

                @Override
                public UUID getAdjustedPaymentMethodId() {
                    return newPaymentMethodId;
                }

                @Override
                public String getAdjustedPluginName() {
                    return newPaymentMethodName;
                }

                @Override
                public Iterable<PluginProperty> getAdjustedPluginProperties() {
                    return null;
                }
            };
        }

        @Override
        public OnSuccessPaymentControlResult onSuccessCall(final PaymentControlContext context, final Iterable<PluginProperty> properties) throws PaymentControlApiException {
            actualOnSuccessCallPaymentId = context.getPaymentId();
            actualOnSuccessCallPaymentExternalKey = context.getPaymentExternalKey();
            actualOnSuccessCallTransactionId = context.getTransactionId();
            actualOnSuccessCallTransactionExternalKey = context.getTransactionExternalKey();
            actualOnSuccessCallProcessedAmount = context.getProcessedAmount();
            actualOnSuccessCallProcessedCurrency = context.getProcessedCurrency();

            return new DefaultOnSuccessPaymentControlResult();
        }

        @Override
        public OnFailurePaymentControlResult onFailureCall(final PaymentControlContext context, final Iterable<PluginProperty> properties) throws PaymentControlApiException {
            actualOnFailureCallPaymentId = context.getPaymentId();
            actualOnFailureCallPaymentExternalKey = context.getPaymentExternalKey();
            actualOnFailureCallTransactionId = context.getTransactionId();
            actualOnFailureCallTransactionExternalKey = context.getTransactionExternalKey();
            actualOnFailureCallProcessedAmount = context.getProcessedAmount();
            actualOnFailureCallProcessedCurrency = context.getProcessedCurrency();

            return new DefaultFailureCallResult(null);
        }

        public void resetActualValues() {
            actualPriorCallPaymentId = null;
            actualPriorCallPaymentExternalKey = null;
            actualPriorCallTransactionId = null;
            actualPriorCallTransactionExternalKey = null;
            actualPriorCallProcessedAmount = null;
            actualPriorCallProcessedCurrency = null;

            actualOnSuccessCallPaymentId = null;
            actualOnSuccessCallPaymentExternalKey = null;
            actualOnSuccessCallTransactionId = null;
            actualOnSuccessCallTransactionExternalKey = null;
            actualOnSuccessCallProcessedAmount = null;
            actualOnSuccessCallProcessedCurrency = null;

            actualOnFailureCallPaymentId = null;
            actualOnFailureCallPaymentExternalKey = null;
            actualOnFailureCallTransactionId = null;
            actualOnFailureCallTransactionExternalKey = null;
            actualOnFailureCallProcessedAmount = null;
            actualOnFailureCallProcessedCurrency = null;
        }
    }
}
