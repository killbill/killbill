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

package org.killbill.billing.osgi.bundles.jruby;

import java.math.BigDecimal;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;

import org.jruby.Ruby;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageDescriptorFields;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageNotification;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.osgi.api.OSGIPluginProperties;
import org.killbill.billing.osgi.api.config.PluginRubyConfig;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.plugin.api.PaymentInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentMethodInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.RefundInfoPlugin;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;

public class JRubyPaymentPlugin extends JRubyPlugin implements PaymentPluginApi {

    private volatile ServiceRegistration paymentInfoPluginRegistration;

    public JRubyPaymentPlugin(final PluginRubyConfig config, final BundleContext bundleContext, final LogService logger) {
        super(config, bundleContext, logger);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void startPlugin(final BundleContext context) {
        super.startPlugin(context);

        final Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put("name", pluginMainClass);
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, pluginGemName);
        paymentInfoPluginRegistration = context.registerService(PaymentPluginApi.class.getName(), this, props);
    }

    @Override
    public void stopPlugin(final BundleContext context) {
        if (paymentInfoPluginRegistration != null) {
            paymentInfoPluginRegistration.unregister();
        }
        super.stopPlugin(context);
    }

    // STEPH_DP
    @Override
    public PaymentInfoPlugin authorizePayment(final UUID uuid, final UUID uuid2, final UUID uuid3, final BigDecimal bigDecimal, final Currency currency, final CallContext callContext) throws PaymentPluginApiException {
        return null;
    }

    @Override
    public PaymentInfoPlugin capturePayment(final UUID uuid, final UUID uuid2, final UUID uuid3, final BigDecimal bigDecimal, final Currency currency, final CallContext callContext) throws PaymentPluginApiException {
        return null;
    }

