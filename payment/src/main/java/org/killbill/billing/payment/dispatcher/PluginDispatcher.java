/*
 * Copyright 2010-2013 Ning, Inc.
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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.shiro.util.ThreadContext;
import org.killbill.billing.payment.core.PaymentExecutors;
import org.killbill.billing.util.UUIDs;
import org.killbill.commons.profiling.Profiling;
import org.killbill.commons.profiling.ProfilingData;
import org.killbill.commons.request.Request;
import org.slf4j.MDC;

import com.google.common.annotations.VisibleForTesting;

public class PluginDispatcher<ReturnType> {

    private final TimeUnit DEFAULT_PLUGIN_TIMEOUT_UNIT = TimeUnit.SECONDS;

    private final long timeoutSeconds;
    private final PaymentExecutors paymentExecutors;

    public PluginDispatcher(final long timeoutSeconds, final PaymentExecutors paymentExecutors) {
        this.timeoutSeconds = timeoutSeconds;
        this.paymentExecutors = paymentExecutors;
    }

    // TODO Once we switch fully to automata, should this throw PaymentPluginApiException instead?
    public ReturnType dispatchWithTimeout(final Callable<PluginDispatcherReturnType<ReturnType>> task) throws TimeoutException, ExecutionException, InterruptedException {
        return dispatchWithTimeout(task, timeoutSeconds, DEFAULT_PLUGIN_TIMEOUT_UNIT);
    }

    @VisibleForTesting
    ReturnType dispatchWithTimeout(final Callable<PluginDispatcherReturnType<ReturnType>> task, final long timeout, final TimeUnit unit)
            throws TimeoutException, ExecutionException, InterruptedException {

        final ExecutorService pluginExecutor = paymentExecutors.getPluginExecutorService();

        // Wrap existing callable to keep the original requestId
        final Callable<PluginDispatcherReturnType<ReturnType>> callableWithRequestData = new CallableWithRequestData(Request.getPerThreadRequestData(),
                                                                                                                     UUIDs.getRandom(),
                                                                                                                     ThreadContext.getSecurityManager(),
                                                                                                                     ThreadContext.getSubject(),
                                                                                                                     MDC.getCopyOfContextMap(),
                                                                                                                     task);

        final Future<PluginDispatcherReturnType<ReturnType>> future = pluginExecutor.submit(callableWithRequestData);
        final PluginDispatcherReturnType<ReturnType> pluginDispatcherResult = future.get(timeout, unit);

        if (pluginDispatcherResult instanceof WithProfilingPluginDispatcherReturnType) {
            // Transfer state from dispatch thread into current one.
            final ProfilingData currentThreadProfilingData = Profiling.getPerThreadProfilingData();
            if (currentThreadProfilingData != null) {
                currentThreadProfilingData.merge(((WithProfilingPluginDispatcherReturnType)pluginDispatcherResult).getProfilingData());
            }
        }
        return pluginDispatcherResult.getReturnType();
    }

    public interface PluginDispatcherReturnType<ReturnType> {
        public ReturnType getReturnType();
    }

    public interface WithProfilingPluginDispatcherReturnType<ReturnType> extends PluginDispatcherReturnType<ReturnType> {
        public ProfilingData getProfilingData();
    }

    public static class DefaultWithProfilingPluginDispatcherReturnType<ReturnType> implements WithProfilingPluginDispatcherReturnType<ReturnType> {
        private final ReturnType returnType;
        private final ProfilingData profilingData;

        public DefaultWithProfilingPluginDispatcherReturnType(final ReturnType returnType, final ProfilingData profilingData) {
            this.returnType = returnType;
            this.profilingData = profilingData;
        }

        @Override
        public ReturnType getReturnType() {
            return returnType;
        }

        @Override
        public ProfilingData getProfilingData() {
            return profilingData;
        }
    }

    public static <ReturnType> PluginDispatcherReturnType<ReturnType> createPluginDispatcherReturnType(final ReturnType returnType) {
        return new DefaultWithProfilingPluginDispatcherReturnType(returnType, Profiling.getPerThreadProfilingData());
    }

}
