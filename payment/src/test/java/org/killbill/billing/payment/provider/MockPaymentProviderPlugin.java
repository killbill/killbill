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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TestPaymentMethodPlugin;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.plugin.api.GatewayNotification;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;
import org.killbill.billing.payment.plugin.api.NoOpPaymentPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentMethodInfoPlugin;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.DefaultPagination;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

/**
 * This MockPaymentProviderPlugin only works for a single accounts as we don't specify the accountId
 * for operations such as addPaymentMethod.
 */
public class MockPaymentProviderPlugin implements NoOpPaymentPluginApi {

    public static final String PLUGIN_NAME = "__NO_OP__";

    private final AtomicBoolean makeNextInvoiceFailWithError = new AtomicBoolean(false);
    private final AtomicBoolean makeNextInvoiceFailWithException = new AtomicBoolean(false);
    private final AtomicBoolean makeAllInvoicesFailWithError = new AtomicBoolean(false);

    private final Map<String, InternalPaymentInfo> payments = new ConcurrentHashMap<String, InternalPaymentInfo>();
    private final Map<String, List<PaymentTransactionInfoPlugin>> paymentTransactions = new ConcurrentHashMap<String, List<PaymentTransactionInfoPlugin>>();

    // Note: we can't use HashMultiMap as we care about storing duplicate key/value pairs
    private final Map<String, PaymentMethodPlugin> paymentMethods = new ConcurrentHashMap<String, PaymentMethodPlugin>();
    private final Map<String, PaymentMethodInfoPlugin> paymentMethodsInfo = new ConcurrentHashMap<String, PaymentMethodInfoPlugin>();

    private final Clock clock;

    private class InternalPaymentInfo {

        private BigDecimal authAmount;
        private BigDecimal captureAmount;
        private BigDecimal purchasedAmount;
        private BigDecimal refundAmount;
        private BigDecimal creditAmount;

        private InternalPaymentInfo() {
            this.authAmount = BigDecimal.ZERO;
            this.captureAmount = BigDecimal.ZERO;
            this.purchasedAmount = BigDecimal.ZERO;
            this.refundAmount = BigDecimal.ZERO;
            this.creditAmount = BigDecimal.ZERO;
        }

        public BigDecimal getAuthAmount() {
            return authAmount;
        }

        public BigDecimal getCaptureAmount() {
            return captureAmount;
        }

        public BigDecimal getPurchasedAmount() {
            return purchasedAmount;
        }

        public BigDecimal getRefundAmount() {
            return refundAmount;
        }

        public BigDecimal getCreditAmount() {
            return creditAmount;
        }

        public BigDecimal getAmount(TransactionType type) {
            switch (type) {
                case AUTHORIZE:
                    return getAuthAmount();
                case CAPTURE:
                    return getCaptureAmount();
                case PURCHASE:
                    return getPurchasedAmount();
                case VOID:
                    return BigDecimal.ZERO;
                case CREDIT:
                    return getCreditAmount();
                case REFUND:
                    return getRefundAmount();
                default:
                    throw new RuntimeException("Unsupported type " + type);
            }
        }

        public void addAmount(TransactionType type, BigDecimal amount) {
            switch (type) {
                case AUTHORIZE:
                    addAuthAmount(amount);
                    break;
                case CAPTURE:
                    addCaptureAmount(amount);
                    break;
                case PURCHASE:
                    addPurchasedAmount(amount);
                    break;
                case VOID:
                    voidAuthAmount();
                    break;
                case CREDIT:
                    addCreditAmount(amount);
                    break;
                case REFUND:
                    addRefundAmount(amount);
                    break;
            }
        }

        public void addAuthAmount(final BigDecimal authAmount) {
            this.authAmount = this.authAmount.add(authAmount);
        }

        public void addCaptureAmount(final BigDecimal captureAmount) {
            this.captureAmount = this.captureAmount.add(captureAmount);
        }

