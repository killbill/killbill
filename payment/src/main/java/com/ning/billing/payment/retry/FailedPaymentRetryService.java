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

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.config.PaymentConfig;
import com.ning.billing.payment.core.PaymentProcessor;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.notificationq.NotificationQueueService;


public class FailedPaymentRetryService extends BaseRetryService implements RetryService {

    private static final Logger log = LoggerFactory.getLogger(FailedPaymentRetryService.class);

    public static final String QUEUE_NAME = "failed-payment";

    private final PaymentProcessor paymentProcessor;


    @Inject
    public FailedPaymentRetryService(final AccountUserApi accountUserApi,
                                     final Clock clock,
                                     final NotificationQueueService notificationQueueService,
                                     final PaymentConfig config,
                                     final PaymentProcessor paymentProcessor,
                                     final PaymentDao paymentDao) {
        super(notificationQueueService, clock, config);
        this.paymentProcessor = paymentProcessor;
    }


    @Override
    public void retry(final UUID paymentId) {
        paymentProcessor.retryFailedPayment(paymentId);
    }


    public static class FailedPaymentRetryServiceScheduler extends RetryServiceScheduler {

        private final PaymentConfig config;
        private final Clock clock;

        @Inject
        public FailedPaymentRetryServiceScheduler(final NotificationQueueService notificationQueueService,
                                                  final Clock clock, final PaymentConfig config) {
            super(notificationQueueService);
            this.config = config;
            this.clock = clock;
        }

        public boolean scheduleRetry(final UUID paymentId, final int retryAttempt) {
            final DateTime timeOfRetry = getNextRetryDate(retryAttempt);
            if (timeOfRetry == null) {
                return false;
            }
            return scheduleRetry(paymentId, timeOfRetry);
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
