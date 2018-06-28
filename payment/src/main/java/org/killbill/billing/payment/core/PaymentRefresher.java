/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.payment.api.DefaultPayment;
import org.killbill.billing.payment.api.DefaultPaymentAttempt;
import org.killbill.billing.payment.api.DefaultPaymentTransaction;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentAttempt;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.core.janitor.IncompletePaymentTransactionTask;
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
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPagination;
import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationFromPlugins;

public class PaymentRefresher extends ProcessorBase {

    private static final Logger log = LoggerFactory.getLogger(PaymentRefresher.class);

    private static final String SCHEDULED = "SCHEDULED";
    private static final ImmutableList<PluginProperty> PLUGIN_PROPERTIES = ImmutableList.<PluginProperty>of();

    private final NotificationQueueService notificationQueueService;
    private final IncompletePaymentTransactionTask incompletePaymentTransactionTask;

    @Inject
    public PaymentRefresher(final PaymentPluginServiceRegistration paymentPluginServiceRegistration,
                            final AccountInternalApi accountUserApi,
                            final PaymentDao paymentDao,
                            final TagInternalApi tagUserApi,
                            final GlobalLocker locker,
                            final InternalCallContextFactory internalCallContextFactory,
                            final InvoiceInternalApi invoiceApi,
                            final Clock clock,
                            final NotificationQueueService notificationQueueService,
                            final IncompletePaymentTransactionTask incompletePaymentTransactionTask) {
        super(paymentPluginServiceRegistration, accountUserApi, paymentDao, tagUserApi, locker, internalCallContextFactory, invoiceApi, clock);
        this.notificationQueueService = notificationQueueService;
        this.incompletePaymentTransactionTask = incompletePaymentTransactionTask;
    }

    protected void onJanitorChange(final PaymentTransactionModelDao curPaymentTransactionModelDao,
                                   final InternalTenantContext internalTenantContext) {}

    public PaymentModelDao invokeJanitor(final PaymentModelDao curPaymentModelDao,
                                         final Collection<PaymentTransactionModelDao> curTransactionsModelDao,
                                         @Nullable final Iterable<PaymentTransactionInfoPlugin> pluginTransactions,
                                         final InternalTenantContext internalTenantContext) {
        PaymentModelDao newPaymentModelDao = curPaymentModelDao;
        final Collection<PaymentTransactionModelDao> transactionsModelDao = new LinkedList<PaymentTransactionModelDao>();
        for (final PaymentTransactionModelDao curPaymentTransactionModelDao : curTransactionsModelDao) {
            PaymentTransactionModelDao newPaymentTransactionModelDao = curPaymentTransactionModelDao;

            final PaymentTransactionInfoPlugin paymentTransactionInfoPlugin = findPaymentTransactionInfoPlugin(newPaymentTransactionModelDao, pluginTransactions);
            if (paymentTransactionInfoPlugin != null) {
                // Make sure to invoke the Janitor task in case the plugin fixes its state on the fly
                // See https://github.com/killbill/killbill/issues/341
                final boolean hasChanged = incompletePaymentTransactionTask.updatePaymentAndTransactionIfNeededWithAccountLock(newPaymentModelDao, newPaymentTransactionModelDao, paymentTransactionInfoPlugin, internalTenantContext);
                if (hasChanged) {
                    newPaymentModelDao = paymentDao.getPayment(newPaymentModelDao.getId(), internalTenantContext);
                    newPaymentTransactionModelDao = paymentDao.getPaymentTransaction(newPaymentTransactionModelDao.getId(), internalTenantContext);

                    onJanitorChange(curPaymentTransactionModelDao, internalTenantContext);
                }
            } else {
                log.warn("Unable to find transaction={} from pluginTransactions={}", curPaymentTransactionModelDao, pluginTransactions);
            }

            transactionsModelDao.add(newPaymentTransactionModelDao);
        }

        curTransactionsModelDao.clear();
        curTransactionsModelDao.addAll(transactionsModelDao);

        return newPaymentModelDao;
    }

