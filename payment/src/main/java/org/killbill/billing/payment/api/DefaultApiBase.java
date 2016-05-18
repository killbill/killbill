/*
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

package org.killbill.billing.payment.api;

import java.util.LinkedList;
import java.util.List;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.payment.invoice.InvoicePaymentControlPluginApi;
import org.killbill.billing.util.config.PaymentConfig;

import com.google.common.collect.ImmutableList;

public class DefaultApiBase {

    private final PaymentConfig paymentConfig;

    public DefaultApiBase(final PaymentConfig paymentConfig) {
        this.paymentConfig = paymentConfig;
    }

    protected List<String> toPaymentControlPluginNames(final PaymentOptions paymentOptions) {
        // Special path for JAX-RS InvoicePayment endpoints (see JaxRsResourceBase)
        if (paymentConfig.getPaymentControlPluginNames() != null &&
            paymentOptions.getPaymentControlPluginNames() != null &&
            paymentOptions.getPaymentControlPluginNames().size() == 1 &&
            InvoicePaymentControlPluginApi.PLUGIN_NAME.equals(paymentOptions.getPaymentControlPluginNames().get(0))) {
            final List<String> paymentControlPluginNames = new LinkedList<String>(paymentOptions.getPaymentControlPluginNames());
            paymentControlPluginNames.addAll(paymentConfig.getPaymentControlPluginNames());
            return paymentControlPluginNames;
        } else if (paymentOptions.getPaymentControlPluginNames() != null && !paymentOptions.getPaymentControlPluginNames().isEmpty()) {
            return paymentOptions.getPaymentControlPluginNames();
        } else if (paymentConfig.getPaymentControlPluginNames() != null && !paymentConfig.getPaymentControlPluginNames().isEmpty()) {
            return paymentConfig.getPaymentControlPluginNames();
        } else {
            return ImmutableList.<String>of();
        }
    }

    protected void checkNotNullParameter(final Object parameter, final String parameterName) throws PaymentApiException {
        if (parameter == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_PARAMETER, parameterName, "should not be null");
        }
    }
}
