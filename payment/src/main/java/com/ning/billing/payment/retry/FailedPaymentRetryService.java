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
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.config.PaymentConfig;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.payment.api.PaymentStatus;

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
    private final PaymentApi paymentApi;
    private final AccountUserApi accountUserApi;
    
    private NotificationQueue retryQueue;
    
    @Inject
    public FailedPaymentRetryService(final AccountUserApi accountUserApi,
            final Clock clock,
            final NotificationQueueService notificationQueueService,
            final PaymentConfig config,
            final PaymentApi paymentApi) {
        this.accountUserApi = accountUserApi;
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

    public void scheduleRetry(PaymentAttempt paymentAttempt, DateTime timeOfRetry) {
        final String id = paymentAttempt.getId().toString();

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

    private void retry(UUID paymentAttemptId, CallContext context) {
        try {
            PaymentInfoEvent paymentInfo = paymentApi.getPaymentInfoForPaymentAttemptId(paymentAttemptId);
            if (paymentInfo == null) {
                log.error(String.format("Failed to retry payment for paymentId %s: no such PaymentInfo", paymentAttemptId));
                return;
            }
            if (paymentInfo != null && PaymentStatus.Processed.equals(PaymentStatus.valueOf(paymentInfo.getStatus()))) {
                return;
            }
            
            Account account = accountUserApi.getAccountById(paymentInfo.getAccountId());
            paymentApi.createPaymentForPaymentAttempt(account.getExternalKey(), paymentAttemptId, context);
        } catch (PaymentApiException e) {
            log.error(String.format("Failed to retry payment for %s", paymentAttemptId), e);
        } catch (AccountApiException e) {
            log.error(String.format("Failed to retry payment for %s", paymentAttemptId), e);
        }
    }
}
