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

import java.util.UUID;

import org.joda.time.DateTime;
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
    private final Clock clock;
    private final PaymentConfig config;
    
    private NotificationQueue retryQueue;
    
    public BaseRetryService(final NotificationQueueService notificationQueueService,
            final Clock clock, final PaymentConfig config) {
        this.notificationQueueService = notificationQueueService;
        this.clock = clock;
        this.config = config;
    }
   

    @Override
    public void initialize(final String svcName) throws NotificationQueueAlreadyExists {
        retryQueue = notificationQueueService.createNotificationQueue(svcName, getQueueName(), new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(String notificationKey, DateTime eventDateTime) {
                retry(UUID.fromString(notificationKey));
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
    
    public abstract String getQueueName();
    
    
    public abstract static class RetryServiceScheduler {
        
        private final NotificationQueueService notificationQueueService;
        
        @Inject
        public RetryServiceScheduler(final NotificationQueueService notificationQueueService) {
            this.notificationQueueService = notificationQueueService;
        }
    
        
        public void scheduleRetry(final UUID paymentId, final DateTime timeOfRetry) {

            try {
                NotificationQueue retryQueue = notificationQueueService.getNotificationQueue(DefaultPaymentService.SERVICE_NAME, getQueueName());
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
                log.error(String.format("Failed to retrieve notification queue %s:%s", DefaultPaymentService.SERVICE_NAME, getQueueName()));
            }
        }
        public abstract String getQueueName();
    }
}
