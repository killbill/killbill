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

package com.ning.billing.payment.provider;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.clock.Clock;
import com.ning.billing.payment.api.PaymentMethodKVInfo;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentMethodInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.plugin.api.PaymentPluginStatus;
import com.ning.billing.payment.plugin.api.RefundInfoPlugin;
import com.ning.billing.payment.plugin.api.RefundPluginStatus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.entity.DefaultPagination;
import com.ning.billing.util.entity.Pagination;

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
    public PaymentInfoPlugin processPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final CallContext context) throws PaymentPluginApiException {
        return new DefaultNoOpPaymentInfoPlugin(amount, clock.getUTCNow(), clock.getUTCNow(), PaymentPluginStatus.PROCESSED, null);
    }

    @Override
    public PaymentInfoPlugin getPaymentInfo(final UUID kbAccountId, final UUID kbPaymentId, final TenantContext context) throws PaymentPluginApiException {
        return new DefaultNoOpPaymentInfoPlugin(BigDecimal.ZERO, clock.getUTCNow(), clock.getUTCNow(), PaymentPluginStatus.PROCESSED, null);
    }

    @Override
    public RefundInfoPlugin processRefund(final UUID kbAccountId, final UUID kbPaymentId, final BigDecimal refundAmount, final Currency currency, final CallContext context) throws PaymentPluginApiException {
        return new DefaultNoOpRefundInfoPlugin(BigDecimal.ZERO, clock.getUTCNow(), clock.getUTCNow(), RefundPluginStatus.PROCESSED, null);
    }

    @Override
    public List<RefundInfoPlugin> getRefundInfo(final UUID kbAccountId, final UUID kbPaymentId, final TenantContext context) throws PaymentPluginApiException {
        return Collections.emptyList();
    }

    @Override
    public void addPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final PaymentMethodPlugin paymentMethodProps, final boolean setDefault, final CallContext context) throws PaymentPluginApiException {
    }

    @Override
    public void deletePaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final CallContext context) throws PaymentPluginApiException {
    }

    @Override
    public PaymentMethodPlugin getPaymentMethodDetail(final UUID kbAccountId, final UUID kbPaymentMethodId, final TenantContext context) throws PaymentPluginApiException {
        return new DefaultNoOpPaymentMethodPlugin("unknown", false, Collections.<PaymentMethodKVInfo>emptyList());
    }

    @Override
    public void setDefaultPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final CallContext context) throws PaymentPluginApiException {
    }

    @Override
    public List<PaymentMethodInfoPlugin> getPaymentMethods(final UUID kbAccountId, final boolean refreshFromGateway, final CallContext context) throws PaymentPluginApiException {
        return ImmutableList.<PaymentMethodInfoPlugin>of();
    }

    @Override
    public Pagination<PaymentMethodPlugin> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final TenantContext tenantContext) throws PaymentPluginApiException {
        return new DefaultPagination<PaymentMethodPlugin>(offset, limit, 0L, 0L, Iterators.<PaymentMethodPlugin>emptyIterator());
    }

    @Override
    public void resetPaymentMethods(final UUID kbAccountId, final List<PaymentMethodInfoPlugin> paymentMethods) throws PaymentPluginApiException {
    }
}
