/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.core.sm;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentMethodModelDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.memory.MemoryGlobalLocker;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestDirectPaymentOperation extends PaymentTestSuiteNoDB {

    private DirectPaymentStateContext directPaymentStateContext;
    private DirectPaymentOperationTest directPaymentOperation;

    @Test(groups = "fast")
    public void testPaymentFailure() throws Exception {
        setUp(PaymentPluginStatus.ERROR);

        Assert.assertNull(directPaymentStateContext.getPaymentInfoPlugin());

        Assert.assertEquals(directPaymentOperation.doOperationCallback(), OperationResult.FAILURE);

        Assert.assertNotNull(directPaymentStateContext.getPaymentInfoPlugin());
    }

    @Test(groups = "fast")
    public void testPluginFailure() throws Exception {
        setUp(null);

        Assert.assertNull(directPaymentStateContext.getPaymentInfoPlugin());

        try {
            directPaymentOperation.doOperationCallback();
            Assert.fail();
        } catch (final OperationException e) {
            Assert.assertEquals(e.getOperationResult(), OperationResult.EXCEPTION);
        }

        Assert.assertNull(directPaymentStateContext.getPaymentInfoPlugin());
    }

    @Test(groups = "fast")
    public void testPaymentPending() throws Exception {
        setUp(PaymentPluginStatus.PENDING);

        Assert.assertNull(directPaymentStateContext.getPaymentInfoPlugin());

        Assert.assertEquals(directPaymentOperation.doOperationCallback(), OperationResult.PENDING);

        Assert.assertNotNull(directPaymentStateContext.getPaymentInfoPlugin());
    }

    @Test(groups = "fast")
    public void testPaymentSuccess() throws Exception {
        setUp(PaymentPluginStatus.PROCESSED);

        Assert.assertNull(directPaymentStateContext.getPaymentInfoPlugin());

        Assert.assertEquals(directPaymentOperation.doOperationCallback(), OperationResult.SUCCESS);

        Assert.assertNotNull(directPaymentStateContext.getPaymentInfoPlugin());
    }

    private void setUp(final PaymentPluginStatus paymentPluginStatus) throws Exception {
        final GlobalLocker locker = new MemoryGlobalLocker();
        final PluginDispatcher<OperationResult> paymentPluginDispatcher = new PluginDispatcher<OperationResult>(1, Executors.newCachedThreadPool());
        directPaymentStateContext = new DirectPaymentStateContext(UUID.randomUUID(),
                                                                  UUID.randomUUID().toString(),
                                                                  UUID.randomUUID().toString(),
                                                                  TransactionType.CAPTURE,
                                                                  Mockito.mock(Account.class),
                                                                  UUID.randomUUID(),
                                                                  new BigDecimal("192.3920111"),
                                                                  Currency.BRL,
                                                                  false,
                                                                  ImmutableList.<PluginProperty>of(),
                                                                  internalCallContext,
                                                                  callContext);

        final PaymentMethodModelDao paymentMethodModelDao = new PaymentMethodModelDao(directPaymentStateContext.getPaymentMethodId(), clock.getUTCNow(), clock.getUTCNow(),
                                                                                      directPaymentStateContext.getAccount().getId(), MockPaymentProviderPlugin.PLUGIN_NAME, true);
        final PaymentDao paymentDao = Mockito.mock(PaymentDao.class);
        Mockito.when(paymentDao.getPaymentMethodIncludedDeleted(directPaymentStateContext.getPaymentMethodId(), internalCallContext)).thenReturn(paymentMethodModelDao);

        final DirectPaymentAutomatonDAOHelper daoHelper = new DirectPaymentAutomatonDAOHelper(directPaymentStateContext, clock.getUTCNow(), paymentDao, registry, internalCallContext);
        directPaymentOperation = new DirectPaymentOperationTest(paymentPluginStatus, daoHelper, locker, paymentPluginDispatcher, directPaymentStateContext);
    }

    private static final class DirectPaymentOperationTest extends DirectPaymentOperation {

        private final PaymentTransactionInfoPlugin paymentInfoPlugin;

        public DirectPaymentOperationTest(@Nullable final PaymentPluginStatus paymentPluginStatus,
                                          final DirectPaymentAutomatonDAOHelper daoHelper, final GlobalLocker locker,
                                          final PluginDispatcher<OperationResult> paymentPluginDispatcher, final DirectPaymentStateContext directPaymentStateContext) throws PaymentApiException {
            super(daoHelper, locker, paymentPluginDispatcher, directPaymentStateContext);
            this.paymentInfoPlugin = (paymentPluginStatus == null ? null : getPaymentInfoPlugin(paymentPluginStatus));
        }

        @Override
        protected PaymentTransactionInfoPlugin doCallSpecificOperationCallback() throws PaymentPluginApiException {
            if (paymentInfoPlugin == null) {
                throw new RuntimeException("Exception expected by test");
            } else {
                return paymentInfoPlugin;
            }
        }

        private PaymentTransactionInfoPlugin getPaymentInfoPlugin(final PaymentPluginStatus paymentPluginStatus) {
            final PaymentTransactionInfoPlugin paymentInfoPlugin = Mockito.mock(PaymentTransactionInfoPlugin.class);
            Mockito.when(paymentInfoPlugin.getStatus()).thenReturn(paymentPluginStatus);
            return paymentInfoPlugin;
        }
    }
}
