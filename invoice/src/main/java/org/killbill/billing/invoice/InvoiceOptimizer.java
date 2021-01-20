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

package org.killbill.billing.invoice;

import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.clock.Clock;

import com.google.common.collect.ImmutableList;

public class InvoiceOptimizer {

    private static final Period DEFAULT_INVOICE_CUTOFF_DATE = new Period(InvoiceConfig.INVOICE_CUTOFF_DATE);
    private final Clock clock;
    private final InvoiceDao invoiceDao;
    private final InvoiceConfig invoiceConfig;

    @Inject
    public InvoiceOptimizer(final InvoiceDao invoiceDao,
                            final Clock clock,
                            final InvoiceConfig invoiceConfig) {

        this.invoiceDao = invoiceDao;
        this.clock = clock;
        this.invoiceConfig = invoiceConfig;
    }

    public AccountInvoices getInvoices(final InternalCallContext callContext) {

        final Period maxInvoiceLimit = invoiceConfig.getMaxInvoiceLimit(callContext);
        final LocalDate fromDate = maxInvoiceLimit.equals(DEFAULT_INVOICE_CUTOFF_DATE)  ?
                                   null :
                                   callContext.toLocalDate(clock.getUTCNow()).minus(maxInvoiceLimit);

        final List<Invoice> existingInvoices = new LinkedList<Invoice>();
        final List<InvoiceModelDao> invoicesByAccount = fromDate != null ?
                                                        invoiceDao.getInvoicesByAccount(false, fromDate, null, callContext) :
                                                        invoiceDao.getInvoicesByAccount(false, callContext);
        for (final InvoiceModelDao invoiceModelDao : invoicesByAccount) {
            existingInvoices.add(new DefaultInvoice(invoiceModelDao));
        }
        return new AccountInvoices(fromDate, existingInvoices);
    }

    public static class AccountInvoices {

        private final LocalDate cutoffDate;
        private final List<Invoice> invoices;

        public AccountInvoices(final LocalDate cutoffDate, final List<Invoice> invoices) {
            this.cutoffDate = cutoffDate;
            this.invoices = invoices;
        }

        public AccountInvoices() {
            this(null, ImmutableList.of());
        }

        public boolean hasAllInvoices() {
            return cutoffDate == null;
        }

        public LocalDate getCutoffDate() {
            return cutoffDate;
        }

        public List<Invoice> getInvoices() {
            return invoices;
        }
    }
}
