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

package com.ning.billing.analytics;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionEvent;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionTransitionData;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;

public class TestAnalyticsListener
{
    private static final String KEY = "1234";
    private static final String ACCOUNT_KEY = "pierre-1234";
    private final Currency CURRENCY = Currency.BRL;

    private final MockBusinessSubscriptionTransitionDao dao = new MockBusinessSubscriptionTransitionDao();
    private final UUID subscriptionId = UUID.randomUUID();
    private final UUID bundleUUID = UUID.randomUUID();
    private final Product product = new MockProduct("platinium", "subscription", ProductCategory.BASE);
    private final Plan plan = new MockPlan("platinum-monthly", product);
    private final PlanPhase phase = new MockPhase(PhaseType.EVERGREEN, plan, MockDuration.UNLIMITED(), 25.95);
    private final PriceList priceList = null;

    private final CatalogService catalogService = BrainDeadProxyFactory.createBrainDeadProxyFor(CatalogService.class);
    private final Catalog catalog = BrainDeadProxyFactory.createBrainDeadProxyFor(Catalog.class);
    
    
    private AnalyticsListener listener;

    @BeforeClass(alwaysRun = true) 
    public void setupCatalog() {
        ((ZombieControl) catalog).addResult("findPlan", plan);
        ((ZombieControl) catalog).addResult("findPhase", phase);        
        ((ZombieControl) catalogService).addResult("getFullCatalog", catalog);        
        
    }
    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception
    {
        final BusinessSubscriptionTransitionRecorder recorder = new BusinessSubscriptionTransitionRecorder(dao, catalogService, new MockEntitlementUserApi(bundleUUID, KEY), new MockAccountUserApi(ACCOUNT_KEY, CURRENCY));
        listener = new AnalyticsListener(recorder, null);
    }

    @Test(groups = "fast")
    public void testSubscriptionLifecycle() throws Exception
    {
        // Create a subscription
        final DateTime effectiveTransitionTime = new DateTime(DateTimeZone.UTC);
        final DateTime requestedTransitionTime = effectiveTransitionTime;
        final SubscriptionTransitionData firstTransition = createFirstSubscriptionTransition(requestedTransitionTime, effectiveTransitionTime);
        final BusinessSubscriptionTransition firstBST = createExpectedFirstBST(firstTransition.getId(), requestedTransitionTime, effectiveTransitionTime);
        listener.handleSubscriptionTransitionChange(new DefaultSubscriptionEvent(firstTransition, effectiveTransitionTime));
        Assert.assertEquals(dao.getTransitions(KEY).size(), 1);
        Assert.assertEquals(dao.getTransitions(KEY).get(0), firstBST);

        // Cancel it
        final DateTime effectiveCancelTransitionTime = new DateTime(DateTimeZone.UTC);
        final DateTime requestedCancelTransitionTime = effectiveCancelTransitionTime;
        final SubscriptionTransitionData cancelledSubscriptionTransition = createCancelSubscriptionTransition(requestedCancelTransitionTime, effectiveCancelTransitionTime, firstTransition.getNextState());
        final BusinessSubscriptionTransition cancelledBST = createExpectedCancelledBST(cancelledSubscriptionTransition.getId(), requestedCancelTransitionTime, effectiveCancelTransitionTime, firstBST.getNextSubscription());
        listener.handleSubscriptionTransitionChange(new DefaultSubscriptionEvent(cancelledSubscriptionTransition, effectiveTransitionTime));
        Assert.assertEquals(dao.getTransitions(KEY).size(), 2);
        Assert.assertEquals(dao.getTransitions(KEY).get(1), cancelledBST);

       // Recreate it
        final DateTime effectiveRecreatedTransitionTime = new DateTime(DateTimeZone.UTC);
        final DateTime requestedRecreatedTransitionTime = effectiveRecreatedTransitionTime;
        final SubscriptionTransitionData recreatedSubscriptionTransition = createRecreatedSubscriptionTransition(requestedRecreatedTransitionTime, effectiveRecreatedTransitionTime, cancelledSubscriptionTransition.getNextState());
        final BusinessSubscriptionTransition recreatedBST = createExpectedRecreatedBST(recreatedSubscriptionTransition.getId(), requestedRecreatedTransitionTime, effectiveRecreatedTransitionTime, cancelledBST.getNextSubscription());
        listener.handleSubscriptionTransitionChange(new DefaultSubscriptionEvent(recreatedSubscriptionTransition, effectiveTransitionTime));
        Assert.assertEquals(dao.getTransitions(KEY).size(), 3);
        Assert.assertEquals(dao.getTransitions(KEY).get(2), recreatedBST);

    }

