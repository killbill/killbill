/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TestPaymentMethodPlugin;
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
import org.killbill.billing.util.entity.DefaultPagination;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DBRouterUntyped;
import org.killbill.billing.util.entity.dao.DBRouterUntyped.THREAD_STATE;
import org.killbill.clock.Clock;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

/**
 * This MockPaymentProviderPlugin only works for a single accounts as we don't specify the accountId
 * for operations such as addPaymentMethod.
 */
public class MockPaymentProviderPlugin implements PaymentPluginApi {

    public static final String GATEWAY_ERROR_CODE = "gatewayErrorCode";
    public static final String GATEWAY_ERROR = "gatewayError";

    public static final String PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE = "paymentPluginStatusOverride";

    public static final String PLUGIN_NAME = "__NO_OP__";

    private final AtomicBoolean makeNextPaymentFailWithError = new AtomicBoolean(false);
    private final AtomicBoolean makeNextPaymentFailWithCancellation = new AtomicBoolean(false);
    private final AtomicBoolean makeNextPaymentFailWithException = new AtomicBoolean(false);
    private final AtomicBoolean makeAllPaymentsFailWithError = new AtomicBoolean(false);
    private final AtomicBoolean makeNextPaymentPending = new AtomicBoolean(false);
    private final AtomicInteger makePluginWaitSomeMilliseconds = new AtomicInteger(0);
    private final AtomicReference<BigDecimal> overrideNextProcessedAmount = new AtomicReference<BigDecimal>();
    private final AtomicReference<Currency> overrideNextProcessedCurrency = new AtomicReference<Currency>();

    private final Map<String, InternalPaymentInfo> payments = new ConcurrentHashMap<String, InternalPaymentInfo>();
    private final Map<String, List<PaymentTransactionInfoPlugin>> paymentTransactions = new ConcurrentHashMap<String, List<PaymentTransactionInfoPlugin>>();

    // Note: we can't use HashMultiMap as we care about storing duplicate key/value pairs
    private final Map<String, PaymentMethodPlugin> paymentMethods = new ConcurrentHashMap<String, PaymentMethodPlugin>();
    private final Map<String, PaymentMethodInfoPlugin> paymentMethodsInfo = new ConcurrentHashMap<String, PaymentMethodInfoPlugin>();

    private final Clock clock;

    private THREAD_STATE lastThreadState = null;

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

    public void clear() {
        makeNextPaymentFailWithException.set(false);
        makeAllPaymentsFailWithError.set(false);
        makeNextPaymentFailWithError.set(false);
        makeNextPaymentFailWithCancellation.set(false);
        makeNextPaymentPending.set(false);
        makePluginWaitSomeMilliseconds.set(0);
        overrideNextProcessedAmount.set(null);
        paymentMethods.clear();
        payments.clear();
        paymentTransactions.clear();
        paymentMethodsInfo.clear();
    }

    public void makeNextPaymentFailWithError() {
        makeNextPaymentFailWithError.set(true);
    }

    public void makeNextPaymentPending() {
        makeNextPaymentPending.set(true);
    }

    public void makeNextPaymentFailWithCancellation() {
        makeNextPaymentFailWithCancellation.set(true);
    }

    public void makeNextPaymentFailWithException() {
        makeNextPaymentFailWithException.set(true);
    }

    public void makeAllInvoicesFailWithError(final boolean failure) {
        makeAllPaymentsFailWithError.set(failure);
    }

    public void makePluginWaitSomeMilliseconds(final int milliseconds) {
        makePluginWaitSomeMilliseconds.set(milliseconds);
    }

    public void overrideNextProcessedAmount(final BigDecimal amount) {
        overrideNextProcessedAmount.set(amount);
    }

    public void overrideNextProcessedCurrency(final Currency currency) {
        overrideNextProcessedCurrency.set(currency);
    }

    public void updatePaymentTransactions(final UUID paymentId, final List<PaymentTransactionInfoPlugin> newTransactions) {
        if (paymentTransactions.containsKey(paymentId.toString())) {
            paymentTransactions.put (paymentId.toString(), newTransactions);
        }
    }

