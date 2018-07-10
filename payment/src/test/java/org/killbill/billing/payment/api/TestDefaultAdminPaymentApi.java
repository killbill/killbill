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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.payment.provider.DefaultNoOpPaymentInfoPlugin;
import org.killbill.billing.payment.provider.MockPaymentControlProviderPlugin;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestDefaultAdminPaymentApi extends PaymentTestSuiteWithEmbeddedDB {

    private MockPaymentProviderPlugin mockPaymentProviderPlugin;
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

        final PaymentControlPluginApi mockPaymentControlProviderPlugin = new MockPaymentControlProviderPlugin();
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
                                              },
                                              mockPaymentControlProviderPlugin);
    }

    @Test(groups = "slow")
    public void testFixPaymentTransactionState() throws PaymentApiException {
        final PaymentOptions paymentOptions = new PaymentOptions() {
            @Override
            public boolean isExternalPayment() {
                return false;
            }

            @Override
            public List<String> getPaymentControlPluginNames() {
                return ImmutableList.<String>of(MockPaymentControlProviderPlugin.PLUGIN_NAME);
            }
        };
        final Payment payment = paymentApi.createAuthorizationWithPaymentControl(account,
                                                                                 account.getPaymentMethodId(),
                                                                                 null,
                                                                                 BigDecimal.TEN,
                                                                                 Currency.EUR,
                                                                                 null,
                                                                                 UUID.randomUUID().toString(),
                                                                                 UUID.randomUUID().toString(),
                                                                                 ImmutableList.<PluginProperty>of(),
                                                                                 paymentOptions,
                                                                                 callContext);
        Assert.assertEquals(payment.getTransactions().size(), 1);

        final PaymentModelDao paymentModelDao = paymentDao.getPayment(payment.getId(), internalCallContext);
        final PaymentTransactionModelDao paymentTransactionModelDao = paymentDao.getPaymentTransaction(payment.getTransactions().get(0).getId(), internalCallContext);
        Assert.assertEquals(paymentModelDao.getStateName(), "AUTH_SUCCESS");
        Assert.assertEquals(paymentModelDao.getLastSuccessStateName(), "AUTH_SUCCESS");
        Assert.assertEquals(paymentTransactionModelDao.getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(paymentTransactionModelDao.getProcessedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(paymentTransactionModelDao.getProcessedCurrency(), Currency.EUR);
        Assert.assertEquals(paymentTransactionModelDao.getGatewayErrorCode(), "");
        Assert.assertEquals(paymentTransactionModelDao.getGatewayErrorMsg(), "");

        adminPaymentApi.fixPaymentTransactionState(payment, payment.getTransactions().get(0), TransactionStatus.PAYMENT_FAILURE, null, "AUTH_ERRORED", ImmutableList.<PluginProperty>of(), callContext);

        Assert.assertEquals(paymentApi.getPayment(payment.getId(), false, false, ImmutableList.<PluginProperty>of(), callContext).getTransactions().size(), 1);
        final PaymentModelDao refreshedPaymentModelDao = paymentDao.getPayment(payment.getId(), internalCallContext);
        final PaymentTransactionModelDao refreshedPaymentTransactionModelDao = paymentDao.getPaymentTransaction(payment.getTransactions().get(0).getId(), internalCallContext);
        Assert.assertEquals(refreshedPaymentModelDao.getStateName(), "AUTH_ERRORED");
        Assert.assertNull(refreshedPaymentModelDao.getLastSuccessStateName());
        Assert.assertEquals(refreshedPaymentTransactionModelDao.getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        Assert.assertEquals(refreshedPaymentTransactionModelDao.getProcessedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(refreshedPaymentTransactionModelDao.getProcessedCurrency(), Currency.EUR);
        Assert.assertEquals(refreshedPaymentTransactionModelDao.getGatewayErrorCode(), "");
        Assert.assertEquals(refreshedPaymentTransactionModelDao.getGatewayErrorMsg(), "");

        // Advance the clock to make sure the effective date of the new transaction is after the first one
        clock.addDays(1);
        assertListenerStatus();

        // Verify subsequent payment retries work
        retryService.retryPaymentTransaction(refreshedPaymentTransactionModelDao.getAttemptId(), ImmutableList.<String>of(MockPaymentControlProviderPlugin.PLUGIN_NAME), internalCallContext);

        final Payment retriedPayment = paymentApi.getPayment(payment.getId(), false, false, ImmutableList.<PluginProperty>of(), callContext);
        Assert.assertEquals(retriedPayment.getTransactions().size(), 2);
        final PaymentModelDao retriedPaymentModelDao = paymentDao.getPayment(payment.getId(), internalCallContext);
        final PaymentTransactionModelDao retriedPaymentTransactionModelDao = paymentDao.getPaymentTransaction(retriedPayment.getTransactions().get(1).getId(), internalCallContext);
        Assert.assertEquals(retriedPaymentModelDao.getStateName(), "AUTH_SUCCESS");
        Assert.assertEquals(retriedPaymentModelDao.getLastSuccessStateName(), "AUTH_SUCCESS");
        Assert.assertEquals(retriedPaymentTransactionModelDao.getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(retriedPaymentTransactionModelDao.getProcessedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(retriedPaymentTransactionModelDao.getProcessedCurrency(), Currency.EUR);
        Assert.assertEquals(retriedPaymentTransactionModelDao.getGatewayErrorCode(), "");
        Assert.assertEquals(retriedPaymentTransactionModelDao.getGatewayErrorMsg(), "");
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/551")
    public void testFixPaymentTransactionStateNoPaymentTransactionInfoPlugin() throws PaymentApiException {
        final Payment payment = paymentApi.createAuthorization(account,
                                                               account.getPaymentMethodId(),
                                                               null,
                                                               BigDecimal.TEN,
                                                               Currency.EUR,
                                                               null,
                                                               UUID.randomUUID().toString(),
                                                               UUID.randomUUID().toString(),
                                                               ImmutableList.<PluginProperty>of(),
                                                               callContext);

        final PaymentModelDao paymentModelDao = paymentDao.getPayment(payment.getId(), internalCallContext);
        final PaymentTransactionModelDao paymentTransactionModelDao = paymentDao.getPaymentTransaction(payment.getTransactions().get(0).getId(), internalCallContext);
        Assert.assertEquals(paymentModelDao.getStateName(), "AUTH_SUCCESS");
        Assert.assertEquals(paymentModelDao.getLastSuccessStateName(), "AUTH_SUCCESS");
        Assert.assertEquals(paymentTransactionModelDao.getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(paymentTransactionModelDao.getProcessedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(paymentTransactionModelDao.getProcessedCurrency(), Currency.EUR);
        Assert.assertEquals(paymentTransactionModelDao.getGatewayErrorCode(), "");
        Assert.assertEquals(paymentTransactionModelDao.getGatewayErrorMsg(), "");

        try {
            // Since no transaction status is passed, PaymentTransactionInfoPlugin should be set
            adminPaymentApi.fixPaymentTransactionState(payment, Mockito.mock(DefaultPaymentTransaction.class), null, null, "AUTH_ERRORED", ImmutableList.<PluginProperty>of(), callContext);
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_INVALID_PARAMETER.getCode());
        }
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/551")
    public void testFixPaymentTransactionStateFromPaymentTransactionInfoPlugin() throws PaymentApiException {
        final Payment payment = paymentApi.createAuthorization(account,
                                                               account.getPaymentMethodId(),
                                                               null,
                                                               BigDecimal.TEN,
                                                               Currency.EUR,
                                                               null,
                                                               UUID.randomUUID().toString(),
                                                               UUID.randomUUID().toString(),
                                                               ImmutableList.<PluginProperty>of(),
                                                               callContext);

        final PaymentModelDao paymentModelDao = paymentDao.getPayment(payment.getId(), internalCallContext);
        final PaymentTransactionModelDao paymentTransactionModelDao = paymentDao.getPaymentTransaction(payment.getTransactions().get(0).getId(), internalCallContext);
        Assert.assertEquals(paymentModelDao.getStateName(), "AUTH_SUCCESS");
        Assert.assertEquals(paymentModelDao.getLastSuccessStateName(), "AUTH_SUCCESS");
        Assert.assertEquals(paymentTransactionModelDao.getTransactionStatus(), TransactionStatus.SUCCESS);
        Assert.assertEquals(paymentTransactionModelDao.getProcessedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(paymentTransactionModelDao.getProcessedCurrency(), Currency.EUR);
        Assert.assertEquals(paymentTransactionModelDao.getGatewayErrorCode(), "");
        Assert.assertEquals(paymentTransactionModelDao.getGatewayErrorMsg(), "");

        final PaymentTransactionInfoPlugin infoPlugin = new DefaultNoOpPaymentInfoPlugin(paymentTransactionModelDao.getPaymentId(),
                                                                                         paymentTransactionModelDao.getId(),
                                                                                         paymentTransactionModelDao.getTransactionType(),
                                                                                         paymentTransactionModelDao.getAmount(),
                                                                                         paymentTransactionModelDao.getCurrency(),
                                                                                         paymentTransactionModelDao.getEffectiveDate(),
                                                                                         paymentTransactionModelDao.getCreatedDate(),
                                                                                         PaymentPluginStatus.ERROR,
                                                                                         "error-code",
                                                                                         "error-msg");
        final PaymentTransaction newPaymentTransaction = new DefaultPaymentTransaction(paymentTransactionModelDao.getId(),
                                                                                       paymentTransactionModelDao.getAttemptId(),
                                                                                       paymentTransactionModelDao.getTransactionExternalKey(),
                                                                                       paymentTransactionModelDao.getCreatedDate(),
                                                                                       paymentTransactionModelDao.getUpdatedDate(),
                                                                                       paymentTransactionModelDao.getPaymentId(),
                                                                                       paymentTransactionModelDao.getTransactionType(),
                                                                                       paymentTransactionModelDao.getEffectiveDate(),
                                                                                       TransactionStatus.PAYMENT_FAILURE,
                                                                                       paymentTransactionModelDao.getAmount(),
                                                                                       paymentTransactionModelDao.getCurrency(),
                                                                                       paymentTransactionModelDao.getProcessedAmount(),
                                                                                       paymentTransactionModelDao.getProcessedCurrency(),
                                                                                       infoPlugin.getGatewayErrorCode(),
                                                                                       infoPlugin.getGatewayError(),
                                                                                       infoPlugin);
        adminPaymentApi.fixPaymentTransactionState(payment, newPaymentTransaction, null, null, "AUTH_ERRORED", ImmutableList.<PluginProperty>of(), callContext);

        final PaymentModelDao refreshedPaymentModelDao = paymentDao.getPayment(payment.getId(), internalCallContext);
        final PaymentTransactionModelDao refreshedPaymentTransactionModelDao = paymentDao.getPaymentTransaction(payment.getTransactions().get(0).getId(), internalCallContext);
        Assert.assertEquals(refreshedPaymentModelDao.getStateName(), "AUTH_ERRORED");
        Assert.assertNull(refreshedPaymentModelDao.getLastSuccessStateName());
        Assert.assertEquals(refreshedPaymentTransactionModelDao.getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        Assert.assertEquals(refreshedPaymentTransactionModelDao.getProcessedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(refreshedPaymentTransactionModelDao.getProcessedCurrency(), Currency.EUR);
        Assert.assertEquals(refreshedPaymentTransactionModelDao.getGatewayErrorCode(), "error-code");
        Assert.assertEquals(refreshedPaymentTransactionModelDao.getGatewayErrorMsg(), "error-msg");
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill-adyen-plugin/pull/60")
    public void testFixPaymentTransactionStateRefundSuccessToRefundFailed() throws PaymentApiException {
        final Payment payment = paymentApi.createPurchase(account,
                                                          account.getPaymentMethodId(),
                                                          null,
                                                          BigDecimal.TEN,
                                                          Currency.EUR,
                                                          null,
                                                          UUID.randomUUID().toString(),
                                                          UUID.randomUUID().toString(),
                                                          ImmutableList.<PluginProperty>of(),
                                                          callContext);

        final PaymentModelDao paymentModelDao = paymentDao.getPayment(payment.getId(), internalCallContext);
        final PaymentTransactionModelDao paymentTransactionModelDao = paymentDao.getPaymentTransaction(payment.getTransactions().get(0).getId(), internalCallContext);
        Assert.assertEquals(paymentModelDao.getStateName(), "PURCHASE_SUCCESS");
        Assert.assertEquals(paymentModelDao.getLastSuccessStateName(), "PURCHASE_SUCCESS");
        Assert.assertEquals(paymentTransactionModelDao.getTransactionStatus(), TransactionStatus.SUCCESS);

        final Payment refund = paymentApi.createRefund(account,
                                                       payment.getId(),
                                                       payment.getPurchasedAmount(),
                                                       payment.getCurrency(),
                                                       null,
                                                       UUID.randomUUID().toString(),
                                                       ImmutableList.<PluginProperty>of(),
                                                       callContext);

        final PaymentModelDao paymentModelDao2 = paymentDao.getPayment(payment.getId(), internalCallContext);
        final PaymentTransactionModelDao paymentTransactionModelDao2 = paymentDao.getPaymentTransaction(refund.getTransactions().get(1).getId(), internalCallContext);
        Assert.assertEquals(paymentModelDao2.getStateName(), "REFUND_SUCCESS");
        Assert.assertEquals(paymentModelDao2.getLastSuccessStateName(), "REFUND_SUCCESS");
        Assert.assertEquals(paymentTransactionModelDao2.getTransactionStatus(), TransactionStatus.SUCCESS);

        adminPaymentApi.fixPaymentTransactionState(refund,
                                                   refund.getTransactions().get(1),
                                                   TransactionStatus.PAYMENT_FAILURE,
                                                   null, /* Let Kill Bill figure it out */
                                                   null, /* Let Kill Bill figure it out */
                                                   ImmutableList.<PluginProperty>of(),
                                                   callContext);

        final PaymentModelDao paymentModelDao3 = paymentDao.getPayment(payment.getId(), internalCallContext);
        final PaymentTransactionModelDao paymentTransactionModelDao3 = paymentDao.getPaymentTransaction(refund.getTransactions().get(1).getId(), internalCallContext);
        Assert.assertEquals(paymentModelDao3.getStateName(), "REFUND_FAILED");
        Assert.assertEquals(paymentModelDao3.getLastSuccessStateName(), "PURCHASE_SUCCESS");
        Assert.assertEquals(paymentTransactionModelDao3.getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill-adyen-plugin/pull/60")
    public void testFixPaymentTransactionStateRefundFailedToRefundSuccess() throws PaymentApiException {
        final Payment payment = paymentApi.createPurchase(account,
                                                          account.getPaymentMethodId(),
                                                          null,
                                                          BigDecimal.TEN,
                                                          Currency.EUR,
                                                          null,
                                                          UUID.randomUUID().toString(),
                                                          UUID.randomUUID().toString(),
                                                          ImmutableList.<PluginProperty>of(),
                                                          callContext);

        final PaymentModelDao paymentModelDao = paymentDao.getPayment(payment.getId(), internalCallContext);
        final PaymentTransactionModelDao paymentTransactionModelDao = paymentDao.getPaymentTransaction(payment.getTransactions().get(0).getId(), internalCallContext);
        Assert.assertEquals(paymentModelDao.getStateName(), "PURCHASE_SUCCESS");
        Assert.assertEquals(paymentModelDao.getLastSuccessStateName(), "PURCHASE_SUCCESS");
        Assert.assertEquals(paymentTransactionModelDao.getTransactionStatus(), TransactionStatus.SUCCESS);

        final Payment refund = paymentApi.createRefund(account,
                                                       payment.getId(),
                                                       payment.getPurchasedAmount(),
                                                       payment.getCurrency(),
                                                       null,
                                                       UUID.randomUUID().toString(),
                                                       ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, PaymentPluginStatus.ERROR.toString(), false)),
                                                       callContext);

        final PaymentModelDao paymentModelDao2 = paymentDao.getPayment(payment.getId(), internalCallContext);
        final PaymentTransactionModelDao paymentTransactionModelDao2 = paymentDao.getPaymentTransaction(refund.getTransactions().get(1).getId(), internalCallContext);
        Assert.assertEquals(paymentModelDao2.getStateName(), "REFUND_FAILED");
        Assert.assertEquals(paymentModelDao2.getLastSuccessStateName(), "PURCHASE_SUCCESS");
        Assert.assertEquals(paymentTransactionModelDao2.getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);

        adminPaymentApi.fixPaymentTransactionState(refund,
                                                   refund.getTransactions().get(1),
                                                   TransactionStatus.SUCCESS,
                                                   null, /* Let Kill Bill figure it out */
                                                   null, /* Let Kill Bill figure it out */
                                                   ImmutableList.<PluginProperty>of(),
                                                   callContext);

        final PaymentModelDao paymentModelDao3 = paymentDao.getPayment(payment.getId(), internalCallContext);
        final PaymentTransactionModelDao paymentTransactionModelDao3 = paymentDao.getPaymentTransaction(refund.getTransactions().get(1).getId(), internalCallContext);
        Assert.assertEquals(paymentModelDao3.getStateName(), "REFUND_SUCCESS");
        Assert.assertEquals(paymentModelDao3.getLastSuccessStateName(), "REFUND_SUCCESS");
        Assert.assertEquals(paymentTransactionModelDao3.getTransactionStatus(), TransactionStatus.SUCCESS);
    }
}