    private BusinessSubscriptionTransition createExpectedFirstBST(final UUID id, final DateTime requestedTransitionTime, final DateTime effectiveTransitionTime)
    {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionCreated(plan.getName(), catalog, effectiveTransitionTime, effectiveTransitionTime);
                
        final Subscription.SubscriptionState subscriptionState = Subscription.SubscriptionState.ACTIVE;
        return createExpectedBST(id, event, requestedTransitionTime, effectiveTransitionTime, null, subscriptionState);
    }

    private BusinessSubscriptionTransition createExpectedCancelledBST(final UUID id, final DateTime requestedTransitionTime, final DateTime effectiveTransitionTime, final BusinessSubscription lastSubscription)
    {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionCancelled(plan.getName(), catalog, effectiveTransitionTime, effectiveTransitionTime);
        return createExpectedBST(id, event, requestedTransitionTime, effectiveTransitionTime, lastSubscription, null);
    }

    private BusinessSubscriptionTransition createExpectedRecreatedBST(final UUID id, final DateTime requestedTransitionTime, final DateTime effectiveTransitionTime, final BusinessSubscription lastSubscription)
    {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionRecreated(plan.getName(), catalog, effectiveTransitionTime, effectiveTransitionTime);
        final Subscription.SubscriptionState subscriptionState = Subscription.SubscriptionState.ACTIVE;
        return createExpectedBST(id, event, requestedTransitionTime, effectiveTransitionTime, lastSubscription, subscriptionState);
    }


    private BusinessSubscriptionTransition createExpectedBST(
        final UUID eventId,
        final BusinessSubscriptionEvent eventType,
        final DateTime requestedTransitionTime,
        final DateTime effectiveTransitionTime,
        @Nullable final BusinessSubscription previousSubscription,
        @Nullable final Subscription.SubscriptionState nextState
    )
    {
        return new BusinessSubscriptionTransition(
            eventId,
            KEY,
            ACCOUNT_KEY,
            requestedTransitionTime,
            eventType,
            previousSubscription,
            nextState == null ? null : new BusinessSubscription(
                null,
                plan.getName(),
                phase.getName(),
                CURRENCY,
                effectiveTransitionTime,
                nextState,
                subscriptionId,
                bundleUUID, catalog
            )
        );
    }

    private SubscriptionTransitionData createFirstSubscriptionTransition(final DateTime requestedTransitionTime, final DateTime effectiveTransitionTime)
    {
        final ApiEventType eventType = ApiEventType.CREATE;
        final Subscription.SubscriptionState nextState = Subscription.SubscriptionState.ACTIVE;
        return new SubscriptionTransitionData(
            UUID.randomUUID(),
            subscriptionId,
            bundleUUID,
            EntitlementEvent.EventType.API_USER,
            eventType,
            requestedTransitionTime,
            effectiveTransitionTime,
            null,
            null,
            null,
            null,
            nextState,
            plan,
            phase,
            priceList,
            1L,
            null,
            true
        );
    }


    private SubscriptionTransitionData createCancelSubscriptionTransition(final DateTime requestedTransitionTime, final DateTime effectiveTransitionTime, final Subscription.SubscriptionState previousState)
    {
        final ApiEventType eventType = ApiEventType.CANCEL;
        // next state is null for canceled events
        return new SubscriptionTransitionData(
            UUID.randomUUID(),
            subscriptionId,
            bundleUUID,
            EntitlementEvent.EventType.API_USER,
            eventType,
            requestedTransitionTime,
            effectiveTransitionTime,
            previousState,
            plan,
            phase,
            priceList,
            null,
            null,
            null,
            null,
            1L,
            null,
            true
        );
    }

    private SubscriptionTransitionData createRecreatedSubscriptionTransition(final DateTime requestedTransitionTime, final DateTime effectiveTransitionTime, final Subscription.SubscriptionState previousState)
    {
        final ApiEventType eventType = ApiEventType.RE_CREATE;
        final Subscription.SubscriptionState nextState = Subscription.SubscriptionState.ACTIVE;
        return new SubscriptionTransitionData(
            UUID.randomUUID(),
            subscriptionId,
            bundleUUID,
            EntitlementEvent.EventType.API_USER,
            eventType,
            requestedTransitionTime,
            effectiveTransitionTime,
            previousState,
            null,
            null,
            null,
            nextState,
            plan,
            phase,
            priceList,
            1L,
            null,
            true
        );
    }


    private SubscriptionTransitionData createSubscriptionTransition(
        final ApiEventType eventType,
        final DateTime requestedTransitionTime,
        final DateTime effectiveTransitionTime,
        final Subscription.SubscriptionState previousState,
        final Subscription.SubscriptionState nextState
    )
    {
        return new SubscriptionTransitionData(
            UUID.randomUUID(),
            subscriptionId,
            bundleUUID,
            EntitlementEvent.EventType.API_USER,
            eventType,
            requestedTransitionTime,
            effectiveTransitionTime,
            previousState,
            plan,
            phase,
            priceList,
            nextState,
            plan,
            phase,
            priceList,
            1L,
            null,
            true
        );
    }
}