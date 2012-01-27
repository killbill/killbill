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


import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.inject.AbstractModule;
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
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.entitlement.api.user.SubscriptionTransitionData;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.lifecycle.KillbillService.ServiceException;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;

import static org.testng.Assert.assertTrue;

public class TestDefaultEntitlementBillingApi {
	private static final UUID zeroId = new UUID(0L,0L);
	private static final UUID oneId = new UUID(1L,0L);
	private static final UUID twoId = new UUID(2L,0L);
	
	private CatalogService catalogService;
	private ArrayList<SubscriptionBundle> bundles;
	private ArrayList<Subscription> subscriptions;
	private ArrayList<SubscriptionTransition> transitions;
	private BrainDeadMockEntitlementDao dao;

	private Clock clock;
	private SubscriptionData subscription;
	private DateTime subscriptionStartDate;

	@BeforeClass(groups={"setup"})
	public void setup() throws ServiceException {
		TestApiBase.loadSystemPropertiesFromClasspath("/entitlement.properties");
        final Injector g = Guice.createInjector(Stage.PRODUCTION, new CatalogModule(), new AbstractModule() {
			protected void configure() {
				 bind(Clock.class).to(DefaultClock.class).asEagerSingleton();
			}  	
        });

        
        catalogService = g.getInstance(CatalogService.class);
        clock = g.getInstance(Clock.class);
        
        ((DefaultCatalogService)catalogService).loadCatalog();
	}
	
	@BeforeMethod(alwaysRun=true)
	public void setupEveryTime() {
		bundles = new ArrayList<SubscriptionBundle>();
		final SubscriptionBundle bundle = new SubscriptionBundleData( zeroId,"TestKey", oneId,  new DateTime().minusDays(4));
		bundles.add(bundle);
		
		
		transitions = new ArrayList<SubscriptionTransition>();
		subscriptions = new ArrayList<Subscription>();
		
		SubscriptionBuilder builder = new SubscriptionBuilder();
		subscriptionStartDate = new DateTime().minusDays(3);
		builder.setStartDate(subscriptionStartDate).setId(oneId);
		subscription = new SubscriptionData(builder) {
		    public List<SubscriptionTransition> getAllTransitions() {
		    	return transitions;
		    }
		};

		subscriptions.add(subscription);
		
		dao = new BrainDeadMockEntitlementDao() {
			public List<SubscriptionBundle> getSubscriptionBundleForAccount(
					UUID accountId) {
				return bundles;
				
			}

			public List<Subscription> getSubscriptions(UUID bundleId) {
				return subscriptions;
			}

			public Subscription getSubscriptionFromId(UUID subscriptionId) {
				return subscription;

			}

            @Override
            public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId) {
                throw new UnsupportedOperationException();
            }

            @Override
			public SubscriptionBundle getSubscriptionBundleFromId(UUID bundleId) {
				return bundle;
			}
		};

