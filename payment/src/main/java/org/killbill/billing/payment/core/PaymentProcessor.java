/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.payment.core;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.automaton.State;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.DefaultPayment;
import org.killbill.billing.payment.api.DefaultPaymentTransaction;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.sm.PaymentAutomatonRunner;
import org.killbill.billing.payment.core.sm.PaymentStateMachineHelper;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.EntityPaginationBuilder;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.SourcePaginationBuilder;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.inject.name.Named;

import static org.killbill.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;
import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPagination;
import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationFromPlugins;

public class PaymentProcessor extends ProcessorBase {

    private final PaymentAutomatonRunner paymentAutomatonRunner;
    private final InternalCallContextFactory internalCallContextFactory;
    private final PaymentStateMachineHelper paymentSMHelper;

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    @Inject
    public PaymentProcessor(final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                            final AccountInternalApi accountUserApi,
                            final InvoiceInternalApi invoiceApi,
                            final TagInternalApi tagUserApi,
                            final PaymentDao paymentDao,
                            final NonEntityDao nonEntityDao,
                            final InternalCallContextFactory internalCallContextFactory,
                            final GlobalLocker locker,
                            @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor,
                            final PaymentAutomatonRunner paymentAutomatonRunner,
                            final PaymentStateMachineHelper paymentSMHelper,
                            final Clock clock) {
        super(pluginRegistry, accountUserApi, paymentDao, nonEntityDao, tagUserApi, locker, executor, invoiceApi, clock);
        this.paymentSMHelper = paymentSMHelper;
        this.internalCallContextFactory = internalCallContextFactory;
        this.paymentAutomatonRunner = paymentAutomatonRunner;
    }

