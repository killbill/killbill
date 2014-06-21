/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.payment;

import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.events.InvoiceCreationInternalEvent;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.payment.api.DirectPaymentApi;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.provider.DefaultNoOpPaymentMethodPlugin;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.clock.Clock;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class TestPaymentHelper {

    protected final AccountInternalApi AccountApi;
    protected final InvoiceInternalApi invoiceApi;
    protected DirectPaymentApi paymentApi;
    private final PersistentBus eventBus;
    private final Clock clock;

    private final CallContext context;

    @Inject
    public TestPaymentHelper(final AccountInternalApi AccountApi, final InvoiceInternalApi invoiceApi,
                             final DirectPaymentApi paymentApi, final PersistentBus eventBus,
                             final Clock clock,
                             final CallContext context) {
        this.eventBus = eventBus;
        this.AccountApi = AccountApi;
        this.invoiceApi = invoiceApi;
        this.paymentApi = paymentApi;
        this.clock = clock;
        this.context = context;
    }

    public Invoice createTestInvoice(final Account account,
                                     final LocalDate targetDate,
                                     final Currency currency,
                                     final InvoiceItem... items) throws EventBusException, InvoiceApiException {
        final Invoice invoice = new MockInvoice(account.getId(), clock.getUTCToday(), targetDate, currency);

        for (final InvoiceItem item : items) {
            if (item instanceof MockRecurringInvoiceItem) {
                final MockRecurringInvoiceItem recurringInvoiceItem = (MockRecurringInvoiceItem) item;
                invoice.addInvoiceItem(new MockRecurringInvoiceItem(invoice.getId(),
                                                                    account.getId(),
                                                                    recurringInvoiceItem.getBundleId(),
                                                                    recurringInvoiceItem.getSubscriptionId(),
                                                                    recurringInvoiceItem.getPlanName(),
                                                                    recurringInvoiceItem.getPhaseName(),
                                                                    null,
                                                                    recurringInvoiceItem.getStartDate(),
                                                                    recurringInvoiceItem.getEndDate(),
                                                                    recurringInvoiceItem.getAmount(),
                                                                    recurringInvoiceItem.getRate(),
                                                                    recurringInvoiceItem.getCurrency()));
            }
        }

        Mockito.when(invoiceApi.getInvoiceById(Mockito.eq(invoice.getId()), Mockito.<InternalTenantContext>any())).thenReturn(invoice);
        Mockito.when(invoiceApi.getInvoiceForPaymentId(Mockito.<UUID>any(), Mockito.<InternalCallContext>any())).thenReturn(invoice);

        final InvoiceCreationInternalEvent event = new MockInvoiceCreationEvent(invoice.getId(), invoice.getAccountId(),
                                                                                invoice.getBalance(), invoice.getCurrency(),
                                                                                invoice.getInvoiceDate(), 1L, 2L, null);

        eventBus.post(event);
        return invoice;
    }

    public Account createTestAccount(final String email, final boolean addPaymentMethod) throws Exception {
        final String name = "First" + UUID.randomUUID().toString() + " " + "Last" + UUID.randomUUID().toString();
        final String externalKey = UUID.randomUUID().toString();

        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(account.getExternalKey()).thenReturn(externalKey);
        Mockito.when(account.getName()).thenReturn(name);
        Mockito.when(account.getFirstNameLength()).thenReturn(10);
        Mockito.when(account.getPhone()).thenReturn("123-456-7890");
        Mockito.when(account.getEmail()).thenReturn(email);
        Mockito.when(account.getCurrency()).thenReturn(Currency.USD);
        Mockito.when(account.getBillCycleDayLocal()).thenReturn(1);
        Mockito.when(account.isMigrated()).thenReturn(false);
        Mockito.when(account.isNotifiedForInvoices()).thenReturn(false);

        Mockito.when(AccountApi.getAccountById(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(account);
        Mockito.when(AccountApi.getAccountByKey(Mockito.anyString(), Mockito.<InternalTenantContext>any())).thenReturn(account);

        if (addPaymentMethod) {
            final PaymentMethodPlugin pm = new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), true, null);
            addTestPaymentMethod(account, pm);
        }
        return account;
    }

    public void addTestPaymentMethod(final Account account, final PaymentMethodPlugin paymentMethodInfo) throws Exception {
        final UUID paymentMethodId = paymentApi.addPaymentMethod(UUID.randomUUID().toString(), account, MockPaymentProviderPlugin.PLUGIN_NAME, true, paymentMethodInfo, ImmutableList.<PluginProperty>of(), context);
        Mockito.when(account.getPaymentMethodId()).thenReturn(paymentMethodId);
    }
}
