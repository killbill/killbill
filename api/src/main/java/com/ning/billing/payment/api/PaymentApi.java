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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.ning.billing.account.api.Account;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

public interface PaymentApi {

    public Payment createPayment(Account account, UUID invoiceId, BigDecimal amount, CallContext context)
            throws PaymentApiException;

    public Payment createExternalPayment(Account account, UUID invoiceId, BigDecimal amount, CallContext context)
            throws PaymentApiException;

    public Refund getRefund(UUID refundId, TenantContext context)
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
    public Refund createRefund(Account account, UUID paymentId, BigDecimal refundAmount, CallContext context)
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
    public Refund createRefundWithAdjustment(Account account, UUID paymentId, BigDecimal refundAmount, CallContext context)
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
    public Refund createRefundWithItemsAdjustments(Account account, UUID paymentId, Set<UUID> invoiceItemIds, CallContext context)
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
    public Refund createRefundWithItemsAdjustments(Account account, UUID paymentId, Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, CallContext context)
            throws PaymentApiException;

    public List<Refund> getAccountRefunds(Account account, TenantContext context)
            throws PaymentApiException;

    public List<Refund> getPaymentRefunds(UUID paymentId, TenantContext context)
            throws PaymentApiException;

    public List<Payment> getInvoicePayments(UUID invoiceId, TenantContext context)
            throws PaymentApiException;

    public List<Payment> getAccountPayments(UUID accountId, TenantContext context)
            throws PaymentApiException;

    public Payment getPayment(UUID paymentId, TenantContext context)
            throws PaymentApiException;

    /*
     * Payment method Apis
     */
    public Set<String> getAvailablePlugins();

    public String initializeAccountPlugin(String pluginName, Account account, CallContext context)
            throws PaymentApiException;

    public UUID addPaymentMethod(String pluginName, Account account, boolean setDefault, PaymentMethodPlugin paymentMethodInfo, CallContext context)
            throws PaymentApiException;

    public List<PaymentMethod> refreshPaymentMethods(String pluginName, Account account, CallContext context)
            throws PaymentApiException;

    public List<PaymentMethod> getPaymentMethods(Account account, boolean withPluginDetail, TenantContext context)
            throws PaymentApiException;

    public PaymentMethod getPaymentMethodById(UUID paymentMethodId, TenantContext context)
            throws PaymentApiException;

    public PaymentMethod getPaymentMethod(Account account, UUID paymentMethodId, boolean withPluginDetail, TenantContext context)
            throws PaymentApiException;

    public void updatePaymentMethod(Account account, UUID paymentMethodId, PaymentMethodPlugin paymentMethodInfo, CallContext context)
            throws PaymentApiException;

    public void deletedPaymentMethod(Account account, UUID paymentMethodId, boolean deleteDefaultPaymentMethodWithAutoPayOff, CallContext context)
            throws PaymentApiException;

    public void setDefaultPaymentMethod(Account account, UUID paymentMethodId, CallContext context)
            throws PaymentApiException;

}
