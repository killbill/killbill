/*
 * Copyright 2014 Groupon, Inc
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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

import org.joda.time.DateTime;
import org.killbill.billing.payment.retry.DefaultFailureCallResult;
import org.killbill.billing.payment.retry.DefaultPriorPaymentControlResult;
import org.killbill.billing.retry.plugin.api.FailureCallResult;
import org.killbill.billing.retry.plugin.api.PaymentControlApiException;
import org.killbill.billing.retry.plugin.api.PaymentControlContext;
import org.killbill.billing.retry.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.retry.plugin.api.PriorPaymentControlResult;

public class DefaultNoOpPaymentControlProviderPlugin implements PaymentControlPluginApi {

    private PaymentControlApiException paymentControlPluginApiException;
    private boolean isRetryAborted;
    private DateTime nextRetryDate;

    @Override
    public PriorPaymentControlResult priorCall(final PaymentControlContext retryPluginContext) throws PaymentControlApiException {
        return new DefaultPriorPaymentControlResult(isRetryAborted, null);
    }

    @Override
    public void onSuccessCall(final PaymentControlContext paymentControlContext) throws PaymentControlApiException {
    }

    @Override
    public FailureCallResult onFailureCall(final PaymentControlContext paymentControlContext) throws PaymentControlApiException {
        return new DefaultFailureCallResult(nextRetryDate);
    }

    public DefaultNoOpPaymentControlProviderPlugin setPaymentControlPluginApiException(final PaymentControlApiException paymentControlPluginApiException) {
        this.paymentControlPluginApiException = paymentControlPluginApiException;
        return this;
    }

    public DefaultNoOpPaymentControlProviderPlugin setRetryAborted(final boolean isRetryAborted) {
        this.isRetryAborted = isRetryAborted;
        return this;
    }

    public DefaultNoOpPaymentControlProviderPlugin setNextRetryDate(final DateTime nextRetryDate) {
        this.nextRetryDate = nextRetryDate;
        return this;
    }
}
