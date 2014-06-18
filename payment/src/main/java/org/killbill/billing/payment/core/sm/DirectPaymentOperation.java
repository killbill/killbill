/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.core.ProcessorBase.WithAccountLockCallback;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.commons.locker.GlobalLocker;

// Encapsulates the payment specific logic
public abstract class DirectPaymentOperation extends PluginOperation implements OperationCallback {

    protected final PaymentPluginApi plugin;

    protected DirectPaymentOperation(final DirectPaymentAutomatonDAOHelper daoHelper, final GlobalLocker locker,
                                     final PluginDispatcher<OperationResult> paymentPluginDispatcher,
                                     final DirectPaymentStateContext directPaymentStateContext) throws PaymentApiException {
        super(locker, paymentPluginDispatcher, directPaymentStateContext);
        this.plugin = daoHelper.getPaymentProviderPlugin();
    }

    protected abstract PaymentTransactionInfoPlugin doPluginOperation() throws PaymentPluginApiException;

    @Override
    public OperationResult doOperationCallback() throws OperationException {
        if (directPaymentStateContext.shouldLockAccountAndDispatch()) {
            return doOperationCallbackWithDispatchAndAccountLock();
        } else {
            return doSimpleOperationCallback();
        }
    }

    private OperationResult doOperationCallbackWithDispatchAndAccountLock() throws OperationException {
        return dispatchWithTimeout(new WithAccountLockCallback<OperationResult>() {
            @Override
            public OperationResult doOperation() throws Exception {
                return doSimpleOperationCallback();
            }
        });
    }

    private OperationResult doSimpleOperationCallback() throws OperationException {
        try {
            return doOperation();
        } catch (PaymentApiException e) {
            throw new OperationException(e, OperationResult.FAILURE);
        }
    }

    private OperationResult doOperation() throws PaymentApiException {
        try {
            final PaymentTransactionInfoPlugin paymentInfoPlugin = doPluginOperation();

            directPaymentStateContext.setPaymentInfoPlugin(paymentInfoPlugin);

            return processPaymentInfoPlugin();
        } catch (final PaymentPluginApiException e) {
            // We don't care about the ErrorCode since it will be unwrapped
            throw new PaymentApiException(e, ErrorCode.__UNKNOWN_ERROR_CODE, "");
        }
    }

    private OperationResult processPaymentInfoPlugin() {
        if (directPaymentStateContext.getPaymentInfoPlugin() == null) {
            return OperationResult.FAILURE;
        }

        switch (directPaymentStateContext.getPaymentInfoPlugin().getStatus()) {
            case PROCESSED:
                return OperationResult.SUCCESS;
            case PENDING:
                return OperationResult.PENDING;
            case ERROR:
            case UNDEFINED:
            default:
                return OperationResult.FAILURE;
        }
    }
}
