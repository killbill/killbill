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

package org.killbill.billing.payment.core.janitor;

import java.util.List;

import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.core.sm.PluginControlledPaymentAutomatonRunner;
import org.killbill.billing.payment.core.sm.RetryStateMachineHelper;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.clock.Clock;

import com.google.common.collect.ImmutableList;

/**
 * Task to find old PENDING transactions and move them into
 */
final class PendingTransactionTask extends CompletionTaskBase<Integer> {

    private Janitor janitor;
    private final List<Integer> itemsForIterations;

    public PendingTransactionTask(final Janitor janitor, final InternalCallContextFactory internalCallContextFactory, final PaymentConfig paymentConfig,
                                 final NonEntityDao nonEntityDao, final PaymentDao paymentDao, final Clock clock,
                                 final RetryStateMachineHelper retrySMHelper, final CacheControllerDispatcher controllerDispatcher, final AccountInternalApi accountInternalApi,
                                 final PluginControlledPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner) {
        super(janitor, internalCallContextFactory, paymentConfig, nonEntityDao, paymentDao, clock, retrySMHelper, controllerDispatcher, accountInternalApi, pluginControlledPaymentAutomatonRunner);
        this.itemsForIterations = ImmutableList.of(new Integer(1));
    }

    @Override
    public List<Integer> getItemsForIteration() {
        return itemsForIterations;
    }

    @Override
    public void doIteration(final Integer item) {
        int result = paymentDao.failOldPendingTransactions(TransactionStatus.PLUGIN_FAILURE, getCreatedDateBefore(), fakeCallContext);
        if (result > 0) {
            log.info("Janitor PendingTransactionTask moved " + result + " PENDING payments ->  PLUGIN_FAILURE");
        }
    }
}
