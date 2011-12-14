/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.invoice.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ning.billing.catalog.api.Currency;

public class MockInvoicePaymentApi implements InvoicePaymentApi
{
    private final CopyOnWriteArrayList<Invoice> invoices = new CopyOnWriteArrayList<Invoice>();

    public void add(Invoice invoice) {
        invoices.add(invoice);
    }

    @Override
    public void paymentSuccessful(UUID invoiceId, BigDecimal amount, Currency currency, UUID paymentId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Invoice> getInvoicesByAccount(UUID accountId) {
        ArrayList<Invoice> result = new ArrayList<Invoice>();

        for (Invoice invoice : invoices) {
            if (accountId.equals(invoice.getAccountId())) {
                result.add(invoice);
            }
        }
        return result;
    }

    @Override
    public Invoice getInvoice(UUID invoiceId) {
        for (Invoice invoice : invoices) {
            if (invoiceId.equals(invoice.getId())) {
                return invoice;
            }
        }
        return null;
    }
}
