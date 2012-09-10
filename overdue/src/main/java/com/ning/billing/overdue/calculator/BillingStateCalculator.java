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

package com.ning.billing.overdue.calculator;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.overdue.config.api.BillingState;
import com.ning.billing.overdue.config.api.OverdueException;
import com.ning.billing.util.clock.Clock;

import com.google.inject.Inject;

public abstract class BillingStateCalculator<T extends Blockable> {

    private final InvoiceUserApi invoiceApi;
    private final Clock clock;

    protected class InvoiceDateComparator implements Comparator<Invoice> {

        @Override
        public int compare(final Invoice i1, final Invoice i2) {
            final LocalDate d1 = i1.getInvoiceDate();
            final LocalDate d2 = i2.getInvoiceDate();
            if (d1.compareTo(d2) == 0) {
                return i1.hashCode() - i2.hashCode(); // consistent (arbitrary) resolution for tied dates
            }
            return d1.compareTo(d2);
        }
    }

    @Inject
    public BillingStateCalculator(final InvoiceUserApi invoiceApi, final Clock clock) {
        this.invoiceApi = invoiceApi;
        this.clock = clock;
    }

    public abstract BillingState<T> calculateBillingState(T overdueable) throws OverdueException;

    protected Invoice earliest(final SortedSet<Invoice> unpaidInvoices) {
        try {
            return unpaidInvoices.first();
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    protected BigDecimal sumBalance(final SortedSet<Invoice> unpaidInvoices) {
        BigDecimal sum = BigDecimal.ZERO;
        for (final Invoice unpaidInvoice : unpaidInvoices) {
            sum = sum.add(unpaidInvoice.getBalance());
        }
        return sum;
    }

    protected SortedSet<Invoice> unpaidInvoicesForAccount(final UUID accountId, final DateTimeZone accountTimeZone) {
        final Collection<Invoice> invoices = invoiceApi.getUnpaidInvoicesByAccountId(accountId, clock.getToday(accountTimeZone));
        final SortedSet<Invoice> sortedInvoices = new TreeSet<Invoice>(new InvoiceDateComparator());
        sortedInvoices.addAll(invoices);
        return sortedInvoices;
    }
}
