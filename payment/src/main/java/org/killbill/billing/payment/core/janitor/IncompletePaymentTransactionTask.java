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

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.core.PaymentTransactionInfoPluginConverter;
import org.killbill.billing.payment.core.sm.PaymentControlStateMachineHelper;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentMethodModelDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.payment.provider.DefaultNoOpPaymentInfoPlugin;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.config.PaymentConfig;
import org.killbill.clock.Clock;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class IncompletePaymentTransactionTask extends CompletionTaskBase<PaymentTransactionModelDao> {

    private static final ImmutableList<TransactionStatus> TRANSACTION_STATUSES_TO_CONSIDER = ImmutableList.<TransactionStatus>builder()
                                                                                                          .add(TransactionStatus.PENDING)
                                                                                                          .add(TransactionStatus.UNKNOWN)
                                                                                                          .build();
    private static final int MAX_ITEMS_PER_LOOP = 100;

    @Inject
    public IncompletePaymentTransactionTask(final InternalCallContextFactory internalCallContextFactory, final PaymentConfig paymentConfig,
                                            final PaymentDao paymentDao, final Clock clock,
                                            final PaymentStateMachineHelper paymentStateMachineHelper, final PaymentControlStateMachineHelper retrySMHelper, final AccountInternalApi accountInternalApi,
                                            final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry) {
        super(internalCallContextFactory, paymentConfig, paymentDao, clock, paymentStateMachineHelper, retrySMHelper, accountInternalApi, pluginRegistry);
    }

    @Override
    public List<PaymentTransactionModelDao> getItemsForIteration() {
        final List<PaymentTransactionModelDao> result = paymentDao.getByTransactionStatusAcrossTenants(TRANSACTION_STATUSES_TO_CONSIDER, getCreatedDateBefore(), getCreatedDateAfter(), MAX_ITEMS_PER_LOOP);
        if (!result.isEmpty()) {
            log.info("Janitor IncompletePaymentTransactionTask start run: found {} pending/unknown payments", result.size());
        }
        return result;
    }

    @Override
    public void doIteration(final PaymentTransactionModelDao paymentTransaction) {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(paymentTransaction.getTenantRecordId(), paymentTransaction.getAccountRecordId());
        final TenantContext tenantContext = internalCallContextFactory.createTenantContext(internalTenantContext);
        final PaymentModelDao payment = paymentDao.getPayment(paymentTransaction.getPaymentId(), internalTenantContext);

        final PaymentMethodModelDao paymentMethod = paymentDao.getPaymentMethod(payment.getPaymentMethodId(), internalTenantContext);
        final PaymentPluginApi paymentPluginApi = getPaymentPluginApi(payment, paymentMethod.getPluginName());

        final PaymentTransactionInfoPlugin undefinedPaymentTransaction = new DefaultNoOpPaymentInfoPlugin(payment.getId(),
                                                                                                          paymentTransaction.getId(),
                                                                                                          paymentTransaction.getTransactionType(),
                                                                                                          paymentTransaction.getAmount(),
                                                                                                          paymentTransaction.getCurrency(),
                                                                                                          paymentTransaction.getCreatedDate(),
                                                                                                          paymentTransaction.getCreatedDate(),
                                                                                                          PaymentPluginStatus.UNDEFINED,
                                                                                                          null);
        PaymentTransactionInfoPlugin paymentTransactionInfoPlugin;
        try {
            final List<PaymentTransactionInfoPlugin> result = paymentPluginApi.getPaymentInfo(payment.getAccountId(), payment.getId(), ImmutableList.<PluginProperty>of(), tenantContext);
            paymentTransactionInfoPlugin = Iterables.tryFind(result, new Predicate<PaymentTransactionInfoPlugin>() {
                @Override
                public boolean apply(final PaymentTransactionInfoPlugin input) {
                    return input.getKbTransactionPaymentId().equals(paymentTransaction.getId());
                }
            }).or(new Supplier<PaymentTransactionInfoPlugin>() {
                @Override
                public PaymentTransactionInfoPlugin get() {
                    return undefinedPaymentTransaction;
                }
            });
        } catch (final Exception e) {
            paymentTransactionInfoPlugin = undefinedPaymentTransaction;
        }

        updatePaymentAndTransactionIfNeeded(payment, paymentTransaction, paymentTransactionInfoPlugin, internalTenantContext);
    }

    public boolean updatePaymentAndTransactionIfNeeded(final PaymentModelDao payment, final PaymentTransactionModelDao paymentTransaction, final PaymentTransactionInfoPlugin paymentTransactionInfoPlugin, final InternalTenantContext internalTenantContext) {
        final CallContext callContext = createCallContext("IncompletePaymentTransactionTask", internalTenantContext);

        // Can happen in the GET case, see PaymentProcessor#toPayment
        if (!TRANSACTION_STATUSES_TO_CONSIDER.contains(paymentTransaction.getTransactionStatus())) {
            // Nothing to do
            return false;
        }

        // First obtain the new transactionStatus,
        // Then compute the new paymentState; this one is mostly interesting in case of success (to compute the lastSuccessPaymentState below)
        final TransactionStatus transactionStatus = computeNewTransactionStatusFromPaymentTransactionInfoPlugin(paymentTransactionInfoPlugin, paymentTransaction.getTransactionStatus());
        final String newPaymentState;
        switch (transactionStatus) {
            case PENDING:
                newPaymentState = paymentStateMachineHelper.getPendingStateForTransaction(paymentTransaction.getTransactionType());
                break;
            case SUCCESS:
                newPaymentState = paymentStateMachineHelper.getSuccessfulStateForTransaction(paymentTransaction.getTransactionType());
                break;
            case PAYMENT_FAILURE:
                newPaymentState = paymentStateMachineHelper.getFailureStateForTransaction(paymentTransaction.getTransactionType());
                break;
            case PLUGIN_FAILURE:
            case UNKNOWN:
            default:
                log.info("Janitor IncompletePaymentTransactionTask unable to repair payment {}, transaction {}: {} -> {}",
                         payment.getId(), paymentTransaction.getId(), paymentTransaction.getTransactionStatus(), transactionStatus);
                // We can't get anything interesting from the plugin...
                return false;
        }

        // Recompute new lastSuccessPaymentState. This is important to be able to allow new operations on the state machine (for e.g an AUTH_SUCCESS would now allow a CAPTURE operation)
        final String lastSuccessPaymentState = paymentStateMachineHelper.isSuccessState(newPaymentState) ? newPaymentState : null;

        // Update the processedAmount, processedCurrency if we got a paymentTransactionInfoPlugin from the plugin and if this is a non error state
        final BigDecimal processedAmount = (paymentTransactionInfoPlugin != null && isPendingOrFinalTransactionStatus(transactionStatus)) ?
                                           paymentTransactionInfoPlugin.getAmount() : paymentTransaction.getProcessedAmount();
        final Currency processedCurrency = (paymentTransactionInfoPlugin != null && isPendingOrFinalTransactionStatus(transactionStatus)) ?
                                           paymentTransactionInfoPlugin.getCurrency() : paymentTransaction.getProcessedCurrency();

        // Update the gatewayErrorCode, gatewayError if we got a paymentTransactionInfoPlugin
        final String gatewayErrorCode = paymentTransactionInfoPlugin != null ? paymentTransactionInfoPlugin.getGatewayErrorCode() : paymentTransaction.getGatewayErrorCode();
        final String gatewayError = paymentTransactionInfoPlugin != null ? paymentTransactionInfoPlugin.getGatewayError() : paymentTransaction.getGatewayErrorMsg();

        log.info("Janitor IncompletePaymentTransactionTask repairing payment {}, transaction {}, transitioning transactionStatus from {} -> {}",
                 payment.getId(), paymentTransaction.getId(), paymentTransaction.getTransactionStatus(), transactionStatus);

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(payment.getAccountId(), callContext);
        paymentDao.updatePaymentAndTransactionOnCompletion(payment.getAccountId(), payment.getId(), paymentTransaction.getTransactionType(), newPaymentState, lastSuccessPaymentState,
                                                           paymentTransaction.getId(), transactionStatus, processedAmount, processedCurrency, gatewayErrorCode, gatewayError, internalCallContext);

        return true;
    }

    // Keep the existing currentTransactionStatus if we can't obtain a better answer from the plugin; if not, return the newTransactionStatus
    private TransactionStatus computeNewTransactionStatusFromPaymentTransactionInfoPlugin(final PaymentTransactionInfoPlugin input, final TransactionStatus currentTransactionStatus) {
        final TransactionStatus newTransactionStatus = PaymentTransactionInfoPluginConverter.toTransactionStatus(input);
        return (newTransactionStatus != TransactionStatus.UNKNOWN) ? newTransactionStatus : currentTransactionStatus;
    }

    private boolean isPendingOrFinalTransactionStatus(final TransactionStatus transactionStatus) {
        return (transactionStatus == TransactionStatus.PENDING ||
                transactionStatus == TransactionStatus.SUCCESS ||
                transactionStatus == TransactionStatus.PAYMENT_FAILURE);
    }

    private PaymentPluginApi getPaymentPluginApi(final PaymentModelDao item, final String pluginName) {
        final PaymentPluginApi pluginApi = pluginRegistry.getServiceForName(pluginName);
        Preconditions.checkState(pluginApi != null, "Janitor IncompletePaymentTransactionTask cannot retrieve PaymentPluginApi " + item.getId() + ", skipping");
        return pluginApi;
    }

    private DateTime getCreatedDateBefore() {
        final long delayBeforeNowMs = paymentConfig.getIncompleteTransactionsTimeSpanDelay().getMillis();
        return clock.getUTCNow().minusMillis((int) delayBeforeNowMs);
    }

    private DateTime getCreatedDateAfter() {
        final long delayBeforeNowMs = paymentConfig.getIncompleteTransactionsTimeSpanGiveup().getMillis();
        return clock.getUTCNow().minusMillis((int) delayBeforeNowMs);
    }
}
