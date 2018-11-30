/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.overdue.calculator;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.overdue.config.api.BillingState;
import org.killbill.billing.overdue.config.api.OverdueException;
import org.killbill.billing.payment.api.PaymentResponse;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.tag.Tag;
import org.killbill.clock.Clock;

import com.google.inject.Inject;

public class BillingStateCalculator {

    private final InvoiceInternalApi invoiceApi;
    private final TagInternalApi tagApi;
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
    public BillingStateCalculator(final InvoiceInternalApi invoiceApi, final Clock clock, final TagInternalApi tagApi) {
        this.invoiceApi = invoiceApi;
        this.clock = clock;
        this.tagApi = tagApi;
    }

    public BillingState calculateBillingState(final ImmutableAccountData account, final InternalCallContext context) throws OverdueException {
        final SortedSet<Invoice> unpaidInvoices = unpaidInvoicesForAccount(account.getId(), context);

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
        final List<Tag> accountTags = tagApi.getTags(account.getId(), ObjectType.ACCOUNT, context);
        final Tag[] tags = accountTags.toArray(new Tag[accountTags.size()]);

        return new BillingState(account.getId(), numberOfUnpaidInvoices, unpaidInvoiceBalance, dateOfEarliestUnpaidInvoice, idOfEarliestUnpaidInvoice, responseForLastFailedPayment, tags);
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

    SortedSet<Invoice> unpaidInvoicesForAccount(final UUID accountId, final InternalCallContext context) {
        final Collection<Invoice> invoices = invoiceApi.getUnpaidInvoicesByAccountId(accountId, context.toLocalDate(context.getCreatedDate()), context);
        final SortedSet<Invoice> sortedInvoices = new TreeSet<Invoice>(new InvoiceDateComparator());
        sortedInvoices.addAll(invoices);
        return sortedInvoices;
    }
}
