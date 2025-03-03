/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

import java.util.Iterator;
import java.util.List;

import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.core.sm.PaymentControlStateMachineHelper;
import org.killbill.billing.payment.core.sm.PluginControlPaymentAutomatonRunner;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.clock.Clock;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestIncompletePaymentAttemptTaskWithDB extends PaymentTestSuiteWithEmbeddedDB {

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/757")
    public void testHandleRuntimeExceptions() throws PaymentApiException {
        final List<PaymentAttemptModelDao> paymentAttemptModelDaos = List.of(new PaymentAttemptModelDao(), new PaymentAttemptModelDao());
        final Iterator<PaymentAttemptModelDao> paymentAttemptModelDaoIterator = paymentAttemptModelDaos.iterator();
        final Iterable<PaymentAttemptModelDao> itemsForIteration = new Iterable<PaymentAttemptModelDao>() {
            @Override
            public Iterator<PaymentAttemptModelDao> iterator() {
                return paymentAttemptModelDaoIterator;
            }
        };
        Assert.assertTrue(paymentAttemptModelDaoIterator.hasNext());

        final Runnable incompletePaymentAttemptTaskWithException = new IncompletePaymentAttemptTaskWithException(itemsForIteration,
                                                                                                                 internalCallContextFactory,
                                                                                                                 paymentConfig,
                                                                                                                 paymentDao,
                                                                                                                 clock,
                                                                                                                 paymentControlStateMachineHelper,
                                                                                                                 accountApi,
                                                                                                                 pluginControlPaymentAutomatonRunner,
                                                                                                                 incompletePaymentTransactionTask);

        incompletePaymentAttemptTaskWithException.run();

        // Make sure we cycled through all entries
        Assert.assertFalse(paymentAttemptModelDaoIterator.hasNext());
    }

    private final class IncompletePaymentAttemptTaskWithException extends IncompletePaymentAttemptTask {

        private final Iterable<PaymentAttemptModelDao> itemsForIteration;

        public IncompletePaymentAttemptTaskWithException(final Iterable<PaymentAttemptModelDao> itemsForIteration,
                                                         final InternalCallContextFactory internalCallContextFactory,
                                                         final PaymentConfig paymentConfig,
                                                         final PaymentDao paymentDao,
                                                         final Clock clock,
                                                         final PaymentControlStateMachineHelper retrySMHelper,
                                                         final AccountInternalApi accountInternalApi,
                                                         final PluginControlPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner,
                                                         final IncompletePaymentTransactionTask incompletePaymentTransactionTask) {
            super(internalCallContextFactory, paymentConfig, paymentDao, clock, retrySMHelper, accountInternalApi, pluginControlledPaymentAutomatonRunner, incompletePaymentTransactionTask);
            this.itemsForIteration = itemsForIteration;
        }

        @Override
        public Iterable<PaymentAttemptModelDao> getItemsForIteration() {
            return itemsForIteration;
        }

        @Override
        public boolean doIteration(final PaymentAttemptModelDao attempt, final boolean isApiPayment, final Iterable<PluginProperty> pluginProperties) {
            throw new NullPointerException("NPE for tests");
        }
    }
}
