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
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.retry.DefaultFailureCallResult;
import org.killbill.billing.payment.retry.DefaultPriorPaymentRoutingResult;
import org.killbill.billing.routing.plugin.api.OnFailurePaymentRoutingResult;
import org.killbill.billing.routing.plugin.api.OnSuccessPaymentRoutingResult;
import org.killbill.billing.routing.plugin.api.PaymentRoutingApiException;
import org.killbill.billing.routing.plugin.api.PaymentRoutingContext;
import org.killbill.billing.routing.plugin.api.PaymentRoutingPluginApi;
import org.killbill.billing.routing.plugin.api.PriorPaymentRoutingResult;

public class MockPaymentRoutingProviderPlugin implements PaymentRoutingPluginApi {

    public static final String PLUGIN_NAME = "MOCK_RETRY_PLUGIN";

    private boolean isAborted;
    private DateTime nextRetryDate;

    public MockPaymentRoutingProviderPlugin setAborted(final boolean isAborted) {
        this.isAborted = isAborted;
        return this;
    }

    public MockPaymentRoutingProviderPlugin setNextRetryDate(final DateTime nextRetryDate) {
        this.nextRetryDate = nextRetryDate;
        return this;
    }

    @Override
    public PriorPaymentRoutingResult priorCall(final PaymentRoutingContext paymentControlContext, Iterable<PluginProperty> properties) throws PaymentRoutingApiException {
        return new DefaultPriorPaymentRoutingResult(isAborted, null, null, null);
    }

    @Override
    public OnSuccessPaymentRoutingResult onSuccessCall(final PaymentRoutingContext paymentControlContext, Iterable<PluginProperty> properties) throws PaymentRoutingApiException {
        return null;
    }

    @Override
    public OnFailurePaymentRoutingResult onFailureCall(final PaymentRoutingContext paymentControlContext, Iterable<PluginProperty> properties) throws PaymentRoutingApiException {
        return new DefaultFailureCallResult(nextRetryDate);
    }
}
