/*
 * Copyright 2014 Groupon, Inc
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

package org.killbill.billing.payment.core;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.automaton.MissingEntryException;
import org.killbill.automaton.State;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.sm.PluginControlledPaymentAutomatonRunner;
import org.killbill.billing.payment.core.sm.RetryStateMachineHelper;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PluginPropertySerializer;
import org.killbill.billing.payment.dao.PluginPropertySerializer.PluginPropertySerializerException;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;

import com.google.common.collect.ImmutableList;
import com.google.inject.name.Named;

import static org.killbill.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;

public class PluginControlledPaymentProcessor extends ProcessorBase {

    private final PluginControlledPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner;
    private final RetryStateMachineHelper retrySMHelper;
    private final CacheControllerDispatcher controllerDispatcher;

    @Inject
    public PluginControlledPaymentProcessor(final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                                            final AccountInternalApi accountInternalApi,
                                            final InvoiceInternalApi invoiceApi,
                                            final TagInternalApi tagUserApi,
                                            final PaymentDao paymentDao,
                                            final NonEntityDao nonEntityDao,
                                            final GlobalLocker locker,
                                            @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor,
                                            final PluginControlledPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner,
                                            final RetryStateMachineHelper retrySMHelper,
                                            final Clock clock,
                                            final CacheControllerDispatcher controllerDispatcher) {
        super(pluginRegistry, accountInternalApi, paymentDao, nonEntityDao, tagUserApi, locker, executor, invoiceApi, clock, controllerDispatcher);
        this.retrySMHelper = retrySMHelper;
        this.pluginControlledPaymentAutomatonRunner = pluginControlledPaymentAutomatonRunner;
        this.controllerDispatcher = controllerDispatcher;
    }

    public Payment createAuthorization(final boolean isApiPayment, final Account account, final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency, final String paymentExternalKey, final String transactionExternalKey,
                                             final Iterable<PluginProperty> properties, final String paymentControlPluginName, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return pluginControlledPaymentAutomatonRunner.run(isApiPayment,
                                                                TransactionType.AUTHORIZE,
                                                                account,
                                                                paymentMethodId,
                                                                paymentId,
                                                                paymentExternalKey,
                                                                transactionExternalKey,
                                                                amount,
                                                                currency,
                                                                properties,
                                                                paymentControlPluginName,
                                                                callContext, internalCallContext);
    }

    public Payment createCapture(final boolean isApiPayment, final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency,
                                       final String transactionExternalKey,
                                       final Iterable<PluginProperty> properties, final String paymentControlPluginName,
                                       final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return pluginControlledPaymentAutomatonRunner.run(isApiPayment,
                                                                TransactionType.CAPTURE,
                                                                account,
                                                                null,
                                                                paymentId,
                                                                null,
                                                                transactionExternalKey,
                                                                amount,
                                                                currency,
                                                                properties,
                                                                paymentControlPluginName,
                                                                callContext, internalCallContext);
    }

    public Payment createPurchase(final boolean isApiPayment, final Account account, final UUID paymentMethodId, final UUID paymentId, final BigDecimal amount, final Currency currency,
                                        final String paymentExternalKey, final String transactionExternalKey, final Iterable<PluginProperty> properties,
                                        final String paymentControlPluginName, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return pluginControlledPaymentAutomatonRunner.run(isApiPayment,
                                                                TransactionType.PURCHASE,
                                                                account,
                                                                paymentMethodId,
                                                                paymentId,
                                                                paymentExternalKey,
                                                                transactionExternalKey,
                                                                amount,
                                                                currency,
                                                                properties,
                                                                paymentControlPluginName,
                                                                callContext, internalCallContext);
    }

    public Payment createVoid(final boolean isApiPayment, final Account account, final UUID paymentId, final String transactionExternalKey,
                                    final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return pluginControlledPaymentAutomatonRunner.run(isApiPayment,
                                                                TransactionType.VOID,
                                                                account,
                                                                null,
                                                                paymentId,
                                                                null,
                                                                transactionExternalKey,
                                                                null,
                                                                null,
                                                                properties,
                                                                null,
                                                                callContext, internalCallContext);
    }

    public Payment createRefund(final boolean isApiPayment, final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency, final String transactionExternalKey,
                                      final Iterable<PluginProperty> properties, final String paymentControlPluginName, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return pluginControlledPaymentAutomatonRunner.run(isApiPayment,
                                                                TransactionType.REFUND,
                                                                account,
                                                                null,
                                                                paymentId,
                                                                null,
                                                                transactionExternalKey,
                                                                amount,
                                                                currency,
                                                                properties,
                                                                paymentControlPluginName,
                                                                callContext, internalCallContext);
    }

    public Payment createCredit(final boolean isApiPayment, final Account account, final UUID paymentMethodId, final UUID paymentId, final BigDecimal amount, final Currency currency, final String paymentExternalKey,
                                      final String transactionExternalKey, final Iterable<PluginProperty> properties, final String paymentControlPluginName, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {

        return pluginControlledPaymentAutomatonRunner.run(isApiPayment,
                                                                TransactionType.CREDIT,
                                                                account,
                                                                paymentMethodId,
                                                                paymentId,
                                                                paymentExternalKey,
                                                                transactionExternalKey,
                                                                amount,
                                                                currency,
                                                                properties,
                                                                paymentControlPluginName,
                                                                callContext, internalCallContext);
    }

    public Payment createChargeback(final Account account, final UUID paymentId, final String transactionExternalKey, final BigDecimal amount, final Currency currency,
                                          final String paymentControlPluginName, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return pluginControlledPaymentAutomatonRunner.run(true,
                                                                TransactionType.CHARGEBACK,
                                                                account,
                                                                null,
                                                                paymentId,
                                                                null,
                                                                transactionExternalKey,
                                                                amount,
                                                                currency,
                                                                ImmutableList.<PluginProperty>of(),
                                                                paymentControlPluginName,
                                                                callContext, internalCallContext);
    }

    public void retryPaymentTransaction(final UUID attemptId, final String pluginName, final InternalCallContext internalCallContext) {
        try {

            final PaymentAttemptModelDao attempt = paymentDao.getPaymentAttempt(attemptId, internalCallContext);
            final PaymentModelDao payment = paymentDao.getPaymentByExternalKey(attempt.getPaymentExternalKey(), internalCallContext);
            final UUID paymentId = payment != null ? payment.getId() : null;

            final Iterable<PluginProperty> pluginProperties = PluginPropertySerializer.deserialize(attempt.getPluginProperties());
            final Account account = accountInternalApi.getAccountById(attempt.getAccountId(), internalCallContext);
            final UUID tenantId = nonEntityDao.retrieveIdFromObject(internalCallContext.getTenantRecordId(), ObjectType.TENANT, controllerDispatcher.getCacheController(CacheType.OBJECT_ID));
            final CallContext callContext = internalCallContext.toCallContext(tenantId);

            final State state = retrySMHelper.getState(attempt.getStateName());
            pluginControlledPaymentAutomatonRunner.run(state,
                                                             false,
                                                             attempt.getTransactionType(),
                                                             account,
                                                             attempt.getPaymentMethodId(),
                                                             paymentId,
                                                             attempt.getPaymentExternalKey(),
                                                             attempt.getTransactionExternalKey(),
                                                             attempt.getAmount(),
                                                             attempt.getCurrency(),
                                                             pluginProperties,
                                                             pluginName,
                                                             callContext,
                                                             internalCallContext);

        } catch (AccountApiException e) {
            log.warn("Failed to retry attempt " + attemptId + " for plugin " + pluginName, e);
        } catch (PaymentApiException e) {
            log.warn("Failed to retry attempt " + attemptId + " for plugin " + pluginName, e);
        } catch (PluginPropertySerializerException e) {
            log.warn("Failed to retry attempt " + attemptId + " for plugin " + pluginName, e);
        } catch (MissingEntryException e) {
            log.warn("Failed to retry attempt " + attemptId + " for plugin " + pluginName, e);
        }
    }
}
