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

package com.ning.billing.entitlement.api.billing;


import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.UUID;

import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.glue.CallContextModule;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.DefaultCatalogService;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.entitlement.api.TestApiBase;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.SubscriptionEventTransition;
import com.ning.billing.entitlement.api.user.SubscriptionTransitionData;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.lifecycle.KillbillService.ServiceException;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.glue.ClockModule;

public class TestDefaultEntitlementBillingApi {
	private static final UUID zeroId = new UUID(0L,0L);
	private static final UUID oneId = new UUID(1L,0L);
	private static final UUID twoId = new UUID(2L,0L);

	private CatalogService catalogService;
	private ArrayList<SubscriptionBundle> bundles;
	private ArrayList<Subscription> subscriptions;
	private ArrayList<SubscriptionEventTransition> transitions;
	private EntitlementDao dao;

	private Clock clock;
	private SubscriptionData subscription;
    private CallContextFactory factory;
	private DateTime subscriptionStartDate;

	@BeforeSuite(alwaysRun=true)
	public void setup() throws ServiceException {
		TestApiBase.loadSystemPropertiesFromClasspath("/entitlement.properties");
        final Injector g = Guice.createInjector(Stage.PRODUCTION, new CatalogModule(), new ClockModule(), new CallContextModule());

        catalogService = g.getInstance(CatalogService.class);
        clock = g.getInstance(Clock.class);
        factory = g.getInstance(CallContextFactory.class);

        ((DefaultCatalogService)catalogService).loadCatalog();
	}

	@BeforeMethod(alwaysRun=true)
	public void setupEveryTime() {
		bundles = new ArrayList<SubscriptionBundle>();
		final SubscriptionBundle bundle = new SubscriptionBundleData( zeroId,"TestKey", oneId,  clock.getUTCNow().minusDays(4));
		bundles.add(bundle);


		transitions = new ArrayList<SubscriptionEventTransition>();
		subscriptions = new ArrayList<Subscription>();

		SubscriptionBuilder builder = new SubscriptionBuilder();
		subscriptionStartDate = clock.getUTCNow().minusDays(3);
		builder.setStartDate(subscriptionStartDate).setId(oneId);
		subscription = new SubscriptionData(builder) {
		    @Override
            public List<SubscriptionEventTransition> getBillingTransitions() {
		    	return transitions;
		    }
		};

		subscriptions.add(subscription);

        dao = BrainDeadProxyFactory.createBrainDeadProxyFor(EntitlementDao.class);
        ((ZombieControl) dao).addResult("getSubscriptionBundleForAccount", bundles);
        ((ZombieControl) dao).addResult("getSubscriptions", subscriptions);
        ((ZombieControl) dao).addResult("getSubscriptionFromId", subscription);
        ((ZombieControl) dao).addResult("getSubscriptionBundleFromId", bundle);

        assertTrue(true);
	}

    @Test(enabled=true, groups="fast")
	public void testBillingEventsEmpty() {

        dao = BrainDeadProxyFactory.createBrainDeadProxyFor(EntitlementDao.class);
        ((ZombieControl) dao).addResult("getSubscriptionBundleForAccount", new ArrayList<SubscriptionBundle>());

        UUID accountId = UUID.randomUUID();
        Account account = BrainDeadProxyFactory.createBrainDeadProxyFor(Account.class);
        ((ZombieControl) account).addResult("getId", accountId).addResult("getCurrency", Currency.USD);

		AccountUserApi accountApi = BrainDeadProxyFactory.createBrainDeadProxyFor(AccountUserApi.class);
        ((ZombieControl) accountApi).addResult("getAccountById", account);

        CallContextFactory factory = new DefaultCallContextFactory(clock);
		DefaultEntitlementBillingApi api = new DefaultEntitlementBillingApi(factory, null, dao, accountApi, catalogService);
		SortedSet<BillingEvent> events = api.getBillingEventsForAccount(new UUID(0L,0L));
		Assert.assertEquals(events.size(), 0);
	}

