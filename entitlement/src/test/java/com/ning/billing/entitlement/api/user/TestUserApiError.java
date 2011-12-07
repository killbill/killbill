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

import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.DefaultPriceListSet;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.entitlement.api.ApiTestListener.NextEvent;
import com.ning.billing.entitlement.glue.MockEngineModuleMemory;
import com.ning.billing.util.clock.DefaultClock;

public class TestUserApiError extends TestUserApiBase {


    @Override
    protected Injector getInjector() {
        return Guice.createInjector(Stage.DEVELOPMENT, new MockEngineModuleMemory());
    }


    @Test(enabled=true)
    public void testCreateSubscriptionBadCatalog() {
        // WRONG PRODUTCS
        tCreateSubscriptionInternal(bundle.getId(), null, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.CAT_NO_SUCH_PRODUCT);
        tCreateSubscriptionInternal(bundle.getId(), "Whatever", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.CAT_NO_SUCH_PRODUCT);


        // TODO: MARTIN TO FIX WITH CORRECT ERROR CODE. RIGHT NOW NPE

        // WRONG BILLING PERIOD
        //tCreateSubscriptionInternal(bundle.getId(), "Shotgun", null, IPriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.);
        // WRONG PLAN SET
        //tCreateSubscriptionInternal(bundle.getId(), "Shotgun", BillingPeriod.ANNUAL, null, ErrorCode.);
        //tCreateSubscriptionInternal(bundle.getId(), "Shotgun", BillingPeriod.ANNUAL, "Whatever", ErrorCode.);

    }

    @Test(enabled=true)
    public void testCreateSubscriptionNoBundle() {
        tCreateSubscriptionInternal(null, "Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.ENT_CREATE_NO_BUNDLE);
    }

    @Test(enabled=false)
    public void testCreateSubscriptionNoBP() {
        //tCreateSubscriptionInternal(bundle.getId(), "Shotgun", BillingPeriod.ANNUAL, IPriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.ENT_CREATE_NO_BP);
    }

    @Test(enabled=true)
    public void testCreateSubscriptionBPExists() {
        try {
            createSubscription("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME);
            tCreateSubscriptionInternal(bundle.getId(), "Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.ENT_CREATE_BP_EXISTS);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertFalse(true);
        }
    }

    private void tCreateSubscriptionInternal(UUID bundleId, String productName,
            BillingPeriod term, String planSet, ErrorCode expected)  {
        try {
            entitlementApi.createSubscription(bundleId, productName, term, planSet, null, clock.getUTCNow());
            assertFalse(true);
        } catch (EntitlementUserApiException e) {
            assertEquals(e.getCode(), expected.getCode());
            try {
                log.info(e.getMessage());
            } catch (Throwable el) {
                assertFalse(true);
            }
        }
    }


    @Test(enabled=true)
    public void testChangeSubscriptionNonActive() {
        try {
            Subscription subscription = createSubscription("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME);

            testListener.pushExpectedEvent(NextEvent.CANCEL);
            subscription.cancel(clock.getUTCNow(), false);
            try {
                subscription.changePlan("Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, clock.getUTCNow());
            } catch (EntitlementUserApiException e) {
                assertEquals(e.getCode(), ErrorCode.ENT_CHANGE_NON_ACTIVE.getCode());
                try {
                    log.info(e.getMessage());
                } catch (Throwable el) {
                    assertFalse(true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertFalse(true);
        }
    }


    @Test(enabled=true)
    public void testChangeSubscriptionFutureCancelled() {
        try {
            Subscription subscription = createSubscription("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

            // SET CTD TO CANCEL IN FUTURE
            PlanPhase trialPhase = subscription.getCurrentPhase();
            DateTime expectedPhaseTrialChange = DefaultClock.addDuration(subscription.getStartDate(), trialPhase.getDuration());
            Duration ctd = getDurationMonth(1);
            DateTime newChargedThroughDate = DefaultClock.addDuration(expectedPhaseTrialChange, ctd);
            billingApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate);
            subscription = entitlementApi.getSubscriptionFromId(subscription.getId());

            subscription.cancel(clock.getUTCNow(), false);
            try {
                subscription.changePlan("Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, clock.getUTCNow());
            } catch (EntitlementUserApiException e) {
                assertEquals(e.getCode(), ErrorCode.ENT_CHANGE_FUTURE_CANCELLED.getCode());
                try {
                    log.info(e.getMessage());
                } catch (Throwable el) {
                    assertFalse(true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertFalse(true);
        }
    }


    @Test(enabled=false)
    public void testCancelBadState() {
    }

    @Test(enabled=true)
    public void testUncancelBadState() {
        try {
            Subscription subscription = createSubscription("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

            try {
                subscription.uncancel();
            } catch (EntitlementUserApiException e) {
                assertEquals(e.getCode(), ErrorCode.ENT_UNCANCEL_BAD_STATE.getCode());
                try {
                    log.info(e.getMessage());
                } catch (Throwable el) {
                    assertFalse(true);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertFalse(true);
        }

    }

}
