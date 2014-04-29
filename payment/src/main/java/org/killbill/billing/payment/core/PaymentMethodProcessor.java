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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nullable;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.DefaultPaymentMethod;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentMethodModelDao;
import org.killbill.billing.payment.plugin.api.PaymentMethodInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.provider.DefaultNoOpPaymentMethodPlugin;
import org.killbill.billing.payment.provider.DefaultPaymentMethodInfoPlugin;
import org.killbill.billing.payment.provider.ExternalPaymentProviderPlugin;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.EntityPaginationBuilder;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.SourcePaginationBuilder;
import org.killbill.bus.api.PersistentBus;
import org.killbill.commons.locker.GlobalLocker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import static org.killbill.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;
import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPagination;
import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationFromPlugins;

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
                                 final boolean setDefault, final PaymentMethodPlugin paymentMethodProps,
                                 final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext context)
            throws PaymentApiException {
        return new WithAccountLock<UUID>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<UUID>() {

            @Override
            public UUID doOperation() throws PaymentApiException {
                PaymentMethod pm = null;
                PaymentPluginApi pluginApi;
                try {
                    pluginApi = getPaymentPluginApi(paymentPluginServiceName);
                    pm = new DefaultPaymentMethod(account.getId(), paymentPluginServiceName, paymentMethodProps);
                    pluginApi.addPaymentMethod(account.getId(), pm.getId(), paymentMethodProps, setDefault, properties, callContext);
                    final PaymentMethodModelDao pmModel = new PaymentMethodModelDao(pm.getId(), pm.getCreatedDate(), pm.getUpdatedDate(),
                                                                                    pm.getAccountId(), pm.getPluginName(), pm.isActive());
                    paymentDao.insertPaymentMethod(pmModel, context);

                    if (setDefault) {
                        accountInternalApi.updatePaymentMethod(account.getId(), pm.getId(), context);
                    }
                } catch (final PaymentPluginApiException e) {
                    log.warn("Error adding payment method " + pm.getId() + " for plugin " + paymentPluginServiceName, e);
                    // STEPH all errors should also take a pluginName
                    throw new PaymentApiException(ErrorCode.PAYMENT_ADD_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
                } catch (final AccountApiException e) {
                    throw new PaymentApiException(e);
                }
                return pm.getId();
            }
        });
    }

    public List<PaymentMethod> getPaymentMethods(final Account account, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final InternalTenantContext context) throws PaymentApiException {
        return getPaymentMethods(account, withPluginInfo, properties, buildTenantContext(context), context);
    }

    public List<PaymentMethod> getPaymentMethods(final Account account, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext context) throws PaymentApiException {
        final List<PaymentMethodModelDao> paymentMethodModels = paymentDao.getPaymentMethods(account.getId(), context);
        if (paymentMethodModels.size() == 0) {
            return Collections.emptyList();
        }
        return getPaymentMethodInternal(paymentMethodModels, withPluginInfo, properties, tenantContext, context);
    }

    public PaymentMethod getPaymentMethodById(final UUID paymentMethodId, final boolean includedDeleted, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final InternalTenantContext context)
            throws PaymentApiException {
        return getPaymentMethodById(paymentMethodId, includedDeleted, withPluginInfo, properties, buildTenantContext(context), context);
    }

    public PaymentMethod getPaymentMethodById(final UUID paymentMethodId, final boolean includedDeleted, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext context)
            throws PaymentApiException {
        final PaymentMethodModelDao paymentMethodModel = includedDeleted ? paymentDao.getPaymentMethodIncludedDeleted(paymentMethodId, context) : paymentDao.getPaymentMethod(paymentMethodId, context);
        if (paymentMethodModel == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
        }

        return buildDefaultPaymentMethod(paymentMethodModel, withPluginInfo, properties, tenantContext, context);
    }

    private PaymentMethod buildDefaultPaymentMethod(final PaymentMethodModelDao paymentMethodModelDao, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext context) throws PaymentApiException {
        final PaymentMethodPlugin paymentMethodPlugin;
        if (withPluginInfo) {
            try {
                final PaymentPluginApi pluginApi = getPaymentPluginApi(paymentMethodModelDao.getPluginName());
                paymentMethodPlugin = pluginApi.getPaymentMethodDetail(paymentMethodModelDao.getAccountId(), paymentMethodModelDao.getId(), properties, tenantContext);
            } catch (final PaymentPluginApiException e) {
                log.warn("Error retrieving payment method " + paymentMethodModelDao.getId() + " from plugin " + paymentMethodModelDao.getPluginName(), e);
                throw new PaymentApiException(ErrorCode.PAYMENT_GET_PAYMENT_METHODS, paymentMethodModelDao.getAccountId(), paymentMethodModelDao.getId());
            }
        } else {
            paymentMethodPlugin = null;
        }

        return new DefaultPaymentMethod(paymentMethodModelDao, paymentMethodPlugin);
    }

    public Pagination<PaymentMethod> getPaymentMethods(final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) {
        return getEntityPaginationFromPlugins(getAvailablePlugins(),
                                              offset,
                                              limit,
                                              new EntityPaginationBuilder<PaymentMethod, PaymentApiException>() {
                                                  @Override
                                                  public Pagination<PaymentMethod> build(final Long offset, final Long limit, final String pluginName) throws PaymentApiException {
                                                      return getPaymentMethods(offset, limit, pluginName, properties, tenantContext, internalTenantContext);
                                                  }
                                              }
                                             );
    }

    public Pagination<PaymentMethod> getPaymentMethods(final Long offset, final Long limit, final String pluginName, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentPluginApi pluginApi = getPaymentPluginApi(pluginName);

        return getEntityPagination(limit,
                                   new SourcePaginationBuilder<PaymentMethodModelDao, PaymentApiException>() {
                                       @Override
                                       public Pagination<PaymentMethodModelDao> build() {
                                           // Find all payment methods for all accounts
                                           return paymentDao.getPaymentMethods(pluginName, offset, limit, internalTenantContext);
                                       }
                                   },
                                   new Function<PaymentMethodModelDao, PaymentMethod>() {
                                       @Override
                                       public PaymentMethod apply(final PaymentMethodModelDao paymentMethodModelDao) {
                                           PaymentMethodPlugin paymentMethodPlugin = null;
                                           try {
                                               paymentMethodPlugin = pluginApi.getPaymentMethodDetail(paymentMethodModelDao.getAccountId(), paymentMethodModelDao.getId(), properties, tenantContext);
                                           } catch (final PaymentPluginApiException e) {
                                               log.warn("Unable to find payment method id " + paymentMethodModelDao.getId() + " in plugin " + pluginName);
                                               // We still want to return a payment method object, even though the plugin details are missing
                                           }

                                           return new DefaultPaymentMethod(paymentMethodModelDao, paymentMethodPlugin);
                                       }
                                   }
                                  );
    }

    public Pagination<PaymentMethod> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) {
        return getEntityPaginationFromPlugins(getAvailablePlugins(),
                                              offset,
                                              limit,
                                              new EntityPaginationBuilder<PaymentMethod, PaymentApiException>() {
                                                  @Override
                                                  public Pagination<PaymentMethod> build(final Long offset, final Long limit, final String pluginName) throws PaymentApiException {
                                                      return searchPaymentMethods(searchKey, offset, limit, pluginName, properties, tenantContext, internalTenantContext);
                                                  }
                                              }
                                             );
    }

    public Pagination<PaymentMethod> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final String pluginName,
                                                          final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentPluginApi pluginApi = getPaymentPluginApi(pluginName);

        return getEntityPagination(limit,
                                   new SourcePaginationBuilder<PaymentMethodPlugin, PaymentApiException>() {
                                       @Override
                                       public Pagination<PaymentMethodPlugin> build() throws PaymentApiException {
                                           try {
                                               return pluginApi.searchPaymentMethods(searchKey, offset, limit, properties, tenantContext);
                                           } catch (final PaymentPluginApiException e) {
                                               throw new PaymentApiException(e, ErrorCode.PAYMENT_PLUGIN_SEARCH_PAYMENT_METHODS, pluginName, searchKey);
                                           }
                                       }
                                   },
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
                                   }
                                  );
    }

    public PaymentMethod getExternalPaymentMethod(final Account account, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext context) throws PaymentApiException {
        final List<PaymentMethod> paymentMethods = getPaymentMethods(account, false, properties, tenantContext, context);
        for (final PaymentMethod paymentMethod : paymentMethods) {
            if (ExternalPaymentProviderPlugin.PLUGIN_NAME.equals(paymentMethod.getPluginName())) {
                return paymentMethod;
            }
        }

        return null;
    }

    public ExternalPaymentProviderPlugin getExternalPaymentProviderPlugin(final Account account, final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext context) throws PaymentApiException {
        // Check if this account has already used the external payment plugin
        // If not, it's the first time - add a payment method for it
        if (getExternalPaymentMethod(account, properties, callContext, context) == null) {
            final DefaultNoOpPaymentMethodPlugin props = new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), false, properties);
            addPaymentMethod(ExternalPaymentProviderPlugin.PLUGIN_NAME, account, false, props, properties, callContext, context);
        }

        return (ExternalPaymentProviderPlugin) getPaymentPluginApi(ExternalPaymentProviderPlugin.PLUGIN_NAME);
    }

    private List<PaymentMethod> getPaymentMethodInternal(final List<PaymentMethodModelDao> paymentMethodModels, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext context)
            throws PaymentApiException {

        final List<PaymentMethod> result = new ArrayList<PaymentMethod>(paymentMethodModels.size());
        for (final PaymentMethodModelDao paymentMethodModel : paymentMethodModels) {
            final PaymentMethod pm = buildDefaultPaymentMethod(paymentMethodModel, withPluginInfo, properties, tenantContext, context);
            result.add(pm);
        }
        return result;
    }

    public void deletedPaymentMethod(final Account account, final UUID paymentMethodId,
                                     final boolean deleteDefaultPaymentMethodWithAutoPayOff,
                                     final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext context)
            throws PaymentApiException {
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
                    pluginApi.deletePaymentMethod(account.getId(), paymentMethodId, properties, callContext);
                    paymentDao.deletedPaymentMethod(paymentMethodId, context);
                    return null;
                } catch (final PaymentPluginApiException e) {
                    log.warn("Error deleting payment method " + paymentMethodId, e);
                    throw new PaymentApiException(ErrorCode.PAYMENT_DEL_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
                } catch (final AccountApiException e) {
                    throw new PaymentApiException(e);
                }
            }
        });
    }

    public void setDefaultPaymentMethod(final Account account, final UUID paymentMethodId, final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext context)
            throws PaymentApiException {
        new WithAccountLock<Void>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<Void>() {

            @Override
            public Void doOperation() throws PaymentApiException {
                final PaymentMethodModelDao paymentMethodModel = paymentDao.getPaymentMethod(paymentMethodId, context);
                if (paymentMethodModel == null) {
                    throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
                }

                try {
                    final PaymentPluginApi pluginApi = getPluginApi(paymentMethodId, context);

                    pluginApi.setDefaultPaymentMethod(account.getId(), paymentMethodId, properties, callContext);
                    accountInternalApi.updatePaymentMethod(account.getId(), paymentMethodId, context);
                    return null;
                } catch (final PaymentPluginApiException e) {
                    throw new PaymentApiException(ErrorCode.PAYMENT_UPD_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
                } catch (final AccountApiException e) {
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
    public List<PaymentMethod> refreshPaymentMethods(final String pluginName, final Account account, final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext context) throws PaymentApiException {
        // Don't hold the account lock while fetching the payment methods from the gateway as those could change anyway
        final PaymentPluginApi pluginApi = getPaymentPluginApi(pluginName);
        final List<PaymentMethodInfoPlugin> pluginPms;
        try {
            pluginPms = pluginApi.getPaymentMethods(account.getId(), true, properties, callContext);
            // The method should never return null by convention, but let's not trust the plugin...
            if (pluginPms == null) {
                log.debug("No payment methods defined on the account {} for plugin {}", account.getId(), pluginName);
                return ImmutableList.<PaymentMethod>of();
            }
        } catch (final PaymentPluginApiException e) {
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
                    pluginApi.resetPaymentMethods(account.getId(), pluginPmsWithId, properties, callContext);
                } catch (final PaymentPluginApiException e) {
                    log.warn("Error resetting payment methods for account " + account.getId() + " and plugin " + pluginName, e);
                    throw new PaymentApiException(ErrorCode.PAYMENT_REFRESH_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
                }

                try {
                    updateDefaultPaymentMethodIfNeeded(pluginName, account, defaultPaymentMethodId, context);
                } catch (final AccountApiException e) {
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
