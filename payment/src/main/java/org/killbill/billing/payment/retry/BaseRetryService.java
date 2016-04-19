/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.payment.retry;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.payment.glue.DefaultPaymentService;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueAlreadyExists;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public abstract class BaseRetryService implements RetryService {

    private static final Logger log = LoggerFactory.getLogger(BaseRetryService.class);

    private final NotificationQueueService notificationQueueService;
    private final InternalCallContextFactory internalCallContextFactory;
    private final String paymentRetryService;

    private NotificationQueue retryQueue;

    public BaseRetryService(final NotificationQueueService notificationQueueService,
                            final InternalCallContextFactory internalCallContextFactory) {
        this.notificationQueueService = notificationQueueService;
        this.internalCallContextFactory = internalCallContextFactory;
        this.paymentRetryService = DefaultPaymentService.SERVICE_NAME + "-" + getQueueName();
    }

    @Override
    public void initialize() throws NotificationQueueAlreadyExists {
        retryQueue = notificationQueueService.createNotificationQueue(DefaultPaymentService.SERVICE_NAME,
                                                                      getQueueName(),
                                                                      new NotificationQueueHandler() {
                                                                          @Override
                                                                          public void handleReadyNotification(final NotificationEvent notificationKey, final DateTime eventDateTime, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
                                                                              if (!(notificationKey instanceof PaymentRetryNotificationKey)) {
                                                                                  log.error("Payment service got an unexpected notification type {}", notificationKey.getClass().getName());
                                                                                  return;
                                                                              }
                                                                              final PaymentRetryNotificationKey key = (PaymentRetryNotificationKey) notificationKey;
                                                                              final InternalCallContext callContext = internalCallContextFactory.createInternalCallContext(tenantRecordId, accountRecordId, paymentRetryService, CallOrigin.INTERNAL, UserType.SYSTEM, userToken);
                                                                              retryPaymentTransaction(key.getAttemptId(), key.getPaymentControlPluginNames(), callContext);
                                                                          }
                                                                      }
                                                                     );
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

        public boolean scheduleRetry(final ObjectType objectType, final UUID objectId, final UUID attemptId, final Long tenantRecordId, final List<String> paymentControlPluginNames, final DateTime timeOfRetry) {
            return scheduleRetryInternal(objectType, objectId, attemptId, tenantRecordId, paymentControlPluginNames, timeOfRetry, null);
        }

        private boolean scheduleRetryInternal(final ObjectType objectType, final UUID objectId, final UUID attemptId, final Long tenantRecordId, final List<String> paymentControlPluginNames, final DateTime timeOfRetry, final EntitySqlDaoWrapperFactory transactionalDao) {
            final InternalCallContext context = createCallContextFromPaymentId(objectType, objectId, tenantRecordId);

            try {
                final NotificationQueue retryQueue = notificationQueueService.getNotificationQueue(DefaultPaymentService.SERVICE_NAME, getQueueName());
                final NotificationEvent key = new PaymentRetryNotificationKey(attemptId, paymentControlPluginNames);
                if (retryQueue != null) {
                    log.debug("Scheduling retry timeOfRetry={}, key={}", timeOfRetry, key);
                    if (transactionalDao == null) {
                        retryQueue.recordFutureNotification(timeOfRetry, key, context.getUserToken(), context.getAccountRecordId(), context.getTenantRecordId());
                    } else {
                        retryQueue.recordFutureNotificationFromTransaction(transactionalDao.getHandle().getConnection(), timeOfRetry, key, context.getUserToken(), context.getAccountRecordId(), context.getTenantRecordId());
                    }
                }
            } catch (final NoSuchNotificationQueue e) {
                log.error("Failed to retrieve notification queue='{}', service='{}'", getQueueName(), DefaultPaymentService.SERVICE_NAME);
                return false;
            } catch (final IOException e) {
                log.error("Failed to serialize notificationQueue event for objectId='{}'", objectId);
                return false;
            }
            return true;
        }

        protected InternalCallContext createCallContextFromPaymentId(final ObjectType objectType, final UUID objectId, final Long tenantRecordId) {
            final String paymentRetryService = DefaultPaymentService.SERVICE_NAME + "-" + getQueueName();
            return internalCallContextFactory.createInternalCallContext(objectId, objectType, paymentRetryService, CallOrigin.INTERNAL, UserType.SYSTEM, null, tenantRecordId);
        }

        public abstract String getQueueName();
    }
}
