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
import org.killbill.billing.payment.api.DefaultPayment;
import org.killbill.billing.payment.api.DefaultPaymentTransaction;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.core.sm.control.AuthorizeControlOperation;
import org.killbill.billing.payment.core.sm.control.ControlPluginRunner;
import org.killbill.billing.payment.core.sm.control.PaymentStateControlContext;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;

public class MockRetryAuthorizeOperationCallback extends AuthorizeControlOperation {

    private final PaymentDao paymentDao;
    private final Clock clock;

    private Exception exception;
    private OperationResult result;

    public MockRetryAuthorizeOperationCallback(final GlobalLocker locker,
                                               final PluginDispatcher<OperationResult> paymentPluginDispatcher,
                                               final PaymentConfig paymentConfig,
                                               final PaymentStateControlContext paymentStateContext,
                                               final PaymentProcessor paymentProcessor,
                                               final ControlPluginRunner controlPluginRunner,
                                               final PaymentDao paymentDao,
                                               final Clock clock) {
        super(locker, paymentPluginDispatcher, paymentConfig, paymentStateContext, paymentProcessor, controlPluginRunner);
        this.paymentDao = paymentDao;
        this.clock = clock;
    }

    @Override
    protected Payment doCallSpecificOperationCallback() throws PaymentApiException {
        if (exception != null) {
            if (exception instanceof PaymentApiException) {
                throw (PaymentApiException) exception;
            } else if (exception instanceof RuntimeException) {
                throw (RuntimeException) exception;
            } else {
                throw new RuntimeException(exception);
            }
        }
        final PaymentModelDao payment = new PaymentModelDao(clock.getUTCNow(),
                                                            clock.getUTCNow(),
                                                            paymentStateContext.getAccount().getId(),
                                                            paymentStateContext.getPaymentMethodId(),
                                                            paymentStateContext.getPaymentExternalKey());

        final PaymentTransactionModelDao transaction = new PaymentTransactionModelDao(clock.getUTCNow(),
                                                                                      clock.getUTCNow(),
                                                                                      paymentStateContext.getAttemptId(),
                                                                                      paymentStateContext.getPaymentTransactionExternalKey(),
                                                                                      paymentStateContext.getPaymentId(),
                                                                                      paymentStateContext.getTransactionType(),
                                                                                      clock.getUTCNow(),
                                                                                      TransactionStatus.SUCCESS,
                                                                                      paymentStateContext.getAmount(),
                                                                                      paymentStateContext.getCurrency(),
                                                                                      "",
                                                                                      "");
        final PaymentModelDao paymentModelDao = paymentDao.insertPaymentWithFirstTransaction(payment, transaction, paymentStateContext.getInternalCallContext()).getPaymentModelDao();
        final PaymentTransaction convertedTransaction = new DefaultPaymentTransaction(transaction.getId(),
                                                                                                  paymentStateContext.getAttemptId(),
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

        return new DefaultPayment(paymentModelDao.getId(), paymentModelDao.getCreatedDate(), paymentModelDao.getUpdatedDate(), paymentModelDao.getAccountId(),
                                        paymentModelDao.getPaymentMethodId(), paymentModelDao.getPaymentNumber(), paymentModelDao.getExternalKey(), Collections.singletonList(convertedTransaction), null);
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
