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

package org.killbill.billing.payment.core.janitor;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.util.config.PaymentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes care of incomplete payment/transactions.
 */
public class Janitor {

    private static final Logger log = LoggerFactory.getLogger(Janitor.class);

    private static final int TERMINATION_TIMEOUT_SEC = 5;

    private final ScheduledExecutorService janitorExecutor;
    private final PaymentConfig paymentConfig;
    private final IncompletePaymentAttemptTask incompletePaymentAttemptTask;
    private final IncompletePaymentTransactionTask incompletePaymentTransactionTask;

    private volatile boolean isStopped;

    @Inject
    public Janitor(final PaymentConfig paymentConfig,
                   @Named(PaymentModule.JANITOR_EXECUTOR_NAMED) final ScheduledExecutorService janitorExecutor,
                   final IncompletePaymentAttemptTask incompletePaymentAttemptTask,
                   final IncompletePaymentTransactionTask incompletePaymentTransactionTask) {
        this.janitorExecutor = janitorExecutor;
        this.paymentConfig = paymentConfig;
        this.incompletePaymentAttemptTask = incompletePaymentAttemptTask;
        this.incompletePaymentTransactionTask = incompletePaymentTransactionTask;
        this.isStopped = false;
    }

    public void start() {
        if (isStopped) {
            log.warn("Janitor is not a restartable service, and was already started, aborting");
            return;
        }

        // Start task for completing incomplete payment attempts
        final TimeUnit attemptCompletionRateUnit = paymentConfig.getJanitorRunningRate().getUnit();
        final long attemptCompletionPeriod = paymentConfig.getJanitorRunningRate().getPeriod();
        janitorExecutor.scheduleAtFixedRate(incompletePaymentAttemptTask, attemptCompletionPeriod, attemptCompletionPeriod, attemptCompletionRateUnit);

        // Start task for completing incomplete payment attempts
        final TimeUnit erroredCompletionRateUnit = paymentConfig.getJanitorRunningRate().getUnit();
        final long erroredCompletionPeriod = paymentConfig.getJanitorRunningRate().getPeriod();
        janitorExecutor.scheduleAtFixedRate(incompletePaymentTransactionTask, erroredCompletionPeriod, erroredCompletionPeriod, erroredCompletionRateUnit);
    }

    public void stop() {
        if (isStopped) {
            log.warn("Janitor is already in a stopped state");
            return;
        }

        incompletePaymentAttemptTask.stop();
        incompletePaymentTransactionTask.stop();

        try {
            /* Previously submitted tasks will be executed with shutdown(); when task executes as a result of shutdown being called
             * or because it was already in its execution loop, it will check for the volatile boolean isStopped flag and
             * return immediately.
             * Then, awaitTermination with a timeout is required to ensure tasks completed.
             */
            janitorExecutor.shutdown();
            final boolean success = janitorExecutor.awaitTermination(TERMINATION_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!success) {
                log.warn("Janitor failed to complete termination within " + TERMINATION_TIMEOUT_SEC + "sec");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Janitor stop sequence got interrupted");
        } finally {
            isStopped = true;
        }
    }
}
