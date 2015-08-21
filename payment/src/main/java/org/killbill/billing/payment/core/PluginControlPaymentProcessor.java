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

package org.killbill.billing.payment.core;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.automaton.MissingEntryException;
import org.killbill.automaton.State;
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
import org.killbill.billing.payment.core.sm.PaymentControlStateMachineHelper;
import org.killbill.billing.payment.core.sm.PluginControlPaymentAutomatonRunner;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PluginPropertySerializer;
import org.killbill.billing.payment.dao.PluginPropertySerializer.PluginPropertySerializerException;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

public class PluginControlPaymentProcessor extends ProcessorBase {

    private static final Joiner JOINER = Joiner.on(", ");

    private final PluginControlPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner;
    private final PaymentControlStateMachineHelper paymentControlStateMachineHelper;

    @Inject
    public PluginControlPaymentProcessor(final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                                         final AccountInternalApi accountInternalApi,
                                         final InvoiceInternalApi invoiceApi,
                                         final TagInternalApi tagUserApi,
                                         final PaymentDao paymentDao,
                                         final GlobalLocker locker,
                                         final InternalCallContextFactory internalCallContextFactory,
                                         final PluginControlPaymentAutomatonRunner pluginControlledPaymentAutomatonRunner,
                                         final PaymentControlStateMachineHelper paymentControlStateMachineHelper,
                                         final Clock clock) {
        super(pluginRegistry, accountInternalApi, paymentDao, tagUserApi, locker, internalCallContextFactory, invoiceApi, clock);
        this.paymentControlStateMachineHelper = paymentControlStateMachineHelper;
        this.pluginControlledPaymentAutomatonRunner = pluginControlledPaymentAutomatonRunner;
    }

    public Payment createAuthorization(final boolean isApiPayment, final Account account, final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency, final String paymentExternalKey, final String transactionExternalKey,
                                       final Iterable<PluginProperty> properties, final List<String> paymentControlPluginNames, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
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
                                                          paymentControlPluginNames,
                                                          callContext, internalCallContext);
    }

    public Payment createCapture(final boolean isApiPayment, final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency,
                                 final String transactionExternalKey,
                                 final Iterable<PluginProperty> properties, final List<String> paymentControlPluginNames,
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
                                                          paymentControlPluginNames,
                                                          callContext, internalCallContext);
    }

    public Payment createPurchase(final boolean isApiPayment, final Account account, final UUID paymentMethodId, final UUID paymentId, final BigDecimal amount, final Currency currency,
                                  final String paymentExternalKey, final String transactionExternalKey, final Iterable<PluginProperty> properties,
                                  final List<String> paymentControlPluginNames, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
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
                                                          paymentControlPluginNames,
                                                          callContext, internalCallContext);
    }

    public Payment createVoid(final boolean isApiPayment, final Account account, final UUID paymentId, final String transactionExternalKey,
                              final Iterable<PluginProperty> properties, final List<String> paymentControlPluginNames, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
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
                                                          paymentControlPluginNames,
                                                          callContext, internalCallContext);
    }

    public Payment createRefund(final boolean isApiPayment, final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency, final String transactionExternalKey,
                                final Iterable<PluginProperty> properties, final List<String> paymentControlPluginNames, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
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
                                                          paymentControlPluginNames,
                                                          callContext, internalCallContext);
    }

    public Payment createCredit(final boolean isApiPayment, final Account account, final UUID paymentMethodId, final UUID paymentId, final BigDecimal amount, final Currency currency, final String paymentExternalKey,
                                final String transactionExternalKey, final Iterable<PluginProperty> properties, final List<String> paymentControlPluginNames, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {

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
                                                          paymentControlPluginNames,
                                                          callContext, internalCallContext);
    }

    public Payment createChargeback(final boolean isApiPayment, final Account account, final UUID paymentId, final String transactionExternalKey, final BigDecimal amount, final Currency currency,
                                    final List<String> paymentControlPluginNames, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return pluginControlledPaymentAutomatonRunner.run(isApiPayment,
                                                          TransactionType.CHARGEBACK,
                                                          account,
                                                          null,
                                                          paymentId,
                                                          null,
                                                          transactionExternalKey,
                                                          amount,
                                                          currency,
                                                          ImmutableList.<PluginProperty>of(),
                                                          paymentControlPluginNames,
                                                          callContext, internalCallContext);
    }

    public void retryPaymentTransaction(final UUID attemptId, final List<String> paymentControlPluginNames, final InternalCallContext internalCallContext) {
        try {
            final PaymentAttemptModelDao attempt = paymentDao.getPaymentAttempt(attemptId, internalCallContext);
            final PaymentModelDao payment = paymentDao.getPaymentByExternalKey(attempt.getPaymentExternalKey(), internalCallContext);
            final UUID paymentId = payment != null ? payment.getId() : null;

            final Iterable<PluginProperty> pluginProperties = PluginPropertySerializer.deserialize(attempt.getPluginProperties());
            final Account account = accountInternalApi.getAccountById(attempt.getAccountId(), internalCallContext);
            final CallContext callContext = buildCallContext(internalCallContext);

            final State state = paymentControlStateMachineHelper.getState(attempt.getStateName());
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
                                                       paymentControlPluginNames,
                                                       callContext,
                                                       internalCallContext);

        } catch (final AccountApiException e) {
            log.warn("Failed to retry attempt " + attemptId + toPluginNamesOnError(" for plugins ", paymentControlPluginNames), e);
        } catch (final PaymentApiException e) {
            log.warn("Failed to retry attempt " + attemptId + toPluginNamesOnError(" for plugins ", paymentControlPluginNames), e);
        } catch (final PluginPropertySerializerException e) {
            log.warn("Failed to retry attempt " + attemptId + toPluginNamesOnError(" for plugins ", paymentControlPluginNames), e);
        } catch (final MissingEntryException e) {
            log.warn("Failed to retry attempt " + attemptId + toPluginNamesOnError(" for plugins ", paymentControlPluginNames), e);
        }
    }

    private String toPluginNamesOnError(final String prefixMessage, final Collection<String> paymentControlPluginNames) {
        if (paymentControlPluginNames == null || paymentControlPluginNames.isEmpty()) {
            return "";
        }
        return prefixMessage + "(" + JOINER.join(paymentControlPluginNames) + ")";
    }
}
