/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.payment.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.automaton.OperationResult;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.payment.api.DefaultPayment;
import org.killbill.billing.payment.api.DefaultPaymentAttempt;
import org.killbill.billing.payment.api.DefaultPaymentTransaction;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentAttempt;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.core.janitor.IncompletePaymentTransactionTask;
import org.killbill.billing.payment.core.sm.PaymentAutomatonDAOHelper;
import org.killbill.billing.payment.core.sm.PaymentAutomatonRunner;
import org.killbill.billing.payment.core.sm.PaymentStateContext;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;
import org.killbill.billing.payment.dao.PluginPropertySerializer;
import org.killbill.billing.payment.dao.PluginPropertySerializer.PluginPropertySerializerException;
import org.killbill.billing.payment.glue.DefaultPaymentService;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.payment.retry.DefaultRetryService;
import org.killbill.billing.payment.retry.PaymentRetryNotificationKey;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.DefaultPagination;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.EntityPaginationBuilder;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.SourcePaginationBuilder;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPagination;
import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationFromPlugins;

public class PaymentProcessor extends ProcessorBase {

    private static final ImmutableList<PluginProperty> PLUGIN_PROPERTIES = ImmutableList.<PluginProperty>of();

    private final PaymentAutomatonRunner paymentAutomatonRunner;
    private final IncompletePaymentTransactionTask incompletePaymentTransactionTask;
    private final NotificationQueueService notificationQueueService;

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    public static final String SCHEDULED = "SCHEDULED";


    @Inject
    public PaymentProcessor(final PaymentPluginServiceRegistration paymentPluginServiceRegistration,
                            final AccountInternalApi accountUserApi,
                            final InvoiceInternalApi invoiceApi,
                            final TagInternalApi tagUserApi,
                            final PaymentDao paymentDao,
                            final InternalCallContextFactory internalCallContextFactory,
                            final GlobalLocker locker,
                            final PaymentAutomatonRunner paymentAutomatonRunner,
                            final IncompletePaymentTransactionTask incompletePaymentTransactionTask,
                            final NotificationQueueService notificationQueueService,
                            final Clock clock) {
        super(paymentPluginServiceRegistration, accountUserApi, paymentDao, tagUserApi, locker, internalCallContextFactory, invoiceApi, clock);
        this.paymentAutomatonRunner = paymentAutomatonRunner;
        this.incompletePaymentTransactionTask = incompletePaymentTransactionTask;
        this.notificationQueueService = notificationQueueService;
    }

