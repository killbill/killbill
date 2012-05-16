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
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContext;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueAlreadyExists;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueHandler;

public class TimedoutPaymentRetryService implements RetryService {

    
    
  private static final Logger log = LoggerFactory.getLogger(TimedoutPaymentRetryService.class);
    
    public static final String QUEUE_NAME = "timedout-retry";

    private final Clock clock;
    private final NotificationQueueService notificationQueueService;
    private final PaymentConfig config;
    private final PaymentApi paymentApi;
    private NotificationQueue retryQueue;

    @Inject
    public TimedoutPaymentRetryService(Clock clock,
                        NotificationQueueService notificationQueueService,
                        PaymentConfig config,
                        PaymentApi paymentApi) {
        this.clock = clock;
        this.notificationQueueService = notificationQueueService;
        this.paymentApi = paymentApi;
        this.config = config;
    }

    @Override
    public void initialize(final String svcName) throws NotificationQueueAlreadyExists {
        retryQueue = notificationQueueService.createNotificationQueue(svcName, QUEUE_NAME, new NotificationQueueHandler() {
            @Override
            public void handleReadyNotification(String notificationKey, DateTime eventDateTime) {
                CallContext context = new DefaultCallContext("TimedoutRetryService", CallOrigin.INTERNAL, UserType.SYSTEM, clock);
                retry(notificationKey, context);
            }
        },
        config);
    }

    @Override
    public void start() {
        retryQueue.startQueue();
    }

    @Override
    public void stop() {
        if (retryQueue != null) {
            retryQueue.stopQueue();
         }
    }

    public void scheduleRetry(PaymentAttempt paymentAttempt, DateTime timeOfRetry) {

        /*
        final String id = paymentAttempt.getPaymentAttemptId().toString();
=======
        final String id = paymentAttempt.getId().toString();
>>>>>>> origin/integration:payment/src/main/java/com/ning/billing/payment/RetryService.java

        NotificationKey key = new NotificationKey() {
            @Override
            public String toString() {
                return id;
            }
        };

        if (retryQueue != null) {
            retryQueue.recordFutureNotification(timeOfRetry, key);
        }
        */
    }

    private void retry(String paymentAttemptId, CallContext context) {
        
        /*
        try {
            PaymentInfoEvent paymentInfo = paymentApi.getPaymentInfoForPaymentAttemptId(paymentAttemptId);
            if (paymentInfo != null && PaymentStatus.Processed.equals(PaymentStatus.valueOf(paymentInfo.getStatus()))) {
                return;
            }
            paymentApi.createPaymentForPaymentAttempt(UUID.fromString(paymentAttemptId), context);
        } catch (PaymentApiException e) {
            log.error(String.format("Failed to retry payment for %s"), e);
        }
        */
    }
}
