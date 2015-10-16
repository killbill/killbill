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

package org.killbill.billing.payment.core.sm.payments;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.killbill.automaton.Operation.OperationCallback;
import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.PaymentTransactionInfoPluginConverter;
import org.killbill.billing.payment.core.ProcessorBase.DispatcherCallback;
import org.killbill.billing.payment.core.sm.OperationCallbackBase;
import org.killbill.billing.payment.core.sm.PaymentAutomatonDAOHelper;
import org.killbill.billing.payment.core.sm.PaymentStateContext;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.dispatcher.PluginDispatcher.PluginDispatcherReturnType;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.payment.provider.DefaultNoOpPaymentInfoPlugin;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;

import com.google.common.base.MoreObjects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

// Encapsulates the payment specific logic
public abstract class PaymentOperation extends OperationCallbackBase<PaymentTransactionInfoPlugin, PaymentPluginApiException> implements OperationCallback {

    protected final PaymentAutomatonDAOHelper daoHelper;
    protected PaymentPluginApi plugin;

    protected PaymentOperation(final GlobalLocker locker,
                               final PaymentAutomatonDAOHelper daoHelper,
                               final PluginDispatcher<OperationResult> paymentPluginDispatcher,
                               final PaymentConfig paymentConfig,
                               final PaymentStateContext paymentStateContext) {
        super(locker, paymentPluginDispatcher, paymentConfig, paymentStateContext);
        this.daoHelper = daoHelper;
    }

    @Override
    public OperationResult doOperationCallback() throws OperationException {
        try {
            final String pluginName = daoHelper.getPaymentProviderPluginName();
            this.plugin = daoHelper.getPaymentPluginApi(pluginName);

            if (paymentStateContext.shouldLockAccountAndDispatch()) {
                return doOperationCallbackWithDispatchAndAccountLock(pluginName);
            } else {
                return doSimpleOperationCallback();
            }
        } catch (final Exception e) {
            throw convertToUnknownTransactionStatusAndErroredPaymentState(e);
        }
    }

    @Override
    protected OperationException unwrapExceptionFromDispatchedTask(final PaymentStateContext paymentStateContext, final Exception e) {

        // If this is an ExecutionException we attempt to extract the cause first
        final Throwable originalExceptionOrCause = e instanceof ExecutionException ? MoreObjects.firstNonNull(e.getCause(), e) : e;

        //
        // Any case of exception (checked or runtime) should lead to a TransactionStatus.UNKNOWN (and a XXX_ERRORED payment state).
        // In order to reach that state we create PaymentTransactionInfoPlugin with an PaymentPluginStatus.UNDEFINED status (and an OperationResult.EXCEPTION).
        //
        if (originalExceptionOrCause instanceof LockFailedException) {
            logger.warn("Failed to lock account {}", paymentStateContext.getAccount().getExternalKey());
        } else if (originalExceptionOrCause instanceof TimeoutException) {
            logger.error("Plugin call TIMEOUT for account {}", paymentStateContext.getAccount().getExternalKey());
        } else if (originalExceptionOrCause instanceof InterruptedException) {
            logger.error("Plugin call was interrupted for account {}", paymentStateContext.getAccount().getExternalKey());
        } else {
            logger.warn("Payment plugin call threw an exception for account {}", paymentStateContext.getAccount().getExternalKey(), originalExceptionOrCause);
        }
        return convertToUnknownTransactionStatusAndErroredPaymentState(originalExceptionOrCause);

    }

    //
    // In case of exceptions, timeouts, we don't really know what happened:
    // - Return an OperationResult.EXCEPTION to transition Payment State to Errored (see PaymentTransactionInfoPluginConverter#toOperationResult)
    // - Construct a PaymentTransactionInfoPlugin whose PaymentPluginStatus = UNDEFINED to end up with a paymentTransactionStatus = UNKNOWN and have a chance to
    //   be fixed by Janitor.
    //
    private OperationException convertToUnknownTransactionStatusAndErroredPaymentState(final Throwable e) {

        final PaymentTransactionInfoPlugin paymentInfoPlugin = new DefaultNoOpPaymentInfoPlugin(paymentStateContext.getPaymentId(),
                                                                                                paymentStateContext.getTransactionId(),
                                                                                                paymentStateContext.getTransactionType(),
                                                                                                paymentStateContext.getAmount(),
                                                                                                paymentStateContext.getCurrency(),
                                                                                                paymentStateContext.getCallContext().getCreatedDate(),
                                                                                                paymentStateContext.getCallContext().getCreatedDate(),
                                                                                                PaymentPluginStatus.UNDEFINED,
                                                                                                null,
                                                                                                null);
        paymentStateContext.setPaymentTransactionInfoPlugin(paymentInfoPlugin);
        return new OperationException(e, OperationResult.EXCEPTION);
    }


