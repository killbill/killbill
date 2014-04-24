/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.core.PaymentProcessor;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class FailedPaymentRetryService extends BaseRetryService implements RetryService {

    private static final Logger log = LoggerFactory.getLogger(FailedPaymentRetryService.class);

    public static final String QUEUE_NAME = "failed-payment";

    private final PaymentProcessor paymentProcessor;

    @Inject
    public FailedPaymentRetryService(final NotificationQueueService notificationQueueService,
                                     final PaymentConfig config,
                                     final PaymentProcessor paymentProcessor,
                                     final InternalCallContextFactory internalCallContextFactory) {
        super(notificationQueueService, internalCallContextFactory);
        this.paymentProcessor = paymentProcessor;
    }

    @Override
    public void retry(final UUID paymentId, final Iterable<PluginProperty> properties, final InternalCallContext context) {
        paymentProcessor.retryFailedPayment(paymentId, properties, context);
    }

    public static class FailedPaymentRetryServiceScheduler extends RetryServiceScheduler {

        private final PaymentConfig config;
        private final Clock clock;

        @Inject
        public FailedPaymentRetryServiceScheduler(final NotificationQueueService notificationQueueService,
                                                  final InternalCallContextFactory internalCallContextFactory,
                                                  final Clock clock,
                                                  final PaymentConfig config) {
            super(notificationQueueService, internalCallContextFactory);
            this.config = config;
            this.clock = clock;
        }

        public boolean scheduleRetry(final UUID paymentId, final int retryAttempt) {
            final DateTime timeOfRetry = getNextRetryDate(retryAttempt);
            if (timeOfRetry == null) {
                return false;
            }
            return super.scheduleRetry(paymentId, timeOfRetry);
        }

        private DateTime getNextRetryDate(final int retryAttempt) {

            DateTime result = null;
            final List<Integer> retryDays = config.getPaymentRetryDays();
            final int retryCount = retryAttempt - 1;
            if (retryCount < retryDays.size()) {
                int retryInDays = 0;
                final DateTime nextRetryDate = clock.getUTCNow();
                try {
                    retryInDays = retryDays.get(retryCount);
                    result = nextRetryDate.plusDays(retryInDays);
                } catch (NumberFormatException ex) {
                    log.error("Could not get retry day for retry count {}", retryCount);
                }
            }
            return result;
        }

        @Override
        public String getQueueName() {
            return QUEUE_NAME;
        }
    }

    @Override
    public String getQueueName() {
        return QUEUE_NAME;
    }
}