    private PaymentTransactionInfoPlugin findPaymentTransactionInfoPlugin(final PaymentTransactionModelDao paymentTransactionModelDao,
                                                                          @Nullable final Iterable<PaymentTransactionInfoPlugin> pluginTransactions) {
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

    public Payment getPaymentByTransactionId(final UUID transactionId, final boolean withPluginInfo, final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentTransactionModelDao paymentTransactionDao = paymentDao.getPaymentTransaction(transactionId, internalTenantContext);
        if (null != paymentTransactionDao) {
            final PaymentModelDao paymentModelDao = paymentDao.getPayment(paymentTransactionDao.getPaymentId(), internalTenantContext);
            return toPayment(paymentModelDao, withPluginInfo, withAttempts, properties, tenantContext, internalTenantContext);
        }
        return null;
    }

    public Payment getPaymentByTransactionExternalKey(final String transactionExternalKey, final boolean withPluginInfo, final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final List<PaymentTransactionModelDao> paymentTransactionDao = paymentDao.getPaymentTransactionsByExternalKey(transactionExternalKey, internalTenantContext);
        if (paymentTransactionDao.isEmpty()) {
            return null;
        }
        // All transactions must be on the same payment (see sanity in buildPaymentStateContext)
        final PaymentModelDao paymentModelDao = paymentDao.getPayment(paymentTransactionDao.get(0).getPaymentId(), internalTenantContext);
        return toPayment(paymentModelDao, withPluginInfo, withAttempts, properties, tenantContext, internalTenantContext);
    }

    protected Payment getPayment(final PaymentModelDao paymentModelDao, final boolean withPluginInfo, final boolean withAttempts, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
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

    // Used in bulk get APIs (getPayments / searchPayments)
    private Payment toPayment(final UUID paymentId,
                              @Nullable final Iterable<PaymentTransactionInfoPlugin> pluginTransactions,
                              final boolean withAttempts,
                              final InternalTenantContext tenantContext) {
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

    // Used in bulk get API (getAccountPayments)
    private Payment toPayment(final PaymentModelDao curPaymentModelDao, final Collection<PaymentTransactionModelDao> allTransactionsModelDao, @Nullable final Iterable<PaymentTransactionInfoPlugin> pluginTransactions, final boolean withAttempts, final InternalTenantContext internalTenantContext) {
        // Need to filter for optimized codepaths looking up by account_record_id
        final Collection<PaymentTransactionModelDao> transactionsModelDao = new LinkedList<PaymentTransactionModelDao>(Collections2.filter(allTransactionsModelDao, new Predicate<PaymentTransactionModelDao>() {
            @Override
            public boolean apply(final PaymentTransactionModelDao curPaymentTransactionModelDao) {
                return curPaymentTransactionModelDao.getPaymentId().equals(curPaymentModelDao.getId());
            }
        }));

        if (pluginTransactions != null) {
            invokeJanitor(curPaymentModelDao, transactionsModelDao, pluginTransactions, internalTenantContext);
        }

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

        final List<PaymentAttempt> paymentAttempts = new ArrayList<PaymentAttempt>();

        // Add Past Payment Attempts
        for (final PaymentAttemptModelDao pastPaymentAttempt : pastPaymentAttempts) {
            final PaymentAttempt paymentAttempt = new DefaultPaymentAttempt(pastPaymentAttempt.getAccountId(),
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
                final PaymentAttemptModelDao lastPaymentAttempt = getLastPaymentAttempt(pastPaymentAttempts,
                                                                                        ((PaymentRetryNotificationKey) notificationEvent.getEvent()).getAttemptId());

                if (lastPaymentAttempt != null) {
                    final PaymentAttempt futurePaymentAttempt = new DefaultPaymentAttempt(lastPaymentAttempt.getAccountId(), // accountId
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
        } catch (final NoSuchNotificationQueue noSuchNotificationQueue) {
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
            } catch (final PluginPropertySerializerException e) {
                log.error("ERROR Deserializing Plugin Properties - " + e.getMessage());
            }
        }
        return null;
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

    List<PaymentTransactionInfoPlugin> getPaymentTransactionInfoPlugins(final PaymentPluginApi plugin, final PaymentModelDao paymentModelDao, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentApiException {
        try {
            return plugin.getPaymentInfo(paymentModelDao.getAccountId(), paymentModelDao.getId(), properties, context);
        } catch (final PaymentPluginApiException e) {
            throw new PaymentApiException(e, ErrorCode.PAYMENT_PLUGIN_GET_PAYMENT_INFO, paymentModelDao.getId(), e.toString());
        }
    }
}
