/*
 * Copyright 2010-2011 Ning, Inc.
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.payment.api.DefaultPaymentMethod;
import com.ning.billing.payment.api.DefaultPaymentMethodPlugin;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.api.PaymentMethodPlugin.PaymentMethodKVInfo;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.dao.PaymentMethodModelDao;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.provider.DefaultNoOpPaymentMethodPlugin;
import com.ning.billing.payment.provider.ExternalPaymentProviderPlugin;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.tag.TagInternalApi;
import com.ning.billing.util.svcsapi.bus.InternalBus;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import static com.ning.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;

public class PaymentMethodProcessor extends ProcessorBase {

    private static final Logger log = LoggerFactory.getLogger(PaymentMethodProcessor.class);

    @Inject
    public PaymentMethodProcessor(final PaymentProviderPluginRegistry pluginRegistry,
                                  final AccountInternalApi accountInternalApi,
                                  final InternalBus eventBus,
                                  final PaymentDao paymentDao,
                                  final TagInternalApi tagUserApi,
                                  final GlobalLocker locker,
                                  @Named(PLUGIN_EXECUTOR_NAMED) final ExecutorService executor) {
        super(pluginRegistry, accountInternalApi, eventBus, paymentDao, tagUserApi, locker, executor);
    }

    public Set<String> getAvailablePlugins() {
        return pluginRegistry.getRegisteredPluginNames();
    }

    public String initializeAccountPlugin(final String pluginName, final Account account, final InternalCallContext context) throws PaymentApiException {

        return new WithAccountLock<String>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<String>() {

            @Override
            public String doOperation() throws PaymentApiException {
                PaymentPluginApi pluginApi = null;
                try {
                    // STEPH do we want to really have a default or fail?? probably fail
                    pluginApi = pluginRegistry.getPlugin(pluginName);
                    return pluginApi.createPaymentProviderAccount(account, context.toCallContext());
                } catch (PaymentPluginApiException e) {
                    throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_ACCOUNT_INIT,
                                                  account.getId(), pluginApi != null ? pluginApi.getName() : null, e.getErrorMessage());
                }
            }
        });
    }

    public UUID addPaymentMethod(final String pluginName, final Account account,
                                 final boolean setDefault, final PaymentMethodPlugin paymentMethodProps, final InternalCallContext context)
            throws PaymentApiException {

        return new WithAccountLock<UUID>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<UUID>() {

            @Override
            public UUID doOperation() throws PaymentApiException {
                PaymentMethod pm = null;
                PaymentPluginApi pluginApi = null;
                try {
                    pluginApi = pluginRegistry.getPlugin(pluginName);
                    pm = new DefaultPaymentMethod(account.getId(), pluginName, paymentMethodProps);
                    final String externalId = pluginApi.addPaymentMethod(account.getExternalKey(), paymentMethodProps, setDefault, context.toCallContext());
                    final PaymentMethodModelDao pmModel = new PaymentMethodModelDao(pm.getId(), pm.getCreatedDate(), pm.getUpdatedDate(),
                                                                                    pm.getAccountId(), pm.getPluginName(), pm.isActive(), externalId);
                    paymentDao.insertPaymentMethod(pmModel, context);

                    if (setDefault) {
                        accountInternalApi.updatePaymentMethod(account.getId(), pm.getId(), context);
                    }
                } catch (PaymentPluginApiException e) {
                    // STEPH all errors should also take a pluginName
                    throw new PaymentApiException(ErrorCode.PAYMENT_ADD_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
                } catch (AccountApiException e) {
                    throw new PaymentApiException(e);
                }
                return pm.getId();
            }
        });
    }

    public List<PaymentMethod> refreshPaymentMethods(final String pluginName, final Account account, final InternalCallContext context)
            throws PaymentApiException {
        // Don't hold the account lock while fetching the payment methods
        final PaymentPluginApi pluginApi = pluginRegistry.getPlugin(pluginName);
        final List<PaymentMethodPlugin> pluginPms;
        try {
            pluginPms = pluginApi.getPaymentMethodDetails(account.getExternalKey(), context.toCallContext());
            // The method should never return null by convention, but let's not trust the plugin...
            if (pluginPms == null) {
                return ImmutableList.<PaymentMethod>of();
            }
        } catch (PaymentPluginApiException e) {
            // STEPH all errors should also take a pluginName
            throw new PaymentApiException(ErrorCode.PAYMENT_REFRESH_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
        }

        return new WithAccountLock<List<PaymentMethod>>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<List<PaymentMethod>>() {

            @Override
            public List<PaymentMethod> doOperation() throws PaymentApiException {
                final List<PaymentMethodModelDao> finalPaymentMethods = new ArrayList<PaymentMethodModelDao>();
                for (final PaymentMethodPlugin cur : pluginPms) {
                    final PaymentMethod input = new DefaultPaymentMethod(account.getId(), pluginName, cur);
                    final PaymentMethodModelDao pmModel = new PaymentMethodModelDao(input.getId(), input.getCreatedDate(), input.getUpdatedDate(),
                                                                                    input.getAccountId(), input.getPluginName(), input.isActive(),
                                                                                    input.getPluginDetail().getExternalPaymentMethodId());
                    finalPaymentMethods.add(pmModel);
                }

                final List<PaymentMethodModelDao> refreshedPaymentMethods = paymentDao.refreshPaymentMethods(account.getId(),
                                                                                                             finalPaymentMethods,
                                                                                                             context);
                return ImmutableList.<PaymentMethod>copyOf(Collections2.transform(refreshedPaymentMethods, new Function<PaymentMethodModelDao, PaymentMethod>() {
                    @Override
                    public PaymentMethod apply(final PaymentMethodModelDao input) {
                        return new DefaultPaymentMethod(input, getPaymentMethodDetail(pluginPms, input.getExternalId()));
                    }
                }));
            }
        });
    }

    public List<PaymentMethod> getPaymentMethods(final Account account, final boolean withPluginDetail, final InternalTenantContext context) throws PaymentApiException {

        final List<PaymentMethodModelDao> paymentMethodModels = paymentDao.getPaymentMethods(account.getId(), context);
        if (paymentMethodModels.size() == 0) {
            return Collections.emptyList();
        }
        return getPaymentMethodInternal(paymentMethodModels, account.getId(), account.getExternalKey(), withPluginDetail, context);
    }

    public PaymentMethod getPaymentMethodById(final UUID paymentMethodId, final InternalTenantContext context)
            throws PaymentApiException {
        final PaymentMethodModelDao paymentMethodModel = paymentDao.getPaymentMethod(paymentMethodId, context);
        if (paymentMethodModel == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
        }
        return new DefaultPaymentMethod(paymentMethodModel, null);
    }

    public PaymentMethod getPaymentMethod(final Account account, final UUID paymentMethodId, final boolean withPluginDetail, final InternalTenantContext context)
            throws PaymentApiException {
        final PaymentMethodModelDao paymentMethodModel = paymentDao.getPaymentMethod(paymentMethodId, context);
        if (paymentMethodModel == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
        }
        final List<PaymentMethod> result = getPaymentMethodInternal(Collections.singletonList(paymentMethodModel), account.getId(),
                                                                    account.getExternalKey(), withPluginDetail, context);
        return (result.size() == 0) ? null : result.get(0);
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

        return (ExternalPaymentProviderPlugin) pluginRegistry.getPlugin(ExternalPaymentProviderPlugin.PLUGIN_NAME);
    }

    private List<PaymentMethod> getPaymentMethodInternal(final List<PaymentMethodModelDao> paymentMethodModels, final UUID accountId,
                                                         final String accountKey, final boolean withPluginDetail, final InternalTenantContext context)
            throws PaymentApiException {

        final List<PaymentMethod> result = new ArrayList<PaymentMethod>(paymentMethodModels.size());
        PaymentPluginApi pluginApi = null;
        try {
            List<PaymentMethodPlugin> pluginDetails = null;
            for (final PaymentMethodModelDao cur : paymentMethodModels) {

                if (withPluginDetail) {
                    pluginApi = pluginRegistry.getPlugin(cur.getPluginName());
                    pluginDetails = pluginApi.getPaymentMethodDetails(accountKey, context.toTenantContext());
                }

                final PaymentMethod pm = new DefaultPaymentMethod(cur, getPaymentMethodDetail(pluginDetails, cur.getExternalId()));
                result.add(pm);
            }
        } catch (PaymentPluginApiException e) {
            throw new PaymentApiException(ErrorCode.PAYMENT_GET_PAYMENT_METHODS, accountId, e.getErrorMessage());
        }
        return result;
    }

    private PaymentMethodPlugin getPaymentMethodDetail(final List<PaymentMethodPlugin> pluginDetails, final String externalId) {
        if (pluginDetails == null) {
            return null;
        }
        for (final PaymentMethodPlugin cur : pluginDetails) {
            if (cur.getExternalPaymentMethodId().equals(externalId)) {
                return cur;
            }
        }
        return null;
    }

    public void updatePaymentMethod(final Account account, final UUID paymentMethodId,
                                    final PaymentMethodPlugin paymentMethodProps, final InternalCallContext context)
            throws PaymentApiException {

        new WithAccountLock<Void>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<Void>() {

            @Override
            public Void doOperation() throws PaymentApiException {
                final PaymentMethodModelDao paymentMethodModel = paymentDao.getPaymentMethod(paymentMethodId, context);
                if (paymentMethodModel == null) {
                    throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
                }

                try {
                    final PaymentMethodPlugin inputWithId = new DefaultPaymentMethodPlugin(paymentMethodProps, paymentMethodModel.getExternalId());
                    final PaymentPluginApi pluginApi = getPluginApi(paymentMethodId, account.getId(), context);
                    pluginApi.updatePaymentMethod(account.getExternalKey(), inputWithId, context.toCallContext());
                    return null;
                } catch (PaymentPluginApiException e) {
                    throw new PaymentApiException(ErrorCode.PAYMENT_UPD_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
                }
            }
        });
    }

    public void deletedPaymentMethod(final Account account, final UUID paymentMethodId,
                                     final boolean deleteDefaultPaymentMethodWithAutoPayOff, final InternalCallContext context)
            throws PaymentApiException {

        new WithAccountLock<Void>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<Void>() {

            @Override
            public Void doOperation() throws PaymentApiException {
                final PaymentMethodModelDao paymentMethodModel = paymentDao.getPaymentMethod(paymentMethodId, context);
                if (paymentMethodModel == null) {
                    throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
                }

                try {
                    if (account.getPaymentMethodId().equals(paymentMethodId)) {
                        if (!deleteDefaultPaymentMethodWithAutoPayOff) {
                            throw new PaymentApiException(ErrorCode.PAYMENT_DEL_DEFAULT_PAYMENT_METHOD, account.getId());
                        } else {
                            final boolean isAccountAutoPayOff = isAccountAutoPayOff(account.getId(), context);
                            if (!isAccountAutoPayOff) {
                                log.info("Setting account {} to AUTO_PAY_OFF because of default payment method deletion");
                                setAccountAutoPayOff(account.getId(), context);
                            }
                            accountInternalApi.removePaymentMethod(account.getId(), context);
                        }
                    }
                    final PaymentPluginApi pluginApi = getPluginApi(paymentMethodId, account.getId(), context);
                    pluginApi.deletePaymentMethod(account.getExternalKey(), paymentMethodModel.getExternalId(), context.toCallContext());
                    paymentDao.deletedPaymentMethod(paymentMethodId, context);
                    return null;
                } catch (PaymentPluginApiException e) {
                    throw new PaymentApiException(ErrorCode.PAYMENT_DEL_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
                } catch (AccountApiException e) {
                    throw new PaymentApiException(e);
                }
            }
        });
    }

    public void setDefaultPaymentMethod(final Account account, final UUID paymentMethodId, final InternalCallContext context)
            throws PaymentApiException {

        new WithAccountLock<Void>().processAccountWithLock(locker, account.getExternalKey(), new WithAccountLockCallback<Void>() {

            @Override
            public Void doOperation() throws PaymentApiException {
                final PaymentMethodModelDao paymentMethodModel = paymentDao.getPaymentMethod(paymentMethodId, context);
                if (paymentMethodModel == null) {
                    throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
                }

                try {
                    final PaymentPluginApi pluginApi = getPluginApi(paymentMethodId, account.getId(), context);

                    pluginApi.setDefaultPaymentMethod(account.getExternalKey(), paymentMethodModel.getExternalId(), context.toCallContext());
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

    private PaymentPluginApi getPluginApi(final UUID paymentMethodId, final UUID accountId, final InternalTenantContext context)
            throws PaymentApiException {
        final PaymentMethodModelDao paymentMethod = paymentDao.getPaymentMethod(paymentMethodId, context);
        if (paymentMethod == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
        }
        return pluginRegistry.getPlugin(paymentMethod.getPluginName());
    }
}
