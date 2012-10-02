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

import com.ning.billing.config.PaymentConfig;
import com.ning.billing.payment.glue.DefaultPaymentService;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.notificationq.NotificationKey;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NoSuchNotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueAlreadyExists;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;

import com.google.inject.Inject;

public abstract class BaseRetryService implements RetryService {

    private static final Logger log = LoggerFactory.getLogger(BaseRetryService.class);
    private static final String PAYMENT_RETRY_SERVICE = "PaymentRetryService";

    private final NotificationQueueService notificationQueueService;
    private final PaymentConfig config;
    private final InternalCallContextFactory internalCallContextFactory;

    private NotificationQueue retryQueue;

    public BaseRetryService(final NotificationQueueService notificationQueueService,
                            final PaymentConfig config,
                            final InternalCallContextFactory internalCallContextFactory) {
        this.notificationQueueService = notificationQueueService;
        this.config = config;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public void initialize(final String svcName) throws NotificationQueueAlreadyExists {
        retryQueue = notificationQueueService.createNotificationQueue(svcName,
                                                                      getQueueName(),
                                                                      new NotificationQueueHandler() {
                                                                          @Override
                                                                          public void handleReadyNotification(final NotificationKey notificationKey, final DateTime eventDateTime) {
                                                                              if (!(notificationKey instanceof PaymentRetryNotificationKey)) {
                                                                                  log.error("Payment service got an unexpected notification type {}", notificationKey.getClass().getName());
                                                                                  return;
                                                                              }
                                                                              final PaymentRetryNotificationKey key = (PaymentRetryNotificationKey) notificationKey;
                                                                              final InternalCallContext callContext =  internalCallContextFactory.createInternalCallContext(PAYMENT_RETRY_SERVICE, CallOrigin.INTERNAL, UserType.SYSTEM, null);
                                                                              retry(key.getUuidKey(), callContext);
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
        private final InternalCallContextFactory internalCallContextFactory;

        @Inject
        public RetryServiceScheduler(final NotificationQueueService notificationQueueService,
                                     final InternalCallContextFactory internalCallContextFactory) {
            this.notificationQueueService = notificationQueueService;
            this.internalCallContextFactory = internalCallContextFactory;
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
            final InternalCallContext context = createCallContext();

            try {
                final NotificationQueue retryQueue = notificationQueueService.getNotificationQueue(DefaultPaymentService.SERVICE_NAME, getQueueName());
                final NotificationKey key = new PaymentRetryNotificationKey(paymentId);
                if (retryQueue != null) {
                    if (transactionalDao == null) {
                        retryQueue.recordFutureNotification(timeOfRetry, null, key, context);
                    } else {
                        retryQueue.recordFutureNotificationFromTransaction(transactionalDao, timeOfRetry, null, key, context);
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

        protected InternalCallContext createCallContext() {
            return internalCallContextFactory.createInternalCallContext(PAYMENT_RETRY_SERVICE, CallOrigin.INTERNAL, UserType.SYSTEM, null);
        }

        public abstract String getQueueName();
    }
}
