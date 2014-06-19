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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.automaton.State;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.sm.PluginControlledDirectPaymentAutomatonRunner;
import org.killbill.billing.payment.dao.DirectPaymentModelDao;
import org.killbill.billing.payment.dao.DirectPaymentTransactionModelDao;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PluginPropertyModelDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.name.Named;

import static org.killbill.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;

public class PluginControlledPaymentProcessor extends ProcessorBase {

    private final PluginControlledDirectPaymentAutomatonRunner pluginControlledDirectPaymentAutomatonRunner;

    @Inject
    public PluginControlledPaymentProcessor(final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                                            final AccountInternalApi accountInternalApi,
                                            final InvoiceInternalApi invoiceApi,
                                            final TagInternalApi tagUserApi,
                                            final PaymentDao paymentDao,
                                            final NonEntityDao nonEntityDao,
                                            final PersistentBus eventBus,
                                            final GlobalLocker locker,
                                            @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor,
                                            final PluginControlledDirectPaymentAutomatonRunner pluginControlledDirectPaymentAutomatonRunner,
                                            final Clock clock) {
        super(pluginRegistry, accountInternalApi, eventBus, paymentDao, nonEntityDao, tagUserApi, locker, executor, invoiceApi, clock);

        this.pluginControlledDirectPaymentAutomatonRunner = pluginControlledDirectPaymentAutomatonRunner;
    }

    public DirectPayment createAuthorization(final boolean isApiPayment, final Account account, final UUID paymentMethodId, @Nullable final UUID directPaymentId, final BigDecimal amount, final Currency currency, final String paymentExternalKey, final String transactionExternalKey,
                                             final Iterable<PluginProperty> properties, final String paymentControlPluginName, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return pluginControlledDirectPaymentAutomatonRunner.run(isApiPayment,
                                                         TransactionType.AUTHORIZE,
                                                         account,
                                                         paymentMethodId,
                                                         directPaymentId,
                                                         paymentExternalKey,
                                                         transactionExternalKey,
                                                         amount,
                                                         currency,
                                                         properties,
                                                         paymentControlPluginName,
                                                         callContext, internalCallContext);
    }

    public DirectPayment createCapture(final boolean isApiPayment, final Account account, final UUID directPaymentId, final BigDecimal amount, final Currency currency,
                                       final String transactionExternalKey,
                                       final Iterable<PluginProperty> properties, final String paymentControlPluginName,
                                       final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return pluginControlledDirectPaymentAutomatonRunner.run(isApiPayment,
                                                         TransactionType.CAPTURE,
                                                         account,
                                                         directPaymentId,
                                                         transactionExternalKey,
                                                         amount,
                                                         currency,
                                                         properties,
                                                         paymentControlPluginName,
                                                         callContext, internalCallContext);
    }

    public DirectPayment createPurchase(final boolean isApiPayment, final Account account, final UUID paymentMethodId, final UUID directPaymentId, final BigDecimal amount, final Currency currency,
                                        final String paymentExternalKey, final String transactionExternalKey, final Iterable<PluginProperty> properties,
                                        final String paymentControlPluginName, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return pluginControlledDirectPaymentAutomatonRunner.run(isApiPayment,
                                                         TransactionType.PURCHASE,
                                                         account,
                                                         paymentMethodId,
                                                         directPaymentId,
                                                         paymentExternalKey,
                                                         transactionExternalKey,
                                                         amount,
                                                         currency,
                                                         properties,
                                                         paymentControlPluginName,
                                                         callContext, internalCallContext);
    }

    public DirectPayment createVoid(final boolean isApiPayment, final Account account, final UUID directPaymentId, final String transactionExternalKey,
                                    final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return pluginControlledDirectPaymentAutomatonRunner.run(isApiPayment,
                                                         TransactionType.VOID,
                                                         account,
                                                         directPaymentId,
                                                         transactionExternalKey,
                                                         properties,
                                                         null,
                                                         callContext, internalCallContext);
    }

    public DirectPayment createRefund(final boolean isApiPayment, final Account account, final UUID directPaymentId, final BigDecimal amount, final Currency currency, final String transactionExternalKey,
                                      final Iterable<PluginProperty> properties, final String paymentControlPluginName, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return pluginControlledDirectPaymentAutomatonRunner.run(isApiPayment,
                                                         TransactionType.REFUND,
                                                         account,
                                                         directPaymentId,
                                                         transactionExternalKey,
                                                         amount,
                                                         currency,
                                                         properties,
                                                         paymentControlPluginName,
                                                         callContext, internalCallContext);
    }

    public DirectPayment createCredit(final boolean isApiPayment, final Account account, final UUID paymentMethodId, final UUID directPaymentId, final BigDecimal amount, final Currency currency, final String paymentExternalKey,
                                      final String transactionExternalKey, final Iterable<PluginProperty> properties, final String paymentControlPluginName, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {

        return pluginControlledDirectPaymentAutomatonRunner.run(isApiPayment,
                                                         TransactionType.CREDIT,
                                                         account,
                                                         paymentMethodId,
                                                         directPaymentId,
                                                         paymentExternalKey,
                                                         transactionExternalKey,
                                                         amount,
                                                         currency,
                                                         properties,
                                                         paymentControlPluginName,
                                                         callContext, internalCallContext);
    }

    public void retryPaymentTransaction(final String transactionExternalKey, final String pluginName, final InternalCallContext internalCallContext) {
        try {

            final PaymentAttemptModelDao attempt = paymentDao.getPaymentAttemptByExternalKey(transactionExternalKey, internalCallContext);
            final DirectPaymentTransactionModelDao transaction = paymentDao.getDirectPaymentTransactionByExternalKey(transactionExternalKey, internalCallContext);
            final DirectPaymentModelDao payment = paymentDao.getDirectPayment(transaction.getDirectPaymentId(), internalCallContext);

            final List<PluginPropertyModelDao> properties = paymentDao.getProperties(transactionExternalKey, internalCallContext);

            final List<PluginProperty> pluginProperties = properties == null ?
                                                          ImmutableList.<PluginProperty>of() :
                                                          ImmutableList.<PluginProperty>copyOf(Iterables.transform(properties, new Function<PluginPropertyModelDao, PluginProperty>() {
                @Nullable
                @Override
                public PluginProperty apply(final PluginPropertyModelDao input) {
                    return new PluginProperty(input.getPropKey(), input.getPropValue(), false);
                }
            }));

            final Account account = accountInternalApi.getAccountById(payment.getAccountId(), internalCallContext);
            final UUID tenantId = nonEntityDao.retrieveIdFromObject(internalCallContext.getTenantRecordId(), ObjectType.TENANT);
            final CallContext callContext = internalCallContext.toCallContext(tenantId);


            // STEPH Nope, keep the original transactionExternalKey
            final String newTransactionExternalKey = UUID.randomUUID().toString();
            final State state = pluginControlledDirectPaymentAutomatonRunner.fetchState(attempt.getStateName());
            pluginControlledDirectPaymentAutomatonRunner.run(state,
                                                      false,
                                                      transaction.getTransactionType(),
                                                      account,
                                                      payment.getPaymentMethodId(),
                                                      payment.getId(),
                                                      payment.getExternalKey(),
                                                      newTransactionExternalKey,
                                                      transaction.getAmount(),
                                                      transaction.getCurrency(),
                                                      pluginProperties,
                                                      pluginName,
                                                      callContext,
                                                      internalCallContext);

        } catch (AccountApiException e) {
            e.printStackTrace();
        } catch (PaymentApiException e) {
            e.printStackTrace();
        }

    }

}
