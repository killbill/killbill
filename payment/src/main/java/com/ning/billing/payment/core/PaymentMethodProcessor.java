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

package com.ning.billing.payment.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountInternalApi;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.commons.locker.GlobalLocker;
import com.ning.billing.invoice.api.InvoiceInternalApi;
import com.ning.billing.osgi.api.OSGIServiceRegistration;
import com.ning.billing.payment.api.DefaultPaymentMethod;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.payment.api.PaymentMethodKVInfo;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.dao.PaymentMethodModelDao;
import com.ning.billing.payment.plugin.api.PaymentMethodInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.provider.DefaultNoOpPaymentMethodPlugin;
import com.ning.billing.payment.provider.DefaultPaymentMethodInfoPlugin;
import com.ning.billing.payment.provider.ExternalPaymentProviderPlugin;
import com.ning.billing.tag.TagInternalApi;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.entity.DefaultPagination;
import com.ning.billing.util.entity.Pagination;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import static com.ning.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;

public class PaymentMethodProcessor extends ProcessorBase {

    private static final Logger log = LoggerFactory.getLogger(PaymentMethodProcessor.class);

    @Inject
    public PaymentMethodProcessor(final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry,
                                  final AccountInternalApi accountInternalApi,
                                  final InvoiceInternalApi invoiceApi,
                                  final PersistentBus eventBus,
                                  final PaymentDao paymentDao,
                                  final NonEntityDao nonEntityDao,
                                  final TagInternalApi tagUserApi,
                                  final GlobalLocker locker,
                                  @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor) {
        super(pluginRegistry, accountInternalApi, eventBus, paymentDao, nonEntityDao, tagUserApi, locker, executor, invoiceApi);
    }

    public UUID addPaymentMethod(final String paymentPluginServiceName, final Account account,
                                 final boolean setDefault, final PaymentMethodPlugin paymentMethodProps, final InternalCallContext context)
            throws PaymentApiException {
        final UUID tenantId = nonEntityDao.retrieveIdFromObject(context.getTenantRecordId(), ObjectType.TENANT);

        return new WithAccountLock<UUID>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<UUID>() {

            @Override
            public UUID doOperation() throws PaymentApiException {
                PaymentMethod pm = null;
                PaymentPluginApi pluginApi = null;
                try {
                    pluginApi = getPaymentPluginApi(paymentPluginServiceName);
                    pm = new DefaultPaymentMethod(account.getId(), paymentPluginServiceName, paymentMethodProps);
                    pluginApi.addPaymentMethod(account.getId(), pm.getId(), paymentMethodProps, setDefault, context.toCallContext(tenantId));
                    final PaymentMethodModelDao pmModel = new PaymentMethodModelDao(pm.getId(), pm.getCreatedDate(), pm.getUpdatedDate(),
                                                                                    pm.getAccountId(), pm.getPluginName(), pm.isActive());
                    paymentDao.insertPaymentMethod(pmModel, context);

                    if (setDefault) {
                        accountInternalApi.updatePaymentMethod(account.getId(), pm.getId(), context);
                    }
                } catch (PaymentPluginApiException e) {
                    log.warn("Error adding payment method " + pm.getId() + " for plugin " + paymentPluginServiceName, e);
                    // STEPH all errors should also take a pluginName
                    throw new PaymentApiException(ErrorCode.PAYMENT_ADD_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
                } catch (AccountApiException e) {
                    throw new PaymentApiException(e);
                }
                return pm.getId();
            }
        });
    }

    public List<PaymentMethod> getPaymentMethods(final Account account, final boolean withPluginInfo, final InternalTenantContext context) throws PaymentApiException {

        final List<PaymentMethodModelDao> paymentMethodModels = paymentDao.getPaymentMethods(account.getId(), context);
        if (paymentMethodModels.size() == 0) {
            return Collections.emptyList();
        }
        return getPaymentMethodInternal(paymentMethodModels, withPluginInfo, context);
    }

