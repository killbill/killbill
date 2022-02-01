/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

package org.killbill.billing.invoice.optimizer;

import java.util.List;

import javax.inject.Inject;

import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.clock.Clock;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public abstract class InvoiceOptimizerBase implements InvoiceOptimizer {

    protected final Clock clock;
    protected final InvoiceDao invoiceDao;
    protected final InvoiceConfig invoiceConfig;

    @Inject
    public InvoiceOptimizerBase(final InvoiceDao invoiceDao,
                                final Clock clock,
                                final InvoiceConfig invoiceConfig) {

        this.invoiceDao = invoiceDao;
        this.clock = clock;
        this.invoiceConfig = invoiceConfig;
    }

    public static class AccountInvoices {

        protected final LocalDate cutoffDate;
        protected final LocalDate beCutoffDate;
        protected final List<Invoice> invoices;

        @VisibleForTesting
        public AccountInvoices(final LocalDate cutoffDate, final LocalDate beCutoffDate, final List<Invoice> invoices) {
            this.cutoffDate = cutoffDate;
            this.invoices = invoices;
            this.beCutoffDate = beCutoffDate;
        }

        public AccountInvoices() {
            this(null, null, ImmutableList.of());
        }

        public LocalDate getCutoffDate() {
            return cutoffDate;
        }

        public LocalDate getBillingEventCutoffDate() {
            return beCutoffDate;
        }

        public List<Invoice> getInvoices() {
            return invoices;
        }

        // Default noop
        public void filterProposedItems(final List<InvoiceItem> proposedItems, final BillingEventSet eventSet, final InternalCallContext internalCallContext) {
        }
    }
}
