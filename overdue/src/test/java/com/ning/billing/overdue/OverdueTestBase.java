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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.MockPlan;
import com.ning.billing.catalog.MockPriceList;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.mock.glue.MockClockModule;
import com.ning.billing.mock.glue.MockInvoiceModule;
import com.ning.billing.mock.glue.MockPaymentModule;
import com.ning.billing.mock.glue.TestDbiModule;
import com.ning.billing.overdue.applicator.ApplicatorMockJunctionModule;
import com.ning.billing.overdue.applicator.ApplicatorMockJunctionModule.ApplicatorBlockingApi;
import com.ning.billing.overdue.applicator.TestOverdueStateApplicator;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.overdue.glue.DefaultOverdueModule;
import com.ning.billing.overdue.service.DefaultOverdueService;
import com.ning.billing.overdue.wrapper.OverdueWrapperFactory;
import com.ning.billing.util.bus.BusService;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.notificationq.NotificationQueueService.NotificationQueueAlreadyExists;

//@Guice(modules = {MockJunctionModule.class, MockInvoiceModule.class, DefaultOverdueModule.class})
@Guice(modules = {DefaultOverdueModule.class, MockClockModule.class, ApplicatorMockJunctionModule.class, CatalogModule.class, MockInvoiceModule.class, MockPaymentModule.class, BusModule.class, NotificationQueueModule.class, TestDbiModule.class})
public class OverdueTestBase {
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
    protected BlockingApi blockingApi;

    @Inject
    protected OverdueWrapperFactory overdueWrapperFactory;

    @Inject
    protected OverdueUserApi overdueApi;


    @Inject
    protected InvoiceUserApi invoiceApi;

    protected Account account;
    protected SubscriptionBundle bundle;
    protected String productName;
    protected BillingPeriod term;
    protected String planSetName;

    @Inject
    EntitlementUserApi entitlementApi;

    @Inject
    protected DefaultOverdueService service;
    @Inject
    protected BusService busService;
    @Inject
    protected MysqlTestingHelper helper;

    protected void setupMySQL() throws IOException {
        final String utilDdl = IOUtils.toString(TestOverdueStateApplicator.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));
        helper.startMysql();
        helper.initDb(utilDdl);
    }


    @BeforeClass(groups = "slow")
    public void setup() throws Exception {

        setupMySQL();
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
        helper.stopMysql();
        service.stop();
    }


    @BeforeMethod(groups = "slow")
    public void setupTest() throws Exception {

        // Pre test cleanup
        helper.cleanupAllTables();
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


    protected SubscriptionBundle createBundle(final DateTime dateOfLastUnPaidInvoice) {
        final SubscriptionBundle bundle = BrainDeadProxyFactory.createBrainDeadProxyFor(SubscriptionBundle.class);
        final UUID bundleId = UUID.randomUUID();
        ((ZombieControl) bundle).addResult("getId", bundleId);
        ((ZombieControl) bundle).addResult("getAccountId", UUID.randomUUID());

        final Invoice invoice = BrainDeadProxyFactory.createBrainDeadProxyFor(Invoice.class);
        ((ZombieControl) invoice).addResult("getInvoiceDate", dateOfLastUnPaidInvoice);
        ((ZombieControl) invoice).addResult("getBalance", BigDecimal.TEN);
        ((ZombieControl) invoice).addResult("getId", UUID.randomUUID());
        ((ZombieControl) invoice).addResult("hashCode", UUID.randomUUID().hashCode());

        final InvoiceItem item = BrainDeadProxyFactory.createBrainDeadProxyFor(InvoiceItem.class);
        ((ZombieControl) item).addResult("getBundleId", bundleId);
        final List<InvoiceItem> items = new ArrayList<InvoiceItem>();
        items.add(item);

        ((ZombieControl) invoice).addResult("getInvoiceItems", items);

        final List<Invoice> invoices = new ArrayList<Invoice>();
        invoices.add(invoice);
        ((ZombieControl) invoiceApi).addResult("getUnpaidInvoicesByAccountId", invoices);


        final Subscription base = BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class);
        ((ZombieControl) base).addResult("getCurrentPlan", MockPlan.createBicycleNoTrialEvergreen1USD());
        ((ZombieControl) base).addResult("getCurrentPriceList", new MockPriceList());
        ((ZombieControl) base).addResult("getCurrentPhase", MockPlan.createBicycleNoTrialEvergreen1USD().getFinalPhase());
        ((ZombieControl) entitlementApi).addResult("getBaseSubscription", base);

        return bundle;
    }
}
