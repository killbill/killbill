/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import org.joda.time.DateTime;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.retry.DefaultFailureCallResult;
import org.killbill.billing.payment.retry.DefaultOnSuccessPaymentControlResult;
import org.killbill.billing.payment.retry.DefaultPriorPaymentControlResult;
import org.killbill.billing.control.plugin.api.OnFailurePaymentControlResult;
import org.killbill.billing.control.plugin.api.OnSuccessPaymentControlResult;
import org.killbill.billing.control.plugin.api.PaymentControlApiException;
import org.killbill.billing.control.plugin.api.PaymentControlContext;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.control.plugin.api.PriorPaymentControlResult;

public class MockPaymentControlProviderPlugin implements PaymentControlPluginApi {

    public static final String PLUGIN_NAME = "MOCK_RETRY_PLUGIN";

    private boolean isAborted;
    private DateTime nextRetryDate;

    public MockPaymentControlProviderPlugin setAborted(final boolean isAborted) {
        this.isAborted = isAborted;
        return this;
    }

    public MockPaymentControlProviderPlugin setNextRetryDate(final DateTime nextRetryDate) {
        this.nextRetryDate = nextRetryDate;
        return this;
    }

    @Override
    public PriorPaymentControlResult priorCall(final PaymentControlContext paymentControlContext, final Iterable<PluginProperty> properties) throws PaymentControlApiException {
        return new DefaultPriorPaymentControlResult(isAborted);
    }

    @Override
    public OnSuccessPaymentControlResult onSuccessCall(final PaymentControlContext paymentControlContext, final Iterable<PluginProperty> properties) throws PaymentControlApiException {
        return new DefaultOnSuccessPaymentControlResult();
    }

    @Override
    public OnFailurePaymentControlResult onFailureCall(final PaymentControlContext paymentControlContext, final Iterable<PluginProperty> properties) throws PaymentControlApiException {
        return new DefaultFailureCallResult(nextRetryDate);
    }
}
