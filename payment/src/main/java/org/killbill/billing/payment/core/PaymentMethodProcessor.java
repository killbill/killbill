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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.control.plugin.api.PaymentApiType;
import org.killbill.billing.control.plugin.api.PaymentControlApiException;
import org.killbill.billing.control.plugin.api.PriorPaymentControlResult;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.payment.api.DefaultPaymentMethod;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.core.sm.control.ControlPluginRunner;
import org.killbill.billing.payment.core.sm.control.PaymentControlApiAbortException;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentMethodModelDao;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.dispatcher.PluginDispatcher.PluginDispatcherReturnType;
import org.killbill.billing.payment.plugin.api.PaymentMethodInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.provider.DefaultNoOpPaymentMethodPlugin;
import org.killbill.billing.payment.provider.DefaultPaymentMethodInfoPlugin;
import org.killbill.billing.payment.provider.ExternalPaymentProviderPlugin;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.PluginProperties;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.util.entity.DefaultPagination;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.EntityPaginationBuilder;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.SourcePaginationBuilder;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

import static org.killbill.billing.payment.dispatcher.PaymentPluginDispatcher.dispatchWithExceptionHandling;
import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPagination;
import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationFromPlugins;

public class PaymentMethodProcessor extends ProcessorBase {

    private static final Logger log = LoggerFactory.getLogger(PaymentMethodProcessor.class);
    private static final Joiner JOINER = Joiner.on(", ");

    private final PluginDispatcher<UUID> uuidPluginNotificationDispatcher;

    private final ControlPluginRunner controlPluginRunner;

    private final PaymentConfig paymentConfig;

    @Inject
    public PaymentMethodProcessor(final PaymentPluginServiceRegistration paymentPluginServiceRegistration,
                                  final AccountInternalApi accountInternalApi,
                                  final InvoiceInternalApi invoiceApi,
                                  final PaymentDao paymentDao,
                                  final TagInternalApi tagUserApi,
                                  final GlobalLocker locker,
                                  final PaymentConfig paymentConfig,
                                  final PaymentExecutors executors,
                                  final ControlPluginRunner controlPluginRunner,
                                  final InternalCallContextFactory internalCallContextFactory,
                                  final Clock clock) {
        super(paymentPluginServiceRegistration, accountInternalApi, paymentDao, tagUserApi, locker, internalCallContextFactory, invoiceApi, clock);
        final long paymentPluginTimeoutSec = TimeUnit.SECONDS.convert(paymentConfig.getPaymentPluginTimeout().getPeriod(), paymentConfig.getPaymentPluginTimeout().getUnit());
        this.paymentConfig = paymentConfig;
        this.controlPluginRunner = controlPluginRunner;
        this.uuidPluginNotificationDispatcher = new PluginDispatcher<UUID>(paymentPluginTimeoutSec, executors);
    }

