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

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContext;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.config.PaymentConfig;
import com.ning.billing.lifecycle.KillbillService;
import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.lifecycle.LifecycleHandlerType.LifecycleLevel;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.payment.api.PaymentStatus;

import com.ning.billing.util.notificationq.NotificationKey;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueAlreadyExists;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;

public class RetryService implements KillbillService {
    public static final String SERVICE_NAME = "retry-service";
    public static final String QUEUE_NAME = "retry-events";

    private final Clock clock;
    private final NotificationQueueService notificationQueueService;
    private final PaymentConfig config;
    private final PaymentApi paymentApi;
    private NotificationQueue retryQueue;

    @Inject
    public RetryService(Clock clock,
                        NotificationQueueService notificationQueueService,
                        PaymentConfig config,
                        PaymentApi paymentApi) {
        this.clock = clock;
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
                CallContext context = new DefaultCallContext("RetryService", CallOrigin.INTERNAL, UserType.SYSTEM, clock);
                retry(notificationKey, context);
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

        if (retryQueue != null) {
            retryQueue.recordFutureNotification(timeOfRetry, key);
        }
    }

    private void retry(String paymentAttemptId, CallContext context) {
        PaymentInfoEvent paymentInfo = paymentApi.getPaymentInfoForPaymentAttemptId(paymentAttemptId);

        if (paymentInfo != null && PaymentStatus.Processed.equals(PaymentStatus.valueOf(paymentInfo.getStatus()))) {
            // update payment attempt with success and notify invoice api of payment
            System.out.println("Found processed payment");
        }
        else {
            System.out.println("Creating payment for payment attempt " + paymentAttemptId);
            paymentApi.createPaymentForPaymentAttempt(UUID.fromString(paymentAttemptId), context);
        }
    }
}
