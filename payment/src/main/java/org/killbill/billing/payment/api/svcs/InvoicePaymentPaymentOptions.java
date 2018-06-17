/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.payment.api.svcs;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.killbill.billing.payment.api.PaymentOptions;
import org.killbill.billing.payment.invoice.InvoicePaymentControlPluginApi;

public class InvoicePaymentPaymentOptions implements PaymentOptions {

    private final boolean isExternalPayment;
    private final List<String> paymentControlPluginNames;

    public InvoicePaymentPaymentOptions(final boolean isExternalPayment, final List<String> getPaymentControlPluginNames) {
        this.isExternalPayment = isExternalPayment;
        this.paymentControlPluginNames = getPaymentControlPluginNames;
    }

    public static InvoicePaymentPaymentOptions create(final PaymentOptions paymentOptions) {
        final List<String> controlPluginNamesFromUser = paymentOptions.getPaymentControlPluginNames();
        final List<String> paymentControlPluginNames = addInvoicePaymentControlPlugin(controlPluginNamesFromUser);
        return new InvoicePaymentPaymentOptions(paymentOptions.isExternalPayment(), paymentControlPluginNames);
    }

    public static List<String> addInvoicePaymentControlPlugin(final Collection<String> controlPluginNamesFromUser) {
        final List<String> paymentControlPluginNames = new LinkedList<String>();
        paymentControlPluginNames.addAll(controlPluginNamesFromUser);
        if (!paymentControlPluginNames.contains(InvoicePaymentControlPluginApi.PLUGIN_NAME)) {
            paymentControlPluginNames.add(InvoicePaymentControlPluginApi.PLUGIN_NAME);
        }
        return paymentControlPluginNames;
    }

    @Override
    public boolean isExternalPayment() {
        return isExternalPayment;
    }

    @Override
    public List<String> getPaymentControlPluginNames() {
        return paymentControlPluginNames;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("InvoicePaymentPaymentOptions{");
        sb.append("isExternalPayment=").append(isExternalPayment);
        sb.append(", paymentControlPluginNames=").append(paymentControlPluginNames);
        sb.append('}');
        return sb.toString();
    }
}