    public PaymentMethod getPaymentMethodById(final UUID paymentMethodId, final boolean includedDeleted, final boolean withPluginInfo, final InternalTenantContext context)
            throws PaymentApiException {
        final PaymentMethodModelDao paymentMethodModel = includedDeleted ? paymentDao.getPaymentMethodIncludedDeleted(paymentMethodId, context) : paymentDao.getPaymentMethod(paymentMethodId, context);
        if (paymentMethodModel == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
        }

        return buildDefaultPaymentMethod(paymentMethodModel, withPluginInfo, context);
    }

    private PaymentMethod buildDefaultPaymentMethod(final PaymentMethodModelDao paymentMethodModelDao, final boolean withPluginInfo, final InternalTenantContext context) throws PaymentApiException {
        final PaymentMethodPlugin paymentMethodPlugin;
        if (withPluginInfo) {
            try {
                final PaymentPluginApi pluginApi = getPaymentPluginApi(paymentMethodModelDao.getPluginName());
                paymentMethodPlugin = pluginApi.getPaymentMethodDetail(paymentMethodModelDao.getAccountId(), paymentMethodModelDao.getId(), buildTenantContext(context));
            } catch (PaymentPluginApiException e) {
                log.warn("Error retrieving payment method " + paymentMethodModelDao.getId() + " from plugin " + paymentMethodModelDao.getPluginName(), e);
                throw new PaymentApiException(ErrorCode.PAYMENT_GET_PAYMENT_METHODS, paymentMethodModelDao.getAccountId(), paymentMethodModelDao.getId());
            }
        } else {
            paymentMethodPlugin = null;
        }

        return new DefaultPaymentMethod(paymentMethodModelDao, paymentMethodPlugin);
    }

    public Pagination<PaymentMethod> getPaymentMethods(final Long offset, final Long limit, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) {
        // Note that we cannot easily do streaming here, since we would have to rely on the statistics
        // returned by the Pagination objects from the plugins and we probably don't want to do that (if
        // one plugin gets it wrong, it may starve the others).
        final List<PaymentMethod> allResults = new LinkedList<PaymentMethod>();
        Long totalNbRecords = 0L;
        Long maxNbRecords = 0L;

        // Search in all plugins (we treat the full set of results as a union with respect to offset/limit)
        boolean firstSearch = true;
        for (final String pluginName : getAvailablePlugins()) {
            try {
                final Pagination<PaymentMethod> paymentMethods;
                if (allResults.size() >= limit) {
                    // We have enough results, we just keep going (limit 1) to get the stats
                    paymentMethods = getPaymentMethods(firstSearch ? offset : 0L, 1L, pluginName, tenantContext, internalTenantContext);
                    // Required to close database connections
                    ImmutableList.<PaymentMethod>copyOf(paymentMethods);
                } else {
                    paymentMethods = getPaymentMethods(firstSearch ? offset : 0L, limit - allResults.size(), pluginName, tenantContext, internalTenantContext);
                    allResults.addAll(ImmutableList.<PaymentMethod>copyOf(paymentMethods));
                }
                firstSearch = false;
                totalNbRecords += paymentMethods.getTotalNbRecords();
                maxNbRecords += paymentMethods.getMaxNbRecords();
            } catch (PaymentApiException e) {
                log.warn("Error while searching plugin " + pluginName, e);
                // Non-fatal, continue to search other plugins
            }
        }

        return new DefaultPagination<PaymentMethod>(offset, limit, totalNbRecords, maxNbRecords, allResults.iterator());
    }

