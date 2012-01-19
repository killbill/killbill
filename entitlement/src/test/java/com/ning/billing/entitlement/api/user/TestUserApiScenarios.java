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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhase;

import com.ning.billing.entitlement.api.ApiTestListener.NextEvent;
import com.ning.billing.entitlement.api.TestApiBase;
import com.ning.billing.entitlement.api.billing.EntitlementBillingApiException;
import com.ning.billing.entitlement.glue.MockEngineModuleSql;
import com.ning.billing.util.clock.DefaultClock;
import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class TestUserApiScenarios extends TestApiBase {

    @Override
    protected Injector getInjector() {
        return Guice.createInjector(Stage.DEVELOPMENT, new MockEngineModuleSql());
    }

    @Test(enabled=true)
    public void testChangeIMMCancelUncancelChangeEOT() throws EntitlementBillingApiException {

        log.info("Starting testChangeIMMCancelUncancelChangeEOT");

        try {
            SubscriptionData subscription = createSubscription("Assault-Rifle", BillingPeriod.MONTHLY, "gunclubDiscount");
            PlanPhase trialPhase = subscription.getCurrentPhase();
            assertEquals(trialPhase.getPhaseType(), PhaseType.TRIAL);

            testListener.pushExpectedEvent(NextEvent.CHANGE);
            subscription.changePlan("Pistol", BillingPeriod.ANNUAL, "gunclubDiscount", clock.getUTCNow());
            testListener.isCompleted(3000);

            // MOVE TO NEXT PHASE
            testListener.pushExpectedEvent(NextEvent.PHASE);
            clock.setDeltaFromReality(trialPhase.getDuration(), DAY_IN_MS);
            assertTrue(testListener.isCompleted(2000));

            // SET CTD
            Duration ctd = getDurationMonth(1);
            DateTime expectedPhaseTrialChange = DefaultClock.addDuration(subscription.getStartDate(), trialPhase.getDuration());
            DateTime newChargedThroughDate = DefaultClock.addDuration(expectedPhaseTrialChange, ctd);
            billingApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate);
            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId());

            // CANCEL EOT
            testListener.pushExpectedEvent(NextEvent.CANCEL);
            subscription.cancel(clock.getUTCNow(), false);
            assertFalse(testListener.isCompleted(2000));
            testListener.reset();

            // UNCANCEL
            subscription.uncancel();

            // CHANGE EOT
            testListener.pushExpectedEvent(NextEvent.CHANGE);
            subscription.changePlan("Pistol", BillingPeriod.MONTHLY, "gunclubDiscount", clock.getUTCNow());
            assertFalse(testListener.isCompleted(2000));

            clock.addDeltaFromReality(ctd);
            assertTrue(testListener.isCompleted(3000));


        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test(enabled=false)
    private void testChangeEOTCancelUncancelChangeIMM() {

    }

}