    public Payment createAuthorization(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, @Nullable final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency,
                                       @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey, @Nullable final UUID paymentIdForNewPayment, @Nullable final UUID paymentTransactionIdForNewPaymentTransaction, final boolean shouldLockAccountAndDispatch,
                                       final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.AUTHORIZE, account, paymentMethodId, paymentId, null, amount, currency, paymentExternalKey, paymentTransactionExternalKey, paymentIdForNewPayment, paymentTransactionIdForNewPaymentTransaction, shouldLockAccountAndDispatch, null, properties, callContext, internalCallContext);
    }

    public Payment createCapture(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency,
                                 @Nullable final String paymentTransactionExternalKey, @Nullable final UUID paymentTransactionIdForNewPaymentTransaction, final boolean shouldLockAccountAndDispatch,
                                 final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.CAPTURE, account, null, paymentId, null, amount, currency, null, paymentTransactionExternalKey, null, paymentTransactionIdForNewPaymentTransaction, shouldLockAccountAndDispatch, null, properties, callContext, internalCallContext);
    }

    public Payment createPurchase(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, @Nullable final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency,
                                  @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey, @Nullable final UUID paymentIdForNewPayment, @Nullable final UUID paymentTransactionIdForNewPaymentTransaction, final boolean shouldLockAccountAndDispatch,
                                  final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.PURCHASE, account, paymentMethodId, paymentId, null, amount, currency, paymentExternalKey, paymentTransactionExternalKey, paymentIdForNewPayment, paymentTransactionIdForNewPaymentTransaction, shouldLockAccountAndDispatch, null, properties, callContext, internalCallContext);
    }

    public Payment createVoid(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, final UUID paymentId, @Nullable final String paymentTransactionExternalKey, @Nullable final UUID paymentTransactionIdForNewPaymentTransaction, final boolean shouldLockAccountAndDispatch,
                              final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.VOID, account, null, paymentId, null, null, null, null, paymentTransactionExternalKey, null, paymentTransactionIdForNewPaymentTransaction, shouldLockAccountAndDispatch, null, properties, callContext, internalCallContext);
    }

    public Payment createRefund(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, final UUID paymentId, final BigDecimal amount, final Currency currency,
                                final String paymentTransactionExternalKey, @Nullable final UUID paymentTransactionIdForNewPaymentTransaction, final boolean shouldLockAccountAndDispatch,
                                final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.REFUND, account, null, paymentId, null, amount, currency, null, paymentTransactionExternalKey, null, paymentTransactionIdForNewPaymentTransaction, shouldLockAccountAndDispatch, null, properties, callContext, internalCallContext);
    }

    public Payment createCredit(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, @Nullable final UUID paymentMethodId, @Nullable final UUID paymentId, final BigDecimal amount, final Currency currency,
                                @Nullable final String paymentExternalKey, @Nullable final String paymentTransactionExternalKey, @Nullable final UUID paymentIdForNewPayment, @Nullable final UUID paymentTransactionIdForNewPaymentTransaction, final boolean shouldLockAccountAndDispatch,
                                final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.CREDIT, account, paymentMethodId, paymentId, null, amount, currency, paymentExternalKey, paymentTransactionExternalKey, paymentIdForNewPayment, paymentTransactionIdForNewPaymentTransaction, shouldLockAccountAndDispatch, null, properties, callContext, internalCallContext);
    }

    public Payment createChargeback(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, final UUID paymentId, @Nullable final String paymentTransactionExternalKey, final BigDecimal amount, final Currency currency, @Nullable final UUID paymentTransactionIdForNewPaymentTransaction, final boolean shouldLockAccountAndDispatch,
                                    final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.CHARGEBACK, account, null, paymentId, null, amount, currency, null, paymentTransactionExternalKey, null, paymentTransactionIdForNewPaymentTransaction, shouldLockAccountAndDispatch, null, PLUGIN_PROPERTIES, callContext, internalCallContext);
    }

    public Payment createChargebackReversal(final boolean isApiPayment, @Nullable final UUID attemptId, final Account account, final UUID paymentId, @Nullable final String paymentTransactionExternalKey, final BigDecimal amount, final Currency currency, @Nullable final UUID paymentTransactionIdForNewPaymentTransaction, final boolean shouldLockAccountAndDispatch,
                                            final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        return performOperation(isApiPayment, attemptId, TransactionType.CHARGEBACK, account, null, paymentId, null, amount, currency, null, paymentTransactionExternalKey, null, paymentTransactionIdForNewPaymentTransaction, shouldLockAccountAndDispatch, OperationResult.FAILURE, PLUGIN_PROPERTIES, callContext, internalCallContext);
    }

    public Payment notifyPendingPaymentOfStateChanged(final Account account, final UUID transactionId, final boolean isSuccess, final CallContext callContext, final InternalCallContext internalCallContext) throws PaymentApiException {
        final PaymentTransactionModelDao transactionModelDao = paymentDao.getPaymentTransaction(transactionId, internalCallContext);
        if (transactionModelDao.getTransactionStatus() != TransactionStatus.PENDING) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_SUCCESS_PAYMENT, transactionModelDao.getPaymentId());
        }

        final OperationResult overridePluginResult = isSuccess ? OperationResult.SUCCESS : OperationResult.FAILURE;

        final boolean runJanitor = false;
        return performOperation(true, runJanitor, null, transactionModelDao.getTransactionType(), account, null, transactionModelDao.getPaymentId(),
                                transactionModelDao.getId(), transactionModelDao.getAmount(), transactionModelDao.getCurrency(), null, transactionModelDao.getTransactionExternalKey(), null, null, true,
                                overridePluginResult, PLUGIN_PROPERTIES, callContext, internalCallContext);
    }

    public List<Payment> getAccountPayments(final UUID accountId, final boolean withPluginInfo, final boolean withAttempts, final TenantContext context, final InternalTenantContext tenantContext) throws PaymentApiException {
        final List<PaymentModelDao> paymentsModelDao = paymentDao.getPaymentsForAccount(accountId, tenantContext);
        final List<PaymentTransactionModelDao> transactionsModelDao = paymentDao.getTransactionsForAccount(accountId, tenantContext);

        final Map<UUID, PaymentPluginApi> paymentPluginByPaymentMethodId = new HashMap<UUID, PaymentPluginApi>();
        final Collection<UUID> absentPlugins = new HashSet<UUID>();
        final List<Payment> transformedPayments = Lists.<PaymentModelDao, Payment>transform(paymentsModelDao,
                                                                                            new Function<PaymentModelDao, Payment>() {
                                                                                                @Override
                                                                                                public Payment apply(final PaymentModelDao paymentModelDao) {
                                                                                                    List<PaymentTransactionInfoPlugin> pluginInfo = null;

                                                                                                    if (withPluginInfo) {
                                                                                                        PaymentPluginApi pluginApi = paymentPluginByPaymentMethodId.get(paymentModelDao.getPaymentMethodId());
                                                                                                        if (pluginApi == null && !absentPlugins.contains(paymentModelDao.getPaymentMethodId())) {
                                                                                                            try {
                                                                                                                pluginApi = getPaymentProviderPlugin(paymentModelDao.getPaymentMethodId(), true, tenantContext);
                                                                                                                paymentPluginByPaymentMethodId.put(paymentModelDao.getPaymentMethodId(), pluginApi);
                                                                                                            } catch (final PaymentApiException e) {
                                                                                                                log.warn("Unable to retrieve pluginApi for payment method " + paymentModelDao.getPaymentMethodId());
                                                                                                                absentPlugins.add(paymentModelDao.getPaymentMethodId());
                                                                                                            }
                                                                                                        }

                                                                                                        pluginInfo = getPaymentTransactionInfoPluginsIfNeeded(pluginApi, paymentModelDao, context);
                                                                                                    }

                                                                                                    return toPayment(paymentModelDao, transactionsModelDao, pluginInfo, withAttempts, tenantContext);
                                                                                                }
                                                                                            });

        // Copy the transformed list, so the transformation function is applied once (otherwise, the Janitor could be invoked multiple times)
        return ImmutableList.<Payment>copyOf(transformedPayments);
    }

    public Payment getPayment(final UUID paymentId, final boolean withPluginInfo, final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentModelDao paymentModelDao = paymentDao.getPayment(paymentId, internalTenantContext);
        return getPayment(paymentModelDao, withPluginInfo, withAttempts, properties, tenantContext, internalTenantContext);
    }

    public Payment getPaymentByExternalKey(final String paymentExternalKey, final boolean withPluginInfo, final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentModelDao paymentModelDao = paymentDao.getPaymentByExternalKey(paymentExternalKey, internalTenantContext);
        return getPayment(paymentModelDao, withPluginInfo, withAttempts, properties, tenantContext, internalTenantContext);
    }

    private Payment getPayment(final PaymentModelDao paymentModelDao, final boolean withPluginInfo, final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        if (paymentModelDao == null) {
            return null;
        }
        return toPayment(paymentModelDao, withPluginInfo, withAttempts, properties, tenantContext, internalTenantContext);
    }

    public Pagination<Payment> getPayments(final Long offset, final Long limit, final boolean withPluginInfo, final boolean withAttempts,
                                           final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) {
        final Map<UUID, Optional<PaymentPluginApi>> paymentMethodIdToPaymentPluginApi = new HashMap<UUID, Optional<PaymentPluginApi>>();

        try {
            return getEntityPagination(limit,
                                       new SourcePaginationBuilder<PaymentModelDao, PaymentApiException>() {
                                           @Override
                                           public Pagination<PaymentModelDao> build() {
                                               // Find all payments for all accounts
                                               return paymentDao.get(offset, limit, internalTenantContext);
                                           }
                                       },
                                       new Function<PaymentModelDao, Payment>() {
                                           @Override
                                           public Payment apply(final PaymentModelDao paymentModelDao) {
                                               final PaymentPluginApi pluginApi;
                                               if (!withPluginInfo) {
                                                   pluginApi = null;
                                               } else {
                                                   if (paymentMethodIdToPaymentPluginApi.get(paymentModelDao.getPaymentMethodId()) == null) {
                                                       try {
                                                           final PaymentPluginApi paymentProviderPlugin = getPaymentProviderPlugin(paymentModelDao.getPaymentMethodId(), true, internalTenantContext);
                                                           paymentMethodIdToPaymentPluginApi.put(paymentModelDao.getPaymentMethodId(), Optional.<PaymentPluginApi>of(paymentProviderPlugin));
                                                       } catch (final PaymentApiException e) {
                                                           log.warn("Unable to retrieve PaymentPluginApi for paymentMethodId='{}'", paymentModelDao.getPaymentMethodId(), e);
                                                           // We use Optional to avoid printing the log line for each result
                                                           paymentMethodIdToPaymentPluginApi.put(paymentModelDao.getPaymentMethodId(), Optional.<PaymentPluginApi>absent());
                                                       }
                                                   }
                                                   pluginApi = paymentMethodIdToPaymentPluginApi.get(paymentModelDao.getPaymentMethodId()).orNull();
                                               }
                                               final List<PaymentTransactionInfoPlugin> pluginInfo = getPaymentTransactionInfoPluginsIfNeeded(pluginApi, paymentModelDao, tenantContext);
                                               return toPayment(paymentModelDao.getId(), pluginInfo, withAttempts, internalTenantContext);
                                           }
                                       }
                                      );
        } catch (final PaymentApiException e) {
            log.warn("Unable to get payments", e);
            return new DefaultPagination<Payment>(offset, limit, null, null, ImmutableSet.<Payment>of().iterator());
        }
    }

    public Pagination<Payment> getPayments(final Long offset, final Long limit, final String pluginName, final boolean withPluginInfo, final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentPluginApi pluginApi = withPluginInfo ? getPaymentPluginApi(pluginName) : null;

        return getEntityPagination(limit,
                                   new SourcePaginationBuilder<PaymentModelDao, PaymentApiException>() {
                                       @Override
                                       public Pagination<PaymentModelDao> build() {
                                           // Find all payments for all accounts
                                           return paymentDao.getPayments(pluginName, offset, limit, internalTenantContext);
                                       }
                                   },
                                   new Function<PaymentModelDao, Payment>() {
                                       @Override
                                       public Payment apply(final PaymentModelDao paymentModelDao) {
                                           final List<PaymentTransactionInfoPlugin> pluginInfo = getPaymentTransactionInfoPluginsIfNeeded(pluginApi, paymentModelDao, tenantContext);
                                           return toPayment(paymentModelDao.getId(), pluginInfo, withAttempts, internalTenantContext);
                                       }
                                   }
                                  );
    }

    public Pagination<Payment> searchPayments(final String searchKey, final Long offset, final Long limit, final boolean withPluginInfo, final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) {
        if (withPluginInfo) {
            return getEntityPaginationFromPlugins(false,
                                                  getAvailablePlugins(),
                                                  offset,
                                                  limit,
                                                  new EntityPaginationBuilder<Payment, PaymentApiException>() {
                                                      @Override
                                                      public Pagination<Payment> build(final Long offset, final Long limit, final String pluginName) throws PaymentApiException {
                                                          return searchPayments(searchKey, offset, limit, pluginName, withPluginInfo, withAttempts, properties, tenantContext, internalTenantContext);
                                                      }
                                                  }
                                                 );
        } else {
            try {
                return getEntityPagination(limit,
                                           new SourcePaginationBuilder<PaymentModelDao, PaymentApiException>() {
                                               @Override
                                               public Pagination<PaymentModelDao> build() {
                                                   return paymentDao.searchPayments(searchKey, offset, limit, internalTenantContext);
                                               }
                                           },
                                           new Function<PaymentModelDao, Payment>() {
                                               @Override
                                               public Payment apply(final PaymentModelDao paymentModelDao) {
                                                   return toPayment(paymentModelDao.getId(), null, withAttempts, internalTenantContext);
                                               }
                                           }
                                          );
            } catch (final PaymentApiException e) {
                log.warn("Unable to search through payments", e);
                return new DefaultPagination<Payment>(offset, limit, null, null, ImmutableSet.<Payment>of().iterator());
            }
        }
    }

    public Pagination<Payment> searchPayments(final String searchKey, final Long offset, final Long limit, final String pluginName, final boolean withPluginInfo,
                                              final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
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
                                               final Payment result = toPayment(pluginTransaction.getKbPaymentId(), withPluginInfo ? ImmutableList.<PaymentTransactionInfoPlugin>copyOf(cachedPaymentTransactions) : ImmutableList.<PaymentTransactionInfoPlugin>of(), withAttempts, internalTenantContext);
                                               cachedPaymentTransactions.clear();
                                               cachedPaymentTransactions.add(pluginTransaction);
                                               return result;
                                           }
                                       }
                                   }
                                  );
    }

    public void cancelScheduledPaymentTransaction(@Nullable final UUID paymentTransactionId, @Nullable final String paymentTransactionExternalKey, final CallContext callContext) throws PaymentApiException {

        final InternalCallContext internalCallContextWithoutAccountRecordId = internalCallContextFactory.createInternalCallContextWithoutAccountRecordId(callContext);
        final String effectivePaymentTransactionExternalKey;
        if (paymentTransactionExternalKey == null) {
            final PaymentTransactionModelDao transaction = paymentDao.getPaymentTransaction(paymentTransactionId, internalCallContextWithoutAccountRecordId);
            effectivePaymentTransactionExternalKey = transaction.getTransactionExternalKey();
        } else {
            effectivePaymentTransactionExternalKey = paymentTransactionExternalKey;
        }

        final List<PaymentAttemptModelDao> attempts = paymentDao.getPaymentAttemptByTransactionExternalKey(effectivePaymentTransactionExternalKey, internalCallContextWithoutAccountRecordId);
        if (attempts.isEmpty()) {
            return;
        }

        final PaymentAttemptModelDao lastPaymentAttempt = attempts.get(attempts.size() - 1);
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(lastPaymentAttempt.getAccountId(), callContext);

        cancelScheduledPaymentTransaction(lastPaymentAttempt.getId(), internalCallContext);
    }

    public void cancelScheduledPaymentTransaction(final UUID lastPaymentAttemptId, final InternalCallContext internalCallContext) throws PaymentApiException {
        try {
            final NotificationQueue retryQueue = notificationQueueService.getNotificationQueue(DefaultPaymentService.SERVICE_NAME, DefaultRetryService.QUEUE_NAME);
            final Iterable<NotificationEventWithMetadata<NotificationEvent>> notificationEventWithMetadatas =
                    retryQueue.getFutureNotificationForSearchKeys(internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId());

            for (final NotificationEventWithMetadata<NotificationEvent> notificationEvent : notificationEventWithMetadatas) {
                if (((PaymentRetryNotificationKey) notificationEvent.getEvent()).getAttemptId().equals(lastPaymentAttemptId)) {
                    retryQueue.removeNotification(notificationEvent.getRecordId());
                }
                // Go through all results to close the connection
            }
        } catch (final NoSuchNotificationQueue noSuchNotificationQueue) {
            log.error("ERROR Loading Notification Queue - " + noSuchNotificationQueue.getMessage());
            throw new IllegalStateException(noSuchNotificationQueue);
        }
    }

    public Payment getPaymentByTransactionId(final UUID transactionId, final boolean withPluginInfo, final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentTransactionModelDao paymentTransactionDao = paymentDao.getPaymentTransaction(transactionId, internalTenantContext);
        if (null != paymentTransactionDao) {
            PaymentModelDao paymentModelDao = paymentDao.getPayment(paymentTransactionDao.getPaymentId(), internalTenantContext);
            return toPayment(paymentModelDao, withPluginInfo, withAttempts, properties, tenantContext, internalTenantContext);
        }
        return null;
    }

    private Payment performOperation(final boolean isApiPayment,
                                     @Nullable final UUID attemptId,
                                     final TransactionType transactionType,
                                     final Account account,
                                     @Nullable final UUID paymentMethodId,
                                     @Nullable final UUID paymentId,
                                     @Nullable final UUID transactionId,
                                     @Nullable final BigDecimal amount,
                                     @Nullable final Currency currency,
                                     @Nullable final String paymentExternalKey,
                                     @Nullable final String paymentTransactionExternalKey,
                                     @Nullable final UUID paymentIdForNewPayment,
                                     @Nullable final UUID paymentTransactionIdForNewPaymentTransaction,
                                     final boolean shouldLockAccountAndDispatch,
                                     @Nullable final OperationResult overridePluginOperationResult,
                                     final Iterable<PluginProperty> properties,
                                     final CallContext callContext,
                                     final InternalCallContext internalCallContext) throws PaymentApiException {
        boolean runJanitor = true;
        return performOperation(isApiPayment, runJanitor, attemptId, transactionType, account, paymentMethodId, paymentId,
                                transactionId, amount, currency, paymentExternalKey, paymentTransactionExternalKey,
                                paymentIdForNewPayment, paymentTransactionIdForNewPaymentTransaction, shouldLockAccountAndDispatch, overridePluginOperationResult, properties, callContext, internalCallContext);
    }

    private Payment performOperation(final boolean isApiPayment,
                                     final boolean runJanitor,
                                     @Nullable final UUID attemptId,
                                     final TransactionType transactionType,
                                     final Account account,
                                     @Nullable final UUID paymentMethodId,
                                     @Nullable final UUID paymentId,
                                     @Nullable final UUID transactionId,
                                     @Nullable final BigDecimal amount,
                                     @Nullable final Currency currency,
                                     @Nullable final String paymentExternalKey,
                                     @Nullable final String paymentTransactionExternalKey,
                                     @Nullable final UUID paymentIdForNewPayment,
                                     @Nullable final UUID paymentTransactionIdForNewPaymentTransaction,
                                     final boolean shouldLockAccountAndDispatch,
                                     @Nullable final OperationResult overridePluginOperationResult,
                                     final Iterable<PluginProperty> properties,
                                     final CallContext callContext,
                                     final InternalCallContext internalCallContext) throws PaymentApiException {
        final PaymentStateContext paymentStateContext = paymentAutomatonRunner.buildPaymentStateContext(isApiPayment,
                                                                                                        transactionType,
                                                                                                        account,
                                                                                                        attemptId,
                                                                                                        paymentMethodId != null ? paymentMethodId : account.getPaymentMethodId(),
                                                                                                        paymentId,
                                                                                                        transactionId,
                                                                                                        paymentExternalKey,
                                                                                                        paymentTransactionExternalKey,
                                                                                                        amount,
                                                                                                        currency,
                                                                                                        paymentIdForNewPayment,
                                                                                                        paymentTransactionIdForNewPaymentTransaction,
                                                                                                        shouldLockAccountAndDispatch,
                                                                                                        overridePluginOperationResult,
                                                                                                        properties,
                                                                                                        callContext,
                                                                                                        internalCallContext);
        final PaymentAutomatonDAOHelper daoHelper = paymentAutomatonRunner.buildDaoHelper(paymentStateContext, internalCallContext);

        String currentStateName = null;
        if (paymentStateContext.getPaymentId() != null) {
            PaymentModelDao paymentModelDao = daoHelper.getPayment();

            // Sanity: verify the payment belongs to the right account (in case it was looked-up by payment or transaction external key)
            if (!paymentModelDao.getAccountRecordId().equals(internalCallContext.getAccountRecordId())) {
                throw new PaymentApiException(ErrorCode.PAYMENT_DIFFERENT_ACCOUNT_ID, paymentStateContext.getPaymentId());
            }

            // Note: the list needs to be modifiable for invokeJanitor
            final Collection<PaymentTransactionModelDao> paymentTransactionsForCurrentPayment = new LinkedList<PaymentTransactionModelDao>(daoHelper.getPaymentDao().getTransactionsForPayment(paymentStateContext.getPaymentId(), paymentStateContext.getInternalCallContext()));
            // Always invoke the Janitor first to get the latest state. The state machine will then
            // prevent disallowed transitions in case the state couldn't be fixed (or if it's already in a final state).
            if (runJanitor) {
                final PaymentPluginApi plugin = getPaymentProviderPlugin(paymentModelDao.getPaymentMethodId(), true, internalCallContext);
                final List<PaymentTransactionInfoPlugin> pluginTransactions = getPaymentTransactionInfoPlugins(plugin, paymentModelDao, properties, callContext);
                paymentModelDao = invokeJanitor(paymentModelDao, paymentTransactionsForCurrentPayment, pluginTransactions, internalCallContext);
            }

            if (paymentStateContext.getPaymentTransactionExternalKey() != null) {
                final List<PaymentTransactionModelDao> allPaymentTransactionsForKey = daoHelper.getPaymentDao().getPaymentTransactionsByExternalKey(paymentStateContext.getPaymentTransactionExternalKey(), internalCallContext);
                runSanityOnTransactionExternalKey(allPaymentTransactionsForKey, paymentStateContext, internalCallContext);
            }

            if (paymentStateContext.getTransactionId() != null || paymentStateContext.getPaymentTransactionExternalKey() != null) {
                // If a transaction id or key is passed, we are maybe completing an existing transaction (unless a new key was provided)
                PaymentTransactionModelDao transactionToComplete = findTransactionToCompleteAndRunSanityChecks(paymentModelDao, paymentTransactionsForCurrentPayment, paymentStateContext, internalCallContext);

                if (transactionToComplete != null) {
                    final UUID transactionToCompleteId = transactionToComplete.getId();
                    transactionToComplete = Iterables.<PaymentTransactionModelDao>find(paymentTransactionsForCurrentPayment,
                                                                                       new Predicate<PaymentTransactionModelDao>() {
                                                                                           @Override
                                                                                           public boolean apply(final PaymentTransactionModelDao input) {
                                                                                               return transactionToCompleteId.equals(input.getId());
                                                                                           }
                                                                                       });

                    // We can't tell where we should be in the state machine - bail (cannot be enforced by the state machine unfortunately because UNKNOWN and PLUGIN_FAILURE are both treated as EXCEPTION)
                    if (transactionToComplete.getTransactionStatus() == TransactionStatus.UNKNOWN) {
                        throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_OPERATION, paymentStateContext.getTransactionType(), transactionToComplete.getTransactionStatus());
                    }

                    paymentStateContext.setPaymentTransactionModelDao(transactionToComplete);
                }
            }

            // Use the original payment method id of the payment being completed
            paymentStateContext.setPaymentMethodId(paymentModelDao.getPaymentMethodId());
            // We always take the last successful state name to permit retries on failures
            currentStateName = paymentModelDao.getLastSuccessStateName();
        }

        paymentAutomatonRunner.run(paymentStateContext, daoHelper, currentStateName, transactionType);

        return getPayment(paymentStateContext.getPaymentModelDao(), true, false, properties, callContext, internalCallContext);
    }

    private void runSanityOnTransactionExternalKey(final Iterable<PaymentTransactionModelDao> allPaymentTransactionsForKey,
                                                   final PaymentStateContext paymentStateContext,
                                                   final InternalCallContext internalCallContext) throws PaymentApiException {
        for (final PaymentTransactionModelDao paymentTransactionModelDao : allPaymentTransactionsForKey) {
            // Sanity: verify we don't already have a successful transaction for that key (chargeback reversals are a bit special, it's the only transaction type we can revert)
            if (paymentTransactionModelDao.getTransactionExternalKey().equals(paymentStateContext.getPaymentTransactionExternalKey()) &&
                paymentTransactionModelDao.getTransactionStatus() == TransactionStatus.SUCCESS &&
                paymentTransactionModelDao.getTransactionType() != TransactionType.CHARGEBACK) {
                throw new PaymentApiException(ErrorCode.PAYMENT_ACTIVE_TRANSACTION_KEY_EXISTS, paymentStateContext.getPaymentTransactionExternalKey());
            }

            // Sanity: don't share keys across accounts
            if (!paymentTransactionModelDao.getAccountRecordId().equals(internalCallContext.getAccountRecordId())) {
                UUID accountId;
                try {
                    accountId = accountInternalApi.getAccountByRecordId(paymentTransactionModelDao.getAccountRecordId(), internalCallContext).getId();
                } catch (final AccountApiException e) {
                    log.warn("Unable to retrieve account", e);
                    accountId = null;
                }
                throw new PaymentApiException(ErrorCode.PAYMENT_TRANSACTION_DIFFERENT_ACCOUNT_ID, accountId);
            }
        }
    }

    private PaymentTransactionModelDao findTransactionToCompleteAndRunSanityChecks(final PaymentModelDao paymentModelDao,
                                                                                   final Iterable<PaymentTransactionModelDao> paymentTransactionsForCurrentPayment,
                                                                                   final PaymentStateContext paymentStateContext,
                                                                                   final InternalCallContext internalCallContext) throws PaymentApiException {
        final Collection<PaymentTransactionModelDao> completionCandidates = new LinkedList<PaymentTransactionModelDao>();
        for (final PaymentTransactionModelDao paymentTransactionModelDao : paymentTransactionsForCurrentPayment) {
            // Check if we already have a transaction for that id or key
            if (!(paymentStateContext.getTransactionId() != null && paymentTransactionModelDao.getId().equals(paymentStateContext.getTransactionId())) &&
                !(paymentStateContext.getPaymentTransactionExternalKey() != null && paymentTransactionModelDao.getTransactionExternalKey().equals(paymentStateContext.getPaymentTransactionExternalKey()))) {
                // Sanity: if not, prevent multiple PENDING transactions for initial calls (cannot be enforced by the state machine unfortunately)
                if ((paymentTransactionModelDao.getTransactionType() == TransactionType.AUTHORIZE ||
                     paymentTransactionModelDao.getTransactionType() == TransactionType.PURCHASE ||
                     paymentTransactionModelDao.getTransactionType() == TransactionType.CREDIT) &&
                    paymentTransactionModelDao.getTransactionStatus() == TransactionStatus.PENDING) {
                    throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_OPERATION, paymentTransactionModelDao.getTransactionType(), paymentModelDao.getStateName());
                } else {
                    continue;
                }
            }

            // Sanity: if we already have a transaction for that id or key, the transaction type must match
            if (paymentTransactionModelDao.getTransactionType() != paymentStateContext.getTransactionType()) {
                throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_PARAMETER, "transactionType", String.format("%s doesn't match existing transaction type %s", paymentStateContext.getTransactionType(), paymentTransactionModelDao.getTransactionType()));
            }

            // UNKNOWN transactions are potential candidates, we'll invoke the Janitor first though
            if (paymentTransactionModelDao.getTransactionStatus() == TransactionStatus.PENDING || paymentTransactionModelDao.getTransactionStatus() == TransactionStatus.UNKNOWN) {
                completionCandidates.add(paymentTransactionModelDao);
            }
        }

        Preconditions.checkState(Iterables.<PaymentTransactionModelDao>size(completionCandidates) <= 1, "There should be at most one completion candidate");
        return Iterables.<PaymentTransactionModelDao>getLast(completionCandidates, null);
    }

    // Used in bulk get API (getAccountPayments / getPayments)
    private List<PaymentTransactionInfoPlugin> getPaymentTransactionInfoPluginsIfNeeded(@Nullable final PaymentPluginApi pluginApi, final PaymentModelDao paymentModelDao, final TenantContext context) {
        if (pluginApi == null) {
            return null;
        }

        try {
            return getPaymentTransactionInfoPlugins(pluginApi, paymentModelDao, PLUGIN_PROPERTIES, context);
        } catch (final PaymentApiException e) {
            log.warn("Unable to retrieve plugin info for payment " + paymentModelDao.getId());
            return null;
        }
    }

    private List<PaymentTransactionInfoPlugin> getPaymentTransactionInfoPlugins(final PaymentPluginApi plugin, final PaymentModelDao paymentModelDao, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        try {
            return plugin.getPaymentInfo(paymentModelDao.getAccountId(), paymentModelDao.getId(), properties, context);
        } catch (final PaymentPluginApiException e) {
            throw new PaymentApiException(e, ErrorCode.PAYMENT_PLUGIN_GET_PAYMENT_INFO, paymentModelDao.getId(), e.toString());
        }
    }

    // Used in bulk get APIs (getPayments / searchPayments)
    private Payment toPayment(final UUID paymentId, @Nullable final Iterable<PaymentTransactionInfoPlugin> pluginTransactions, final boolean withAttempts, final InternalTenantContext tenantContext) {
        final PaymentModelDao paymentModelDao = paymentDao.getPayment(paymentId, tenantContext);
        if (paymentModelDao == null) {
            log.warn("Unable to find payment id " + paymentId);
            return null;
        }

        return toPayment(paymentModelDao, pluginTransactions, withAttempts, tenantContext);
    }

    // Used in single get APIs (getPayment / getPaymentByExternalKey)
    private Payment toPayment(final PaymentModelDao paymentModelDao, final boolean withPluginInfo, final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext context, final InternalTenantContext tenantContext) throws PaymentApiException {
        final PaymentPluginApi plugin = getPaymentProviderPlugin(paymentModelDao.getPaymentMethodId(), true, tenantContext);
        final List<PaymentTransactionInfoPlugin> pluginTransactions = withPluginInfo ? getPaymentTransactionInfoPlugins(plugin, paymentModelDao, properties, context) : null;

        return toPayment(paymentModelDao, pluginTransactions, withAttempts, tenantContext);
    }

    private Payment toPayment(final PaymentModelDao paymentModelDao, @Nullable final Iterable<PaymentTransactionInfoPlugin> pluginTransactions,
                              final boolean withAttempts, final InternalTenantContext tenantContext) {
        final InternalTenantContext tenantContextWithAccountRecordId = getInternalTenantContextWithAccountRecordId(paymentModelDao.getAccountId(), tenantContext);
        final List<PaymentTransactionModelDao> transactionsForPayment = paymentDao.getTransactionsForPayment(paymentModelDao.getId(), tenantContextWithAccountRecordId);

        return toPayment(paymentModelDao, transactionsForPayment, pluginTransactions, withAttempts, tenantContextWithAccountRecordId);
    }

    private PaymentModelDao invokeJanitor(final PaymentModelDao curPaymentModelDao, final Collection<PaymentTransactionModelDao> curTransactionsModelDao, @Nullable final Iterable<PaymentTransactionInfoPlugin> pluginTransactions, final InternalTenantContext internalTenantContext) {
        // Need to filter for optimized codepaths looking up by account_record_id
        final Iterable<PaymentTransactionModelDao> filteredTransactions = Iterables.filter(curTransactionsModelDao, new Predicate<PaymentTransactionModelDao>() {
            @Override
            public boolean apply(final PaymentTransactionModelDao curPaymentTransactionModelDao) {
                return curPaymentTransactionModelDao.getPaymentId().equals(curPaymentModelDao.getId());
            }
        });

        PaymentModelDao newPaymentModelDao = curPaymentModelDao;
        final Collection<PaymentTransactionModelDao> transactionsModelDao = new LinkedList<PaymentTransactionModelDao>();
        for (final PaymentTransactionModelDao curPaymentTransactionModelDao : filteredTransactions) {
            PaymentTransactionModelDao newPaymentTransactionModelDao = curPaymentTransactionModelDao;

            final PaymentTransactionInfoPlugin paymentTransactionInfoPlugin = findPaymentTransactionInfoPlugin(newPaymentTransactionModelDao, pluginTransactions);
            if (paymentTransactionInfoPlugin != null) {
                // Make sure to invoke the Janitor task in case the plugin fixes its state on the fly
                // See https://github.com/killbill/killbill/issues/341
                final boolean hasChanged = incompletePaymentTransactionTask.updatePaymentAndTransactionIfNeededWithAccountLock(newPaymentModelDao, newPaymentTransactionModelDao, paymentTransactionInfoPlugin, internalTenantContext);
                if (hasChanged) {
                    newPaymentModelDao = paymentDao.getPayment(newPaymentModelDao.getId(), internalTenantContext);
                    newPaymentTransactionModelDao = paymentDao.getPaymentTransaction(newPaymentTransactionModelDao.getId(), internalTenantContext);
                }
            }

            transactionsModelDao.add(newPaymentTransactionModelDao);
        }

        curTransactionsModelDao.clear();
        curTransactionsModelDao.addAll(transactionsModelDao);

        return newPaymentModelDao;
    }

    // Used in bulk get API (getAccountPayments)
    private Payment toPayment(final PaymentModelDao curPaymentModelDao, final Collection<PaymentTransactionModelDao> curTransactionsModelDao, @Nullable final Iterable<PaymentTransactionInfoPlugin> pluginTransactions, final boolean withAttempts, final InternalTenantContext internalTenantContext) {
        final Collection<PaymentTransactionModelDao> transactionsModelDao = new LinkedList<PaymentTransactionModelDao>(curTransactionsModelDao);
        invokeJanitor(curPaymentModelDao, transactionsModelDao, pluginTransactions, internalTenantContext);

        final Collection<PaymentTransaction> transactions = new LinkedList<PaymentTransaction>();
        for (final PaymentTransactionModelDao newPaymentTransactionModelDao : transactionsModelDao) {
            final PaymentTransactionInfoPlugin paymentTransactionInfoPlugin = findPaymentTransactionInfoPlugin(newPaymentTransactionModelDao, pluginTransactions);
            final PaymentTransaction transaction = new DefaultPaymentTransaction(newPaymentTransactionModelDao.getId(),
                                                                                 newPaymentTransactionModelDao.getAttemptId(),
                                                                                 newPaymentTransactionModelDao.getTransactionExternalKey(),
                                                                                 newPaymentTransactionModelDao.getCreatedDate(),
                                                                                 newPaymentTransactionModelDao.getUpdatedDate(),
                                                                                 newPaymentTransactionModelDao.getPaymentId(),
                                                                                 newPaymentTransactionModelDao.getTransactionType(),
                                                                                 newPaymentTransactionModelDao.getEffectiveDate(),
                                                                                 newPaymentTransactionModelDao.getTransactionStatus(),
                                                                                 newPaymentTransactionModelDao.getAmount(),
                                                                                 newPaymentTransactionModelDao.getCurrency(),
                                                                                 newPaymentTransactionModelDao.getProcessedAmount(),
                                                                                 newPaymentTransactionModelDao.getProcessedCurrency(),
                                                                                 newPaymentTransactionModelDao.getGatewayErrorCode(),
                                                                                 newPaymentTransactionModelDao.getGatewayErrorMsg(),
                                                                                 paymentTransactionInfoPlugin);
            transactions.add(transaction);
        }

        final Ordering<PaymentTransaction> perPaymentTransactionOrdering = Ordering.<PaymentTransaction>from(new Comparator<PaymentTransaction>() {
            @Override
            public int compare(final PaymentTransaction o1, final PaymentTransaction o2) {
                return o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
            }
        });
        final List<PaymentTransaction> sortedTransactions = perPaymentTransactionOrdering.immutableSortedCopy(transactions);
        return new DefaultPayment(curPaymentModelDao.getId(),
                                  curPaymentModelDao.getCreatedDate(),
                                  curPaymentModelDao.getUpdatedDate(),
                                  curPaymentModelDao.getAccountId(),
                                  curPaymentModelDao.getPaymentMethodId(),
                                  curPaymentModelDao.getPaymentNumber(),
                                  curPaymentModelDao.getExternalKey(),
                                  sortedTransactions,
                                  (withAttempts && !sortedTransactions.isEmpty()) ?
                                  getPaymentAttempts(paymentDao.getPaymentAttempts(curPaymentModelDao.getExternalKey(), internalTenantContext),
                                                     internalTenantContext) : null
                                  );
    }

    private List<PaymentAttempt> getPaymentAttempts(final List<PaymentAttemptModelDao> pastPaymentAttempts,
                                                    final InternalTenantContext internalTenantContext) {

        List<PaymentAttempt> paymentAttempts = new ArrayList<PaymentAttempt>();

        // Add Past Payment Attempts
        for (PaymentAttemptModelDao pastPaymentAttempt : pastPaymentAttempts) {
            DefaultPaymentAttempt paymentAttempt = new DefaultPaymentAttempt(
                    pastPaymentAttempt.getAccountId(),
                    pastPaymentAttempt.getPaymentMethodId(),
                    pastPaymentAttempt.getId(),
                    pastPaymentAttempt.getCreatedDate(),
                    pastPaymentAttempt.getUpdatedDate(),
                    pastPaymentAttempt.getCreatedDate(),
                    pastPaymentAttempt.getPaymentExternalKey(),
                    pastPaymentAttempt.getTransactionId(),
                    pastPaymentAttempt.getTransactionExternalKey(),
                    pastPaymentAttempt.getTransactionType(),
                    pastPaymentAttempt.getStateName(),
                    pastPaymentAttempt.getAmount(),
                    pastPaymentAttempt.getCurrency(),
                    pastPaymentAttempt.getPluginName(),
                    buildPluginProperties(pastPaymentAttempt));
            paymentAttempts.add(paymentAttempt);
        }

        // Get Future Payment Attempts from Notification Queue and add them to the list
        try {
            final NotificationQueue retryQueue = notificationQueueService.getNotificationQueue(DefaultPaymentService.SERVICE_NAME, DefaultRetryService.QUEUE_NAME);
            final Iterable<NotificationEventWithMetadata<NotificationEvent>> notificationEventWithMetadatas =
                    retryQueue.getFutureNotificationForSearchKeys(internalTenantContext.getAccountRecordId(), internalTenantContext.getTenantRecordId());

            for (final NotificationEventWithMetadata<NotificationEvent> notificationEvent : notificationEventWithMetadatas) {
                // Last Attempt
                PaymentAttemptModelDao lastPaymentAttempt = getLastPaymentAttempt(pastPaymentAttempts,
                                                                                  ((PaymentRetryNotificationKey) notificationEvent.getEvent()).getAttemptId());

                if (lastPaymentAttempt != null) {
                    DefaultPaymentAttempt futurePaymentAttempt = new DefaultPaymentAttempt(
                            lastPaymentAttempt.getAccountId(), // accountId
                            lastPaymentAttempt.getPaymentMethodId(), // paymentMethodId
                            ((PaymentRetryNotificationKey) notificationEvent.getEvent()).getAttemptId(), // id
                            null, // createdDate
                            null, // updatedDate
                            notificationEvent.getEffectiveDate(), // effectiveDate
                            lastPaymentAttempt.getPaymentExternalKey(), // paymentExternalKey
                            null, // transactionId
                            lastPaymentAttempt.getTransactionExternalKey(), // transactionExternalKey
                            lastPaymentAttempt.getTransactionType(), // transactionType
                            SCHEDULED, // stateName
                            lastPaymentAttempt.getAmount(), // amount
                            lastPaymentAttempt.getCurrency(), // currency
                            ((PaymentRetryNotificationKey) notificationEvent.getEvent()).getPaymentControlPluginNames().get(0), // pluginName,
                            buildPluginProperties(lastPaymentAttempt)); // pluginProperties
                    paymentAttempts.add(futurePaymentAttempt);
                }
            }
        } catch (NoSuchNotificationQueue noSuchNotificationQueue) {
            log.error("ERROR Loading Notification Queue - " + noSuchNotificationQueue.getMessage());
        }
        return paymentAttempts;
    }

    private PaymentAttemptModelDao getLastPaymentAttempt(final List<PaymentAttemptModelDao> pastPaymentAttempts, final UUID attemptId) {
        if (!pastPaymentAttempts.isEmpty()) {
            for (int i = pastPaymentAttempts.size() - 1; i >= 0; i--) {
                if (pastPaymentAttempts.get(i).getId().equals(attemptId)) {
                    return pastPaymentAttempts.get(i);
                }
            }
        }
        return null;
    }

    private List<PluginProperty> buildPluginProperties(final PaymentAttemptModelDao pastPaymentAttempt) {
        if (pastPaymentAttempt.getPluginProperties() != null) {
            try {
                return Lists.newArrayList(PluginPropertySerializer.deserialize(pastPaymentAttempt.getPluginProperties()));
            } catch (PluginPropertySerializerException e) {
                log.error("ERROR Deserializing Plugin Properties - " + e.getMessage());
            }
        }
        return null;
    }

    private PaymentTransactionInfoPlugin findPaymentTransactionInfoPlugin(final PaymentTransactionModelDao paymentTransactionModelDao, @Nullable final Iterable<PaymentTransactionInfoPlugin> pluginTransactions) {
        if (pluginTransactions == null) {
            return null;
        }

        return Iterables.tryFind(pluginTransactions,
                                 new Predicate<PaymentTransactionInfoPlugin>() {
                                     @Override
                                     public boolean apply(final PaymentTransactionInfoPlugin paymentTransactionInfoPlugin) {
                                         return paymentTransactionModelDao.getId().equals(paymentTransactionInfoPlugin.getKbTransactionPaymentId());
                                     }
                                 }).orNull();
    }

    private InternalTenantContext getInternalTenantContextWithAccountRecordId(final UUID accountId, final InternalTenantContext tenantContext) {
        final InternalTenantContext tenantContextWithAccountRecordId;
        if (tenantContext.getAccountRecordId() == null) {
            tenantContextWithAccountRecordId = internalCallContextFactory.createInternalTenantContext(accountId, tenantContext);
        } else {
            tenantContextWithAccountRecordId = tenantContext;
        }
        return tenantContextWithAccountRecordId;
    }
}
