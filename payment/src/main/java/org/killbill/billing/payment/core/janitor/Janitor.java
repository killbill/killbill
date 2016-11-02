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

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.events.PaymentInternalEvent;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.core.PaymentExecutors;
import org.killbill.billing.payment.core.sm.PaymentControlStateMachineHelper;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.killbill.billing.payment.core.sm.PluginControlPaymentAutomatonRunner;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.glue.DefaultPaymentService;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes care of incomplete payment/transactions.
 */
public class Janitor {

    private static final Logger log = LoggerFactory.getLogger(Janitor.class);

    private static final int TERMINATION_TIMEOUT_SEC = 5;
    public static final String QUEUE_NAME = "janitor";

    private final NotificationQueueService notificationQueueService;
    private final PaymentConfig paymentConfig;
    private final PaymentExecutors paymentExecutors;
    private final Clock clock;
    private final PaymentDao paymentDao;
    private final InternalCallContextFactory internalCallContextFactory;
    private final PaymentStateMachineHelper paymentStateMachineHelper;
    private final PaymentControlStateMachineHelper retrySMHelper;
    private final AccountInternalApi accountInternalApi;
    private final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry;
    private final GlobalLocker locker;
    private final PluginControlPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner;




    private IncompletePaymentAttemptTask incompletePaymentAttemptTask;
    private IncompletePaymentTransactionTask incompletePaymentTransactionTask;
    private NotificationQueue janitorQueue;
    private ScheduledExecutorService janitorExecutor;

    private volatile boolean isStopped;

    @Inject
    public Janitor(final InternalCallContextFactory internalCallContextFactory,
                   final PaymentDao paymentDao,
                   final Clock clock,
                   final PaymentStateMachineHelper paymentStateMachineHelper,
                   final PaymentControlStateMachineHelper retrySMHelper,
                   final AccountInternalApi accountInternalApi,
                   final PluginControlPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner,
                   final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                   final GlobalLocker locker,
                   final PaymentConfig paymentConfig,
                   final NotificationQueueService notificationQueueService,
                   final PaymentExecutors paymentExecutors) {
        this.notificationQueueService = notificationQueueService;
        this.paymentExecutors = paymentExecutors;
        this.paymentConfig = paymentConfig;
        this.internalCallContextFactory = internalCallContextFactory;
        this.paymentDao = paymentDao;
        this.clock = clock;
        this.pluginControlledPaymentAutomatonRunner = pluginControlledPaymentAutomatonRunner;
        this.paymentStateMachineHelper = paymentStateMachineHelper;
        this.retrySMHelper = retrySMHelper;
        this.accountInternalApi = accountInternalApi;
        this.pluginRegistry = pluginRegistry;
        this.locker = locker;

    }


    public void initialize() throws NotificationQueueAlreadyExists {
        janitorQueue = notificationQueueService.createNotificationQueue(DefaultPaymentService.SERVICE_NAME,
                                                                        QUEUE_NAME,
                                                                        new NotificationQueueHandler() {
                                                                            @Override
                                                                            public void handleReadyNotification(final NotificationEvent notificationKey, final DateTime eventDateTime, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
                                                                                if (!(notificationKey instanceof JanitorNotificationKey)) {
                                                                                    log.error("Janitor service received an unexpected event className='{}", notificationKey.getClass());
                                                                                    return;

                                                                                }
                                                                                final JanitorNotificationKey janitorKey = (JanitorNotificationKey) notificationKey;
                                                                                if (janitorKey.getTaskName().equals(incompletePaymentTransactionTask.getClass().toString())) {
                                                                                    incompletePaymentTransactionTask.processNotification(janitorKey, userToken, accountRecordId, tenantRecordId);
                                                                                }
                                                                            }
                                                                        }
                                                                       );

        this.incompletePaymentAttemptTask = new IncompletePaymentAttemptTask(internalCallContextFactory,
                                                                             paymentConfig,
                                                                             paymentDao,
                                                                             clock,
                                                                             paymentStateMachineHelper,
                                                                             retrySMHelper,
                                                                             accountInternalApi,
                                                                             pluginControlledPaymentAutomatonRunner,
                                                                             pluginRegistry,
                                                                             locker);

        this.incompletePaymentTransactionTask = new IncompletePaymentTransactionTask(internalCallContextFactory,
                                                                                     paymentConfig,
                                                                                     paymentDao,
                                                                                     clock,
                                                                                     paymentStateMachineHelper,
                                                                                     retrySMHelper,
                                                                                     accountInternalApi,
                                                                                     pluginRegistry,
                                                                                     locker);


        incompletePaymentTransactionTask.attachJanitorQueue(janitorQueue);
        incompletePaymentAttemptTask.attachJanitorQueue(janitorQueue);
    }

    public void start() {

        this.isStopped = false;

        janitorExecutor = paymentExecutors.getJanitorExecutorService();

        janitorQueue.startQueue();

        // Start task for completing incomplete payment attempts
        final TimeUnit attemptCompletionRateUnit = paymentConfig.getJanitorRunningRate().getUnit();
        final long attemptCompletionPeriod = paymentConfig.getJanitorRunningRate().getPeriod();
        janitorExecutor.scheduleAtFixedRate(incompletePaymentAttemptTask, attemptCompletionPeriod, attemptCompletionPeriod, attemptCompletionRateUnit);

        // Start task for completing incomplete payment attempts
        final TimeUnit erroredCompletionRateUnit = paymentConfig.getJanitorRunningRate().getUnit();
        final long erroredCompletionPeriod = paymentConfig.getJanitorRunningRate().getPeriod();
        janitorExecutor.scheduleAtFixedRate(incompletePaymentTransactionTask, erroredCompletionPeriod, erroredCompletionPeriod, erroredCompletionRateUnit);
    }

    public void stop() throws NoSuchNotificationQueue {
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

            if (janitorQueue != null) {
                janitorQueue.stopQueue();
                notificationQueueService.deleteNotificationQueue(DefaultPaymentService.SERVICE_NAME, QUEUE_NAME);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Janitor stop sequence got interrupted");
        } finally {
            isStopped = true;
        }
    }

    public void processPaymentEvent(final PaymentInternalEvent event) {
        incompletePaymentAttemptTask.processPaymentEvent(event, janitorQueue);
        incompletePaymentTransactionTask.processPaymentEvent(event, janitorQueue);
    }
}
