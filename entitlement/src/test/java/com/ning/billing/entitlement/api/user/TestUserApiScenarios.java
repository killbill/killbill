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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.TestApiBase;
import com.ning.billing.entitlement.glue.MockEngineModuleSql;
import com.ning.billing.util.clock.DefaultClock;
import com.ning.billing.util.svcapi.entitlement.EntitlementBillingApiException;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

public class TestUserApiScenarios extends TestApiBase {
    @Override
    protected Injector getInjector() {
        return Guice.createInjector(Stage.DEVELOPMENT, new MockEngineModuleSql());
    }

    @Test(groups = "slow")
    public void testChangeIMMCancelUncancelChangeEOT() throws EntitlementBillingApiException {
        try {
            SubscriptionData subscription = createSubscription("Assault-Rifle", BillingPeriod.MONTHLY, "gunclubDiscount");
            final PlanPhase trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            testListener.pushExpectedEvent(NextEvent.CHANGE);
            subscription.changePlan("Pistol", BillingPeriod.ANNUAL, "gunclubDiscount", clock.getUTCNow(), callContext);
            testListener.isCompleted(5000);

            // MOVE TO NEXT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);
            Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));

            // SET CTD
            final Duration ctd = getDurationMonth(1);
            final DateTime expectedPhaseTrialChange = DefaultClock.addDuration(subscription.getStartDate(), trialPhase.getDuration());
            final DateTime newChargedThroughDate = DefaultClock.addDuration(expectedPhaseTrialChange, ctd);
            billingApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate.toLocalDate(), internalCallContext);
            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId(), callContext);

            // CANCEL EOT
            testListener.setNonExpectedMode();
            testListener.pushExpectedEvent(NextEvent.CANCEL);
            subscription.cancel(clock.getUTCNow(), callContext);
            assertFalse(testListener.isCompleted(5000));
            testListener.reset();

            // UNCANCEL
            subscription.uncancel(callContext);

            // CHANGE EOT
            testListener.setNonExpectedMode();
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            subscription.changePlan("Pistol", BillingPeriod.MONTHLY, "gunclubDiscount", clock.getUTCNow(), callContext);
            assertFalse(testListener.isCompleted(5000));
            testListener.reset();

            testListener.pushExpectedEvent(NextEvent.CHANGE);
            it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(1));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));

            assertListenerStatus();

        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }
}
