/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

import javax.inject.Inject;

import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.payment.core.janitor.IncompletePaymentAttemptTask;
import org.killbill.billing.payment.core.janitor.IncompletePaymentTransactionTask;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.notificationq.api.NotificationQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PaymentControlAwareRefresher extends PaymentRefresher {

    private static final Logger log = LoggerFactory.getLogger(PaymentControlAwareRefresher.class);

    private final IncompletePaymentAttemptTask incompletePaymentAttemptTask;

    @Inject
    public PaymentControlAwareRefresher(final PaymentPluginServiceRegistration paymentPluginServiceRegistration,
                                        final AccountInternalApi accountUserApi,
                                        final PaymentDao paymentDao,
                                        final TagInternalApi tagUserApi,
                                        final GlobalLocker locker,
                                        final InternalCallContextFactory internalCallContextFactory,
                                        final InvoiceInternalApi invoiceApi,
                                        final Clock clock,
                                        final IncompletePaymentTransactionTask incompletePaymentTransactionTask,
                                        final NotificationQueueService notificationQueueService,
                                        final IncompletePaymentAttemptTask incompletePaymentAttemptTask) {
        super(paymentPluginServiceRegistration, accountUserApi, paymentDao, tagUserApi, locker, internalCallContextFactory, invoiceApi, clock, notificationQueueService, incompletePaymentTransactionTask);
        this.incompletePaymentAttemptTask = incompletePaymentAttemptTask;
    }

    @Override
    protected boolean invokeJanitor(final UUID accountId,
                                    final PaymentTransactionModelDao paymentTransactionModelDao,
                                    final PaymentTransactionInfoPlugin paymentTransactionInfoPlugin,
                                    final boolean isApiPayment,
                                    final InternalTenantContext internalTenantContext) {
        return incompletePaymentAttemptTask.updatePaymentAndTransactionIfNeeded(accountId,
                                                                                paymentTransactionModelDao.getId(),
                                                                                paymentTransactionModelDao.getTransactionStatus(),
                                                                                paymentTransactionInfoPlugin,
                                                                                isApiPayment,
                                                                                internalTenantContext);
    }
}