    public UUID addPaymentMethod(final String paymentMethodExternalKey, final String paymentPluginServiceName, final Account account,
                                 final boolean setDefault, final PaymentMethodPlugin paymentMethodProps,
                                 final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext context)
            throws PaymentApiException {
        return dispatchWithExceptionHandling(account,
                                             paymentPluginServiceName,
                                             new CallableWithAccountLock<UUID, PaymentApiException>(locker,
                                                                                                    account.getId(),
                                                                                                    paymentConfig,
                                                                                                    new DispatcherCallback<PluginDispatcherReturnType<UUID>, PaymentApiException>() {

                                                                                                        @Override
                                                                                                        public PluginDispatcherReturnType<UUID> doOperation() throws PaymentApiException {
                                                                                                            PaymentMethod pm = null;
                                                                                                            try {

                                                                                                                validateUniqueExternalPaymentMethod(account.getId(), paymentPluginServiceName);

                                                                                                                pm = new DefaultPaymentMethod(paymentMethodExternalKey, account.getId(), paymentPluginServiceName, paymentMethodProps);
                                                                                                                final PaymentPluginApi pluginApi = getPaymentPluginApi(paymentPluginServiceName);
                                                                                                                pluginApi.addPaymentMethod(account.getId(), pm.getId(), paymentMethodProps, setDefault, properties, callContext);

                                                                                                                final String actualPaymentMethodExternalKey = retrieveActualPaymentMethodExternalKey(account, pm, pluginApi, properties, callContext, context);
                                                                                                                final PaymentMethodModelDao pmModel = new PaymentMethodModelDao(pm.getId(),
                                                                                                                                                                                actualPaymentMethodExternalKey,
                                                                                                                                                                                pm.getCreatedDate(),
                                                                                                                                                                                pm.getUpdatedDate(),
                                                                                                                                                                                pm.getAccountId(),
                                                                                                                                                                                pm.getPluginName(),
                                                                                                                                                                                pm.isActive());
                                                                                                                paymentDao.insertPaymentMethod(pmModel, context);

                                                                                                                if (setDefault) {
                                                                                                                    accountInternalApi.updatePaymentMethod(account.getId(), pm.getId(), context);
                                                                                                                }
                                                                                                            } catch (final PaymentPluginApiException e) {
                                                                                                                throw new PaymentApiException(ErrorCode.PAYMENT_ADD_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
                                                                                                            } catch (final AccountApiException e) {
                                                                                                                throw new PaymentApiException(e);
                                                                                                            }
                                                                                                            return PluginDispatcher.createPluginDispatcherReturnType(pm.getId());
                                                                                                        }

                                                                                                        private void validateUniqueExternalPaymentMethod(final UUID accountId, final String pluginName) throws PaymentApiException {
                                                                                                            if (ExternalPaymentProviderPlugin.PLUGIN_NAME.equals(pluginName)) {
                                                                                                                final List<PaymentMethodModelDao> accountPaymentMethods = paymentDao.getPaymentMethods(context);
                                                                                                                if (Iterables.any(accountPaymentMethods, new Predicate<PaymentMethodModelDao>() {
                                                                                                                    @Override
                                                                                                                    public boolean apply(final PaymentMethodModelDao input) {
                                                                                                                        return ExternalPaymentProviderPlugin.PLUGIN_NAME.equals(input.getPluginName());
                                                                                                                    }
                                                                                                                })) {
                                                                                                                    throw new PaymentApiException(ErrorCode.PAYMENT_EXTERNAL_PAYMENT_METHOD_ALREADY_EXISTS, accountId);
                                                                                                                }
                                                                                                            }
                                                                                                        }
                                                                                                    }),
                                             uuidPluginNotificationDispatcher);
    }

    public UUID addPaymentMethodWithControl(final String paymentMethodExternalKey, final String paymentPluginServiceName, final Account account,
                                            final boolean setDefault, final PaymentMethodPlugin paymentMethodProps, final Iterable<PluginProperty> properties,
                                            final List<String> paymentControlPluginNames, final CallContext callContext, final InternalCallContext context) throws PaymentApiException {
        final Iterable<PluginProperty> mergedProperties = PluginProperties.merge(paymentMethodProps.getProperties(), properties);
        return executeWithPaymentMethodControl(paymentPluginServiceName, account, mergedProperties, paymentControlPluginNames, callContext, uuidPluginNotificationDispatcher, new WithPaymentMethodControlCallback<UUID>() {
            @Override
            public UUID doPaymentMethodApiOperation(final String adjustedPaymentPluginServiceName, final Iterable<PluginProperty> adjustedPluginProperties) throws PaymentApiException {
                if (adjustedPaymentPluginServiceName == null) {
                    return addPaymentMethod(paymentMethodExternalKey, paymentPluginServiceName, account, setDefault, paymentMethodProps, properties, callContext, context);
                } else {
                    return addPaymentMethod(paymentMethodExternalKey, adjustedPaymentPluginServiceName, account, setDefault, paymentMethodProps, properties, callContext, context);
                }
            }
        });
    }


    private interface WithPaymentMethodControlCallback<T> {
        T doPaymentMethodApiOperation(final String adjustedPluginName, final Iterable<PluginProperty> adjustedPluginProperties) throws PaymentApiException;
    }

    private <T> T executeWithPaymentMethodControl(final String paymentPluginServiceName,
                                                  final Account account,
                                                  final Iterable<PluginProperty> properties,
                                                  final List<String> paymentControlPluginNames,
                                                  final CallContext callContext,
                                                  final PluginDispatcher<T> pluginDispatcher,
                                                  final WithPaymentMethodControlCallback<T> callback) throws PaymentApiException {

        return dispatchWithExceptionHandling(account,
                                             JOINER.join(paymentControlPluginNames),
                                             new Callable<PluginDispatcherReturnType<T>>() {
                                                 @Override
                                                 public PluginDispatcherReturnType<T> call() throws Exception {
                                                     final PriorPaymentControlResult priorCallResult;
                                                     try {
                                                         priorCallResult = controlPluginRunner.executePluginPriorCalls(account,
                                                                                                                       null,
                                                                                                                       paymentPluginServiceName,
                                                                                                                       null,
                                                                                                                       null,
                                                                                                                       null,
                                                                                                                       null,
                                                                                                                       null,
                                                                                                                       PaymentApiType.PAYMENT_METHOD,
                                                                                                                       null,
                                                                                                                       null,
                                                                                                                       null,
                                                                                                                       null,
                                                                                                                       null,
                                                                                                                       null,
                                                                                                                       true,
                                                                                                                       paymentControlPluginNames,
                                                                                                                       properties,
                                                                                                                       callContext);

                                                     } catch (final PaymentControlApiAbortException e) {
                                                         throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_API_ABORTED, e.getPluginName());
                                                     } catch (final PaymentControlApiException e) {
                                                         throw new PaymentApiException(e, ErrorCode.PAYMENT_PLUGIN_EXCEPTION, e);
                                                     }

                                                     try {
                                                         final T result = callback.doPaymentMethodApiOperation(priorCallResult.getAdjustedPluginName(), priorCallResult.getAdjustedPluginProperties());
                                                         controlPluginRunner.executePluginOnSuccessCalls(account,
                                                                                                         null,
                                                                                                         priorCallResult.getAdjustedPluginName(),
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         PaymentApiType.PAYMENT_METHOD,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         true,
                                                                                                         paymentControlPluginNames,
                                                                                                         priorCallResult.getAdjustedPluginProperties(),
                                                                                                         callContext);
                                                         return PluginDispatcher.createPluginDispatcherReturnType(result);
                                                     } catch (final PaymentApiException e) {
                                                         controlPluginRunner.executePluginOnFailureCalls(account,
                                                                                                         null,
                                                                                                         priorCallResult.getAdjustedPluginName(),
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         PaymentApiType.PAYMENT_METHOD,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         null,
                                                                                                         true,
                                                                                                         paymentControlPluginNames,
                                                                                                         priorCallResult.getAdjustedPluginProperties(),
                                                                                                         callContext);
                                                         throw e;
                                                     }
                                                 }
                                             },
                                             pluginDispatcher);
    }