    @Override
    protected abstract PaymentTransactionInfoPlugin doCallSpecificOperationCallback() throws PaymentPluginApiException;

    protected Iterable<PaymentTransactionModelDao> getOnLeavingStateExistingTransactionsForType(final TransactionType transactionType) {
        if (paymentStateContext.getOnLeavingStateExistingTransactions() == null || paymentStateContext.getOnLeavingStateExistingTransactions().isEmpty()) {
            return ImmutableList.of();
        }
        return Iterables.filter(paymentStateContext.getOnLeavingStateExistingTransactions(), new Predicate<PaymentTransactionModelDao>() {
            @Override
            public boolean apply(final PaymentTransactionModelDao input) {
                return input.getTransactionStatus() == TransactionStatus.SUCCESS && input.getTransactionType() == transactionType;
            }
        });
    }

    protected BigDecimal getSumAmount(final Iterable<PaymentTransactionModelDao> transactions) {
        BigDecimal result = BigDecimal.ZERO;
        final Iterator<PaymentTransactionModelDao> iterator = transactions.iterator();
        while (iterator.hasNext()) {
            result = result.add(iterator.next().getAmount());
        }
        return result;
    }

    private OperationResult doOperationCallbackWithDispatchAndAccountLock(String pluginName) throws OperationException {
        return dispatchWithAccountLockAndTimeout(pluginName, new DispatcherCallback<PluginDispatcherReturnType<OperationResult>, OperationException>() {
            @Override
            public PluginDispatcherReturnType<OperationResult> doOperation() throws OperationException {
                final OperationResult result = doSimpleOperationCallback();
                return PluginDispatcher.createPluginDispatcherReturnType(result);
            }
        });
    }

    private OperationResult doSimpleOperationCallback() throws OperationException {
        try {
            return doOperation();
        } catch (final PaymentApiException e) {
            throw new OperationException(e, OperationResult.EXCEPTION);
        } catch (final RuntimeException e) {
            throw new OperationException(e, OperationResult.EXCEPTION);
        }
    }

    private OperationResult doOperation() throws PaymentApiException {
        try {
            //
            // If the OperationResult was specified in the plugin, it means we want to bypass the plugin and just care
            // about running through the state machine to bring the transaction/payment into a new state.
            //
            if (paymentStateContext.getOverridePluginOperationResult() == null) {
                final PaymentTransactionInfoPlugin paymentInfoPlugin = doCallSpecificOperationCallback();
                //
                // We catch null paymentInfoPlugin and throw a RuntimeException to end up in an UNKNOWN transactionStatus
                // That way we can use the null paymentInfoPlugin when a PaymentPluginApiException is thrown and correctly
                // make the transition to PLUGIN_FAILURE
                //
                if (paymentInfoPlugin == null) {
                    throw new IllegalStateException("Payment plugin returned a null result");
                }

                paymentStateContext.setPaymentTransactionInfoPlugin(paymentInfoPlugin);
                return PaymentTransactionInfoPluginConverter.toOperationResult(paymentStateContext.getPaymentTransactionInfoPlugin());
            } else {
                final PaymentTransactionInfoPlugin paymentInfoPlugin = new DefaultNoOpPaymentInfoPlugin(paymentStateContext.getPaymentId(),
                                                                                                        paymentStateContext.getTransactionId(),
                                                                                                        paymentStateContext.getTransactionType(),
                                                                                                        paymentStateContext.getPaymentTransactionModelDao().getProcessedAmount(),
                                                                                                        paymentStateContext.getPaymentTransactionModelDao().getProcessedCurrency(),
                                                                                                        paymentStateContext.getPaymentTransactionModelDao().getEffectiveDate(),
                                                                                                        paymentStateContext.getPaymentTransactionModelDao().getCreatedDate(),
                                                                                                        buildPaymentPluginStatusFromOperationResult(paymentStateContext.getOverridePluginOperationResult()),
                                                                                                        null,
                                                                                                        null);
                paymentStateContext.setPaymentTransactionInfoPlugin(paymentInfoPlugin);
                return paymentStateContext.getOverridePluginOperationResult();
            }
        } catch (final PaymentPluginApiException e) {
            throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_EXCEPTION, e.getErrorMessage());
        }
    }

    private PaymentPluginStatus buildPaymentPluginStatusFromOperationResult(final OperationResult operationResult) {
        switch (operationResult) {
            case PENDING:
                return PaymentPluginStatus.PENDING;
            case SUCCESS:
                return PaymentPluginStatus.PROCESSED;
            case FAILURE:
                return PaymentPluginStatus.ERROR;
            case EXCEPTION:
            default:
                return PaymentPluginStatus.UNDEFINED;
        }
    }
}