    public Pagination<PaymentMethod> getPaymentMethods(final Long offset, final Long limit, final String pluginName, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentPluginApi pluginApi = getPaymentPluginApi(pluginName);

        // Find all payment methods for all accounts
        final Pagination<PaymentMethodModelDao> paymentMethodModelDaos = paymentDao.getPaymentMethods(pluginName, offset, limit, internalTenantContext);

        return new DefaultPagination<PaymentMethod>(paymentMethodModelDaos,
                                                    limit,
                                                    Iterators.<PaymentMethod>filter(Iterators.<PaymentMethodModelDao, PaymentMethod>transform(paymentMethodModelDaos.iterator(),
                                                                                                                                              new Function<PaymentMethodModelDao, PaymentMethod>() {
                                                                                                                                                  @Override
                                                                                                                                                  public PaymentMethod apply(final PaymentMethodModelDao paymentMethodModelDao) {
                                                                                                                                                      final PaymentMethodPlugin paymentMethodPlugin;
                                                                                                                                                      try {
                                                                                                                                                          paymentMethodPlugin = pluginApi.getPaymentMethodDetail(paymentMethodModelDao.getAccountId(), paymentMethodModelDao.getId(), tenantContext);
                                                                                                                                                      } catch (PaymentPluginApiException e) {
                                                                                                                                                          log.warn("Unable to find payment method id " + paymentMethodModelDao.getId() + " in plugin " + pluginName);
                                                                                                                                                          return null;
                                                                                                                                                      }

                                                                                                                                                      if (paymentMethodPlugin.getKbPaymentMethodId() == null) {
                                                                                                                                                          // Garbage from the plugin?
                                                                                                                                                          log.debug("Plugin {} returned a payment method without a kbPaymentMethodId", pluginName);
                                                                                                                                                          return null;
                                                                                                                                                      }

                                                                                                                                                      return new DefaultPaymentMethod(paymentMethodModelDao, paymentMethodPlugin);
                                                                                                                                                  }
                                                                                                                                              }),
                                                                                    Predicates.<PaymentMethod>notNull()));
    }

