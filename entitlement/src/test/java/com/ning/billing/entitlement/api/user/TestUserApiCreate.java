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
import org.testng.Assert;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.catalog.api.IPriceListSet;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.ApiTestListener.NextEvent;
import com.ning.billing.entitlement.events.IEvent;
import com.ning.billing.entitlement.events.phase.IPhaseEvent;
import com.ning.billing.util.clock.Clock;

public abstract class TestUserApiCreate extends TestUserApiBase {


    protected void testSimpleCreateSubscriptionReal() {

        log.info("Starting testSimpleCreateSubscription");
        try {

            DateTime init = clock.getUTCNow();

            String productName = "Shotgun";
            BillingPeriod term = BillingPeriod.MONTHLY;
            String planSetName = IPriceListSet.DEFAULT_PRICELIST_NAME;

            testListener.pushExpectedEvent(NextEvent.CREATE);

            Subscription subscription = (Subscription) entitlementApi.createSubscription(bundle.getId(), productName, term, planSetName, clock.getUTCNow());
            assertNotNull(subscription);

            assertEquals(subscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);
            //assertEquals(subscription.getAccount(), account.getId());
            assertEquals(subscription.getBundleId(), bundle.getId());
            assertDateWithin(subscription.getStartDate(), init, clock.getUTCNow());
            assertDateWithin(subscription.getBundleStartDate(), init, clock.getUTCNow());

            printSubscriptionTransitions(subscription.getActiveTransitions());

            IPlan currentPlan = subscription.getCurrentPlan();
            assertNotNull(currentPlan);
            assertEquals(currentPlan.getProduct().getName(), productName);
            assertEquals(currentPlan.getProduct().getCategory(), ProductCategory.BASE);
            assertEquals(currentPlan.getBillingPeriod(), BillingPeriod.MONTHLY);

            IPlanPhase currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.TRIAL);

            List<ISubscriptionTransition> transitions = subscription.getActiveTransitions();
            assertNotNull(transitions);
            assertEquals(transitions.size(), 1);

            assertTrue(testListener.isCompleted(5000));

            List<IEvent> events = dao.getPendingEventsForSubscription(subscription.getId());
            assertNotNull(events);
            printEvents(events);
            assertTrue(events.size() == 1);
            assertTrue(events.get(0) instanceof IPhaseEvent);
            DateTime nextPhaseChange = ((IPhaseEvent ) events.get(0)).getEffectiveDate();
            DateTime nextExpectedPhaseChange = Clock.addDuration(subscription.getStartDate(), currentPhase.getDuration());
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


    protected void testSimpleSubscriptionThroughPhasesReal() {

        log.info("Starting testSimpleSubscriptionThroughPhases");
        try {

            DateTime curTime = clock.getUTCNow();

            String productName = "Pistol";
            BillingPeriod term = BillingPeriod.ANNUAL;
            String planSetName = "gunclubDiscount";

            testListener.pushExpectedEvent(NextEvent.CREATE);

            // CREATE SUBSCRIPTION
            Subscription subscription = (Subscription) entitlementApi.createSubscription(bundle.getId(), productName, term, planSetName, clock.getUTCNow());
            assertNotNull(subscription);

            IPlanPhase currentPhase = subscription.getCurrentPhase();
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

            subscription = (Subscription) entitlementApi.getSubscriptionFromId(subscription.getId());
            curTime = clock.getUTCNow();
            currentPhase = subscription.getCurrentPhase();
            assertNotNull(currentPhase);
            assertEquals(currentPhase.getPhaseType(), PhaseType.EVERGREEN);


        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    protected void testSubscriptionWithAddOnReal() {

        log.info("Starting testSubscriptionWithAddOn");
        try {

            String productName = "Shotgun";
            BillingPeriod term = BillingPeriod.ANNUAL;
            String planSetName = IPriceListSet.DEFAULT_PRICELIST_NAME;

            testListener.pushExpectedEvent(NextEvent.CREATE);

            Subscription subscription = (Subscription) entitlementApi.createSubscription(bundle.getId(), productName, term, planSetName, clock.getUTCNow());
            assertNotNull(subscription);

        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }


}
