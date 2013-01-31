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

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.ErrorCode;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.entitlement.EntitlementTestSuiteNoDB;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.clock.DefaultClock;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestUserApiError extends EntitlementTestSuiteNoDB {

    private final TenantContext tenantContext = Mockito.mock(TenantContext.class);

    @Test(groups = "fast")
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

    @Test(groups = "fast")
    public void testCreateSubscriptionNoBundle() {
        tCreateSubscriptionInternal(null, "Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.ENT_CREATE_NO_BUNDLE);
    }

    @Test(groups = "fast")
    public void testCreateSubscriptionNoBP() {
        tCreateSubscriptionInternal(bundle.getId(), "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.ENT_CREATE_NO_BP);
    }

    @Test(groups = "fast")
    public void testCreateSubscriptionBPExists() {
        try {
            testUtil.createSubscription(bundle, "Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME);
            tCreateSubscriptionInternal(bundle.getId(), "Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.ENT_CREATE_BP_EXISTS);
        } catch (Exception e) {
            Assert.fail(e.toString());
        }
    }

    @Test(groups = "fast")
    public void testRecreateSubscriptionBPNotCancelled() {
        try {
            final SubscriptionData subscription = testUtil.createSubscription(bundle, "Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME);
            try {
                subscription.recreate(testUtil.getProductSpecifier("Pistol", PriceListSet.DEFAULT_PRICELIST_NAME, BillingPeriod.MONTHLY, null), clock.getUTCNow(), callContext);
                Assert.assertFalse(true);
            } catch (EntitlementUserApiException e) {
                assertEquals(e.getCode(), ErrorCode.ENT_RECREATE_BAD_STATE.getCode());
            }
        } catch (Exception e) {
            Assert.fail(e.toString());
        }
    }

    @Test(groups = "fast")
    public void testCreateSubscriptionAddOnNotAvailable() {
        try {
            final UUID accountId = UUID.randomUUID();
            final SubscriptionBundle aoBundle = entitlementApi.createBundleForAccount(accountId, "myAOBundle", callContext);
            testUtil.createSubscriptionWithBundle(aoBundle.getId(), "Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
            tCreateSubscriptionInternal(aoBundle.getId(), "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.ENT_CREATE_AO_NOT_AVAILABLE);
        } catch (Exception e) {
            Assert.fail(e.toString());
        }
    }

    @Test(groups = "fast")
    public void testCreateSubscriptionAddOnIncluded() {
        log.info("Starting testCreateSubscriptionAddOnIncluded");
        try {
            final UUID accountId = UUID.randomUUID();
            final SubscriptionBundle aoBundle = entitlementApi.createBundleForAccount(accountId, "myAOBundle", callContext);
            testUtil.createSubscriptionWithBundle(aoBundle.getId(), "Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
            tCreateSubscriptionInternal(aoBundle.getId(), "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.ENT_CREATE_AO_ALREADY_INCLUDED);
        } catch (Exception e) {
            Assert.fail(e.toString());
        }
    }

    private void tCreateSubscriptionInternal(@Nullable final UUID bundleId, @Nullable final String productName,
                                             @Nullable final BillingPeriod term, final String planSet, final ErrorCode expected) {
        try {
            entitlementApi.createSubscription(bundleId,
                                              testUtil.getProductSpecifier(productName, planSet, term, null),
                                              clock.getUTCNow(), callContext);
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

    @Test(groups = "fast")
    public void testChangeSubscriptionNonActive() {
        try {
            final Subscription subscription = testUtil.createSubscription(bundle, "Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME);

            testListener.pushExpectedEvent(NextEvent.CANCEL);
            subscription.cancel(clock.getUTCNow(), callContext);
            try {
                subscription.changePlan("Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, clock.getUTCNow(), callContext);
            } catch (EntitlementUserApiException e) {
                assertEquals(e.getCode(), ErrorCode.ENT_CHANGE_NON_ACTIVE.getCode());
                try {
                    log.info(e.getMessage());
                } catch (Throwable el) {
                    assertFalse(true);
                }
            }
        } catch (Exception e) {
            Assert.fail(e.toString());
        }
    }

    @Test(groups = "fast")
    public void testChangeSubscriptionWithPolicy() throws Exception {
        final Subscription subscription = testUtil.createSubscription(bundle, "Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME);

        try {
            subscription.changePlanWithPolicy("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, clock.getUTCNow(), ActionPolicy.ILLEGAL, callContext);
            Assert.fail();
        } catch (EntitlementError error) {
            assertTrue(true);
            assertEquals(entitlementApi.getSubscriptionFromId(subscription.getId(), tenantContext).getCurrentPlan().getBillingPeriod(), BillingPeriod.ANNUAL);
        }

        assertTrue(subscription.changePlanWithPolicy("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, clock.getUTCNow(), ActionPolicy.IMMEDIATE, callContext));
        assertEquals(entitlementApi.getSubscriptionFromId(subscription.getId(), tenantContext).getCurrentPlan().getBillingPeriod(), BillingPeriod.MONTHLY);
    }

    @Test(groups = "fast")
    public void testChangeSubscriptionFutureCancelled() {
        try {
            Subscription subscription = testUtil.createSubscription(bundle, "Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
            final PlanPhase trialPhase = subscription.getCurrentPhase();

            // MOVE TO NEXT PHASE
            final PlanPhase currentPhase = subscription.getCurrentPhase();
            testListener.pushExpectedEvent(NextEvent.PHASE);
            final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));

            // SET CTD TO CANCEL IN FUTURE
            final DateTime expectedPhaseTrialChange = DefaultClock.addDuration(subscription.getStartDate(), trialPhase.getDuration());
            final Duration ctd = testUtil.getDurationMonth(1);
            final DateTime newChargedThroughDate = DefaultClock.addDuration(expectedPhaseTrialChange, ctd);
            entitlementInternalApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate, internalCallContext);

            subscription = entitlementApi.getSubscriptionFromId(subscription.getId(), tenantContext);

            subscription.cancel(clock.getUTCNow(), callContext);
            try {
                subscription.changePlan("Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, clock.getUTCNow(), callContext);
            } catch (EntitlementUserApiException e) {
                assertEquals(e.getCode(), ErrorCode.ENT_CHANGE_FUTURE_CANCELLED.getCode());
                try {
                    log.info(e.getMessage());
                } catch (Throwable el) {
                    assertFalse(true);
                }
            }

            assertListenerStatus();
        } catch (Exception e) {
            Assert.fail(e.toString());
        }
    }

    @Test(enabled = false, groups = "fast")
    public void testCancelBadState() {
    }

    @Test(groups = "fast")
    public void testUncancelBadState() {
        try {
            final Subscription subscription = testUtil.createSubscription(bundle, "Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

            try {
                subscription.uncancel(callContext);
            } catch (EntitlementUserApiException e) {
                assertEquals(e.getCode(), ErrorCode.ENT_UNCANCEL_BAD_STATE.getCode());
                try {
                    log.info(e.getMessage());
                } catch (Throwable el) {
                    assertFalse(true);
                }
            }
            assertListenerStatus();
        } catch (Exception e) {
            Assert.fail(e.toString());
        }
    }
}
