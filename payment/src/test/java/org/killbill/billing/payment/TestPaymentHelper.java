/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.GuicyKillbillTestSuite;
import org.killbill.billing.GuicyKillbillTestSuiteNoDB;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.account.api.ImmutableAccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.callcontext.MutableCallContext;
import org.killbill.billing.callcontext.MutableInternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.events.InvoiceCreationInternalEvent;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.mock.MockAccountBuilder;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.provider.DefaultNoOpPaymentMethodPlugin;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.clock.Clock;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class TestPaymentHelper {

    protected final AccountUserApi accountApi;
    protected final AccountInternalApi accountInternalApi;
    protected final ImmutableAccountInternalApi immutableAccountInternalApi;
    protected final InvoiceInternalApi invoiceApi;
    private final PersistentBus eventBus;
    private final Clock clock;
    private final NonEntityDao nonEntityDao;
    private final MutableInternalCallContext internalCallContext;
    private final InternalCallContextFactory internalCallContextFactory;
    private final CallContext context;
    protected PaymentApi paymentApi;

    @Inject
    public TestPaymentHelper(final AccountUserApi accountApi,
                             final AccountInternalApi accountInternalApi,
                             final ImmutableAccountInternalApi immutableAccountInternalApi,
                             final InvoiceInternalApi invoiceApi,
                             final PaymentApi paymentApi,
                             final PersistentBus eventBus,
                             final Clock clock,
                             final NonEntityDao nonEntityDao,
                             final MutableInternalCallContext internalCallContext,
                             final InternalCallContextFactory internalCallContextFactory,
                             final CallContext context) {
        this.accountApi = accountApi;
        this.eventBus = eventBus;
        this.accountInternalApi = accountInternalApi;
        this.immutableAccountInternalApi = immutableAccountInternalApi;
        this.invoiceApi = invoiceApi;
        this.paymentApi = paymentApi;
        this.clock = clock;
        this.nonEntityDao = nonEntityDao;
        this.internalCallContext = internalCallContext;
        this.internalCallContextFactory = internalCallContextFactory;
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
                                                                                invoice.getInvoiceDate(), internalCallContext.getAccountRecordId(),
                                                                                internalCallContext.getTenantRecordId(), null);

        eventBus.post(event);
        return invoice;
    }

    public Account createTestAccount(final String email, final boolean addPaymentMethod) throws Exception {
        final String name = "First" + UUID.randomUUID().toString() + " " + "Last" + UUID.randomUUID().toString();
        final String externalKey = UUID.randomUUID().toString();

        final Account accountData = Mockito.mock(Account.class);
        Mockito.when(accountData.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(accountData.getExternalKey()).thenReturn(externalKey);
        Mockito.when(accountData.getName()).thenReturn(name);
        Mockito.when(accountData.getFirstNameLength()).thenReturn(10);
        Mockito.when(accountData.getPhone()).thenReturn("123-456-7890");
        Mockito.when(accountData.getEmail()).thenReturn(email);
        Mockito.when(accountData.getCurrency()).thenReturn(Currency.USD);
        Mockito.when(accountData.getBillCycleDayLocal()).thenReturn(1);
        Mockito.when(accountData.isMigrated()).thenReturn(false);
        Mockito.when(accountData.getTimeZone()).thenReturn(DateTimeZone.UTC);
        Mockito.when(accountData.getCreatedDate()).thenReturn(clock.getUTCNow());
        Mockito.when(accountData.getReferenceTime()).thenReturn(clock.getUTCNow());

        final MutableCallContext mutableCallContext = new MutableCallContext(internalCallContext);

        Account account;
        if (isFastTest()) {
            account = GuicyKillbillTestSuiteNoDB.createMockAccount(accountData, accountApi, accountInternalApi, immutableAccountInternalApi, nonEntityDao, clock, internalCallContextFactory, mutableCallContext, internalCallContext);
        } else {
            account = accountApi.createAccount(accountData, context);
        }

        GuicyKillbillTestSuite.refreshCallContext(account.getId(), clock, internalCallContextFactory, mutableCallContext, internalCallContext);

        if (addPaymentMethod) {
            final PaymentMethodPlugin pm = new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), true, null);
            account = addTestPaymentMethod(account, pm);
        }

        return account;
    }

    public Account addTestPaymentMethod(final Account account, final PaymentMethodPlugin paymentMethodInfo) throws Exception {
        return addTestPaymentMethod(account, paymentMethodInfo, ImmutableList.<PluginProperty>of());
    }

    public Account addTestPaymentMethod(final Account account, final PaymentMethodPlugin paymentMethodInfo, final Iterable<PluginProperty> pluginProperties) throws Exception {
        final UUID paymentMethodId = paymentApi.addPaymentMethod(account, paymentMethodInfo.getExternalPaymentMethodId(), MockPaymentProviderPlugin.PLUGIN_NAME, true, paymentMethodInfo, pluginProperties, context);
        if (isFastTest()) {
            final Account account1 = new MockAccountBuilder(account).paymentMethodId(paymentMethodId).build();
            accountApi.updateAccount(account, context);
            return account1;
        } else {
            // To reflect the payment method id change
            return accountApi.getAccountById(account.getId(), context);
        }
    }

    // Unfortunately, this helper is shared across fast and slow tests
    private boolean isFastTest() {
        return Mockito.mockingDetails(accountInternalApi).isMock();
    }
}
