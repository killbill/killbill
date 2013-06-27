/*
 * Copyright 2010-2013 Ning, Inc.
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

import com.ning.billing.notificationq.NotificationQueueService;
import com.ning.billing.util.config.PaymentConfig;
import com.ning.billing.payment.core.PaymentProcessor;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;

import com.google.inject.Inject;

public class AutoPayRetryService extends BaseRetryService implements RetryService {

    public static final String QUEUE_NAME = "autopayoff";

    private final PaymentProcessor paymentProcessor;

    @Inject
    public AutoPayRetryService(final NotificationQueueService notificationQueueService,
                               final PaymentConfig config,
                               final PaymentProcessor paymentProcessor,
                               final InternalCallContextFactory internalCallContextFactory) {
        super(notificationQueueService, internalCallContextFactory);
        this.paymentProcessor = paymentProcessor;
    }

    @Override
    public String getQueueName() {
        return QUEUE_NAME;
    }

    @Override
    public void retry(final UUID paymentId, final InternalCallContext context) {
        paymentProcessor.retryAutoPayOff(paymentId, context);
    }

    public static class AutoPayRetryServiceScheduler extends RetryServiceScheduler {

        @Inject
        public AutoPayRetryServiceScheduler(final NotificationQueueService notificationQueueService,
                                            final InternalCallContextFactory internalCallContextFactory) {
            super(notificationQueueService, internalCallContextFactory);
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
