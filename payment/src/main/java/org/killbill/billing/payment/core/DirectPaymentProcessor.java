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

import org.joda.time.DateTime;
import org.killbill.automaton.State;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.events.BusInternalEvent;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.DefaultDirectPayment;
import org.killbill.billing.payment.api.DefaultDirectPaymentTransaction;
import org.killbill.billing.payment.api.DefaultPaymentErrorEvent;
import org.killbill.billing.payment.api.DefaultPaymentInfoEvent;
import org.killbill.billing.payment.api.DefaultPaymentPluginErrorEvent;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.DirectPaymentTransaction;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.sm.DirectPaymentAutomatonRunner;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
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

public class DirectPaymentProcessor extends ProcessorBase {

    private final DirectPaymentAutomatonRunner directPaymentAutomatonRunner;
    private final InternalCallContextFactory internalCallContextFactory;

    private static final Logger log = LoggerFactory.getLogger(DirectPaymentProcessor.class);

    @Inject
    public DirectPaymentProcessor(final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                                  final AccountInternalApi accountUserApi,
                                  final InvoiceInternalApi invoiceApi,
                                  final TagInternalApi tagUserApi,
                                  final PaymentDao paymentDao,
                                  final NonEntityDao nonEntityDao,
                                  final PersistentBus eventBus,
                                  final InternalCallContextFactory internalCallContextFactory,
                                  final GlobalLocker locker,
                                  @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor,
                                  final DirectPaymentAutomatonRunner directPaymentAutomatonRunner,
                                  final Clock clock) {
        super(pluginRegistry, accountUserApi, eventBus, paymentDao, nonEntityDao, tagUserApi, locker, executor, invoiceApi, clock);
        this.internalCallContextFactory = internalCallContextFactory;
        this.directPaymentAutomatonRunner = directPaymentAutomatonRunner;
    }

    public DirectPayment createAuthorization(final Account account, @Nullable final UUID paymentMethodId, @Nullable final UUID directPaymentId, final BigDecimal amount, final Currency currency,
                                             final String directPaymentExternalKey, final String directPaymentTransactionExternalKey, final boolean shouldLockAccountAndDispatch,
                                             final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        final UUID nonNullDirectPaymentId = directPaymentAutomatonRunner.run(TransactionType.AUTHORIZE,
                                                                             account,
                                                                             paymentMethodId,
                                                                             directPaymentId,
                                                                             directPaymentExternalKey,
                                                                             directPaymentTransactionExternalKey,
                                                                             amount,
                                                                             currency,
                                                                             shouldLockAccountAndDispatch,
                                                                             properties,
                                                                             callContext,
                                                                             internalCallContext);
        // TODO STEPH We should try to post the event from within transaction
        final DirectPayment directPayment = getPayment(nonNullDirectPaymentId, true, properties, callContext, internalCallContext);
        postPaymentEvent(account, directPayment, directPaymentTransactionExternalKey, internalCallContext);
        return directPayment;
    }

    public DirectPayment createCapture(final Account account, final UUID directPaymentId, final BigDecimal amount, final Currency currency,
                                       final String directPaymentTransactionExternalKey, final boolean shouldLockAccountAndDispatch,
                                       final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        final UUID nonNullDirectPaymentId = directPaymentAutomatonRunner.run(TransactionType.CAPTURE,
                                                                             account,
                                                                             directPaymentId,
                                                                             directPaymentTransactionExternalKey,
                                                                             amount,
                                                                             currency,
                                                                             shouldLockAccountAndDispatch,
                                                                             properties,
                                                                             callContext,
                                                                             internalCallContext);

        final DirectPayment directPayment = getPayment(nonNullDirectPaymentId, true, properties, callContext, internalCallContext);
        postPaymentEvent(account, directPayment, directPaymentTransactionExternalKey, internalCallContext);
        return directPayment;
    }

    public DirectPayment createPurchase(final Account account, @Nullable final UUID paymentMethodId, @Nullable final UUID directPaymentId, final BigDecimal amount, final Currency currency,
                                        final String directPaymentExternalKey, final String directPaymentTransactionExternalKey, final boolean shouldLockAccountAndDispatch,
                                        final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        final UUID nonNullDirectPaymentId = directPaymentAutomatonRunner.run(TransactionType.PURCHASE,
                                                                             account,
                                                                             paymentMethodId,
                                                                             directPaymentId,
                                                                             directPaymentExternalKey,
                                                                             directPaymentTransactionExternalKey,
                                                                             amount,
                                                                             currency,
                                                                             shouldLockAccountAndDispatch,
                                                                             properties,
                                                                             callContext,
                                                                             internalCallContext);

        final DirectPayment directPayment = getPayment(nonNullDirectPaymentId, true, properties, callContext, internalCallContext);
        postPaymentEvent(account, directPayment, directPaymentTransactionExternalKey, internalCallContext);
        return directPayment;
    }