    private String retrieveActualPaymentMethodExternalKey(final Account account, final PaymentMethod pm, final PaymentPluginApi pluginApi, final Iterable<PluginProperty> properties, final TenantContext callContext, final InternalCallContext context) {
        // If the user specified an external key, use it
        if (pm.getExternalKey() != null) {
            return pm.getExternalKey();
        }

        // Otherwise, check if the plugin sets an external payment method id
        final PaymentMethodPlugin paymentMethodPlugin;
        try {
            paymentMethodPlugin = pluginApi.getPaymentMethodDetail(account.getId(), pm.getId(), properties, callContext);
        } catch (final PaymentPluginApiException e) {
            if (e.getCause() == null) {
                log.warn("Error retrieving paymentMethodId='{}', plugin='{}', errorMessage='{}', errorType='{}'", pm.getId(), pm.getPluginName(), e.getErrorMessage(), e.getErrorType());
            } else {
                log.warn("Error retrieving paymentMethodId='{}', plugin='{}', errorMessage='{}', errorType='{}'", pm.getId(), pm.getPluginName(), e.getErrorMessage(), e.getErrorType(), e);
            }
            return null;
        }

        if (paymentMethodPlugin != null && paymentMethodPlugin.getExternalPaymentMethodId() != null) {
            // An external payment method id is set but make sure it doesn't conflict with an existing one
            final String externalKey = paymentMethodPlugin.getExternalPaymentMethodId();
            try {
                @SuppressWarnings("unused")
                final PaymentMethodModelDao paymentMethodModelDao = getPaymentMethodByExternalKey(externalKey, true, context);
                return null;
            } catch (final PaymentApiException e) {
                return externalKey;
            }
        } else {
            return null;
        }
    }

