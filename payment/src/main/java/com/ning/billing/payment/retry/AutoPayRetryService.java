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
import com.ning.billing.payment.core.PaymentProcessor;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.notificationq.NotificationQueueService;


public class AutoPayRetryService extends BaseRetryService implements RetryService {


    private static final Logger log = LoggerFactory.getLogger(FailedPaymentRetryService.class);

    public static final String QUEUE_NAME = "autopayoff";

    private final PaymentProcessor paymentProcessor;

    @Inject
    public AutoPayRetryService(final NotificationQueueService notificationQueueService,
            final Clock clock,
            final PaymentConfig config,
            final PaymentProcessor paymentProcessor) {
        super(notificationQueueService, clock, config);
        this.paymentProcessor = paymentProcessor;
    }


    @Override
    public String getQueueName() {
        return QUEUE_NAME;
    }

    @Override
    public void retry(final UUID paymentId) {
        paymentProcessor.retryAutoPayOff(paymentId);
    }

    public static class AutoPayRetryServiceScheduler extends RetryServiceScheduler {


        @Inject
        public AutoPayRetryServiceScheduler(final NotificationQueueService notificationQueueService) {
            super(notificationQueueService);
        }

        @Override
        public boolean scheduleRetry(final UUID paymentId, final DateTime timeOfRetry) {
            return super.scheduleRetry(paymentId, timeOfRetry);
        }

        @Override
        public String getQueueName() {
            return QUEUE_NAME;
        }
    }
}