    public DirectPayment createVoid(final Account account, final UUID directPaymentId, final String directPaymentTransactionExternalKey, final boolean shouldLockAccountAndDispatch,
                                    final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        final UUID nonNullDirectPaymentId = directPaymentAutomatonRunner.run(TransactionType.VOID,
                                                                             account,
                                                                             directPaymentId,
                                                                             directPaymentTransactionExternalKey,
                                                                             shouldLockAccountAndDispatch,
                                                                             properties,
                                                                             callContext,
                                                                             internalCallContext);
        final DirectPayment directPayment = getPayment(nonNullDirectPaymentId, true, properties, callContext, internalCallContext);
        postPaymentEvent(account, directPayment, directPaymentTransactionExternalKey, internalCallContext);
        return directPayment;
    }

    public DirectPayment createRefund(final Account account, final UUID directPaymentId, final BigDecimal amount, final Currency currency,
                                      final String directPaymentTransactionExternalKey, final boolean shouldLockAccountAndDispatch,
                                      final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        final UUID nonNullDirectPaymentId = directPaymentAutomatonRunner.run(TransactionType.REFUND,
                                                                             account,
                                                                             directPaymentId,
                                                                             directPaymentTransactionExternalKey,
                                                                             amount,
                                                                             currency,
                                                                             shouldLockAccountAndDispatch,
                                                                             properties,
                                                                             callContext,
                                                                             internalCallContext);
        final DirectPayment directPayment = getPayment(nonNullDirectPaymentId, true, properties, callContext, internalCallContext);
        postPaymentEvent(account, directPayment, directPaymentTransactionExternalKey, internalCallContext);
        return directPayment;
    }

    public DirectPayment createCredit(final Account account, @Nullable final UUID paymentMethodId, @Nullable final UUID directPaymentId, final BigDecimal amount, final Currency currency,
                                      final String directPaymentExternalKey, final String directPaymentTransactionExternalKey, final boolean shouldLockAccountAndDispatch,
                                      final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        final UUID nonNullDirectPaymentId = directPaymentAutomatonRunner.run(TransactionType.CREDIT,
                                                                             account,
                                                                             paymentMethodId,
                                                                             directPaymentId,
                                                                             directPaymentExternalKey,
                                                                             directPaymentTransactionExternalKey,
                                                                             amount,
                                                                             currency,
                                                                             shouldLockAccountAndDispatch,
                                                                             properties,
                                                                             callContext,
                                                                             internalCallContext);
        final DirectPayment directPayment = getPayment(nonNullDirectPaymentId, true, properties, callContext, internalCallContext);
        postPaymentEvent(account, directPayment, directPaymentTransactionExternalKey, internalCallContext);
        return directPayment;
    }

    public List<DirectPayment> getAccountPayments(final UUID accountId, final InternalTenantContext tenantContext) throws PaymentApiException {
        final List<PaymentModelDao> paymentsModelDao = paymentDao.getDirectPaymentsForAccount(accountId, tenantContext);
        final List<PaymentTransactionModelDao> transactionsModelDao = paymentDao.getDirectTransactionsForAccount(accountId, tenantContext);

        return Lists.<PaymentModelDao, DirectPayment>transform(paymentsModelDao,
                                                                     new Function<PaymentModelDao, DirectPayment>() {
                                                                         @Override
                                                                         public DirectPayment apply(final PaymentModelDao curPaymentModelDao) {
                                                                             return toDirectPayment(curPaymentModelDao, transactionsModelDao, null);
                                                                         }
                                                                     }
                                                                    );
    }

    public void notifyPendingPaymentOfStateChanged(final Account account, UUID transactionId, final boolean isSuccess, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {

        final PaymentTransactionModelDao transactionModelDao = paymentDao.getDirectPaymentTransaction(transactionId, internalCallContext);
        if (transactionModelDao.getTransactionStatus() != TransactionStatus.PENDING) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_SUCCESS_PAYMENT, transactionModelDao.getPaymentId());

        }
        final PaymentModelDao paymentModelDao = paymentDao.getDirectPayment(transactionModelDao.getPaymentId(), internalCallContext);
        Preconditions.checkState(paymentModelDao != null);

