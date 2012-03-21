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

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.clock.Clock;
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
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.billing.BillingModeType;
import com.ning.billing.entitlement.api.billing.DefaultBillingEvent;
import com.ning.billing.entitlement.api.billing.EntitlementBillingApi;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionTransition.SubscriptionTransitionType;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.model.InvoiceGenerator;
import com.ning.billing.invoice.notification.NextBillingDateNotifier;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.util.bus.BusService;
import com.ning.billing.util.bus.DefaultBusService;
import com.ning.billing.util.globallocker.GlobalLocker;

@Guice(modules = {MockModule.class})
public class TestInvoiceDispatcher {
	private Logger log = LoggerFactory.getLogger(TestInvoiceDispatcher.class);

	@Inject
	private InvoiceUserApi invoiceUserApi;

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
    private Clock clock;

    private CallContext context;

	@BeforeSuite(alwaysRun = true)
	public void setup() throws IOException
	{
		final String accountDdl = IOUtils.toString(TestInvoiceDispatcher.class.getResourceAsStream("/com/ning/billing/account/ddl.sql"));
		final String entitlementDdl = IOUtils.toString(TestInvoiceDispatcher.class.getResourceAsStream("/com/ning/billing/entitlement/ddl.sql"));
		final String invoiceDdl = IOUtils.toString(TestInvoiceDispatcher.class.getResourceAsStream("/com/ning/billing/invoice/ddl.sql"));
		//        final String paymentDdl = IOUtils.toString(TestInvoiceDispatcher.class.getResourceAsStream("/com/ning/billing/payment/ddl.sql"));
		final String utilDdl = IOUtils.toString(TestInvoiceDispatcher.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));

		helper.startMysql();

		helper.initDb(accountDdl);
		helper.initDb(entitlementDdl);
		helper.initDb(invoiceDdl);
		//        helper.initDb(paymentDdl);
		helper.initDb(utilDdl);
		notifier.initialize();
		notifier.start();

        context = new DefaultCallContextFactory(clock).createCallContext("Miracle Max", CallOrigin.TEST, UserType.TEST);

		busService.getBus().start();
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

	@Test(groups={"fast"}, enabled=true)
	public void testDryRunInvoice() throws InvoiceApiException {
		UUID accountId = UUID.randomUUID();
		UUID subscriptionId = UUID.randomUUID();

		AccountUserApi accountUserApi = BrainDeadProxyFactory.createBrainDeadProxyFor(AccountUserApi.class);
		Account account = BrainDeadProxyFactory.createBrainDeadProxyFor(Account.class);
		((ZombieControl)accountUserApi).addResult("getAccountById", account);
		((ZombieControl)account).addResult("getCurrency", Currency.USD);
		((ZombieControl)account).addResult("getId", accountId);

		Subscription subscription =  BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class);
		((ZombieControl)subscription).addResult("getId", subscriptionId);
		SortedSet<BillingEvent> events = new TreeSet<BillingEvent>();
		Plan plan = MockPlan.createBicycleNoTrialEvergreen1USD();
		PlanPhase planPhase = MockPlanPhase.create1USDMonthlyEvergreen();
		DateTime effectiveDate = new DateTime().minusDays(1);
		Currency currency = Currency.USD;
		BigDecimal fixedPrice = null;
		events.add(new DefaultBillingEvent(subscription, effectiveDate,plan, planPhase,
				fixedPrice, BigDecimal.ONE, currency, BillingPeriod.MONTHLY, 1,
				BillingModeType.IN_ADVANCE, "", 1L, SubscriptionTransitionType.CREATE));

		EntitlementBillingApi entitlementBillingApi = BrainDeadProxyFactory.createBrainDeadProxyFor(EntitlementBillingApi.class);
		((ZombieControl)entitlementBillingApi).addResult("getBillingEventsForAccount", events);

		DateTime target = new DateTime();

		InvoiceDispatcher dispatcher = new InvoiceDispatcher(generator, accountUserApi, entitlementBillingApi, invoiceDao, locker);

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
