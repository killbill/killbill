/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.subscription.api.user;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.subscription.SubscriptionTestSuiteNoDB;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.exceptions.SubscriptionBaseError;
import org.killbill.clock.DefaultClock;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestUserApiError extends SubscriptionTestSuiteNoDB {

    @Test(groups = "fast")
    public void testCreateSubscriptionBadCatalog() {
        // WRONG PRODUCTS
        tCreateSubscriptionInternal(bundle, null, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.CAT_NULL_PRODUCT_NAME);
        tCreateSubscriptionInternal(bundle, "Whatever", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.CAT_NO_SUCH_PRODUCT);

        // TODO: MARTIN TO FIX WITH CORRECT ERROR CODE. RIGHT NOW NPE

        // WRONG BILLING PERIOD
        tCreateSubscriptionInternal(bundle, "Shotgun", null, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.CAT_NULL_BILLING_PERIOD);
        // WRONG PLAN SET
        tCreateSubscriptionInternal(bundle, "Shotgun", BillingPeriod.ANNUAL, "Whatever", ErrorCode.CAT_PRICE_LIST_NOT_FOUND);
    }

    @Test(groups = "fast")
    public void testCreateSubscriptionNoBundle() throws SubscriptionBaseApiException {
        testUtil.createSubscription(null, "Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME);
    }

    @Test(groups = "fast")
    public void testCreateSubscriptionNoBP() {
        tCreateSubscriptionInternal(bundle, "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.SUB_CREATE_NO_BP);
    }

    @Test(groups = "fast")
    public void testCreateSubscriptionAddOnNotAvailable() throws SubscriptionBaseApiException {
        testUtil.createSubscription(bundle, "Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
        tCreateSubscriptionInternal(bundle, "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.SUB_CREATE_AO_NOT_AVAILABLE);
    }

    @Test(groups = "fast")
    public void testCreateSubscriptionAddOnIncluded() throws SubscriptionBaseApiException {
        testUtil.createSubscription(bundle, "Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
        tCreateSubscriptionInternal(bundle, "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.SUB_CREATE_AO_ALREADY_INCLUDED);
    }

    private void tCreateSubscriptionInternal(@Nullable final SubscriptionBaseBundle bundle, @Nullable final String productName,
                                             @Nullable final BillingPeriod term, final String planSet, final ErrorCode expected) {
        try {
            testUtil.createSubscription(true, bundle, productName, term, planSet);
            Assert.fail("Exception expected, error code: " + expected);
        } catch (final SubscriptionBaseApiException e) {
            assertEquals(e.getCode(), expected.getCode());
        }
    }

    @Test(groups = "fast")
    public void testChangeSubscriptionNonActive() throws SubscriptionBaseApiException {
        final SubscriptionBase subscription = testUtil.createSubscription(bundle, "Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME);

        testListener.pushExpectedEvent(NextEvent.CANCEL);
        subscription.cancelWithDate(clock.getUTCNow(), callContext);
        assertListenerStatus();

        try {
            final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier("Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
            subscription.changePlanWithDate(new DefaultEntitlementSpecifier(planPhaseSpecifier), clock.getUTCNow(), callContext);
            Assert.fail("Exception expected, error code: " + ErrorCode.SUB_CHANGE_NON_ACTIVE);
        } catch (final SubscriptionBaseApiException e) {
            assertEquals(e.getCode(), ErrorCode.SUB_CHANGE_NON_ACTIVE.getCode());
        }
    }

    @Test(groups = "fast")
    public void testChangeSubscriptionWithPolicy() throws Exception {
        final SubscriptionBase subscription = testUtil.createSubscription(bundle, "Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME);

        try {
            final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier("Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
            subscription.changePlanWithPolicy(new DefaultEntitlementSpecifier(planPhaseSpecifier), BillingActionPolicy.ILLEGAL, callContext);
            Assert.fail("Call changePlanWithPolicy should have failed");
        } catch (final SubscriptionBaseError error) {
            assertTrue(true);
            assertEquals(subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), internalCallContext).getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);
        }

        // Assume the call takes less than a second
        final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier("Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
        assertEquals(DefaultClock.truncateMs(subscription.changePlanWithPolicy(new DefaultEntitlementSpecifier(planPhaseSpecifier), BillingActionPolicy.IMMEDIATE, callContext)),
                     DefaultClock.truncateMs(clock.getUTCNow()));
        assertEquals(subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), internalCallContext).getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.MONTHLY);
    }

    @Test(groups = "fast")
    public void testChangeSubscriptionFutureCancelled() throws SubscriptionBaseApiException {
        SubscriptionBase subscription = testUtil.createSubscription(bundle, "Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
        final PlanPhase trialPhase = subscription.getCurrentPhase();

        // MOVE TO NEXT PHASE
        final PlanPhase currentPhase = subscription.getCurrentPhase();
        testListener.pushExpectedEvent(NextEvent.PHASE);
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
        clock.addDeltaFromReality(it.toDurationMillis());
        assertListenerStatus();

        // SET CTD TO CANCEL IN FUTURE
        final DateTime expectedPhaseTrialChange = TestSubscriptionHelper.addDuration(subscription.getStartDate(), trialPhase.getDuration());
        final Duration ctd = testUtil.getDurationMonth(1);
        final DateTime newChargedThroughDate = TestSubscriptionHelper.addDuration(expectedPhaseTrialChange, ctd);
        subscriptionInternalApi.setChargedThroughDate(subscription.getId(), newChargedThroughDate, internalCallContext);

        subscription = subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), internalCallContext);

        subscription.cancelWithPolicy(BillingActionPolicy.END_OF_TERM, -1, callContext);
        try {
            final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier("Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
            subscription.changePlanWithDate(new DefaultEntitlementSpecifier(planPhaseSpecifier), clock.getUTCNow(), callContext);
            Assert.fail("Exception expected, error code: " + ErrorCode.SUB_CHANGE_FUTURE_CANCELLED);
        } catch (final SubscriptionBaseApiException e) {
            assertEquals(e.getCode(), ErrorCode.SUB_CHANGE_FUTURE_CANCELLED.getCode());
        }

        assertListenerStatus();
    }

    @Test(groups = "fast")
    public void testUncancelBadState() throws SubscriptionBaseApiException {
        final SubscriptionBase subscription = testUtil.createSubscription(bundle, "Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);

        try {
            subscription.uncancel(callContext);
            Assert.fail("Exception expected, error code: " + ErrorCode.SUB_UNCANCEL_BAD_STATE);
        } catch (final SubscriptionBaseApiException e) {
            assertEquals(e.getCode(), ErrorCode.SUB_UNCANCEL_BAD_STATE.getCode());
        }
        assertListenerStatus();
    }
}