    @Test(enabled=true, groups="fast")
	public void testBillingEventsNoBillingPeriod() throws CatalogApiException {
		DateTime now = clock.getUTCNow();
		DateTime then = now.minusDays(1);
		Plan nextPlan = catalogService.getFullCatalog().findPlan("shotgun-annual", now);
		PlanPhase nextPhase = nextPlan.getAllPhases()[0]; // The trial has no billing period
		String nextPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;
		SubscriptionEventTransition t = new SubscriptionTransitionData(
				zeroId, oneId, twoId, EventType.API_USER, ApiEventType.CREATE, then, now, null, null, null, null, SubscriptionState.ACTIVE, nextPlan, nextPhase, nextPriceList, 1, null, true);
		transitions.add(t);

        AccountUserApi accountApi = BrainDeadProxyFactory.createBrainDeadProxyFor(AccountUserApi.class);
        Account account = BrainDeadProxyFactory.createBrainDeadProxyFor(Account.class);
        ((ZombieControl)account).addResult("getBillCycleDay", 32);
        ((ZombieControl)account).addResult("getCurrency", Currency.USD);
        ((ZombieControl)accountApi).addResult("getAccountById", account);
		       
		DefaultEntitlementBillingApi api = new DefaultEntitlementBillingApi(factory, null, dao, accountApi, catalogService);
		SortedSet<BillingEvent> events = api.getBillingEventsForAccount(new UUID(0L,0L));
		checkFirstEvent(events, nextPlan, 32, oneId, now, nextPhase, ApiEventType.CREATE.toString());
	}

    @Test(enabled=true, groups="fast")
	public void testBillingEventsAnnual() throws CatalogApiException {
		DateTime now = clock.getUTCNow();
		DateTime then = now.minusDays(1);
		Plan nextPlan = catalogService.getFullCatalog().findPlan("shotgun-annual", now);
		PlanPhase nextPhase = nextPlan.getAllPhases()[1];
		String nextPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;
		SubscriptionEventTransition t = new SubscriptionTransitionData(
				zeroId, oneId, twoId, EventType.API_USER, ApiEventType.CREATE, then, now, null, null, null, null, SubscriptionState.ACTIVE, nextPlan, nextPhase, nextPriceList, 1, null, true);
		transitions.add(t);

		Account account = BrainDeadProxyFactory.createBrainDeadProxyFor(Account.class);
		((ZombieControl)account).addResult("getBillCycleDay", 1).addResult("getTimeZone", DateTimeZone.UTC)
                                .addResult("getCurrency", Currency.USD);


		AccountUserApi accountApi = BrainDeadProxyFactory.createBrainDeadProxyFor(AccountUserApi.class);
		((ZombieControl)accountApi).addResult("getAccountById", account);

        CallContextFactory factory = new DefaultCallContextFactory(clock);
		DefaultEntitlementBillingApi api = new DefaultEntitlementBillingApi(factory, null, dao, accountApi, catalogService);
		SortedSet<BillingEvent> events = api.getBillingEventsForAccount(new UUID(0L,0L));
		checkFirstEvent(events, nextPlan, subscription.getStartDate().getDayOfMonth(), oneId, now, nextPhase, ApiEventType.CREATE.toString());
	}

