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

package org.killbill.billing.overdue.calculator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.UUID;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.overdue.OverdueTestSuiteNoDB;
import org.killbill.billing.overdue.config.api.BillingState;
import org.killbill.billing.callcontext.InternalTenantContext;

public class TestBillingStateCalculator extends OverdueTestSuiteNoDB {

    protected LocalDate now;

    @Override
    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getTimeZone()).thenReturn(DateTimeZone.UTC);
        Mockito.when(accountApi.getAccountById(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(account);
    }

    public BillingStateCalculator createBSCalc() {
        now = new LocalDate();
        final Collection<Invoice> invoices = new ArrayList<Invoice>();
        invoices.add(createInvoice(now, BigDecimal.ZERO, null));
        invoices.add(createInvoice(now.plusDays(1), BigDecimal.TEN, null));
        invoices.add(createInvoice(now.plusDays(2), new BigDecimal("100.0"), null));

        Mockito.when(invoiceApi.getUnpaidInvoicesByAccountId(Mockito.<UUID>any(), Mockito.<LocalDate>any(), Mockito.<InternalTenantContext>any())).thenReturn(invoices);

        return new BillingStateCalculator(invoiceApi, clock, tagInternalApi) {
            @Override
            public BillingState calculateBillingState(final ImmutableAccountData overdueable,
                                                      final InternalCallContext context) {
                return null;
            }
        };
    }

    public Invoice createInvoice(final LocalDate date, final BigDecimal balance, final List<InvoiceItem> invoiceItems) {
        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getBalance()).thenReturn(balance);
        Mockito.when(invoice.getInvoiceDate()).thenReturn(date);
        Mockito.when(invoice.getInvoiceItems()).thenReturn(invoiceItems);
        Mockito.when(invoice.getId()).thenReturn(UUID.randomUUID());

        return invoice;
    }

    @Test(groups = "fast")
    public void testUnpaidInvoices() {
        final BillingStateCalculator calc = createBSCalc();
        final SortedSet<Invoice> invoices = calc.unpaidInvoicesForAccount(new UUID(0L, 0L), internalCallContext);

        Assert.assertEquals(invoices.size(), 3);
        Assert.assertEquals(BigDecimal.ZERO.compareTo(invoices.first().getBalance()), 0);
        Assert.assertEquals(new BigDecimal("100.0").compareTo(invoices.last().getBalance()), 0);
    }

    @Test(groups = "fast")
    public void testSum() {
        final BillingStateCalculator calc = createBSCalc();
        final SortedSet<Invoice> invoices = calc.unpaidInvoicesForAccount(new UUID(0L, 0L), internalCallContext);
        Assert.assertEquals(new BigDecimal("110.0").compareTo(calc.sumBalance(invoices)), 0);
    }

    @Test(groups = "fast")
    public void testEarliest() {
        final BillingStateCalculator calc = createBSCalc();
        final SortedSet<Invoice> invoices = calc.unpaidInvoicesForAccount(new UUID(0L, 0L), internalCallContext);
        Assert.assertEquals(calc.earliest(invoices).getInvoiceDate(), now);
    }
}