    @Override
    public PaymentInfoPlugin processPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final CallContext context) throws PaymentPluginApiException {

        return callWithRuntimeAndChecking(new PluginCallback(VALIDATION_PLUGIN_TYPE.PAYMENT) {
            @Override
            public PaymentInfoPlugin doCall(final Ruby runtime) throws PaymentPluginApiException {
                return ((PaymentPluginApi) pluginInstance).processPayment(kbAccountId, kbPaymentId, kbPaymentMethodId, amount, currency, context);
            }
        });
    }

    @Override
    public PaymentInfoPlugin voidPayment(final UUID uuid, final UUID uuid2, final UUID uuid3, final CallContext callContext) throws PaymentPluginApiException {
        return null;
    }

    @Override
    public PaymentInfoPlugin getPaymentInfo(final UUID kbAccountId, final UUID kbPaymentId, final TenantContext context) throws PaymentPluginApiException {

        return callWithRuntimeAndChecking(new PluginCallback(VALIDATION_PLUGIN_TYPE.PAYMENT) {
            @Override
            public PaymentInfoPlugin doCall(final Ruby runtime) throws PaymentPluginApiException {
                return ((PaymentPluginApi) pluginInstance).getPaymentInfo(kbAccountId, kbPaymentId, context);
            }
        });
    }

    @Override
    public Pagination<PaymentInfoPlugin> searchPayments(final String searchKey, final Long offset, final Long limit, final TenantContext tenantContext) throws PaymentPluginApiException {
        return callWithRuntimeAndChecking(new PluginCallback(VALIDATION_PLUGIN_TYPE.PAYMENT) {
            @Override
            public Pagination<PaymentInfoPlugin> doCall(final Ruby runtime) throws PaymentPluginApiException {
                return ((PaymentPluginApi) pluginInstance).searchPayments(searchKey, offset, limit, tenantContext);
            }
        });
    }

    @Override
    public RefundInfoPlugin processRefund(final UUID kbAccountId, final UUID kbPaymentId, final BigDecimal refundAmount, final Currency currency, final CallContext context) throws PaymentPluginApiException {

        return callWithRuntimeAndChecking(new PluginCallback(VALIDATION_PLUGIN_TYPE.PAYMENT) {
            @Override
            public RefundInfoPlugin doCall(final Ruby runtime) throws PaymentPluginApiException {
                return ((PaymentPluginApi) pluginInstance).processRefund(kbAccountId, kbPaymentId, refundAmount, currency, context);
            }
        });

    }

    @Override
    public List<RefundInfoPlugin> getRefundInfo(final UUID kbAccountId, final UUID kbPaymentId, final TenantContext context) throws PaymentPluginApiException {
        return callWithRuntimeAndChecking(new PluginCallback(VALIDATION_PLUGIN_TYPE.PAYMENT) {
            @Override
            public List<RefundInfoPlugin> doCall(final Ruby runtime) throws PaymentPluginApiException {
                return ((PaymentPluginApi) pluginInstance).getRefundInfo(kbAccountId, kbPaymentId, context);
            }
        });
    }

    @Override
    public Pagination<RefundInfoPlugin> searchRefunds(final String searchKey, final Long offset, final Long limit, final TenantContext tenantContext) throws PaymentPluginApiException {
        return callWithRuntimeAndChecking(new PluginCallback(VALIDATION_PLUGIN_TYPE.PAYMENT) {
            @Override
            public Pagination<RefundInfoPlugin> doCall(final Ruby runtime) throws PaymentPluginApiException {
                return ((PaymentPluginApi) pluginInstance).searchRefunds(searchKey, offset, limit, tenantContext);
            }
        });
    }

    @Override
    public void addPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final PaymentMethodPlugin paymentMethodProps, final boolean setDefault, final CallContext context) throws PaymentPluginApiException {

        callWithRuntimeAndChecking(new PluginCallback(VALIDATION_PLUGIN_TYPE.PAYMENT) {
            @Override
            public Void doCall(final Ruby runtime) throws PaymentPluginApiException {
                ((PaymentPluginApi) pluginInstance).addPaymentMethod(kbAccountId, kbPaymentMethodId, paymentMethodProps, Boolean.valueOf(setDefault), context);
                return null;
            }
        });
    }

    @Override
    public void deletePaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final CallContext context) throws PaymentPluginApiException {

        callWithRuntimeAndChecking(new PluginCallback(VALIDATION_PLUGIN_TYPE.PAYMENT) {
            @Override
            public Void doCall(final Ruby runtime) throws PaymentPluginApiException {
                ((PaymentPluginApi) pluginInstance).deletePaymentMethod(kbAccountId, kbPaymentMethodId, context);
                return null;
            }
        });
    }

    @Override
    public PaymentMethodPlugin getPaymentMethodDetail(final UUID kbAccountId, final UUID kbPaymentMethodId, final TenantContext context) throws PaymentPluginApiException {

        return callWithRuntimeAndChecking(new PluginCallback(VALIDATION_PLUGIN_TYPE.PAYMENT) {
            @Override
            public PaymentMethodPlugin doCall(final Ruby runtime) throws PaymentPluginApiException {
                return ((PaymentPluginApi) pluginInstance).getPaymentMethodDetail(kbAccountId, kbPaymentMethodId, context);
            }
        });
    }

    @Override
    public void setDefaultPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final CallContext context) throws PaymentPluginApiException {

        callWithRuntimeAndChecking(new PluginCallback(VALIDATION_PLUGIN_TYPE.PAYMENT) {
            @Override
            public Void doCall(final Ruby runtime) throws PaymentPluginApiException {
                ((PaymentPluginApi) pluginInstance).setDefaultPaymentMethod(kbAccountId, kbPaymentMethodId, context);
                return null;
            }
        });
    }

    @Override
    public List<PaymentMethodInfoPlugin> getPaymentMethods(final UUID kbAccountId, final boolean refreshFromGateway, final CallContext context) throws PaymentPluginApiException {
        return callWithRuntimeAndChecking(new PluginCallback(VALIDATION_PLUGIN_TYPE.PAYMENT) {
            @Override
            public List<PaymentMethodInfoPlugin> doCall(final Ruby runtime) throws PaymentPluginApiException {
                return ((PaymentPluginApi) pluginInstance).getPaymentMethods(kbAccountId, Boolean.valueOf(refreshFromGateway), context);
            }
        });
    }

    @Override
    public Pagination<PaymentMethodPlugin> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final TenantContext tenantContext) throws PaymentPluginApiException {
        return callWithRuntimeAndChecking(new PluginCallback(VALIDATION_PLUGIN_TYPE.PAYMENT) {
            @Override
            public Pagination<PaymentMethodPlugin> doCall(final Ruby runtime) throws PaymentPluginApiException {
                return ((PaymentPluginApi) pluginInstance).searchPaymentMethods(searchKey, offset, limit, tenantContext);
            }
        });
    }

    @Override
    public void resetPaymentMethods(final UUID kbAccountId, final List<PaymentMethodInfoPlugin> paymentMethods) throws PaymentPluginApiException {

        callWithRuntimeAndChecking(new PluginCallback(VALIDATION_PLUGIN_TYPE.PAYMENT) {
            @Override
            public Void doCall(final Ruby runtime) throws PaymentPluginApiException {
                ((PaymentPluginApi) pluginInstance).resetPaymentMethods(kbAccountId, paymentMethods);
                return null;
            }
        });
    }

    @Override
    public HostedPaymentPageFormDescriptor buildFormDescriptor(final UUID uuid, final HostedPaymentPageDescriptorFields hostedPaymentPageDescriptorFields, final TenantContext tenantContext) {
        return null;
    }

    @Override
    public HostedPaymentPageNotification processNotification(final String s, final TenantContext tenantContext) throws PaymentPluginApiException {
        return null;
    }
}