    public void overridePaymentPluginStatus(final UUID kbPaymentId, final UUID kbTransactionId, final PaymentPluginStatus status) {
        final List<PaymentTransactionInfoPlugin> existingTransactions = paymentTransactions.remove(kbPaymentId.toString());
        final List<PaymentTransactionInfoPlugin> newTransactions = new LinkedList<PaymentTransactionInfoPlugin>();
        paymentTransactions.put(kbPaymentId.toString(), newTransactions);

        for (final PaymentTransactionInfoPlugin existingTransaction : existingTransactions) {
            if (existingTransaction.getKbTransactionPaymentId().equals(kbTransactionId)) {
                final PaymentTransactionInfoPlugin newTransaction = new DefaultNoOpPaymentInfoPlugin(existingTransaction.getKbPaymentId(),
                                                                                                     existingTransaction.getKbTransactionPaymentId(),
                                                                                                     existingTransaction.getTransactionType(),
                                                                                                     existingTransaction.getAmount(),
                                                                                                     existingTransaction.getCurrency(),
                                                                                                     existingTransaction.getEffectiveDate(),
                                                                                                     existingTransaction.getCreatedDate(),
                                                                                                     status,
                                                                                                     existingTransaction.getGatewayErrorCode(),
                                                                                                     existingTransaction.getGatewayError());
                newTransactions.add(newTransaction);
            } else {
                newTransactions.add(existingTransaction);
            }
        }
    }

    public THREAD_STATE getLastThreadState() {
        return lastThreadState;
    }

    @Override
    public PaymentTransactionInfoPlugin authorizePayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentPluginApiException {
        updateLastThreadState();
        return getPaymentTransactionInfoPluginResult(kbPaymentId, kbTransactionId, TransactionType.AUTHORIZE, amount, currency, properties);
    }

    @Override
    public PaymentTransactionInfoPlugin capturePayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentPluginApiException {
        updateLastThreadState();
        return getPaymentTransactionInfoPluginResult(kbPaymentId, kbTransactionId, TransactionType.CAPTURE, amount, currency, properties);
    }

