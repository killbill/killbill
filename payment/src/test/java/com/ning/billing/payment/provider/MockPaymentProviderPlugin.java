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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.payment.api.TestPaymentMethodPlugin;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.plugin.api.NoOpPaymentPluginApi;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin.PaymentPluginStatus;
import com.ning.billing.payment.plugin.api.PaymentMethodInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.plugin.api.RefundInfoPlugin;
import com.ning.billing.payment.plugin.api.RefundInfoPlugin.RefundPluginStatus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.clock.Clock;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;

/**
 * This MockPaymentProviderPlugin only works for a single accounts as we don't specify the accountId
 * for opeartions such as addPaymentMethod.
 */
public class MockPaymentProviderPlugin implements NoOpPaymentPluginApi {

    public static final String PLUGIN_NAME = "__NO_OP__";

    private final AtomicBoolean makeNextInvoiceFailWithError = new AtomicBoolean(false);
    private final AtomicBoolean makeNextInvoiceFailWithException = new AtomicBoolean(false);
    private final AtomicBoolean makeAllInvoicesFailWithError = new AtomicBoolean(false);

    private final Map<String, PaymentInfoPlugin> payments = new ConcurrentHashMap<String, PaymentInfoPlugin>();
    // Note: we can't use HashMultiMap as we care about storing duplicate key/value pairs
    private final Multimap<String, RefundInfoPlugin> refunds = LinkedListMultimap.<String, RefundInfoPlugin>create();
    private final Map<String, PaymentMethodPlugin> paymentMethods = new ConcurrentHashMap<String, PaymentMethodPlugin>();
    private final Map<String, PaymentMethodInfoPlugin> paymentMethodsInfo = new ConcurrentHashMap<String, PaymentMethodInfoPlugin>();

    private final Clock clock;

    @Inject
    public MockPaymentProviderPlugin(final Clock clock) {
        this.clock = clock;
        clear();
    }

    @Override
    public void clear() {
        makeNextInvoiceFailWithException.set(false);
        makeAllInvoicesFailWithError.set(false);
        makeNextInvoiceFailWithError.set(false);
    }

    @Override
    public void makeNextPaymentFailWithError() {
        makeNextInvoiceFailWithError.set(true);
    }

    @Override
    public void makeNextPaymentFailWithException() {
        makeNextInvoiceFailWithException.set(true);
    }

    @Override
    public void makeAllInvoicesFailWithError(final boolean failure) {
        makeAllInvoicesFailWithError.set(failure);
    }

    @Override
    public PaymentInfoPlugin processPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final CallContext context) throws PaymentPluginApiException {
        if (makeNextInvoiceFailWithException.getAndSet(false)) {
            throw new PaymentPluginApiException("", "test error");
        }

        final PaymentPluginStatus status = (makeAllInvoicesFailWithError.get() || makeNextInvoiceFailWithError.getAndSet(false)) ? PaymentPluginStatus.ERROR : PaymentPluginStatus.PROCESSED;
        final PaymentInfoPlugin result = new DefaultNoOpPaymentInfoPlugin(amount, clock.getUTCNow(), clock.getUTCNow(), status, null);
        payments.put(kbPaymentId.toString(), result);
        return result;
    }

    @Override
    public PaymentInfoPlugin getPaymentInfo(final UUID kbAccountId, final UUID kbPaymentId, final TenantContext context) throws PaymentPluginApiException {
        final PaymentInfoPlugin payment = payments.get(kbPaymentId.toString());
        if (payment == null) {
            throw new PaymentPluginApiException("", "No payment found for payment id " + kbPaymentId.toString());
        }
        return payment;
    }


    @Override
    public void addPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final PaymentMethodPlugin paymentMethodProps, final boolean setDefault, final CallContext context) throws PaymentPluginApiException {
        // externalPaymentMethodId is set to a random value
        final PaymentMethodPlugin realWithID = new TestPaymentMethodPlugin(paymentMethodProps, UUID.randomUUID().toString());
        paymentMethods.put(kbPaymentMethodId.toString(), realWithID);

        final PaymentMethodInfoPlugin realInfoWithID = new DefaultPaymentMethodInfoPlugin(kbAccountId, kbPaymentMethodId, setDefault, UUID.randomUUID().toString());
        paymentMethodsInfo.put(kbPaymentMethodId.toString(), realInfoWithID);
    }

    @Override
    public void deletePaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final CallContext context) throws PaymentPluginApiException {
        paymentMethods.remove(kbPaymentMethodId.toString());
        paymentMethodsInfo.remove(kbPaymentMethodId.toString());
    }

    @Override
    public PaymentMethodPlugin getPaymentMethodDetail(final UUID kbAccountId, final UUID kbPaymentMethodId, final TenantContext context) throws PaymentPluginApiException {
        return paymentMethods.get(kbPaymentMethodId.toString());
    }

    @Override
    public void setDefaultPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final CallContext context) throws PaymentPluginApiException {
    }

    @Override
    public List<PaymentMethodInfoPlugin> getPaymentMethods(final UUID kbAccountId, final boolean refreshFromGateway, final CallContext context) {
        return ImmutableList.<PaymentMethodInfoPlugin>copyOf(paymentMethodsInfo.values());
    }

    @Override
    public void resetPaymentMethods(final UUID kbAccountId, final List<PaymentMethodInfoPlugin> input) {
        paymentMethodsInfo.clear();
        if (input != null) {
            for (final PaymentMethodInfoPlugin cur : input) {
                paymentMethodsInfo.put(cur.getPaymentMethodId().toString(), cur);
            }
        }
    }

    @Override
    public RefundInfoPlugin processRefund(final UUID kbAccountId, final UUID kbPaymentId, final BigDecimal refundAmount, final Currency currency, final CallContext context) throws PaymentPluginApiException {
        final PaymentInfoPlugin paymentInfoPlugin = getPaymentInfo(kbAccountId, kbPaymentId, context);
        if (paymentInfoPlugin == null) {
            throw new PaymentPluginApiException("", String.format("No payment found for payment id %s (plugin %s)", kbPaymentId.toString(), PLUGIN_NAME));
        }

        BigDecimal maxAmountRefundable = paymentInfoPlugin.getAmount();
        for (final RefundInfoPlugin refund : refunds.get(kbPaymentId.toString())) {
            maxAmountRefundable = maxAmountRefundable.add(refund.getAmount().negate());
        }
        if (maxAmountRefundable.compareTo(refundAmount) < 0) {
            throw new PaymentPluginApiException("", String.format("Refund amount of %s for payment id %s is bigger than the payment amount %s (plugin %s)",
                                                                  refundAmount, kbPaymentId.toString(), paymentInfoPlugin.getAmount(), PLUGIN_NAME));
        }

        final DefaultNoOpRefundInfoPlugin refundInfoPlugin = new DefaultNoOpRefundInfoPlugin(refundAmount, clock.getUTCNow(), clock.getUTCNow(), RefundPluginStatus.PROCESSED, null);
        refunds.put(kbPaymentId.toString(), refundInfoPlugin);

        return refundInfoPlugin;
    }

    @Override
    public List<RefundInfoPlugin> getRefundInfo(final UUID kbAccountId, final UUID kbPaymentId, final TenantContext context) throws PaymentPluginApiException {
        return Collections.<RefundInfoPlugin>emptyList();
    }
}
