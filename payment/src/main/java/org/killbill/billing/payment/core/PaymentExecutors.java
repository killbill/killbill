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

package org.killbill.billing.payment.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.commons.concurrent.Executors;
import org.killbill.commons.concurrent.WithProfilingThreadPoolExecutor;

public class PaymentExecutors {

    private static final long TIMEOUT_EXECUTOR_SEC = 3L;

    private static final int DEFAULT_MIN_PLUGIN_THREADS = 5;

    private static final String PLUGIN_THREAD_PREFIX = "Plugin-th-";
    private static final String PAYMENT_PLUGIN_TH_GROUP_NAME = "pay-plugin-grp";

    public static final String JANITOR_EXECUTOR_NAMED = "JanitorExecutor";
    public static final String PLUGIN_EXECUTOR_NAMED = "PluginExecutor";

    private final PaymentConfig paymentConfig;

    private volatile ThreadPoolExecutor pluginExecutorService;
    private volatile ScheduledExecutorService janitorExecutorService;

    @Inject
    public PaymentExecutors(PaymentConfig paymentConfig) {
        this.paymentConfig = paymentConfig;

    }

    public void initialize() {
        this.pluginExecutorService = createPluginExecutorService();
        this.pluginExecutorService.prestartAllCoreThreads();
        this.janitorExecutorService = createJanitorExecutorService();
    }


    public void stop() throws InterruptedException {
        pluginExecutorService.shutdownNow();
        janitorExecutorService.shutdownNow();

        pluginExecutorService.awaitTermination(TIMEOUT_EXECUTOR_SEC, TimeUnit.SECONDS);
        pluginExecutorService = null;

        janitorExecutorService.awaitTermination(TIMEOUT_EXECUTOR_SEC, TimeUnit.SECONDS);
        janitorExecutorService = null;
    }

    public ExecutorService getPluginExecutorService() {
        return pluginExecutorService;
    }

    public ScheduledExecutorService getJanitorExecutorService() {
        return janitorExecutorService;
    }

    private ThreadPoolExecutor createPluginExecutorService() {
        final int minThreadNb = DEFAULT_MIN_PLUGIN_THREADS < paymentConfig.getPaymentPluginThreadNb() ? DEFAULT_MIN_PLUGIN_THREADS : paymentConfig.getPaymentPluginThreadNb();
        return new WithProfilingThreadPoolExecutor(minThreadNb,
                                                   paymentConfig.getPaymentPluginThreadNb(),
                                                   10,
                                                   TimeUnit.MINUTES,
                                                   new LinkedBlockingQueue<Runnable>(),
                                                   new ThreadFactory() {

                                                       @Override
                                                       public Thread newThread(final Runnable r) {
                                                           final Thread th = new Thread(new ThreadGroup(PAYMENT_PLUGIN_TH_GROUP_NAME), r);
                                                           th.setName(PLUGIN_THREAD_PREFIX + th.getId());
                                                           return th;
                                                       }
                                                   });

    }

    private ScheduledExecutorService createJanitorExecutorService() {
        return Executors.newSingleThreadScheduledExecutor("PaymentJanitor");
    }
}
