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

package com.ning.billing.payment.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.ning.billing.account.api.Account;
import com.ning.billing.util.callcontext.CallContext;

public interface PaymentApi {

    public Payment createPayment(final Account account, final UUID invoiceId, final BigDecimal amount, final CallContext context)
            throws PaymentApiException;

    public Payment createExternalPayment(final Account account, final UUID invoiceId, final BigDecimal amount, final CallContext context)
            throws PaymentApiException;

    public Refund getRefund(final UUID refundId)
            throws PaymentApiException;

    /**
     * Create a refund for a given payment. The associated invoice is not adjusted.
     *
     * @param account      account to refund
     * @param paymentId    payment associated with that refund
     * @param refundAmount amount to refund
     * @param context      the call context
     * @return the created Refund
     * @throws PaymentApiException
     */
    public Refund createRefund(final Account account, final UUID paymentId, final BigDecimal refundAmount, final CallContext context)
            throws PaymentApiException;

    /**
     * Create a refund for a given payment. The associated invoice is adjusted.
     *
     * @param account      account to refund
     * @param paymentId    payment associated with that refund
     * @param refundAmount amount to refund
     * @param context      the call context
     * @return the created Refund
     * @throws PaymentApiException
     */
    public Refund createRefundWithAdjustment(final Account account, final UUID paymentId, final BigDecimal refundAmount, final CallContext context)
            throws PaymentApiException;

    /**
     * Create a refund for a given payment. The specified invoice items are fully adjusted.
     * The refund amount will be the sum of all invoice items amounts.
     *
     * @param account        account to refund
     * @param paymentId      payment associated with that refund
     * @param invoiceItemIds invoice item ids to adjust
     * @param context        the call context
     * @return the created Refund
     * @throws PaymentApiException
     */
    public Refund createRefundWithItemsAdjustments(final Account account, final UUID paymentId, final Set<UUID> invoiceItemIds, final CallContext context)
            throws PaymentApiException;

    /**
     * Create a refund for a given payment. The specified invoice items are partially adjusted.
     * The refund amount will be the sum of all adjustments.
     *
     * @param account                   account to refund
     * @param paymentId                 payment associated with that refund
     * @param invoiceItemIdsWithAmounts invoice item ids and associated amounts to adjust
     * @param context                   the call context
     * @return the created Refund
     * @throws PaymentApiException
     */
    public Refund createRefundWithItemsAdjustments(final Account account, final UUID paymentId, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, final CallContext context)
            throws PaymentApiException;

    public List<Refund> getAccountRefunds(final Account account)
            throws PaymentApiException;

    public List<Refund> getPaymentRefunds(final UUID paymentId)
            throws PaymentApiException;

    public List<Payment> getInvoicePayments(final UUID invoiceId)
            throws PaymentApiException;

    public List<Payment> getAccountPayments(final UUID accountId)
            throws PaymentApiException;

    public Payment getPayment(final UUID paymentId)
            throws PaymentApiException;

    /*
     * Payment method Apis
     */
    public Set<String> getAvailablePlugins();

    public String initializeAccountPlugin(final String pluginName, final Account account)
            throws PaymentApiException;

    public UUID addPaymentMethod(final String pluginName, final Account account, boolean setDefault, final PaymentMethodPlugin paymentMethodInfo, final CallContext context)
            throws PaymentApiException;

    public List<PaymentMethod> refreshPaymentMethods(final String pluginName, final Account account, final CallContext context)
            throws PaymentApiException;

    public List<PaymentMethod> getPaymentMethods(final Account account, final boolean withPluginDetail)
            throws PaymentApiException;

    public PaymentMethod getPaymentMethodById(final UUID paymentMethodId)
            throws PaymentApiException;

    public PaymentMethod getPaymentMethod(final Account account, final UUID paymentMethodId, final boolean withPluginDetail)
            throws PaymentApiException;

    public void updatePaymentMethod(final Account account, final UUID paymentMethodId, final PaymentMethodPlugin paymentMetghodInfo)
            throws PaymentApiException;

    public void deletedPaymentMethod(final Account account, final UUID paymentMethodId, final boolean deleteDefaultPaymentMethodWithAutoPayOff, final CallContext context)
            throws PaymentApiException;

    public void setDefaultPaymentMethod(final Account account, final UUID paymentMethodId, final CallContext context)
            throws PaymentApiException;

}