    @Test(enabled=true, groups="fast")
	public void testBillingEventsMonthly() throws CatalogApiException {
		DateTime now = clock.getUTCNow();
		DateTime then = now.minusDays(1);
		Plan nextPlan = catalogService.getFullCatalog().findPlan("shotgun-monthly", now);
		PlanPhase nextPhase = nextPlan.getAllPhases()[1];
		String nextPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;
		SubscriptionEventTransition t = new SubscriptionTransitionData(
				zeroId, oneId, twoId, EventType.API_USER, ApiEventType.CREATE, then, now, null, null, null, null, SubscriptionState.ACTIVE, nextPlan, nextPhase, nextPriceList, 1, null, true);
		transitions.add(t);

        AccountUserApi accountApi = BrainDeadProxyFactory.createBrainDeadProxyFor(AccountUserApi.class);
        Account account = BrainDeadProxyFactory.createBrainDeadProxyFor(Account.class);
        ((ZombieControl)account).addResult("getBillCycleDay", 32);
        ((ZombieControl)account).addResult("getCurrency", Currency.USD);
        ((ZombieControl)accountApi).addResult("getAccountById", account);

        DefaultEntitlementBillingApi api = new DefaultEntitlementBillingApi(factory, null, dao,accountApi,catalogService);
		SortedSet<BillingEvent> events = api.getBillingEventsForAccount(new UUID(0L,0L));
		checkFirstEvent(events, nextPlan, 32, oneId, now, nextPhase, ApiEventType.CREATE.toString());
	}

    @Test(enabled=true, groups="fast")
	public void testBillingEventsAddOn() throws CatalogApiException {
		DateTime now = clock.getUTCNow();
		DateTime then = now.minusDays(1);
		Plan nextPlan = catalogService.getFullCatalog().findPlan("laser-scope-monthly", now);
		PlanPhase nextPhase = nextPlan.getAllPhases()[0];
		String nextPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;
		SubscriptionEventTransition t = new SubscriptionTransitionData(
				zeroId, oneId, twoId, EventType.API_USER, ApiEventType.CREATE, then, now, null, null, null, null, SubscriptionState.ACTIVE, nextPlan, nextPhase, nextPriceList, 1, null, true);
		transitions.add(t);

		Account account = BrainDeadProxyFactory.createBrainDeadProxyFor(Account.class);
		((ZombieControl)account).addResult("getBillCycleDay", 1).addResult("getTimeZone", DateTimeZone.UTC);
        ((ZombieControl)account).addResult("getCurrency", Currency.USD);

		AccountUserApi accountApi = BrainDeadProxyFactory.createBrainDeadProxyFor(AccountUserApi.class);
		((ZombieControl)accountApi).addResult("getAccountById", account);

        CallContextFactory factory = new DefaultCallContextFactory(clock);
		DefaultEntitlementBillingApi api = new DefaultEntitlementBillingApi(factory, null, dao, accountApi, catalogService);
		SortedSet<BillingEvent> events = api.getBillingEventsForAccount(new UUID(0L,0L));
		checkFirstEvent(events, nextPlan, bundles.get(0).getStartDate().getDayOfMonth(), oneId, now, nextPhase, ApiEventType.CREATE.toString());
	}


	private void checkFirstEvent(SortedSet<BillingEvent> events, Plan nextPlan,
			int BCD, UUID id, DateTime time, PlanPhase nextPhase, String desc) throws CatalogApiException {
		Assert.assertEquals(events.size(), 1);
		BillingEvent event = events.first();

        if(nextPhase.getFixedPrice() != null) {
			Assert.assertEquals(nextPhase.getFixedPrice().getPrice(Currency.USD), event.getFixedPrice());
        } else {
            assertNull(event.getFixedPrice());
		}

		if(nextPhase.getRecurringPrice() != null) {
			Assert.assertEquals(nextPhase.getRecurringPrice().getPrice(Currency.USD), event.getRecurringPrice());
        } else {
            assertNull(event.getRecurringPrice());
		}

		Assert.assertEquals(BCD, event.getBillCycleDay());
		Assert.assertEquals(id, event.getSubscription().getId());
		Assert.assertEquals(time, event.getEffectiveDate());
		Assert.assertEquals(nextPhase, event.getPlanPhase());
		Assert.assertEquals(nextPlan, event.getPlan());
		Assert.assertEquals(nextPhase.getBillingPeriod(), event.getBillingPeriod());
		Assert.assertEquals(BillingModeType.IN_ADVANCE, event.getBillingMode());
		Assert.assertEquals(desc, event.getDescription());
	}


}
