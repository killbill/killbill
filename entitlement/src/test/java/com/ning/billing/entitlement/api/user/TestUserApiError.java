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
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.entitlement.api.TestApiBase;
import com.ning.billing.entitlement.api.ApiTestListener.NextEvent;
import com.ning.billing.entitlement.glue.MockEngineModuleMemory;
import com.ning.billing.util.clock.DefaultClock;
import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.annotation.Nullable;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestUserApiError extends TestApiBase {


    @Override
    protected Injector getInjector() {
        return Guice.createInjector(Stage.DEVELOPMENT, new MockEngineModuleMemory());
    }


    @Test(enabled=true, groups={"fast"})
    public void testCreateSubscriptionBadCatalog() {
        // WRONG PRODUCTS
        tCreateSubscriptionInternal(bundle.getId(), null, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.CAT_NULL_PRODUCT_NAME);
        tCreateSubscriptionInternal(bundle.getId(), "Whatever", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.CAT_NO_SUCH_PRODUCT);


        // TODO: MARTIN TO FIX WITH CORRECT ERROR CODE. RIGHT NOW NPE

        // WRONG BILLING PERIOD
        tCreateSubscriptionInternal(bundle.getId(), "Shotgun", null, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.CAT_PLAN_NOT_FOUND);
        // WRONG PLAN SET
        tCreateSubscriptionInternal(bundle.getId(), "Shotgun", BillingPeriod.ANNUAL, "Whatever", ErrorCode.CAT_PRICE_LIST_NOT_FOUND);

    }

    @Test(enabled=true, groups={"fast"})
    public void testCreateSubscriptionNoBundle() {
        tCreateSubscriptionInternal(null, "Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.ENT_CREATE_NO_BUNDLE);
    }

    @Test(enabled=true, groups={"fast"})
    public void testCreateSubscriptionNoBP() {
        tCreateSubscriptionInternal(bundle.getId(), "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.ENT_CREATE_NO_BP);
    }

    @Test(enabled=true, groups={"fast"})
    public void testCreateSubscriptionBPExists() {
        try {
            createSubscription("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME);
            tCreateSubscriptionInternal(bundle.getId(), "Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.ENT_CREATE_BP_EXISTS);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertFalse(true);
        }
    }

    @Test(enabled=true, groups={"fast"})
    public void testRecreateSubscriptionBPNotCancelled() {
        try {
            SubscriptionData subscription = createSubscription("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME);
            try {
                subscription.recreate(getProductSpecifier("Pistol", PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, null), clock.getUTCNow(), context);
                Assert.assertFalse(true);
            } catch (EntitlementUserApiException e) {
                assertEquals(e.getCode(), ErrorCode.ENT_RECREATE_BAD_STATE.getCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertFalse(true);
        }
    }

    @Test(enabled=true, groups={"fast"})
    public void testCreateSubscriptionAddOnNotAvailable() {
        try {
            UUID accountId = UUID.randomUUID();
            SubscriptionBundle aoBundle = entitlementApi.createBundleForAccount(accountId, "myAOBundle", context);
            createSubscriptionWithBundle(aoBundle.getId(), "Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
            tCreateSubscriptionInternal(aoBundle.getId(), "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.ENT_CREATE_AO_NOT_AVAILABLE);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertFalse(true);
        }
    }

    @Test(enabled=true, groups={"fast"})
    public void testCreateSubscriptionAddOnIncluded() {
        try {
            UUID accountId = UUID.randomUUID();
            SubscriptionBundle aoBundle = entitlementApi.createBundleForAccount(accountId, "myAOBundle", context);
            createSubscriptionWithBundle(aoBundle.getId(), "Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
            tCreateSubscriptionInternal(aoBundle.getId(), "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.ENT_CREATE_AO_ALREADY_INCLUDED);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertFalse(true);
        }
    }


    private void tCreateSubscriptionInternal(@Nullable UUID bundleId, @Nullable String productName,
            @Nullable BillingPeriod term, String planSet, ErrorCode expected)  {
        try {
            entitlementApi.createSubscription(bundleId,
                    getProductSpecifier(productName, planSet, term, null),
                    clock.getUTCNow(), context);
            Assert.fail("Exception expected, error code: " + expected);
        } catch (EntitlementUserApiException e) {
            assertEquals(e.getCode(), expected.getCode());
            try {
                log.info(e.getMessage());
            } catch (Throwable el) {
                assertFalse(true);
            }
        }
    }


    @Test(enabled=true, groups={"fast"})
    public void testChangeSubscriptionNonActive() {
        try {
            Subscription subscription = createSubscription("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME);

            testListener.pushExpectedEvent(NextEvent.CANCEL);
            subscription.cancel(clock.getUTCNow(), false, context);
            try {
                subscription.changePlan("Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, clock.getUTCNow(), context);
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


    @Test(enabled=true, groups={"fast"})
    public void testChangeSubscriptionFutureCancelled() {
        try {
            Subscription subscription = createSubscription("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
            PlanPhase trialPhase = subscription.getCurrentPhase();

            // MOVE TO NEXT PHASE
            PlanPhase currentPhase = subscription.getCurrentPhase();
            testListener.pushExpectedEvent(NextEvent.PHASE);
            clock.setDeltaFromReality(currentPhase.getDuration(), DAY_IN_MS);
            assertTrue(testListener.isCompleted(3000));


            // SET CTD TO CANCEL IN FUTURE
            DateTime expectedPhaseTrialChange = DefaultClock.addDuration(subscription.getStartDate(), trialPhase.getDuration());
            Duration ctd = getDurationMonth(1);
            DateTime newChargedThroughDate = DefaultClock.addDuration(expectedPhaseTrialChange, ctd);
            billingApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate, context);

            subscription = entitlementApi.getSubscriptionFromId(subscription.getId());

            subscription.cancel(clock.getUTCNow(), false, context);
            try {
                subscription.changePlan("Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, clock.getUTCNow(), context);
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


    @Test(enabled=false, groups={"fast"})
    public void testCancelBadState() {
    }

    @Test(enabled=true, groups={"fast"})
    public void testUncancelBadState() {
        try {
            Subscription subscription = createSubscription("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

            try {
                subscription.uncancel(context);
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
