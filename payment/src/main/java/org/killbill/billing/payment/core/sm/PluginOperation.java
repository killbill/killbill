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
import java.util.concurrent.TimeoutException;

import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.core.ProcessorBase.CallableWithAccountLock;
import org.killbill.billing.payment.core.ProcessorBase.CallableWithoutAccountLock;
import org.killbill.billing.payment.core.ProcessorBase.WithAccountLockCallback;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.commons.locker.GlobalLocker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;

// Encapsulates the plugin delegation logic
public abstract class PluginOperation {

    private final Logger logger = LoggerFactory.getLogger(PluginOperation.class);

    private final GlobalLocker locker;
    private final PluginDispatcher<OperationResult> paymentPluginDispatcher;

    protected final DirectPaymentStateContext directPaymentStateContext;

    protected PluginOperation(final GlobalLocker locker,
                              final PluginDispatcher<OperationResult> paymentPluginDispatcher,
                              final DirectPaymentStateContext directPaymentStateContext) {
        this.locker = locker;
        this.paymentPluginDispatcher = paymentPluginDispatcher;
        this.directPaymentStateContext = directPaymentStateContext;
    }

    protected abstract <PluginResult> PluginResult  doPluginOperation() throws Exception;

    protected OperationResult dispatchWithTimeout(final WithAccountLockCallback<OperationResult> callback) throws OperationException {
        final Account account = directPaymentStateContext.getAccount();
        logger.debug("Dispatching plugin call for account {}", account.getExternalKey());

        try {
            final Callable<OperationResult> task = new CallableWithAccountLock<OperationResult>(locker,
                                                                                                account.getExternalKey(),
                                                                                                callback);

            final OperationResult operationResult = paymentPluginDispatcher.dispatchWithTimeout(task);
            logger.debug("Successful plugin call for account {} with result {}", account.getExternalKey(), operationResult);
            return operationResult;
        } catch (final PaymentApiException e) {
            final Throwable realException = Objects.firstNonNull(e.getCause(), e);
            logger.warn("Unsuccessful plugin call for account {}", account.getExternalKey(), realException);
            throw new OperationException(realException, OperationResult.FAILURE);
        } catch (final TimeoutException e) {
            logger.error("Plugin call TIMEOUT for account {}: {}", account.getExternalKey(), e.getMessage());
            throw new OperationException(e, OperationResult.EXCEPTION);
        } catch (final RuntimeException e) {
            logger.warn("Plugin call threw an exception for account {}", account.getExternalKey(), e);
            throw new OperationException(e, OperationResult.EXCEPTION);
        }
    }
}
