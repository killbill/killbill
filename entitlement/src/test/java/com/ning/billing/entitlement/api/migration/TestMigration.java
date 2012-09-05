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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.testng.Assert;

import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.TestApiBase;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementAccountMigration;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementBundleMigration;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementSubscriptionMigration;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementSubscriptionMigrationCase;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public abstract class TestMigration extends TestApiBase {
    public void testSingleBasePlan() {
        try {
            final DateTime startDate = clock.getUTCNow().minusMonths(2);
            final DateTime beforeMigration = clock.getUTCNow();
            final EntitlementAccountMigration toBeMigrated = createAccountForMigrationWithRegularBasePlan(startDate);
            final DateTime afterMigration = clock.getUTCNow();

            testListener.pushExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated, context);
            assertTrue(testListener.isCompleted(5000));

            final List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey());
            assertEquals(bundles.size(), 1);
            final SubscriptionBundle bundle = bundles.get(0);

            final List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId());
            assertEquals(subscriptions.size(), 1);
            final Subscription subscription = subscriptions.get(0);
            assertDateWithin(subscription.getStartDate(), beforeMigration, afterMigration);
            assertEquals(subscription.getEndDate(), null);
            assertEquals(subscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "assault-rifle-annual");
            assertEquals(subscription.getChargedThroughDate(), startDate.plusYears(1));

            assertListenerStatus();
        } catch (EntitlementMigrationApiException e) {
            Assert.fail("", e);
        }
    }

    public void testPlanWithAddOn() {
        try {
            final DateTime beforeMigration = clock.getUTCNow();
            final DateTime initalBPStart = clock.getUTCNow().minusMonths(3);
            final DateTime initalAddonStart = clock.getUTCNow().minusMonths(1).plusDays(7);
            final EntitlementAccountMigration toBeMigrated = createAccountForMigrationWithRegularBasePlanAndAddons(initalBPStart, initalAddonStart);
            final DateTime afterMigration = clock.getUTCNow();

            testListener.pushExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            testListener.pushExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated, context);
            assertTrue(testListener.isCompleted(5000));

            final List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey());
            assertEquals(bundles.size(), 1);
            final SubscriptionBundle bundle = bundles.get(0);

            final List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId());
            assertEquals(subscriptions.size(), 2);

            final Subscription baseSubscription = (subscriptions.get(0).getCurrentPlan().getProduct().getCategory() == ProductCategory.BASE) ?
                    subscriptions.get(0) : subscriptions.get(1);
            assertDateWithin(baseSubscription.getStartDate(), beforeMigration, afterMigration);
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

    public void testSingleBasePlanFutureCancelled() {

        try {
            final DateTime startDate = clock.getUTCNow().minusMonths(1);
            final DateTime beforeMigration = clock.getUTCNow();
            final EntitlementAccountMigration toBeMigrated = createAccountForMigrationWithRegularBasePlanFutreCancelled(startDate);
            final DateTime afterMigration = clock.getUTCNow();

            testListener.pushExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated, context);
            assertTrue(testListener.isCompleted(5000));

            final List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey());
            assertEquals(bundles.size(), 1);
            final SubscriptionBundle bundle = bundles.get(0);
            //assertEquals(bundle.getStartDate(), effectiveDate);

            final List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId());
            assertEquals(subscriptions.size(), 1);
            final Subscription subscription = subscriptions.get(0);
            assertDateWithin(subscription.getStartDate(), beforeMigration, afterMigration);
            assertEquals(subscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "assault-rifle-annual");
            assertEquals(subscription.getChargedThroughDate(), startDate.plusYears(1));

            testListener.pushExpectedEvent(NextEvent.MIGRATE_BILLING);
            testListener.pushExpectedEvent(NextEvent.CANCEL);

            final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusYears(1));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));

            assertDateWithin(subscription.getStartDate(), beforeMigration, afterMigration);
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

    public void testSingleBasePlanWithPendingPhase() {
        try {
            final DateTime trialDate = clock.getUTCNow().minusDays(10);
            final EntitlementAccountMigration toBeMigrated = createAccountForMigrationFuturePendingPhase(trialDate);

            testListener.pushExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated, context);
            assertTrue(testListener.isCompleted(5000));

            final List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey());
            assertEquals(bundles.size(), 1);
            final SubscriptionBundle bundle = bundles.get(0);

            final List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId());
            assertEquals(subscriptions.size(), 1);
            final Subscription subscription = subscriptions.get(0);

            assertEquals(subscription.getStartDate(), trialDate);
            assertEquals(subscription.getEndDate(), null);
            assertEquals(subscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.TRIAL);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "assault-rifle-monthly");
            assertEquals(subscription.getChargedThroughDate(), trialDate.plusDays(30));

            testListener.pushExpectedEvent(NextEvent.MIGRATE_BILLING);
            testListener.pushExpectedEvent(NextEvent.PHASE);

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

    public void testSingleBasePlanWithPendingChange() {
        try {
            final DateTime beforeMigration = clock.getUTCNow();
            final EntitlementAccountMigration toBeMigrated = createAccountForMigrationFuturePendingChange();
            final DateTime afterMigration = clock.getUTCNow();

            testListener.pushExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated, context);
            assertTrue(testListener.isCompleted(5000));

            final List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey());
            assertEquals(bundles.size(), 1);
            final SubscriptionBundle bundle = bundles.get(0);

            final List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId());
            assertEquals(subscriptions.size(), 1);
            final Subscription subscription = subscriptions.get(0);
            assertDateWithin(subscription.getStartDate(), beforeMigration, afterMigration);
            assertEquals(subscription.getEndDate(), null);
            assertEquals(subscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "assault-rifle-monthly");

            testListener.pushExpectedEvent(NextEvent.CHANGE);

            final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusMonths(1));
            clock.addDeltaFromReality(it.toDurationMillis());
            assertTrue(testListener.isCompleted(5000));

            assertDateWithin(subscription.getStartDate(), beforeMigration, afterMigration);
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
}
