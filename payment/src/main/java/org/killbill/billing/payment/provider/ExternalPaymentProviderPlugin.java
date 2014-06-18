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
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentMethodInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.DefaultPagination;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.inject.Inject;

/**
 * Special plugin used to record external payments (i.e. payments not issued by Killbill), such as checks.
 * <p/>
 * The implementation is very similar to the no-op plugin, which it extends. This can potentially be an issue
 * if Killbill is processing a lot of external payments as they are all kept in memory.
 */
public class ExternalPaymentProviderPlugin implements PaymentPluginApi {

    public static final String PLUGIN_NAME = "__EXTERNAL_PAYMENT__";

    private final Clock clock;

    @Inject
    public ExternalPaymentProviderPlugin(final Clock clock) {
        this.clock = clock;
    }

    @Override
    public PaymentTransactionInfoPlugin authorizePayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentPluginApiException {
        return new DefaultNoOpPaymentInfoPlugin(kbPaymentId, kbTransactionId, TransactionType.AUTHORIZE, amount, currency, clock.getUTCNow(), clock.getUTCNow(), PaymentPluginStatus.PROCESSED, null);
    }

    @Override
    public PaymentTransactionInfoPlugin capturePayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentPluginApiException {
        return new DefaultNoOpPaymentInfoPlugin(kbPaymentId, kbTransactionId, TransactionType.CAPTURE, amount, currency, clock.getUTCNow(), clock.getUTCNow(), PaymentPluginStatus.PROCESSED, null);
    }

    @Override
    public PaymentTransactionInfoPlugin processPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        return new DefaultNoOpPaymentInfoPlugin(kbPaymentId, kbTransactionId, TransactionType.PURCHASE, amount, currency, clock.getUTCNow(), clock.getUTCNow(), PaymentPluginStatus.PROCESSED, null);
    }

    @Override
    public PaymentTransactionInfoPlugin voidPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentPluginApiException {
        return new DefaultNoOpPaymentInfoPlugin(kbPaymentId, kbTransactionId, TransactionType.VOID, BigDecimal.ZERO, null, clock.getUTCNow(), clock.getUTCNow(), PaymentPluginStatus.PROCESSED, null);
    }

    @Override
    public PaymentTransactionInfoPlugin creditPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentPluginApiException {
        return new DefaultNoOpPaymentInfoPlugin(kbPaymentId, kbTransactionId, TransactionType.CREDIT, amount, currency, clock.getUTCNow(), clock.getUTCNow(), PaymentPluginStatus.PROCESSED, null);
    }

    @Override
    public List<PaymentTransactionInfoPlugin> getPaymentInfo(final UUID kbAccountId, final UUID kbPaymentId, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentPluginApiException {
        // STEPH broken...
        return ImmutableList.of();
    }

    @Override
    public Pagination<PaymentTransactionInfoPlugin> searchPayments(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentPluginApiException {
        return new DefaultPagination<PaymentTransactionInfoPlugin>(offset, limit, 0L, 0L, Iterators.<PaymentTransactionInfoPlugin>emptyIterator());
    }

    @Override
    public PaymentTransactionInfoPlugin processRefund(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final BigDecimal refundAmount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        return new DefaultNoOpPaymentInfoPlugin(kbPaymentId, kbTransactionId, TransactionType.REFUND, BigDecimal.ZERO, currency, clock.getUTCNow(), clock.getUTCNow(), PaymentPluginStatus.PROCESSED, null);
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
        return new DefaultPagination<PaymentMethodPlugin>(offset, limit, 0L, 0L, Iterators.<PaymentMethodPlugin>emptyIterator());
    }

    @Override
    public void resetPaymentMethods(final UUID kbAccountId, final List<PaymentMethodInfoPlugin> paymentMethods, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentPluginApiException {
    }

    @Override
    public HostedPaymentPageFormDescriptor buildFormDescriptor(final UUID kbAccountId, final Iterable<PluginProperty> customFields, final Iterable<PluginProperty> properties, final CallContext callContext) {
        return null;
    }

    @Override
    public GatewayNotification processNotification(final String notification, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentPluginApiException {
        return null;
    }
}
