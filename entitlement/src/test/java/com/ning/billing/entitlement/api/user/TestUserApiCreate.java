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


import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.TestApiBase;
import com.ning.billing.entitlement.api.ApiTestListener.NextEvent;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.util.clock.DefaultClock;

public abstract class TestUserApiCreate extends TestApiBase {
	Logger log = LoggerFactory.getLogger(TestUserApiCreate.class);

    public void testCreateWithRequestedDate() {
        log.info("Starting testCreateWithRequestedDate");
        try {

            DateTime init = clock.getUTCNow();
            DateTime requestedDate = init.minusYears(1);

            String productName = "Shotgun";
            BillingPeriod term = BillingPeriod.MONTHLY;
            String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;


            testListener.pushExpectedEvent(NextEvent.PHASE);
            testListener.pushExpectedEvent(NextEvent.CREATE);


            SubscriptionData subscription = (SubscriptionData) entitlementApi.createSubscription(bundle.getId(),
                    getProductSpecifier(productName, planSetName, term, null), requestedDate);
            assertNotNull(subscription);

            assertEquals(subscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);
            //assertEquals(subscription.getAccount(), account.getId());
            assertEquals(subscription.getBundleId(), bundle.getId());
            assertEquals(subscription.getStartDate(), requestedDate);

            assertTrue(testListener.isCompleted(5000));

        } catch (EntitlementUserApiException e) {
        	log.error("Unexpected exception",e);
            Assert.fail(e.getMessage());
        }
    }

    protected void testCreateWithInitialPhase() {
        log.info("Starting testCreateWithInitialPhase");
        try {


            DateTime init = clock.getUTCNow();

            String productName = "Shotgun";
            BillingPeriod term = BillingPeriod.MONTHLY;
            String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

            testListener.pushExpectedEvent(NextEvent.CREATE);

            SubscriptionData subscription = (SubscriptionData) entitlementApi.createSubscription(bundle.getId(),
                    getProductSpecifier(productName, planSetName, term, PhaseType.EVERGREEN), clock.getUTCNow());
            assertNotNull(subscription);

            assertEquals(subscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);
            //assertEquals(subscription.getAccount(), account.getId());
            assertEquals(subscription.getBundleId(), bundle.getId());
            assertDateWithin(subscription.getStartDate(), init, clock.getUTCNow());
            assertDateWithin(subscription.getBundleStartDate(), init, clock.getUTCNow());

            printSubscriptionTransitions(subscription.getActiveTransitions());

            Plan currentPlan = subscription.getCurrentPlan();
            assertNotNull(currentPlan);
            assertEquals(currentPlan.getProduct().getName(), productName);
            assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
            assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);

            PlanPhase currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);

        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    protected void testSimpleCreateSubscription() {

        log.info("Starting testSimpleCreateSubscription");
        try {

            DateTime init = clock.getUTCNow();

            String productName = "Shotgun";
            BillingPeriod term = BillingPeriod.MONTHLY;
            String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

            testListener.pushExpectedEvent(NextEvent.CREATE);

            SubscriptionData subscription = (SubscriptionData) entitlementApi.createSubscription(bundle.getId(),
                    getProductSpecifier(productName, planSetName, term, null),
                    clock.getUTCNow());
            assertNotNull(subscription);

            assertEquals(subscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);
            //assertEquals(subscription.getAccount(), account.getId());
            assertEquals(subscription.getBundleId(), bundle.getId());
            assertDateWithin(subscription.getStartDate(), init, clock.getUTCNow());
            assertDateWithin(subscription.getBundleStartDate(), init, clock.getUTCNow());

            printSubscriptionTransitions(subscription.getActiveTransitions());

            Plan currentPlan = subscription.getCurrentPlan();
            assertNotNull(currentPlan);
            assertEquals(currentPlan.getProduct().getName(), productName);
            assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
            assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);

            PlanPhase currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);

            List<SubscriptionTransition> transitions = subscription.getActiveTransitions();
            assertNotNull(transitions);
            assertEquals(transitions.size(), 1);

            assertTrue(testListener.isCompleted(5000));

            List<EntitlementEvent> events = dao.getPendingEventsForSubscription(subscription.getId());
            assertNotNull(events);
            printEvents(events);
            assertTrue(events.size() == 1);
            assertTrue(events.get(0) instanceof PhaseEvent);
            DateTime nextPhaseChange = ((PhaseEvent ) events.get(0)).getEffectiveDate();
            DateTime nextExpectedPhaseChange = DefaultClock.addDuration(subscription.getStartDate(), currentPhase.getDuration());
            assertEquals(nextPhaseChange, nextExpectedPhaseChange);

            testListener.pushExpectedEvent(NextEvent.PHASE);

            clock.setDeltaFromReality(currentPhase.getDuration(), DAY_IN_MS);

            DateTime futureNow = clock.getUTCNow();
            assertTrue(futureNow.isAfter(nextPhaseChange));

            assertTrue(testListener.isCompleted(5000));

        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }


    protected void testSimpleSubscriptionThroughPhases() {

        log.info("Starting testSimpleSubscriptionThroughPhases");
        try {

            DateTime curTime = clock.getUTCNow();

            String productName = "Pistol";
            BillingPeriod term = BillingPeriod.ANNUAL;
            String planSetName = "gunclubDiscount";

            testListener.pushExpectedEvent(NextEvent.CREATE);

            // CREATE SUBSCRIPTION
            SubscriptionData subscription = (SubscriptionData) entitlementApi.createSubscription(bundle.getId(),
                    getProductSpecifier(productName, planSetName, term, null), clock.getUTCNow());
            assertNotNull(subscription);

            PlanPhase currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);
            assertTrue(testListener.isCompleted(5000));



            // MOVE TO DISCOUNT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);
            clock.setDeltaFromReality(currentPhase.getDuration(), DAY_IN_MS);
            curTime = clock.getUTCNow();
            currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.DISCOUNT);
            assertTrue(testListener.isCompleted(2000));

            // MOVE TO EVERGREEN PHASE + RE-READ SUBSCRIPTION
            testListener.pushExpectedEvent(NextEvent.PHASE);
            clock.addDeltaFromReality(currentPhase.getDuration());
            assertTrue(testListener.isCompleted(2000));

            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId());
            curTime = clock.getUTCNow();
            currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);


        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    protected void testSubscriptionWithAddOn() {

        log.info("Starting testSubscriptionWithAddOn");
        try {

            String productName = "Shotgun";
            BillingPeriod term = BillingPeriod.ANNUAL;
            String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

            testListener.pushExpectedEvent(NextEvent.CREATE);

            SubscriptionData subscription = (SubscriptionData) entitlementApi.createSubscription(bundle.getId(),
                    getProductSpecifier(productName, planSetName, term, null), clock.getUTCNow());
            assertNotNull(subscription);

        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }



}
