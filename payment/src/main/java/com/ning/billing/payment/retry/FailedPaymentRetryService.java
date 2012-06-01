/*
 * Copyright 2010-2012 Ning, Inc.
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

import java.util.List;
import java.util.UUID;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContext;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.config.PaymentConfig;
import com.ning.billing.payment.core.PaymentProcessor;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.glue.DefaultPaymentService;


import com.ning.billing.util.notificationq.NotificationKey;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NoSuchNotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueAlreadyExists;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;

public class FailedPaymentRetryService implements RetryService {
    
    private static final Logger log = LoggerFactory.getLogger(FailedPaymentRetryService.class);
    
    public static final String QUEUE_NAME = "failed-retry";

    private final Clock clock;
    private final NotificationQueueService notificationQueueService;
    private final PaymentConfig config;
    private final PaymentProcessor paymentProcessor;
    
    private NotificationQueue retryQueue;
    
    @Inject
    public FailedPaymentRetryService(final AccountUserApi accountUserApi,
            final Clock clock,
            final NotificationQueueService notificationQueueService,
            final PaymentConfig config,
            final PaymentProcessor paymentProcessor,
            final PaymentDao paymentDao) {
        this.clock = clock;
        this.notificationQueueService = notificationQueueService;
        this.config = config;
        this.paymentProcessor = paymentProcessor;
    }

    @Override
    public void initialize(final String svcName) throws NotificationQueueAlreadyExists {
        retryQueue = notificationQueueService.createNotificationQueue(svcName, QUEUE_NAME, new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(String notificationKey, DateTime eventDateTime) {
                CallContext context = new DefaultCallContext("FailedRetryService", CallOrigin.INTERNAL, UserType.SYSTEM, clock);
                retry(UUID.fromString(notificationKey), context);
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

    

    private void retry(final UUID paymentId, final CallContext context) {
        paymentProcessor.retryPayment(paymentId);
    }
    
    
    public static class FailedPaymentRetryServiceScheduler {
        
        private final NotificationQueueService notificationQueueService;
        
        @Inject
        public FailedPaymentRetryServiceScheduler(final NotificationQueueService notificationQueueService) {
            this.notificationQueueService = notificationQueueService;
        }
    
        
        public void scheduleRetry(final UUID paymentId, final DateTime timeOfRetry) {

            try {
                NotificationQueue retryQueue = notificationQueueService.getNotificationQueue(DefaultPaymentService.SERVICE_NAME, QUEUE_NAME);
                NotificationKey key = new NotificationKey() {
                    @Override
                    public String toString() {
                        return paymentId.toString();
                    }
                };
                if (retryQueue != null) {
                    retryQueue.recordFutureNotification(timeOfRetry, key);
                }
            } catch (NoSuchNotificationQueue e) {
                log.error(String.format("Failed to retrieve notification queue %s:%s", DefaultPaymentService.SERVICE_NAME, QUEUE_NAME));
            }
        }
    }
}
