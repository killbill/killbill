/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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
package com.ning.billing.payment.retry;

import java.io.IOException;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.config.PaymentConfig;
import com.ning.billing.payment.glue.DefaultPaymentService;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.notificationq.NotificationKey;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NoSuchNotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueAlreadyExists;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;

public abstract class BaseRetryService implements RetryService {

    private static final Logger log = LoggerFactory.getLogger(BaseRetryService.class);

    private final NotificationQueueService notificationQueueService;
    private final PaymentConfig config;

    private NotificationQueue retryQueue;

    public BaseRetryService(final NotificationQueueService notificationQueueService,
            final Clock clock, final PaymentConfig config) {
        this.notificationQueueService = notificationQueueService;
        final Clock clock1 = clock;
        this.config = config;
    }


    @Override
    public void initialize(final String svcName) throws NotificationQueueAlreadyExists {
        retryQueue = notificationQueueService.createNotificationQueue(svcName, getQueueName(), new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(final NotificationKey notificationKey, final DateTime eventDateTime) {
                if (! (notificationKey instanceof PaymentRetryNotificationKey)) {
                    log.error("Payment service got an unexpected notification type {}", notificationKey.getClass().getName());
                    return;
                }
                final PaymentRetryNotificationKey key = (PaymentRetryNotificationKey) notificationKey;
                retry(key.getUuidKey());
            }
        },
        config);
    }

    @Override
    public void start() {
        retryQueue.startQueue();
    }

    @Override
    public void stop() throws NoSuchNotificationQueue {
        if (retryQueue != null) {
            retryQueue.stopQueue();
            notificationQueueService.deleteNotificationQueue(retryQueue.getServiceName(), retryQueue.getQueueName());
        }
    }

    @Override
    public abstract String getQueueName();


    public abstract static class RetryServiceScheduler {

        private final NotificationQueueService notificationQueueService;

        @Inject
        public RetryServiceScheduler(final NotificationQueueService notificationQueueService) {
            this.notificationQueueService = notificationQueueService;
        }

        public boolean scheduleRetryFromTransaction(final UUID paymentId, final DateTime timeOfRetry, final Transmogrifier transactionalDao) {
            return scheduleRetryInternal(paymentId, timeOfRetry, transactionalDao);
        }

        public boolean scheduleRetry(final UUID paymentId, final DateTime timeOfRetry) {
            return scheduleRetryInternal(paymentId, timeOfRetry, null);
        }

        // STEPH TimedoutPaymentRetryServiceScheduler
        public void cancelAllScheduleRetryForKey(final UUID paymentId) {
            /*
            try {
                NotificationQueue retryQueue = notificationQueueService.getNotificationQueue(DefaultPaymentService.SERVICE_NAME, getQueueName());
                NotificationKey key = new NotificationKey() {
                    @Override
                    public String toString() {
                        return paymentId.toString();
                    }
                };
                retryQueue.removeNotificationsByKey(key);
            } catch (NoSuchNotificationQueue e) {
                log.error(String.format("Failed to retrieve notification queue %s:%s", DefaultPaymentService.SERVICE_NAME, getQueueName()));
            }
             */
        }

        private boolean scheduleRetryInternal(final UUID paymentId, final DateTime timeOfRetry, final Transmogrifier transactionalDao) {

            try {
                final NotificationQueue retryQueue = notificationQueueService.getNotificationQueue(DefaultPaymentService.SERVICE_NAME, getQueueName());
                final NotificationKey key = new PaymentRetryNotificationKey(paymentId);
                if (retryQueue != null) {
                    if (transactionalDao == null) {
                        retryQueue.recordFutureNotification(timeOfRetry, null, key);
                    } else {
                        retryQueue.recordFutureNotificationFromTransaction(transactionalDao, timeOfRetry, null, key);
                    }
                }
            } catch (NoSuchNotificationQueue e) {
                log.error(String.format("Failed to retrieve notification queue %s:%s", DefaultPaymentService.SERVICE_NAME, getQueueName()));
                return false;
            } catch (IOException e) {
                log.error(String.format("Failed to serialize notificationQueue event for paymentId %s", paymentId));
                return false;
            }
            return true;
        }

        public abstract String getQueueName();
    }
}
