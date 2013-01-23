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

package com.ning.billing.overdue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.MockPlan;
import com.ning.billing.catalog.MockPriceList;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.mock.glue.MockAccountModule;
import com.ning.billing.mock.glue.MockClockModule;
import com.ning.billing.mock.glue.MockEntitlementModule;
import com.ning.billing.mock.glue.MockInvoiceModule;
import com.ning.billing.mock.glue.MockPaymentModule;
import com.ning.billing.mock.glue.TestDbiModule;
import com.ning.billing.overdue.applicator.ApplicatorMockJunctionModule;
import com.ning.billing.overdue.applicator.ApplicatorMockJunctionModule.ApplicatorBlockingApi;
import com.ning.billing.overdue.applicator.OverdueListenerTesterModule;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.overdue.glue.DefaultOverdueModule;
import com.ning.billing.overdue.service.DefaultOverdueService;
import com.ning.billing.overdue.wrapper.OverdueWrapperFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.email.EmailModule;
import com.ning.billing.util.email.templates.TemplateModule;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.CallContextModule;
import com.ning.billing.util.glue.NonEntityDaoModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.TagStoreModule;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueAlreadyExists;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;
import com.ning.billing.util.svcapi.invoice.InvoiceInternalApi;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;
import com.ning.billing.util.svcsapi.bus.BusService;

import com.google.inject.Inject;

@Guice(modules = {DefaultOverdueModule.class, OverdueListenerTesterModule.class, MockClockModule.class, ApplicatorMockJunctionModule.class,
                  CallContextModule.class, CatalogModule.class, MockInvoiceModule.class, MockPaymentModule.class, NotificationQueueModule.class,
                  EmailModule.class, TemplateModule.class, TestDbiModule.class, MockEntitlementModule.class, MockInvoiceModule.class,
        MockAccountModule.class, CacheModule.class, NonEntityDaoModule.class, TagStoreModule.class})
public abstract class OverdueTestBase extends OverdueTestSuiteWithEmbeddedDB {
    protected final String configXml =
            "<overdueConfig>" +
                    "   <bundleOverdueStates>" +
                    "       <state name=\"OD3\">" +
                    "           <condition>" +
                    "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                    "                   <unit>DAYS</unit><number>50</number>" +
                    "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                    "           </condition>" +
                    "           <externalMessage>Reached OD3</externalMessage>" +
                    "           <blockChanges>true</blockChanges>" +
                    "           <disableEntitlementAndChangesBlocked>true</disableEntitlementAndChangesBlocked>" +
                    "           <autoReevaluationInterval>" +
                    "               <unit>DAYS</unit><number>5</number>" +
                    "           </autoReevaluationInterval>" +
                    "       </state>" +
                    "       <state name=\"OD2\">" +
                    "           <condition>" +
                    "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                    "                   <unit>DAYS</unit><number>40</number>" +
                    "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                    "           </condition>" +
                    "           <externalMessage>Reached OD2</externalMessage>" +
                    "           <blockChanges>true</blockChanges>" +
                    "           <disableEntitlementAndChangesBlocked>true</disableEntitlementAndChangesBlocked>" +
                    "           <autoReevaluationInterval>" +
                    "               <unit>DAYS</unit><number>5</number>" +
                    "           </autoReevaluationInterval>" +
                    "       </state>" +
                    "       <state name=\"OD1\">" +
                    "           <condition>" +
                    "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                    "                   <unit>DAYS</unit><number>30</number>" +
                    "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                    "           </condition>" +
                    "           <externalMessage>Reached OD1</externalMessage>" +
                    "           <blockChanges>true</blockChanges>" +
                    "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
                    "           <autoReevaluationInterval>" +
                    "               <unit>DAYS</unit><number>100</number>" + // this number is intentionally too high
                    "           </autoReevaluationInterval>" +
                    "       </state>" +
                    "   </bundleOverdueStates>" +
                    "</overdueConfig>";
    protected OverdueConfig config;

    @Inject
    protected ClockMock clock;