    public List<PaymentMethod> getPaymentMethods(final boolean includedInactive, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final InternalTenantContext context) throws PaymentApiException {
        return getPaymentMethods(includedInactive, withPluginInfo, properties, buildTenantContext(context), context);
    }

    public List<PaymentMethod> getPaymentMethods(final boolean includedInactive, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext context) throws PaymentApiException {
        final List<PaymentMethodModelDao> paymentMethodModels = includedInactive ?
                                                                paymentDao.getPaymentMethodsIncludedDeleted(context) :
                                                                paymentDao.getPaymentMethods(context);
        if (paymentMethodModels.isEmpty()) {
            return Collections.emptyList();
        }
        return getPaymentMethodInternal(paymentMethodModels, withPluginInfo, properties, tenantContext, context);
    }

    public PaymentMethod getPaymentMethodById(final UUID paymentMethodId, final boolean includedDeleted, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext context)
            throws PaymentApiException {
        final PaymentMethodModelDao paymentMethodModel = getPaymentMethodById(paymentMethodId, includedDeleted, context);
        return buildDefaultPaymentMethod(paymentMethodModel, withPluginInfo, properties, tenantContext, context);
    }

    public PaymentMethod getPaymentMethodByExternalKey(final String paymentMethodExternalKey, final boolean includedDeleted, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext context)
            throws PaymentApiException {
        final PaymentMethodModelDao paymentMethodModel = getPaymentMethodByExternalKey(paymentMethodExternalKey, includedDeleted, context);
        return buildDefaultPaymentMethod(paymentMethodModel, withPluginInfo, properties, tenantContext, context);
    }

    private PaymentMethod buildDefaultPaymentMethod(final PaymentMethodModelDao paymentMethodModelDao, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext context) throws PaymentApiException {
        final PaymentMethodPlugin paymentMethodPlugin;
        if (withPluginInfo) {
            try {
                final PaymentPluginApi pluginApi = getPaymentPluginApi(paymentMethodModelDao.getPluginName());
                paymentMethodPlugin = pluginApi.getPaymentMethodDetail(paymentMethodModelDao.getAccountId(), paymentMethodModelDao.getId(), properties, tenantContext);
            } catch (final PaymentPluginApiException e) {
                throw new PaymentApiException(e, ErrorCode.PAYMENT_GET_PAYMENT_METHODS, paymentMethodModelDao.getAccountId(), paymentMethodModelDao.getId());
            }
        } else {
            paymentMethodPlugin = null;
        }

        return new DefaultPaymentMethod(paymentMethodModelDao, paymentMethodPlugin);
    }

    public Pagination<PaymentMethod> getPaymentMethods(final Long offset, final Long limit, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) {
        return getEntityPaginationFromPlugins(true,
                                              getAvailablePlugins(),
                                              offset,
                                              limit,
                                              new EntityPaginationBuilder<PaymentMethod, PaymentApiException>() {
                                                  @Override
                                                  public Pagination<PaymentMethod> build(final Long offset, final Long limit, final String pluginName) throws PaymentApiException {
                                                      return getPaymentMethods(offset, limit, pluginName, withPluginInfo, properties, tenantContext, internalTenantContext);
                                                  }
                                              }
                                             );
    }

