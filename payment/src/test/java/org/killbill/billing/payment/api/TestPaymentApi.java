/*
 * Copyright 2010-2013 Ning, Inc.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.joda.time.LocalDate;
import org.joda.time.LocalDate.Property;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.control.plugin.api.PaymentControlApiException;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.payment.MockRecurringInvoiceItem;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentSqlDao;
import org.killbill.billing.payment.invoice.InvoicePaymentControlPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.provider.ExternalPaymentProviderPlugin;
import org.killbill.billing.payment.provider.MockPaymentControlProviderPlugin;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestPaymentApi extends PaymentTestSuiteWithEmbeddedDB {

    private MockPaymentProviderPlugin mockPaymentProviderPlugin;

    private MockPaymentControlProviderPlugin mockPaymentControlProviderPlugin;

    final PaymentOptions INVOICE_PAYMENT = new PaymentOptions() {
        @Override
        public boolean isExternalPayment() {
            return false;
        }

        @Override
        public List<String> getPaymentControlPluginNames() {
            return ImmutableList.<String>of(InvoicePaymentControlPluginApi.PLUGIN_NAME);
        }
    };

    final PaymentOptions CONTROL_PLUGIN_OPTIONS = new PaymentOptions() {
        @Override
        public boolean isExternalPayment() {
            return true;
        }

        @Override
        public List<String> getPaymentControlPluginNames() {
            return Arrays.asList(MockPaymentControlProviderPlugin.PLUGIN_NAME);
        }
    };

    private Account account;

    @BeforeClass(groups = "slow")
    protected void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        mockPaymentProviderPlugin = (MockPaymentProviderPlugin) registry.getServiceForName(MockPaymentProviderPlugin.PLUGIN_NAME);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        mockPaymentProviderPlugin.clear();
        account = testHelper.createTestAccount("bobo@gmail.com", true);

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
    public void testUniqueExternalPaymentMethod() throws PaymentApiException {
        paymentApi.addPaymentMethod(account, "thisonewillwork", ExternalPaymentProviderPlugin.PLUGIN_NAME, true, null, ImmutableList.<PluginProperty>of(), callContext);

        try {
            paymentApi.addPaymentMethod(account, "thisonewillnotwork", ExternalPaymentProviderPlugin.PLUGIN_NAME, true, null, ImmutableList.<PluginProperty>of(), callContext);

        } catch (PaymentApiException e) {
            assertEquals(e.getCode(), ErrorCode.PAYMENT_EXTERNAL_PAYMENT_METHOD_ALREADY_EXISTS.getCode());
        }
    }

    @Test(groups = "slow")
    public void testAddRemovePaymentMethod() throws Exception {
        final Long baseNbRecords = paymentApi.getPaymentMethods(0L, 1000L, false, ImmutableList.<PluginProperty>of(), callContext).getMaxNbRecords();
        Assert.assertEquals(baseNbRecords, (Long) 1L);

        final Account account = testHelper.createTestAccount(UUID.randomUUID().toString(), true);
        final UUID paymentMethodId = account.getPaymentMethodId();

        checkPaymentMethodPagination(paymentMethodId, baseNbRecords + 1, false);

        paymentApi.deletePaymentMethod(account, paymentMethodId, true, false, ImmutableList.<PluginProperty>of(), callContext);

        List<PaymentMethod> paymentMethods = paymentApi.getAccountPaymentMethods(account.getId(), false, false, ImmutableList.<PluginProperty>of(), callContext);
        assertEquals(paymentMethods.size(), 0);


        paymentMethods = paymentApi.getAccountPaymentMethods(account.getId(), true, false, ImmutableList.<PluginProperty>of(), callContext);
        assertEquals(paymentMethods.size(), 1);

        checkPaymentMethodPagination(paymentMethodId, baseNbRecords, true);
    }

    @Test(groups = "slow")
    public void testAddRemovePaymentMethodWithForcedDeletion() throws Exception {
        final Long baseNbRecords = paymentApi.getPaymentMethods(0L, 1000L, false, ImmutableList.<PluginProperty>of(), callContext).getMaxNbRecords();
        Assert.assertEquals(baseNbRecords, (Long) 1L);

        final Account account = testHelper.createTestAccount(UUID.randomUUID().toString(), true);
        final UUID paymentMethodId = account.getPaymentMethodId();

        checkPaymentMethodPagination(paymentMethodId, baseNbRecords + 1, false);

        paymentApi.deletePaymentMethod(account, paymentMethodId, false, true, ImmutableList.<PluginProperty>of(), callContext);

        checkPaymentMethodPagination(paymentMethodId, baseNbRecords, true);
    }

    @Test(groups = "slow")
    public void testAddRemovePaymentMethodWithoutForcedDeletion() throws Exception {
        final Long baseNbRecords = paymentApi.getPaymentMethods(0L, 1000L, false, ImmutableList.<PluginProperty>of(), callContext).getMaxNbRecords();
        Assert.assertEquals(baseNbRecords, (Long) 1L);

        final Account account = testHelper.createTestAccount(UUID.randomUUID().toString(), true);
        final UUID paymentMethodId = account.getPaymentMethodId();

        try {
            paymentApi.deletePaymentMethod(account, paymentMethodId, false, false, ImmutableList.<PluginProperty>of(), callContext);
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INTERNAL_ERROR.getCode());
        }
        checkPaymentMethodPagination(paymentMethodId, baseNbRecords + 1, false);
    }

    @Test(groups = "slow", description="Verify we can make a refund on  payment whose original payment method was deleted. See 694")
    public void testRefundAfterDeletedPaymentMethod() throws PaymentApiException {

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final Payment payment = paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount, Currency.EUR, null, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                                                          ImmutableList.<PluginProperty>of(), callContext);

        paymentApi.deletePaymentMethod(account, account.getPaymentMethodId(), false, true, ImmutableList.<PluginProperty>of(), callContext);

        final Payment newPayment = paymentApi.createRefund(account, payment.getId(),requestedAmount,  Currency.EUR, null, UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(newPayment.getTransactions().size(), 2);
    }

    @Test(groups = "slow")
    public void testCreateSuccessPurchase() throws PaymentApiException {

        final BigDecimal requestedAmount = BigDecimal.TEN;

        final String paymentExternalKey = "bwwrr";
        final String transactionExternalKey = "krapaut";

        final Payment payment = paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, null, paymentExternalKey, transactionExternalKey,
                                                          ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment.getExternalKey(), paymentExternalKey);
        assertEquals(payment.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment.getAccountId(), account.getId());
        assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getPurchasedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCurrency(), Currency.AED);

        assertEquals(payment.getTransactions().size(), 1);
        assertEquals(payment.getTransactions().get(0).getExternalKey(), transactionExternalKey);
        assertEquals(payment.getTransactions().get(0).getPaymentId(), payment.getId());
        assertEquals(payment.getTransactions().get(0).getAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getTransactions().get(0).getCurrency(), Currency.AED);
        assertEquals(payment.getTransactions().get(0).getProcessedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getTransactions().get(0).getProcessedCurrency(), Currency.AED);

        assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment.getTransactions().get(0).getTransactionType(), TransactionType.PURCHASE);
        assertNotNull(payment.getTransactions().get(0).getGatewayErrorMsg());
        assertNotNull(payment.getTransactions().get(0).getGatewayErrorCode());
    }

    @Test(groups = "slow")
    public void testCreateFailedPurchase() throws PaymentApiException {

        final BigDecimal requestedAmount = BigDecimal.TEN;

        final String paymentExternalKey = "ohhhh";
        final String transactionExternalKey = "naaahhh";

        mockPaymentProviderPlugin.makeNextPaymentFailWithError();

        final Payment payment = paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, null, paymentExternalKey, transactionExternalKey,
                                                          ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment.getExternalKey(), paymentExternalKey);
        assertEquals(payment.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment.getAccountId(), account.getId());
        assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCurrency(), Currency.AED);

        assertEquals(payment.getTransactions().size(), 1);
        assertEquals(payment.getTransactions().get(0).getExternalKey(), transactionExternalKey);
        assertEquals(payment.getTransactions().get(0).getPaymentId(), payment.getId());
        assertEquals(payment.getTransactions().get(0).getAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getTransactions().get(0).getCurrency(), Currency.AED);
        assertEquals(payment.getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getTransactions().get(0).getProcessedCurrency(), Currency.AED);

        assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        assertEquals(payment.getTransactions().get(0).getTransactionType(), TransactionType.PURCHASE);
        assertNotNull(payment.getTransactions().get(0).getGatewayErrorMsg());
        assertNotNull(payment.getTransactions().get(0).getGatewayErrorCode());

        final Payment payment2 = paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, null, paymentExternalKey, transactionExternalKey,
                                                           ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment2.getExternalKey(), paymentExternalKey);
        assertEquals(payment2.getTransactions().get(0).getExternalKey(), transactionExternalKey);
        assertEquals(payment2.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        assertEquals(payment2.getTransactions().get(0).getTransactionType(), TransactionType.PURCHASE);

        assertEquals(payment2.getTransactions().get(1).getExternalKey(), transactionExternalKey);
        assertEquals(payment2.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment2.getTransactions().get(1).getTransactionType(), TransactionType.PURCHASE);

    }


    @Test(groups = "slow")
    public void testCreateCancelledPurchase() throws PaymentApiException {

        final BigDecimal requestedAmount = BigDecimal.TEN;

        final String paymentExternalKey = "hgh3";
        final String transactionExternalKey = "hgh3sss";

        mockPaymentProviderPlugin.makeNextPaymentFailWithCancellation();

        final Payment payment = paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, null, paymentExternalKey, transactionExternalKey,
                                                          ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment.getExternalKey(), paymentExternalKey);
        assertEquals(payment.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment.getAccountId(), account.getId());
        assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCurrency(), Currency.AED);

        assertEquals(payment.getTransactions().size(), 1);
        assertEquals(payment.getTransactions().get(0).getExternalKey(), transactionExternalKey);
        assertEquals(payment.getTransactions().get(0).getPaymentId(), payment.getId());
        assertEquals(payment.getTransactions().get(0).getAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getTransactions().get(0).getCurrency(), Currency.AED);
        assertEquals(payment.getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getTransactions().get(0).getProcessedCurrency(), Currency.AED);

        assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PLUGIN_FAILURE);
        assertEquals(payment.getTransactions().get(0).getTransactionType(), TransactionType.PURCHASE);
        assertNotNull(payment.getTransactions().get(0).getGatewayErrorMsg());
        assertNotNull(payment.getTransactions().get(0).getGatewayErrorCode());

        final Payment payment2 = paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, null, paymentExternalKey, transactionExternalKey,
                                                           ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment2.getExternalKey(), paymentExternalKey);
        assertEquals(payment2.getTransactions().get(0).getExternalKey(), transactionExternalKey);
        assertEquals(payment2.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PLUGIN_FAILURE);
        assertEquals(payment2.getTransactions().get(0).getTransactionType(), TransactionType.PURCHASE);

        assertEquals(payment2.getTransactions().get(1).getExternalKey(), transactionExternalKey);
        assertEquals(payment2.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment2.getTransactions().get(1).getTransactionType(), TransactionType.PURCHASE);

    }

    @Test(groups = "slow")
    public void testCreatePurchasePaymentPluginException() {
        mockPaymentProviderPlugin.makeNextPaymentFailWithException();

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final String paymentExternalKey = "pay external key";
        final String transactionExternalKey = "txn external key";
        try {
            paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, null,
                                      paymentExternalKey, transactionExternalKey, ImmutableList.<PluginProperty>of(), callContext);
            fail();
        } catch (PaymentApiException e) {
            assertTrue(e.getCause() instanceof PaymentPluginApiException);
        }
    }

    @Test(groups = "slow")
    public void testCreatePurchaseWithControlPaymentPluginException() throws Exception {
        mockPaymentProviderPlugin.makeNextPaymentFailWithException();

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final String paymentExternalKey = "pay controle external key";;
        final String transactionExternalKey = "txn control external key";
        try {
            paymentApi.createPurchaseWithPaymentControl(
                    account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, null,
                    paymentExternalKey, transactionExternalKey, ImmutableList.<PluginProperty>of(), CONTROL_PLUGIN_OPTIONS, callContext);
            fail();
        } catch (PaymentApiException e) {
            assertTrue(e.getCause() instanceof PaymentPluginApiException);
        }
    }

    @Test(groups = "slow")
    public void testCreatePurchaseWithControlPluginException() throws Exception {
        mockPaymentControlProviderPlugin.throwsException(new PaymentControlApiException());

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final String paymentExternalKey = "pay controle external key";;
        final String transactionExternalKey = "txn control external key";
        try {
            paymentApi.createPurchaseWithPaymentControl(
                    account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, null,
                    paymentExternalKey, transactionExternalKey, ImmutableList.<PluginProperty>of(), CONTROL_PLUGIN_OPTIONS, callContext);
            fail();
        } catch (PaymentApiException e) {
            assertTrue(e.getCause() instanceof PaymentControlApiException);
        }
    }

    @Test(groups = "slow")
    public void testCreatePurchaseWithControlPluginRuntimeException() throws Exception {
        mockPaymentControlProviderPlugin.throwsException(new IllegalStateException());

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final String paymentExternalKey = "pay controle external key";;
        final String transactionExternalKey = "txn control external key";
        try {
            paymentApi.createPurchaseWithPaymentControl(
                    account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, null,
                    paymentExternalKey, transactionExternalKey, ImmutableList.<PluginProperty>of(), CONTROL_PLUGIN_OPTIONS, callContext);
            fail();
        } catch (PaymentApiException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
    }

    @Test(groups = "slow")
    public void testCreateSuccessAuthVoid() throws PaymentApiException {
        final BigDecimal authAmount = BigDecimal.TEN;

        final String paymentExternalKey = UUID.randomUUID().toString();
        final String transactionExternalKey = UUID.randomUUID().toString();
        final String transactionExternalKey2 = UUID.randomUUID().toString();

        final Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, authAmount, Currency.AED, null,
                                                               paymentExternalKey, transactionExternalKey,
                                                               ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment.getExternalKey(), paymentExternalKey);
        assertEquals(payment.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment.getAccountId(), account.getId());
        assertEquals(payment.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCurrency(), Currency.AED);
        assertFalse(payment.isAuthVoided());

        assertEquals(payment.getTransactions().size(), 1);
        assertEquals(payment.getTransactions().get(0).getExternalKey(), transactionExternalKey);
        assertEquals(payment.getTransactions().get(0).getPaymentId(), payment.getId());
        assertEquals(payment.getTransactions().get(0).getAmount().compareTo(authAmount), 0);
        assertEquals(payment.getTransactions().get(0).getCurrency(), Currency.AED);
        assertEquals(payment.getTransactions().get(0).getProcessedAmount().compareTo(authAmount), 0);
        assertEquals(payment.getTransactions().get(0).getProcessedCurrency(), Currency.AED);

        assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment.getTransactions().get(0).getTransactionType(), TransactionType.AUTHORIZE);
        assertNotNull(payment.getTransactions().get(0).getGatewayErrorMsg());
        assertNotNull(payment.getTransactions().get(0).getGatewayErrorCode());

        // Void the authorization
        final Payment payment2 = paymentApi.createVoid(account, payment.getId(), null, transactionExternalKey2, ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment2.getExternalKey(), paymentExternalKey);
        assertEquals(payment2.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment2.getAccountId(), account.getId());
        assertEquals(payment2.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getCurrency(), Currency.AED);
        assertTrue(payment2.isAuthVoided());

        assertEquals(payment2.getTransactions().size(), 2);
        assertEquals(payment2.getTransactions().get(1).getExternalKey(), transactionExternalKey2);
        assertEquals(payment2.getTransactions().get(1).getPaymentId(), payment.getId());
        assertNull(payment2.getTransactions().get(1).getAmount());
        assertNull(payment2.getTransactions().get(1).getCurrency());
        assertNull(payment2.getTransactions().get(1).getProcessedAmount());
        assertNull(payment2.getTransactions().get(1).getProcessedCurrency());

        assertEquals(payment2.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment2.getTransactions().get(1).getTransactionType(), TransactionType.VOID);
        assertNotNull(payment2.getTransactions().get(1).getGatewayErrorMsg());
        assertNotNull(payment2.getTransactions().get(1).getGatewayErrorCode());

        try {
            // Verify further VOIDs are prohibited (see https://github.com/killbill/killbill/issues/514)
            paymentApi.createVoid(account, payment.getId(), null, UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
        }
    }

    @Test(groups = "slow")
    public void testCreateSuccessAuthCaptureVoidCapture() throws PaymentApiException {
        // Overwrite the default state machine to allow void on captures
        stateMachineConfigCache.loadDefaultPaymentStateMachineConfig("org/killbill/billing/payment/PermissivePaymentStates.xml");

        final BigDecimal authAmount = BigDecimal.TEN;
        final BigDecimal captureAmount = BigDecimal.ONE;

        final String paymentExternalKey = UUID.randomUUID().toString();
        final String transactionExternalKey = UUID.randomUUID().toString();
        final String transactionExternalKey2 = UUID.randomUUID().toString();
        final String transactionExternalKey3 = UUID.randomUUID().toString();
        final String transactionExternalKey4 = UUID.randomUUID().toString();

        final Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, authAmount, Currency.AED, null,
                                                               paymentExternalKey, transactionExternalKey,
                                                               ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment.getExternalKey(), paymentExternalKey);
        assertEquals(payment.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment.getAccountId(), account.getId());
        assertEquals(payment.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCurrency(), Currency.AED);
        assertFalse(payment.isAuthVoided());

        assertEquals(payment.getTransactions().size(), 1);
        assertEquals(payment.getTransactions().get(0).getExternalKey(), transactionExternalKey);
        assertEquals(payment.getTransactions().get(0).getPaymentId(), payment.getId());
        assertEquals(payment.getTransactions().get(0).getAmount().compareTo(authAmount), 0);
        assertEquals(payment.getTransactions().get(0).getCurrency(), Currency.AED);
        assertEquals(payment.getTransactions().get(0).getProcessedAmount().compareTo(authAmount), 0);
        assertEquals(payment.getTransactions().get(0).getProcessedCurrency(), Currency.AED);

        assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment.getTransactions().get(0).getTransactionType(), TransactionType.AUTHORIZE);
        assertNotNull(payment.getTransactions().get(0).getGatewayErrorMsg());
        assertNotNull(payment.getTransactions().get(0).getGatewayErrorCode());

        final Payment payment2 = paymentApi.createCapture(account, payment.getId(), captureAmount, Currency.AED, null, transactionExternalKey2,
                                                          ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment2.getExternalKey(), paymentExternalKey);
        assertEquals(payment2.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment2.getAccountId(), account.getId());
        assertEquals(payment2.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment2.getCapturedAmount().compareTo(captureAmount), 0);
        assertEquals(payment2.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getCurrency(), Currency.AED);
        assertFalse(payment2.isAuthVoided());

        assertEquals(payment2.getTransactions().size(), 2);
        assertEquals(payment2.getTransactions().get(1).getExternalKey(), transactionExternalKey2);
        assertEquals(payment2.getTransactions().get(1).getPaymentId(), payment.getId());
        assertEquals(payment2.getTransactions().get(1).getAmount().compareTo(captureAmount), 0);
        assertEquals(payment2.getTransactions().get(1).getCurrency(), Currency.AED);
        assertEquals(payment2.getTransactions().get(1).getProcessedAmount().compareTo(captureAmount), 0);
        assertEquals(payment2.getTransactions().get(1).getProcessedCurrency(), Currency.AED);

        assertEquals(payment2.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment2.getTransactions().get(1).getTransactionType(), TransactionType.CAPTURE);
        assertNotNull(payment2.getTransactions().get(1).getGatewayErrorMsg());
        assertNotNull(payment2.getTransactions().get(1).getGatewayErrorCode());

        // Void the capture
        final Payment payment3 = paymentApi.createVoid(account, payment.getId(), null, transactionExternalKey3, ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment3.getExternalKey(), paymentExternalKey);
        assertEquals(payment3.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment3.getAccountId(), account.getId());
        assertEquals(payment3.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment3.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment3.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment3.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment3.getCurrency(), Currency.AED);
        assertFalse(payment3.isAuthVoided());

        assertEquals(payment3.getTransactions().size(), 3);
        assertEquals(payment3.getTransactions().get(2).getExternalKey(), transactionExternalKey3);
        assertEquals(payment3.getTransactions().get(2).getPaymentId(), payment.getId());
        assertNull(payment3.getTransactions().get(2).getAmount());
        assertNull(payment3.getTransactions().get(2).getCurrency());
        assertNull(payment3.getTransactions().get(2).getProcessedAmount());
        assertNull(payment3.getTransactions().get(2).getProcessedCurrency());

        assertEquals(payment3.getTransactions().get(2).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment3.getTransactions().get(2).getTransactionType(), TransactionType.VOID);
        assertNotNull(payment3.getTransactions().get(2).getGatewayErrorMsg());
        assertNotNull(payment3.getTransactions().get(2).getGatewayErrorCode());

        // Capture again
        final Payment payment4 = paymentApi.createCapture(account, payment.getId(), captureAmount, Currency.AED, null, transactionExternalKey4,
                                                          ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment4.getExternalKey(), paymentExternalKey);
        assertEquals(payment4.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment4.getAccountId(), account.getId());
        assertEquals(payment4.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment4.getCapturedAmount().compareTo(captureAmount), 0);
        assertEquals(payment4.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment4.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment4.getCurrency(), Currency.AED);
        assertFalse(payment4.isAuthVoided());

        assertEquals(payment4.getTransactions().size(), 4);
        assertEquals(payment4.getTransactions().get(3).getExternalKey(), transactionExternalKey4);
        assertEquals(payment4.getTransactions().get(3).getPaymentId(), payment.getId());
        assertEquals(payment4.getTransactions().get(3).getAmount().compareTo(captureAmount), 0);
        assertEquals(payment4.getTransactions().get(3).getCurrency(), Currency.AED);
        assertEquals(payment4.getTransactions().get(3).getProcessedAmount().compareTo(captureAmount), 0);
        assertEquals(payment4.getTransactions().get(3).getProcessedCurrency(), Currency.AED);

        assertEquals(payment4.getTransactions().get(3).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment4.getTransactions().get(3).getTransactionType(), TransactionType.CAPTURE);
        assertNotNull(payment4.getTransactions().get(3).getGatewayErrorMsg());
        assertNotNull(payment4.getTransactions().get(3).getGatewayErrorCode());
    }

    @Test(groups = "slow")
    public void testCreateSuccessAuthCaptureVoidFailed() throws PaymentApiException {
        final BigDecimal authAmount = BigDecimal.TEN;
        final BigDecimal captureAmount = BigDecimal.ONE;

        final String paymentExternalKey = UUID.randomUUID().toString();
        final String transactionExternalKey = UUID.randomUUID().toString();
        final String transactionExternalKey2 = UUID.randomUUID().toString();
        final String transactionExternalKey3 = UUID.randomUUID().toString();

        final Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, authAmount, Currency.AED, null,
                                                               paymentExternalKey, transactionExternalKey,
                                                               ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment.getExternalKey(), paymentExternalKey);
        assertEquals(payment.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment.getAccountId(), account.getId());
        assertEquals(payment.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCurrency(), Currency.AED);
        assertFalse(payment.isAuthVoided());

        assertEquals(payment.getTransactions().size(), 1);
        assertEquals(payment.getTransactions().get(0).getExternalKey(), transactionExternalKey);
        assertEquals(payment.getTransactions().get(0).getPaymentId(), payment.getId());
        assertEquals(payment.getTransactions().get(0).getAmount().compareTo(authAmount), 0);
        assertEquals(payment.getTransactions().get(0).getCurrency(), Currency.AED);
        assertEquals(payment.getTransactions().get(0).getProcessedAmount().compareTo(authAmount), 0);
        assertEquals(payment.getTransactions().get(0).getProcessedCurrency(), Currency.AED);

        assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment.getTransactions().get(0).getTransactionType(), TransactionType.AUTHORIZE);
        assertNotNull(payment.getTransactions().get(0).getGatewayErrorMsg());
        assertNotNull(payment.getTransactions().get(0).getGatewayErrorCode());

        final Payment payment2 = paymentApi.createCapture(account, payment.getId(), captureAmount, Currency.AED, null, transactionExternalKey2,
                                                          ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment2.getExternalKey(), paymentExternalKey);
        assertEquals(payment2.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment2.getAccountId(), account.getId());
        assertEquals(payment2.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment2.getCapturedAmount().compareTo(captureAmount), 0);
        assertEquals(payment2.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getCurrency(), Currency.AED);
        assertFalse(payment2.isAuthVoided());

        assertEquals(payment2.getTransactions().size(), 2);
        assertEquals(payment2.getTransactions().get(1).getExternalKey(), transactionExternalKey2);
        assertEquals(payment2.getTransactions().get(1).getPaymentId(), payment.getId());
        assertEquals(payment2.getTransactions().get(1).getAmount().compareTo(captureAmount), 0);
        assertEquals(payment2.getTransactions().get(1).getCurrency(), Currency.AED);
        assertEquals(payment2.getTransactions().get(1).getProcessedAmount().compareTo(captureAmount), 0);
        assertEquals(payment2.getTransactions().get(1).getProcessedCurrency(), Currency.AED);

        assertEquals(payment2.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment2.getTransactions().get(1).getTransactionType(), TransactionType.CAPTURE);
        assertNotNull(payment2.getTransactions().get(1).getGatewayErrorMsg());
        assertNotNull(payment2.getTransactions().get(1).getGatewayErrorCode());

        try {
            // Voiding a capture is prohibited by default
            paymentApi.createVoid(account, payment.getId(), null, transactionExternalKey3, ImmutableList.<PluginProperty>of(), callContext);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
        }
    }

    @Test(groups = "slow")
    public void testCreateSuccessAuthCaptureVoidVoid() throws PaymentApiException {
        // Overwrite the default state machine to allow void on captures
        stateMachineConfigCache.loadDefaultPaymentStateMachineConfig("org/killbill/billing/payment/PermissivePaymentStates.xml");

        final BigDecimal authAmount = BigDecimal.TEN;
        final BigDecimal captureAmount = BigDecimal.ONE;

        final String paymentExternalKey = UUID.randomUUID().toString();
        final String transactionExternalKey = UUID.randomUUID().toString();
        final String transactionExternalKey2 = UUID.randomUUID().toString();
        final String transactionExternalKey3 = UUID.randomUUID().toString();
        final String transactionExternalKey4 = UUID.randomUUID().toString();

        final Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, authAmount, Currency.AED, null,
                                                               paymentExternalKey, transactionExternalKey,
                                                               ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment.getExternalKey(), paymentExternalKey);
        assertEquals(payment.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment.getAccountId(), account.getId());
        assertEquals(payment.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCurrency(), Currency.AED);
        assertFalse(payment.isAuthVoided());

        assertEquals(payment.getTransactions().size(), 1);
        assertEquals(payment.getTransactions().get(0).getExternalKey(), transactionExternalKey);
        assertEquals(payment.getTransactions().get(0).getPaymentId(), payment.getId());
        assertEquals(payment.getTransactions().get(0).getAmount().compareTo(authAmount), 0);
        assertEquals(payment.getTransactions().get(0).getCurrency(), Currency.AED);
        assertEquals(payment.getTransactions().get(0).getProcessedAmount().compareTo(authAmount), 0);
        assertEquals(payment.getTransactions().get(0).getProcessedCurrency(), Currency.AED);

        assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment.getTransactions().get(0).getTransactionType(), TransactionType.AUTHORIZE);
        assertNotNull(payment.getTransactions().get(0).getGatewayErrorMsg());
        assertNotNull(payment.getTransactions().get(0).getGatewayErrorCode());

        final Payment payment2 = paymentApi.createCapture(account, payment.getId(), captureAmount, Currency.AED, null, transactionExternalKey2,
                                                          ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment2.getExternalKey(), paymentExternalKey);
        assertEquals(payment2.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment2.getAccountId(), account.getId());
        assertEquals(payment2.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment2.getCapturedAmount().compareTo(captureAmount), 0);
        assertEquals(payment2.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getCurrency(), Currency.AED);
        assertFalse(payment2.isAuthVoided());

        assertEquals(payment2.getTransactions().size(), 2);
        assertEquals(payment2.getTransactions().get(1).getExternalKey(), transactionExternalKey2);
        assertEquals(payment2.getTransactions().get(1).getPaymentId(), payment.getId());
        assertEquals(payment2.getTransactions().get(1).getAmount().compareTo(captureAmount), 0);
        assertEquals(payment2.getTransactions().get(1).getCurrency(), Currency.AED);
        assertEquals(payment2.getTransactions().get(1).getProcessedAmount().compareTo(captureAmount), 0);
        assertEquals(payment2.getTransactions().get(1).getProcessedCurrency(), Currency.AED);

        assertEquals(payment2.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment2.getTransactions().get(1).getTransactionType(), TransactionType.CAPTURE);
        assertNotNull(payment2.getTransactions().get(1).getGatewayErrorMsg());
        assertNotNull(payment2.getTransactions().get(1).getGatewayErrorCode());

        // Void the capture
        final Payment payment3 = paymentApi.createVoid(account, payment.getId(), null, transactionExternalKey3, ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment3.getExternalKey(), paymentExternalKey);
        assertEquals(payment3.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment3.getAccountId(), account.getId());
        assertEquals(payment3.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment3.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment3.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment3.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment3.getCurrency(), Currency.AED);
        assertFalse(payment3.isAuthVoided());

        assertEquals(payment3.getTransactions().size(), 3);
        assertEquals(payment3.getTransactions().get(2).getExternalKey(), transactionExternalKey3);
        assertEquals(payment3.getTransactions().get(2).getPaymentId(), payment.getId());
        assertNull(payment3.getTransactions().get(2).getAmount());
        assertNull(payment3.getTransactions().get(2).getCurrency());
        assertNull(payment3.getTransactions().get(2).getProcessedAmount());
        assertNull(payment3.getTransactions().get(2).getProcessedCurrency());

        assertEquals(payment3.getTransactions().get(2).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment3.getTransactions().get(2).getTransactionType(), TransactionType.VOID);
        assertNotNull(payment3.getTransactions().get(2).getGatewayErrorMsg());
        assertNotNull(payment3.getTransactions().get(2).getGatewayErrorCode());

        // Void the authorization
        final Payment payment4 = paymentApi.createVoid(account, payment.getId(), null, transactionExternalKey4, ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment4.getExternalKey(), paymentExternalKey);
        assertEquals(payment4.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment4.getAccountId(), account.getId());
        assertEquals(payment4.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment4.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment4.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment4.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment4.getCurrency(), Currency.AED);
        assertTrue(payment4.isAuthVoided());

        assertEquals(payment4.getTransactions().size(), 4);
        assertEquals(payment4.getTransactions().get(3).getExternalKey(), transactionExternalKey4);
        assertEquals(payment4.getTransactions().get(3).getPaymentId(), payment.getId());
        assertNull(payment4.getTransactions().get(3).getAmount());
        assertNull(payment4.getTransactions().get(3).getCurrency());
        assertNull(payment4.getTransactions().get(3).getProcessedAmount());
        assertNull(payment4.getTransactions().get(3).getProcessedCurrency());

        assertEquals(payment4.getTransactions().get(3).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment4.getTransactions().get(3).getTransactionType(), TransactionType.VOID);
        assertNotNull(payment4.getTransactions().get(3).getGatewayErrorMsg());
        assertNotNull(payment4.getTransactions().get(3).getGatewayErrorCode());
    }

    @Test(groups = "slow")
    public void testCreateSuccessAuthMultipleCaptureAndRefund() throws PaymentApiException {

        final BigDecimal authAmount = BigDecimal.TEN;
        final BigDecimal captureAmount = BigDecimal.ONE;

        final String paymentExternalKey = "courou";
        final String transactionExternalKey = "sioux";
        final String transactionExternalKey2 = "sioux2";
        final String transactionExternalKey3 = "sioux3";
        final String transactionExternalKey4 = "sioux4";

        final Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, authAmount, Currency.USD, null, paymentExternalKey, transactionExternalKey,
                                                               ImmutableList.<PluginProperty>of(), callContext);

        paymentApi.createCapture(account, payment.getId(), captureAmount, Currency.USD, null, transactionExternalKey2,
                                 ImmutableList.<PluginProperty>of(), callContext);

        final Payment payment3 = paymentApi.createCapture(account, payment.getId(), captureAmount, Currency.USD, null, transactionExternalKey3,
                                                          ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment3.getExternalKey(), paymentExternalKey);
        assertEquals(payment3.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment3.getAccountId(), account.getId());
        assertEquals(payment3.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment3.getCapturedAmount().compareTo(captureAmount.add(captureAmount)), 0);
        assertEquals(payment3.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment3.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment3.getCurrency(), Currency.USD);
        assertEquals(payment3.getTransactions().size(), 3);

        final Payment payment4 = paymentApi.createRefund(account, payment3.getId(), payment3.getCapturedAmount(), Currency.USD, null, transactionExternalKey4, ImmutableList.<PluginProperty>of(), callContext);
        assertEquals(payment4.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment4.getCapturedAmount().compareTo(captureAmount.add(captureAmount)), 0);
        assertEquals(payment4.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment4.getRefundedAmount().compareTo(payment3.getCapturedAmount()), 0);
        assertEquals(payment4.getTransactions().size(), 4);

        assertEquals(payment4.getTransactions().get(3).getExternalKey(), transactionExternalKey4);
        assertEquals(payment4.getTransactions().get(3).getPaymentId(), payment.getId());
        assertEquals(payment4.getTransactions().get(3).getAmount().compareTo(payment3.getCapturedAmount()), 0);
        assertEquals(payment4.getTransactions().get(3).getCurrency(), Currency.USD);
        assertEquals(payment4.getTransactions().get(3).getProcessedAmount().compareTo(payment3.getCapturedAmount()), 0);
        assertEquals(payment4.getTransactions().get(3).getProcessedCurrency(), Currency.USD);
        assertEquals(payment4.getTransactions().get(3).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment4.getTransactions().get(3).getTransactionType(), TransactionType.REFUND);
        assertNotNull(payment4.getTransactions().get(3).getGatewayErrorMsg());
        assertNotNull(payment4.getTransactions().get(3).getGatewayErrorCode());
    }

    @Test(groups = "slow")
    public void testCreateSuccessPurchaseWithPaymentControl() throws PaymentApiException, InvoiceApiException, EventBusException {

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate now = clock.getUTCToday();

        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD);

        final String paymentExternalKey = invoice.getId().toString();
        final String transactionExternalKey = "brrrrrr";

        invoice.addInvoiceItem(new MockRecurringInvoiceItem(invoice.getId(), account.getId(),
                                                            subscriptionId,
                                                            bundleId,
                                                            "test plan", "test phase", null,
                                                            now,
                                                            now.plusMonths(1),
                                                            requestedAmount,
                                                            new BigDecimal("1.0"),
                                                            Currency.USD));
        final Payment payment = paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null, paymentExternalKey, transactionExternalKey,
                                                                            createPropertiesForInvoice(invoice), INVOICE_PAYMENT, callContext);

        assertEquals(payment.getExternalKey(), paymentExternalKey);
        assertEquals(payment.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment.getAccountId(), account.getId());
        assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getPurchasedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCurrency(), Currency.USD);

        assertEquals(payment.getTransactions().size(), 1);
        assertEquals(payment.getTransactions().get(0).getExternalKey(), transactionExternalKey);
        assertEquals(payment.getTransactions().get(0).getPaymentId(), payment.getId());
        assertEquals(payment.getTransactions().get(0).getAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payment.getTransactions().get(0).getProcessedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getTransactions().get(0).getProcessedCurrency(), Currency.USD);

        assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment.getTransactions().get(0).getTransactionType(), TransactionType.PURCHASE);
        assertNotNull(payment.getTransactions().get(0).getGatewayErrorMsg());
        assertNotNull(payment.getTransactions().get(0).getGatewayErrorCode());

        // Not stricly an API test but interesting to verify that we indeed went through the attempt logic
        final List<PaymentAttemptModelDao> attempts = paymentDao.getPaymentAttempts(payment.getExternalKey(), internalCallContext);
        assertEquals(attempts.size(), 1);
    }

    @Test(groups = "slow")
    public void testCreatePurchaseWithExternalKeyOverLimit() throws PaymentApiException, InvoiceApiException, EventBusException {
        final BigDecimal requestedAmount = BigDecimal.TEN;
        final LocalDate now = clock.getUTCToday();

        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD);

        final String paymentExternalKey = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean massa. Cum sociis natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Donec quam felis, ultricies nec, pellentesque eu, pretium quis,.";
        final String transactionExternalKey = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean massa. Cum sociis natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Donec quam felis, ultricies nec, pellentesque eu, pretium quis,.";

        try {
            paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null, paymentExternalKey, transactionExternalKey,
                                                        createPropertiesForInvoice(invoice), INVOICE_PAYMENT, callContext);
            Assert.fail();
        } catch (final PaymentApiException e) {
            assertEquals(e.getCode(), ErrorCode.EXTERNAL_KEY_LIMIT_EXCEEDED.getCode());
        }

    }

    @Test(groups = "slow")
    public void testCreateFailedPurchaseWithPaymentControl() throws PaymentApiException, InvoiceApiException, EventBusException {

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate now = clock.getUTCToday();

        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD);

        final String paymentExternalKey = invoice.getId().toString();
        final String transactionExternalKey = "brrrrrr";

        mockPaymentProviderPlugin.makeNextPaymentFailWithError();

        invoice.addInvoiceItem(new MockRecurringInvoiceItem(invoice.getId(), account.getId(),
                                                            subscriptionId,
                                                            bundleId,
                                                            "test plan", "test phase", null,
                                                            now,
                                                            now.plusMonths(1),
                                                            requestedAmount,
                                                            new BigDecimal("1.0"),
                                                            Currency.USD));
        try {
            paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null, paymentExternalKey, transactionExternalKey,
                                                        createPropertiesForInvoice(invoice), INVOICE_PAYMENT, callContext);
        } catch (final PaymentApiException expected) {
            assertTrue(true);
        }


        final List<Payment> accountPayments = paymentApi.getAccountPayments(account.getId(), false, false, ImmutableList.<PluginProperty>of(), callContext);
        assertEquals(accountPayments.size(), 1);
        final Payment payment = accountPayments.get(0);
        assertEquals(payment.getExternalKey(), paymentExternalKey);
        assertEquals(payment.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment.getAccountId(), account.getId());
        assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCurrency(), Currency.USD);

        assertEquals(payment.getTransactions().size(), 1);
        assertEquals(payment.getTransactions().get(0).getExternalKey(), transactionExternalKey);
        assertEquals(payment.getTransactions().get(0).getPaymentId(), payment.getId());
        assertEquals(payment.getTransactions().get(0).getAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payment.getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getTransactions().get(0).getProcessedCurrency(), Currency.USD);

        assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        assertEquals(payment.getTransactions().get(0).getTransactionType(), TransactionType.PURCHASE);
    }


    @Test(groups = "slow")
    public void testCreateCancelledPurchaseWithPaymentControl() throws PaymentApiException, InvoiceApiException, EventBusException {

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate now = clock.getUTCToday();

        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD);

        final String paymentExternalKey = invoice.getId().toString();
        final String transactionExternalKey = "hgjhgjgjhg33";

        mockPaymentProviderPlugin.makeNextPaymentFailWithCancellation();

        invoice.addInvoiceItem(new MockRecurringInvoiceItem(invoice.getId(), account.getId(),
                                                            subscriptionId,
                                                            bundleId,
                                                            "test plan", "test phase", null,
                                                            now,
                                                            now.plusMonths(1),
                                                            requestedAmount,
                                                            new BigDecimal("1.0"),
                                                            Currency.USD));
        try {
            paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null, paymentExternalKey, transactionExternalKey,
                                                        createPropertiesForInvoice(invoice), INVOICE_PAYMENT, callContext);
        } catch (final PaymentApiException expected) {
            assertTrue(true);
        }


        final List<Payment> accountPayments = paymentApi.getAccountPayments(account.getId(), false, false, ImmutableList.<PluginProperty>of(), callContext);
        assertEquals(accountPayments.size(), 1);
        final Payment payment = accountPayments.get(0);
        assertEquals(payment.getExternalKey(), paymentExternalKey);
        assertEquals(payment.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment.getAccountId(), account.getId());
        assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCurrency(), Currency.USD);

        assertEquals(payment.getTransactions().size(), 1);
        assertEquals(payment.getTransactions().get(0).getExternalKey(), transactionExternalKey);
        assertEquals(payment.getTransactions().get(0).getPaymentId(), payment.getId());
        assertEquals(payment.getTransactions().get(0).getAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payment.getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getTransactions().get(0).getProcessedCurrency(), Currency.USD);

        assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PLUGIN_FAILURE);
        assertEquals(payment.getTransactions().get(0).getTransactionType(), TransactionType.PURCHASE);

        // Make sure we can retry and that works
        paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null, paymentExternalKey, transactionExternalKey,
                                                    createPropertiesForInvoice(invoice), INVOICE_PAYMENT, callContext);


        final List<Payment> accountPayments2 = paymentApi.getAccountPayments(account.getId(), false, false, ImmutableList.<PluginProperty>of(), callContext);
        assertEquals(accountPayments2.size(), 1);
        final Payment payment2 = accountPayments2.get(0);
        assertEquals(payment2.getTransactions().size(), 2);

        assertEquals(payment2.getTransactions().get(0).getExternalKey(), transactionExternalKey);
        assertEquals(payment2.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PLUGIN_FAILURE);
        assertEquals(payment2.getTransactions().get(0).getTransactionType(), TransactionType.PURCHASE);

        assertEquals(payment2.getTransactions().get(1).getExternalKey(), transactionExternalKey);
        assertEquals(payment2.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment2.getTransactions().get(1).getTransactionType(), TransactionType.PURCHASE);

    }


    @Test(groups = "slow")
    public void testCreateAbortedPurchaseWithPaymentControl() throws InvoiceApiException, EventBusException {

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate now = clock.getUTCToday();

        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD);

        final String paymentExternalKey = invoice.getId().toString();
        final String transactionExternalKey = "brrrrrr";

        invoice.addInvoiceItem(new MockRecurringInvoiceItem(invoice.getId(), account.getId(),
                                                            subscriptionId,
                                                            bundleId,
                                                            "test plan", "test phase", null,
                                                            now,
                                                            now.plusMonths(1),
                                                            BigDecimal.ONE,
                                                            new BigDecimal("1.0"),
                                                            Currency.USD));

        try {
            paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null, paymentExternalKey, transactionExternalKey,
                                                        createPropertiesForInvoice(invoice), INVOICE_PAYMENT, callContext);
            Assert.fail("Unexpected success");
        } catch (final PaymentApiException e) {
        }
    }

    @Test(groups = "slow")
    public void testCreateSuccessRefundWithPaymentControl() throws PaymentApiException, InvoiceApiException, EventBusException {

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate now = clock.getUTCToday();

        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD);

        final String paymentExternalKey = invoice.getId().toString();
        final String transactionExternalKey = "sacrebleu";
        final String transactionExternalKey2 = "maisenfin";

        final InvoiceItem invoiceItem = new MockRecurringInvoiceItem(invoice.getId(), account.getId(),
                                                                     subscriptionId,
                                                                     bundleId,
                                                                     "test plan", "test phase", null,
                                                                     now,
                                                                     now.plusMonths(1),
                                                                     requestedAmount,
                                                                     new BigDecimal("1.0"),
                                                                     Currency.USD);
        invoice.addInvoiceItem(invoiceItem);

        final Payment payment = paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null, paymentExternalKey, transactionExternalKey,
                                                                            createPropertiesForInvoice(invoice), INVOICE_PAYMENT, callContext);

        final List<PluginProperty> refundProperties = ImmutableList.<PluginProperty>of();
        final Payment payment2 = paymentApi.createRefundWithPaymentControl(account, payment.getId(), requestedAmount, Currency.USD, null, transactionExternalKey2,
                                                                           refundProperties, INVOICE_PAYMENT, callContext);

        assertEquals(payment2.getTransactions().size(), 2);
        assertEquals(payment2.getExternalKey(), paymentExternalKey);
        assertEquals(payment2.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment2.getAccountId(), account.getId());
        assertEquals(payment2.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getPurchasedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment2.getRefundedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment2.getCurrency(), Currency.USD);
    }

    @Test(groups = "slow")
    public void testCreateAbortedRefundWithPaymentControl() throws PaymentApiException, InvoiceApiException, EventBusException {

        final BigDecimal requestedAmount = BigDecimal.ONE;
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate now = clock.getUTCToday();

        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD);

        final String paymentExternalKey = invoice.getId().toString();
        final String transactionExternalKey = "payment";
        final String transactionExternalKey2 = "refund";

        final InvoiceItem invoiceItem = new MockRecurringInvoiceItem(invoice.getId(), account.getId(),
                                                                     subscriptionId,
                                                                     bundleId,
                                                                     "test plan", "test phase", null,
                                                                     now,
                                                                     now.plusMonths(1),
                                                                     requestedAmount,
                                                                     new BigDecimal("1.0"),
                                                                     Currency.USD);
        invoice.addInvoiceItem(invoiceItem);

        final Payment payment = paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null, paymentExternalKey, transactionExternalKey,
                                                                            createPropertiesForInvoice(invoice), INVOICE_PAYMENT, callContext);

        final List<PluginProperty> refundProperties = ImmutableList.<PluginProperty>of();

        try {
            paymentApi.createRefundWithPaymentControl(account, payment.getId(), BigDecimal.TEN, Currency.USD, null,transactionExternalKey2,
                                                      refundProperties, INVOICE_PAYMENT, callContext);
        } catch (final PaymentApiException e) {
            assertTrue(e.getCause() instanceof PaymentControlApiException);
        }
    }

    @Test(groups = "slow")
    public void testCreateSuccessRefundPaymentControlWithItemAdjustments() throws PaymentApiException, InvoiceApiException, EventBusException {

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate now = clock.getUTCToday();

        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD);

        final String paymentExternalKey = invoice.getId().toString();
        final String transactionExternalKey = "hopla";
        final String transactionExternalKey2 = "chouette";

        final InvoiceItem invoiceItem = new MockRecurringInvoiceItem(invoice.getId(), account.getId(),
                                                                     subscriptionId,
                                                                     bundleId,
                                                                     "test plan", "test phase", null,
                                                                     now,
                                                                     now.plusMonths(1),
                                                                     requestedAmount,
                                                                     new BigDecimal("1.0"),
                                                                     Currency.USD);
        invoice.addInvoiceItem(invoiceItem);

        final Payment payment = paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, null, paymentExternalKey, transactionExternalKey,
                                                                            createPropertiesForInvoice(invoice), INVOICE_PAYMENT, callContext);

        final List<PluginProperty> refundProperties = new ArrayList<PluginProperty>();
        final HashMap<UUID, BigDecimal> uuidBigDecimalHashMap = new HashMap<UUID, BigDecimal>();
        uuidBigDecimalHashMap.put(invoiceItem.getId(), null);
        final PluginProperty refundIdsProp = new PluginProperty(InvoicePaymentControlPluginApi.PROP_IPCD_REFUND_IDS_WITH_AMOUNT_KEY, uuidBigDecimalHashMap, false);
        refundProperties.add(refundIdsProp);

        final Payment payment2 = paymentApi.createRefundWithPaymentControl(account, payment.getId(), null, Currency.USD, null, transactionExternalKey2,
                                                                           refundProperties, INVOICE_PAYMENT, callContext);

        assertEquals(payment2.getTransactions().size(), 2);
        assertEquals(payment2.getExternalKey(), paymentExternalKey);
        assertEquals(payment2.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment2.getAccountId(), account.getId());
        assertEquals(payment2.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getPurchasedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment2.getRefundedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment2.getCurrency(), Currency.USD);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/477")
    public void testCreateChargeback() throws PaymentApiException {
        final BigDecimal requestedAmount = BigDecimal.TEN;
        final Currency currency = Currency.AED;
        final String paymentExternalKey = UUID.randomUUID().toString();
        final String purchaseTransactionExternalKey = UUID.randomUUID().toString();
        final String chargebackTransactionExternalKey = UUID.randomUUID().toString();
        final ImmutableList<PluginProperty> properties = ImmutableList.<PluginProperty>of();

        final Payment payment = paymentApi.createPurchase(account,
                                                          account.getPaymentMethodId(),
                                                          null,
                                                          requestedAmount,
                                                          currency,
                                                          null,
                                                          paymentExternalKey,
                                                          purchaseTransactionExternalKey,
                                                          properties,
                                                          callContext);

        assertEquals(payment.getExternalKey(), paymentExternalKey);
        assertEquals(payment.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment.getAccountId(), account.getId());
        assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getPurchasedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCurrency(), currency);

        assertEquals(payment.getTransactions().size(), 1);
        assertEquals(payment.getTransactions().get(0).getExternalKey(), purchaseTransactionExternalKey);
        assertEquals(payment.getTransactions().get(0).getPaymentId(), payment.getId());
        assertEquals(payment.getTransactions().get(0).getAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getTransactions().get(0).getCurrency(), currency);
        assertEquals(payment.getTransactions().get(0).getProcessedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getTransactions().get(0).getProcessedCurrency(), currency);
        assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment.getTransactions().get(0).getTransactionType(), TransactionType.PURCHASE);
        assertEquals(payment.getTransactions().get(0).getGatewayErrorMsg(), "");
        assertEquals(payment.getTransactions().get(0).getGatewayErrorCode(), "");

        assertEquals(paymentDao.getPayment(payment.getId(), internalCallContext).getStateName(), "PURCHASE_SUCCESS");
        assertEquals(paymentDao.getPayment(payment.getId(), internalCallContext).getLastSuccessStateName(), "PURCHASE_SUCCESS");

        // First chargeback
        final Payment payment2 = paymentApi.createChargeback(account,
                                                             payment.getId(),
                                                             requestedAmount,
                                                             currency,
                                                             null,
                                                             chargebackTransactionExternalKey,
                                                             callContext);

        assertEquals(payment2.getExternalKey(), paymentExternalKey);
        assertEquals(payment2.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment2.getAccountId(), account.getId());
        assertEquals(payment2.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        // Purchase amount zero-ed out
        assertEquals(payment2.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getCurrency(), currency);

        assertEquals(payment2.getTransactions().size(), 2);
        assertEquals(payment2.getTransactions().get(1).getExternalKey(), chargebackTransactionExternalKey);
        assertEquals(payment2.getTransactions().get(1).getPaymentId(), payment.getId());
        assertEquals(payment2.getTransactions().get(1).getAmount().compareTo(requestedAmount), 0);
        assertEquals(payment2.getTransactions().get(1).getCurrency(), currency);
        assertEquals(payment2.getTransactions().get(1).getProcessedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment2.getTransactions().get(1).getProcessedCurrency(), currency);
        assertEquals(payment2.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment2.getTransactions().get(1).getTransactionType(), TransactionType.CHARGEBACK);
        assertNull(payment2.getTransactions().get(1).getGatewayErrorMsg());
        assertNull(payment2.getTransactions().get(1).getGatewayErrorCode());

        assertEquals(paymentDao.getPayment(payment.getId(), internalCallContext).getStateName(), "CHARGEBACK_SUCCESS");
        assertEquals(paymentDao.getPayment(payment.getId(), internalCallContext).getLastSuccessStateName(), "CHARGEBACK_SUCCESS");

        try {
            paymentApi.createRefund(account,
                                    payment.getId(),
                                    requestedAmount,
                                    currency,
                                    null,
                                    UUID.randomUUID().toString(),
                                    properties,
                                    callContext);
            Assert.fail("Refunds are no longer permitted after a chargeback");
        } catch (final PaymentApiException e) {
            assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
        }

        // First reversal
        final Payment payment3 = paymentApi.createChargebackReversal(account,
                                                                     payment.getId(),
                                                                     null,
                                                                     chargebackTransactionExternalKey,
                                                                     callContext);

        assertEquals(payment3.getExternalKey(), paymentExternalKey);
        assertEquals(payment3.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment3.getAccountId(), account.getId());
        assertEquals(payment3.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment3.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        // Actual purchase amount
        assertEquals(payment3.getPurchasedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment3.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment3.getCurrency(), currency);

        assertEquals(payment3.getTransactions().size(), 3);
        assertEquals(payment3.getTransactions().get(2).getExternalKey(), chargebackTransactionExternalKey);
        assertEquals(payment3.getTransactions().get(2).getPaymentId(), payment.getId());
        assertNull(payment3.getTransactions().get(2).getAmount());
        assertNull(payment3.getTransactions().get(2).getCurrency());
        assertEquals(payment3.getTransactions().get(2).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        assertNull(payment3.getTransactions().get(2).getProcessedCurrency());
        assertEquals(payment3.getTransactions().get(2).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        assertEquals(payment3.getTransactions().get(2).getTransactionType(), TransactionType.CHARGEBACK);
        assertNull(payment3.getTransactions().get(2).getGatewayErrorMsg());
        assertNull(payment3.getTransactions().get(2).getGatewayErrorCode());

        assertEquals(paymentDao.getPayment(payment.getId(), internalCallContext).getStateName(), "CHARGEBACK_FAILED");
        assertEquals(paymentDao.getPayment(payment.getId(), internalCallContext).getLastSuccessStateName(), "CHARGEBACK_FAILED");

        // Attempt a refund
        final BigDecimal refundAmount = BigDecimal.ONE;
        final String refundTransactionExternalKey = UUID.randomUUID().toString();
        final Payment payment4 = paymentApi.createRefund(account,
                                                         payment.getId(),
                                                         refundAmount,
                                                         currency,
                                                         null,
                                                         refundTransactionExternalKey,
                                                         properties,
                                                         callContext);

        assertEquals(payment4.getExternalKey(), paymentExternalKey);
        assertEquals(payment4.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment4.getAccountId(), account.getId());
        assertEquals(payment4.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment4.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        // Actual purchase amount
        assertEquals(payment4.getPurchasedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment4.getRefundedAmount().compareTo(refundAmount), 0);
        assertEquals(payment4.getCurrency(), currency);

        assertEquals(payment4.getTransactions().size(), 4);
        assertEquals(payment4.getTransactions().get(3).getExternalKey(), refundTransactionExternalKey);
        assertEquals(payment4.getTransactions().get(3).getPaymentId(), payment.getId());
        assertEquals(payment4.getTransactions().get(3).getAmount().compareTo(refundAmount), 0);
        assertEquals(payment4.getTransactions().get(3).getCurrency(), currency);
        assertEquals(payment4.getTransactions().get(3).getProcessedAmount().compareTo(refundAmount), 0);
        assertEquals(payment4.getTransactions().get(3).getProcessedCurrency(), currency);
        assertEquals(payment4.getTransactions().get(3).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment4.getTransactions().get(3).getTransactionType(), TransactionType.REFUND);
        assertEquals(payment4.getTransactions().get(3).getGatewayErrorMsg(), "");
        assertEquals(payment4.getTransactions().get(3).getGatewayErrorCode(), "");

        assertEquals(paymentDao.getPayment(payment.getId(), internalCallContext).getStateName(), "REFUND_SUCCESS");
        assertEquals(paymentDao.getPayment(payment.getId(), internalCallContext).getLastSuccessStateName(), "REFUND_SUCCESS");

        // Second chargeback
        final BigDecimal secondChargebackAmount = requestedAmount.add(refundAmount.negate());
        final Payment payment5 = paymentApi.createChargeback(account,
                                                             payment.getId(),
                                                             secondChargebackAmount,
                                                             currency,
                                                             null,
                                                             chargebackTransactionExternalKey,
                                                             callContext);

        assertEquals(payment5.getExternalKey(), paymentExternalKey);
        assertEquals(payment5.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment5.getAccountId(), account.getId());
        assertEquals(payment5.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment5.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        // Purchase amount zero-ed out
        assertEquals(payment5.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment5.getRefundedAmount().compareTo(refundAmount), 0);
        assertEquals(payment5.getCurrency(), currency);

        assertEquals(payment5.getTransactions().size(), 5);
        assertEquals(payment5.getTransactions().get(4).getExternalKey(), chargebackTransactionExternalKey);
        assertEquals(payment5.getTransactions().get(4).getPaymentId(), payment.getId());
        assertEquals(payment5.getTransactions().get(4).getAmount().compareTo(secondChargebackAmount), 0);
        assertEquals(payment5.getTransactions().get(4).getCurrency(), currency);
        assertEquals(payment5.getTransactions().get(4).getProcessedAmount().compareTo(secondChargebackAmount), 0);
        assertEquals(payment5.getTransactions().get(4).getProcessedCurrency(), currency);
        assertEquals(payment5.getTransactions().get(4).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment5.getTransactions().get(4).getTransactionType(), TransactionType.CHARGEBACK);
        assertNull(payment5.getTransactions().get(4).getGatewayErrorMsg());
        assertNull(payment5.getTransactions().get(4).getGatewayErrorCode());

        assertEquals(paymentDao.getPayment(payment.getId(), internalCallContext).getStateName(), "CHARGEBACK_SUCCESS");
        assertEquals(paymentDao.getPayment(payment.getId(), internalCallContext).getLastSuccessStateName(), "CHARGEBACK_SUCCESS");

        try {
            paymentApi.createRefund(account,
                                    payment.getId(),
                                    refundAmount,
                                    currency,
                                    null,
                                    UUID.randomUUID().toString(),
                                    properties,
                                    callContext);
            Assert.fail("Refunds are no longer permitted after a chargeback");
        } catch (final PaymentApiException e) {
            assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
        }

        // Second reversal
        final Payment payment6 = paymentApi.createChargebackReversal(account,
                                                                     payment.getId(),
                                                                     null,
                                                                     chargebackTransactionExternalKey,
                                                                     callContext);

        assertEquals(payment6.getExternalKey(), paymentExternalKey);
        assertEquals(payment6.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment6.getAccountId(), account.getId());
        assertEquals(payment6.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment6.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        // Actual purchase amount
        assertEquals(payment6.getPurchasedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment6.getRefundedAmount().compareTo(refundAmount), 0);
        assertEquals(payment6.getCurrency(), currency);

        assertEquals(payment6.getTransactions().size(), 6);
        assertEquals(payment6.getTransactions().get(5).getExternalKey(), chargebackTransactionExternalKey);
        assertEquals(payment6.getTransactions().get(5).getPaymentId(), payment.getId());
        assertNull(payment6.getTransactions().get(5).getAmount());
        assertNull(payment6.getTransactions().get(5).getCurrency());
        assertEquals(payment6.getTransactions().get(5).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        assertNull(payment6.getTransactions().get(5).getProcessedCurrency());
        assertEquals(payment6.getTransactions().get(5).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        assertEquals(payment6.getTransactions().get(5).getTransactionType(), TransactionType.CHARGEBACK);
        assertNull(payment6.getTransactions().get(5).getGatewayErrorMsg());
        assertNull(payment6.getTransactions().get(5).getGatewayErrorCode());

        assertEquals(paymentDao.getPayment(payment.getId(), internalCallContext).getStateName(), "CHARGEBACK_FAILED");
        assertEquals(paymentDao.getPayment(payment.getId(), internalCallContext).getLastSuccessStateName(), "CHARGEBACK_FAILED");
    }

    @Test(groups = "slow")
    public void testCreateChargebackReversalBeforeChargeback() throws PaymentApiException {
        final BigDecimal requestedAmount = BigDecimal.TEN;
        final Currency currency = Currency.AED;
        final String paymentExternalKey = UUID.randomUUID().toString();
        final String purchaseTransactionExternalKey = UUID.randomUUID().toString();
        final String chargebackTransactionExternalKey = UUID.randomUUID().toString();
        final ImmutableList<PluginProperty> properties = ImmutableList.<PluginProperty>of();

        final Payment payment = paymentApi.createPurchase(account,
                                                          account.getPaymentMethodId(),
                                                          null,
                                                          requestedAmount,
                                                          currency,
                                                          null,
                                                          paymentExternalKey,
                                                          purchaseTransactionExternalKey,
                                                          properties,
                                                          callContext);

        assertEquals(payment.getExternalKey(), paymentExternalKey);
        assertEquals(payment.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment.getAccountId(), account.getId());
        assertEquals(payment.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getPurchasedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCurrency(), currency);

        assertEquals(payment.getTransactions().size(), 1);
        assertEquals(payment.getTransactions().get(0).getExternalKey(), purchaseTransactionExternalKey);
        assertEquals(payment.getTransactions().get(0).getPaymentId(), payment.getId());
        assertEquals(payment.getTransactions().get(0).getAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getTransactions().get(0).getCurrency(), currency);
        assertEquals(payment.getTransactions().get(0).getProcessedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment.getTransactions().get(0).getProcessedCurrency(), currency);
        assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment.getTransactions().get(0).getTransactionType(), TransactionType.PURCHASE);
        assertEquals(payment.getTransactions().get(0).getGatewayErrorMsg(), "");
        assertEquals(payment.getTransactions().get(0).getGatewayErrorCode(), "");

        assertEquals(paymentDao.getPayment(payment.getId(), internalCallContext).getStateName(), "PURCHASE_SUCCESS");
        assertEquals(paymentDao.getPayment(payment.getId(), internalCallContext).getLastSuccessStateName(), "PURCHASE_SUCCESS");

        try {
            paymentApi.createChargebackReversal(account,
                                                payment.getId(),
                                                null,
                                                chargebackTransactionExternalKey,
                                                callContext);
            Assert.fail("Chargeback reversals are not permitted before a chargeback");
        } catch (final PaymentApiException e) {
            assertEquals(e.getCode(), ErrorCode.PAYMENT_NO_SUCH_SUCCESS_PAYMENT.getCode());
        }

        assertEquals(paymentApi.getPayment(payment.getId(), false, false, ImmutableList.<PluginProperty>of(), callContext).getTransactions().size(), 1);

        assertEquals(paymentDao.getPayment(payment.getId(), internalCallContext).getStateName(), "PURCHASE_SUCCESS");
        assertEquals(paymentDao.getPayment(payment.getId(), internalCallContext).getLastSuccessStateName(), "PURCHASE_SUCCESS");
    }

    @Test(groups = "slow")
    public void testVerifyJanitorFromPendingDuringCompletionFlow() throws PaymentApiException {
        final BigDecimal authAmount = BigDecimal.TEN;
        final String transactionExternalKey = UUID.randomUUID().toString();

        final Payment initialPayment = createPayment(TransactionType.AUTHORIZE, null, UUID.randomUUID().toString(), transactionExternalKey, authAmount, PaymentPluginStatus.PENDING);
        Assert.assertEquals(initialPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PENDING);

        mockPaymentProviderPlugin.overridePaymentPluginStatus(initialPayment.getId(), initialPayment.getTransactions().get(0).getId(), PaymentPluginStatus.PROCESSED);

        try {
            final Payment completedPayment = createPayment(TransactionType.AUTHORIZE, initialPayment.getId(), initialPayment.getExternalKey(), transactionExternalKey, authAmount, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_ACTIVE_TRANSACTION_KEY_EXISTS.getCode());
        }
    }

    @Test(groups = "slow")
    public void testVerifyJanitorFromUnknownDuringCompletionFlow() throws PaymentApiException {
        final BigDecimal authAmount = BigDecimal.TEN;
        final String transactionExternalKey = UUID.randomUUID().toString();

        final Payment initialPayment = createPayment(TransactionType.AUTHORIZE, null, UUID.randomUUID().toString(), transactionExternalKey, authAmount, PaymentPluginStatus.UNDEFINED);
        Assert.assertEquals(initialPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.UNKNOWN);

        mockPaymentProviderPlugin.overridePaymentPluginStatus(initialPayment.getId(), initialPayment.getTransactions().get(0).getId(), PaymentPluginStatus.PROCESSED);

        try {
            final Payment completedPayment = createPayment(TransactionType.AUTHORIZE, initialPayment.getId(), initialPayment.getExternalKey(), transactionExternalKey, authAmount, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_ACTIVE_TRANSACTION_KEY_EXISTS.getCode());
        }
    }

    @Test(groups = "slow")
    public void testNotifyPendingTransactionOfStateChanged() throws PaymentApiException {

        final BigDecimal authAmount = BigDecimal.TEN;

        final String paymentExternalKey = "rouge";
        final String transactionExternalKey = "vert";

        final Payment initialPayment = createPayment(TransactionType.AUTHORIZE, null, paymentExternalKey, transactionExternalKey, authAmount, PaymentPluginStatus.PENDING);

        final Payment payment = paymentApi.notifyPendingTransactionOfStateChanged(account, initialPayment.getTransactions().get(0).getId(), true, callContext);

        assertEquals(payment.getExternalKey(), paymentExternalKey);
        assertEquals(payment.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment.getAccountId(), account.getId());
        assertEquals(payment.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCurrency(), Currency.USD);

        assertEquals(payment.getTransactions().size(), 1);
        assertEquals(payment.getTransactions().get(0).getExternalKey(), transactionExternalKey);
        assertEquals(payment.getTransactions().get(0).getPaymentId(), payment.getId());
        assertEquals(payment.getTransactions().get(0).getAmount().compareTo(authAmount), 0);
        assertEquals(payment.getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payment.getTransactions().get(0).getProcessedAmount().compareTo(authAmount), 0);
        assertEquals(payment.getTransactions().get(0).getProcessedCurrency(), Currency.USD);

        assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment.getTransactions().get(0).getTransactionType(), TransactionType.AUTHORIZE);
        assertNull(payment.getTransactions().get(0).getGatewayErrorMsg());
        assertNull(payment.getTransactions().get(0).getGatewayErrorCode());
    }

    @Test(groups = "slow")
    public void testSimpleAuthCaptureWithInvalidPaymentId() throws Exception {
        final BigDecimal requestedAmount = new BigDecimal("80.0091");

        final Payment initialPayment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, account.getCurrency(), null,
                                                                      UUID.randomUUID().toString(), UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);
        try {
            paymentApi.createCapture(account, UUID.randomUUID(), requestedAmount, account.getCurrency(), null, UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);
            Assert.fail("Expected capture to fail...");
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_NO_SUCH_PAYMENT.getCode());

            final Payment latestPayment = paymentApi.getPayment(initialPayment.getId(), true, false, ImmutableList.<PluginProperty>of(), callContext);
            assertEquals(latestPayment, initialPayment);
        }
    }

    @Test(groups = "slow")
    public void testSimpleAuthCaptureWithInvalidCurrency() throws Exception {
        final BigDecimal requestedAmount = new BigDecimal("80.0091");

        final Payment initialPayment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, account.getCurrency(), null,
                                                                      UUID.randomUUID().toString(), UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);

        try {
            paymentApi.createCapture(account, initialPayment.getId(), requestedAmount, Currency.AMD, null, UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);
            Assert.fail("Expected capture to fail...");
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_PARAMETER.getCode());

            final Payment latestPayment = paymentApi.getPayment(initialPayment.getId(), true, false, ImmutableList.<PluginProperty>of(), callContext);
            assertEquals(latestPayment, initialPayment);
        }
    }

    @Test(groups = "slow")
    public void testInvalidTransitionAfterFailure() throws PaymentApiException {

        final BigDecimal requestedAmount = BigDecimal.TEN;

        final String paymentExternalKey = "krapo";
        final String transactionExternalKey = "grenouye";

        final Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, Currency.EUR,  null,paymentExternalKey, transactionExternalKey,
                                                               ImmutableList.<PluginProperty>of(), callContext);

        // Hack the Database to make it look like it was a failure
        paymentDao.updatePaymentAndTransactionOnCompletion(account.getId(), null, payment.getId(), TransactionType.AUTHORIZE, "AUTH_ERRORED", null,
                                                           payment.getTransactions().get(0).getId(), TransactionStatus.PLUGIN_FAILURE, null, null, null, null, internalCallContext);
        final PaymentSqlDao paymentSqlDao = dbi.onDemand(PaymentSqlDao.class);
        paymentSqlDao.updateLastSuccessPaymentStateName(payment.getId().toString(), "AUTH_ERRORED", null, internalCallContext);

        try {
            paymentApi.createCapture(account, payment.getId(), requestedAmount, Currency.EUR, null, "tetard", ImmutableList.<PluginProperty>of(), callContext);
            Assert.fail("Unexpected success");
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
        }
    }

    @Test(groups = "slow")
    public void testApiWithPendingPaymentTransaction() throws Exception {
        for (final TransactionType transactionType : ImmutableList.<TransactionType>of(TransactionType.AUTHORIZE, TransactionType.PURCHASE, TransactionType.CREDIT)) {
            testApiWithPendingPaymentTransaction(transactionType, BigDecimal.TEN, BigDecimal.TEN);
            testApiWithPendingPaymentTransaction(transactionType, BigDecimal.TEN, BigDecimal.ONE);
            // See https://github.com/killbill/killbill/issues/372
            testApiWithPendingPaymentTransaction(transactionType, BigDecimal.TEN, null);
        }
    }

    @Test(groups = "slow")
    public void testApiWithPendingRefundPaymentTransaction() throws Exception {
        final String paymentExternalKey = UUID.randomUUID().toString();
        final String paymentTransactionExternalKey = UUID.randomUUID().toString();
        final String refundTransactionExternalKey = UUID.randomUUID().toString();
        final BigDecimal requestedAmount = BigDecimal.TEN;
        final BigDecimal refundAmount = BigDecimal.ONE;
        final Iterable<PluginProperty> pendingPluginProperties = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, TransactionStatus.PENDING.toString(), false));

        final Payment payment = createPayment(TransactionType.PURCHASE, null, paymentExternalKey, paymentTransactionExternalKey, requestedAmount, PaymentPluginStatus.PROCESSED);
        assertNotNull(payment);
        Assert.assertEquals(payment.getExternalKey(), paymentExternalKey);
        Assert.assertEquals(payment.getTransactions().size(), 1);
        Assert.assertEquals(payment.getTransactions().get(0).getAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getTransactions().get(0).getProcessedAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(payment.getTransactions().get(0).getCurrency(), account.getCurrency());
        Assert.assertEquals(payment.getTransactions().get(0).getExternalKey(), paymentTransactionExternalKey);
        Assert.assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);

        final Payment pendingRefund = paymentApi.createRefund(account,
                                                              payment.getId(),
                                                              requestedAmount,
                                                              account.getCurrency(),
                                                              null,
                                                              refundTransactionExternalKey,
                                                              pendingPluginProperties,
                                                              callContext);
        verifyRefund(pendingRefund, paymentExternalKey, paymentTransactionExternalKey, refundTransactionExternalKey, requestedAmount, requestedAmount, TransactionStatus.PENDING);

        // Test Janitor path (regression test for https://github.com/killbill/killbill/issues/363)
        verifyPaymentViaGetPath(pendingRefund);

        // See https://github.com/killbill/killbill/issues/372
        final Payment pendingRefund2 = paymentApi.createRefund(account,
                                                               payment.getId(),
                                                               null,
                                                               null,
                                                               null,
                                                               refundTransactionExternalKey,
                                                               pendingPluginProperties,
                                                               callContext);
        verifyRefund(pendingRefund2, paymentExternalKey, paymentTransactionExternalKey, refundTransactionExternalKey, requestedAmount, requestedAmount, TransactionStatus.PENDING);

        verifyPaymentViaGetPath(pendingRefund2);

        // Note: we change the refund amount
        final Payment pendingRefund3 = paymentApi.createRefund(account,
                                                               payment.getId(),
                                                               refundAmount,
                                                               account.getCurrency(),
                                                               null,
                                                               refundTransactionExternalKey,
                                                               pendingPluginProperties,
                                                               callContext);
        verifyRefund(pendingRefund3, paymentExternalKey, paymentTransactionExternalKey, refundTransactionExternalKey, requestedAmount, refundAmount, TransactionStatus.PENDING);

        verifyPaymentViaGetPath(pendingRefund3);

        // Pass null, we revert back to the original refund amount
        final Payment pendingRefund4 = paymentApi.createRefund(account,
                                                               payment.getId(),
                                                               null,
                                                               null,
                                                               null,
                                                               refundTransactionExternalKey,
                                                               ImmutableList.<PluginProperty>of(),
                                                               callContext);
        verifyRefund(pendingRefund4, paymentExternalKey, paymentTransactionExternalKey, refundTransactionExternalKey, requestedAmount, requestedAmount, TransactionStatus.SUCCESS);

        verifyPaymentViaGetPath(pendingRefund4);
    }

    @Test(groups = "slow")
    public void testCompletionOfUnknownAuthorization() throws Exception {
        final String paymentExternalKey = UUID.randomUUID().toString();
        final String paymentTransactionExternalKey = UUID.randomUUID().toString();
        final BigDecimal requestedAmount = BigDecimal.TEN;

        final Payment pendingPayment = createPayment(TransactionType.AUTHORIZE, null, paymentExternalKey, paymentTransactionExternalKey, requestedAmount, PaymentPluginStatus.UNDEFINED);
        assertNotNull(pendingPayment);
        Assert.assertEquals(pendingPayment.getTransactions().size(), 1);
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.UNKNOWN);

        try {
            // Attempt to complete the payment
            createPayment(TransactionType.AUTHORIZE, pendingPayment.getId(), paymentExternalKey, paymentTransactionExternalKey, requestedAmount, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
        }
    }

    @Test(groups = "slow")
    public void testCompletionOfUnknownCapture() throws Exception {
        final String paymentExternalKey = UUID.randomUUID().toString();
        final BigDecimal requestedAmount = BigDecimal.TEN;

        final Payment authorization = createPayment(TransactionType.AUTHORIZE, null, paymentExternalKey, UUID.randomUUID().toString(), requestedAmount, PaymentPluginStatus.PROCESSED);
        assertNotNull(authorization);
        Assert.assertEquals(authorization.getTransactions().size(), 1);
        Assert.assertEquals(authorization.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);

        final String paymentTransactionExternalKey = UUID.randomUUID().toString();
        Payment pendingPayment = createPayment(TransactionType.CAPTURE, authorization.getId(), paymentExternalKey, paymentTransactionExternalKey, requestedAmount, PaymentPluginStatus.UNDEFINED);
        Assert.assertEquals(pendingPayment.getTransactions().size(), 2);
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(pendingPayment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.UNKNOWN);

        try {
            // Attempt to complete the payment
            pendingPayment = createPayment(TransactionType.CAPTURE, authorization.getId(), paymentExternalKey, paymentTransactionExternalKey, requestedAmount, PaymentPluginStatus.PROCESSED);
            Assert.assertEquals(pendingPayment.getTransactions().size(), 2);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
        }
    }

    @Test(groups = "slow")
    public void testDoubleCaptureOnASuccessfulCapture() throws Exception {
        final String paymentExternalKey = UUID.randomUUID().toString();
        final BigDecimal requestedAmount = BigDecimal.TEN;

        final Payment authorization = createPayment(TransactionType.AUTHORIZE, null, paymentExternalKey, UUID.randomUUID().toString(), requestedAmount, PaymentPluginStatus.PROCESSED);
        assertNotNull(authorization);
        Assert.assertEquals(authorization.getTransactions().size(), 1);
        Assert.assertEquals(authorization.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);

        final String paymentTransactionExternalKey = UUID.randomUUID().toString();

        // 1st capture with success
        Payment pendingPayment = createPayment(TransactionType.CAPTURE, authorization.getId(), paymentExternalKey, paymentTransactionExternalKey, requestedAmount, PaymentPluginStatus.PROCESSED);
        Assert.assertEquals(pendingPayment.getTransactions().size(), 2);
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(pendingPayment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);

        // 2nd capture request with identical transaction external key
        try {
            // Attempt to complete the payment
            pendingPayment = createPayment(TransactionType.CAPTURE, authorization.getId(), paymentExternalKey, paymentTransactionExternalKey, requestedAmount, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(pendingPayment.getTransactions().size(), 2); //should be 2
            Assert.assertEquals(pendingPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
            Assert.assertEquals(pendingPayment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_ACTIVE_TRANSACTION_KEY_EXISTS.getCode());
        }

    }

    @Test(groups = "slow")
    public void testDoubleCaptureOnAPotentiallySuccessfulCapture() throws Exception {
        final String paymentExternalKey = UUID.randomUUID().toString();
        final BigDecimal requestedAmount = BigDecimal.TEN;

        final Payment authorization = createPayment(TransactionType.AUTHORIZE, null, paymentExternalKey, UUID.randomUUID().toString(), requestedAmount, PaymentPluginStatus.PROCESSED);
        assertNotNull(authorization);
        Assert.assertEquals(authorization.getTransactions().size(), 1);
        Assert.assertEquals(authorization.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);

        final String paymentTransactionExternalKey = UUID.randomUUID().toString();

        // 1st capture with UNDEFINED
        Payment pendingPayment = createPayment(TransactionType.CAPTURE, authorization.getId(), paymentExternalKey, paymentTransactionExternalKey, requestedAmount, PaymentPluginStatus.UNDEFINED);
        Assert.assertEquals(pendingPayment.getTransactions().size(), 2);
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(pendingPayment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.UNKNOWN);

        // but actually successful
        mockPaymentProviderPlugin.overridePaymentPluginStatus(pendingPayment.getId(), pendingPayment.getTransactions().get(1).getId(), PaymentPluginStatus.PROCESSED);

        // 2nd capture request with identical transaction external key
        try {
            createPayment(TransactionType.CAPTURE, authorization.getId(), paymentExternalKey, paymentTransactionExternalKey, requestedAmount, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            final Payment refreshedPayment = paymentApi.getPayment(pendingPayment.getId(), true, false, ImmutableList.<PluginProperty>of(), callContext);
            Assert.assertEquals(refreshedPayment.getTransactions().size(), 2);
            Assert.assertEquals(refreshedPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
            Assert.assertEquals(refreshedPayment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_ACTIVE_TRANSACTION_KEY_EXISTS.getCode());
        }
    }

    @Test(groups = "slow")
    public void testCaptureWithAPotentiallySuccessfulCapture() throws Exception {
        final String paymentExternalKey = UUID.randomUUID().toString();
        final BigDecimal requestedAmount = BigDecimal.TEN;

        final Payment authorization = createPayment(TransactionType.AUTHORIZE, null, paymentExternalKey, UUID.randomUUID().toString(), requestedAmount, PaymentPluginStatus.PROCESSED);
        assertNotNull(authorization);
        Assert.assertEquals(authorization.getTransactions().size(), 1);
        Assert.assertEquals(authorization.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);

        final String paymentTransactionExternalKey = UUID.randomUUID().toString();

        // 1st capture with UNDEFINED
        Payment pendingPayment = createPayment(TransactionType.CAPTURE, authorization.getId(), paymentExternalKey, paymentTransactionExternalKey, requestedAmount, PaymentPluginStatus.UNDEFINED);
        Assert.assertEquals(pendingPayment.getTransactions().size(), 2);
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(pendingPayment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.UNKNOWN);

        // but actually successful
        mockPaymentProviderPlugin.overridePaymentPluginStatus(pendingPayment.getId(), pendingPayment.getTransactions().get(1).getId(), PaymentPluginStatus.PROCESSED);

        final String anotherPaymentTransactionExternalKey = UUID.randomUUID().toString();
        // 2nd capture request with a different transaction external key
        pendingPayment = createPayment(TransactionType.CAPTURE, authorization.getId(), paymentExternalKey, anotherPaymentTransactionExternalKey, requestedAmount, PaymentPluginStatus.PROCESSED);
        Assert.assertEquals(pendingPayment.getTransactions().size(), 3);
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(pendingPayment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(pendingPayment.getTransactions().get(2).getTransactionStatus(), TransactionStatus.SUCCESS);
    }

    @Test(groups = "slow")
    public void testDoubleCaptureOnAPotentiallyFailedCapture() throws Exception {
        final String paymentExternalKey = UUID.randomUUID().toString();
        final BigDecimal requestedAmount = BigDecimal.TEN;

        final Payment authorization = createPayment(TransactionType.AUTHORIZE, null, paymentExternalKey, UUID.randomUUID().toString(), requestedAmount, PaymentPluginStatus.PROCESSED);
        assertNotNull(authorization);
        Assert.assertEquals(authorization.getTransactions().size(), 1);
        Assert.assertEquals(authorization.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);

        final String paymentTransactionExternalKey = UUID.randomUUID().toString();

        // 1st capture with UNDEFINED
        Payment pendingPayment = createPayment(TransactionType.CAPTURE, authorization.getId(), paymentExternalKey, paymentTransactionExternalKey, requestedAmount, PaymentPluginStatus.UNDEFINED);
        Assert.assertEquals(pendingPayment.getTransactions().size(), 2);
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(pendingPayment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.UNKNOWN);

        // but actually failed
        mockPaymentProviderPlugin.overridePaymentPluginStatus(pendingPayment.getId(), pendingPayment.getTransactions().get(1).getId(), PaymentPluginStatus.ERROR);

        // 2nd capture request with identical transaction external key
        pendingPayment = createPayment(TransactionType.CAPTURE, authorization.getId(), paymentExternalKey, paymentTransactionExternalKey, requestedAmount, PaymentPluginStatus.PROCESSED);
        Assert.assertEquals(pendingPayment.getTransactions().size(), 3);
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(pendingPayment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        Assert.assertEquals(pendingPayment.getTransactions().get(2).getTransactionStatus(), TransactionStatus.SUCCESS);
    }

    @Test(groups = "slow")
    public void testCaptureWithAPotentiallyFailedCapture() throws Exception {
        final String paymentExternalKey = UUID.randomUUID().toString();
        final BigDecimal requestedAmount = BigDecimal.TEN;

        final Payment authorization = createPayment(TransactionType.AUTHORIZE, null, paymentExternalKey, UUID.randomUUID().toString(), requestedAmount, PaymentPluginStatus.PROCESSED);
        assertNotNull(authorization);
        Assert.assertEquals(authorization.getTransactions().size(), 1);
        Assert.assertEquals(authorization.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);

        final String paymentTransactionExternalKey = UUID.randomUUID().toString();

        // 1st capture with UNDEFINED
        Payment pendingPayment = createPayment(TransactionType.CAPTURE, authorization.getId(), paymentExternalKey, paymentTransactionExternalKey, requestedAmount, PaymentPluginStatus.UNDEFINED);
        Assert.assertEquals(pendingPayment.getTransactions().size(), 2);
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(pendingPayment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.UNKNOWN);

        // but actually failed
        mockPaymentProviderPlugin.overridePaymentPluginStatus(pendingPayment.getId(), pendingPayment.getTransactions().get(1).getId(), PaymentPluginStatus.ERROR);

        final String anotherPaymentTransactionExternalKey = UUID.randomUUID().toString();
        // 2nd capture request with different transaction external key
        pendingPayment = createPayment(TransactionType.CAPTURE, authorization.getId(), paymentExternalKey, anotherPaymentTransactionExternalKey, requestedAmount, PaymentPluginStatus.PROCESSED);
        Assert.assertEquals(pendingPayment.getTransactions().size(), 3);
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(pendingPayment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        Assert.assertEquals(pendingPayment.getTransactions().get(2).getTransactionStatus(), TransactionStatus.SUCCESS);
    }

    @Test(groups = "slow")
    public void testCreatePurchaseWithTimeout() throws Exception {
        final BigDecimal requestedAmount = BigDecimal.TEN;
        final String paymentExternalKey = "ohhhh";
        final String transactionExternalKey = "naaahhh";

        mockPaymentProviderPlugin.makePluginWaitSomeMilliseconds((int) (paymentConfig.getPaymentPluginTimeout().getMillis() + 100));
        try {
            paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, null,
                                      paymentExternalKey, transactionExternalKey, ImmutableList.<PluginProperty>of(), callContext);
            fail();
        } catch (PaymentApiException e) {
            assertEquals(e.getCode(), ErrorCode.PAYMENT_PLUGIN_TIMEOUT.getCode());
        }
    }

    @Test(groups = "slow")
    public void testCreatePurchaseWithControlTimeout() throws Exception {
        final BigDecimal requestedAmount = BigDecimal.ONE;
        final String paymentExternalKey = "111111";
        final String transactionExternalKey = "11111";

        mockPaymentProviderPlugin.makePluginWaitSomeMilliseconds((int) (paymentConfig.getPaymentPluginTimeout().getMillis() + 100));
        try {
            paymentApi.createPurchaseWithPaymentControl(
                    account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, null, paymentExternalKey,
                    transactionExternalKey, ImmutableList.<PluginProperty>of(), CONTROL_PLUGIN_OPTIONS, callContext);
            fail();
        } catch (PaymentApiException e) {
            assertEquals(e.getCode(), ErrorCode.PAYMENT_PLUGIN_TIMEOUT.getCode());
        }
    }

    @Test(groups = "slow")
    public void testSanityAcrossTransactionTypes() throws PaymentApiException {
        final BigDecimal requestedAmount = BigDecimal.TEN;
        final String paymentExternalKey = "ahhhhhhhh";
        final String transactionExternalKey = "okkkkkkk";

        final Payment pendingPayment = createPayment(TransactionType.AUTHORIZE, null, paymentExternalKey, transactionExternalKey, requestedAmount, PaymentPluginStatus.PENDING);
        assertNotNull(pendingPayment);
        Assert.assertEquals(pendingPayment.getExternalKey(), paymentExternalKey);
        Assert.assertEquals(pendingPayment.getTransactions().size(), 1);
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getProcessedAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getCurrency(), account.getCurrency());
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getExternalKey(), transactionExternalKey);
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PENDING);

        try {
            createPayment(TransactionType.PURCHASE, null, paymentExternalKey, transactionExternalKey, requestedAmount, PaymentPluginStatus.PENDING);
            Assert.fail("PURCHASE transaction with same key should have failed");
        } catch (final PaymentApiException expected) {
            Assert.assertEquals(expected.getCode(), ErrorCode.PAYMENT_INVALID_PARAMETER.getCode());
        }
    }

    @Test(groups = "slow")
    public void testSuccessfulInitialTransactionToSameTransaction() throws Exception {
        final BigDecimal requestedAmount = BigDecimal.TEN;
        for (final TransactionType transactionType : ImmutableList.<TransactionType>of(TransactionType.AUTHORIZE, TransactionType.PURCHASE, TransactionType.CREDIT)) {
            final String paymentExternalKey = UUID.randomUUID().toString();
            final String keyA = UUID.randomUUID().toString();

            final Payment processedPayment = createPayment(transactionType, null, paymentExternalKey, keyA, requestedAmount, PaymentPluginStatus.PROCESSED);
            assertNotNull(processedPayment);
            Assert.assertEquals(processedPayment.getTransactions().size(), 1);
            Assert.assertEquals(processedPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);

            // Attempt to create another {AUTH, PURCHASE, CREDIT} with different key => KB state machine should make the request fail as we don't allow
            // multiple SUCCESS {AUTH, PURCHASE, CREDIT}
            final String keyB = UUID.randomUUID().toString();
            try {
                createPayment(transactionType, processedPayment.getId(), paymentExternalKey, keyB, requestedAmount, PaymentPluginStatus.PROCESSED);
                Assert.fail("Retrying initial successful transaction (AUTHORIZE, PURCHASE, CREDIT) with same different key should fail");
            } catch (final PaymentApiException e) {
                Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
            }

            // Attempt to create another {AUTH, PURCHASE, CREDIT} with same key => key constraint should make the request fail
            try {
                createPayment(transactionType, processedPayment.getId(), paymentExternalKey, keyA, requestedAmount, PaymentPluginStatus.PROCESSED);
                Assert.fail("Retrying initial successful transaction (AUTHORIZE, PURCHASE, CREDIT) with same transaction key should fail");
            } catch (final PaymentApiException e) {
                Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_ACTIVE_TRANSACTION_KEY_EXISTS.getCode());
            }
        }
    }

    @Test(groups = "slow")
    public void testPendingInitialTransactionToSameTransaction() throws Exception {
        final BigDecimal requestedAmount = BigDecimal.TEN;
        for (final TransactionType transactionType : ImmutableList.<TransactionType>of(TransactionType.AUTHORIZE, TransactionType.PURCHASE, TransactionType.CREDIT)) {
            final String paymentExternalKey = UUID.randomUUID().toString();
            final String keyA = UUID.randomUUID().toString();

            final Payment pendingPayment = createPayment(transactionType, null, paymentExternalKey, keyA, requestedAmount, PaymentPluginStatus.PENDING);
            assertNotNull(pendingPayment);
            Assert.assertEquals(pendingPayment.getTransactions().size(), 1);
            Assert.assertEquals(pendingPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PENDING);

            // Attempt to create another {AUTH, PURCHASE, CREDIT} with different key => KB state machine should make the request fail as we don't allow
            // multiple SUCCESS {AUTH, PURCHASE, CREDIT}
            final String keyB = UUID.randomUUID().toString();
            try {
                createPayment(transactionType, pendingPayment.getId(), paymentExternalKey, keyB, requestedAmount, PaymentPluginStatus.PROCESSED);
                Assert.fail("Retrying initial successful transaction (AUTHORIZE, PURCHASE, CREDIT) with same different key should fail");
            } catch (final PaymentApiException e) {
                Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
            }

            // Attempt to create another {AUTH, PURCHASE, CREDIT} with same key => That should work because we are completing the payment
            final Payment completedPayment = createPayment(transactionType, pendingPayment.getId(), paymentExternalKey, keyA, requestedAmount, PaymentPluginStatus.PROCESSED);
            assertNotNull(completedPayment);
            Assert.assertEquals(completedPayment.getId(), pendingPayment.getId());
            Assert.assertEquals(completedPayment.getTransactions().size(), 1);
        }
    }

    @Test(groups = "slow")
    public void testFailedInitialTransactionToSameTransactionWithSameKey() throws Exception {
        final BigDecimal requestedAmount = BigDecimal.TEN;
        for (final TransactionType transactionType : ImmutableList.<TransactionType>of(TransactionType.AUTHORIZE, TransactionType.PURCHASE, TransactionType.CREDIT)) {
            final String paymentExternalKey = UUID.randomUUID().toString();
            final String keyA = UUID.randomUUID().toString();

            final Payment errorPayment = createPayment(transactionType, null, paymentExternalKey, keyA, requestedAmount, PaymentPluginStatus.ERROR);
            assertNotNull(errorPayment);
            Assert.assertEquals(errorPayment.getTransactions().size(), 1);
            Assert.assertEquals(errorPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);

            // Attempt to create another {AUTH, PURCHASE, CREDIT} with same key => That should work because we are completing the payment
            final Payment successfulPayment = createPayment(transactionType, errorPayment.getId(), paymentExternalKey, keyA, requestedAmount, PaymentPluginStatus.PROCESSED);
            assertNotNull(successfulPayment);
            Assert.assertEquals(successfulPayment.getId(), errorPayment.getId());
            Assert.assertEquals(successfulPayment.getTransactions().size(), 2);
        }
    }

    @Test(groups = "slow")
    public void testFailedInitialTransactionToSameTransactionWithDifferentKey() throws Exception {
        final BigDecimal requestedAmount = BigDecimal.TEN;
        for (final TransactionType transactionType : ImmutableList.<TransactionType>of(TransactionType.AUTHORIZE, TransactionType.PURCHASE, TransactionType.CREDIT)) {
            final String paymentExternalKey = UUID.randomUUID().toString();
            final String keyA = UUID.randomUUID().toString();

            final Payment errorPayment = createPayment(transactionType, null, paymentExternalKey, keyA, requestedAmount, PaymentPluginStatus.ERROR);
            assertNotNull(errorPayment);
            Assert.assertEquals(errorPayment.getTransactions().size(), 1);
            Assert.assertEquals(errorPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);

            // Attempt to create another {AUTH, PURCHASE, CREDIT} with different key => KB state machine should make the request fail as we don't allow
            // multiple SUCCESS {AUTH, PURCHASE, CREDIT}
            final String keyB = UUID.randomUUID().toString();
            final Payment successfulPayment = createPayment(transactionType, errorPayment.getId(), paymentExternalKey, keyB, requestedAmount, PaymentPluginStatus.PROCESSED);
            assertNotNull(successfulPayment);
            Assert.assertEquals(successfulPayment.getId(), errorPayment.getId());
            Assert.assertEquals(successfulPayment.getTransactions().size(), 2);
        }
    }

    @Test(groups = "slow")
    public void testKeysSanityOnPending() throws Exception {
        final String authKey = UUID.randomUUID().toString();
        final Payment pendingAuthorization = createPayment(TransactionType.AUTHORIZE, null, null, authKey, BigDecimal.TEN, PaymentPluginStatus.PENDING);
        assertNotNull(pendingAuthorization);
        Assert.assertEquals(pendingAuthorization.getTransactions().size(), 1);
        Assert.assertEquals(pendingAuthorization.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PENDING);

        try {
            // Capture with the same transaction external key should fail
            createPayment(TransactionType.CAPTURE, pendingAuthorization.getId(), null, authKey, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_PARAMETER.getCode());
        }

        final Account account1 = testHelper.createTestAccount("bobo2@gmail.com", true);
        try {
            // Different auth with the same payment external key on a different account should fail
            createPayment(account1, TransactionType.AUTHORIZE, null, pendingAuthorization.getExternalKey(), null, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_DIFFERENT_ACCOUNT_ID.getCode());
        }

        try {
            // Different auth with the same payment external key but different transaction external key on a different account should fail
            createPayment(account1, TransactionType.AUTHORIZE, null, pendingAuthorization.getExternalKey(), UUID.randomUUID().toString(), BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_DIFFERENT_ACCOUNT_ID.getCode());
        }

        try {
            // Different auth with the same transaction external key on a different account should fail
            createPayment(account1, TransactionType.AUTHORIZE, null, null, authKey, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_DIFFERENT_ACCOUNT_ID.getCode());
        }

        try {
            // Auth with the same payment external key but different transaction external key should not go through
            createPayment(TransactionType.AUTHORIZE, null, pendingAuthorization.getExternalKey(), UUID.randomUUID().toString(), BigDecimal.TEN, PaymentPluginStatus.PENDING);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
        }

        // Auth with the same payment and transaction external keys should go through (completion)
        final Payment pendingAuthorization2 = createPayment(TransactionType.AUTHORIZE, null, pendingAuthorization.getExternalKey(), authKey, BigDecimal.TEN, PaymentPluginStatus.PENDING);
        assertNotNull(pendingAuthorization2);
        Assert.assertEquals(pendingAuthorization2.getTransactions().size(), 1);
        Assert.assertEquals(pendingAuthorization2.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PENDING);

        // Auth with the same transaction external key should go through (completion)
        final Payment authorization = createPayment(TransactionType.AUTHORIZE, null, null, authKey, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
        assertNotNull(authorization);
        Assert.assertEquals(authorization.getTransactions().size(), 1);
        Assert.assertEquals(authorization.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);

        try {
            // Different auth with the same payment external key on a different account should still fail
            createPayment(account1, TransactionType.AUTHORIZE, null, pendingAuthorization.getExternalKey(), null, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_DIFFERENT_ACCOUNT_ID.getCode());
        }

        try {
            // Different auth with the same payment external key but different transaction external key on a different account should still fail
            createPayment(account1, TransactionType.AUTHORIZE, null, pendingAuthorization.getExternalKey(), UUID.randomUUID().toString(), BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_DIFFERENT_ACCOUNT_ID.getCode());
        }

        try {
            // Different auth with the same transaction external key on a different account should still fail
            createPayment(account1, TransactionType.AUTHORIZE, null, null, authKey, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_DIFFERENT_ACCOUNT_ID.getCode());
        }

        // Capture with a different transaction external key should go through
        final String captureKey = UUID.randomUUID().toString();
        final Payment pendingCapture = createPayment(TransactionType.CAPTURE, authorization.getId(), null, captureKey, BigDecimal.ONE, PaymentPluginStatus.PENDING);
        Assert.assertEquals(pendingCapture.getTransactions().size(), 2);
        Assert.assertEquals(pendingCapture.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(pendingCapture.getTransactions().get(1).getTransactionStatus(), TransactionStatus.PENDING);

        try {
            // Different auth with the same transaction external key should fail
            createPayment(TransactionType.AUTHORIZE, null, null, captureKey, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_PARAMETER.getCode());
        }

        try {
            // Different auth with the same transaction external key on a different account should fail
            createPayment(account1, TransactionType.AUTHORIZE, null, null, captureKey, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_DIFFERENT_ACCOUNT_ID.getCode());
        }

        // Second capture with the same transaction external key should go through (completion)
        final Payment capturedPayment = createPayment(TransactionType.CAPTURE, authorization.getId(), null, captureKey, BigDecimal.ONE, PaymentPluginStatus.PROCESSED);
        Assert.assertEquals(capturedPayment.getTransactions().size(), 2);
        Assert.assertEquals(capturedPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(capturedPayment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);

        // Second capture with a different transaction external key should go through
        final String captureKey2 = UUID.randomUUID().toString();
        final Payment capturedPayment2 = createPayment(TransactionType.CAPTURE, authorization.getId(), null, captureKey2, BigDecimal.ONE, PaymentPluginStatus.PROCESSED);
        Assert.assertEquals(capturedPayment2.getTransactions().size(), 3);
        Assert.assertEquals(capturedPayment2.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(capturedPayment2.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(capturedPayment2.getTransactions().get(2).getTransactionStatus(), TransactionStatus.SUCCESS);
    }

    @Test(groups = "slow")
    public void testKeysSanityOnSuccess() throws Exception {
        final String authKey = UUID.randomUUID().toString();
        final Payment authorization = createPayment(TransactionType.AUTHORIZE, null, null, authKey, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
        assertNotNull(authorization);
        Assert.assertEquals(authorization.getTransactions().size(), 1);
        Assert.assertEquals(authorization.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);

        try {
            // Capture with the same transaction external key should fail
            createPayment(TransactionType.CAPTURE, authorization.getId(), null, authKey, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_ACTIVE_TRANSACTION_KEY_EXISTS.getCode());
        }

        try {
            // Different auth with the same payment external key should fail
            createPayment(TransactionType.AUTHORIZE, null, authorization.getExternalKey(), null, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
        }

        try {
            // Different auth with the same payment external key but different transaction external key should fail
            createPayment(TransactionType.AUTHORIZE, null, authorization.getExternalKey(), UUID.randomUUID().toString(), BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_OPERATION.getCode());
        }

        try {
            // Different auth with the same transaction external key should fail
            createPayment(TransactionType.AUTHORIZE, null, null, authKey, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_ACTIVE_TRANSACTION_KEY_EXISTS.getCode());
        }

        final Account account1 = testHelper.createTestAccount("bobo2@gmail.com", true);
        try {
            // Different auth with the same payment external key on a different account should fail
            createPayment(account1, TransactionType.AUTHORIZE, null, authorization.getExternalKey(), UUID.randomUUID().toString(), BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_DIFFERENT_ACCOUNT_ID.getCode());
        }

        try {
            // Different auth with the same payment external key but different transaction external key on a different account should fail
            createPayment(account1, TransactionType.AUTHORIZE, null, authorization.getExternalKey(), UUID.randomUUID().toString(), BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_DIFFERENT_ACCOUNT_ID.getCode());
        }

        try {
            // Different auth with the same transaction external key on a different account should fail
            createPayment(account1, TransactionType.AUTHORIZE, null, null, authKey, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_DIFFERENT_ACCOUNT_ID.getCode());
        }

        // Capture with a different transaction external key should go through
        final String captureKey = UUID.randomUUID().toString();
        final Payment capturedPayment = createPayment(TransactionType.CAPTURE, authorization.getId(), null, captureKey, BigDecimal.ONE, PaymentPluginStatus.PROCESSED);
        Assert.assertEquals(capturedPayment.getTransactions().size(), 2);
        Assert.assertEquals(capturedPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(capturedPayment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);

        try {
            // Second capture with the same transaction external key should fail
            createPayment(TransactionType.CAPTURE, authorization.getId(), null, captureKey, BigDecimal.ONE, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_ACTIVE_TRANSACTION_KEY_EXISTS.getCode());
        }

        try {
            // Different auth with the same transaction external key should fail
            createPayment(TransactionType.AUTHORIZE, null, null, captureKey, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_ACTIVE_TRANSACTION_KEY_EXISTS.getCode());
        }

        try {
            // Different auth with the same transaction external key on a different account should fail
            createPayment(account1, TransactionType.AUTHORIZE, null, null, captureKey, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_DIFFERENT_ACCOUNT_ID.getCode());
        }

        // Second capture with a different transaction external key should go through
        final String captureKey2 = UUID.randomUUID().toString();
        final Payment capturedPayment2 = createPayment(TransactionType.CAPTURE, authorization.getId(), null, captureKey2, BigDecimal.ONE, PaymentPluginStatus.PROCESSED);
        Assert.assertEquals(capturedPayment2.getTransactions().size(), 3);
        Assert.assertEquals(capturedPayment2.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(capturedPayment2.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(capturedPayment2.getTransactions().get(2).getTransactionStatus(), TransactionStatus.SUCCESS);

        final String refundKey = UUID.randomUUID().toString();
        final Payment refundedPayment = createPayment(TransactionType.REFUND, authorization.getId(), null, refundKey, BigDecimal.ONE, PaymentPluginStatus.PROCESSED);
        Assert.assertEquals(refundedPayment.getTransactions().size(), 4);
        Assert.assertEquals(refundedPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(refundedPayment.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(refundedPayment.getTransactions().get(2).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(refundedPayment.getTransactions().get(3).getTransactionStatus(), TransactionStatus.SUCCESS);

        // Second payment

        final String auth2Key = UUID.randomUUID().toString();
        final Payment authorization2 = createPayment(TransactionType.AUTHORIZE, null, null, auth2Key, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
        assertNotNull(authorization2);
        Assert.assertEquals(authorization2.getTransactions().size(), 1);
        Assert.assertEquals(authorization2.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);

        try {
            // Capture with an existing successful transaction external key should fail
            createPayment(TransactionType.CAPTURE, authorization2.getId(), null, captureKey, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_ACTIVE_TRANSACTION_KEY_EXISTS.getCode());
        }

        final String capture2Key = UUID.randomUUID().toString();
        final Payment capture2 = createPayment(TransactionType.CAPTURE, authorization2.getId(), null, capture2Key, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
        Assert.assertEquals(capture2.getTransactions().size(), 2);
        Assert.assertEquals(capture2.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(capture2.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);

        try {
            // Refund with an existing successful transaction external key should fail
            createPayment(TransactionType.REFUND, authorization2.getId(), null, refundKey, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_ACTIVE_TRANSACTION_KEY_EXISTS.getCode());
        }
    }

    @Test(groups = "slow")
    public void testKeysSanityOnFailure() throws Exception {
        final String authKey = UUID.randomUUID().toString();
        final Payment failedAuthorization1 = createPayment(TransactionType.AUTHORIZE, null, null, authKey, BigDecimal.TEN, PaymentPluginStatus.ERROR);
        assertNotNull(failedAuthorization1);
        Assert.assertEquals(failedAuthorization1.getTransactions().size(), 1);
        Assert.assertEquals(failedAuthorization1.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);

        final Account account1 = testHelper.createTestAccount("bobo2@gmail.com", true);
        try {
            // Different auth with the same payment external key on a different account should fail
            createPayment(account1, TransactionType.AUTHORIZE, null, failedAuthorization1.getExternalKey(), null, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_DIFFERENT_ACCOUNT_ID.getCode());
        }

        try {
            // Different auth with the same transaction external key on a different account should fail
            createPayment(account1, TransactionType.AUTHORIZE, null, null, authKey, BigDecimal.TEN, PaymentPluginStatus.PROCESSED);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_DIFFERENT_ACCOUNT_ID.getCode());
        }

        // Different auth with the same payment external key should go through
        final Payment failedAuthorization2 = createPayment(TransactionType.AUTHORIZE, null, failedAuthorization1.getExternalKey(), null, BigDecimal.TEN, PaymentPluginStatus.ERROR);
        assertNotNull(failedAuthorization2);
        Assert.assertEquals(failedAuthorization2.getTransactions().size(), 2);
        Assert.assertEquals(failedAuthorization2.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        Assert.assertEquals(failedAuthorization2.getTransactions().get(0).getExternalKey(), authKey);
        Assert.assertEquals(failedAuthorization2.getTransactions().get(1).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        Assert.assertNotEquals(failedAuthorization2.getTransactions().get(1).getExternalKey(), authKey);

        // Different auth with the same transaction external key should go through
        final Payment failedAuthorization3 = createPayment(TransactionType.AUTHORIZE, null, null, authKey, BigDecimal.TEN, PaymentPluginStatus.ERROR);
        assertNotNull(failedAuthorization3);
        Assert.assertEquals(failedAuthorization3.getTransactions().size(), 3);
        Assert.assertEquals(failedAuthorization3.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        Assert.assertEquals(failedAuthorization3.getTransactions().get(0).getExternalKey(), authKey);
        Assert.assertEquals(failedAuthorization3.getTransactions().get(1).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        Assert.assertNotEquals(failedAuthorization3.getTransactions().get(1).getExternalKey(), authKey);
        Assert.assertEquals(failedAuthorization3.getTransactions().get(2).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        Assert.assertEquals(failedAuthorization3.getTransactions().get(2).getExternalKey(), authKey);

        // Different auth with the same payment external key but different transaction external key should go through
        final Payment failedAuthorization4 = createPayment(TransactionType.AUTHORIZE, null, failedAuthorization1.getExternalKey(), UUID.randomUUID().toString(), BigDecimal.TEN, PaymentPluginStatus.ERROR);
        assertNotNull(failedAuthorization4);
        Assert.assertEquals(failedAuthorization4.getTransactions().size(), 4);
        Assert.assertEquals(failedAuthorization4.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        Assert.assertEquals(failedAuthorization4.getTransactions().get(0).getExternalKey(), authKey);
        Assert.assertEquals(failedAuthorization4.getTransactions().get(1).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        Assert.assertNotEquals(failedAuthorization4.getTransactions().get(1).getExternalKey(), authKey);
        Assert.assertEquals(failedAuthorization4.getTransactions().get(2).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        Assert.assertEquals(failedAuthorization4.getTransactions().get(2).getExternalKey(), authKey);
        Assert.assertEquals(failedAuthorization4.getTransactions().get(3).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        Assert.assertNotEquals(failedAuthorization4.getTransactions().get(3).getExternalKey(), authKey);
    }

    private void verifyRefund(final Payment refund, final String paymentExternalKey, final String paymentTransactionExternalKey, final String refundTransactionExternalKey, final BigDecimal requestedAmount, final BigDecimal refundAmount, final TransactionStatus transactionStatus) {
        Assert.assertEquals(refund.getExternalKey(), paymentExternalKey);
        Assert.assertEquals(refund.getTransactions().size(), 2);
        Assert.assertEquals(refund.getTransactions().get(0).getAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(refund.getTransactions().get(0).getProcessedAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(refund.getTransactions().get(0).getCurrency(), account.getCurrency());
        Assert.assertEquals(refund.getTransactions().get(0).getExternalKey(), paymentTransactionExternalKey);
        Assert.assertEquals(refund.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(refund.getTransactions().get(1).getAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(refund.getTransactions().get(1).getProcessedAmount().compareTo(refundAmount), 0);
        Assert.assertEquals(refund.getTransactions().get(1).getCurrency(), account.getCurrency());
        Assert.assertEquals(refund.getTransactions().get(1).getExternalKey(), refundTransactionExternalKey);
        Assert.assertEquals(refund.getTransactions().get(1).getTransactionStatus(), transactionStatus);
    }

    private Payment testApiWithPendingPaymentTransaction(final TransactionType transactionType, final BigDecimal requestedAmount, @Nullable final BigDecimal pendingAmount) throws PaymentApiException {
        final String paymentExternalKey = UUID.randomUUID().toString();
        final String paymentTransactionExternalKey = UUID.randomUUID().toString();

        final Payment pendingPayment = createPayment(transactionType, null, paymentExternalKey, paymentTransactionExternalKey, requestedAmount, PaymentPluginStatus.PENDING);
        assertNotNull(pendingPayment);
        Assert.assertEquals(pendingPayment.getExternalKey(), paymentExternalKey);
        Assert.assertEquals(pendingPayment.getTransactions().size(), 1);
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getProcessedAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getCurrency(), account.getCurrency());
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getExternalKey(), paymentTransactionExternalKey);
        Assert.assertEquals(pendingPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PENDING);

        // Test Janitor path (regression test for https://github.com/killbill/killbill/issues/363)
        verifyPaymentViaGetPath(pendingPayment);

        final Payment pendingPayment2 = createPayment(transactionType, pendingPayment.getId(), paymentExternalKey, paymentTransactionExternalKey, pendingAmount, PaymentPluginStatus.PENDING);
        assertNotNull(pendingPayment2);
        Assert.assertEquals(pendingPayment2.getExternalKey(), paymentExternalKey);
        Assert.assertEquals(pendingPayment2.getTransactions().size(), 1);
        Assert.assertEquals(pendingPayment2.getTransactions().get(0).getAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(pendingPayment2.getTransactions().get(0).getProcessedAmount().compareTo(pendingAmount == null ? requestedAmount : pendingAmount), 0);
        Assert.assertEquals(pendingPayment2.getTransactions().get(0).getCurrency(), account.getCurrency());
        Assert.assertEquals(pendingPayment2.getTransactions().get(0).getExternalKey(), paymentTransactionExternalKey);
        Assert.assertEquals(pendingPayment2.getTransactions().get(0).getTransactionStatus(), TransactionStatus.PENDING);

        verifyPaymentViaGetPath(pendingPayment2);

        final Payment completedPayment = createPayment(transactionType, pendingPayment.getId(), paymentExternalKey, paymentTransactionExternalKey, pendingAmount, PaymentPluginStatus.PROCESSED);
        assertNotNull(completedPayment);
        Assert.assertEquals(completedPayment.getExternalKey(), paymentExternalKey);
        Assert.assertEquals(completedPayment.getTransactions().size(), 1);
        Assert.assertEquals(completedPayment.getTransactions().get(0).getAmount().compareTo(requestedAmount), 0);
        Assert.assertEquals(completedPayment.getTransactions().get(0).getProcessedAmount().compareTo(pendingAmount == null ? requestedAmount : pendingAmount), 0);
        Assert.assertEquals(completedPayment.getTransactions().get(0).getCurrency(), account.getCurrency());
        Assert.assertEquals(completedPayment.getTransactions().get(0).getExternalKey(), paymentTransactionExternalKey);
        Assert.assertEquals(completedPayment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);

        verifyPaymentViaGetPath(completedPayment);

        return completedPayment;
    }

    private void verifyPaymentViaGetPath(final Payment payment) throws PaymentApiException {
        // We can't use Assert.assertEquals because the updateDate may have been updated by the Janitor
        final Payment refreshedPayment = paymentApi.getPayment(payment.getId(), true, false, ImmutableList.<PluginProperty>of(), callContext);

        Assert.assertEquals(refreshedPayment.getAccountId(), payment.getAccountId());

        Assert.assertEquals(refreshedPayment.getTransactions().size(), payment.getTransactions().size());
        Assert.assertEquals(refreshedPayment.getExternalKey(), payment.getExternalKey());
        Assert.assertEquals(refreshedPayment.getPaymentMethodId(), payment.getPaymentMethodId());
        Assert.assertEquals(refreshedPayment.getAccountId(), payment.getAccountId());
        Assert.assertEquals(refreshedPayment.getAuthAmount().compareTo(payment.getAuthAmount()), 0);
        Assert.assertEquals(refreshedPayment.getCapturedAmount().compareTo(payment.getCapturedAmount()), 0);
        Assert.assertEquals(refreshedPayment.getPurchasedAmount().compareTo(payment.getPurchasedAmount()), 0);
        Assert.assertEquals(refreshedPayment.getRefundedAmount().compareTo(payment.getRefundedAmount()), 0);
        Assert.assertEquals(refreshedPayment.getCurrency(), payment.getCurrency());

        for (int i = 0; i < refreshedPayment.getTransactions().size(); i++) {
            final PaymentTransaction refreshedPaymentTransaction = refreshedPayment.getTransactions().get(i);
            final PaymentTransaction paymentTransaction = payment.getTransactions().get(i);
            Assert.assertEquals(refreshedPaymentTransaction.getAmount().compareTo(paymentTransaction.getAmount()), 0);
            Assert.assertEquals(refreshedPaymentTransaction.getProcessedAmount().compareTo(paymentTransaction.getProcessedAmount()), 0);
            Assert.assertEquals(refreshedPaymentTransaction.getCurrency(), paymentTransaction.getCurrency());
            Assert.assertEquals(refreshedPaymentTransaction.getExternalKey(), paymentTransaction.getExternalKey());
            Assert.assertEquals(refreshedPaymentTransaction.getTransactionStatus(), paymentTransaction.getTransactionStatus());
        }
    }

    private Payment createPayment(final TransactionType transactionType,
                                  @Nullable final UUID paymentId,
                                  @Nullable final String paymentExternalKey,
                                  @Nullable final String paymentTransactionExternalKey,
                                  @Nullable final BigDecimal amount,
                                  final PaymentPluginStatus paymentPluginStatus) throws PaymentApiException {
        return createPayment(account, transactionType, paymentId, paymentExternalKey, paymentTransactionExternalKey, amount, paymentPluginStatus);
    }

    private Payment createPayment(final Account account,
                                  final TransactionType transactionType,
                                  @Nullable final UUID paymentId,
                                  @Nullable final String paymentExternalKey,
                                  @Nullable final String paymentTransactionExternalKey,
                                  @Nullable final BigDecimal amount,
                                  final PaymentPluginStatus paymentPluginStatus) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, paymentPluginStatus.toString(), false));
        switch (transactionType) {
            case AUTHORIZE:
                return paymentApi.createAuthorization(account,
                                                      account.getPaymentMethodId(),
                                                      paymentId,
                                                      amount,
                                                      amount == null ? null : account.getCurrency(),
                                                      null,
                                                      paymentExternalKey,
                                                      paymentTransactionExternalKey,
                                                      pluginProperties,
                                                      callContext);
            case PURCHASE:
                return paymentApi.createPurchase(account,
                                                 account.getPaymentMethodId(),
                                                 paymentId,
                                                 amount,
                                                 amount == null ? null : account.getCurrency(),
                                                 null,
                                                 paymentExternalKey,
                                                 paymentTransactionExternalKey,
                                                 pluginProperties,
                                                 callContext);
            case CREDIT:
                return paymentApi.createCredit(account,
                                               account.getPaymentMethodId(),
                                               paymentId,
                                               amount,
                                               amount == null ? null : account.getCurrency(),
                                               null,
                                               paymentExternalKey,
                                               paymentTransactionExternalKey,
                                               pluginProperties,
                                               callContext);
            case CAPTURE:
                return paymentApi.createCapture(account,
                                                paymentId,
                                                amount,
                                                amount == null ? null : account.getCurrency(),
                                                null,
                                                paymentTransactionExternalKey,
                                                pluginProperties,
                                                callContext);
            case REFUND:
                return paymentApi.createRefund(account,
                                               paymentId,
                                               amount,
                                               amount == null ? null : account.getCurrency(),
                                               null,
                                               paymentTransactionExternalKey,
                                               pluginProperties,
                                               callContext);
            default:
                Assert.fail();
                return null;
        }
    }

    private List<PluginProperty> createPropertiesForInvoice(final Invoice invoice) {
        final List<PluginProperty> result = new ArrayList<PluginProperty>();
        result.add(new PluginProperty(InvoicePaymentControlPluginApi.PROP_IPCD_INVOICE_ID, invoice.getId().toString(), false));
        return result;
    }

    // Search by a key supported by the search in MockPaymentProviderPlugin
    private void checkPaymentMethodPagination(final UUID paymentMethodId, final Long maxNbRecords, final boolean deleted) throws PaymentApiException {
        final Pagination<PaymentMethod> foundPaymentMethods = paymentApi.searchPaymentMethods(paymentMethodId.toString(), 0L, maxNbRecords + 1, false, ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(foundPaymentMethods.iterator().hasNext(), !deleted);
        Assert.assertEquals(foundPaymentMethods.getMaxNbRecords(), maxNbRecords);
        Assert.assertEquals(foundPaymentMethods.getTotalNbRecords(), (Long) (!deleted ? 1L : 0L));

        final Pagination<PaymentMethod> foundPaymentMethodsWithPluginInfo = paymentApi.searchPaymentMethods(paymentMethodId.toString(), 0L, maxNbRecords + 1, true, ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(foundPaymentMethodsWithPluginInfo.iterator().hasNext(), !deleted);
        Assert.assertEquals(foundPaymentMethodsWithPluginInfo.getMaxNbRecords(), maxNbRecords);
        Assert.assertEquals(foundPaymentMethodsWithPluginInfo.getTotalNbRecords(), (Long) (!deleted ? 1L : 0L));

        final Pagination<PaymentMethod> foundPaymentMethods2 = paymentApi.searchPaymentMethods(paymentMethodId.toString(), 0L, maxNbRecords + 1, MockPaymentProviderPlugin.PLUGIN_NAME, false, ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(foundPaymentMethods2.iterator().hasNext(), !deleted);
        Assert.assertEquals(foundPaymentMethods2.getMaxNbRecords(), maxNbRecords);
        Assert.assertEquals(foundPaymentMethods2.getTotalNbRecords(), (Long) (!deleted ? 1L : 0L));

        final Pagination<PaymentMethod> foundPaymentMethods2WithPluginInfo = paymentApi.searchPaymentMethods(paymentMethodId.toString(), 0L, maxNbRecords + 1, MockPaymentProviderPlugin.PLUGIN_NAME, true, ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(foundPaymentMethods2WithPluginInfo.iterator().hasNext(), !deleted);
        Assert.assertEquals(foundPaymentMethods2WithPluginInfo.getMaxNbRecords(), maxNbRecords);
        Assert.assertEquals(foundPaymentMethods2WithPluginInfo.getTotalNbRecords(), (Long) (!deleted ? 1L : 0L));

        final Pagination<PaymentMethod> gotPaymentMethods = paymentApi.getPaymentMethods(0L, maxNbRecords + 1L, false, ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(gotPaymentMethods.iterator().hasNext(), maxNbRecords > 0);
        Assert.assertEquals(gotPaymentMethods.getMaxNbRecords(), maxNbRecords);
        Assert.assertEquals(gotPaymentMethods.getTotalNbRecords(), maxNbRecords);

        final Pagination<PaymentMethod> gotPaymentMethodsWithPluginInfo = paymentApi.getPaymentMethods(0L, maxNbRecords + 1L, true, ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(gotPaymentMethodsWithPluginInfo.iterator().hasNext(), maxNbRecords > 0);
        Assert.assertEquals(gotPaymentMethodsWithPluginInfo.getMaxNbRecords(), maxNbRecords);
        Assert.assertEquals(gotPaymentMethodsWithPluginInfo.getTotalNbRecords(), maxNbRecords);

        final Pagination<PaymentMethod> gotPaymentMethods2 = paymentApi.getPaymentMethods(0L, maxNbRecords + 1L, MockPaymentProviderPlugin.PLUGIN_NAME, false, ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(gotPaymentMethods2.iterator().hasNext(), maxNbRecords > 0);
        Assert.assertEquals(gotPaymentMethods2.getMaxNbRecords(), maxNbRecords);
        Assert.assertEquals(gotPaymentMethods2.getTotalNbRecords(), maxNbRecords);

        final Pagination<PaymentMethod> gotPaymentMethods2WithPluginInfo = paymentApi.getPaymentMethods(0L, maxNbRecords + 1L, MockPaymentProviderPlugin.PLUGIN_NAME, true, ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(gotPaymentMethods2WithPluginInfo.iterator().hasNext(), maxNbRecords > 0);
        Assert.assertEquals(gotPaymentMethods2WithPluginInfo.getMaxNbRecords(), maxNbRecords);
        Assert.assertEquals(gotPaymentMethods2WithPluginInfo.getTotalNbRecords(), maxNbRecords);
    }
}
