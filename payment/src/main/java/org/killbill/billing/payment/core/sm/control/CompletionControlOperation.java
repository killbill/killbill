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

package org.killbill.billing.payment.core.sm.control;

import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.payment.core.ProcessorBase.WithAccountLockCallback;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.dispatcher.PluginDispatcher.PluginDispatcherReturnType;
import org.killbill.billing.routing.plugin.api.PaymentRoutingContext;
import org.killbill.billing.routing.plugin.api.PaymentRoutingPluginApi;
import org.killbill.commons.locker.GlobalLocker;

//
// Used from AttemptCompletionTask to resume an incomplete payment that went through control API.
//
public class CompletionControlOperation extends OperationControlCallback {

    public CompletionControlOperation(final GlobalLocker locker, final PluginDispatcher<OperationResult> paymentPluginDispatcher, final PaymentStateControlContext paymentStateContext, final PaymentProcessor paymentProcessor, final OSGIServiceRegistration<PaymentRoutingPluginApi> retryPluginRegistry) {
        super(locker, paymentPluginDispatcher, paymentStateContext, paymentProcessor, retryPluginRegistry);
    }

    @Override
    public OperationResult doOperationCallback() throws OperationException {

        return dispatchWithAccountLockAndTimeout(new WithAccountLockCallback<PluginDispatcherReturnType<OperationResult>, OperationException>() {
            @Override
            public PluginDispatcherReturnType<OperationResult> doOperation() throws OperationException {
                final PaymentTransactionModelDao transaction = paymentStateContext.getPaymentTransactionModelDao();
                final PaymentRoutingContext updatedPaymentControlContext = new DefaultPaymentControlContext(paymentStateContext.getAccount(),
                                                                                                            paymentStateContext.getPaymentMethodId(),
                                                                                                            paymentStateControlContext.getAttemptId(),
                                                                                                            transaction.getPaymentId(),
                                                                                                            paymentStateContext.getPaymentExternalKey(),
                                                                                                            transaction.getId(),
                                                                                                            paymentStateContext.getPaymentTransactionExternalKey(),
                                                                                                            paymentStateContext.getTransactionType(),
                                                                                                            transaction.getAmount(),
                                                                                                            transaction.getCurrency(),
                                                                                                            transaction.getProcessedAmount(),
                                                                                                            transaction.getProcessedCurrency(),
                                                                                                            paymentStateContext.getProperties(),
                                                                                                            paymentStateControlContext.isApiPayment(),
                                                                                                            paymentStateContext.getCallContext());

                executePluginOnSuccessCalls(paymentStateControlContext.getPaymentControlPluginNames(), updatedPaymentControlContext);
                return PluginDispatcher.createPluginDispatcherReturnType(OperationResult.SUCCESS);
            }
        });
    }

    @Override
    protected Payment doCallSpecificOperationCallback() throws PaymentApiException {
        return null;
    }
}
