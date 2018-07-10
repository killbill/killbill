/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.payment.provider;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.GatewayNotification;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.payment.plugin.api.PaymentMethodInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.util.entity.DefaultPagination;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

/**
 * Special plugin used to record external payments (i.e. payments not issued by Killbill), such as checks.
 */
public class ExternalPaymentProviderPlugin implements PaymentPluginApi {

    public static final String PLUGIN_NAME = "__EXTERNAL_PAYMENT__";

    private final Clock clock;
    private final PaymentConfig paymentConfig;

    @Inject
    public ExternalPaymentProviderPlugin(final Clock clock, final PaymentConfig paymentConfig) {
        this.clock = clock;
        this.paymentConfig = paymentConfig;
    }

    @Override
    public PaymentTransactionInfoPlugin authorizePayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentPluginApiException {
        return new DefaultNoOpPaymentInfoPlugin(kbPaymentId, kbTransactionId, TransactionType.AUTHORIZE, amount, currency, callContext.getCreatedDate(), callContext.getCreatedDate(), getPaymentPluginStatus(properties), null, null);
    }

    @Override
    public PaymentTransactionInfoPlugin capturePayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentPluginApiException {
        return new DefaultNoOpPaymentInfoPlugin(kbPaymentId, kbTransactionId, TransactionType.CAPTURE, amount, currency, callContext.getCreatedDate(), callContext.getCreatedDate(), getPaymentPluginStatus(properties), null, null);
    }

    @Override
    public PaymentTransactionInfoPlugin purchasePayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        return new DefaultNoOpPaymentInfoPlugin(kbPaymentId, kbTransactionId, TransactionType.PURCHASE, amount, currency, context.getCreatedDate(), context.getCreatedDate(), getPaymentPluginStatus(properties), null, null);
    }

    @Override
    public PaymentTransactionInfoPlugin voidPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentPluginApiException {
        return new DefaultNoOpPaymentInfoPlugin(kbPaymentId, kbTransactionId, TransactionType.VOID, BigDecimal.ZERO, null, callContext.getCreatedDate(), callContext.getCreatedDate(), getPaymentPluginStatus(properties), null, null);
    }

    @Override
    public PaymentTransactionInfoPlugin creditPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentPluginApiException {
        return new DefaultNoOpPaymentInfoPlugin(kbPaymentId, kbTransactionId, TransactionType.CREDIT, amount, currency, callContext.getCreatedDate(), callContext.getCreatedDate(), getPaymentPluginStatus(properties), null, null);
    }

    @Override
    public List<PaymentTransactionInfoPlugin> getPaymentInfo(final UUID kbAccountId, final UUID kbPaymentId, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentPluginApiException {
        return ImmutableList.of();
    }

    @Override
    public Pagination<PaymentTransactionInfoPlugin> searchPayments(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentPluginApiException {
        return new DefaultPagination<PaymentTransactionInfoPlugin>(offset, limit, 0L, 0L, ImmutableSet.<PaymentTransactionInfoPlugin>of().iterator());
    }

    @Override
    public PaymentTransactionInfoPlugin refundPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal refundAmount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        return new DefaultNoOpPaymentInfoPlugin(kbPaymentId, kbTransactionId, TransactionType.REFUND, refundAmount, currency, context.getCreatedDate(), context.getCreatedDate(), getPaymentPluginStatus(properties), null, null);
    }

    @Override
    public void addPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final PaymentMethodPlugin paymentMethodProps, final boolean setDefault, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
    }

    @Override
    public void deletePaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
    }

    @Override
    public PaymentMethodPlugin getPaymentMethodDetail(final UUID kbAccountId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentPluginApiException {
        return new DefaultNoOpPaymentMethodPlugin(kbPaymentMethodId, "unknown", false, Collections.<PluginProperty>emptyList());
    }

    @Override
    public void setDefaultPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
    }

    @Override
    public List<PaymentMethodInfoPlugin> getPaymentMethods(final UUID kbAccountId, final boolean refreshFromGateway, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        return ImmutableList.<PaymentMethodInfoPlugin>of();
    }

    @Override
    public Pagination<PaymentMethodPlugin> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentPluginApiException {
        return new DefaultPagination<PaymentMethodPlugin>(offset, limit, 0L, 0L, ImmutableSet.<PaymentMethodPlugin>of().iterator());
    }

    @Override
    public void resetPaymentMethods(final UUID kbAccountId, final List<PaymentMethodInfoPlugin> paymentMethods, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentPluginApiException {
    }

    @Override
    public HostedPaymentPageFormDescriptor buildFormDescriptor(final UUID kbAccountId, final Iterable<PluginProperty> customFields, final Iterable<PluginProperty> properties, final CallContext callContext) {
        return new DefaultNoOpHostedPaymentPageFormDescriptor(kbAccountId);
    }

    @Override
    public GatewayNotification processNotification(final String notification, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentPluginApiException {
        return new DefaultNoOpGatewayNotification();
    }

    private PaymentPluginStatus getPaymentPluginStatus(final Iterable<PluginProperty> properties) throws PaymentPluginApiException {
        if (shouldPaymentFailWithError(properties)) {
            return PaymentPluginStatus.ERROR;
        } else if (shouldPaymentFailWithException(properties)) {
            throw new PaymentPluginApiException("Failed to process payment", "Triggered from killbill.external.payment.fail.cancellation");
        } else if (shouldPaymentFailWithCancellation(properties)) {
            return PaymentPluginStatus.CANCELED;
        } else if (shouldPaymentTimeout(properties)) {
            try {
                Thread.sleep(paymentConfig.getPaymentPluginTimeout().getMillis() + 1000);
            } catch (final InterruptedException ignored) {
            }
        }
        return PaymentPluginStatus.PROCESSED;
    }

    private boolean shouldPaymentFailWithError(final Iterable<PluginProperty> properties) {
        return isPropertySet(properties, "killbill.external.payment.fail.error");
    }

    private boolean shouldPaymentFailWithException(final Iterable<PluginProperty> properties) {
        return isPropertySet(properties, "killbill.external.payment.fail.exception");
    }

    private boolean shouldPaymentFailWithCancellation(final Iterable<PluginProperty> properties) {
        return isPropertySet(properties, "killbill.external.payment.fail.cancellation");
    }

    private boolean shouldPaymentTimeout(final Iterable<PluginProperty> properties) {
        return isPropertySet(properties, "killbill.external.payment.fail.timeout");
    }

    private boolean isPropertySet(final Iterable<PluginProperty> properties, final String targetProperty) {
        return Iterables.any(properties, new Predicate<PluginProperty>() {
            @Override
            public boolean apply(final PluginProperty input) {
                return input.getKey().equals(targetProperty) && input.getValue().equals("true");
            }
        });
    }

}
