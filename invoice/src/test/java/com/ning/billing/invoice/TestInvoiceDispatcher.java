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

package com.ning.billing.invoice;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import com.ning.billing.invoice.api.InvoiceNotifier;
import com.ning.billing.invoice.notification.NullInvoiceNotifier;
import com.ning.billing.invoice.tests.InvoicingTestBase;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.MockPlan;
import com.ning.billing.catalog.MockPlanPhase;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.billing.BillingModeType;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.model.InvoiceGenerator;
import com.ning.billing.invoice.notification.NextBillingDateNotifier;
import com.ning.billing.junction.api.BillingApi;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.mock.glue.MockJunctionModule;
import com.ning.billing.util.bus.BusService;
import com.ning.billing.util.bus.DefaultBusService;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.globallocker.GlobalLocker;

@Test(groups = "slow")
@Guice(modules = {MockModule.class})
public class TestInvoiceDispatcher extends InvoicingTestBase {
	private Logger log = LoggerFactory.getLogger(TestInvoiceDispatcher.class);

	@Inject
	private InvoiceGenerator generator;

	@Inject
	private InvoiceDao invoiceDao;

	@Inject
	private GlobalLocker locker;

	@Inject
	private MysqlTestingHelper helper;

	@Inject
	private NextBillingDateNotifier notifier;

    @Inject
    private BusService busService;

    @Inject
    private BillingApi billingApi;

    @Inject
    private Clock clock;

    private CallContext context;

    @BeforeSuite(groups = "slow")
    public void setup() throws IOException
    {
		final String invoiceDdl = IOUtils.toString(TestInvoiceDispatcher.class.getResourceAsStream("/com/ning/billing/invoice/ddl.sql"));
		final String utilDdl = IOUtils.toString(TestInvoiceDispatcher.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));

		helper.startMysql();

		helper.initDb(invoiceDdl);
		helper.initDb(utilDdl);
		notifier.initialize();
		notifier.start();

        context = new DefaultCallContextFactory(clock).createCallContext("Miracle Max", CallOrigin.TEST, UserType.TEST);

		busService.getBus().start();
		((ZombieControl)billingApi).addResult("setChargedThroughDate", BrainDeadProxyFactory.ZOMBIE_VOID);
	}

	@AfterClass(alwaysRun = true)
	public void tearDown() {
		try {
			((DefaultBusService) busService).stopBus();
			notifier.stop();
			helper.stopMysql();
		} catch (Exception e) {
			log.warn("Failed to tearDown test properly ", e);
		}
    }
	    
    @Test(groups={"slow"}, enabled=true)
    public void testDryRunInvoice() throws InvoiceApiException {
        UUID accountId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

		AccountUserApi accountUserApi = BrainDeadProxyFactory.createBrainDeadProxyFor(AccountUserApi.class);
		Account account = BrainDeadProxyFactory.createBrainDeadProxyFor(Account.class);

		((ZombieControl)accountUserApi).addResult("getAccountById", account);
		((ZombieControl)account).addResult("getCurrency", Currency.USD);
		((ZombieControl)account).addResult("getId", accountId);
        ((ZombieControl)account).addResult(("isNotifiedForInvoices"), true);

		Subscription subscription =  BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class);
        ((ZombieControl)subscription).addResult("getId", subscriptionId);
        ((ZombieControl)subscription).addResult("getBundleId", new UUID(0L,0L));
		SortedSet<BillingEvent> events = new TreeSet<BillingEvent>();
		Plan plan = MockPlan.createBicycleNoTrialEvergreen1USD();
		PlanPhase planPhase = MockPlanPhase.create1USDMonthlyEvergreen();
		DateTime effectiveDate = new DateTime().minusDays(1);
		Currency currency = Currency.USD;
		BigDecimal fixedPrice = null;
		events.add(createMockBillingEvent(account, subscription, effectiveDate, plan, planPhase,
                fixedPrice, BigDecimal.ONE, currency, BillingPeriod.MONTHLY, 1,
                BillingModeType.IN_ADVANCE, "", 1L, SubscriptionTransitionType.CREATE));

		((ZombieControl) billingApi).addResult("getBillingEventsForAccountAndUpdateAccountBCD", events);

		DateTime target = new DateTime();

        InvoiceNotifier invoiceNotifier = new NullInvoiceNotifier();
		InvoiceDispatcher dispatcher = new InvoiceDispatcher(generator, accountUserApi, billingApi, invoiceDao,
                                                             invoiceNotifier, locker, busService.getBus(), clock);

		Invoice invoice = dispatcher.processAccount(accountId, target, true, context);
		Assert.assertNotNull(invoice);

		List<Invoice> invoices = invoiceDao.getInvoicesByAccount(accountId);
		Assert.assertEquals(invoices.size(), 0);

		// Try it again to double check
		invoice = dispatcher.processAccount(accountId, target, true, context);
		Assert.assertNotNull(invoice);

		invoices = invoiceDao.getInvoicesByAccount(accountId);
		Assert.assertEquals(invoices.size(), 0);

		// This time no dry run
		invoice = dispatcher.processAccount(accountId, target, false, context);
		Assert.assertNotNull(invoice);

		invoices = invoiceDao.getInvoicesByAccount(accountId);
		Assert.assertEquals(invoices.size(),1);

	}

}