    @Inject
    protected BlockingInternalApi blockingApi;

    @Inject
    protected OverdueWrapperFactory overdueWrapperFactory;

    @Inject
    protected OverdueUserApi overdueApi;

    @Inject
    protected InvoiceInternalApi invoiceApi;

    protected Account account;
    protected SubscriptionBundle bundle;

    @Inject
    AccountInternalApi accountApi;

    @Inject
    EntitlementInternalApi entitlementApi;

    @Inject
    protected DefaultOverdueService service;

    @Inject
    protected BusService busService;

    @BeforeClass(groups = "slow")
    public void setup() throws Exception {
        service.registerForBus();
        try {
            service.initialize();
        } catch (RuntimeException e) {
            if (!(e.getCause() instanceof NotificationQueueAlreadyExists)) {
                throw e;
            }
        }
        service.start();
    }

    @AfterClass(groups = "slow")
    public void tearDown() throws Exception {
        service.stop();
    }

    @BeforeMethod(groups = "slow")
    public void setupTest() throws Exception {
        clock.resetDeltaFromReality();
    }

    protected void checkStateApplied(final OverdueState<SubscriptionBundle> state) {
        final BlockingState result = ((ApplicatorBlockingApi) blockingApi).getBlockingState();
        checkStateApplied(result, state);
    }

    protected void checkStateApplied(final BlockingState result, final OverdueState<SubscriptionBundle> state) {
        Assert.assertEquals(result.getStateName(), state.getName());
        Assert.assertEquals(result.isBlockChange(), state.blockChanges());
        Assert.assertEquals(result.isBlockEntitlement(), state.disableEntitlementAndChangesBlocked());
        Assert.assertEquals(result.isBlockBilling(), state.disableEntitlementAndChangesBlocked());
    }

    protected SubscriptionBundle createBundle(final LocalDate dateOfLastUnPaidInvoice) throws EntitlementUserApiException, AccountApiException {
        final SubscriptionBundle bundle = Mockito.mock(SubscriptionBundle.class);
        final UUID bundleId = UUID.randomUUID();
        Mockito.when(bundle.getId()).thenReturn(bundleId);

        final UUID accountId = UUID.randomUUID();
        account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(accountId);
        Mockito.when(account.getTimeZone()).thenReturn(DateTimeZone.UTC);
        Mockito.when(accountApi.getAccountById(Mockito.eq(account.getId()), Mockito.<InternalTenantContext>any())).thenReturn(account);

        Mockito.when(bundle.getAccountId()).thenReturn(accountId);

        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getInvoiceDate()).thenReturn(dateOfLastUnPaidInvoice);
        Mockito.when(invoice.getBalance()).thenReturn(BigDecimal.TEN);
        Mockito.when(invoice.getId()).thenReturn(UUID.randomUUID());

        final InvoiceItem item = Mockito.mock(InvoiceItem.class);
        Mockito.when(item.getBundleId()).thenReturn(bundleId);
        final List<InvoiceItem> items = new ArrayList<InvoiceItem>();
        items.add(item);

        Mockito.when(invoice.getInvoiceItems()).thenReturn(items);

        final List<Invoice> invoices = new ArrayList<Invoice>();
        invoices.add(invoice);
        Mockito.when(invoiceApi.getUnpaidInvoicesByAccountId(Mockito.<UUID>any(), Mockito.<LocalDate>any(), Mockito.<InternalTenantContext>any())).thenReturn(invoices);

        final Subscription base = Mockito.mock(Subscription.class);
        Mockito.when(base.getCurrentPlan()).thenReturn(MockPlan.createBicycleNoTrialEvergreen1USD());
        Mockito.when(base.getCurrentPriceList()).thenReturn(new MockPriceList());
        Mockito.when(base.getCurrentPhase()).thenReturn(MockPlan.createBicycleNoTrialEvergreen1USD().getFinalPhase());
        Mockito.when(entitlementApi.getBaseSubscription(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(base);

        return bundle;
    }
}