        assertTrue(true);
	}
	
    @Test(enabled=true, groups="fast")
	public void testBillingEventsEmpty() {
		EntitlementDao dao = new BrainDeadMockEntitlementDao() {
			public List<SubscriptionBundle> getSubscriptionBundleForAccount(
					UUID accountId) {
				return new ArrayList<SubscriptionBundle>();
			}

            @Override
            public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId) {
                throw new UnsupportedOperationException();
            }

        };
		AccountUserApi accountApi = new BrainDeadAccountUserApi() ;
		DefaultEntitlementBillingApi api = new DefaultEntitlementBillingApi(dao,accountApi,catalogService);
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
		SubscriptionTransition t = new SubscriptionTransitionData(
				zeroId, oneId, twoId, EventType.API_USER, ApiEventType.CREATE, then, now, null, null, null, null, SubscriptionState.ACTIVE, nextPlan, nextPhase, nextPriceList);
		transitions.add(t);
		
		AccountUserApi accountApi = new BrainDeadAccountUserApi(){

			@Override
			public Account getAccountById(UUID accountId) {
				return new BrainDeadAccount(){@Override
				public int getBillCycleDay() {
					return 32;
				}};
			}} ;
		DefaultEntitlementBillingApi api = new DefaultEntitlementBillingApi(dao,accountApi,catalogService);
		SortedSet<BillingEvent> events = api.getBillingEventsForAccount(new UUID(0L,0L));
		checkFirstEvent(events, nextPlan, 32, oneId, now, nextPhase, ApiEventType.CREATE.toString());
	}

    @Test(enabled=true, groups="fast")
	public void testBillingEventsAnual() throws CatalogApiException {
		DateTime now = clock.getUTCNow();
		DateTime then = now.minusDays(1);
		Plan nextPlan = catalogService.getFullCatalog().findPlan("shotgun-annual", now);
		PlanPhase nextPhase = nextPlan.getAllPhases()[1];
		String nextPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;
		SubscriptionTransition t = new SubscriptionTransitionData(
				zeroId, oneId, twoId, EventType.API_USER, ApiEventType.CREATE, then, now, null, null, null, null, SubscriptionState.ACTIVE, nextPlan, nextPhase, nextPriceList);
		transitions.add(t);
		
		AccountUserApi accountApi = new BrainDeadAccountUserApi(){

			@Override
			public Account getAccountById(UUID accountId) {
				return new BrainDeadAccount(){@Override
				public int getBillCycleDay() {
					return 1;
				}};
			}} ;
		DefaultEntitlementBillingApi api = new DefaultEntitlementBillingApi(dao,accountApi,catalogService);
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
		SubscriptionTransition t = new SubscriptionTransitionData(
				zeroId, oneId, twoId, EventType.API_USER, ApiEventType.CREATE, then, now, null, null, null, null, SubscriptionState.ACTIVE, nextPlan, nextPhase, nextPriceList);
		transitions.add(t);
		
		AccountUserApi accountApi = new BrainDeadAccountUserApi(){

			@Override
			public Account getAccountById(UUID accountId) {
				return new BrainDeadAccount(){@Override
				public int getBillCycleDay() {
					return 32;
				}};
			}} ;
		DefaultEntitlementBillingApi api = new DefaultEntitlementBillingApi(dao,accountApi,catalogService);
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
		SubscriptionTransition t = new SubscriptionTransitionData(
				zeroId, oneId, twoId, EventType.API_USER, ApiEventType.CREATE, then, now, null, null, null, null, SubscriptionState.ACTIVE, nextPlan, nextPhase, nextPriceList);
		transitions.add(t);
		
		AccountUserApi accountApi = new BrainDeadAccountUserApi(){

			@Override
			public Account getAccountById(UUID accountId) {
				return new BrainDeadAccount(){@Override
				public int getBillCycleDay() {
					return 1;
				}};
			}} ;
		DefaultEntitlementBillingApi api = new DefaultEntitlementBillingApi(dao,accountApi,catalogService);
		SortedSet<BillingEvent> events = api.getBillingEventsForAccount(new UUID(0L,0L));
		checkFirstEvent(events, nextPlan, bundles.get(0).getStartDate().getDayOfMonth(), oneId, now, nextPhase, ApiEventType.CREATE.toString());
	}


	private void checkFirstEvent(SortedSet<BillingEvent> events, Plan nextPlan,
			int BCD, UUID id, DateTime time, PlanPhase nextPhase, String desc) throws CatalogApiException {
		Assert.assertEquals(events.size(), 1);
		BillingEvent event = events.first();
		if(nextPhase.getFixedPrice() != null) {
			Assert.assertEquals(nextPhase.getFixedPrice().getPrice(Currency.USD), event.getFixedPrice().getPrice(Currency.USD));
		}
		if(nextPhase.getRecurringPrice() != null) {
			Assert.assertEquals(nextPhase.getRecurringPrice().getPrice(Currency.USD), event.getRecurringPrice().getPrice(Currency.USD));
		}
		
		Assert.assertEquals(BCD, event.getBillCycleDay());
		Assert.assertEquals(id, event.getSubscription().getId());
		Assert.assertEquals(time, event.getEffectiveDate());
		Assert.assertEquals(nextPhase, event.getPlanPhase());
		Assert.assertEquals(nextPlan, event.getPlan());
		Assert.assertEquals(nextPhase.getBillingPeriod(), event.getBillingPeriod());
		Assert.assertEquals(BillingModeType.IN_ADVANCE, event.getBillingMode());
		Assert.assertEquals(desc, event.getDescription());
		Assert.assertEquals(nextPhase.getFixedPrice(), event.getFixedPrice());
		Assert.assertEquals(nextPhase.getRecurringPrice(), event.getRecurringPrice());
	}
	
	
}
