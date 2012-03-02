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

package com.ning.billing.payment;

import java.util.UUID;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.lifecycle.KillbillService;
import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.lifecycle.LifecycleHandlerType.LifecycleLevel;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentInfo;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.payment.setup.PaymentConfig;
import com.ning.billing.util.notificationq.NotificationKey;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueAlreadyExists;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;

public class RetryService implements KillbillService {
    public static final String SERVICE_NAME = "retry-service";
    public static final String QUEUE_NAME = "retry-events";

    private final NotificationQueueService notificationQueueService;
    private final PaymentConfig config;
    private final PaymentApi paymentApi;
    private NotificationQueue retryQueue;

    @Inject
    public RetryService(NotificationQueueService notificationQueueService,
                        PaymentConfig config,
                        PaymentApi paymentApi) {
        this.notificationQueueService = notificationQueueService;
        this.paymentApi = paymentApi;
        this.config = config;
    }

    @Override
    public String getName() {
        return SERVICE_NAME;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public void initialize() throws NotificationQueueAlreadyExists {
        retryQueue = notificationQueueService.createNotificationQueue(SERVICE_NAME, QUEUE_NAME, new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(String notificationKey, DateTime eventDateTime) {
                retry(notificationKey);
            }
        },
        config);
    }

    @LifecycleHandlerType(LifecycleLevel.START_SERVICE)
    public void start() {
        retryQueue.startQueue();
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public void stop() {
        if (retryQueue != null) {
            retryQueue.stopQueue();
         }
    }

    public void scheduleRetry(PaymentAttempt paymentAttempt, DateTime timeOfRetry) {
        final String id = paymentAttempt.getPaymentAttemptId().toString();

        NotificationKey key = new NotificationKey() {
            @Override
            public String toString() {
                return id;
            }
        };
        retryQueue.recordFutureNotification(timeOfRetry, key);
    }

    private void retry(String paymentAttemptId) {
        PaymentInfo paymentInfo = paymentApi.getPaymentInfoForPaymentAttemptId(paymentAttemptId);

        if (paymentInfo == null || !PaymentStatus.Processed.equals(PaymentStatus.valueOf(paymentInfo.getStatus()))) {
            paymentApi.createPaymentForPaymentAttempt(UUID.fromString(paymentAttemptId));
        }
    }
}
