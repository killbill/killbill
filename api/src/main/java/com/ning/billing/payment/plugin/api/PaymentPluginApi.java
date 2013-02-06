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

package com.ning.billing.payment.plugin.api;

import java.math.BigDecimal;
import java.util.UUID;

import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

public interface PaymentPluginApi {

    /**
     * @return plugin name
     */
    public String getName();

    /**
     * Charge a specific amount in the Gateway. Required.
     *
     * @param pluginPaymentMethodKey payment method key to charge
     * @param kbPaymentId            killbill payment id (for reference)
     * @param amount                 amount to charge
     * @param context                call context
     * @return information about the payment in the gateway
     * @throws PaymentPluginApiException
     */
    public PaymentInfoPlugin processPayment(String pluginPaymentMethodKey, UUID kbPaymentId, BigDecimal amount, CallContext context)
            throws PaymentPluginApiException;

    /**
     * Retrieve information about a given payment. Optional (not all gateways will support it).
     *
     *
     * @param kbPaymentId      killbill payment id (for reference)
     * @param context          call context
     * @return information about the payment in the gateway
     * @throws PaymentPluginApiException
     */
    public PaymentInfoPlugin getPaymentInfo(UUID kbPaymentId, TenantContext context)
            throws PaymentPluginApiException;

    /**
     * Process a refund against a given payment. Required.
     *
     *
     * @param kbPaymentId      killbill payment id (for reference)
     * @param refundAmount     call context
     * @param context          call context
     * @return information about the refund in the gateway
     * @throws PaymentPluginApiException
     */
    public RefundInfoPlugin processRefund(UUID kbPaymentId, BigDecimal refundAmount, CallContext context)
            throws PaymentPluginApiException;

    /**
     * Add a payment method for a Killbill account in the gateway. Optional.
     *
     * @param paymentMethodProps payment method details
     * @param kbAccountId        killbill account id
     * @param setDefault         set it as the default payment method in the gateway
     * @param context            call context
     * @return payment method key in the gateway
     * @throws PaymentPluginApiException
     */
    public String addPaymentMethod(PaymentMethodPlugin paymentMethodProps, UUID kbAccountId, boolean setDefault, CallContext context)
            throws PaymentPluginApiException;

    /**
     * Delete a payment method in the gateway. Optional.
     *
     * @param pluginPaymentMethodKey payment method key to delete
     * @param kbAccountId            killbill account id
     * @param context                call context
     * @throws PaymentPluginApiException
     */
    public void deletePaymentMethod(String pluginPaymentMethodKey, UUID kbAccountId, CallContext context)
            throws PaymentPluginApiException;

    /**
     * Set a payment method as default in the gateway. Optional.
     *
     * @param pluginPaymentMethodKey payment method key to update
     * @param kbAccountId            killbill account id
     * @param context                call context
     * @throws PaymentPluginApiException
     */
    public void setDefaultPaymentMethod(String pluginPaymentMethodKey, UUID kbAccountId, CallContext context)
            throws PaymentPluginApiException;
}
