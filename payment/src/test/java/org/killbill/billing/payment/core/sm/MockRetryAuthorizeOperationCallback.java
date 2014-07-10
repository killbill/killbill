/*
 * Copyright 2014 Groupon, Inc
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.core.sm;

import java.util.Collections;

import org.killbill.automaton.OperationResult;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.DefaultDirectPayment;
import org.killbill.billing.payment.api.DefaultDirectPaymentTransaction;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.DirectPaymentTransaction;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.retry.plugin.api.PaymentControlPluginApi;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;

public class MockRetryAuthorizeOperationCallback extends RetryAuthorizeOperationCallback {

    private final PaymentDao paymentDao;
    private final Clock clock;

    private Exception exception;
    private OperationResult result;

    public MockRetryAuthorizeOperationCallback(final GlobalLocker locker, final PluginDispatcher<OperationResult> paymentPluginDispatcher, final RetryableDirectPaymentStateContext directPaymentStateContext, final DirectPaymentProcessor directPaymentProcessor, final OSGIServiceRegistration<PaymentControlPluginApi> retryPluginRegistry, final PaymentDao paymentDao, final Clock clock) {
        super(locker, paymentPluginDispatcher, directPaymentStateContext, directPaymentProcessor, retryPluginRegistry);
        this.paymentDao = paymentDao;
        this.clock = clock;
    }

    @Override
    protected DirectPayment doCallSpecificOperationCallback() throws PaymentApiException {
        if (exception != null) {
            if (exception instanceof PaymentApiException) {
                throw (PaymentApiException) exception;
            } else {
                throw new RuntimeException(exception);
            }
        }
        final PaymentModelDao payment = new PaymentModelDao(clock.getUTCNow(),
                                                            clock.getUTCNow(),
                                                            directPaymentStateContext.account.getId(),
                                                            directPaymentStateContext.paymentMethodId,
                                                            directPaymentStateContext.directPaymentExternalKey);

        final PaymentTransactionModelDao transaction = new PaymentTransactionModelDao(clock.getUTCNow(),
                                                                                      clock.getUTCNow(),
                                                                                      directPaymentStateContext.getAttemptId(),
                                                                                      directPaymentStateContext.directPaymentTransactionExternalKey,
                                                                                      directPaymentStateContext.directPaymentId,
                                                                                      directPaymentStateContext.transactionType,
                                                                                      clock.getUTCNow(),
                                                                                      TransactionStatus.SUCCESS,
                                                                                      directPaymentStateContext.amount,
                                                                                      directPaymentStateContext.currency,
                                                                                      "",
                                                                                      "");
        final PaymentModelDao paymentModelDao = paymentDao.insertDirectPaymentWithFirstTransaction(payment, transaction, directPaymentStateContext.internalCallContext);
        final DirectPaymentTransaction convertedTransaction = new DefaultDirectPaymentTransaction(transaction.getId(),
                                                                                                  directPaymentStateContext.getAttemptId(),
                                                                                                  transaction.getTransactionExternalKey(),
                                                                                                  transaction.getCreatedDate(),
                                                                                                  transaction.getUpdatedDate(),
                                                                                                  transaction.getPaymentId(),
                                                                                                  transaction.getTransactionType(),
                                                                                                  transaction.getEffectiveDate(),
                                                                                                  transaction.getTransactionStatus(),
                                                                                                  transaction.getAmount(),
                                                                                                  transaction.getCurrency(),
                                                                                                  transaction.getProcessedAmount(),
                                                                                                  transaction.getProcessedCurrency(),
                                                                                                  transaction.getGatewayErrorCode(),
                                                                                                  transaction.getGatewayErrorMsg(),
                                                                                                  null);

        return new DefaultDirectPayment(paymentModelDao.getId(), paymentModelDao.getCreatedDate(), paymentModelDao.getUpdatedDate(), paymentModelDao.getAccountId(),
                                        paymentModelDao.getPaymentMethodId(), paymentModelDao.getPaymentNumber(), paymentModelDao.getExternalKey(), Collections.singletonList(convertedTransaction));
    }

    public MockRetryAuthorizeOperationCallback setException(final Exception exception) {
        this.exception = exception;
        return this;
    }

    public MockRetryAuthorizeOperationCallback setResult(final OperationResult result) {
        this.result = result;
        return this;
    }
}
