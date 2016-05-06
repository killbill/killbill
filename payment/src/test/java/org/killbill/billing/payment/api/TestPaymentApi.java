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

import org.joda.time.LocalDate;
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
        super.beforeClass();
        mockPaymentProviderPlugin = (MockPaymentProviderPlugin) registry.getServiceForName(MockPaymentProviderPlugin.PLUGIN_NAME);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
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

        paymentApi.deletePaymentMethod(account, paymentMethodId, true, ImmutableList.<PluginProperty>of(), callContext);

        checkPaymentMethodPagination(paymentMethodId, baseNbRecords, true);
    }

    @Test(groups = "slow")
    public void testCreateSuccessPurchase() throws PaymentApiException {

        final BigDecimal requestedAmount = BigDecimal.TEN;

        final String paymentExternalKey = "bwwrr";
        final String transactionExternalKey = "krapaut";

        final Payment payment = paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, paymentExternalKey, transactionExternalKey,
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

        final Payment payment = paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, paymentExternalKey, transactionExternalKey,
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

        final Payment payment2 = paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, paymentExternalKey, transactionExternalKey,
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

        final Payment payment = paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, paymentExternalKey, transactionExternalKey,
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

        final Payment payment2 = paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, paymentExternalKey, transactionExternalKey,
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
            paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED,
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
                    account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED,
                    paymentExternalKey, transactionExternalKey, ImmutableList.<PluginProperty>of(), CONTROL_PLUGIN_OPTIONS, callContext);
            fail();
        } catch (PaymentApiException e) {
            assertTrue(e.getCause() instanceof PaymentPluginApiException);
        }
    }

    @Test(groups = "slow")
    public void testCreatePurchaseWithControlPluginException() throws Exception {
        mockPaymentControlProviderPlugin.throwsException(new IllegalStateException());

        final BigDecimal requestedAmount = BigDecimal.TEN;
        final String paymentExternalKey = "pay controle external key";;
        final String transactionExternalKey = "txn control external key";
        try {
            paymentApi.createPurchaseWithPaymentControl(
                    account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED,
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

        final Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, authAmount, Currency.AED,
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
        final Payment payment2 = paymentApi.createVoid(account, payment.getId(), transactionExternalKey2, ImmutableList.<PluginProperty>of(), callContext);

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
    }

    @Test(groups = "slow")
    public void testCreateSuccessAuthCaptureVoidCapture() throws PaymentApiException {
        final BigDecimal authAmount = BigDecimal.TEN;
        final BigDecimal captureAmount = BigDecimal.ONE;

        final String paymentExternalKey = UUID.randomUUID().toString();
        final String transactionExternalKey = UUID.randomUUID().toString();
        final String transactionExternalKey2 = UUID.randomUUID().toString();
        final String transactionExternalKey3 = UUID.randomUUID().toString();
        final String transactionExternalKey4 = UUID.randomUUID().toString();

        final Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, authAmount, Currency.AED,
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

        final Payment payment2 = paymentApi.createCapture(account, payment.getId(), captureAmount, Currency.AED, transactionExternalKey2,
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
        final Payment payment3 = paymentApi.createVoid(account, payment.getId(), transactionExternalKey3, ImmutableList.<PluginProperty>of(), callContext);

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
        final Payment payment4 = paymentApi.createCapture(account, payment.getId(), captureAmount, Currency.AED, transactionExternalKey4,
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
    public void testCreateSuccessAuthCaptureVoidVoid() throws PaymentApiException {
        final BigDecimal authAmount = BigDecimal.TEN;
        final BigDecimal captureAmount = BigDecimal.ONE;

        final String paymentExternalKey = UUID.randomUUID().toString();
        final String transactionExternalKey = UUID.randomUUID().toString();
        final String transactionExternalKey2 = UUID.randomUUID().toString();
        final String transactionExternalKey3 = UUID.randomUUID().toString();
        final String transactionExternalKey4 = UUID.randomUUID().toString();

        final Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, authAmount, Currency.AED,
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

        final Payment payment2 = paymentApi.createCapture(account, payment.getId(), captureAmount, Currency.AED, transactionExternalKey2,
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
        final Payment payment3 = paymentApi.createVoid(account, payment.getId(), transactionExternalKey3, ImmutableList.<PluginProperty>of(), callContext);

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
        final Payment payment4 = paymentApi.createVoid(account, payment.getId(), transactionExternalKey4, ImmutableList.<PluginProperty>of(), callContext);

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

        final Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, authAmount, Currency.USD, paymentExternalKey, transactionExternalKey,
                                                               ImmutableList.<PluginProperty>of(), callContext);

        paymentApi.createCapture(account, payment.getId(), captureAmount, Currency.USD, transactionExternalKey2,
                                 ImmutableList.<PluginProperty>of(), callContext);

        final Payment payment3 = paymentApi.createCapture(account, payment.getId(), captureAmount, Currency.USD, transactionExternalKey3,
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

        final Payment payment4 = paymentApi.createRefund(account, payment3.getId(), payment3.getCapturedAmount(), Currency.USD, transactionExternalKey4, ImmutableList.<PluginProperty>of(), callContext);
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
        final Payment payment = paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, paymentExternalKey, transactionExternalKey,
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
            paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, paymentExternalKey, transactionExternalKey,
                                                        createPropertiesForInvoice(invoice), INVOICE_PAYMENT, callContext);
        } catch (final PaymentApiException expected) {
            assertTrue(true);
        }


        final List<Payment> accountPayments = paymentApi.getAccountPayments(account.getId(), false, ImmutableList.<PluginProperty>of(), callContext);
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
            paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, paymentExternalKey, transactionExternalKey,
                                                        createPropertiesForInvoice(invoice), INVOICE_PAYMENT, callContext);
        } catch (final PaymentApiException expected) {
            assertTrue(true);
        }


        final List<Payment> accountPayments = paymentApi.getAccountPayments(account.getId(), false, ImmutableList.<PluginProperty>of(), callContext);
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
        paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, paymentExternalKey, transactionExternalKey,
                                                    createPropertiesForInvoice(invoice), INVOICE_PAYMENT, callContext);


        final List<Payment> accountPayments2 = paymentApi.getAccountPayments(account.getId(), false, ImmutableList.<PluginProperty>of(), callContext);
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
            paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, paymentExternalKey, transactionExternalKey,
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

        final Payment payment = paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, paymentExternalKey, transactionExternalKey,
                                                                            createPropertiesForInvoice(invoice), INVOICE_PAYMENT, callContext);

        final List<PluginProperty> refundProperties = ImmutableList.<PluginProperty>of();
        final Payment payment2 = paymentApi.createRefundWithPaymentControl(account, payment.getId(), requestedAmount, Currency.USD, transactionExternalKey2,
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

        final Payment payment = paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, paymentExternalKey, transactionExternalKey,
                                                                            createPropertiesForInvoice(invoice), INVOICE_PAYMENT, callContext);

        final List<PluginProperty> refundProperties = ImmutableList.<PluginProperty>of();

        try {
            paymentApi.createRefundWithPaymentControl(account, payment.getId(), BigDecimal.TEN, Currency.USD, transactionExternalKey2,
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

        final Payment payment = paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, Currency.USD, paymentExternalKey, transactionExternalKey,
                                                                            createPropertiesForInvoice(invoice), INVOICE_PAYMENT, callContext);

        final List<PluginProperty> refundProperties = new ArrayList<PluginProperty>();
        final HashMap<UUID, BigDecimal> uuidBigDecimalHashMap = new HashMap<UUID, BigDecimal>();
        uuidBigDecimalHashMap.put(invoiceItem.getId(), null);
        final PluginProperty refundIdsProp = new PluginProperty(InvoicePaymentControlPluginApi.PROP_IPCD_REFUND_IDS_WITH_AMOUNT_KEY, uuidBigDecimalHashMap, false);
        refundProperties.add(refundIdsProp);

        final Payment payment2 = paymentApi.createRefundWithPaymentControl(account, payment.getId(), null, Currency.USD, transactionExternalKey2,
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
    public void testCreateChargeback() throws PaymentApiException {
        final BigDecimal requestedAmount = BigDecimal.TEN;

        final String paymentExternalKey = "couic";
        final String transactionExternalKey = "couac";
        final String transactionExternalKey2 = "couyc";

        final Payment payment = paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, paymentExternalKey, transactionExternalKey,
                                                          ImmutableList.<PluginProperty>of(), callContext);

        paymentApi.createChargeback(account, payment.getId(), requestedAmount, Currency.AED, transactionExternalKey2, callContext);
        final Payment payment2 = paymentApi.getPayment(payment.getId(), false, ImmutableList.<PluginProperty>of(), callContext);

        assertEquals(payment2.getExternalKey(), paymentExternalKey);
        assertEquals(payment2.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment2.getAccountId(), account.getId());
        assertEquals(payment2.getAuthAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getPurchasedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment2.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment2.getCurrency(), Currency.AED);

        assertEquals(payment2.getTransactions().size(), 2);
        assertEquals(payment2.getTransactions().get(1).getExternalKey(), transactionExternalKey2);
        assertEquals(payment2.getTransactions().get(1).getPaymentId(), payment.getId());
        assertEquals(payment2.getTransactions().get(1).getAmount().compareTo(requestedAmount), 0);
        assertEquals(payment2.getTransactions().get(1).getCurrency(), Currency.AED);

        assertEquals(payment2.getTransactions().get(1).getProcessedAmount().compareTo(requestedAmount), 0);
        assertEquals(payment2.getTransactions().get(1).getProcessedCurrency(), Currency.AED);

        assertEquals(payment2.getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment2.getTransactions().get(1).getTransactionType(), TransactionType.CHARGEBACK);
        assertNull(payment2.getTransactions().get(1).getGatewayErrorMsg());
        assertNull(payment2.getTransactions().get(1).getGatewayErrorCode());

        // Attempt to any other operation afterwards, that should fail
        try {
            paymentApi.createPurchase(account, account.getPaymentMethodId(), payment.getId(), requestedAmount, Currency.AED, paymentExternalKey, transactionExternalKey,
                                      ImmutableList.<PluginProperty>of(), callContext);
            Assert.fail("Purchase not succeed after a chargeback");
        } catch (final PaymentApiException e) {
            Assert.assertTrue(true);
        }
    }

    @Test(groups = "slow")
    public void testNotifyPendingTransactionOfStateChanged() throws PaymentApiException {

        final BigDecimal authAmount = BigDecimal.TEN;

        final String paymentExternalKey = "rouge";
        final String transactionExternalKey = "vert";

        final Payment initialPayment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, authAmount, Currency.AED, paymentExternalKey, transactionExternalKey,
                                                                      ImmutableList.<PluginProperty>of(), callContext);

        // Update the payment/transaction by hand to simulate a PENDING state.
        final PaymentTransaction paymentTransaction = initialPayment.getTransactions().get(0);
        paymentDao.updatePaymentAndTransactionOnCompletion(account.getId(), initialPayment.getId(), TransactionType.AUTHORIZE, "AUTH_PENDING", "AUTH_PENDING",
                                                           paymentTransaction.getId(), TransactionStatus.PENDING, paymentTransaction.getProcessedAmount(), paymentTransaction.getProcessedCurrency(),
                                                           null, null, internalCallContext);

        final Payment payment = paymentApi.notifyPendingTransactionOfStateChanged(account, paymentTransaction.getId(), true, callContext);

        assertEquals(payment.getExternalKey(), paymentExternalKey);
        assertEquals(payment.getPaymentMethodId(), account.getPaymentMethodId());
        assertEquals(payment.getAccountId(), account.getId());
        assertEquals(payment.getAuthAmount().compareTo(authAmount), 0);
        assertEquals(payment.getCapturedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payment.getCurrency(), Currency.AED);

        assertEquals(payment.getTransactions().size(), 1);
        assertEquals(payment.getTransactions().get(0).getExternalKey(), transactionExternalKey);
        assertEquals(payment.getTransactions().get(0).getPaymentId(), payment.getId());
        assertEquals(payment.getTransactions().get(0).getAmount().compareTo(authAmount), 0);
        assertEquals(payment.getTransactions().get(0).getCurrency(), Currency.AED);
        assertEquals(payment.getTransactions().get(0).getProcessedAmount().compareTo(authAmount), 0);
        assertEquals(payment.getTransactions().get(0).getProcessedCurrency(), Currency.AED);

        assertEquals(payment.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payment.getTransactions().get(0).getTransactionType(), TransactionType.AUTHORIZE);
        assertNull(payment.getTransactions().get(0).getGatewayErrorMsg());
        assertNull(payment.getTransactions().get(0).getGatewayErrorCode());
    }

    @Test(groups = "slow")
    public void testSimpleAuthCaptureWithInvalidPaymentId() throws Exception {
        final BigDecimal requestedAmount = new BigDecimal("80.0091");

        final Payment initialPayment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, account.getCurrency(),
                                                                      UUID.randomUUID().toString(), UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);
        try {
            paymentApi.createCapture(account, UUID.randomUUID(), requestedAmount, account.getCurrency(), UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);
            Assert.fail("Expected capture to fail...");
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_NO_SUCH_PAYMENT.getCode());

            final Payment latestPayment = paymentApi.getPayment(initialPayment.getId(), true, ImmutableList.<PluginProperty>of(), callContext);
            assertEquals(latestPayment, initialPayment);
        }
    }

    @Test(groups = "slow")
    public void testSimpleAuthCaptureWithInvalidCurrency() throws Exception {
        final BigDecimal requestedAmount = new BigDecimal("80.0091");

        final Payment initialPayment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, account.getCurrency(),
                                                                      UUID.randomUUID().toString(), UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);

        try {
            paymentApi.createCapture(account, initialPayment.getId(), requestedAmount, Currency.AMD, UUID.randomUUID().toString(), ImmutableList.<PluginProperty>of(), callContext);
            Assert.fail("Expected capture to fail...");
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_PARAMETER.getCode());

            final Payment latestPayment = paymentApi.getPayment(initialPayment.getId(), true, ImmutableList.<PluginProperty>of(), callContext);
            assertEquals(latestPayment, initialPayment);
        }
    }

    @Test(groups = "slow")
    public void testInvalidTransitionAfterFailure() throws PaymentApiException {

        final BigDecimal requestedAmount = BigDecimal.TEN;

        final String paymentExternalKey = "krapo";
        final String transactionExternalKey = "grenouye";

        final Payment payment = paymentApi.createAuthorization(account, account.getPaymentMethodId(), null, requestedAmount, Currency.EUR, paymentExternalKey, transactionExternalKey,
                                                               ImmutableList.<PluginProperty>of(), callContext);

        // Hack the Database to make it look like it was a failure
        paymentDao.updatePaymentAndTransactionOnCompletion(account.getId(), payment.getId(), TransactionType.AUTHORIZE, "AUTH_ERRORED", null,
                                                           payment.getTransactions().get(0).getId(), TransactionStatus.PLUGIN_FAILURE, null, null, null, null, internalCallContext);
        final PaymentSqlDao paymentSqlDao = dbi.onDemand(PaymentSqlDao.class);
        paymentSqlDao.updateLastSuccessPaymentStateName(payment.getId().toString(), "AUTH_ERRORED", null, internalCallContext);

        try {
            paymentApi.createCapture(account, payment.getId(), requestedAmount, Currency.EUR, "tetard", ImmutableList.<PluginProperty>of(), callContext);
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
                                                               refundTransactionExternalKey,
                                                               ImmutableList.<PluginProperty>of(),
                                                               callContext);
        verifyRefund(pendingRefund4, paymentExternalKey, paymentTransactionExternalKey, refundTransactionExternalKey, requestedAmount, requestedAmount, TransactionStatus.SUCCESS);

        verifyPaymentViaGetPath(pendingRefund4);
    }

    @Test(groups = "slow")
    public void testCreatePurchaseWithTimeout() throws Exception {
        final BigDecimal requestedAmount = BigDecimal.TEN;
        final String paymentExternalKey = "ohhhh";
        final String transactionExternalKey = "naaahhh";

        mockPaymentProviderPlugin.makePluginWaitSomeMilliseconds((int) (paymentConfig.getPaymentPluginTimeout().getMillis() + 100));
        try {
            paymentApi.createPurchase(account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED,
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
                    account, account.getPaymentMethodId(), null, requestedAmount, Currency.AED, paymentExternalKey,
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
            }

            // Attempt to create another {AUTH, PURCHASE, CREDIT} with same key => key constraint should make the request fail
            try {
                createPayment(transactionType, processedPayment.getId(), paymentExternalKey, keyA, requestedAmount, PaymentPluginStatus.PROCESSED);
                Assert.fail("Retrying initial successful transaction (AUTHORIZE, PURCHASE, CREDIT) with same transaction key should fail");
            } catch (final PaymentApiException e) {
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
        final Payment refreshedPayment = paymentApi.getPayment(payment.getId(), true, ImmutableList.<PluginProperty>of(), callContext);

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
        final Iterable<PluginProperty> pluginProperties = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, paymentPluginStatus.toString(), false));
        switch (transactionType) {
            case AUTHORIZE:
                return paymentApi.createAuthorization(account,
                                                      account.getPaymentMethodId(),
                                                      paymentId,
                                                      amount,
                                                      amount == null ? null : account.getCurrency(),
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
                                               paymentExternalKey,
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
