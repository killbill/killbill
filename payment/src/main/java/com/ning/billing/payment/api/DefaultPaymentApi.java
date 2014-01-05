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

package com.ning.billing.payment.api;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.clock.Clock;
import com.ning.billing.payment.core.PaymentMethodProcessor;
import com.ning.billing.payment.core.PaymentProcessor;
import com.ning.billing.payment.core.RefundProcessor;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.entity.Pagination;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

public class DefaultPaymentApi implements PaymentApi {

    private final PaymentMethodProcessor methodProcessor;
    private final PaymentProcessor paymentProcessor;
    private final RefundProcessor refundProcessor;
    private final InternalCallContextFactory internalCallContextFactory;
    private final Clock clock;

    @Inject
    public DefaultPaymentApi(final PaymentMethodProcessor methodProcessor,
                             final PaymentProcessor paymentProcessor,
                             final RefundProcessor refundProcessor,
                             final Clock clock,
                             final InternalCallContextFactory internalCallContextFactory) {
        this.methodProcessor = methodProcessor;
        this.paymentProcessor = paymentProcessor;
        this.refundProcessor = refundProcessor;
        this.clock = clock;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public Payment createPayment(final Account account, final UUID invoiceId,
                                 final BigDecimal amount, final CallContext context) throws PaymentApiException {
        return paymentProcessor.createPayment(account, invoiceId, amount,
                                              internalCallContextFactory.createInternalCallContext(account.getId(), context), true, false);
    }

    @Override
    public Payment createExternalPayment(final Account account, final UUID invoiceId, final BigDecimal amount, final CallContext context) throws PaymentApiException {
        return paymentProcessor.createPayment(account, invoiceId, amount,
                                              internalCallContextFactory.createInternalCallContext(account.getId(), context), true, true);
    }

    @Override
    public void notifyPendingPaymentOfStateChanged(final Account account, final UUID paymentId, final boolean isSuccess, final CallContext context) throws PaymentApiException {
        paymentProcessor.notifyPendingPaymentOfStateChanged(account, paymentId, isSuccess,
                                                            internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public Payment retryPayment(final Account account, final UUID paymentId, final CallContext context) throws PaymentApiException {
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), context);
        paymentProcessor.retryPaymentFromApi(paymentId, internalCallContext);
        return getPayment(paymentId, false, context);
    }

    @Override
    public Payment getPayment(final UUID paymentId, final boolean withPluginInfo, final TenantContext context) throws PaymentApiException {
        final Payment payment = paymentProcessor.getPayment(paymentId, withPluginInfo, internalCallContextFactory.createInternalTenantContext(context));
        if (payment == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, paymentId);
        }
        return payment;
    }

