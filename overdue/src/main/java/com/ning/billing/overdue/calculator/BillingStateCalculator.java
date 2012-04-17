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
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.overdue.config.api.BillingState;
import com.ning.billing.overdue.config.api.Overdueable;
import com.ning.billing.util.clock.Clock;

public abstract class BillingStateCalculator<T extends Overdueable> {

    private final InvoiceUserApi invoiceApi;
    private final Clock clock;
    
    protected class InvoiceDateComparator implements Comparator<Invoice> {
        @Override
        public int compare(Invoice i1, Invoice i2) {
            DateTime d1 = i1.getInvoiceDate();
            DateTime d2 = i2.getInvoiceDate();
            if(d1.compareTo(d2) == 0) {
                return i1.hashCode() - i2.hashCode(); // consistent (arbitrary) resolution for tied dates
            }
            return d1.compareTo(d2);
        }
    }

    @Inject 
    public BillingStateCalculator(InvoiceUserApi invoiceApi, Clock clock) {
        this.invoiceApi = invoiceApi;
        this.clock = clock;
    }
    
    public abstract BillingState<T> calculateBillingState(T overdueable);
    
    protected DateTime earliest(SortedSet<Invoice> unpaidInvoices) {
        return unpaidInvoices.first().getInvoiceDate();
    }

    protected BigDecimal sumBalance(SortedSet<Invoice> unpaidInvoices) {
        BigDecimal sum = BigDecimal.ZERO;
        Iterator<Invoice> it = unpaidInvoices.iterator();
        while(it.hasNext()) {
            sum = sum.add(it.next().getBalance());
        }
        return sum;
    }

    protected SortedSet<Invoice> unpaidInvoicesForAccount(UUID accountId) {
        Collection<Invoice> invoices = invoiceApi.getUnpaidInvoicesByAccountId(accountId, clock.getUTCNow());
        SortedSet<Invoice> sortedInvoices = new TreeSet<Invoice>(new InvoiceDateComparator());
        sortedInvoices.addAll(invoices);
        return sortedInvoices;
    }
}
