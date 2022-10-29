/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.subscription.api.user;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.subscription.DefaultSubscriptionTestInitializer;
import org.killbill.billing.subscription.SubscriptionTestSuiteWithEmbeddedDB;
import org.killbill.billing.subscription.api.SubscriptionBaseWithAddOns;
import org.killbill.billing.subscription.api.SubscriptionBaseWithAddOnsSpecifier;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.phase.PhaseEvent;
import org.killbill.billing.subscription.events.user.ApiEventBase;
import org.killbill.billing.subscription.events.user.ApiEventCreate;
import org.killbill.billing.subscription.events.user.ApiEventType;
import org.killbill.billing.util.callcontext.CallContext;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestUserApiCreate extends SubscriptionTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testCreateBundleWithNoExternalKey() throws Exception {
        final SubscriptionBaseBundle newBundle = subscriptionInternalApi.createBundleForAccount(bundle.getAccountId(), null, true, internalCallContext);
        assertNotNull(newBundle.getExternalKey());
    }

    @Test(groups = "slow")
    public void testCreateBundlesWithSameExternalKeys() throws SubscriptionBaseApiException {
        final LocalDate init = clock.getUTCToday();
        final LocalDate requestedDate = init.minusYears(1);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        testListener.pushExpectedEvents(NextEvent.PHASE);
        final DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, productName, term, planSetName, requestedDate);
        assertListenerStatus();
        assertNotNull(subscription);

        // Verify we can't create a second bundle with the same key
        try {
            subscriptionInternalApi.createBundleForAccount(bundle.getAccountId(), DefaultSubscriptionTestInitializer.DEFAULT_BUNDLE_KEY, true, internalCallContext);
            Assert.fail("Should not be able to create a bundle with same externalKey");
        } catch (final SubscriptionBaseApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.SUB_CREATE_ACTIVE_BUNDLE_KEY_EXISTS.getCode());
        }

        testListener.pushExpectedEvent(NextEvent.CANCEL);
        subscription.cancelWithDate(clock.getUTCNow(), callContext);
        assertListenerStatus();

        try {
            subscriptionInternalApi.createBundleForAccount(bundle.getAccountId(), DefaultSubscriptionTestInitializer.DEFAULT_BUNDLE_KEY, false, internalCallContext);
            Assert.fail("createBundleForAccount should fail because key already exists");
        } catch (final RuntimeException e) {
            assertTrue(e.getCause() instanceof SQLException && (e.getCause() instanceof SQLIntegrityConstraintViolationException || "23505".compareTo(((SQLException) e.getCause()).getSQLState()) == 0));
        }

        final SubscriptionBaseBundle newBundle = subscriptionInternalApi.createBundleForAccount(bundle.getAccountId(), DefaultSubscriptionTestInitializer.DEFAULT_BUNDLE_KEY, true, internalCallContext);
        assertNotNull(newBundle);
        assertNotEquals(newBundle.getId(), subscription.getBundleId());
        assertEquals(newBundle.getExternalKey(), DefaultSubscriptionTestInitializer.DEFAULT_BUNDLE_KEY);
        assertEquals(newBundle.getOriginalCreatedDate().compareTo(bundle.getCreatedDate()), 0, String.format("OriginalCreatedDate=%s != CreatedDate=%s", newBundle.getOriginalCreatedDate(), bundle.getCreatedDate()));

        subscriptionInternalApi.updateExternalKey(newBundle.getId(), "myNewSuperKey", internalCallContext);
        final SubscriptionBaseBundle bundleWithNewKey = subscriptionInternalApi.getBundleFromId(newBundle.getId(), internalCallContext);
        assertEquals(bundleWithNewKey.getExternalKey(), "myNewSuperKey");
    }

    @Test(groups = "slow")
    public void testCreateWithRequestedDate() throws SubscriptionBaseApiException {
        final LocalDate init = clock.getUTCToday();
        final LocalDate requestedDate = init.minusYears(1);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        testListener.pushExpectedEvent(NextEvent.PHASE);
        final DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, productName, term, planSetName, requestedDate);
        assertNotNull(subscription);

        //
        // In addition to Alignment phase we also test SubscriptionBaseTransition eventIds and created dates.
        // Keep tracks of row events to compare with ids and created dates returned by SubscriptionBaseTransition later.
        //
        final List<SubscriptionBaseEvent> events = subscription.getEvents();
        Assert.assertEquals(events.size(), 2);

        final SubscriptionBaseEvent trialEvent = events.get(0);
        final SubscriptionBaseEvent phaseEvent = events.get(1);

        assertEquals(subscription.getBundleExternalKey(), bundle.getExternalKey());
        assertEquals(subscription.getStartDate().compareTo(requestedDate.toDateTime(accountData.getReferenceTime())), 0);

        assertListenerStatus();

        final SubscriptionBaseTransition transition = subscription.getPreviousTransition();

        assertEquals(transition.getPreviousEventId(), trialEvent.getId());
        assertEquals(transition.getNextEventId(), phaseEvent.getId());

        assertEquals(transition.getPreviousEventCreatedDate().compareTo(trialEvent.getCreatedDate()), 0);
        assertEquals(transition.getNextEventCreatedDate().compareTo(phaseEvent.getCreatedDate()), 0);
    }

    @Test(groups = "slow")
    public void testCreateWithInitialPhase() throws SubscriptionBaseApiException {
        final DateTime init = clock.getUTCNow();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        final DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, productName, term, planSetName, PhaseType.EVERGREEN, null);
        assertNotNull(subscription);

        assertEquals(subscription.getBundleExternalKey(), bundle.getExternalKey());
        testUtil.assertDateWithin(subscription.getStartDate(), init, clock.getUTCNow());
        testUtil.assertDateWithin(subscription.getBundleStartDate(), init, clock.getUTCNow());

        final Plan currentPlan = subscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), productName);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        final PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);

        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testSimpleCreateSubscription() throws SubscriptionBaseApiException {
        final DateTime init = clock.getUTCNow();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        final DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, productName, term, planSetName);
        assertNotNull(subscription);

        assertEquals(subscription.getBundleExternalKey(), bundle.getExternalKey());
        testUtil.assertDateWithin(subscription.getStartDate(), init, clock.getUTCNow());
        testUtil.assertDateWithin(subscription.getBundleStartDate(), init, clock.getUTCNow());

        final Plan currentPlan = subscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), productName);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        final PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);
        assertListenerStatus();

        final List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof PhaseEvent);
        final DateTime nextPhaseChange = events.get(0).getEffectiveDate();
        final DateTime nextExpectedPhaseChange = TestSubscriptionHelper.addDuration(subscription.getStartDate(), currentPhase.getDuration());
        assertEquals(nextPhaseChange, nextExpectedPhaseChange);

        testListener.pushExpectedEvent(NextEvent.PHASE);
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
        clock.addDeltaFromReality(it.toDurationMillis());

        final DateTime futureNow = clock.getUTCNow();
        assertTrue(futureNow.isAfter(nextPhaseChange));

        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testSimpleSubscriptionThroughPhases() throws SubscriptionBaseApiException {
        final String productName = "Pistol";
        final BillingPeriod term = BillingPeriod.ANNUAL;
        final String planSetName = "gunclubDiscount";

        // CREATE SUBSCRIPTION
        DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, productName, term, planSetName);
        assertNotNull(subscription);

        PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);
        assertListenerStatus();

        // MOVE TO DISCOUNT PHASE
        testListener.pushExpectedEvent(NextEvent.PHASE);
        Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();
        currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.DISCOUNT);

        // MOVE TO EVERGREEN PHASE + RE-READ SUBSCRIPTION
        testListener.pushExpectedEvent(NextEvent.PHASE);
        it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusYears(1));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), false, internalCallContext);
        currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);

        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testSubscriptionWithAddOn() throws SubscriptionBaseApiException {
        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        final DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, productName, term, planSetName);
        assertNotNull(subscription);

        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testCreateSubscriptionInTheFuture() throws SubscriptionBaseApiException {
        final LocalDate init = clock.getUTCToday();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        final LocalDate futureCreationDate = init.plusDays(10);

        DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, productName, term, planSetName, futureCreationDate);
        assertListenerStatus();
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.PENDING);

        testListener.pushExpectedEvent(NextEvent.CREATE);
        clock.addDays(10);
        assertListenerStatus();

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), false, internalCallContext);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);
    }

    @Test(groups = "slow")
    public void testCreateSubscriptionWithBCD() throws SubscriptionBaseApiException {
        final DateTime init = clock.getUTCNow();

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(bundle.getAccountId(),
                                                                                                             ObjectType.ACCOUNT,
                                                                                                             this.internalCallContext.getUpdatedBy(),
                                                                                                             this.internalCallContext.getCallOrigin(),
                                                                                                             this.internalCallContext.getContextUserType(),
                                                                                                             this.internalCallContext.getUserToken(),
                                                                                                             this.internalCallContext.getTenantRecordId());

        final Iterable<EntitlementSpecifier> specifiers = List.of(new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("shotgun-monthly"), 18, null, null));
        final SubscriptionBaseWithAddOnsSpecifier subscriptionBaseWithAddOnsSpecifier = new SubscriptionBaseWithAddOnsSpecifier(bundle.getId(),
                                                                                                                                bundle.getExternalKey(),
                                                                                                                                specifiers,
                                                                                                                                init,
                                                                                                                                false);

        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BCD_CHANGE);
        final List<SubscriptionBaseWithAddOns> subscriptionBaseWithAddOns = subscriptionInternalApi.createBaseSubscriptionsWithAddOns(catalog.getCatalog(),
                                                                                                                                      List.of(subscriptionBaseWithAddOnsSpecifier),
                                                                                                                                      false,
                                                                                                                                      internalCallContext);
        testListener.assertListenerStatus();

        assertEquals(subscriptionBaseWithAddOns.size(), 1);
        assertEquals(subscriptionBaseWithAddOns.get(0).getSubscriptionBaseList().size(), 1);

        final DefaultSubscriptionBase subscription = (DefaultSubscriptionBase) subscriptionBaseWithAddOns.get(0).getSubscriptionBaseList().get(0);
        assertNotNull(subscription);
        assertNotNull(subscription.getBillCycleDayLocal());
        assertEquals(subscription.getBillCycleDayLocal().intValue(), 18);

    }
    
    @Test(groups = "slow")
    public void testCreateSubscriptionAndCheckSubscriptionEvents() throws SubscriptionBaseApiException {
        final DateTime init = clock.getUTCNow();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        final DefaultSubscriptionBase subscription = testUtil.createSubscription(bundle, productName, term, planSetName);
        assertNotNull(subscription);

        assertEquals(subscription.getBundleExternalKey(), bundle.getExternalKey());
        testUtil.assertDateWithin(subscription.getStartDate(), init, clock.getUTCNow());
        testUtil.assertDateWithin(subscription.getBundleStartDate(), init, clock.getUTCNow());

        final Plan currentPlan = subscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), productName);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        final PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);
        assertListenerStatus();

        //retrieve events with includeDeletedEvents=false (no deleted events exist currently)
        final List<SubscriptionBaseEvent> activeEvents = dao.getEventsForSubscription(subscription.getId(), false, internalCallContext);
        assertNotNull(activeEvents);
        testUtil.printEvents(activeEvents);
        assertTrue(activeEvents.size() == 2);
        assertTrue(activeEvents.get(0) instanceof ApiEventBase);
        assertTrue(activeEvents.get(1) instanceof PhaseEvent);
    
        clock.addDays(2);
        
        //change plan, this will result in deletion of the PhaseEvent
        testListener.pushExpectedEvent(NextEvent.CHANGE);
        final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier("pistol-monthly-notrial");        
        subscription.changePlan(new DefaultEntitlementSpecifier(planPhaseSpecifier), callContext);
        assertListenerStatus();
        
        //retrieve events with includeDeletedEvents=false 
        List<SubscriptionBaseEvent> eventsAfterChange = dao.getEventsForSubscription(subscription.getId(), false, internalCallContext);
        assertNotNull(eventsAfterChange);
        testUtil.printEvents(eventsAfterChange);
        assertTrue(eventsAfterChange.size() == 2); //PhaseEvent is deleted
        SubscriptionBaseEvent event = eventsAfterChange.get(0);
        assertTrue(event instanceof ApiEventBase);
        assertEquals(((ApiEventBase) event).getApiEventType(), ApiEventType.CREATE);
        
        event = eventsAfterChange.get(1);
        assertTrue(event instanceof ApiEventBase);
        assertEquals(((ApiEventBase) event).getApiEventType(), ApiEventType.CHANGE);        
        
        ///retrieve events with includeDeletedEvents=true 
        eventsAfterChange = dao.getEventsForSubscription(subscription.getId(), true, internalCallContext);
        assertNotNull(eventsAfterChange);
        testUtil.printEvents(eventsAfterChange);
        assertTrue(eventsAfterChange.size() == 3); //Returns PhaseEvent as well
        
        event = eventsAfterChange.get(0);
        assertTrue(event instanceof ApiEventBase);
        assertEquals(((ApiEventBase) event).getApiEventType(), ApiEventType.CREATE);
        
        event = eventsAfterChange.get(1);
        assertTrue(event instanceof ApiEventBase);
        assertEquals(((ApiEventBase) event).getApiEventType(), ApiEventType.CHANGE);    
        
        event = eventsAfterChange.get(2);
        assertTrue(event instanceof PhaseEvent);
    }    
}
