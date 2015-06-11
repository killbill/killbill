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

import java.math.BigDecimal;
import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.core.sm.payments.PaymentEnteringStateCallback;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.killbill.billing.payment.core.sm.PluginRoutingPaymentAutomatonRunner;
import org.killbill.billing.payment.core.sm.PaymentControlStateMachineHelper;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentMethodModelDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.clock.Clock;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class ErroredPaymentTask extends CompletionTaskBase<PaymentModelDao> {

    // We could configure all that if this becomes useful but we also want to avoid a flurry of parameters.
    private static final int SAFETY_DELAY_MS = (3 * 60 * 1000); // 3 minutes
    private final int OLDER_PAYMENTS_IN_DAYS = 3; // don't look at ERRORED payment older than 3 days
    private final int MAX_ITEMS_PER_LOOP = 100; // Limit of items per iteration

    public ErroredPaymentTask(final Janitor janitor, final InternalCallContextFactory internalCallContextFactory, final PaymentConfig paymentConfig,
                              final PaymentDao paymentDao, final Clock clock,
                              final PaymentStateMachineHelper paymentStateMachineHelper, final PaymentControlStateMachineHelper retrySMHelper, final AccountInternalApi accountInternalApi,
                              final PluginRoutingPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner, final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry) {
        super(janitor, internalCallContextFactory, paymentConfig, paymentDao, clock, paymentStateMachineHelper, retrySMHelper, accountInternalApi, pluginControlledPaymentAutomatonRunner, pluginRegistry);
    }

    @Override
    public List<PaymentModelDao> getItemsForIteration() {
        // In theory this should be the plugin timeout but we add a 3 minutes delay for safety.
        final int delayBeforeNow = (int) paymentConfig.getPaymentPluginTimeout().getMillis() + SAFETY_DELAY_MS;
        final DateTime createdBeforeDate = clock.getUTCNow().minusMillis(delayBeforeNow);

        // We want to avoid iterating on the same failed payments -- if for some reasons they can't fix themselves.
        final DateTime createdAfterDate = clock.getUTCNow().minusDays(OLDER_PAYMENTS_IN_DAYS);

        final List<PaymentModelDao> result = paymentDao.getPaymentsByStatesAcrossTenants(paymentStateMachineHelper.getErroredStateNames(), createdBeforeDate, createdAfterDate, MAX_ITEMS_PER_LOOP);
        if (!result.isEmpty()) {
            log.info("Janitor ErroredPaymentTask start run: found {} errored/unknown payments", result.size());
        }
        return result;
    }

    @Override
    public void doIteration(final PaymentModelDao item) {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(item.getTenantRecordId(), item.getAccountRecordId());
        final CallContext callContext = createCallContext("ErroredPaymentTask", internalTenantContext);
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(item.getAccountId(), callContext);

        final List<PaymentTransactionModelDao> transactions = paymentDao.getTransactionsForPayment(item.getId(), internalTenantContext);
        Preconditions.checkState(!transactions.isEmpty(), "Janitor ErroredPaymentTask found item " + item.getId() + " with no transactions, skipping");

        // We look for latest transaction in an UNKNOWN state, if not we skip
        final PaymentTransactionModelDao unknownTransaction = transactions.get(transactions.size() - 1);
        if (unknownTransaction.getTransactionStatus() != TransactionStatus.UNKNOWN) {
            return;
        }

        final PaymentMethodModelDao paymentMethod = paymentDao.getPaymentMethod(item.getPaymentMethodId(), internalCallContext);
        final PaymentPluginApi paymentPluginApi = getPaymentPluginApi(item, paymentMethod.getPluginName());

        PaymentTransactionInfoPlugin pluginErroredTransaction = null;
        try {
            final List<PaymentTransactionInfoPlugin> result = paymentPluginApi.getPaymentInfo(item.getAccountId(), item.getId(), ImmutableList.<PluginProperty>of(), callContext);

            pluginErroredTransaction = Iterables.tryFind(result, new Predicate<PaymentTransactionInfoPlugin>() {
                @Override
                public boolean apply(@Nullable final PaymentTransactionInfoPlugin input) {
                    return input.getKbTransactionPaymentId().equals(unknownTransaction.getId());
                }
            }).orNull();
        } catch (final PaymentPluginApiException ignored) {

        }

        // Compute new transactionStatus based on pluginInfo state; and if that did not change, bail early.
        final TransactionStatus transactionStatus = PaymentEnteringStateCallback.paymentPluginStatusToTransactionStatus(pluginErroredTransaction);
        if (transactionStatus == unknownTransaction.getTransactionStatus()) {
            return;
        }

        // This piece of logic is obviously outside of the state machine, and this is a bit of a hack; at least all the paymentStates internal config is
        // kept into paymentStateMachineHelper.
        final String newPaymentState;
        switch (transactionStatus) {
            case PENDING:
                newPaymentState = paymentStateMachineHelper.getPendingStateForTransaction(unknownTransaction.getTransactionType());
                break;
            case SUCCESS:
                newPaymentState = paymentStateMachineHelper.getSuccessfulStateForTransaction(unknownTransaction.getTransactionType());
                break;
            case PAYMENT_FAILURE:
                newPaymentState = paymentStateMachineHelper.getFailureStateForTransaction(unknownTransaction.getTransactionType());
                break;
            case PLUGIN_FAILURE:
            case UNKNOWN:
            default:
                newPaymentState = paymentStateMachineHelper.getErroredStateForTransaction(unknownTransaction.getTransactionType());
                break;
        }
        final String lastSuccessPaymentState = paymentStateMachineHelper.isSuccessState(newPaymentState) ? newPaymentState : null;

        final BigDecimal processedAmount = pluginErroredTransaction != null ? pluginErroredTransaction.getAmount() : null;
        final Currency processedCurrency = pluginErroredTransaction != null ? pluginErroredTransaction.getCurrency() : null;
        final String gatewayErrorCode = pluginErroredTransaction != null ? pluginErroredTransaction.getGatewayErrorCode() : null;
        final String gatewayError = pluginErroredTransaction != null ? pluginErroredTransaction.getGatewayError() : null;

        log.info("Janitor ErroredPaymentTask repairing payment {}, transaction {}", item.getId(), unknownTransaction.getId());

        paymentDao.updatePaymentAndTransactionOnCompletion(item.getAccountId(), item.getId(), unknownTransaction.getTransactionType(), newPaymentState, lastSuccessPaymentState,
                                                           unknownTransaction.getId(), transactionStatus, processedAmount, processedCurrency, gatewayErrorCode, gatewayError, internalCallContext);

    }

    private PaymentPluginApi getPaymentPluginApi(final PaymentModelDao item, final String pluginName) {
        final PaymentPluginApi pluginApi = pluginRegistry.getServiceForName(pluginName);
        Preconditions.checkState(pluginApi != null, "Janitor ErroredPaymentTask cannot retrieve PaymentPluginApi " + item.getId() + ", skipping");
        return pluginApi;
    }
}
