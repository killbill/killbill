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

package org.killbill.billing.invoice.api;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.killbill.billing.util.callcontext.TenantContext;

public class MockInvoicePaymentApi implements InvoicePaymentApi {

    private final CopyOnWriteArrayList<Invoice> invoices = new CopyOnWriteArrayList<Invoice>();
    private final CopyOnWriteArrayList<InvoicePayment> invoicePayments = new CopyOnWriteArrayList<InvoicePayment>();

    public void add(final Invoice invoice) {
        invoices.add(invoice);
    }

    @Override
    public List<InvoicePayment> getInvoicePayments(final UUID paymentId, final TenantContext context) {
        final List<InvoicePayment> result = new LinkedList<InvoicePayment>();
        for (final InvoicePayment invoicePayment : invoicePayments) {
            if (paymentId.equals(invoicePayment.getPaymentId())) {
                result.add(invoicePayment);
            }
        }
        return result;
    }

    @Override
    public List<InvoicePayment> getInvoicePaymentsByAccount(final UUID uuid, final TenantContext tenantContext) {
        throw new UnsupportedOperationException();
    }
}
