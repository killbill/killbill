/*
 * Copyright 2017 Groupon, Inc
 * Copyright 2017 The Billing Project, LLC
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

import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.dao.PaymentMethodModelDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;

public class PaymentPluginServiceRegistration {

    private final PaymentDao paymentDao;
    private final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry;

    @Inject
    public PaymentPluginServiceRegistration(final PaymentDao paymentDao, final OSGIServiceRegistration<PaymentPluginApi> pluginRegistry) {
        this.paymentDao = paymentDao;
        this.pluginRegistry = pluginRegistry;
    }

    public Set<String> getAvailablePlugins() {
        return pluginRegistry.getAllServices();
    }

    public PaymentMethodModelDao getPaymentMethodById(final UUID paymentMethodId, final boolean includedDeleted, final InternalTenantContext context) throws PaymentApiException {
        final PaymentMethodModelDao paymentMethodModel;
        if (includedDeleted) {
            paymentMethodModel = paymentDao.getPaymentMethodIncludedDeleted(paymentMethodId, context);
        } else {
            paymentMethodModel = paymentDao.getPaymentMethod(paymentMethodId, context);
        }

        if (paymentMethodModel == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodId);
        }

        return paymentMethodModel;
    }

    public PaymentMethodModelDao getPaymentMethodByExternalKey(final String paymentMethodExternalKey, final boolean includedDeleted, final InternalTenantContext context) throws PaymentApiException {
        final PaymentMethodModelDao paymentMethodModel;
        if (includedDeleted) {
            paymentMethodModel = paymentDao.getPaymentMethodByExternalKeyIncludedDeleted(paymentMethodExternalKey, context);
        } else {
            paymentMethodModel = paymentDao.getPaymentMethodByExternalKey(paymentMethodExternalKey, context);
        }

        if (paymentMethodModel == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD, paymentMethodExternalKey);
        }
        return paymentMethodModel;
    }

    public PaymentPluginApi getPaymentPluginApi(final UUID paymentMethodId, final boolean includedDeleted, final InternalTenantContext context) throws PaymentApiException {
        final PaymentMethodModelDao paymentMethodModelDao = getPaymentMethodById(paymentMethodId, includedDeleted, context);
        return getPaymentPluginApi(paymentMethodModelDao.getPluginName());
    }

    public PaymentPluginApi getPaymentPluginApi(final String pluginName) throws PaymentApiException {
        final PaymentPluginApi pluginApi = pluginRegistry.getServiceForName(pluginName);
        if (pluginApi == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT_PLUGIN, pluginName);
        }
        return pluginApi;
    }
}
