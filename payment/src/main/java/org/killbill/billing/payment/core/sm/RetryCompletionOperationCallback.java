/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.payment.core.sm;

import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.killbill.billing.payment.core.ProcessorBase.WithAccountLockCallback;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.retry.plugin.api.PaymentControlContext;
import org.killbill.billing.retry.plugin.api.PaymentControlPluginApi;
import org.killbill.commons.locker.GlobalLocker;

public class RetryCompletionOperationCallback extends RetryOperationCallback {

    public RetryCompletionOperationCallback(final GlobalLocker locker, final PluginDispatcher<OperationResult> paymentPluginDispatcher, final RetryableDirectPaymentStateContext directPaymentStateContext, final DirectPaymentProcessor directPaymentProcessor, final OSGIServiceRegistration<PaymentControlPluginApi> retryPluginRegistry) {
        super(locker, paymentPluginDispatcher, directPaymentStateContext, directPaymentProcessor, retryPluginRegistry);
    }

    @Override
    public OperationResult doOperationCallback() throws OperationException {

        return dispatchWithAccountLockAndTimeout(new WithAccountLockCallback<OperationResult, OperationException>() {
            @Override
            public OperationResult doOperation() throws OperationException {
                final PaymentTransactionModelDao transaction = directPaymentStateContext.getDirectPaymentTransactionModelDao();
                final PaymentControlContext updatedPaymentControlContext = new DefaultPaymentControlContext(directPaymentStateContext.getAccount(),
                                                                                                            directPaymentStateContext.getPaymentMethodId(),
                                                                                                            retryableDirectPaymentStateContext.getAttemptId(),
                                                                                                            transaction.getPaymentId(),
                                                                                                            directPaymentStateContext.getDirectPaymentExternalKey(),
                                                                                                            transaction.getId(),
                                                                                                            directPaymentStateContext.getDirectPaymentTransactionExternalKey(),
                                                                                                            directPaymentStateContext.getTransactionType(),
                                                                                                            transaction.getAmount(),
                                                                                                            transaction.getCurrency(),
                                                                                                            transaction.getProcessedAmount(),
                                                                                                            transaction.getProcessedCurrency(),
                                                                                                            directPaymentStateContext.getProperties(),
                                                                                                            retryableDirectPaymentStateContext.isApiPayment(),
                                                                                                            directPaymentStateContext.callContext);

                onCompletion(retryableDirectPaymentStateContext.getPluginName(), updatedPaymentControlContext);
                return OperationResult.SUCCESS;
            }
        });
    }

    @Override
    protected DirectPayment doCallSpecificOperationCallback() throws PaymentApiException {
        return null;
    }
}
