/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.payment.dispatcher;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.killbill.automaton.OperationException;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.payment.api.PaymentApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;

public class PluginDispatcher<T> {

    private static final Logger log = LoggerFactory.getLogger(PluginDispatcher.class);

    private final TimeUnit DEEFAULT_PLUGIN_TIMEOUT_UNIT = TimeUnit.SECONDS;

    private final long timeoutSeconds;
    private final ExecutorService executor;

    public PluginDispatcher(final long timeoutSeconds, final ExecutorService executor) {
        this.timeoutSeconds = timeoutSeconds;
        this.executor = executor;
    }

    // TODO Once we switch fully to automata, should this throw PaymentPluginApiException instead?
    public T dispatchWithTimeout(final Callable<T> task) throws PaymentApiException, OperationException, TimeoutException {
        return dispatchWithTimeout(task, timeoutSeconds, DEEFAULT_PLUGIN_TIMEOUT_UNIT);
    }

    public T dispatchWithTimeout(final Callable<T> task, final long timeout, final TimeUnit unit)
            throws PaymentApiException, TimeoutException, OperationException {

        try {
            final Future<T> future = executor.submit(task);
            return future.get(timeout, unit);
        } catch (final ExecutionException e) {
            if (e.getCause() instanceof PaymentApiException) {
                throw (PaymentApiException) e.getCause();
            } else if (e.getCause() instanceof OperationException) {
                throw (OperationException) e.getCause();
            } else if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            } else {
                throw new PaymentApiException(Objects.firstNonNull(e.getCause(), e), ErrorCode.PAYMENT_INTERNAL_ERROR, e.getMessage());
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentApiException(ErrorCode.PAYMENT_INTERNAL_ERROR, e.getMessage());
        }
    }
}
