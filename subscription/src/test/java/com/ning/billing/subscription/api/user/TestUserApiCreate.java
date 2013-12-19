/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.subscription.api.user;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.ErrorCode;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.subscription.DefaultSubscriptionTestInitializer;
import com.ning.billing.subscription.SubscriptionTestSuiteWithEmbeddedDB;
import com.ning.billing.subscription.events.SubscriptionBaseEvent;
import com.ning.billing.subscription.events.phase.PhaseEvent;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestUserApiCreate extends SubscriptionTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testCreateBundlesWithSameExternalKeys() throws SubscriptionBaseApiException {
        final DateTime init = clock.getUTCNow();
        final DateTime requestedDate = init.minusYears(1);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.PHASE);
        final DefaultSubscriptionBase subscription = (DefaultSubscriptionBase) subscriptionInternalApi.createSubscription(bundle.getId(),
                                                                                                                          testUtil.getProductSpecifier(productName, planSetName, term, null), requestedDate, internalCallContext);
        assertListenerStatus();
        assertNotNull(subscription);

        testListener.pushExpectedEvent(NextEvent.CANCEL);
        subscription.cancelWithDate(clock.getUTCNow(), callContext);
        assertListenerStatus();

        final SubscriptionBaseBundle newBundle = subscriptionInternalApi.createBundleForAccount(bundle.getAccountId(), DefaultSubscriptionTestInitializer.DEFAULT_BUNDLE_KEY, internalCallContext);
        assertNotNull(newBundle);
        assertEquals(newBundle.getOriginalCreatedDate().compareTo(bundle.getCreatedDate()), 0);

        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.PHASE);
        final DefaultSubscriptionBase newSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.createSubscription(newBundle.getId(),
                                                                                                                             testUtil.getProductSpecifier(productName, planSetName, term, null), requestedDate, internalCallContext);
        assertListenerStatus();
        assertNotNull(newSubscription);
    }

    @Test(groups = "slow")
    public void testCreateWithRequestedDate() throws SubscriptionBaseApiException {
        final DateTime init = clock.getUTCNow();
        final DateTime requestedDate = init.minusYears(1);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        testListener.pushExpectedEvent(NextEvent.PHASE);
        testListener.pushExpectedEvent(NextEvent.CREATE);

        final DefaultSubscriptionBase subscription = (DefaultSubscriptionBase) subscriptionInternalApi.createSubscription(bundle.getId(),
                                                                                                                          testUtil.getProductSpecifier(productName, planSetName, term, null), requestedDate, internalCallContext);
        assertNotNull(subscription);

        //
        // In addition to Alignment phase we also test SubscriptionBaseTransition eventIds and created dates.
        // Keep tracks of row events to compare with ids and created dates returned by SubscriptionBaseTransition later.
        //
        final List<SubscriptionBaseEvent> events = subscription.getEvents();
        Assert.assertEquals(events.size(), 2);

        final SubscriptionBaseEvent trialEvent = events.get(0);
        final SubscriptionBaseEvent phaseEvent = events.get(1);

        assertEquals(subscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);
        //assertEquals(subscription.getAccount(), account.getId());
        assertEquals(subscription.getBundleId(), bundle.getId());
        assertEquals(subscription.getStartDate(), requestedDate);

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

        testListener.pushExpectedEvent(NextEvent.CREATE);

        final DefaultSubscriptionBase subscription = (DefaultSubscriptionBase) subscriptionInternalApi.createSubscription(bundle.getId(),
                                                                                                                          testUtil.getProductSpecifier(productName, planSetName, term, PhaseType.EVERGREEN), clock.getUTCNow(), internalCallContext);
        assertNotNull(subscription);

        assertEquals(subscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);
        //assertEquals(subscription.getAccount(), account.getId());
        assertEquals(subscription.getBundleId(), bundle.getId());
        testUtil.assertDateWithin(subscription.getStartDate(), init, clock.getUTCNow());
        testUtil.assertDateWithin(subscription.getBundleStartDate(), init, clock.getUTCNow());

        final Plan currentPlan = subscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), productName);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);

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

        testListener.pushExpectedEvent(NextEvent.CREATE);

        final DefaultSubscriptionBase subscription = (DefaultSubscriptionBase) subscriptionInternalApi.createSubscription(bundle.getId(),
                                                                                                                          testUtil.getProductSpecifier(productName, planSetName, term, null),
                                                                                                                          clock.getUTCNow(), internalCallContext);
        assertNotNull(subscription);

        assertEquals(subscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);
        //assertEquals(subscription.getAccount(), account.getId());
        assertEquals(subscription.getBundleId(), bundle.getId());
        testUtil.assertDateWithin(subscription.getStartDate(), init, clock.getUTCNow());
        testUtil.assertDateWithin(subscription.getBundleStartDate(), init, clock.getUTCNow());

        final Plan currentPlan = subscription.getCurrentPlan();
        assertNotNull(currentPlan);
        assertEquals(currentPlan.getProduct().getName(), productName);
        assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
        assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);

        final PlanPhase currentPhase = subscription.getCurrentPhase();
        assertNotNull(currentPhase);
        assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);
        assertListenerStatus();

        final List<SubscriptionBaseEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
        assertNotNull(events);
        testUtil.printEvents(events);
        assertTrue(events.size() == 1);
        assertTrue(events.get(0) instanceof PhaseEvent);
        final DateTime nextPhaseChange = ((PhaseEvent) events.get(0)).getEffectiveDate();
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

        testListener.pushExpectedEvent(NextEvent.CREATE);

        // CREATE SUBSCRIPTION
        DefaultSubscriptionBase subscription = (DefaultSubscriptionBase) subscriptionInternalApi.createSubscription(bundle.getId(),
                                                                                                                    testUtil.getProductSpecifier(productName, planSetName, term, null), clock.getUTCNow(), internalCallContext);
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

        subscription = (DefaultSubscriptionBase) subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), internalCallContext);
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

        testListener.pushExpectedEvent(NextEvent.CREATE);

        final DefaultSubscriptionBase subscription = (DefaultSubscriptionBase) subscriptionInternalApi.createSubscription(bundle.getId(),
                                                                                                                          testUtil.getProductSpecifier(productName, planSetName, term, null), clock.getUTCNow(), internalCallContext);
        assertNotNull(subscription);

        assertListenerStatus();
    }
}