    @Override
    public PaymentTransactionInfoPlugin purchasePayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        updateLastThreadState();
        return getPaymentTransactionInfoPluginResult(kbPaymentId, kbTransactionId, TransactionType.PURCHASE, amount, currency, properties);
    }

    @Override
    public PaymentTransactionInfoPlugin voidPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentPluginApiException {
        updateLastThreadState();
        return getPaymentTransactionInfoPluginResult(kbPaymentId, kbTransactionId, TransactionType.VOID, null, null, properties);
    }

    @Override
    public PaymentTransactionInfoPlugin creditPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal amount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context)
            throws PaymentPluginApiException {
        updateLastThreadState();
        return getPaymentTransactionInfoPluginResult(kbPaymentId, kbTransactionId, TransactionType.CREDIT, amount, currency, properties);
    }

    @Override
    public List<PaymentTransactionInfoPlugin> getPaymentInfo(final UUID kbAccountId, final UUID kbPaymentId, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentPluginApiException {
        updateLastThreadState();
        final List<PaymentTransactionInfoPlugin> result = paymentTransactions.get(kbPaymentId.toString());
        return result != null ? result : ImmutableList.<PaymentTransactionInfoPlugin>of();
    }

    @Override
    public Pagination<PaymentTransactionInfoPlugin> searchPayments(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentPluginApiException {
        updateLastThreadState();
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void addPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final PaymentMethodPlugin paymentMethodProps, final boolean setDefault, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        updateLastThreadState();
        // externalPaymentMethodId is set to a random value
        final PaymentMethodPlugin realWithID = new TestPaymentMethodPlugin(kbPaymentMethodId, paymentMethodProps, UUID.randomUUID().toString());
        paymentMethods.put(kbPaymentMethodId.toString(), realWithID);

        final PaymentMethodInfoPlugin realInfoWithID = new DefaultPaymentMethodInfoPlugin(kbAccountId, kbPaymentMethodId, setDefault, UUID.randomUUID().toString());
        paymentMethodsInfo.put(kbPaymentMethodId.toString(), realInfoWithID);
    }

    @Override
    public void deletePaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        updateLastThreadState();
        paymentMethods.remove(kbPaymentMethodId.toString());
        paymentMethodsInfo.remove(kbPaymentMethodId.toString());
    }

    @Override
    public PaymentMethodPlugin getPaymentMethodDetail(final UUID kbAccountId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentPluginApiException {
        updateLastThreadState();
        return paymentMethods.get(kbPaymentMethodId.toString());
    }

    @Override
    public void setDefaultPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        updateLastThreadState();
    }

    @Override
    public List<PaymentMethodInfoPlugin> getPaymentMethods(final UUID kbAccountId, final boolean refreshFromGateway, final Iterable<PluginProperty> properties, final CallContext context) {
        updateLastThreadState();
        return ImmutableList.<PaymentMethodInfoPlugin>copyOf(paymentMethodsInfo.values());
    }

    @Override
    public Pagination<PaymentMethodPlugin> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final Iterable<PluginProperty> properties, final TenantContext tenantContext) throws PaymentPluginApiException {
        updateLastThreadState();
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
        return DefaultPagination.<PaymentMethodPlugin>build(offset, limit, paymentMethods.size(), results);
    }

    @Override
    public void resetPaymentMethods(final UUID kbAccountId, final List<PaymentMethodInfoPlugin> input, final Iterable<PluginProperty> properties, final CallContext callContext) {
        updateLastThreadState();
        paymentMethodsInfo.clear();
        if (input != null) {
            for (final PaymentMethodInfoPlugin cur : input) {
                paymentMethodsInfo.put(cur.getPaymentMethodId().toString(), cur);
            }
        }
    }

    @Override
    public HostedPaymentPageFormDescriptor buildFormDescriptor(final UUID kbAccountId, final Iterable<PluginProperty> customFields, final Iterable<PluginProperty> properties, final CallContext callContext) {
        updateLastThreadState();
        return new DefaultNoOpHostedPaymentPageFormDescriptor(kbAccountId);
    }

    @Override
    public GatewayNotification processNotification(final String notification, final Iterable<PluginProperty> properties, final CallContext callContext) throws PaymentPluginApiException {
        updateLastThreadState();
        return new DefaultNoOpGatewayNotification();
    }

    @Override
    public PaymentTransactionInfoPlugin refundPayment(final UUID kbAccountId, final UUID kbPaymentId, final UUID kbTransactionId, final UUID kbPaymentMethodId, final BigDecimal refundAmount, final Currency currency, final Iterable<PluginProperty> properties, final CallContext context) throws PaymentPluginApiException {
        updateLastThreadState();

        final InternalPaymentInfo info = payments.get(kbPaymentId.toString());
        if (info == null) {
            throw new PaymentPluginApiException("", String.format("No payment found for payment id %s (plugin %s)", kbPaymentId.toString(), PLUGIN_NAME));
        }
        BigDecimal maxAmountRefundable = info.getCaptureAmount().add(info.getPurchasedAmount());
        if (maxAmountRefundable.compareTo(info.getRefundAmount()) < 0) {
            throw new PaymentPluginApiException("", String.format("Refund amount of %s for payment id %s is bigger than the payment amount %s (plugin %s)",
                                                                  refundAmount, kbPaymentId.toString(), maxAmountRefundable, PLUGIN_NAME));
        }
        return getPaymentTransactionInfoPluginResult(kbPaymentId, kbTransactionId, TransactionType.REFUND, refundAmount, currency, properties);
    }

    public void overridePaymentTransactionPluginResult(final UUID kbPaymentId, final UUID kbTransactionId, final PaymentPluginStatus paymentPluginStatus) throws PaymentPluginApiException {
        final List<PaymentTransactionInfoPlugin> existingTransactions = paymentTransactions.get(kbPaymentId.toString());
        PaymentTransactionInfoPlugin paymentTransactionInfoPlugin = null;
        for (final PaymentTransactionInfoPlugin existingTransaction : existingTransactions) {
            if (existingTransaction.getKbTransactionPaymentId().equals(kbTransactionId)) {
                paymentTransactionInfoPlugin = existingTransaction;
                break;
            }
        }
        Preconditions.checkNotNull(paymentTransactionInfoPlugin);

        final Iterable<PluginProperty> pluginProperties = ImmutableList.<PluginProperty>of(new PluginProperty(MockPaymentProviderPlugin.PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE, paymentPluginStatus.toString(), false));
        getPaymentTransactionInfoPluginResult(kbPaymentId, kbTransactionId, TransactionType.AUTHORIZE, paymentTransactionInfoPlugin.getAmount(), paymentTransactionInfoPlugin.getCurrency(), pluginProperties);
    }

    private PaymentTransactionInfoPlugin getPaymentTransactionInfoPluginResult(final UUID kbPaymentId, final UUID kbTransactionId, final TransactionType type, @Nullable final BigDecimal amount, @Nullable final Currency currency, final Iterable<PluginProperty> pluginProperties) throws PaymentPluginApiException {
        if (makePluginWaitSomeMilliseconds.get() > 0) {
            try {
                Thread.sleep(makePluginWaitSomeMilliseconds.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PaymentPluginApiException("An Interruption occurred while the Thread was sleeping.", e);
            }
        }

        if (makeNextPaymentFailWithException.getAndSet(false)) {
            throw new PaymentPluginApiException("", "test error");
        }

        final PluginProperty paymentPluginStatusOverride = Iterables.tryFind(pluginProperties, new Predicate<PluginProperty>() {
            @Override
            public boolean apply(final PluginProperty input) {
                return PLUGIN_PROPERTY_PAYMENT_PLUGIN_STATUS_OVERRIDE.equals(input.getKey());
            }
        }).orNull();

        final PaymentPluginStatus status;
        if (paymentPluginStatusOverride != null && paymentPluginStatusOverride.getValue() != null) {
            status = PaymentPluginStatus.valueOf(paymentPluginStatusOverride.getValue().toString());
        } else if (makeAllPaymentsFailWithError.get() || makeNextPaymentFailWithError.getAndSet(false)) {
            status = PaymentPluginStatus.ERROR;
        } else if (makeNextPaymentFailWithCancellation.getAndSet(false)) {
            status = PaymentPluginStatus.CANCELED;
        } else if (makeNextPaymentPending.getAndSet(false)) {
            status = PaymentPluginStatus.PENDING;
        } else {
            status = PaymentPluginStatus.PROCESSED;
        }
        final String errorCode = status == PaymentPluginStatus.PROCESSED ? "" : GATEWAY_ERROR_CODE;
        final String error = status == PaymentPluginStatus.PROCESSED ? "" : GATEWAY_ERROR;

        InternalPaymentInfo info = payments.get(kbPaymentId.toString());
        if (info == null) {
            info = new InternalPaymentInfo();
            payments.put(kbPaymentId.toString(), info);
        }

        final BigDecimal overrideNextProcessedAmount = this.overrideNextProcessedAmount.getAndSet(null);
        final BigDecimal processedAmount = overrideNextProcessedAmount != null ? overrideNextProcessedAmount : amount;
        Currency processedCurrency = overrideNextProcessedCurrency.getAndSet(null);
        if (processedCurrency == null) {
            processedCurrency = currency;
        }

        final PaymentTransactionInfoPlugin result = new DefaultNoOpPaymentInfoPlugin(kbPaymentId, kbTransactionId, type, processedAmount, processedCurrency, clock.getUTCNow(), clock.getUTCNow(), status, errorCode, error);
        List<PaymentTransactionInfoPlugin> existingTransactions = paymentTransactions.get(kbPaymentId.toString());
        if (existingTransactions == null) {
            existingTransactions = new ArrayList<PaymentTransactionInfoPlugin>();
            paymentTransactions.put(kbPaymentId.toString(), existingTransactions);
        }

        final Iterator<PaymentTransactionInfoPlugin> iterator = existingTransactions.iterator();
        while (iterator.hasNext()) {
            final PaymentTransactionInfoPlugin existingTransaction = iterator.next();
            if (existingTransaction.getKbTransactionPaymentId().equals(kbTransactionId)) {
                info.addAmount(type, existingTransaction.getAmount().negate());
                iterator.remove();
            }
        }
        existingTransactions.add(result);
        info.addAmount(type, result.getAmount());

        return result;
    }

    private void updateLastThreadState() {
        lastThreadState = DBRouterUntyped.getCurrentState();
    }
}