        final TransactionStatus newStatus = isSuccess ? TransactionStatus.SUCCESS : TransactionStatus.PAYMENT_FAILURE;
        // STEPH This works if the pending transaction we are trying to update matches is the one that gave the state to the payment. Also can we have multiple PENDING for a given payment?
        final State currentPaymentState = directPaymentAutomatonRunner.fetchNextState(paymentModelDao.getStateName(), isSuccess);
        // STEPH : should we insert a new transaction row to keep the PENDING one?
        paymentDao.updateDirectPaymentAndTransactionOnCompletion(transactionModelDao.getPaymentId(), currentPaymentState.getName(), transactionModelDao.getId(), newStatus,
                                                                 transactionModelDao.getProcessedAmount(), transactionModelDao.getProcessedCurrency(),
                                                                 transactionModelDao.getGatewayErrorCode(), transactionModelDao.getGatewayErrorMsg(), internalCallContext);
    }

    public void notifyPaymentPaymentOfChargeback(final Account account, final UUID transactionId, final String chargebackTransactionExternalKey, final BigDecimal amount, final Currency currency, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        final PaymentTransactionModelDao transactionModelDao = paymentDao.getDirectPaymentTransaction(transactionId, internalCallContext);
        Preconditions.checkState(transactionModelDao != null);

        final DateTime utcNow = clock.getUTCNow();
        final PaymentTransactionModelDao chargebackTransaction = new PaymentTransactionModelDao(utcNow, utcNow, chargebackTransactionExternalKey, transactionModelDao.getPaymentId(),

                                                                                                            TransactionType.CHARGEBACK, utcNow, TransactionStatus.SUCCESS, amount, currency, null, null);
        final State currentPaymentState = directPaymentAutomatonRunner.fetchNextState("CHARGEBACK_INIT", true);

        // TODO STEPH we could create a DAO operation to do both steps at once
        paymentDao.updateDirectPaymentWithNewTransaction(transactionModelDao.getPaymentId(), chargebackTransaction, internalCallContext);
        paymentDao.updateDirectPaymentAndTransactionOnCompletion(transactionModelDao.getPaymentId(), currentPaymentState.getName(), chargebackTransaction.getId(), TransactionStatus.SUCCESS,
                                                                 chargebackTransaction.getAmount(), chargebackTransaction.getCurrency(),
                                                                 chargebackTransaction.getGatewayErrorCode(), chargebackTransaction.getGatewayErrorMsg(), internalCallContext);

    }

    public DirectPayment getPayment(final UUID directPaymentId, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentModelDao paymentModelDao = paymentDao.getDirectPayment(directPaymentId, internalTenantContext);
        if (paymentModelDao == null) {
            return null;
        }
        return getDirectPayment(paymentModelDao, withPluginInfo, properties, tenantContext, internalTenantContext);
    }

    public DirectPayment getPaymentByExternalKey(final String paymentExternalKey, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentModelDao paymentModelDao = paymentDao.getDirectPaymentByExternalKey(paymentExternalKey, internalTenantContext);
        if (paymentModelDao == null) {
            return null;
        }
        return getDirectPayment(paymentModelDao, withPluginInfo, properties, tenantContext, internalTenantContext);

    }

    public Pagination<DirectPayment> getPayments(final Long offset, final Long limit, final Iterable<PluginProperty> properties,
                                                 final TenantContext tenantContext, final InternalTenantContext internalTenantContext) {
        return getEntityPaginationFromPlugins(getAvailablePlugins(),
                                              offset,
                                              limit,
                                              new EntityPaginationBuilder<DirectPayment, PaymentApiException>() {
                                                  @Override
                                                  public Pagination<DirectPayment> build(final Long offset, final Long limit, final String pluginName) throws PaymentApiException {
                                                      return getPayments(offset, limit, pluginName, properties, tenantContext, internalTenantContext);
                                                  }
                                              }
                                             );
    }

    public Pagination<DirectPayment> getPayments(final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentPluginApi pluginApi = getPaymentPluginApi(pluginName);

        return getEntityPagination(limit,
                                   new SourcePaginationBuilder<PaymentModelDao, PaymentApiException>() {
                                       @Override
                                       public Pagination<PaymentModelDao> build() {
                                           // Find all payments for all accounts
                                           return paymentDao.getDirectPayments(pluginName, offset, limit, internalTenantContext);
                                       }
                                   },
                                   new Function<PaymentModelDao, DirectPayment>() {
                                       @Override
                                       public DirectPayment apply(final PaymentModelDao paymentModelDao) {
                                           List<PaymentTransactionInfoPlugin> pluginInfo = null;
                                           try {
                                               pluginInfo = pluginApi.getPaymentInfo(paymentModelDao.getAccountId(), paymentModelDao.getId(), properties, tenantContext);
                                           } catch (final PaymentPluginApiException e) {
                                               log.warn("Unable to find payment id " + paymentModelDao.getId() + " in plugin " + pluginName);
                                               // We still want to return a payment object, even though the plugin details are missing
                                           }

                                           return toDirectPayment(paymentModelDao.getId(), pluginInfo, internalTenantContext);
                                       }
                                   }
                                  );
    }

    public Pagination<DirectPayment> searchPayments(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) {
        return getEntityPaginationFromPlugins(getAvailablePlugins(),
                                              offset,
                                              limit,
                                              new EntityPaginationBuilder<DirectPayment, PaymentApiException>() {
                                                  @Override
                                                  public Pagination<DirectPayment> build(final Long offset, final Long limit, final String pluginName) throws PaymentApiException {
                                                      return searchPayments(searchKey, offset, limit, pluginName, properties, tenantContext, internalTenantContext);
                                                  }
                                              }
                                             );
    }

    public Pagination<DirectPayment> searchPayments(final String searchKey, final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
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
                                   new Function<PaymentTransactionInfoPlugin, DirectPayment>() {

                                       final List<PaymentTransactionInfoPlugin> cachedPaymentTransactions = new LinkedList<PaymentTransactionInfoPlugin>();

                                       @Override
                                       public DirectPayment apply(final PaymentTransactionInfoPlugin pluginTransaction) {

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
                                               final DirectPayment result = toDirectPayment(pluginTransaction.getKbPaymentId(), ImmutableList.<PaymentTransactionInfoPlugin>copyOf(cachedPaymentTransactions), internalTenantContext);
                                               cachedPaymentTransactions.clear();
                                               cachedPaymentTransactions.add(pluginTransaction);
                                               return result;
                                           }
                                       }
                                   }
                                  );
    }

    public DirectPayment toDirectPayment(final UUID directPaymentId, @Nullable final List<PaymentTransactionInfoPlugin> pluginTransactions, final InternalTenantContext tenantContext) {
        final PaymentModelDao paymentModelDao = paymentDao.getDirectPayment(directPaymentId, tenantContext);
        if (paymentModelDao == null) {
            log.warn("Unable to find direct payment id " + directPaymentId);
            return null;
        }

        final InternalTenantContext tenantContextWithAccountRecordId = internalCallContextFactory.createInternalTenantContext(paymentModelDao.getAccountId(), tenantContext);
        final List<PaymentTransactionModelDao> transactionsForAccount = paymentDao.getDirectTransactionsForAccount(paymentModelDao.getAccountId(), tenantContextWithAccountRecordId);

        return toDirectPayment(paymentModelDao, transactionsForAccount, pluginTransactions);
    }

    private DirectPayment getDirectPayment(final PaymentModelDao paymentModelDao, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext context, final InternalTenantContext tenantContext) throws PaymentApiException {
        final InternalTenantContext tenantContextWithAccountRecordId;
        if (tenantContext.getAccountRecordId() == null) {
            tenantContextWithAccountRecordId = internalCallContextFactory.createInternalTenantContext(paymentModelDao.getAccountId(), tenantContext);
        } else {
            tenantContextWithAccountRecordId = tenantContext;
        }
        final List<PaymentTransactionModelDao> transactionsForDirectPayment = paymentDao.getDirectTransactionsForDirectPayment(paymentModelDao.getId(), tenantContextWithAccountRecordId);

        final PaymentPluginApi plugin = withPluginInfo ? getPaymentProviderPlugin(paymentModelDao.getPaymentMethodId(), tenantContext) : null;
        List<PaymentTransactionInfoPlugin> pluginInfo = null;
        if (plugin != null) {
            try {
                pluginInfo = plugin.getPaymentInfo(paymentModelDao.getAccountId(), paymentModelDao.getId(), properties, context);
            } catch (final PaymentPluginApiException e) {
                throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_GET_PAYMENT_INFO, paymentModelDao.getId(), e.toString());
            }
        }
        return toDirectPayment(paymentModelDao, transactionsForDirectPayment, pluginInfo);
    }

    private DirectPayment toDirectPayment(final PaymentModelDao curPaymentModelDao, final Iterable<PaymentTransactionModelDao> transactionsModelDao, @Nullable final List<PaymentTransactionInfoPlugin> pluginTransactions) {
        final Ordering<DirectPaymentTransaction> perPaymentTransactionOrdering = Ordering.<DirectPaymentTransaction>from(new Comparator<DirectPaymentTransaction>() {
            @Override
            public int compare(final DirectPaymentTransaction o1, final DirectPaymentTransaction o2) {
                return o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
            }
        });

        final Iterable<PaymentTransactionModelDao> filteredTransactions = Iterables.filter(transactionsModelDao, new Predicate<PaymentTransactionModelDao>() {
            @Override
            public boolean apply(final PaymentTransactionModelDao curDirectPaymentTransactionModelDao) {
                return curDirectPaymentTransactionModelDao.getPaymentId().equals(curPaymentModelDao.getId());
            }
        });

        final Iterable<DirectPaymentTransaction> transactions = Iterables.transform(filteredTransactions, new Function<PaymentTransactionModelDao, DirectPaymentTransaction>() {
            @Override
            public DirectPaymentTransaction apply(final PaymentTransactionModelDao input) {

                final PaymentTransactionInfoPlugin info = pluginTransactions != null ?
                                                          Iterables.tryFind(pluginTransactions, new Predicate<PaymentTransactionInfoPlugin>() {
                                                              @Override
                                                              public boolean apply(final PaymentTransactionInfoPlugin input) {
                                                                  return input.getKbTransactionPaymentId().equals(input.getKbTransactionPaymentId());
                                                              }
                                                          }).orNull() : null;

                return new DefaultDirectPaymentTransaction(input.getId(), input.getTransactionExternalKey(), input.getCreatedDate(), input.getUpdatedDate(), input.getPaymentId(),
                                                           input.getTransactionType(), input.getEffectiveDate(), input.getTransactionStatus(), input.getAmount(), input.getCurrency(),
                                                           input.getProcessedAmount(), input.getProcessedCurrency(),
                                                           input.getGatewayErrorCode(), input.getGatewayErrorMsg(), info);
            }
        });

        final List<DirectPaymentTransaction> sortedTransactions = perPaymentTransactionOrdering.immutableSortedCopy(transactions);
        return new DefaultDirectPayment(curPaymentModelDao.getId(), curPaymentModelDao.getCreatedDate(), curPaymentModelDao.getUpdatedDate(), curPaymentModelDao.getAccountId(),
                                        curPaymentModelDao.getPaymentMethodId(), curPaymentModelDao.getPaymentNumber(), curPaymentModelDao.getExternalKey(), sortedTransactions);
    }

    private void postPaymentEvent(final Account account, final DirectPayment directPayment, final String transactionExternalKey, final InternalCallContext context) {
        final BusInternalEvent event = buildPaymentEvent(account, directPayment, transactionExternalKey, context);
        postPaymentEvent(event, account.getId(), context);
    }

    private BusInternalEvent buildPaymentEvent(final Account account, final DirectPayment directPayment, final String transactionExternalKey, final InternalCallContext context) {


        final DirectPaymentTransaction directPaymentTransaction = directPayment.getTransactions().get(directPayment.getTransactions().size() - 1);

        /* STEPH
        Iterables.<DirectPaymentTransaction>tryFind(directPayment.getTransactions(),
                                                                                                              new Predicate<DirectPaymentTransaction>() {
                                                                                                                  @Override
                                                                                                                  public boolean apply(final DirectPaymentTransaction input) {
                                                                                                                      return input.getExternalKey().equals(transactionExternalKey);
                                                                                                                  }
                                                                                                              }
                                                                                                             ).get()
                                                                                                             */

        switch (directPaymentTransaction.getTransactionStatus()) {
            case SUCCESS:
            case PENDING:
                return new DefaultPaymentInfoEvent(account.getId(),
                                                   null,
                                                   directPayment.getId(),
                                                   directPaymentTransaction.getAmount(),
                                                   directPayment.getPaymentNumber(),
                                                   directPaymentTransaction.getTransactionStatus(),
                                                   directPaymentTransaction.getEffectiveDate(),
                                                   context.getAccountRecordId(),
                                                   context.getTenantRecordId(),
                                                   context.getUserToken());
            case PAYMENT_FAILURE:
                return new DefaultPaymentErrorEvent(account.getId(),
                                                    null,
                                                    directPayment.getId(),
                                                    directPaymentTransaction.getPaymentInfoPlugin() == null ? null : directPaymentTransaction.getPaymentInfoPlugin().getGatewayError(),
                                                    context.getAccountRecordId(),
                                                    context.getTenantRecordId(),
                                                    context.getUserToken());
            case PLUGIN_FAILURE:
            default:
                return new DefaultPaymentPluginErrorEvent(account.getId(),
                                                          null,
                                                          directPayment.getId(),
                                                          directPaymentTransaction.getPaymentInfoPlugin() == null ? null : directPaymentTransaction.getPaymentInfoPlugin().getGatewayError(),
                                                          context.getAccountRecordId(),
                                                          context.getTenantRecordId(),
                                                          context.getUserToken());
        }
    }

}