    @Override
    public Pagination<Payment> searchPayments(final String searchKey, final Long offset, final Long limit, final TenantContext context) {
        return paymentProcessor.searchPayments(searchKey, offset, limit, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<Payment> searchPayments(final String searchKey, final Long offset, final Long limit, final String pluginName, final TenantContext context) throws PaymentApiException {
        return paymentProcessor.searchPayments(searchKey, offset, limit, pluginName, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public List<Payment> getInvoicePayments(final UUID invoiceId, final TenantContext context) {
        return paymentProcessor.getInvoicePayments(invoiceId, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public List<Payment> getAccountPayments(final UUID accountId, final TenantContext context)
            throws PaymentApiException {
        return paymentProcessor.getAccountPayments(accountId, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Refund getRefund(final UUID refundId, final boolean withPluginInfo, final TenantContext context) throws PaymentApiException {
        return refundProcessor.getRefund(refundId, withPluginInfo, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Refund createRefund(final Account account, final UUID paymentId, final BigDecimal refundAmount, final CallContext context) throws PaymentApiException {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentApiException(ErrorCode.PAYMENT_REFUND_AMOUNT_NEGATIVE_OR_NULL);
        }
        return refundProcessor.createRefund(account, paymentId, refundAmount, false, ImmutableMap.<UUID, BigDecimal>of(),
                                            internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public void notifyPendingRefundOfStateChanged(final Account account, final UUID refundId, final boolean isSuccess, final CallContext context) throws PaymentApiException {
        return refundProcessor.notifyPendingRefundOfStateChanged(account, refundId, isSuccess,
                                                                 internalCallContextFactory.createInternalCallContext(account.getId(), context);
    }

    @Override
    public Refund createRefundWithAdjustment(final Account account, final UUID paymentId, final BigDecimal refundAmount, final CallContext context) throws PaymentApiException {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentApiException(ErrorCode.PAYMENT_REFUND_AMOUNT_NEGATIVE_OR_NULL);
        }
        return refundProcessor.createRefund(account, paymentId, refundAmount, true, ImmutableMap.<UUID, BigDecimal>of(),
                                            internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public Refund createRefundWithItemsAdjustments(final Account account, final UUID paymentId, final Set<UUID> invoiceItemIds, final CallContext context) throws PaymentApiException {
        final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts = new HashMap<UUID, BigDecimal>();
        for (final UUID invoiceItemId : invoiceItemIds) {
            invoiceItemIdsWithAmounts.put(invoiceItemId, null);
        }

        return refundProcessor.createRefund(account, paymentId, null, true, invoiceItemIdsWithAmounts,
                                            internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public Refund createRefundWithItemsAdjustments(final Account account, final UUID paymentId, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, final CallContext context) throws PaymentApiException {
        return refundProcessor.createRefund(account, paymentId, null, true, invoiceItemIdsWithAmounts,
                                            internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public List<Refund> getAccountRefunds(final Account account, final TenantContext context)
            throws PaymentApiException {
        return refundProcessor.getAccountRefunds(account, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public List<Refund> getPaymentRefunds(final UUID paymentId, final TenantContext context)
            throws PaymentApiException {
        return refundProcessor.getPaymentRefunds(paymentId, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Set<String> getAvailablePlugins() {
        return methodProcessor.getAvailablePlugins();
    }

    @Override
    public UUID addPaymentMethod(final String pluginName, final Account account,
                                 final boolean setDefault, final PaymentMethodPlugin paymentMethodInfo, final CallContext context)
            throws PaymentApiException {
        return methodProcessor.addPaymentMethod(pluginName, account, setDefault, paymentMethodInfo,
                                                internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public List<PaymentMethod> getPaymentMethods(final Account account, final boolean withPluginInfo, final TenantContext context)
            throws PaymentApiException {
        return methodProcessor.getPaymentMethods(account, withPluginInfo, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public PaymentMethod getPaymentMethodById(final UUID paymentMethodId, final boolean includedDeleted, final boolean withPluginInfo, final TenantContext context)
            throws PaymentApiException {
        return methodProcessor.getPaymentMethodById(paymentMethodId, includedDeleted, withPluginInfo, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> getPaymentMethods(final Long offset, final Long limit, final TenantContext context) {
        return methodProcessor.getPaymentMethods(offset, limit, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> getPaymentMethods(final Long offset, final Long limit, final String pluginName, final TenantContext context) throws PaymentApiException {
        return methodProcessor.getPaymentMethods(offset, limit, pluginName, context, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final TenantContext context) {
        return methodProcessor.searchPaymentMethods(searchKey, offset, limit, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Pagination<PaymentMethod> searchPaymentMethods(final String searchKey, final Long offset, final Long limit, final String pluginName, final TenantContext context) throws PaymentApiException {
        return methodProcessor.searchPaymentMethods(searchKey, offset, limit, pluginName, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public void deletedPaymentMethod(final Account account, final UUID paymentMethodId, final boolean deleteDefaultPaymentMethodWithAutoPayOff, final CallContext context)
            throws PaymentApiException {
        methodProcessor.deletedPaymentMethod(account, paymentMethodId, deleteDefaultPaymentMethodWithAutoPayOff, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public void setDefaultPaymentMethod(final Account account, final UUID paymentMethodId, final CallContext context)
            throws PaymentApiException {
        methodProcessor.setDefaultPaymentMethod(account, paymentMethodId, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public List<PaymentMethod> refreshPaymentMethods(final String pluginName, final Account account, final CallContext context)
            throws PaymentApiException {
        return methodProcessor.refreshPaymentMethods(pluginName, account, internalCallContextFactory.createInternalCallContext(account.getId(), context));
    }

    @Override
    public List<PaymentMethod> refreshPaymentMethods(final Account account, final CallContext context)
            throws PaymentApiException {
        final InternalCallContext callContext = internalCallContextFactory.createInternalCallContext(account.getId(), context);

        final List<PaymentMethod> paymentMethods = new LinkedList<PaymentMethod>();
        for (final String pluginName : methodProcessor.getAvailablePlugins()) {
            paymentMethods.addAll(methodProcessor.refreshPaymentMethods(pluginName, account, callContext));
        }

        return paymentMethods;
    }
}
