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

import static com.ning.billing.payment.glue.PaymentModule.PLUGIN_EXECUTOR_NAMED;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;


import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.payment.api.DefaultPaymentMethod;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.dao.PaymentMethodModelDao;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.globallocker.GlobalLocker;

public class PaymentMethodProcessor extends ProcessorBase {

    private final PaymentDao paymentDao;
    
    @Inject
    public PaymentMethodProcessor(final PaymentProviderPluginRegistry pluginRegistry,
            final AccountUserApi accountUserApi,
            final Bus eventBus,
            final PaymentDao paymentDao,
            final GlobalLocker locker,
            @Named(PLUGIN_EXECUTOR_NAMED)  final ExecutorService executor) {
        super(pluginRegistry, accountUserApi, eventBus, locker, executor);
        this.paymentDao = paymentDao;
    }
    
    public Set<String> getAvailablePlugins() {
        return pluginRegistry.getRegisteredPluginNames();
    }


    public String initializeAccountPlugin(String pluginName, Account account) throws PaymentApiException {
        PaymentPluginApi pluginApi = null;
        try {
            pluginApi = getPaymentProviderPlugin(account.getExternalKey());
            return pluginApi.createPaymentProviderAccount(account);
        } catch (PaymentPluginApiException e) {
            throw new PaymentApiException(ErrorCode.PAYMENT_PLUGIN_ACCOUNT_INIT,
                    account.getId(), pluginApi != null ? pluginApi.getName() : null, e.getErrorMessage());
        }
    }


