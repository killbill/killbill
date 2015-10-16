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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.payment.core.ProcessorBase.CallableWithAccountLock;
import org.killbill.billing.payment.core.ProcessorBase.DispatcherCallback;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.dispatcher.PluginDispatcher.PluginDispatcherReturnType;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.commons.locker.GlobalLocker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class OperationCallbackBase<CallbackOperationResult, CallbackOperationException extends Exception> {

    protected final Logger logger = LoggerFactory.getLogger(OperationCallbackBase.class);

    private final GlobalLocker locker;
    private final PluginDispatcher<OperationResult> paymentPluginDispatcher;
    private final PaymentConfig paymentConfig;

    protected final PaymentStateContext paymentStateContext;

    protected OperationCallbackBase(final GlobalLocker locker,
                                    final PluginDispatcher<OperationResult> paymentPluginDispatcher,
                                    final PaymentConfig paymentConfig,
                                    final PaymentStateContext paymentStateContext) {
        this.locker = locker;
        this.paymentPluginDispatcher = paymentPluginDispatcher;
        this.paymentStateContext = paymentStateContext;
        this.paymentConfig = paymentConfig;
    }

    //
    // Dispatch the Callable to the executor by first wrapping it into a CallableWithAccountLock
    // The dispatcher may throw a TimeoutException, ExecutionException, or InterruptedException; those will be handled in specific
    // callback to eventually throw a OperationException, that will be used to drive the state machine in the right direction.
    //
    protected <ExceptionType extends Exception> OperationResult dispatchWithAccountLockAndTimeout(final String pluginNames, final DispatcherCallback<PluginDispatcherReturnType<OperationResult>, ExceptionType> callback) throws OperationException {
        final Account account = paymentStateContext.getAccount();
        logger.debug("Dispatching plugin call for account {}", account.getExternalKey());

        try {
            final Callable<PluginDispatcherReturnType<OperationResult>> task = new CallableWithAccountLock<OperationResult, ExceptionType>(locker,
                                                                                                                                           account.getExternalKey(),
                                                                                                                                           paymentConfig,
                                                                                                                                           callback);
            logger.debug("Calling plugin {}", pluginNames);
            final OperationResult operationResult = paymentPluginDispatcher.dispatchWithTimeout(task);
            logger.debug("Successful plugin call of {} for account {} with result {}", pluginNames, account.getExternalKey(), operationResult);
            return operationResult;
        } catch (final ExecutionException e) {
            throw unwrapExceptionFromDispatchedTask(paymentStateContext, e);
        } catch (final TimeoutException e) {
            logger.warn("TimeoutException while executing the plugin(s) {}", pluginNames);
            throw unwrapExceptionFromDispatchedTask(paymentStateContext, e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("InterruptedException while executing the following plugin(s): {}", pluginNames);
            throw unwrapExceptionFromDispatchedTask(paymentStateContext, e);
        }
    }

    //
    // The OperationCallback per state machine are often very similar in between operation
    //
    // There is a base glue code that is common to all calls and shared in a base class and then a per call specific operation
    // using the doCallSpecificOperationCallback method below.
    //
    protected abstract CallbackOperationResult doCallSpecificOperationCallback() throws CallbackOperationException;

    protected abstract OperationException unwrapExceptionFromDispatchedTask(final PaymentStateContext paymentStateContext, final Exception e);
}
