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

package org.killbill.billing.invoice.provider;

import java.util.List;

import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.plugin.api.NoOpInvoicePluginApi;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.clock.Clock;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class DefaultNoOpInvoiceProviderPlugin implements NoOpInvoicePluginApi {

    private final Clock clock;

    @Inject
    public DefaultNoOpInvoiceProviderPlugin(final Clock clock) {
        this.clock = clock;
    }

    @Override
    public List<InvoiceItem> getAdditionalInvoiceItems(final Invoice invoice, final boolean isDryRun, final Iterable<PluginProperty> properties, CallContext context) {
        return ImmutableList.<InvoiceItem>of();
    }
}
