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

package com.ning.billing.osgi.bundles.test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentMethodInfoPlugin;
import com.ning.billing.payment.plugin.api.PaymentPluginApiException;
import com.ning.billing.payment.plugin.api.PaymentPluginApiWithTestControl;
import com.ning.billing.payment.plugin.api.RefundInfoPlugin;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

public class TestPaymentPluginApi implements PaymentPluginApiWithTestControl {

    private final String name;

    private PaymentPluginApiException paymentPluginApiExceptionOnNextCalls;
    private RuntimeException runtimeExceptionOnNextCalls;

    public TestPaymentPluginApi(final String name) {
        this.name = name;
        resetToNormalbehavior();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public PaymentInfoPlugin processPayment(final UUID kbPaymentId, final UUID kbPaymentMethodId, final BigDecimal amount, final CallContext context) throws PaymentPluginApiException {
        return withRuntimeCheckForExceptions(new PaymentInfoPlugin() {
            @Override
            public BigDecimal getAmount() {
                return amount;
            }
            @Override
            public DateTime getCreatedDate() {
                return new DateTime();
            }
            @Override
            public DateTime getEffectiveDate() {
                return new DateTime();
            }
            @Override
            public PaymentPluginStatus getStatus() {
                return PaymentPluginStatus.PROCESSED;
            }
            @Override
            public String getGatewayError() {
                return null;
            }
            @Override
            public String getGatewayErrorCode() {
                return null;
            }
        });
    }

    @Override
    public PaymentInfoPlugin getPaymentInfo(final UUID kbPaymentId, final TenantContext context) throws PaymentPluginApiException {

        final BigDecimal someAmount = new BigDecimal("12.45");
        return withRuntimeCheckForExceptions(new PaymentInfoPlugin() {
            @Override
            public BigDecimal getAmount() {
                return someAmount;
            }
            @Override
            public DateTime getCreatedDate() {
                return new DateTime();
            }
            @Override
            public DateTime getEffectiveDate() {
                return new DateTime();
            }
            @Override
            public PaymentPluginStatus getStatus() {
                return PaymentPluginStatus.PROCESSED;
            }
            @Override
            public String getGatewayError() {
                return null;
            }
            @Override
            public String getGatewayErrorCode() {
                return null;
            }
        });
    }

    @Override
    public RefundInfoPlugin processRefund(final UUID kbPaymentId, final BigDecimal refundAmount, final CallContext context) throws PaymentPluginApiException {

        final BigDecimal someAmount = new BigDecimal("12.45");
        return withRuntimeCheckForExceptions(new RefundInfoPlugin() {
            @Override
            public BigDecimal getAmount() {
                return null;
            }
            @Override
            public DateTime getCreatedDate() {
                return null;
            }
            @Override
            public DateTime getEffectiveDate() {
                return null;
            }
            @Override
            public RefundPluginStatus getStatus() {
                return null;
            }
            @Override
            public String getGatewayError() {
                return null;
            }
            @Override
            public String getGatewayErrorCode() {
                return null;
            }
        });
    }

    @Override
    public void addPaymentMethod(final UUID kbAccountId, final UUID kbPaymentMethodId, final PaymentMethodPlugin paymentMethodProps, final boolean setDefault, final CallContext context) throws PaymentPluginApiException {
    }

    @Override
    public void deletePaymentMethod(final UUID kbPaymentMethodId, final CallContext context) throws PaymentPluginApiException {
    }

    @Override
    public PaymentMethodPlugin getPaymentMethodDetail(final UUID kbAccountId, final UUID kbPaymentMethodId, final TenantContext context) throws PaymentPluginApiException {
        return null;
    }

    @Override
    public void setDefaultPaymentMethod(final UUID kbPaymentMethodId, final CallContext context) throws PaymentPluginApiException {
    }

    @Override
    public List<PaymentMethodInfoPlugin> getPaymentMethods(final UUID kbAccountId, final boolean refreshFromGateway, final CallContext context) throws PaymentPluginApiException {
        return null;
    }

    @Override
    public void resetPaymentMethods(final List<PaymentMethodInfoPlugin> paymentMethods) throws PaymentPluginApiException {
    }


    private <T> T withRuntimeCheckForExceptions(final T result) throws PaymentPluginApiException {
        if (paymentPluginApiExceptionOnNextCalls != null) {
            throw paymentPluginApiExceptionOnNextCalls;

        } else if (runtimeExceptionOnNextCalls != null) {
            throw runtimeExceptionOnNextCalls;
        } else {
            return result;
        }
    }

    @Override
    public void setPaymentPluginApiExceptionOnNextCalls(final PaymentPluginApiException e) {
        resetToNormalbehavior();
        paymentPluginApiExceptionOnNextCalls = e;
    }

    @Override
    public void setPaymentRuntimeExceptionOnNextCalls(final RuntimeException e) {
        resetToNormalbehavior();
        runtimeExceptionOnNextCalls = e;
    }

    @Override
    public void resetToNormalbehavior() {
        paymentPluginApiExceptionOnNextCalls = null;
        runtimeExceptionOnNextCalls = null;
    }
}
