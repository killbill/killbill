/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.invoice.api.migration;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Guice;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.MockPlan;
import com.ning.billing.catalog.MockPlanPhase;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.invoice.InvoiceDispatcher;
import com.ning.billing.invoice.MockBillingEventSet;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceMigrationApi;
import com.ning.billing.invoice.api.InvoiceNotifier;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.dao.InvoiceModelDao;
import com.ning.billing.invoice.generator.InvoiceGenerator;
import com.ning.billing.invoice.notification.NullInvoiceNotifier;
import com.ning.billing.invoice.tests.InvoicingTestBase;
import com.ning.billing.mock.api.MockBillCycleDay;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.bus.DefaultBusService;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;
import com.ning.billing.util.svcapi.junction.BillingEventSet;
import com.ning.billing.util.svcapi.junction.BillingInternalApi;
import com.ning.billing.util.svcapi.junction.BillingModeType;
import com.ning.billing.util.svcsapi.bus.BusService;

import com.google.inject.Inject;

@Guice(modules = {MockModuleNoEntitlement.class})
public abstract class InvoiceApiTestBase extends InvoicingTestBase {

    protected static final Currency accountCurrency = Currency.USD;

    @Inject
    protected InvoiceUserApi invoiceUserApi;

    @Inject
    protected InvoicePaymentApi invoicePaymentApi;

    @Inject
    protected InvoiceMigrationApi migrationApi;

    @Inject
    protected InvoiceGenerator generator;

    @Inject
    protected BillingInternalApi billingApi;

    @Inject
    protected AccountInternalApi accountApi;

    @Inject
    protected EntitlementInternalApi entitlementApi;

    @Inject
    protected BusService busService;

    @Inject
    protected InvoiceDao invoiceDao;

    @Inject
    protected TagUserApi tagUserApi;

    @Inject
    protected GlobalLocker locker;

    @Inject
    protected Clock clock;

    @Inject
    protected InternalCallContextFactory internalCallContextFactory;

    @BeforeSuite(groups = "slow")
    public void setup() throws Exception {
        busService.getBus().start();
    }

    @AfterSuite(groups = "slow")
    public void tearDown() {
        try {
            ((DefaultBusService) busService).stopBus();
        } catch (Exception e) {
            log.warn("Failed to tearDown test properly ", e);
        }
    }

    protected UUID generateRegularInvoice(final Account account, final DateTime targetDate) throws Exception {
        final Subscription subscription = Mockito.mock(Subscription.class);
        Mockito.when(subscription.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(subscription.getBundleId()).thenReturn(new UUID(0L, 0L));
        final BillingEventSet events = new MockBillingEventSet();
        final Plan plan = MockPlan.createBicycleNoTrialEvergreen1USD();
        final PlanPhase planPhase = MockPlanPhase.create1USDMonthlyEvergreen();
        final DateTime effectiveDate = new DateTime().minusDays(1);
        final Currency currency = Currency.USD;
        final BigDecimal fixedPrice = null;
        events.add(createMockBillingEvent(account, subscription, effectiveDate, plan, planPhase,
                                          fixedPrice, BigDecimal.ONE, currency, BillingPeriod.MONTHLY, 1,
                                          BillingModeType.IN_ADVANCE, "", 1L, SubscriptionTransitionType.CREATE));

        Mockito.when(billingApi.getBillingEventsForAccountAndUpdateAccountBCD(Mockito.<UUID>any(), Mockito.<InternalCallContext>any())).thenReturn(events);

        final InvoiceNotifier invoiceNotifier = new NullInvoiceNotifier();
        final InvoiceDispatcher dispatcher = new InvoiceDispatcher(generator, accountApi, billingApi, entitlementApi,
                                                                   invoiceDao, invoiceNotifier, locker, busService.getBus(),
                                                                   clock);

        Invoice invoice = dispatcher.processAccount(account.getId(), targetDate, true, internalCallContext);
        Assert.assertNotNull(invoice);

        List<InvoiceModelDao> invoices = invoiceDao.getInvoicesByAccount(account.getId(), internalCallContext);
        Assert.assertEquals(invoices.size(), 0);

        invoice = dispatcher.processAccount(account.getId(), targetDate, false, internalCallContext);
        Assert.assertNotNull(invoice);

        invoices = invoiceDao.getInvoicesByAccount(account.getId(), internalCallContext);
        Assert.assertEquals(invoices.size(), 1);

        return invoice.getId();
    }

    protected Account createAccount() throws AccountApiException {
        final UUID accountId = UUID.randomUUID();
        final Account account = Mockito.mock(Account.class);
        Mockito.when(accountApi.getAccountById(Mockito.<UUID>any(), Mockito.<InternalCallContext>any())).thenReturn(account);
        Mockito.when(account.getCurrency()).thenReturn(accountCurrency);
        Mockito.when(account.getId()).thenReturn(accountId);
        Mockito.when(account.isNotifiedForInvoices()).thenReturn(true);
        Mockito.when(account.getBillCycleDay()).thenReturn(new MockBillCycleDay(31));

        return account;
    }
}
