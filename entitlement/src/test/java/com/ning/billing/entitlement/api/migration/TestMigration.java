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

package com.ning.billing.entitlement.api.migration;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementAccountMigration;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionTransitionData;
import com.ning.billing.entitlement.events.user.ApiEventType;


public class TestMigration extends EntitlementTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testSingleBasePlan() {
        try {
            final DateTime startDate = clock.getUTCNow().minusMonths(2);
            final DateTime beforeMigration = clock.getUTCNow();
            final EntitlementAccountMigration toBeMigrated = testUtil.createAccountForMigrationWithRegularBasePlan(startDate);
            final DateTime afterMigration = clock.getUTCNow();

            testListener.pushExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated, callContext);
            assertTrue(testListener.isCompleted(5000));

            final List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey(), callContext);
            assertEquals(bundles.size(), 1);
            final SubscriptionBundle bundle = bundles.get(0);

            final List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId(), callContext);
            assertEquals(subscriptions.size(), 1);
            final Subscription subscription = subscriptions.get(0);
            assertTrue(subscription.getStartDate().compareTo(startDate) == 0);
            assertEquals(subscription.getEndDate(), null);
            assertEquals(subscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "shotgun-annual");
            assertEquals(subscription.getChargedThroughDate(), startDate.plusYears(1));

            assertListenerStatus();
        } catch (EntitlementMigrationApiException e) {
            Assert.fail("", e);
        }
    }

    @Test(groups = "slow")
    public void testPlanWithAddOn() {
        try {
            final DateTime beforeMigration = clock.getUTCNow();
            final DateTime initalBPStart = clock.getUTCNow().minusMonths(3);
            final DateTime initalAddonStart = clock.getUTCNow().minusMonths(1).plusDays(7);
            final EntitlementAccountMigration toBeMigrated = testUtil.createAccountForMigrationWithRegularBasePlanAndAddons(initalBPStart, initalAddonStart);
            final DateTime afterMigration = clock.getUTCNow();

            testListener.pushExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            testListener.pushExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated, callContext);
            assertTrue(testListener.isCompleted(5000));

            final List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey(), callContext);
            assertEquals(bundles.size(), 1);
            final SubscriptionBundle bundle = bundles.get(0);

            final List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId(), callContext);
            assertEquals(subscriptions.size(), 2);

            final Subscription baseSubscription = (subscriptions.get(0).getCurrentPlan().getProduct().getCategory() == ProductCategory.BASE) ?
                    subscriptions.get(0) : subscriptions.get(1);
            assertTrue(baseSubscription.getStartDate().compareTo(initalBPStart) == 0);
            assertEquals(baseSubscription.getEndDate(), null);
            assertEquals(baseSubscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(baseSubscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
            assertEquals(baseSubscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(baseSubscription.getCurrentPlan().getName(), "shotgun-annual");
            assertEquals(baseSubscription.getChargedThroughDate(), initalBPStart.plusYears(1));

            final Subscription aoSubscription = (subscriptions.get(0).getCurrentPlan().getProduct().getCategory() == ProductCategory.ADD_ON) ?
                    subscriptions.get(0) : subscriptions.get(1);
            // initalAddonStart.plusMonths(1).minusMonths(1) may be different from initalAddonStart, depending on exact date
            // e.g : March 31 + 1 month => April 30 and April 30 - 1 month = March 30 which is != March 31 !!!!
            assertEquals(aoSubscription.getStartDate(), initalAddonStart.plusMonths(1).minusMonths(1));
            assertEquals(aoSubscription.getEndDate(), null);
            assertEquals(aoSubscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(aoSubscription.getCurrentPhase().getPhaseType(), PhaseType.DISCOUNT);
            assertEquals(aoSubscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(aoSubscription.getCurrentPlan().getName(), "telescopic-scope-monthly");
            assertEquals(aoSubscription.getChargedThroughDate(), initalAddonStart.plusMonths(1));

            assertListenerStatus();
        } catch (EntitlementMigrationApiException e) {
            Assert.fail("", e);
        }
    }

    @Test(groups = "slow")
    public void testSingleBasePlanFutureCancelled() {

        try {
            final DateTime startDate = clock.getUTCNow().minusMonths(1);
            final DateTime beforeMigration = clock.getUTCNow();
            final EntitlementAccountMigration toBeMigrated = testUtil.createAccountForMigrationWithRegularBasePlanFutreCancelled(startDate);
            final DateTime afterMigration = clock.getUTCNow();

            testListener.pushExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated, callContext);
            assertTrue(testListener.isCompleted(5000));

            final List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey(), callContext);
            assertEquals(bundles.size(), 1);
            final SubscriptionBundle bundle = bundles.get(0);
            //assertEquals(bundle.getStartDate(), effectiveDate);

            final List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId(), callContext);
            assertEquals(subscriptions.size(), 1);
            final Subscription subscription = subscriptions.get(0);
            assertTrue(subscription.getStartDate().compareTo(startDate) == 0);
            assertEquals(subscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "assault-rifle-annual");
            assertEquals(subscription.getChargedThroughDate(), startDate.plusYears(1));

            // The MIGRATE_BILLING will not be there because the subscription is cancelled at the same date so no BILLING should occur
            //testListener.pushExpectedEvent(NextEvent.MIGRATE_BILLING);
            testListener.pushExpectedEvent(NextEvent.CANCEL);

            final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusYears(1));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));

            assertTrue(subscription.getStartDate().compareTo(startDate) == 0);
            assertNotNull(subscription.getEndDate());
            assertTrue(subscription.getEndDate().isAfterNow());
            assertEquals(subscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase(), null);
            assertEquals(subscription.getState(), SubscriptionState.CANCELLED);
            assertNull(subscription.getCurrentPlan());

            assertListenerStatus();
        } catch (EntitlementMigrationApiException e) {
            Assert.fail("", e);
        }
    }

    @Test(groups = "slow")
    public void testSingleBasePlanWithPendingPhase() {
        try {
            final DateTime trialDate = clock.getUTCNow().minusDays(10);
            final EntitlementAccountMigration toBeMigrated = testUtil.createAccountForMigrationFuturePendingPhase(trialDate);

            testListener.pushExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated, callContext);
            assertTrue(testListener.isCompleted(5000));

            final List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey(), callContext);
            assertEquals(bundles.size(), 1);
            final SubscriptionBundle bundle = bundles.get(0);

            final List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId(), callContext);
            assertEquals(subscriptions.size(), 1);
            final Subscription subscription = subscriptions.get(0);

            assertEquals(subscription.getStartDate(), trialDate);
            assertEquals(subscription.getEndDate(), null);
            assertEquals(subscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.TRIAL);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "assault-rifle-monthly");
            assertEquals(subscription.getChargedThroughDate(), trialDate.plusDays(30));

            testListener.pushExpectedEvent(NextEvent.PHASE);
            testListener.pushExpectedEvent(NextEvent.MIGRATE_BILLING);

            final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(30));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));

            assertEquals(subscription.getStartDate(), trialDate);
            assertEquals(subscription.getEndDate(), null);
            assertEquals(subscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "assault-rifle-monthly");
            assertEquals(subscription.getCurrentPhase().getName(), "assault-rifle-monthly-evergreen");

            assertListenerStatus();
        } catch (EntitlementMigrationApiException e) {
            Assert.fail("", e);
        }
    }

    @Test(groups = "slow")
    public void testSingleBasePlanWithPendingChange() {
        try {
            final DateTime beforeMigration = clock.getUTCNow();
            final EntitlementAccountMigration toBeMigrated = testUtil.createAccountForMigrationFuturePendingChange();
            final DateTime afterMigration = clock.getUTCNow();

            testListener.pushExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated, callContext);
            assertTrue(testListener.isCompleted(5000));

            final List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey(), callContext);
            assertEquals(bundles.size(), 1);
            final SubscriptionBundle bundle = bundles.get(0);

            final List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId(), callContext);
            assertEquals(subscriptions.size(), 1);
            final Subscription subscription = subscriptions.get(0);
            //assertDateWithin(subscription.getStartDate(), beforeMigration, afterMigration);
            assertEquals(subscription.getEndDate(), null);
            assertEquals(subscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "assault-rifle-monthly");

            testListener.pushExpectedEvent(NextEvent.CHANGE);
            testListener.pushExpectedEvent(NextEvent.MIGRATE_BILLING);

            final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(1));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));

            //assertDateWithin(subscription.getStartDate(), beforeMigration, afterMigration);
            assertEquals(subscription.getEndDate(), null);
            assertEquals(subscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);

            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "shotgun-annual");

            assertListenerStatus();

        } catch (EntitlementMigrationApiException e) {
            Assert.fail("", e);
        }
    }

    @Test(groups = "slow")
    public void testChangePriorMigrateBilling() throws Exception {
        try {
            final DateTime startDate = clock.getUTCNow().minusMonths(2);
            final DateTime beforeMigration = clock.getUTCNow();
            final EntitlementAccountMigration toBeMigrated = testUtil.createAccountForMigrationWithRegularBasePlan(startDate);
            final DateTime afterMigration = clock.getUTCNow();

            testListener.pushExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated, callContext);
            assertTrue(testListener.isCompleted(5000));
            assertListenerStatus();

            final List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey(), callContext);
            assertEquals(bundles.size(), 1);

            final List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundles.get(0).getId(), callContext);
            assertEquals(subscriptions.size(), 1);
            final SubscriptionData subscription = (SubscriptionData) subscriptions.get(0);

            final List<SubscriptionTransitionData> transitions = subscription.getAllTransitions();
            assertEquals(transitions.size(), 2);
            final SubscriptionTransitionData initialMigrateBilling = transitions.get(1);
            assertEquals(initialMigrateBilling.getApiEventType(), ApiEventType.MIGRATE_BILLING);
            assertTrue(initialMigrateBilling.getEffectiveTransitionTime().compareTo(subscription.getChargedThroughDate()) == 0);
            assertEquals(initialMigrateBilling.getNextPlan().getName(), "shotgun-annual");
            assertEquals(initialMigrateBilling.getNextPhase().getName(), "shotgun-annual-evergreen");

            final List<SubscriptionTransitionData> billingTransitions = subscription.getBillingTransitions();
            assertEquals(billingTransitions.size(), 1);
            assertEquals(billingTransitions.get(0), initialMigrateBilling);

            // Now make an IMMEDIATE change of plan
            subscription.changePlan("Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, clock.getUTCNow(), callContext);

            final List<SubscriptionTransitionData> newTransitions = subscription.getAllTransitions();
            assertEquals(newTransitions.size(), 3);

            final SubscriptionTransitionData changeTransition = newTransitions.get(1);
            assertEquals(changeTransition.getApiEventType(), ApiEventType.CHANGE);

            final SubscriptionTransitionData newMigrateBilling = newTransitions.get(2);
            assertEquals(newMigrateBilling.getApiEventType(), ApiEventType.MIGRATE_BILLING);
            assertTrue(newMigrateBilling.getEffectiveTransitionTime().compareTo(subscription.getChargedThroughDate()) == 0);
            assertTrue(newMigrateBilling.getEffectiveTransitionTime().compareTo(initialMigrateBilling.getEffectiveTransitionTime()) == 0);
            assertEquals(newMigrateBilling.getNextPlan().getName(), "assault-rifle-monthly");
            assertEquals(newMigrateBilling.getNextPhase().getName(), "assault-rifle-monthly-evergreen");


            final List<SubscriptionTransitionData> newBillingTransitions = subscription.getBillingTransitions();
            assertEquals(newBillingTransitions.size(), 1);
            assertEquals(newBillingTransitions.get(0), newMigrateBilling);


        } catch (EntitlementMigrationApiException e) {
            Assert.fail("", e);
        }

    }
}
