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

import org.killbill.automaton.OperationResult;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.core.DirectPaymentProcessor;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.retry.plugin.api.PaymentControlPluginApi;
import org.killbill.commons.locker.GlobalLocker;

public class RetryPurchaseOperationCallback extends RetryOperationCallback {

    public RetryPurchaseOperationCallback(final GlobalLocker locker, final PluginDispatcher<OperationResult> paymentPluginDispatcher, final RetryableDirectPaymentStateContext directPaymentStateContext, final DirectPaymentProcessor directPaymentProcessor, final OSGIServiceRegistration<PaymentControlPluginApi> retryPluginRegistry) {
        super(locker, paymentPluginDispatcher, directPaymentStateContext, directPaymentProcessor, retryPluginRegistry);
    }

    @Override
    protected DirectPayment doCallSpecificOperationCallback() throws PaymentApiException {
        return directPaymentProcessor.createPurchase(directPaymentStateContext.account, directPaymentStateContext.paymentMethodId, directPaymentStateContext.directPaymentId, directPaymentStateContext.getAmount(),
                                                     directPaymentStateContext.getCurrency(), directPaymentStateContext.directPaymentExternalKey, directPaymentStateContext.directPaymentTransactionExternalKey, false,
                                                     directPaymentStateContext.getProperties(), directPaymentStateContext.callContext, directPaymentStateContext.internalCallContext);
    }
}
