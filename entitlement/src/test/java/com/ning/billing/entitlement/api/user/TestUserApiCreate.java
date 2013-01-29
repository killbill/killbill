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

package com.ning.billing.entitlement.api.user;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.util.clock.DefaultClock;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestUserApiCreate extends EntitlementTestSuiteWithEmbeddedDB {

    private static final Logger log = LoggerFactory.getLogger(TestUserApiCreate.class);

    @Test(groups = "slow")
    public void testCreateWithRequestedDate() {
        try {
            final DateTime init = clock.getUTCNow();
            final DateTime requestedDate = init.minusYears(1);

            final String productName = "Shotgun";
            final BillingPeriod term = BillingPeriod.MONTHLY;
            final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

            testListener.pushExpectedEvent(NextEvent.PHASE);
            testListener.pushExpectedEvent(NextEvent.CREATE);

            final SubscriptionData subscription = (SubscriptionData) entitlementApi.createSubscription(bundle.getId(),
                                                                                                       testUtil.getProductSpecifier(productName, planSetName, term, null), requestedDate, callContext);
            assertNotNull(subscription);

            assertEquals(subscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);
            //assertEquals(subscription.getAccount(), account.getId());
            assertEquals(subscription.getBundleId(), bundle.getId());
            assertEquals(subscription.getStartDate(), requestedDate);

            assertTrue(testListener.isCompleted(5000));

            assertListenerStatus();

        } catch (EntitlementUserApiException e) {
            log.error("Unexpected exception", e);
            Assert.fail(e.getMessage());
        }
    }

    @Test(groups = "slow")
    public void testCreateWithInitialPhase() {
        try {
            final DateTime init = clock.getUTCNow();

            final String productName = "Shotgun";
            final BillingPeriod term = BillingPeriod.MONTHLY;
            final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

            testListener.pushExpectedEvent(NextEvent.CREATE);

            final SubscriptionData subscription = (SubscriptionData) entitlementApi.createSubscription(bundle.getId(),
                                                                                                       testUtil.getProductSpecifier(productName, planSetName, term, PhaseType.EVERGREEN), clock.getUTCNow(), callContext);
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

        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test(groups = "slow")
    public void testSimpleCreateSubscription() {
        try {
            final DateTime init = clock.getUTCNow();

            final String productName = "Shotgun";
            final BillingPeriod term = BillingPeriod.MONTHLY;
            final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

            testListener.pushExpectedEvent(NextEvent.CREATE);

            final SubscriptionData subscription = (SubscriptionData) entitlementApi.createSubscription(bundle.getId(),
                                                                                                       testUtil.getProductSpecifier(productName, planSetName, term, null),
                                                                                                       clock.getUTCNow(), callContext);
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
            assertTrue(testListener.isCompleted(5000));

            final List<EntitlementEvent> events = dao.getPendingEventsForSubscription(subscription.getId(), internalCallContext);
            assertNotNull(events);
            testUtil.printEvents(events);
            assertTrue(events.size() == 1);
            assertTrue(events.get(0) instanceof PhaseEvent);
            final DateTime nextPhaseChange = ((PhaseEvent) events.get(0)).getEffectiveDate();
            final DateTime nextExpectedPhaseChange = DefaultClock.addDuration(subscription.getStartDate(), currentPhase.getDuration());
            assertEquals(nextPhaseChange, nextExpectedPhaseChange);

            testListener.pushExpectedEvent(NextEvent.PHASE);
            final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
            clock.addDeltaFromReality(it.toDurationMillis());

            final DateTime futureNow = clock.getUTCNow();
            assertTrue(futureNow.isAfter(nextPhaseChange));

            assertTrue(testListener.isCompleted(5000));

            assertListenerStatus();
        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test(groups = "slow")
    public void testSimpleSubscriptionThroughPhases() {
        try {
            final String productName = "Pistol";
            final BillingPeriod term = BillingPeriod.ANNUAL;
            final String planSetName = "gunclubDiscount";

            testListener.pushExpectedEvent(NextEvent.CREATE);

            // CREATE SUBSCRIPTION
            SubscriptionData subscription = (SubscriptionData) entitlementApi.createSubscription(bundle.getId(),
                                                                                                 testUtil.getProductSpecifier(productName, planSetName, term, null), clock.getUTCNow(), callContext);
            assertNotNull(subscription);

            PlanPhase currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);
            assertTrue(testListener.isCompleted(5000));

            // MOVE TO DISCOUNT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);
            Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));
            currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.DISCOUNT);

            // MOVE TO EVERGREEN PHASE + RE-READ SUBSCRIPTION
            testListener.pushExpectedEvent(NextEvent.PHASE);
            it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusYears(1));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));

            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId(), callContext);
            currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);

            assertListenerStatus();
        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test(groups = "slow")
    public void testSubscriptionWithAddOn() {
        try {
            final String productName = "Shotgun";
            final BillingPeriod term = BillingPeriod.ANNUAL;
            final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

            testListener.pushExpectedEvent(NextEvent.CREATE);

            final SubscriptionData subscription = (SubscriptionData) entitlementApi.createSubscription(bundle.getId(),
                                                                                                       testUtil.getProductSpecifier(productName, planSetName, term, null), clock.getUTCNow(), callContext);
            assertNotNull(subscription);

            assertListenerStatus();
        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }
}
