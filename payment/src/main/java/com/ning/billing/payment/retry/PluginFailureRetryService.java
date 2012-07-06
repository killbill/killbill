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
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.config.PaymentConfig;
import com.ning.billing.payment.core.PaymentProcessor;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.notificationq.NotificationQueueService;

public class PluginFailureRetryService extends BaseRetryService implements RetryService {

    private static final Logger log = LoggerFactory.getLogger(PluginFailureRetryService.class);

    public static final String QUEUE_NAME = "plugin-failure";

    private final PaymentProcessor paymentProcessor;


    @Inject
    public PluginFailureRetryService(final AccountUserApi accountUserApi,
                                     final Clock clock,
                                     final NotificationQueueService notificationQueueService,
                                     final PaymentConfig config,
                                     final PaymentProcessor paymentProcessor) {
        super(notificationQueueService, clock, config);
        this.paymentProcessor = paymentProcessor;
    }


    @Override
    public void retry(final UUID paymentId) {
        paymentProcessor.retryPluginFailure(paymentId);
    }


    public static class PluginFailureRetryServiceScheduler extends RetryServiceScheduler {

        private final Clock clock;
        private final PaymentConfig config;

        @Inject
        public PluginFailureRetryServiceScheduler(final NotificationQueueService notificationQueueService,
                                                  final Clock clock, final PaymentConfig config) {
            super(notificationQueueService);
            this.clock = clock;
            this.config = config;
        }

        @Override
        public String getQueueName() {
            return QUEUE_NAME;
        }

        public boolean scheduleRetry(final UUID paymentId, final int retryAttempt) {
            final DateTime nextRetryDate = getNextRetryDate(retryAttempt);
            if (nextRetryDate == null) {
                return false;
            }
            return super.scheduleRetry(paymentId, nextRetryDate);
        }

        public boolean scheduleRetryFromTransaction(final UUID paymentId, final int retryAttempt, final Transmogrifier transactionalDao) {
            final DateTime nextRetryDate = getNextRetryDate(retryAttempt);
            if (nextRetryDate == null) {
                return false;
            }
            return scheduleRetryFromTransaction(paymentId, nextRetryDate, transactionalDao);
        }

        private DateTime getNextRetryDate(final int retryAttempt) {


            if (retryAttempt > config.getPluginFailureRetryMaxAttempts()) {
                return null;
            }
            int nbSec = config.getPluginFailureRetryStart();
            int remainingAttempts = retryAttempt;
            while (--remainingAttempts > 0) {
                nbSec = nbSec * config.getPluginFailureRetryMultiplier();
            }
            return clock.getUTCNow().plusSeconds(nbSec);
        }

    }

    @Override
    public String getQueueName() {
        return QUEUE_NAME;
    }
}
