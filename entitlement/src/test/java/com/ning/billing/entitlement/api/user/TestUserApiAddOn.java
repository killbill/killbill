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

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.TestApiBase;
import com.ning.billing.entitlement.api.ApiTestListener.NextEvent;
import com.ning.billing.entitlement.glue.MockEngineModuleSql;

public class TestUserApiAddOn extends TestApiBase {

    @Override
    public Injector getInjector() {
        return Guice.createInjector(Stage.DEVELOPMENT, new MockEngineModuleSql());
    }

    @Test(enabled=false, groups={"sql"})
    public void testAddonCreateWithBundleAlign() {
        try {

            String baseProduct = "Shotgun";
            BillingPeriod baseTerm = BillingPeriod.MONTHLY;
            String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

            // CREATE BP
            SubscriptionData baseSubscription = createSubscription(baseProduct, baseTerm, basePriceList);

            String aoProduct = "Telescopic-Scope";
            BillingPeriod aoTerm = BillingPeriod.MONTHLY;
            String aoPriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

            // MOVE CLOCK 14 DAYS LATER
            Duration someTimeLater = getDurationDay(13);
            clock.setDeltaFromReality(someTimeLater, DAY_IN_MS);

            // CREATE ADDON (ALIGN BUNDLE)
            DateTime beforeAOCreation = clock.getUTCNow();
            SubscriptionData aoSubscription = createSubscription(aoProduct, aoTerm, aoPriceList);
            DateTime afterAOCreation = clock.getUTCNow();

            // CHECK EVERYTHING
            Plan aoCurrentPlan = aoSubscription.getCurrentPlan();
            assertNotNull(aoCurrentPlan);
            assertEquals(aoCurrentPlan.getProduct().getName(),aoProduct);
            assertEquals(aoCurrentPlan.getProduct().getCategory(), ProductCategory.ADD_ON);
            assertEquals(aoCurrentPlan.getBillingPeriod(), aoTerm);

            PlanPhase aoCurrentPhase = aoSubscription.getCurrentPhase();
            assertNotNull(aoCurrentPhase);
            assertEquals(aoCurrentPhase.getPhaseType(), PhaseType.DISCOUNT);

           assertDateWithin(aoSubscription.getStartDate(), beforeAOCreation, afterAOCreation);
           assertEquals(aoSubscription.getBundleStartDate(), baseSubscription.getBundleStartDate());

           // CHECK next AO PHASE EVENT IS INDEED A MONTH AFTER BP STARTED => BUNDLE ALIGNMENT
           SubscriptionTransition aoPendingTranstion = aoSubscription.getPendingTransition();
           assertEquals(aoPendingTranstion.getEffectiveTransitionTime(), baseSubscription.getStartDate().plusMonths(1));



           // ADD TWO PHASE EVENTS (BP + AO)
           testListener.reset();
           testListener.pushExpectedEvent(NextEvent.PHASE);
           testListener.pushExpectedEvent(NextEvent.PHASE);

           // MOVE THROUGH TIME TO GO INTO EVERGREEN
           someTimeLater = getDurationDay(20);
           clock.addDeltaFromReality(someTimeLater);
           assertTrue(testListener.isCompleted(5000));


           try {
               Thread.currentThread().sleep(1000 * 1000);
           } catch (InterruptedException e) {


           }

           // CHECK EVERYTHING AGAIN
           aoSubscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(aoSubscription.getId());

           aoCurrentPlan = aoSubscription.getCurrentPlan();
           assertNotNull(aoCurrentPlan);
           assertEquals(aoCurrentPlan.getProduct().getName(),aoProduct);
           assertEquals(aoCurrentPlan.getProduct().getCategory(), ProductCategory.ADD_ON);
           assertEquals(aoCurrentPlan.getBillingPeriod(), aoTerm);

           aoCurrentPhase = aoSubscription.getCurrentPhase();
           assertNotNull(aoCurrentPhase);
           assertEquals(aoCurrentPhase.getPhaseType(), PhaseType.EVERGREEN);

        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        }
    }
}
