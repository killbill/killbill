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

package org.killbill.billing.invoice.provider;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.plugin.api.AdditionalItemsResult;
import org.killbill.billing.invoice.plugin.api.InvoiceContext;
import org.killbill.billing.invoice.plugin.api.InvoiceGroupingResult;
import org.killbill.billing.invoice.plugin.api.NoOpInvoicePluginApi;
import org.killbill.billing.invoice.plugin.api.OnFailureInvoiceResult;
import org.killbill.billing.invoice.plugin.api.OnSuccessInvoiceResult;
import org.killbill.billing.invoice.plugin.api.PriorInvoiceResult;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.callcontext.CallContext;

public class DefaultNoOpInvoiceProviderPlugin implements NoOpInvoicePluginApi {

    @Inject
    public DefaultNoOpInvoiceProviderPlugin() {
    }

    @Override
    public PriorInvoiceResult priorCall(final InvoiceContext invoiceContext, final Iterable<PluginProperty> properties) {
        return null;
    }

    @Override
    public AdditionalItemsResult getAdditionalInvoiceItems(final Invoice invoice, final boolean isDryRun, final Iterable<PluginProperty> properties, final InvoiceContext context) {
        return null;
    }

    @Override
    public InvoiceGroupingResult getInvoiceGrouping(final Invoice invoice, final boolean dryRun, final Iterable<PluginProperty> properties, final InvoiceContext context) {
        return null;
    }

    @Override
    public OnSuccessInvoiceResult onSuccessCall(final InvoiceContext invoiceContext, final Iterable<PluginProperty> properties) {
        return null;
    }

    @Override
    public OnFailureInvoiceResult onFailureCall(final InvoiceContext invoiceContext, final Iterable<PluginProperty> properties) {
        return null;
    }
}