    public Payment createAuthorization(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, @Nullable final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency,
                                       @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey, final boolean shouldLockAccountAndDispatch,
                                       final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.AUTHORIZE, account, paymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey, shouldLockAccountAndDispatch, properties, callContext, internalCallContext);
    }

    public Payment createCapture(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency,
                                 @Nullable final String paymentTransactionExternalKey, final boolean shouldLockAccountAndDispatch,
                                 final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {

        return performOperation(isApiPayment, attemptId, TransactionType.CAPTURE, account, null, paymentId, amount, currency, null, paymentTransactionExternalKey, shouldLockAccountAndDispatch, properties, callContext, internalCallContext);
    }

    public Payment createPurchase(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, @Nullable final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency,
                                  @Nullable  final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey, final boolean shouldLockAccountAndDispatch,
                                  final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.PURCHASE, account, paymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey, shouldLockAccountAndDispatch, properties, callContext, internalCallContext);
    }

    public Payment createVoid(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, final UUID paymentId, @Nullable final String paymentTransactionExternalKey, final boolean shouldLockAccountAndDispatch,
                              final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.VOID, account, null, paymentId, null, null, null, paymentTransactionExternalKey, shouldLockAccountAndDispatch, properties, callContext, internalCallContext);
    }

    public Payment createRefund(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency,
                                final String paymentTransactionExternalKey, final boolean shouldLockAccountAndDispatch,
                                final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.REFUND, account, null, paymentId, amount, currency, null, paymentTransactionExternalKey, shouldLockAccountAndDispatch, properties, callContext, internalCallContext);
    }

    public Payment createCredit(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, @Nullable final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency,
                                @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey, final boolean shouldLockAccountAndDispatch,
                                final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.CREDIT, account, paymentMethodId, paymentId, amount, currency, paymentExternalKey, paymentTransactionExternalKey, shouldLockAccountAndDispatch, properties, callContext, internalCallContext);
    }

    public Payment createChargeback(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, final UUID paymentId, @Nullable final String paymentTransactionExternalKey, final BigDecimal amount, final Currency currency, final boolean shouldLockAccountAndDispatch,
                                    final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.CHARGEBACK, account, null, paymentId, amount, currency, null, paymentTransactionExternalKey, shouldLockAccountAndDispatch, ImmutableList.<PluginProperty>of(), callContext, internalCallContext);
    }

    public List<Payment> getAccountPayments(final UUID accountId, final InternalTenantContext tenantContext) throws PaymentApiException {
        final List<PaymentModelDao> paymentsModelDao = paymentDao.getPaymentsForAccount(accountId, tenantContext);
        final List<PaymentTransactionModelDao> transactionsModelDao = paymentDao.getTransactionsForAccount(accountId, tenantContext);

        return Lists.<PaymentModelDao, Payment>transform(paymentsModelDao,
                                                         new Function<PaymentModelDao, Payment>() {
                                                             @Override
                                                             public Payment apply(final PaymentModelDao curPaymentModelDao) {
                                                                 return toPayment(curPaymentModelDao, transactionsModelDao, null);
                                                             }
                                                         }
                                                        );
    }

    public void notifyPendingPaymentOfStateChanged(final Account account, UUID transactionId, final boolean isSuccess, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {

        final PaymentTransactionModelDao transactionModelDao = paymentDao.getPaymentTransaction(transactionId, internalCallContext);
        if (transactionModelDao.getTransactionStatus() != TransactionStatus.PENDING) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_SUCCESS_PAYMENT, transactionModelDao.getPaymentId());

        }
        final PaymentModelDao paymentModelDao = paymentDao.getPayment(transactionModelDao.getPaymentId(), internalCallContext);
        Preconditions.checkState(paymentModelDao != null);

        final TransactionStatus newStatus = isSuccess ? TransactionStatus.SUCCESS : TransactionStatus.PAYMENT_FAILURE;
        // STEPH This works if the pending transaction we are trying to update matches is the one that gave the state to the payment. Also can we have multiple PENDING for a given payment?
        final State currentPaymentState;
        final String stateName = paymentModelDao.getStateName();
        final String lastSuccessPaymentStateStrOrNull = paymentSMHelper.isSuccessState(stateName) ? stateName : null;
        paymentDao.updatePaymentAndTransactionOnCompletion(account.getId(), transactionModelDao.getPaymentId(), transactionModelDao.getTransactionType(),
                                                           stateName, lastSuccessPaymentStateStrOrNull, transactionModelDao.getId(), newStatus,
                                                           transactionModelDao.getProcessedAmount(), transactionModelDao.getProcessedCurrency(),
                                                           transactionModelDao.getGatewayErrorCode(), transactionModelDao.getGatewayErrorMsg(), internalCallContext);
    }

    public Payment getPayment(final UUID paymentId, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentModelDao paymentModelDao = paymentDao.getPayment(paymentId, internalTenantContext);
        if (paymentModelDao == null) {
            return null;
        }
        return getPayment(paymentModelDao, withPluginInfo, properties, tenantContext, internalTenantContext);
    }

    public Payment getPaymentByExternalKey(final String paymentExternalKey, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentModelDao paymentModelDao = paymentDao.getPaymentByExternalKey(paymentExternalKey, internalTenantContext);
        if (paymentModelDao == null) {
            return null;
        }
        return getPayment(paymentModelDao, withPluginInfo, properties, tenantContext, internalTenantContext);

    }

    public Pagination<Payment> getPayments(final Long offset, final Long limit, final Iterable<PluginProperty> properties,
                                           final TenantContext tenantContext, final InternalTenantContext internalTenantContext) {
        return getEntityPaginationFromPlugins(getAvailablePlugins(),
                                              offset,
                                              limit,
                                              new EntityPaginationBuilder<Payment, PaymentApiException>() {
                                                  @Override
                                                  public Pagination<Payment> build(final Long offset, final Long limit, final String pluginName) throws PaymentApiException {
                                                      final Pagination<Payment> result = getPayments(offset, limit, pluginName, properties, tenantContext, internalTenantContext);
                                                      return result;
                                                  }
                                              }
                                             );
    }

    public Pagination<Payment> getPayments(final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentPluginApi pluginApi = getPaymentPluginApi(pluginName);

        return getEntityPagination(limit,
                                   new SourcePaginationBuilder<PaymentModelDao, PaymentApiException>() {
                                       @Override
                                       public Pagination<PaymentModelDao> build() {
                                           // Find all payments for all accounts
                                           final Pagination<PaymentModelDao> result = paymentDao.getPayments(pluginName, offset, limit, internalTenantContext);
                                           return result;
                                       }
                                   },
                                   new Function<PaymentModelDao, Payment>() {
                                       @Override
                                       public Payment apply(final PaymentModelDao paymentModelDao) {
                                           List<PaymentTransactionInfoPlugin> pluginInfo = null;
                                           try {
                                               pluginInfo = pluginApi.getPaymentInfo(paymentModelDao.getAccountId(), paymentModelDao.getId(), properties, tenantContext);
                                           } catch (final PaymentPluginApiException e) {
                                               log.warn("Unable to find payment id " + paymentModelDao.getId() + " in plugin " + pluginName);
                                               // We still want to return a payment object, even though the plugin details are missing
                                           }

                                           return toPayment(paymentModelDao.getId(), pluginInfo, internalTenantContext);
                                       }
                                   }
                                  );
    }

    public Pagination<Payment> searchPayments(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) {
        return getEntityPaginationFromPlugins(getAvailablePlugins(),
                                              offset,
                                              limit,
                                              new EntityPaginationBuilder<Payment, PaymentApiException>() {
                                                  @Override
                                                  public Pagination<Payment> build(final Long offset, final Long limit, final String pluginName) throws PaymentApiException {
                                                      return searchPayments(searchKey, offset, limit, pluginName, properties, tenantContext, internalTenantContext);
                                                  }
                                              }
                                             );
    }

    public Pagination<Payment> searchPayments(final String searchKey, final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentPluginApi pluginApi = getPaymentPluginApi(pluginName);

        return getEntityPagination(limit,
                                   new SourcePaginationBuilder<PaymentTransactionInfoPlugin, PaymentApiException>() {
                                       @Override
                                       public Pagination<PaymentTransactionInfoPlugin> build() throws PaymentApiException {
                                           try {
                                               return pluginApi.searchPayments(searchKey, offset, limit, properties, tenantContext);
                                           } catch (final PaymentPluginApiException e) {
                                               throw new PaymentApiException(e, ErrorCode.PAYMENT_PLUGIN_SEARCH_PAYMENTS, pluginName, searchKey);
                                           }
                                       }

                                   },
                                   new Function<PaymentTransactionInfoPlugin, Payment>() {

                                       final List<PaymentTransactionInfoPlugin> cachedPaymentTransactions = new LinkedList<PaymentTransactionInfoPlugin>();

                                       @Override
                                       public Payment apply(final PaymentTransactionInfoPlugin pluginTransaction) {

                                           if (pluginTransaction.getKbPaymentId() == null) {
                                               // Garbage from the plugin?
                                               log.debug("Plugin {} returned a payment without a kbPaymentId for searchKey {}", pluginName, searchKey);
                                               return null;
                                           }

                                           if (cachedPaymentTransactions.isEmpty() ||
                                               (cachedPaymentTransactions.get(0).getKbPaymentId().equals(pluginTransaction.getKbPaymentId()))) {
                                               cachedPaymentTransactions.add(pluginTransaction);
                                               return null;
                                           } else {
                                               final Payment result = toPayment(pluginTransaction.getKbPaymentId(), ImmutableList.<PaymentTransactionInfoPlugin>copyOf(cachedPaymentTransactions), internalTenantContext);
                                               cachedPaymentTransactions.clear();
                                               cachedPaymentTransactions.add(pluginTransaction);
                                               return result;
                                           }
                                       }
                                   }
                                  );
    }

    public Payment toPayment(final UUID paymentId, @Nullable final List<PaymentTransactionInfoPlugin> pluginTransactions, final InternalTenantContext tenantContext) {
        final PaymentModelDao paymentModelDao = paymentDao.getPayment(paymentId, tenantContext);
        if (paymentModelDao == null) {
            log.warn("Unable to find payment id " + paymentId);
            return null;
        }

        final InternalTenantContext tenantContextWithAccountRecordId = internalCallContextFactory.createInternalTenantContext(paymentModelDao.getAccountId(), tenantContext);
        final List<PaymentTransactionModelDao> transactionsForAccount = paymentDao.getTransactionsForAccount(paymentModelDao.getAccountId(), tenantContextWithAccountRecordId);

        return toPayment(paymentModelDao, transactionsForAccount, pluginTransactions);
    }

    private Payment performOperation(final boolean isApiPayment, @Nullable final UUID attemptId, final TransactionType transactionType, final Account account, final UUID paymentMethodId, final UUID paymentId, final BigDecimal amount, final Currency currency,
                                     @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey,
                                     final boolean shouldLockAccountAndDispatch, final Iterable<PluginProperty> properties, final CallContext callContext,
                                     final InternalCallContext internalCallContext) throws PaymentApiException {

        validateUniqueTransactionExternalKey(paymentTransactionExternalKey, internalCallContext);

        final UUID nonNullPaymentId = paymentAutomatonRunner.run(isApiPayment,
                                                                 transactionType,
                                                                 account,
                                                                 attemptId,
                                                                 paymentMethodId,
                                                                 paymentId,
                                                                 paymentExternalKey,
                                                                 paymentTransactionExternalKey,
                                                                 amount,
                                                                 currency,
                                                                 shouldLockAccountAndDispatch,
                                                                 properties,
                                                                 callContext,
                                                                 internalCallContext);
        return getPayment(nonNullPaymentId, true, properties, callContext, internalCallContext);
    }

    private Payment getPayment(final PaymentModelDao paymentModelDao, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context, final InternalTenantContext tenantContext) throws PaymentApiException {
        final InternalTenantContext tenantContextWithAccountRecordId;
        if (tenantContext.getAccountRecordId() == null) {
            tenantContextWithAccountRecordId = internalCallContextFactory.createInternalTenantContext(paymentModelDao.getAccountId(), tenantContext);
        } else {
            tenantContextWithAccountRecordId = tenantContext;
        }
        final List<PaymentTransactionModelDao> transactionsForPayment = paymentDao.getTransactionsForPayment(paymentModelDao.getId(), tenantContextWithAccountRecordId);

        final PaymentPluginApi plugin = withPluginInfo ? getPaymentProviderPlugin(paymentModelDao.getPaymentMethodId(), tenantContext) : null;
        List<PaymentTransactionInfoPlugin> pluginInfo = null;
        if (plugin != null) {
            try {
                pluginInfo = plugin.getPaymentInfo(paymentModelDao.getAccountId(), paymentModelDao.getId(), properties, context);
            } catch (final PaymentPluginApiException e) {
                throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_GET_PAYMENT_INFO, paymentModelDao.getId(), e.toString());
            }
        }
        return toPayment(paymentModelDao, transactionsForPayment, pluginInfo);
    }

    private Payment toPayment(final PaymentModelDao curPaymentModelDao, final Iterable<PaymentTransactionModelDao> transactionsModelDao, @Nullable final List<PaymentTransactionInfoPlugin> pluginTransactions) {
        final Ordering<PaymentTransaction> perPaymentTransactionOrdering = Ordering.<PaymentTransaction>from(new Comparator<PaymentTransaction>() {
            @Override
            public int compare(final PaymentTransaction o1, final PaymentTransaction o2) {
                return o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
            }
        });

        final Iterable<PaymentTransactionModelDao> filteredTransactions = Iterables.filter(transactionsModelDao, new Predicate<PaymentTransactionModelDao>() {
            @Override
            public boolean apply(final PaymentTransactionModelDao curPaymentTransactionModelDao) {
                return curPaymentTransactionModelDao.getPaymentId().equals(curPaymentModelDao.getId());
            }
        });

        final Iterable<PaymentTransaction> transactions = Iterables.transform(filteredTransactions, new Function<PaymentTransactionModelDao, PaymentTransaction>() {
            @Override
            public PaymentTransaction apply(final PaymentTransactionModelDao input) {

                final PaymentTransactionInfoPlugin info = pluginTransactions != null ?
                                                          Iterables.tryFind(pluginTransactions, new Predicate<PaymentTransactionInfoPlugin>() {
                                                              @Override
                                                              public boolean apply(final PaymentTransactionInfoPlugin input) {
                                                                  return input.getKbTransactionPaymentId().equals(input.getKbTransactionPaymentId());
                                                              }
                                                          }).orNull() : null;

                return new DefaultPaymentTransaction(input.getId(), input.getAttemptId(), input.getTransactionExternalKey(), input.getCreatedDate(), input.getUpdatedDate(), input.getPaymentId(),
                                                     input.getTransactionType(), input.getEffectiveDate(), input.getTransactionStatus(), input.getAmount(), input.getCurrency(),
                                                     input.getProcessedAmount(), input.getProcessedCurrency(),
                                                     input.getGatewayErrorCode(), input.getGatewayErrorMsg(), info);
            }
        });

        final List<PaymentTransaction> sortedTransactions = perPaymentTransactionOrdering.immutableSortedCopy(transactions);
        return new DefaultPayment(curPaymentModelDao.getId(), curPaymentModelDao.getCreatedDate(), curPaymentModelDao.getUpdatedDate(), curPaymentModelDao.getAccountId(),
                                  curPaymentModelDao.getPaymentMethodId(), curPaymentModelDao.getPaymentNumber(), curPaymentModelDao.getExternalKey(), sortedTransactions);
    }
}