    public UUID addPaymentMethod(String pluginName, Account account,
            boolean setDefault, final PaymentMethodPlugin paymentMethodProps, CallContext context) 
    throws PaymentApiException {
        
        PaymentMethod pm = null;
        PaymentPluginApi pluginApi = null;
        try {
            pluginApi = getPaymentProviderPlugin(account.getExternalKey());
            pm = new DefaultPaymentMethod(account.getId(), pluginName, paymentMethodProps);
            String externalId = pluginApi.addPaymentMethod(account.getExternalKey(), paymentMethodProps, setDefault);
            PaymentMethodModelDao pmModel = new PaymentMethodModelDao(pm.getId(), pm.getAccountId(), pm.getPluginName(), pm.isActive(), externalId);
            paymentDao.insertPaymentMethod(pmModel, context);
            
            // STEPH setDefault
        } catch (PaymentPluginApiException e) {
            // STEPH all errors should also take a pluginName
            throw new PaymentApiException(ErrorCode.PAYMENT_ADD_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
        }
        return pm.getId();
    }


    public List<PaymentMethod> refreshPaymentMethods(String pluginName,
            Account account, final CallContext context)
            throws PaymentApiException {

        List<PaymentMethod> result = new LinkedList<PaymentMethod>();
        PaymentPluginApi pluginApi = null;
        try {
            pluginApi = getPaymentProviderPlugin(account.getExternalKey());
            
            List<PaymentMethodPlugin> pluginPms = pluginApi.getPaymentMethodDetails(account.getExternalKey());
            for (PaymentMethodPlugin cur : pluginPms) {
                PaymentMethod input = new DefaultPaymentMethod(account.getId(), pluginName, cur);
                PaymentMethodModelDao pmModel = new PaymentMethodModelDao(input.getId(), input.getAccountId(), input.getPluginName(), input.isActive(), input.getPluginDetail().getExternalPaymentMethodId());
                // STEPH we should insert iwithin one batch
                paymentDao.insertPaymentMethod(pmModel, context);
                result.add(input);
            }
        } catch (PaymentPluginApiException e) {
            // STEPH all errors should also take a pluginName
            throw new PaymentApiException(ErrorCode.PAYMENT_REFRESH_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
        }
        return result;

    }

    public List<PaymentMethod> getPaymentMethods(Account account, boolean withPluginDetail) throws PaymentApiException {

        List<PaymentMethodModelDao> paymentMethodModels = paymentDao.getPaymentMethods(account.getId());
        if (paymentMethodModels.size() == 0) {
            return Collections.emptyList();
        }
        return getPaymentMethodInternal(paymentMethodModels, account.getId(), account.getExternalKey(), withPluginDetail);
    }

    public PaymentMethod getPaymentMethod(Account account, UUID paymentMethodId, boolean withPluginDetail) 
    throws PaymentApiException {
        PaymentMethodModelDao paymentMethodModel = paymentDao.getPaymentMethod(paymentMethodId);
        if (paymentMethodModel == null) {
            return null;
        }
        List<PaymentMethod> result =  getPaymentMethodInternal(Collections.singletonList(paymentMethodModel), account.getId(), account.getExternalKey(), withPluginDetail);
        return (result.size() == 0) ? null : result.get(0); 
    }


    private List<PaymentMethod> getPaymentMethodInternal(List<PaymentMethodModelDao> paymentMethodModels, UUID accountId, String accountKey, boolean withPluginDetail)
    throws PaymentApiException {


        List<PaymentMethod> result = new ArrayList<PaymentMethod>(paymentMethodModels.size());

        PaymentPluginApi pluginApi = null;
        try {
            List<PaymentMethodPlugin> pluginDetails = null;

            if (withPluginDetail) {
                pluginApi = getPaymentProviderPlugin(accountKey);
                pluginDetails = pluginApi.getPaymentMethodDetails(accountKey); 
            }
            
            for (PaymentMethodModelDao cur : paymentMethodModels) {
                PaymentMethod pm = new DefaultPaymentMethod(cur, getPaymentMethodDetail(pluginDetails, cur.getExternalId()));
                result.add(pm);
            }
        } catch (PaymentPluginApiException e) {
            throw new PaymentApiException(ErrorCode.PAYMENT_GET_PAYMENT_METHODS, accountId, e.getErrorMessage());
        }
        return result;
    }
    
    
    private PaymentMethodPlugin getPaymentMethodDetail(List<PaymentMethodPlugin> pluginDetails, String externalId) {
        
        if (pluginDetails == null) {
            return null;
        }
        for (PaymentMethodPlugin cur : pluginDetails) {
            if (cur.getExternalPaymentMethodId().equals(externalId)) {
                return cur;
            }
        }
        return null;
    }




    public void updatePaymentMethod(Account account, UUID paymentMethodId,
            PaymentMethodPlugin paymentMethodProps) 
    throws PaymentApiException {
        
        PaymentMethodModelDao paymentMethodModel = paymentDao.getPaymentMethod(paymentMethodId);
        if (paymentMethodModel == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, account.getId(), paymentMethodId);
        }

        PaymentPluginApi pluginApi = null;
        try {
            pluginApi = getPaymentProviderPlugin(account.getExternalKey());
            
            pluginApi.updatePaymentMethod(account.getExternalKey(), paymentMethodModel.getExternalId(), paymentMethodProps);
        } catch (PaymentPluginApiException e) {
            throw new PaymentApiException(ErrorCode.PAYMENT_UPD_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
        }
    }


    public void deletedPaymentMethod(Account account, UUID paymentMethodId) 
    throws PaymentApiException {

        PaymentMethodModelDao paymentMethodModel = paymentDao.getPaymentMethod(paymentMethodId);
        if (paymentMethodModel == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, account.getId(), paymentMethodId);
        }

        PaymentPluginApi pluginApi = null;
        try {
            pluginApi = getPaymentProviderPlugin(account.getExternalKey());

            pluginApi.deletePaymentMethod(account.getExternalKey(), paymentMethodModel.getExternalId());
        } catch (PaymentPluginApiException e) {
            throw new PaymentApiException(ErrorCode.PAYMENT_DEL_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
        }
        
    }

    public void setDefaultPaymentMethod(Account account, UUID paymentMethodId) 
    throws PaymentApiException {
        
        PaymentMethodModelDao paymentMethodModel = paymentDao.getPaymentMethod(paymentMethodId);
        if (paymentMethodModel == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, account.getId(), paymentMethodId);
        }

        PaymentPluginApi pluginApi = null;
        try {
            pluginApi = getPaymentProviderPlugin(account.getExternalKey());
            
            pluginApi.setDefaultPaymentMethod(account.getExternalKey(), paymentMethodModel.getExternalId());
            
        } catch (PaymentPluginApiException e) {
            throw new PaymentApiException(ErrorCode.PAYMENT_UPD_PAYMENT_METHOD, account.getId(), e.getErrorMessage());
        }
    }
    
}
