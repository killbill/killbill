/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.billing.payment.invoice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.invoice.dao.InvoicePaymentControlDao;
import org.killbill.billing.payment.retry.BaseRetryService.RetryServiceScheduler;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.clock.Clock;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestInvoicePaymentControlPluginApiUnit extends PaymentTestSuiteNoDB {

    private InvoicePaymentControlPluginApi createInvoicePaymentControlApi() {
        final PaymentConfig paymentConfig = Mockito.mock(PaymentConfig.class);
        final InvoiceInternalApi internalApi = Mockito.mock(InvoiceInternalApi.class);
        final TagUserApi tagUserApi = Mockito.mock(TagUserApi.class);
        final PaymentDao paymentDao = Mockito.mock(PaymentDao.class);
        final InvoicePaymentControlDao invoicePaymentControlDao = Mockito.mock(InvoicePaymentControlDao.class);
        final RetryServiceScheduler retryServiceScheduler = Mockito.mock(RetryServiceScheduler.class);
        final InternalCallContextFactory contextFactory = Mockito.mock(InternalCallContextFactory.class);
        final AccountInternalApi accountInternalApi = Mockito.mock(AccountInternalApi.class);
        final Clock clock = Mockito.mock(Clock.class);

        return new InvoicePaymentControlPluginApi(paymentConfig,
                                                  internalApi,
                                                  tagUserApi,
                                                  paymentDao,
                                                  invoicePaymentControlDao,
                                                  retryServiceScheduler,
                                                  contextFactory,
                                                  clock,
                                                  accountInternalApi);
    }

    private Collection<PaymentTransactionModelDao> createPaymentTransactionModelDao(final TransactionStatus... modelDaoAvailableStatuses) {
        final Collection<PaymentTransactionModelDao> result = new ArrayList<>();
        for (final TransactionStatus status : modelDaoAvailableStatuses) {
            final PaymentTransactionModelDao modelDao = Mockito.mock(PaymentTransactionModelDao.class);
            Mockito.when(modelDao.getTransactionStatus()).thenReturn(status);
            result.add(modelDao);
        }
        return result;
    }

    @Test(groups = "fast")
    public void testGetNumberAttemptsInState() {
        final Collection<PaymentTransactionModelDao> modelDao = createPaymentTransactionModelDao(
                TransactionStatus.SUCCESS,
                TransactionStatus.SUCCESS,
                TransactionStatus.PAYMENT_FAILURE,
                TransactionStatus.PENDING,
                TransactionStatus.PAYMENT_FAILURE,
                TransactionStatus.PAYMENT_SYSTEM_OFF,
                TransactionStatus.PENDING,
                TransactionStatus.SUCCESS);

        final InvoicePaymentControlPluginApi api = createInvoicePaymentControlApi();

        int result = api.getNumberAttemptsInState(modelDao, TransactionStatus.SUCCESS);
        Assert.assertEquals(result, 3);

        result = api.getNumberAttemptsInState(modelDao, TransactionStatus.PENDING, TransactionStatus.PAYMENT_FAILURE);
        Assert.assertEquals(result, 4);

        result = api.getNumberAttemptsInState(modelDao, TransactionStatus.PAYMENT_SYSTEM_OFF);
        Assert.assertEquals(result, 1);

        result = api.getNumberAttemptsInState(modelDao, TransactionStatus.UNKNOWN);
        Assert.assertEquals(result, 0);

        result = api.getNumberAttemptsInState(Collections.emptyList(), TransactionStatus.SUCCESS);
        Assert.assertEquals(result, 0);
    }
}
