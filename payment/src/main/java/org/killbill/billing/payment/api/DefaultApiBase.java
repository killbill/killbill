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
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.payment.invoice.InvoicePaymentControlPluginApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.config.definition.PaymentConfig;

import com.google.common.collect.ImmutableList;

public class DefaultApiBase {

    private final PaymentConfig paymentConfig;
    protected final InternalCallContextFactory internalCallContextFactory;

    public DefaultApiBase(final PaymentConfig paymentConfig, final InternalCallContextFactory internalCallContextFactory) {
        this.paymentConfig = paymentConfig;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    protected List<String> toPaymentControlPluginNames(final PaymentOptions paymentOptions, final TenantContext callContext) {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(callContext);

        // Special path for JAX-RS InvoicePayment endpoints (see JaxRsResourceBase)
        final List<String> controlPluginNames = paymentConfig.getPaymentControlPluginNames(internalTenantContext);
        if (controlPluginNames != null &&
            paymentOptions.getPaymentControlPluginNames() != null &&
            paymentOptions.getPaymentControlPluginNames().isEmpty()) {
            final List<String> paymentControlPluginNames = new LinkedList<String>(paymentOptions.getPaymentControlPluginNames());
            paymentControlPluginNames.addAll(controlPluginNames);
            return paymentControlPluginNames;
        } else if (paymentOptions.getPaymentControlPluginNames() != null && !paymentOptions.getPaymentControlPluginNames().isEmpty()) {
            return paymentOptions.getPaymentControlPluginNames();
        } else if (controlPluginNames != null && !controlPluginNames.isEmpty()) {
            return controlPluginNames;
        } else {
            return ImmutableList.<String>of();
        }
    }

    protected void checkNotNullParameter(final Object parameter, final String parameterName) throws PaymentApiException {
        if (parameter == null) {
            throw new PaymentApiException(ErrorCode.PAYMENT_INVALID_PARAMETER, parameterName, "should not be null");
        }
    }

    protected void checkExternalKeyLength(final String externalKey) throws PaymentApiException {
        if (null != externalKey && externalKey.length() > 255) {
            throw new PaymentApiException(ErrorCode.EXTERNAL_KEY_LIMIT_EXCEEDED);
        }
    }
}
