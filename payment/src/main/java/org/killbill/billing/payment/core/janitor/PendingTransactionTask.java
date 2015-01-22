/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.killbill.billing.payment.core.sm.PluginRoutingPaymentAutomatonRunner;
import org.killbill.billing.payment.core.sm.RetryStateMachineHelper;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.clock.Clock;

import com.google.common.collect.ImmutableList;

/**
 * Task to find old PENDING transactions and move them into
 */
final class PendingTransactionTask extends CompletionTaskBase<Integer> {

    private final List<Integer> itemsForIterations;

    public PendingTransactionTask(final Janitor janitor, final InternalCallContextFactory internalCallContextFactory, final PaymentConfig paymentConfig,
                                  final PaymentDao paymentDao, final Clock clock, final PaymentStateMachineHelper paymentStateMachineHelper,
                                  final RetryStateMachineHelper retrySMHelper, final AccountInternalApi accountInternalApi,
                                  final PluginRoutingPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner, final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry) {
        super(janitor, internalCallContextFactory, paymentConfig, paymentDao, clock, paymentStateMachineHelper, retrySMHelper, accountInternalApi, pluginControlledPaymentAutomatonRunner, pluginRegistry);
        this.itemsForIterations = ImmutableList.of(new Integer(1));
    }

    @Override
    public List<Integer> getItemsForIteration() {
        return itemsForIterations;
    }

    @Override
    public void doIteration(final Integer item) {

        // TODO this is needs to be fixed see- #230
        int result = paymentDao.failOldPendingTransactions(TransactionStatus.PLUGIN_FAILURE, getCreatedDateBefore(), completionTaskCallContext);
        if (result > 0) {
            log.info("Janitor PendingTransactionTask moved " + result + " PENDING payments ->  PLUGIN_FAILURE");
        }
    }
}
