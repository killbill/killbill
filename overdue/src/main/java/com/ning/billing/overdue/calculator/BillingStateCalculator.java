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

import com.ning.billing.account.api.Account;
import com.ning.billing.clock.Clock;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.overdue.config.api.BillingState;
import com.ning.billing.overdue.config.api.OverdueException;
import com.ning.billing.overdue.config.api.PaymentResponse;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.svcapi.invoice.InvoiceInternalApi;
import com.ning.billing.util.tag.Tag;

import com.google.inject.Inject;

public class BillingStateCalculator {

    private final InvoiceInternalApi invoiceApi;
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
    public BillingStateCalculator(final InvoiceInternalApi invoiceApi, final Clock clock) {
        this.invoiceApi = invoiceApi;
        this.clock = clock;
    }

    public BillingState calculateBillingState(final Account account, final InternalTenantContext context) throws OverdueException {
        final SortedSet<Invoice> unpaidInvoices = unpaidInvoicesForAccount(account.getId(), account.getTimeZone(), context);

        final int numberOfUnpaidInvoices = unpaidInvoices.size();
        final BigDecimal unpaidInvoiceBalance = sumBalance(unpaidInvoices);
        LocalDate dateOfEarliestUnpaidInvoice = null;
        UUID idOfEarliestUnpaidInvoice = null;
        final Invoice invoice = earliest(unpaidInvoices);
        if (invoice != null) {
            dateOfEarliestUnpaidInvoice = invoice.getInvoiceDate();
            idOfEarliestUnpaidInvoice = invoice.getId();
        }
        final PaymentResponse responseForLastFailedPayment = PaymentResponse.INSUFFICIENT_FUNDS; //TODO MDW
        final Tag[] tags = new Tag[]{}; //TODO MDW


        return new BillingState(account.getId(), numberOfUnpaidInvoices, unpaidInvoiceBalance, dateOfEarliestUnpaidInvoice, account.getTimeZone(), idOfEarliestUnpaidInvoice, responseForLastFailedPayment, tags);
    }

    // Package scope for testing
    Invoice earliest(final SortedSet<Invoice> unpaidInvoices) {
        try {
            return unpaidInvoices.first();
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    BigDecimal sumBalance(final SortedSet<Invoice> unpaidInvoices) {
        BigDecimal sum = BigDecimal.ZERO;
        for (final Invoice unpaidInvoice : unpaidInvoices) {
            sum = sum.add(unpaidInvoice.getBalance());
        }
        return sum;
    }

    SortedSet<Invoice> unpaidInvoicesForAccount(final UUID accountId, final DateTimeZone accountTimeZone, final InternalTenantContext context) {
        final Collection<Invoice> invoices = invoiceApi.getUnpaidInvoicesByAccountId(accountId, clock.getToday(accountTimeZone), context);
        final SortedSet<Invoice> sortedInvoices = new TreeSet<Invoice>(new InvoiceDateComparator());
        sortedInvoices.addAll(invoices);
        return sortedInvoices;
    }
}
