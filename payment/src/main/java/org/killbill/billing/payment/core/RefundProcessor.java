/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.bus.api.PersistentBus;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.DefaultRefund;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.Refund;
import org.killbill.billing.payment.api.RefundStatus;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.RefundModelDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.RefundInfoPlugin;
import org.killbill.billing.payment.plugin.api.RefundPluginStatus;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.EntityPaginationBuilder;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.SourcePaginationBuilder;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.inject.name.Named;

import static org.killbill.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;
import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPagination;
import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationFromPlugins;

public class RefundProcessor extends ProcessorBase {

    private static final Logger log = LoggerFactory.getLogger(RefundProcessor.class);

    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public RefundProcessor(final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                           final AccountInternalApi accountApi,
                           final InvoiceInternalApi invoiceApi,
                           final PersistentBus eventBus,
                           final InternalCallContextFactory internalCallContextFactory,
                           final TagInternalApi tagUserApi,
                           final PaymentDao paymentDao,
                           final NonEntityDao nonEntityDao,
                           final GlobalLocker locker,
                           @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor) {
        super(pluginRegistry, accountApi, eventBus, paymentDao, nonEntityDao, tagUserApi, locker, executor, invoiceApi);
        this.internalCallContextFactory = internalCallContextFactory;
    }