        public void addPurchasedAmount(final BigDecimal purchasedAmount) {
            this.purchasedAmount = this.purchasedAmount.add(purchasedAmount);
        }

        public void addRefundAmount(final BigDecimal refundAmount) {
            this.refundAmount = this.refundAmount.add(refundAmount);
        }

        public void addCreditAmount(final BigDecimal creditAmount) {
            this.creditAmount = this.creditAmount.add(creditAmount);
        }

        public void voidAuthAmount() {
            this.authAmount = BigDecimal.ZERO;
        }
    }

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
        paymentMethods.clear();
        payments.clear();
        paymentTransactions.clear();
        paymentMethodsInfo.clear();
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
    public PaymentTransactionInfoPlugin authorizePayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentPluginApiException {
        return getPaymentTransactionInfoPluginResult(kbPaymentId, kbTransactionId, TransactionType.AUTHORIZE, amount, currency);
    }

    @Override
    public PaymentTransactionInfoPlugin capturePayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentPluginApiException {
        return getPaymentTransactionInfoPluginResult(kbPaymentId, kbTransactionId, TransactionType.CAPTURE, amount, currency);
    }

    @Override
    public PaymentTransactionInfoPlugin purchasePayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        return getPaymentTransactionInfoPluginResult(kbPaymentId, kbTransactionId, TransactionType.PURCHASE, amount, currency);
    }

    @Override
    public PaymentTransactionInfoPlugin voidPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentPluginApiException {
        return getPaymentTransactionInfoPluginResult(kbPaymentId, kbTransactionId, TransactionType.VOID, BigDecimal.ZERO, null);
    }

    @Override
    public PaymentTransactionInfoPlugin creditPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentPluginApiException {
        return getPaymentTransactionInfoPluginResult(kbPaymentId, kbTransactionId, TransactionType.CREDIT, amount, currency);
    }

    @Override
    public List<PaymentTransactionInfoPlugin> getPaymentInfo(final UUID kbAccountId, final UUID kbPaymentId, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentPluginApiException {
        final List<PaymentTransactionInfoPlugin> result = paymentTransactions.get(kbPaymentId.toString());
        return result != null ? result : ImmutableList.<PaymentTransactionInfoPlugin>of();
    }

    @Override
    public Pagination<PaymentTransactionInfoPlugin> searchPayments(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentPluginApiException {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void addPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final PaymentMethodPlugin paymentMethodProps, final boolean setDefault, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        // externalPaymentMethodId is set to a random value
        final PaymentMethodPlugin realWithID = new TestPaymentMethodPlugin(kbPaymentMethodId, paymentMethodProps, UUID.randomUUID().toString());
        paymentMethods.put(kbPaymentMethodId.toString(), realWithID);

        final PaymentMethodInfoPlugin realInfoWithID = new DefaultPaymentMethodInfoPlugin(kbAccountId, kbPaymentMethodId, setDefault, UUID.randomUUID().toString());
        paymentMethodsInfo.put(kbPaymentMethodId.toString(), realInfoWithID);
    }

    @Override
    public void deletePaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        paymentMethods.remove(kbPaymentMethodId.toString());
        paymentMethodsInfo.remove(kbPaymentMethodId.toString());
    }

    @Override
    public PaymentMethodPlugin getPaymentMethodDetail(final UUID kbAccountId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentPluginApiException {
        return paymentMethods.get(kbPaymentMethodId.toString());
    }

    @Override
    public void setDefaultPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
    }

    @Override
    public List<PaymentMethodInfoPlugin> getPaymentMethods(final UUID kbAccountId, final boolean refreshFromGateway, final Iterable<PluginProperty> properties, final CallContext context) {
        return ImmutableList.<PaymentMethodInfoPlugin>copyOf(paymentMethodsInfo.values());
    }

    @Override
    public Pagination<PaymentMethodPlugin> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentPluginApiException {
        final ImmutableList<PaymentMethodPlugin> results = ImmutableList.<PaymentMethodPlugin>copyOf(Iterables.<PaymentMethodPlugin>filter(paymentMethods.values(), new Predicate<PaymentMethodPlugin>() {
            @Override
            public boolean apply(final PaymentMethodPlugin input) {
                if (input.getProperties() !=  null) {
                    for (PluginProperty cur : input.getProperties()) {
                        if (cur.getValue().equals(searchKey)) {
                            return true;
                        }
                    }
                }
                return (input.getKbPaymentMethodId().toString().equals(searchKey));
            }
        }));
        return DefaultPagination.<PaymentMethodPlugin>build(offset, limit, results);
    }

    @Override
    public void resetPaymentMethods(final UUID kbAccountId, final List<PaymentMethodInfoPlugin> input, final Iterable<PluginProperty> properties, final CallContext callContext) {
        paymentMethodsInfo.clear();
        if (input != null) {
            for (final PaymentMethodInfoPlugin cur : input) {
                paymentMethodsInfo.put(cur.getPaymentMethodId().toString(), cur);
            }
        }
    }

    @Override
    public HostedPaymentPageFormDescriptor buildFormDescriptor(final UUID kbAccountId, final Iterable<PluginProperty> customFields, final Iterable<PluginProperty> properties, final CallContext callContext) {
        return new DefaultNoOpHostedPaymentPageFormDescriptor(kbAccountId);
    }

    @Override
    public GatewayNotification processNotification(final String notification, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentPluginApiException {
        return new DefaultNoOpGatewayNotification();
    }

    @Override
    public PaymentTransactionInfoPlugin refundPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbPaymentMethodId, final UUID kbTransactionId, final BigDecimal refundAmount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {

        final InternalPaymentInfo info = payments.get(kbPaymentId.toString());
        if (info == null) {
            throw new PaymentPluginApiException("", String.format("No payment found for payment id %s (plugin %s)", kbPaymentId.toString(), PLUGIN_NAME));
        }
        BigDecimal maxAmountRefundable = info.getCaptureAmount().add(info.getPurchasedAmount());
        if (maxAmountRefundable.compareTo(info.getRefundAmount()) < 0) {
            throw new PaymentPluginApiException("", String.format("Refund amount of %s for payment id %s is bigger than the payment amount %s (plugin %s)",
                                                                  refundAmount, kbPaymentId.toString(), maxAmountRefundable, PLUGIN_NAME));
        }
        return getPaymentTransactionInfoPluginResult(kbPaymentId, kbTransactionId, TransactionType.REFUND, refundAmount, currency);
    }

    private PaymentTransactionInfoPlugin getPaymentTransactionInfoPluginResult(final UUID kbPaymentId, final UUID kbTransactionId, final TransactionType type, final BigDecimal amount, final Currency currency) throws PaymentPluginApiException {

        if (makeNextInvoiceFailWithException.getAndSet(false)) {
            throw new PaymentPluginApiException("", "test error");
        }

        final PaymentPluginStatus status = (makeAllInvoicesFailWithError.get() || makeNextInvoiceFailWithError.getAndSet(false)) ? PaymentPluginStatus.ERROR : PaymentPluginStatus.PROCESSED;

        InternalPaymentInfo info = payments.get(kbPaymentId.toString());
        if (info == null) {
            info = new InternalPaymentInfo();
            payments.put(kbPaymentId.toString(), info);
        }
        info.addAmount(type, amount);

        final PaymentTransactionInfoPlugin result = new DefaultNoOpPaymentInfoPlugin(kbPaymentId, kbTransactionId, type, amount, currency, clock.getUTCNow(), clock.getUTCNow(), status, null);
        List<PaymentTransactionInfoPlugin> existingTransactions = paymentTransactions.get(kbPaymentId.toString());
        if (existingTransactions == null) {
            existingTransactions = new ArrayList<PaymentTransactionInfoPlugin>();
            paymentTransactions.put(kbPaymentId.toString(), existingTransactions);
        }

        existingTransactions.add(result);
        return result;
    }
}
