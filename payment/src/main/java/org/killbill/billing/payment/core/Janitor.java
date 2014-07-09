/*
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

package org.killbill.billing.payment.core;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes care of incomplete payment/transactions.
 */
public class Janitor {

    private final static Logger log = LoggerFactory.getLogger(Janitor.class);

    private final ScheduledExecutorService janitorExecutor;
    private final PaymentDao paymentDao;
    private final Clock clock;
    private final PaymentConfig paymentConfig;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public Janitor(final PaymentDao paymentDao,
                   final PaymentConfig paymentConfig,
                   final Clock clock,
                   final InternalCallContextFactory internalCallContextFactory,
                   @Named(PaymentModule.JANITOR_EXECUTOR_NAMED) final ScheduledExecutorService janitorExecutor) {
        this.paymentDao = paymentDao;
        this.clock = clock;
        this.paymentConfig = paymentConfig;
        this.janitorExecutor = janitorExecutor;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    public void start() {
        final TimeUnit rateUnit = paymentConfig.getJanitorRunningRate().getUnit();
        final long period = paymentConfig.getJanitorRunningRate().getPeriod();
        janitorExecutor.scheduleAtFixedRate(new PendingTransactionTask(), period, period, rateUnit);
    }

    public void stop() {
        janitorExecutor.shutdown();
    }

    /**
     * Task to find old PENDING transactions and move them into
     */
    private final class PendingTransactionTask implements Runnable {

        private final InternalTenantContext fakeCallContext;

        private PendingTransactionTask() {
            this.fakeCallContext = internalCallContextFactory.createInternalCallContext((Long) null, (Long) null, "PendingJanitorTask", CallOrigin.INTERNAL, UserType.SYSTEM, UUID.randomUUID());
        }

        private DateTime getCreatedDateBefore() {
            final long delayBeforeNowMs = paymentConfig.getJanitorPendingCleanupTime().getMillis();
            return clock.getUTCNow().minusMillis((int) delayBeforeNowMs);
        }

        @Override
        public void run() {
            paymentDao.failOldPendingTransactions(TransactionStatus.PLUGIN_FAILURE, getCreatedDateBefore(), fakeCallContext);
        }
    }
}