    /**
     * Create a refund and adjust the invoice or invoice items as necessary.
     *
     * @param account                   account to refund
     * @param paymentId                 payment associated with that refund
     * @param specifiedRefundAmount     amount to refund. If null, the amount will be the sum of adjusted invoice items
     * @param isAdjusted                whether the refund should trigger an invoice or invoice item adjustment
     * @param invoiceItemIdsWithAmounts invoice item ids and associated amounts to adjust
     * @param context                   the call callcontext
     * @return the created callcontext
     * @throws PaymentApiException
     */
    public Refund createRefund(final Account account, final UUID paymentId, @Nullable final BigDecimal specifiedRefundAmount,
                               final boolean isAdjusted, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, final InternalCallContext context)
            throws PaymentApiException {
        final UUID tenantId = nonEntityDao.retrieveIdFromObject(context.getTenantRecordId(), ObjectType.TENANT);

        return new WithAccountLock<Refund>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<Refund>() {

            @Override
            public Refund doOperation() throws PaymentApiException {
                // First, compute the refund amount, if necessary
                final BigDecimal refundAmount = computeRefundAmount(paymentId, specifiedRefundAmount, invoiceItemIdsWithAmounts, context);

                try {
                    final PaymentModelDao payment = paymentDao.getPayment(paymentId, context);
                    if (payment == null) {
                        throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_SUCCESS_PAYMENT, paymentId);
                    }

                    final RefundModelDao refundInfo = new RefundModelDao(account.getId(), paymentId, refundAmount, account.getCurrency(), refundAmount, account.getCurrency(), isAdjusted);
                    paymentDao.insertRefund(refundInfo, context);

                    final PaymentPluginApi plugin = getPaymentProviderPlugin(payment.getPaymentMethodId(), context);
                    final RefundInfoPlugin refundInfoPlugin = plugin.processRefund(account.getId(), paymentId, refundAmount, account.getCurrency(), context.toCallContext(tenantId));

                    switch (refundInfoPlugin.getStatus()) {
                        case PROCESSED:
                            paymentDao.updateRefundStatus(refundInfo.getId(), RefundStatus.PLUGIN_COMPLETED, refundInfoPlugin.getAmount(), refundInfoPlugin.getCurrency(), context);

                            invoiceApi.createRefund(paymentId, refundAmount, isAdjusted, invoiceItemIdsWithAmounts, refundInfo.getId(), context);

                            paymentDao.updateRefundStatus(refundInfo.getId(), RefundStatus.COMPLETED, refundInfoPlugin.getAmount(), refundInfoPlugin.getCurrency(), context);

                            return new DefaultRefund(refundInfo.getId(), refundInfo.getCreatedDate(), refundInfo.getUpdatedDate(),
                                                     paymentId, refundInfo.getAmount(), account.getCurrency(),
                                                     isAdjusted, refundInfo.getCreatedDate(), RefundStatus.COMPLETED);

                        case PENDING:
                            paymentDao.updateRefundStatus(refundInfo.getId(), RefundStatus.PENDING, refundInfoPlugin.getAmount(), refundInfoPlugin.getCurrency(), context);
                            return new DefaultRefund(refundInfo.getId(), refundInfo.getCreatedDate(), refundInfo.getUpdatedDate(),
                                                     paymentId, refundInfo.getAmount(), account.getCurrency(),
                                                     isAdjusted, refundInfo.getCreatedDate(), RefundStatus.PENDING);

                        default:
                            paymentDao.updateRefundStatus(refundInfo.getId(), RefundStatus.PLUGIN_ERRORED, refundAmount, account.getCurrency(), context);
                            throw new PaymentPluginApiException("Refund error for RefundInfo: " + refundInfo.toString(),
                                                                String.format("Gateway error: %s, Gateway error code: %s, Reference ids: %s / %s",
                                                                              refundInfoPlugin.getGatewayError(),
                                                                              refundInfoPlugin.getGatewayErrorCode(),
                                                                              refundInfoPlugin.getFirstRefundReferenceId(),
                                                                              refundInfoPlugin.getSecondRefundReferenceId()));
                    }
                } catch (PaymentPluginApiException e) {
                    throw new PaymentApiException(ErrorCode.PAYMENT_CREATE_REFUND, account.getId(), e.getErrorMessage());
                } catch (InvoiceApiException e) {
                    throw new PaymentApiException(e);
                }
            }
        });
    }

    public void notifyPendingRefundOfStateChanged(final Account account, final UUID refundId, final boolean isSuccess, final InternalCallContext context)
            throws PaymentApiException {

        new WithAccountLock<Void>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<Void>() {

            @Override
            public Void doOperation() throws PaymentApiException {
                try {
                    final RefundModelDao refund = paymentDao.getRefund(refundId, context);
                    if (refund == null) {
                        throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_REFUND, refundId);
                    }
                    if (refund.getRefundStatus() != RefundStatus.PENDING) {
                        throw new PaymentApiException(ErrorCode.PAYMENT_NOT_PENDING, refundId);
                    }

                    // TODO STEPH : Model is broken if we had an invoice item adjustements as we lost track of them
                    invoiceApi.createRefund(refund.getPaymentId(), refund.getAmount(), refund.isAdjusted(), Collections.<UUID, BigDecimal>emptyMap(), refund.getId(), context);
                    paymentDao.updateRefundStatus(refund.getId(), RefundStatus.COMPLETED, refund.getAmount(), refund.getCurrency(), context);
                } catch (InvoiceApiException e) {
                }
                return null;
            }
        });

    }

    /**
     * Compute the refund amount (computed from the invoice or invoice items as necessary).
     *
     * @param paymentId                 payment id associated with this refund
     * @param specifiedRefundAmount     amount to refund. If null, the amount will be the sum of adjusted invoice items
     * @param invoiceItemIdsWithAmounts invoice item ids and associated amounts to adjust
     * @return the refund amount
     */
    private BigDecimal computeRefundAmount(final UUID paymentId, @Nullable final BigDecimal specifiedRefundAmount,
                                           final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, final InternalTenantContext context)
            throws PaymentApiException {
        try {
            final List<InvoiceItem> items = invoiceApi.getInvoiceForPaymentId(paymentId, context).getInvoiceItems();

            BigDecimal amountFromItems = BigDecimal.ZERO;
            for (final UUID itemId : invoiceItemIdsWithAmounts.keySet()) {
                amountFromItems = amountFromItems.add(Objects.firstNonNull(invoiceItemIdsWithAmounts.get(itemId),
                                                                           getAmountFromItem(items, itemId)));
            }

            // Sanity check: if some items were specified, then the sum should be equal to specified refund amount, if specified
            if (amountFromItems.compareTo(BigDecimal.ZERO) != 0 && specifiedRefundAmount != null && specifiedRefundAmount.compareTo(amountFromItems) != 0) {
                throw new IllegalArgumentException("You can't specify a refund amount that doesn't match the invoice items amounts");
            }

            return Objects.firstNonNull(specifiedRefundAmount, amountFromItems);
        } catch (InvoiceApiException e) {
            throw new PaymentApiException(e);
        }
    }

    private BigDecimal getAmountFromItem(final List<InvoiceItem> items, final UUID itemId) {
        for (final InvoiceItem item : items) {
            if (item.getId().equals(itemId)) {
                return item.getAmount();
            }
        }

        throw new IllegalArgumentException("Unable to find invoice item for id " + itemId);
    }

    public Refund getRefund(final UUID refundId, final boolean withPluginInfo, final InternalTenantContext context) throws PaymentApiException {
        RefundModelDao result = paymentDao.getRefund(refundId, context);
        if (result == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_REFUND, refundId);
        }

        final List<RefundModelDao> filteredInput = filterUncompletedPluginRefund(Collections.singletonList(result));
        if (filteredInput.isEmpty()) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_REFUND, refundId);
        }

        if (completePluginCompletedRefund(filteredInput, context)) {
            result = paymentDao.getRefund(refundId, context);
        }

        final PaymentModelDao payment = paymentDao.getPayment(result.getPaymentId(), context);
        if (payment == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, result.getPaymentId());
        }

        final PaymentPluginApi plugin = withPluginInfo ? getPaymentProviderPlugin(payment.getPaymentMethodId(), context) : null;
        List<RefundInfoPlugin> refundInfoPlugins = ImmutableList.<RefundInfoPlugin>of();
        if (plugin != null) {
            try {
                refundInfoPlugins = plugin.getRefundInfo(result.getAccountId(), result.getPaymentId(), buildTenantContext(context));
            } catch (final PaymentPluginApiException e) {
                throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_GET_REFUND_INFO, refundId, e.toString());
            }
        }

        return new DefaultRefund(result, findRefundInfoPlugin(result, refundInfoPlugins));
    }

    private RefundInfoPlugin findRefundInfoPlugin(final RefundModelDao refundModelDao, final List<RefundInfoPlugin> refundInfoPlugins) {
        // We have a mapping 1:N for payment:refunds and a mapping 1:1 for RefundModelDao:RefundInfoPlugin.
        // Unfortunately, we processing a refund, we don't tell the plugin about the refund id, so we need to do some heuristics
        // to map a RefundInfoPlugin back to its RefundModelDao
        // TODO This will break for multiple partial refunds of the same amount. Check the effective date won't help for same day partial refunds and checking effective datetime seems risky
        return Iterables.<RefundInfoPlugin>tryFind(refundInfoPlugins,
                                                   new Predicate<RefundInfoPlugin>() {
                                                       @Override
                                                       public boolean apply(final RefundInfoPlugin refundInfoPlugin) {
                                                           return refundObjectsMatch(refundModelDao, refundInfoPlugin);
                                                       }
                                                   }).orNull();
    }

    private boolean refundObjectsMatch(final RefundModelDao refundModelDao, final RefundInfoPlugin refundInfoPlugin) {
        return (refundInfoPlugin.getKbPaymentId() != null && refundModelDao.getPaymentId() != null && refundInfoPlugin.getKbPaymentId().equals(refundModelDao.getPaymentId())) &&
               (refundInfoPlugin.getAmount() != null && refundModelDao.getProcessedAmount() != null && refundInfoPlugin.getAmount().compareTo(refundModelDao.getProcessedAmount()) == 0) &&
               (refundInfoPlugin.getCurrency() != null && refundModelDao.getProcessedCurrency() != null && refundInfoPlugin.getCurrency().equals(refundModelDao.getProcessedCurrency())) &&
               (
                       (refundInfoPlugin.getStatus().equals(RefundPluginStatus.PROCESSED) && refundModelDao.getRefundStatus().equals(RefundStatus.COMPLETED)) ||
                       (refundInfoPlugin.getStatus().equals(RefundPluginStatus.PENDING) && refundModelDao.getRefundStatus().equals(RefundStatus.PENDING))
               );
    }

    public Pagination<Refund> getRefunds(final Long offset, final Long limit, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) {
        return getEntityPaginationFromPlugins(getAvailablePlugins(),
                                              offset,
                                              limit,
                                              new EntityPaginationBuilder<Refund, PaymentApiException>() {
                                                  @Override
                                                  public Pagination<Refund> build(final Long offset, final Long limit, final String pluginName) throws PaymentApiException {
                                                      return getRefunds(offset, limit, pluginName, tenantContext, internalTenantContext);
                                                  }
                                              });
    }

    public Pagination<Refund> getRefunds(final Long offset, final Long limit, final String pluginName, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentPluginApi pluginApi = getPaymentPluginApi(pluginName);

        return getEntityPagination(limit,
                                   new SourcePaginationBuilder<RefundModelDao, PaymentApiException>() {
                                       @Override
                                       public Pagination<RefundModelDao> build() {
                                           // Find all refunds for all accounts
                                           return paymentDao.getRefunds(pluginName, offset, limit, internalTenantContext);
                                       }
                                   },
                                   new Function<RefundModelDao, Refund>() {
                                       @Override
                                       public Refund apply(final RefundModelDao refundModelDao) {
                                           List<RefundInfoPlugin> refundInfoPlugins = null;
                                           try {
                                               refundInfoPlugins = pluginApi.getRefundInfo(refundModelDao.getAccountId(), refundModelDao.getId(), tenantContext);
                                           } catch (final PaymentPluginApiException e) {
                                               log.warn("Unable to find refund id " + refundModelDao.getId() + " in plugin " + pluginName);
                                               // We still want to return a refund object, even though the plugin details are missing
                                           }

                                           final RefundInfoPlugin refundInfoPlugin = refundInfoPlugins == null ? null : findRefundInfoPlugin(refundModelDao, refundInfoPlugins);
                                           return new DefaultRefund(refundModelDao, refundInfoPlugin);
                                       }
                                   }
                                  );
    }

    public Pagination<Refund> searchRefunds(final String searchKey, final Long offset, final Long limit, final InternalTenantContext internalTenantContext) {
        return getEntityPaginationFromPlugins(getAvailablePlugins(),
                                              offset,
                                              limit,
                                              new EntityPaginationBuilder<Refund, PaymentApiException>() {
                                                  @Override
                                                  public Pagination<Refund> build(final Long offset, final Long limit, final String pluginName) throws PaymentApiException {
                                                      return searchRefunds(searchKey, offset, limit, pluginName, internalTenantContext);
                                                  }
                                              });
    }

    public Pagination<Refund> searchRefunds(final String searchKey, final Long offset, final Long limit, final String pluginName, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentPluginApi pluginApi = getPaymentPluginApi(pluginName);

        final Map<UUID, List<RefundInfoPlugin>> refundsByPaymentId = new HashMap<UUID, List<RefundInfoPlugin>>();
        final Map<UUID, List<RefundModelDao>> refundModelDaosByPaymentId = new HashMap<UUID, List<RefundModelDao>>();

        return getEntityPagination(limit,
                                   new SourcePaginationBuilder<RefundInfoPlugin, PaymentApiException>() {
                                       @Override
                                       public Pagination<RefundInfoPlugin> build() throws PaymentApiException {
                                           final Pagination<RefundInfoPlugin> refunds;
                                           try {
                                               refunds = pluginApi.searchRefunds(searchKey, offset, limit, buildTenantContext(internalTenantContext));
                                           } catch (final PaymentPluginApiException e) {
                                               throw new PaymentApiException(e, ErrorCode.PAYMENT_PLUGIN_SEARCH_REFUNDS, pluginName, searchKey);
                                           }

                                           // We need to group the refunds from the plugin by payment id. Since the ordering of the results is unspecified,
                                           // we cannot do streaming here unfortunately
                                           for (final RefundInfoPlugin refundInfoPlugin : refunds) {
                                               if (refundInfoPlugin.getKbPaymentId() == null) {
                                                   // Garbage from the plugin?
                                                   log.debug("Plugin {} returned a refund without a kbPaymentId for searchKey {}", pluginName, searchKey);
                                                   continue;
                                               }

                                               if (refundsByPaymentId.get(refundInfoPlugin.getKbPaymentId()) == null) {
                                                   refundsByPaymentId.put(refundInfoPlugin.getKbPaymentId(), new LinkedList<RefundInfoPlugin>());
                                               }
                                               refundsByPaymentId.get(refundInfoPlugin.getKbPaymentId()).add(refundInfoPlugin);
                                           }

                                           return refunds;
                                       }
                                   },
                                   new Function<RefundInfoPlugin, Refund>() {
                                       @Override
                                       public Refund apply(final RefundInfoPlugin refundInfoPlugin) {
                                           if (refundInfoPlugin.getKbPaymentId() == null) {
                                               // Garbage from the plugin?
                                               log.debug("Plugin {} returned a refund without a kbPaymentId for searchKey {}", pluginName, searchKey);
                                               return null;
                                           }

                                           List<RefundModelDao> modelCandidates = refundModelDaosByPaymentId.get(refundInfoPlugin.getKbPaymentId());
                                           if (modelCandidates == null) {
                                               refundModelDaosByPaymentId.put(refundInfoPlugin.getKbPaymentId(), paymentDao.getRefundsForPayment(refundInfoPlugin.getKbPaymentId(), internalTenantContext));
                                               modelCandidates = refundModelDaosByPaymentId.get(refundInfoPlugin.getKbPaymentId());
                                           }

                                           final RefundModelDao model = Iterables.<RefundModelDao>tryFind(modelCandidates,
                                                                                                          new Predicate<RefundModelDao>() {
                                                                                                              @Override
                                                                                                              public boolean apply(final RefundModelDao refundModelDao) {
                                                                                                                  return refundObjectsMatch(refundModelDao, refundInfoPlugin);
                                                                                                              }
                                                                                                          }).orNull();

                                           if (model == null) {
                                               log.warn("Unable to find refund for payment id " + refundInfoPlugin.getKbPaymentId() + " present in plugin " + pluginName);
                                               return null;
                                           }

                                           return new DefaultRefund(model, refundInfoPlugin);
                                       }
                                   }
                                  );
    }

    public List<Refund> getAccountRefunds(final Account account, final InternalTenantContext context)
            throws PaymentApiException {
        List<RefundModelDao> result = paymentDao.getRefundsForAccount(account.getId(), context);
        if (completePluginCompletedRefund(result, context)) {
            result = paymentDao.getRefundsForAccount(account.getId(), context);
        }
        final List<RefundModelDao> filteredInput = filterUncompletedPluginRefund(result);
        return toRefunds(filteredInput);
    }

    public List<Refund> getPaymentRefunds(final UUID paymentId, final InternalTenantContext context)
            throws PaymentApiException {
        List<RefundModelDao> result = paymentDao.getRefundsForPayment(paymentId, context);
        if (completePluginCompletedRefund(result, context)) {
            result = paymentDao.getRefundsForPayment(paymentId, context);
        }
        final List<RefundModelDao> filteredInput = filterUncompletedPluginRefund(result);
        return toRefunds(filteredInput);
    }

    public List<Refund> toRefunds(final List<RefundModelDao> in) {
        return new ArrayList<Refund>(Collections2.transform(in, new Function<RefundModelDao, Refund>() {
            @Override
            public Refund apply(final RefundModelDao cur) {
                return new DefaultRefund(cur.getId(), cur.getCreatedDate(), cur.getUpdatedDate(),
                                         cur.getPaymentId(), cur.getAmount(), cur.getCurrency(),
                                         cur.isAdjusted(), cur.getCreatedDate(), cur.getRefundStatus());
            }
        }));
    }

    private List<RefundModelDao> filterUncompletedPluginRefund(final List<RefundModelDao> input) {
        return new ArrayList<RefundModelDao>(Collections2.filter(input, new Predicate<RefundModelDao>() {
            @Override
            public boolean apply(final RefundModelDao in) {
                return in.getRefundStatus() != RefundStatus.CREATED;
            }
        }));
    }

    private boolean completePluginCompletedRefund(final List<RefundModelDao> refunds, final InternalTenantContext tenantContext) throws PaymentApiException {

        final Collection<RefundModelDao> refundsToBeFixed = Collections2.filter(refunds, new Predicate<RefundModelDao>() {
            @Override
            public boolean apply(final RefundModelDao in) {
                return in.getRefundStatus() == RefundStatus.PLUGIN_COMPLETED;
            }
        });
        if (refundsToBeFixed.size() == 0) {
            return false;
        }

        try {

            // TODO callcontext should be created for each refund and have the correct userToken
            final InternalCallContext context = internalCallContextFactory.createInternalCallContext(refundsToBeFixed.iterator().next().getId(), ObjectType.REFUND, "RefundProcessor",
                                                                                                     CallOrigin.INTERNAL, UserType.SYSTEM, null);

            final Account account = accountInternalApi.getAccountById(refundsToBeFixed.iterator().next().getAccountId(), context);
            new WithAccountLock<Void>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<Void>() {

                @Override
                public Void doOperation() throws PaymentApiException {
                    try {
                        for (final RefundModelDao cur : refundsToBeFixed) {

                            // TODO - we currently don't save the items to be adjusted. If we crash, they won't be adjusted...
                            invoiceApi.createRefund(cur.getPaymentId(), cur.getAmount(), cur.isAdjusted(), ImmutableMap.<UUID, BigDecimal>of(), cur.getId(), context);
                            paymentDao.updateRefundStatus(cur.getId(), RefundStatus.COMPLETED, cur.getProcessedAmount(), cur.getProcessedCurrency(), context);
                        }
                    } catch (InvoiceApiException e) {
                        throw new PaymentApiException(e);
                    }
                    return null;
                }
            });
            return true;
        } catch (AccountApiException e) {
            throw new PaymentApiException(e);
        }
    }
}
