/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import java.util.List;

import javax.inject.Inject;

import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.payment.core.janitor.IncompletePaymentAttemptTask;
import org.killbill.billing.payment.core.janitor.IncompletePaymentTransactionTask;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.notificationq.api.NotificationQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

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
    protected void onJanitorChange(final PaymentTransactionModelDao curPaymentTransactionModelDao, final InternalTenantContext internalTenantContext) {
        // If there is a payment attempt associated with that transaction, we need to update it as well
        final List<PaymentAttemptModelDao> paymentAttemptsModelDao = paymentDao.getPaymentAttemptByTransactionExternalKey(curPaymentTransactionModelDao.getTransactionExternalKey(), internalTenantContext);
        final PaymentAttemptModelDao paymentAttemptModelDao = Iterables.<PaymentAttemptModelDao>tryFind(paymentAttemptsModelDao,
                                                                                                        new Predicate<PaymentAttemptModelDao>() {
                                                                                                            @Override
                                                                                                            public boolean apply(final PaymentAttemptModelDao input) {
                                                                                                                return curPaymentTransactionModelDao.getId().equals(input.getTransactionId());
                                                                                                            }
                                                                                                        }).orNull();
        if (paymentAttemptModelDao != null) {
            // We can re-use the logic from IncompletePaymentAttemptTask as it is doing very similar work (i.e. run the completion part of
            // the state machine to call the plugins and update the attempt in the right terminal state)
            incompletePaymentAttemptTask.doIteration(paymentAttemptModelDao);
        }
    }
}