    public Pagination<PaymentMethod> getPaymentMethods(final Long offset, final Long limit, final String pluginName, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) throws PaymentApiException {
        final PaymentPluginApi pluginApi = withPluginInfo ? getPaymentPluginApi(pluginName) : null;

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
                                           if (pluginApi != null) {
                                               try {
                                                   paymentMethodPlugin = pluginApi.getPaymentMethodDetail(paymentMethodModelDao.getAccountId(), paymentMethodModelDao.getId(), properties, tenantContext);
                                               } catch (final PaymentPluginApiException e) {
                                                   if (e.getCause() == null) {
                                                       log.warn("Error retrieving paymentMethodId='{}', plugin='{}', errorMessage='{}', errorType='{}'", paymentMethodModelDao.getId(), pluginName, e.getErrorMessage(), e.getErrorType());
                                                   } else {
                                                       log.warn("Error retrieving paymentMethodId='{}', plugin='{}', errorMessage='{}', errorType='{}'", paymentMethodModelDao.getId(), pluginName, e.getErrorMessage(), e.getErrorType(), e);
                                                   }
                                                   // We still want to return a payment method object, even though the plugin details are missing
                                               }
                                           }

                                           return new DefaultPaymentMethod(paymentMethodModelDao, paymentMethodPlugin);
                                       }
                                   }
                                  );
    }

    public Pagination<PaymentMethod> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext internalTenantContext) {
        if (withPluginInfo) {
            return getEntityPaginationFromPlugins(false,
                                                  getAvailablePlugins(),
                                                  offset,
                                                  limit,
                                                  new EntityPaginationBuilder<PaymentMethod, PaymentApiException>() {
                                                      @Override
                                                      public Pagination<PaymentMethod> build(final Long offset, final Long limit, final String pluginName) throws PaymentApiException {
                                                          return searchPaymentMethods(searchKey, offset, limit, pluginName, withPluginInfo, properties, tenantContext, internalTenantContext);
                                                      }
                                                  }
                                                 );
        } else {
            try {
                return getEntityPagination(limit,
                                           new SourcePaginationBuilder<PaymentMethodModelDao, PaymentApiException>() {
                                               @Override
                                               public Pagination<PaymentMethodModelDao> build() {
                                                   return paymentDao.searchPaymentMethods(searchKey, offset, limit, internalTenantContext);
                                               }
                                           },
                                           new Function<PaymentMethodModelDao, PaymentMethod>() {
                                               @Override
                                               public PaymentMethod apply(final PaymentMethodModelDao paymentMethodModelDao) {
                                                   return new DefaultPaymentMethod(paymentMethodModelDao, null);
                                               }
                                           }
                                          );
            } catch (final PaymentApiException e) {
                log.warn("Unable to search through payment methods", e);
                return new DefaultPagination<PaymentMethod>(offset, limit, null, null, ImmutableSet.<PaymentMethod>of().iterator());
            }
        }
    }

    public Pagination<PaymentMethod> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final String pluginName, final boolean withPluginInfo,
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

                                           return new DefaultPaymentMethod(paymentMethodModelDao, withPluginInfo ? paymentMethodPlugin : null);
                                       }
                                   }
                                  );
    }

    private PaymentMethod getExternalPaymentMethod(final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext context) throws PaymentApiException {
        final List<PaymentMethod> paymentMethods = getPaymentMethods(false, false, properties, tenantContext, context);
        for (final PaymentMethod paymentMethod : paymentMethods) {
            if (ExternalPaymentProviderPlugin.PLUGIN_NAME.equals(paymentMethod.getPluginName())) {
                return paymentMethod;
            }
        }
        return null;
    }

    public UUID createOrGetExternalPaymentMethod(final String paymentMethodExternalKey, final Account account, final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext context) throws PaymentApiException {
        // Check if this account has already used the external payment plugin
        // If not, it's the first time - add a payment method for it
        final PaymentMethod externalPaymentMethod = getExternalPaymentMethod(properties, callContext, context);
        if (externalPaymentMethod != null) {
            return externalPaymentMethod.getId();
        }
        final DefaultNoOpPaymentMethodPlugin props = new DefaultNoOpPaymentMethodPlugin(UUIDs.randomUUID().toString(), false, properties);
        return addPaymentMethod(paymentMethodExternalKey, ExternalPaymentProviderPlugin.PLUGIN_NAME, account, false, props, properties, callContext, context);
    }

    public ExternalPaymentProviderPlugin createPaymentMethodAndGetExternalPaymentProviderPlugin(final String paymentMethodExternalKey, final Account account, final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext internalContext) throws PaymentApiException {
        // Check if this account has already used the external payment plugin
        // If not, it's the first time - add a payment method for it
        createOrGetExternalPaymentMethod(paymentMethodExternalKey, account, properties, callContext, internalContext);
        return (ExternalPaymentProviderPlugin) getPaymentPluginApi(ExternalPaymentProviderPlugin.PLUGIN_NAME);
    }

    private List<PaymentMethod> getPaymentMethodInternal(final Collection<PaymentMethodModelDao> paymentMethodModels, final boolean withPluginInfo, final Iterable<PluginProperty> properties, final TenantContext tenantContext, final InternalTenantContext context)
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
                                     final boolean forceDefaultPaymentMethodDeletion,
                                     final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext context)
            throws PaymentApiException {
        try {
            new WithAccountLock<Void, PaymentApiException>(paymentConfig).processAccountWithLock(locker, account.getId(), new DispatcherCallback<PluginDispatcherReturnType<Void>, PaymentApiException>() {

                @Override
                public PluginDispatcherReturnType<Void> doOperation() throws PaymentApiException {
                    @SuppressWarnings("unused")
                    final PaymentMethodModelDao paymentMethodModel = getPaymentMethodById(paymentMethodId, false, context);

                    try {
                        // Note: account.getPaymentMethodId() may be null
                        if (paymentMethodId.equals(account.getPaymentMethodId())) {
                            if (!deleteDefaultPaymentMethodWithAutoPayOff && !forceDefaultPaymentMethodDeletion) {
                                throw new PaymentApiException(ErrorCode.PAYMENT_DEL_DEFAULT_PAYMENT_METHOD, account.getId());
                            } else {
                                if (deleteDefaultPaymentMethodWithAutoPayOff && !isAccountAutoPayOff(account.getId(), context)) {
                                    log.info("Setting AUTO_PAY_OFF on accountId='{}' because of default payment method deletion", account.getId());
                                    setAccountAutoPayOff(account.getId(), context);
                                }
                                accountInternalApi.removePaymentMethod(account.getId(), context);
                            }
                        }
                        final PaymentPluginApi pluginApi = getPaymentProviderPlugin(paymentMethodId, false, context);
                        pluginApi.deletePaymentMethod(account.getId(), paymentMethodId, properties, callContext);
                        paymentDao.deletedPaymentMethod(paymentMethodId, context);
                        return PluginDispatcher.createPluginDispatcherReturnType(null);
                    } catch (final PaymentPluginApiException e) {
                        throw new PaymentApiException(ErrorCode.PAYMENT_DEL_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
                    } catch (final AccountApiException e) {
                        throw new PaymentApiException(e);
                    }
                }
            });
        } catch (final Exception e) {
            throw new PaymentApiException(e, ErrorCode.PAYMENT_INTERNAL_ERROR, MoreObjects.firstNonNull(e.getMessage(), ""));
        }
    }

    public void setDefaultPaymentMethod(final Account account, final UUID paymentMethodId, final Iterable<PluginProperty> properties, final CallContext callContext, final InternalCallContext context)
            throws PaymentApiException {
        try {
            new WithAccountLock<Void, PaymentApiException>(paymentConfig).processAccountWithLock(locker, account.getId(), new DispatcherCallback<PluginDispatcherReturnType<Void>, PaymentApiException>() {

                @Override
                public PluginDispatcherReturnType<Void> doOperation() throws PaymentApiException {
                    final PaymentMethodModelDao paymentMethodModel = getPaymentMethodById(paymentMethodId, false, context);

                    if (!paymentMethodModel.getAccountId().equals(account.getId())) {
                        throw new PaymentApiException(ErrorCode.PAYMENT_METHOD_DIFFERENT_ACCOUNT_ID, paymentMethodId);
                    }

                    try {
                        final PaymentPluginApi pluginApi = getPaymentProviderPlugin(paymentMethodId, false, context);

                        pluginApi.setDefaultPaymentMethod(account.getId(), paymentMethodId, properties, callContext);
                        accountInternalApi.updatePaymentMethod(account.getId(), paymentMethodId, context);
                        return PluginDispatcher.createPluginDispatcherReturnType(null);
                    } catch (final PaymentPluginApiException e) {
                        throw new PaymentApiException(ErrorCode.PAYMENT_UPD_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
                    } catch (final AccountApiException e) {
                        throw new PaymentApiException(e);
                    }
                }
            });
        } catch (final Exception e) {
            throw new PaymentApiException(e, ErrorCode.PAYMENT_INTERNAL_ERROR, MoreObjects.firstNonNull(e.getMessage(), ""));
        }
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
            throw new PaymentApiException(ErrorCode.PAYMENT_REFRESH_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
        }

        try {
            final PluginDispatcherReturnType<List<PaymentMethod>> result = new WithAccountLock<List<PaymentMethod>, PaymentApiException>(paymentConfig).processAccountWithLock(locker, account.getId(), new DispatcherCallback<PluginDispatcherReturnType<List<PaymentMethod>>, PaymentApiException>() {
                @Override
                public PluginDispatcherReturnType<List<PaymentMethod>> doOperation() throws PaymentApiException {

                    UUID defaultPaymentMethodId = null;

                    final List<PaymentMethodInfoPlugin> pluginPmsWithId = new ArrayList<PaymentMethodInfoPlugin>();
                    final List<PaymentMethodModelDao> finalPaymentMethods = new ArrayList<PaymentMethodModelDao>();
                    for (final PaymentMethodInfoPlugin cur : pluginPms) {
                        // If the kbPaymentId is NULL, the plugin does not know about it, so we create a new UUID
                        final UUID paymentMethodId = cur.getPaymentMethodId() != null ? cur.getPaymentMethodId() : UUIDs.randomUUID();
                        final String externalKey = cur.getExternalPaymentMethodId() != null ? cur.getExternalPaymentMethodId() : paymentMethodId.toString();
                        final PaymentMethod input = new DefaultPaymentMethod(paymentMethodId, externalKey, account.getId(), pluginName);
                        final PaymentMethodModelDao pmModel = new PaymentMethodModelDao(input.getId(), input.getExternalKey(), input.getCreatedDate(), input.getUpdatedDate(),
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

                    final List<PaymentMethodModelDao> refreshedPaymentMethods = paymentDao.refreshPaymentMethods(pluginName,
                                                                                                                 finalPaymentMethods,
                                                                                                                 context);

                    try {
                        pluginApi.resetPaymentMethods(account.getId(), pluginPmsWithId, properties, callContext);
                    } catch (final PaymentPluginApiException e) {
                        throw new PaymentApiException(ErrorCode.PAYMENT_REFRESH_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
                    }
                    try {
                        updateDefaultPaymentMethodIfNeeded(pluginName, account, defaultPaymentMethodId, context);
                    } catch (final AccountApiException e) {
                        throw new PaymentApiException(e);
                    }
                    final List<PaymentMethod> result = ImmutableList.<PaymentMethod>copyOf(Collections2.transform(refreshedPaymentMethods, new Function<PaymentMethodModelDao, PaymentMethod>() {
                        @Override
                        public PaymentMethod apply(final PaymentMethodModelDao input) {
                            return new DefaultPaymentMethod(input, null);
                        }
                    }));
                    return PluginDispatcher.createPluginDispatcherReturnType(result);
                }
            });
            return result.getReturnType();
        } catch (final Exception e) {
            throw new PaymentApiException(e, ErrorCode.PAYMENT_INTERNAL_ERROR, MoreObjects.firstNonNull(e.getMessage(), ""));
        }
    }

    private void updateDefaultPaymentMethodIfNeeded(final String pluginName, final Account account, @Nullable final UUID defaultPluginPaymentMethodId, final InternalCallContext context) throws PaymentApiException, AccountApiException {

        // Some gateways have the concept of default payment methods. Kill Bill has also its own default payment method
        // and is authoritative on this matter. However, if the default payment method is associated with a given plugin,
        // and if the default payment method in that plugin has changed, we will reflect this change in Kill Bill as well.

        boolean shouldUpdateDefaultPaymentMethod = true;
        if (account.getPaymentMethodId() != null) {
            final PaymentMethodModelDao currentDefaultPaymentMethod = getPaymentMethodById(account.getPaymentMethodId(), true, context);
            shouldUpdateDefaultPaymentMethod = pluginName.equals(currentDefaultPaymentMethod.getPluginName());
        }
        if (shouldUpdateDefaultPaymentMethod) {
            accountInternalApi.updatePaymentMethod(account.getId(), defaultPluginPaymentMethodId, context);
        }
    }
}