    public Pagination<PaymentMethod> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final InternalTenantContext internalTenantContext) {
        // Note that we cannot easily do streaming here, since we would have to rely on the statistics
        // returned by the Pagination objects from the plugins and we probably don't want to do that (if
        // one plugin gets it wrong, it may starve the others).
        final List<PaymentMethod> allResults = new LinkedList<PaymentMethod>();
        Long totalNbRecords = 0L;
        Long maxNbRecords = 0L;

        // Search in all plugins (we treat the full set of results as a union with respect to offset/limit)
        boolean firstSearch = true;
        for (final String pluginName : getAvailablePlugins()) {
            try {
                final Pagination<PaymentMethod> paymentMethods;
                if (allResults.size() >= limit) {
                    // We have enough results, we just keep going (limit 1) to get the stats
                    paymentMethods = searchPaymentMethods(searchKey, firstSearch ? offset : 0L, 1L, pluginName, internalTenantContext);
                    // Required to close database connections
                    ImmutableList.<PaymentMethod>copyOf(paymentMethods);
                } else {
                    paymentMethods = searchPaymentMethods(searchKey, firstSearch ? offset : 0L, limit - allResults.size(), pluginName, internalTenantContext);
                    allResults.addAll(ImmutableList.<PaymentMethod>copyOf(paymentMethods));
                }
                firstSearch = false;
                totalNbRecords += paymentMethods.getTotalNbRecords();
                maxNbRecords += paymentMethods.getMaxNbRecords();
            } catch (PaymentApiException e) {
                log.warn("Error while searching plugin " + pluginName, e);
                // Non-fatal, continue to search other plugins
            }
        }

        return new DefaultPagination<PaymentMethod>(offset, limit, totalNbRecords, maxNbRecords, allResults.iterator());
    }

    public Pagination<PaymentMethod> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final String pluginName, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentPluginApi pluginApi = getPaymentPluginApi(pluginName);
        final Pagination<PaymentMethodPlugin> paymentMethods;
        try {
            paymentMethods = pluginApi.searchPaymentMethods(searchKey, offset, limit, buildTenantContext(internalTenantContext));
        } catch (PaymentPluginApiException e) {
            throw new PaymentApiException(e, ErrorCode.PAYMENT_PLUGIN_SEARCH_PAYMENT_METHODS, pluginName, searchKey);
        }

        return new DefaultPagination<PaymentMethod>(paymentMethods,
                                                    limit,
                                                    Iterators.<PaymentMethod>filter(Iterators.<PaymentMethodPlugin, PaymentMethod>transform(paymentMethods.iterator(),
                                                                                                                                            new Function<PaymentMethodPlugin, PaymentMethod>() {
                                                                                                                                                @Override
                                                                                                                                                public PaymentMethod apply(final PaymentMethodPlugin paymentMethodPlugin) {
                                                                                                                                                    if (paymentMethodPlugin.getKbPaymentMethodId() == null) {
                                                                                                                                                        // Garbage from the plugin?
                                                                                                                                                        log.debug("Plugin {} returned a payment method without a kbPaymentMethodId for searchKey {}", pluginName, searchKey);
                                                                                                                                                        return null;
                                                                                                                                                    }

                                                                                                                                                    final PaymentMethodModelDao paymentMethodModelDao = paymentDao.getPaymentMethodIncludedDeleted(paymentMethodPlugin.getKbPaymentMethodId(), internalTenantContext);
                                                                                                                                                    if (paymentMethodModelDao == null) {
                                                                                                                                                        log.warn("Unable to find payment method id " + paymentMethodPlugin.getKbPaymentMethodId() + " present in plugin " + pluginName);
                                                                                                                                                        return null;
                                                                                                                                                    }

                                                                                                                                                    return new DefaultPaymentMethod(paymentMethodModelDao, paymentMethodPlugin);
                                                                                                                                                }
                                                                                                                                            }),
                                                                                    Predicates.<PaymentMethod>notNull()));
    }

    public PaymentMethod getExternalPaymentMethod(final Account account, final InternalTenantContext context) throws PaymentApiException {
        final List<PaymentMethod> paymentMethods = getPaymentMethods(account, false, context);
        for (final PaymentMethod paymentMethod : paymentMethods) {
            if (ExternalPaymentProviderPlugin.PLUGIN_NAME.equals(paymentMethod.getPluginName())) {
                return paymentMethod;
            }
        }

        return null;
    }

    public ExternalPaymentProviderPlugin getExternalPaymentProviderPlugin(final Account account, final InternalCallContext context) throws PaymentApiException {
        // Check if this account has already used the external payment plugin
        // If not, it's the first time - add a payment method for it
        if (getExternalPaymentMethod(account, context) == null) {
            final DefaultNoOpPaymentMethodPlugin props = new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), false, ImmutableList.<PaymentMethodKVInfo>of());
            addPaymentMethod(ExternalPaymentProviderPlugin.PLUGIN_NAME, account, false, props, context);
        }

        return (ExternalPaymentProviderPlugin) getPaymentPluginApi(ExternalPaymentProviderPlugin.PLUGIN_NAME);
    }

    private List<PaymentMethod> getPaymentMethodInternal(final List<PaymentMethodModelDao> paymentMethodModels, final boolean withPluginInfo, final InternalTenantContext context)
            throws PaymentApiException {

        final List<PaymentMethod> result = new ArrayList<PaymentMethod>(paymentMethodModels.size());
        for (final PaymentMethodModelDao paymentMethodModel : paymentMethodModels) {
            final PaymentMethod pm = buildDefaultPaymentMethod(paymentMethodModel, withPluginInfo, context);
            result.add(pm);
        }
        return result;
    }

    public void deletedPaymentMethod(final Account account, final UUID paymentMethodId,
                                     final boolean deleteDefaultPaymentMethodWithAutoPayOff, final InternalCallContext context)
            throws PaymentApiException {
        final UUID tenantId = nonEntityDao.retrieveIdFromObject(context.getTenantRecordId(), ObjectType.TENANT);

        new WithAccountLock<Void>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<Void>() {

            @Override
            public Void doOperation() throws PaymentApiException {
                final PaymentMethodModelDao paymentMethodModel = paymentDao.getPaymentMethod(paymentMethodId, context);
                if (paymentMethodModel == null) {
                    throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
                }

                try {
                    // Note: account.getPaymentMethodId() may be null
                    if (paymentMethodId.equals(account.getPaymentMethodId())) {
                        if (!deleteDefaultPaymentMethodWithAutoPayOff) {
                            throw new PaymentApiException(ErrorCode.PAYMENT_DEL_DEFAULT_PAYMENT_METHOD, account.getId());
                        } else {
                            final boolean isAccountAutoPayOff = isAccountAutoPayOff(account.getId(), context);
                            if (!isAccountAutoPayOff) {
                                log.info("Setting account {} to AUTO_PAY_OFF because of default payment method deletion", account.getId());
                                setAccountAutoPayOff(account.getId(), context);
                            }
                            accountInternalApi.removePaymentMethod(account.getId(), context);
                        }
                    }
                    final PaymentPluginApi pluginApi = getPluginApi(paymentMethodId, context);
                    pluginApi.deletePaymentMethod(account.getId(), paymentMethodId, context.toCallContext(tenantId));
                    paymentDao.deletedPaymentMethod(paymentMethodId, context);
                    return null;
                } catch (PaymentPluginApiException e) {
                    log.warn("Error deleting payment method " + paymentMethodId, e);
                    throw new PaymentApiException(ErrorCode.PAYMENT_DEL_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
                } catch (AccountApiException e) {
                    throw new PaymentApiException(e);
                }
            }
        });
    }

    public void setDefaultPaymentMethod(final Account account, final UUID paymentMethodId, final InternalCallContext context)
            throws PaymentApiException {
        final UUID tenantId = nonEntityDao.retrieveIdFromObject(context.getTenantRecordId(), ObjectType.TENANT);

        new WithAccountLock<Void>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<Void>() {

            @Override
            public Void doOperation() throws PaymentApiException {
                final PaymentMethodModelDao paymentMethodModel = paymentDao.getPaymentMethod(paymentMethodId, context);
                if (paymentMethodModel == null) {
                    throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
                }

                try {
                    final PaymentPluginApi pluginApi = getPluginApi(paymentMethodId, context);

                    pluginApi.setDefaultPaymentMethod(account.getId(), paymentMethodId, context.toCallContext(tenantId));
                    accountInternalApi.updatePaymentMethod(account.getId(), paymentMethodId, context);
                    return null;
                } catch (PaymentPluginApiException e) {
                    throw new PaymentApiException(ErrorCode.PAYMENT_UPD_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
                } catch (AccountApiException e) {
                    throw new PaymentApiException(e);
                }
            }
        });
    }

    private PaymentPluginApi getPluginApi(final UUID paymentMethodId, final InternalTenantContext context)
            throws PaymentApiException {
        final PaymentMethodModelDao paymentMethod = paymentDao.getPaymentMethod(paymentMethodId, context);
        if (paymentMethod == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
        }
        return getPaymentPluginApi(paymentMethod.getPluginName());
    }

    /**
     * This refreshed the payment methods from the plugin for cases when adding payment method does not flow through KB because of PCI compliance
     * issues. The logic below is not optimal because there is no atomicity in the step but the good news is that this is idempotent so can always be
     * replayed if necessary-- partial failure scenario.
     *
     * @param pluginName
     * @param account
     * @param context
     * @return the list of payment methods -- should be identical between KB, the plugin view-- if it keeps a state-- and the gateway.
     * @throws PaymentApiException
     */
    public List<PaymentMethod> refreshPaymentMethods(final String pluginName, final Account account, final InternalCallContext context) throws PaymentApiException {
        final UUID tenantId = nonEntityDao.retrieveIdFromObject(context.getTenantRecordId(), ObjectType.TENANT);

        // Don't hold the account lock while fetching the payment methods from the gateway as those could change anyway
        final PaymentPluginApi pluginApi = getPaymentPluginApi(pluginName);
        final List<PaymentMethodInfoPlugin> pluginPms;
        try {
            pluginPms = pluginApi.getPaymentMethods(account.getId(), true, context.toCallContext(tenantId));
            // The method should never return null by convention, but let's not trust the plugin...
            if (pluginPms == null) {
                log.debug("No payment methods defined on the account {} for plugin {}", account.getId(), pluginName);
                return ImmutableList.<PaymentMethod>of();
            }
        } catch (PaymentPluginApiException e) {
            log.warn("Error refreshing payment methods for account " + account.getId() + " and plugin " + pluginName, e);
            throw new PaymentApiException(ErrorCode.PAYMENT_REFRESH_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
        }

        return new WithAccountLock<List<PaymentMethod>>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<List<PaymentMethod>>() {

            @Override
            public List<PaymentMethod> doOperation() throws PaymentApiException {

                UUID defaultPaymentMethodId = null;

                final List<PaymentMethodInfoPlugin> pluginPmsWithId = new ArrayList<PaymentMethodInfoPlugin>();
                final List<PaymentMethodModelDao> finalPaymentMethods = new ArrayList<PaymentMethodModelDao>();
                for (final PaymentMethodInfoPlugin cur : pluginPms) {
                    // If the kbPaymentId is NULL, the plugin does not know about it, so we create a new UUID
                    final UUID paymentMethodId = cur.getPaymentMethodId() != null ? cur.getPaymentMethodId() : UUID.randomUUID();
                    final PaymentMethod input = new DefaultPaymentMethod(paymentMethodId, account.getId(), pluginName);
                    final PaymentMethodModelDao pmModel = new PaymentMethodModelDao(input.getId(), input.getCreatedDate(), input.getUpdatedDate(),
                                                                                    input.getAccountId(), input.getPluginName(), input.isActive());
                    finalPaymentMethods.add(pmModel);

                    pluginPmsWithId.add(new DefaultPaymentMethodInfoPlugin(cur, paymentMethodId));

                    // Note: we do not unset the default payment method in Kill Bill even if isDefault is false here.
                    // Some gateways don't support the concept of "default" payment methods, in that case the plugin
                    // will always return false - it's Kill Bill in that case which is responsible to manage default payment methods
                    if (cur.isDefault()) {
                        defaultPaymentMethodId = paymentMethodId;
                    }
                }

                final List<PaymentMethodModelDao> refreshedPaymentMethods = paymentDao.refreshPaymentMethods(account.getId(),
                                                                                                             pluginName,
                                                                                                             finalPaymentMethods,
                                                                                                             context);
                try {
                    pluginApi.resetPaymentMethods(account.getId(), pluginPmsWithId);
                } catch (PaymentPluginApiException e) {
                    log.warn("Error resetting payment methods for account " + account.getId() + " and plugin " + pluginName, e);
                    throw new PaymentApiException(ErrorCode.PAYMENT_REFRESH_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
                }

                try {
                    updateDefaultPaymentMethodIfNeeded(pluginName, account, defaultPaymentMethodId, context);
                } catch (AccountApiException e) {
                    throw new PaymentApiException(e);
                }

                return ImmutableList.<PaymentMethod>copyOf(Collections2.transform(refreshedPaymentMethods, new Function<PaymentMethodModelDao, PaymentMethod>() {
                    @Override
                    public PaymentMethod apply(final PaymentMethodModelDao input) {
                        return new DefaultPaymentMethod(input, null);
                    }
                }));
            }
        });
    }

    private void updateDefaultPaymentMethodIfNeeded(final String pluginName, final Account account, @Nullable final UUID defaultPluginPaymentMethodId, final InternalCallContext context) throws PaymentApiException, AccountApiException {

        // If the plugin does not have a default payment gateway, we keep the current default payment method in KB account as it is.
        if (defaultPluginPaymentMethodId == null) {
            return;
        }

        // Some gateways have the concept of default payment methods. Kill Bill has also its own default payment method
        // and is authoritative on this matter. However, if the default payment method is associated with a given plugin,
        // and if the default payment method in that plugin has changed, we will reflect this change in Kill Bill as well.

        boolean shouldUpdateDefaultPaymentMethod = true;
        if (account.getPaymentMethodId() != null) {
            final PaymentMethodModelDao currentDefaultPaymentMethod = paymentDao.getPaymentMethod(account.getPaymentMethodId(), context);
            shouldUpdateDefaultPaymentMethod = pluginName.equals(currentDefaultPaymentMethod.getPluginName());
        }
        if (shouldUpdateDefaultPaymentMethod) {
            accountInternalApi.updatePaymentMethod(account.getId(), defaultPluginPaymentMethodId, context);
        }
    }
}
