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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

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

import com.google.common.base.Objects;

// Encapsulates the payment specific logic
public abstract class DirectPaymentOperation extends OperationCallbackBase implements OperationCallback {

    protected final PaymentPluginApi plugin;

    protected DirectPaymentOperation(final DirectPaymentAutomatonDAOHelper daoHelper, final GlobalLocker locker,
                                     final PluginDispatcher<OperationResult> paymentPluginDispatcher,
                                     final DirectPaymentStateContext directPaymentStateContext) throws PaymentApiException {
        super(locker, paymentPluginDispatcher, directPaymentStateContext);
        this.plugin = daoHelper.getPaymentProviderPlugin();
    }


    @Override
    public OperationResult doOperationCallback() throws OperationException {
        if (directPaymentStateContext.shouldLockAccountAndDispatch()) {
            return doOperationCallbackWithDispatchAndAccountLock();
        } else {
            return doSimpleOperationCallback();
        }
    }

    @Override
    protected OperationException rewrapExecutionException(final DirectPaymentStateContext directPaymentStateContext, final ExecutionException e) {
        if (e.getCause() instanceof PaymentPluginApiException) {
            final Throwable realException = Objects.firstNonNull(e.getCause(), e);
            logger.warn("Unsuccessful plugin call for account {}", directPaymentStateContext.getAccount().getExternalKey(), realException);
            return new OperationException(realException, OperationResult.FAILURE);
        } else /* if (e instanceof RuntimeException) */ {
            logger.warn("Plugin call threw an exception for account {}", directPaymentStateContext.getAccount().getExternalKey(), e);
            return new OperationException(e, OperationResult.EXCEPTION);
        }
    }

    @Override
    protected OperationException wrapTimeoutException(final DirectPaymentStateContext directPaymentStateContext, final TimeoutException e) {
        logger.error("Plugin call TIMEOUT for account {}: {}", directPaymentStateContext.getAccount().getExternalKey(), e.getMessage());
        return new OperationException(e, OperationResult.EXCEPTION);
    }

    @Override
    protected OperationException wrapInterruptedException(final DirectPaymentStateContext directPaymentStateContext, final InterruptedException e) {
        logger.error("Plugin call was interrupted for account {}: {}", directPaymentStateContext.getAccount().getExternalKey(), e.getMessage());
        return new OperationException(e, OperationResult.EXCEPTION);
    }

    @Override
    protected abstract PaymentTransactionInfoPlugin doCallSpecificOperationCallback() throws PaymentPluginApiException;

    private OperationResult doOperationCallbackWithDispatchAndAccountLock() throws OperationException {
        return dispatchWithAccountLockAndTimeout(new WithAccountLockCallback<OperationResult, OperationException>() {
            @Override
            public OperationResult doOperation() throws OperationException {
                return doSimpleOperationCallback();
            }
        });
    }

    private OperationResult doSimpleOperationCallback() throws OperationException {
        try {
            return doOperation();
        } catch (final PaymentApiException e) {
            throw new OperationException(e, OperationResult.FAILURE);
        }
    }

    private OperationResult doOperation() throws PaymentApiException {
        try {
            final PaymentTransactionInfoPlugin paymentInfoPlugin = doCallSpecificOperationCallback();

            directPaymentStateContext.setPaymentInfoPlugin(paymentInfoPlugin);

            return processPaymentInfoPlugin();
        } catch (final PaymentPluginApiException e) {
            throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_EXCEPTION, e.getErrorMessage());
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
