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

package com.ning.billing.payment;

import java.util.UUID;

import org.joda.time.LocalDate;
import org.mockito.Mockito;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceCreationEvent;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.mock.api.MockBillCycleDay;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.glue.PaymentTestModuleWithMocks;
import com.ning.billing.payment.provider.DefaultNoOpPaymentMethodPlugin;
import com.ning.billing.util.svcsapi.bus.Bus;
import com.ning.billing.util.svcsapi.bus.Bus.EventBusException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;

import com.google.inject.Inject;

public class TestHelper {
    protected final AccountUserApi accountUserApi;
    protected final InvoicePaymentApi invoicePaymentApi;
    protected PaymentApi paymentApi;
    private final CallContext context;
    private final Bus eventBus;
    private final Clock clock;

    @Inject
    public TestHelper(final CallContextFactory factory, final AccountUserApi accountUserApi, final InvoicePaymentApi invoicePaymentApi,
                      final PaymentApi paymentApi, final Bus eventBus, final Clock clock) {
        this.eventBus = eventBus;
        this.accountUserApi = accountUserApi;
        this.invoicePaymentApi = invoicePaymentApi;
        this.paymentApi = paymentApi;
        this.clock = clock;
        context = factory.createCallContext(null, "Princess Buttercup", CallOrigin.TEST, UserType.TEST);
    }

    public Invoice createTestInvoice(final Account account,
                                     final LocalDate targetDate,
                                     final Currency currency,
                                     final CallContext context,
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
                                                                    recurringInvoiceItem.getStartDate(),
                                                                    recurringInvoiceItem.getEndDate(),
                                                                    recurringInvoiceItem.getAmount(),
                                                                    recurringInvoiceItem.getRate(),
                                                                    recurringInvoiceItem.getCurrency()));
            }
        }

        Mockito.when(invoicePaymentApi.getInvoice(Mockito.eq(invoice.getId()), Mockito.<TenantContext>any())).thenReturn(invoice);
        final InvoiceCreationEvent event = new MockInvoiceCreationEvent(invoice.getId(), invoice.getAccountId(),
                                                                        invoice.getBalance(), invoice.getCurrency(),
                                                                        invoice.getInvoiceDate(),
                                                                        context.getUserToken());

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
        Mockito.when(account.getBillCycleDay()).thenReturn(new MockBillCycleDay(1));
        Mockito.when(account.isMigrated()).thenReturn(false);
        Mockito.when(account.isNotifiedForInvoices()).thenReturn(false);

        Mockito.when(accountUserApi.getAccountById(Mockito.<UUID>any(), Mockito.<TenantContext>any())).thenReturn(account);
        Mockito.when(accountUserApi.getAccountByKey(Mockito.anyString(), Mockito.<TenantContext>any())).thenReturn(account);

        if (addPaymentMethod) {
            final PaymentMethodPlugin pm = new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), true, null);
            addTestPaymentMethod(account, pm);
        }
        return account;
    }

    public void addTestPaymentMethod(final Account account, final PaymentMethodPlugin paymentMethodInfo) throws Exception {
        final UUID paymentMethodId = paymentApi.addPaymentMethod(PaymentTestModuleWithMocks.PLUGIN_TEST_NAME, account, true, paymentMethodInfo, context);
        Mockito.when(account.getPaymentMethodId()).thenReturn(paymentMethodId);
    }
}
