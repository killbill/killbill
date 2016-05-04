/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.payment.dispatcher;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.dispatcher.PluginDispatcher.PluginDispatcherReturnType;
import org.killbill.commons.locker.LockFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;

public class PaymentPluginDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PaymentPluginDispatcher.class);

    public static <ReturnType> ReturnType dispatchWithExceptionHandling(@Nullable final Account account, final String pluginNames, final Callable<PluginDispatcherReturnType<ReturnType>> callable, final PluginDispatcher<ReturnType> pluginDispatcher) throws PaymentApiException {
        final UUID accountId = account != null ? account.getId() : null;
        final String accountExternalKey = account != null ? account.getExternalKey() : "";

        try {
            log.debug("Calling plugin(s) {}", pluginNames);
            final ReturnType result = pluginDispatcher.dispatchWithTimeout(callable);
            log.debug("Successful plugin(s) call of {} for account {} with result {}", pluginNames, accountExternalKey, result);
            return result;
        } catch (final TimeoutException e) {
            final String errorMessage = String.format("Call TIMEOUT for accountId='%s' accountExternalKey='%s' plugin='%s'", accountId, accountExternalKey, pluginNames);
            log.warn(errorMessage);
            throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_TIMEOUT, accountId, errorMessage);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            final String errorMessage = String.format("Call was interrupted for accountId='%s' accountExternalKey='%s' plugin='%s'", accountId, accountExternalKey, pluginNames);
            log.warn(errorMessage, e);
            throw new PaymentApiException(ErrorCode.PAYMENT_INTERNAL_ERROR, MoreObjects.firstNonNull(e.getMessage(), errorMessage));
        } catch (final ExecutionException e) {
            if (e.getCause() instanceof PaymentApiException) {
                throw (PaymentApiException) e.getCause();
            } else if (e.getCause() instanceof LockFailedException) {
                final String format = String.format("Failed to lock accountExternalKey='%s'", accountExternalKey);
                log.warn(format);
                throw new PaymentApiException(ErrorCode.PAYMENT_INTERNAL_ERROR, format);
            } else {
                // Unwraps the ExecutionException (e.getCause()), since it's a dispatch implementation detail
                throw new PaymentApiException(e.getCause(), ErrorCode.PAYMENT_INTERNAL_ERROR, MoreObjects.firstNonNull(e.getMessage(), ""));
            }
        }
    }
}
