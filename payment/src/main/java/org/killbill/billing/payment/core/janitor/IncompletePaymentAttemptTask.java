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

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.core.sm.PaymentControlStateMachineHelper;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.killbill.billing.payment.core.sm.PluginRoutingPaymentAutomatonRunner;
import org.killbill.billing.payment.core.sm.control.PaymentStateControlContext;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.dao.PluginPropertySerializer;
import org.killbill.billing.payment.dao.PluginPropertySerializer.PluginPropertySerializerException;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.clock.Clock;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

/**
 * Task to complete 'partially' incomplete payment attempts. Tis only matters for calls that went through PaymentControl apis.
 * <p/>
 * If the state of the transaction associated with the attempt completed, but the attempt state machine did not,
 * we rerun the retry state machine to complete the call and transition the attempt into a terminal state.
 */
public class IncompletePaymentAttemptTask extends CompletionTaskBase<PaymentAttemptModelDao> {

    private final PluginRoutingPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner;

    @Inject
    public IncompletePaymentAttemptTask(final InternalCallContextFactory internalCallContextFactory, final PaymentConfig paymentConfig,
                                        final PaymentDao paymentDao, final Clock clock, final PaymentStateMachineHelper paymentStateMachineHelper,
                                        final PaymentControlStateMachineHelper retrySMHelper, final AccountInternalApi accountInternalApi,
                                        final PluginRoutingPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner, final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry) {
        super(internalCallContextFactory, paymentConfig, paymentDao, clock, paymentStateMachineHelper, retrySMHelper, accountInternalApi, pluginRegistry);
        this.pluginControlledPaymentAutomatonRunner = pluginControlledPaymentAutomatonRunner;
    }

    @Override
    public List<PaymentAttemptModelDao> getItemsForIteration() {
        final List<PaymentAttemptModelDao> incompleteAttempts = paymentDao.getPaymentAttemptsByStateAcrossTenants(retrySMHelper.getInitialState().getName(), getCreatedDateBefore());
        if (!incompleteAttempts.isEmpty()) {
            log.info("Janitor AttemptCompletionTask start run: found {} incomplete attempts", incompleteAttempts.size());
        }
        return incompleteAttempts;
    }

    @Override
    public void doIteration(final PaymentAttemptModelDao attempt) {
        final InternalTenantContext tenantContext = internalCallContextFactory.createInternalTenantContext(attempt.getTenantRecordId(), attempt.getAccountRecordId());
        final CallContext callContext = createCallContext("AttemptCompletionJanitorTask", tenantContext);
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(attempt.getAccountId(), callContext);

        final List<PaymentTransactionModelDao> transactions = paymentDao.getPaymentTransactionsByExternalKey(attempt.getTransactionExternalKey(), tenantContext);
        final PaymentTransactionModelDao transaction = Iterables.tryFind(transactions, new Predicate<PaymentTransactionModelDao>() {
            @Override
            public boolean apply(final PaymentTransactionModelDao input) {
                return input.getAttemptId().equals(attempt.getId()) &&
                       input.getTransactionStatus() == TransactionStatus.SUCCESS;
            }
        }).orNull();

        if (transaction == null) {
            log.info("Janitor AttemptCompletionTask moving attempt " + attempt.getId() + " -> ABORTED");
            paymentDao.updatePaymentAttempt(attempt.getId(), attempt.getTransactionId(), "ABORTED", internalCallContext);
            return;
        }

        try {
            log.info("Janitor AttemptCompletionTask completing attempt " + attempt.getId() + " -> SUCCESS");

            final Account account = accountInternalApi.getAccountById(attempt.getAccountId(), tenantContext);
            final boolean isApiPayment = true; // unclear
            final PaymentStateControlContext paymentStateContext = new PaymentStateControlContext(attempt.toPaymentControlPluginNames(),
                                                                                                  isApiPayment,
                                                                                                  transaction.getPaymentId(),
                                                                                                  attempt.getPaymentExternalKey(),
                                                                                                  transaction.getTransactionExternalKey(),
                                                                                                  transaction.getTransactionType(),
                                                                                                  account,
                                                                                                  attempt.getPaymentMethodId(),
                                                                                                  transaction.getAmount(),
                                                                                                  transaction.getCurrency(),
                                                                                                  PluginPropertySerializer.deserialize(attempt.getPluginProperties()),
                                                                                                  internalCallContext,
                                                                                                  callContext);

            paymentStateContext.setAttemptId(attempt.getId()); // Normally set by leavingState Callback
            paymentStateContext.setPaymentTransactionModelDao(transaction); // Normally set by raw state machine
            //
            // Will rerun the state machine with special callbacks to only make the executePluginOnSuccessCalls call
            // to the PaymentControlPluginApi plugin and transition the state.
            //
            pluginControlledPaymentAutomatonRunner.completeRun(paymentStateContext);
        } catch (final AccountApiException e) {
            log.warn("Janitor AttemptCompletionTask failed to complete payment attempt " + attempt.getId(), e);
        } catch (final PluginPropertySerializerException e) {
            log.warn("Janitor AttemptCompletionTask failed to complete payment attempt " + attempt.getId(), e);
        } catch (final PaymentApiException e) {
            log.warn("Janitor AttemptCompletionTask failed to complete payment attempt " + attempt.getId(), e);
        }
    }

    private DateTime getCreatedDateBefore() {
        final long delayBeforeNowMs = paymentConfig.getIncompleteAttemptsTimeSpanDelay().getMillis();
        return clock.getUTCNow().minusMillis((int) delayBeforeNowMs);
    }
}
