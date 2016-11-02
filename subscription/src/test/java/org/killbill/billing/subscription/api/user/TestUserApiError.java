/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
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
        tCreateSubscriptionInternal(bundle.getId(), null, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.CAT_NULL_PRODUCT_NAME);
        tCreateSubscriptionInternal(bundle.getId(), "Whatever", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.CAT_NO_SUCH_PRODUCT);

        // TODO: MARTIN TO FIX WITH CORRECT ERROR CODE. RIGHT NOW NPE

        // WRONG BILLING PERIOD
        tCreateSubscriptionInternal(bundle.getId(), "Shotgun", null, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.CAT_NULL_BILLING_PERIOD);
        // WRONG PLAN SET
        tCreateSubscriptionInternal(bundle.getId(), "Shotgun", BillingPeriod.ANNUAL, "Whatever", ErrorCode.CAT_PRICE_LIST_NOT_FOUND);
    }

    @Test(groups = "fast")
    public void testCreateSubscriptionNoBundle() {
        tCreateSubscriptionInternal(null, "Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.SUB_CREATE_NO_BUNDLE);
    }

    @Test(groups = "fast")
    public void testCreateSubscriptionNoBP() {
        tCreateSubscriptionInternal(bundle.getId(), "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.SUB_CREATE_NO_BP);
    }

    @Test(groups = "fast")
    public void testCreateSubscriptionBPExists() throws SubscriptionBaseApiException {
        testUtil.createSubscription(bundle, "Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME);
        tCreateSubscriptionInternal(bundle.getId(), "Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.SUB_CREATE_BP_EXISTS);
    }

    @Test(groups = "fast")
    public void testCreateSubscriptionAddOnNotAvailable() throws SubscriptionBaseApiException {
        final SubscriptionBaseBundle aoBundle = subscriptionInternalApi.createBundleForAccount(bundle.getAccountId(), "myAOBundle", internalCallContext);
        testUtil.createSubscriptionWithBundle(aoBundle.getId(), "Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        tCreateSubscriptionInternal(aoBundle.getId(), "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.SUB_CREATE_AO_NOT_AVAILABLE);
    }

    @Test(groups = "fast")
    public void testCreateSubscriptionAddOnIncluded() throws SubscriptionBaseApiException {
        final SubscriptionBaseBundle aoBundle = subscriptionInternalApi.createBundleForAccount(bundle.getAccountId(), "myAOBundle", internalCallContext);
        testUtil.createSubscriptionWithBundle(aoBundle.getId(), "Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        tCreateSubscriptionInternal(aoBundle.getId(), "Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, ErrorCode.SUB_CREATE_AO_ALREADY_INCLUDED);
    }

    private void tCreateSubscriptionInternal(@Nullable final UUID bundleId, @Nullable final String productName,
                                             @Nullable final BillingPeriod term, final String planSet, final ErrorCode expected) {
        try {
            subscriptionInternalApi.createSubscription(bundleId,
                                                       testUtil.getProductSpecifier(productName, planSet, term, null),
                                                       null, clock.getUTCNow(), false, internalCallContext);
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
        try {
            subscription.changePlanWithDate(new PlanSpecifier("Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME), null, clock.getUTCNow(), callContext);
        } catch (final SubscriptionBaseApiException e) {
            assertEquals(e.getCode(), ErrorCode.SUB_CHANGE_NON_ACTIVE.getCode());
        }
    }

    @Test(groups = "fast")
    public void testChangeSubscriptionWithPolicy() throws Exception {
        final SubscriptionBase subscription = testUtil.createSubscription(bundle, "Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME);

        try {
            subscription.changePlanWithPolicy(new PlanSpecifier("Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME), null, BillingActionPolicy.ILLEGAL, callContext);
            Assert.fail();
        } catch (final SubscriptionBaseError error) {
            assertTrue(true);
            assertEquals(subscriptionInternalApi.getSubscriptionFromId(subscription.getId(), internalCallContext).getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);
        }

        // Assume the call takes less than a second
        assertEquals(DefaultClock.truncateMs(subscription.changePlanWithPolicy(new PlanSpecifier("Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME), null, BillingActionPolicy.IMMEDIATE, callContext)),
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

        subscription.cancelWithPolicy(BillingActionPolicy.END_OF_TERM, callContext);
        try {
            subscription.changePlanWithDate(new PlanSpecifier("Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME), null, clock.getUTCNow(), callContext);
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
        } catch (final SubscriptionBaseApiException e) {
            assertEquals(e.getCode(), ErrorCode.SUB_UNCANCEL_BAD_STATE.getCode());
        }
        assertListenerStatus();
    }
}
