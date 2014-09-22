/*
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

package org.killbill.billing.payment.core.janitor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.killbill.billing.payment.core.sm.PluginControlledPaymentAutomatonRunner;
import org.killbill.billing.payment.core.sm.RetryStateMachineHelper;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes care of incomplete payment/transactions.
 */
public class Janitor {

    private final static Logger log = LoggerFactory.getLogger(Janitor.class);

    private final static int TERMINATION_TIMEOUT_SEC = 5;

    private final ScheduledExecutorService janitorExecutor;
    private final PaymentConfig paymentConfig;
    private final PendingTransactionTask pendingTransactionTask;
    private final AttemptCompletionTask attemptCompletionTask;
    private final ErroredPaymentTask erroredPaymentCompletionTask;

    private volatile boolean isStopped;

    @Inject
    public Janitor(final AccountInternalApi accountInternalApi,
                   final PaymentDao paymentDao,
                   final PaymentConfig paymentConfig,
                   final Clock clock,
                   final NonEntityDao nonEntityDao,
                   final InternalCallContextFactory internalCallContextFactory,
                   final PluginControlledPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner,
                   @Named(PaymentModule.JANITOR_EXECUTOR_NAMED) final ScheduledExecutorService janitorExecutor,
                   final PaymentStateMachineHelper paymentSMHelper,
                   final RetryStateMachineHelper retrySMHelper,
                   final CacheControllerDispatcher controllerDispatcher,
                   final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry) {
        this.janitorExecutor = janitorExecutor;
        this.paymentConfig = paymentConfig;
        this.pendingTransactionTask = new PendingTransactionTask(this, internalCallContextFactory, paymentConfig, nonEntityDao, paymentDao, clock, paymentSMHelper, retrySMHelper,
                                                                 controllerDispatcher, accountInternalApi, pluginControlledPaymentAutomatonRunner, pluginRegistry);
        this.attemptCompletionTask = new AttemptCompletionTask(this, internalCallContextFactory, paymentConfig, nonEntityDao, paymentDao, clock, paymentSMHelper, retrySMHelper,
                                                               controllerDispatcher, accountInternalApi, pluginControlledPaymentAutomatonRunner, pluginRegistry);
        this.erroredPaymentCompletionTask = new ErroredPaymentTask(this, internalCallContextFactory, paymentConfig, nonEntityDao, paymentDao, clock, paymentSMHelper, retrySMHelper,
                                                               controllerDispatcher, accountInternalApi, pluginControlledPaymentAutomatonRunner, pluginRegistry);
        this.isStopped = false;
    }

    public void start() {
        if (isStopped) {
            log.warn("Janitor is not a restartable service, and was already started, aborting");
            return;
        }

        // Start task for removing old pending payments.
        final TimeUnit pendingRateUnit = paymentConfig.getJanitorRunningRate().getUnit();
        final long pendingPeriod = paymentConfig.getJanitorRunningRate().getPeriod();
        janitorExecutor.scheduleAtFixedRate(pendingTransactionTask, pendingPeriod, pendingPeriod, pendingRateUnit);

        // Start task for completing incomplete payment attempts
        final TimeUnit attemptCompletionRateUnit = paymentConfig.getJanitorRunningRate().getUnit();
        final long attemptCompletionPeriod = paymentConfig.getJanitorRunningRate().getPeriod();
        janitorExecutor.scheduleAtFixedRate(attemptCompletionTask, attemptCompletionPeriod, attemptCompletionPeriod, attemptCompletionRateUnit);

        // Start task for completing incomplete payment attempts
        final TimeUnit erroredCompletionRateUnit = paymentConfig.getJanitorRunningRate().getUnit();
        final long erroredCompletionPeriod = paymentConfig.getJanitorRunningRate().getPeriod();
        janitorExecutor.scheduleAtFixedRate(erroredPaymentCompletionTask, erroredCompletionPeriod, erroredCompletionPeriod, erroredCompletionRateUnit);
    }

    public void stop() {
        if (isStopped) {
            log.warn("Janitor is already in a stopped state");
            return;
        }
        try {
            /* Previously submitted tasks will be executed with shutdown(); when task executes as a result of shutdown being called
             * or because it was already in its execution loop, it will check for the volatile boolean isStopped flag and
             * return immediately.
             * Then, awaitTermination with a timeout is required to ensure tasks completed.
             */
            janitorExecutor.shutdown();
            boolean success = janitorExecutor.awaitTermination(TERMINATION_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!success) {
                log.warn("Janitor failed to complete termination within " + TERMINATION_TIMEOUT_SEC + "sec");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Janitor stop sequence got interrupted");
        } finally {
            isStopped = true;
        }
    }

    public boolean isStopped() {
        return isStopped;
    }
}
