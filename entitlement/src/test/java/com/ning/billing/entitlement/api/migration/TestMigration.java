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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.Assert;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.ApiTestListener.NextEvent;
import com.ning.billing.entitlement.api.TestApiBase;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementAccountMigration;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementBundleMigration;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementSubscriptionMigration;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementSubscriptionMigrationCase;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import org.testng.annotations.Test;

public abstract class TestMigration extends TestApiBase {
    public void testSingleBasePlan() {

        try {
            final DateTime startDate = clock.getUTCNow().minusMonths(2);
            DateTime beforeMigration = clock.getUTCNow();
            EntitlementAccountMigration toBeMigrated = createAccountWithRegularBasePlan(startDate);
            DateTime afterMigration = clock.getUTCNow();

            testListener.pushNextApiExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated, context);
            assertTrue(testListener.isApiCompleted(5000));

            List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey());
            assertEquals(bundles.size(), 1);
            SubscriptionBundle bundle = bundles.get(0);

            List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId());
            assertEquals(subscriptions.size(), 1);
            Subscription subscription = subscriptions.get(0);
            assertDateWithin(subscription.getStartDate(), beforeMigration, afterMigration);
            assertEquals(subscription.getEndDate(), null);
            assertEquals(subscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "assault-rifle-annual");
            assertEquals(subscription.getChargedThroughDate(), startDate.plusYears(1));
        } catch (EntitlementMigrationApiException e) {
            Assert.fail("", e);
        }
    }

    public void testPlanWithAddOn() {
        try {
            DateTime beforeMigration = clock.getUTCNow();
            final DateTime initalBPStart = clock.getUTCNow().minusMonths(3);
            final DateTime initalAddonStart = clock.getUTCNow().minusMonths(1).plusDays(7);
            EntitlementAccountMigration toBeMigrated = createAccountWithRegularBasePlanAndAddons(initalBPStart, initalAddonStart);
            DateTime afterMigration = clock.getUTCNow();

            testListener.pushNextApiExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            testListener.pushNextApiExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated, context);
            assertTrue(testListener.isApiCompleted(5000));

            List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey());
            assertEquals(bundles.size(), 1);
            SubscriptionBundle bundle = bundles.get(0);

            List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId());
            assertEquals(subscriptions.size(), 2);

            Subscription baseSubscription = (subscriptions.get(0).getCurrentPlan().getProduct().getCategory() == ProductCategory.BASE) ?
                    subscriptions.get(0) : subscriptions.get(1);
            assertDateWithin(baseSubscription.getStartDate(), beforeMigration, afterMigration);
            assertEquals(baseSubscription.getEndDate(), null);
            assertEquals(baseSubscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(baseSubscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
            assertEquals(baseSubscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(baseSubscription.getCurrentPlan().getName(), "shotgun-annual");
            assertEquals(baseSubscription.getChargedThroughDate(), initalBPStart.plusYears(1));

            Subscription aoSubscription = (subscriptions.get(0).getCurrentPlan().getProduct().getCategory() == ProductCategory.ADD_ON) ?
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

        } catch (EntitlementMigrationApiException e) {
            Assert.fail("", e);
        }
    }

    public void testSingleBasePlanFutureCancelled() {

        try {

            final DateTime startDate = clock.getUTCNow().minusMonths(1);
            DateTime beforeMigration = clock.getUTCNow();
            EntitlementAccountMigration toBeMigrated = createAccountWithRegularBasePlanFutreCancelled(startDate);
            DateTime afterMigration = clock.getUTCNow();

            testListener.pushNextApiExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated, context);
            assertTrue(testListener.isApiCompleted(5000));

            List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey());
            assertEquals(bundles.size(), 1);
            SubscriptionBundle bundle = bundles.get(0);
            //assertEquals(bundle.getStartDate(), effectiveDate);

            List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId());
            assertEquals(subscriptions.size(), 1);
            Subscription subscription = subscriptions.get(0);
            assertDateWithin(subscription.getStartDate(), beforeMigration, afterMigration);
            assertEquals(subscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "assault-rifle-annual");
            assertEquals(subscription.getChargedThroughDate(), startDate.plusYears(1));

            testListener.pushNextApiExpectedEvent(NextEvent.MIGRATE_BILLING);
            testListener.pushNextApiExpectedEvent(NextEvent.CANCEL);
 //           Duration oneYear = getDurationYear(1);
 //           clock.setDeltaFromReality(oneYear, 0);
            clock.addYears(1);
            assertTrue(testListener.isApiCompleted(5000));

            assertDateWithin(subscription.getStartDate(), beforeMigration, afterMigration);
            assertNotNull(subscription.getEndDate());
            assertTrue(subscription.getEndDate().isAfterNow());
            assertEquals(subscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase(), null);
            assertEquals(subscription.getState(), SubscriptionState.CANCELLED);
            assertNull(subscription.getCurrentPlan());

        } catch (EntitlementMigrationApiException e) {
            Assert.fail("", e);
        }
    }

    public void testSingleBasePlanWithPendingPhase() {

        try {
            final DateTime trialDate = clock.getUTCNow().minusDays(10);
            EntitlementAccountMigration toBeMigrated = createAccountFuturePendingPhase(trialDate);

            testListener.pushNextApiExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated, context);
            assertTrue(testListener.isApiCompleted(5000));

            List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey());
            assertEquals(bundles.size(), 1);
            SubscriptionBundle bundle = bundles.get(0);

            List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId());
            assertEquals(subscriptions.size(), 1);
            Subscription subscription = subscriptions.get(0);

            assertEquals(subscription.getStartDate(), trialDate);
            assertEquals(subscription.getEndDate(), null);
            assertEquals(subscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.TRIAL);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "assault-rifle-monthly");
            assertEquals(subscription.getChargedThroughDate(), trialDate.plusDays(30));

            testListener.pushNextApiExpectedEvent(NextEvent.MIGRATE_BILLING);
            testListener.pushNextApiExpectedEvent(NextEvent.PHASE);
            Duration thirtyDays = getDurationDay(30);
           // clock.setDeltaFromReality(thirtyDays, 0);
            clock.addDays(30);
            assertTrue(testListener.isApiCompleted(5000));

            assertEquals(subscription.getStartDate(), trialDate);
            assertEquals(subscription.getEndDate(), null);
            assertEquals(subscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "assault-rifle-monthly");
            assertEquals(subscription.getCurrentPhase().getName(), "assault-rifle-monthly-evergreen");

        } catch (EntitlementMigrationApiException e) {
            Assert.fail("", e);
        }
    }

    public void testSingleBasePlanWithPendingChange() {

        try {
            DateTime beforeMigration = clock.getUTCNow();
            EntitlementAccountMigration toBeMigrated = createAccountFuturePendingChange();
            DateTime afterMigration = clock.getUTCNow();

            testListener.pushNextApiExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated, context);
            assertTrue(testListener.isApiCompleted(5000));

            List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey());
            assertEquals(bundles.size(), 1);
            SubscriptionBundle bundle = bundles.get(0);

            List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId());
            assertEquals(subscriptions.size(), 1);
            Subscription subscription = subscriptions.get(0);
            assertDateWithin(subscription.getStartDate(), beforeMigration, afterMigration);
            assertEquals(subscription.getEndDate(), null);
            assertEquals(subscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "assault-rifle-monthly");

            testListener.pushNextApiExpectedEvent(NextEvent.CHANGE);
            Duration oneMonth = getDurationMonth(1);
            clock.setDeltaFromReality(oneMonth, 0);
            assertTrue(testListener.isApiCompleted(5000));

            assertDateWithin(subscription.getStartDate(), beforeMigration, afterMigration);
            assertEquals(subscription.getEndDate(), null);
            assertEquals(subscription.getCurrentPriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);

            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "shotgun-annual");

        } catch (EntitlementMigrationApiException e) {
            Assert.fail("", e);
        }
    }


    private EntitlementAccountMigration createAccountTest(final List<List<EntitlementSubscriptionMigrationCaseWithCTD>> cases) {

        return new EntitlementAccountMigration() {

            private final UUID accountId = UUID.randomUUID();

            @Override
            public EntitlementBundleMigration[] getBundles() {
                List<EntitlementBundleMigration> bundles = new ArrayList<EntitlementBundleMigration>();
                EntitlementBundleMigration bundle0 = new EntitlementBundleMigration() {

                    @Override
                    public EntitlementSubscriptionMigration[] getSubscriptions() {

                        EntitlementSubscriptionMigration[] result = new EntitlementSubscriptionMigration[cases.size()];

                        for (int i = 0; i < cases.size(); i++) {

                            final List<EntitlementSubscriptionMigrationCaseWithCTD> curCases = cases.get(i);
                            EntitlementSubscriptionMigration subscription = new EntitlementSubscriptionMigration() {
                                @Override
                                public EntitlementSubscriptionMigrationCaseWithCTD[] getSubscriptionCases() {
                                    return curCases.toArray(new EntitlementSubscriptionMigrationCaseWithCTD[curCases.size()]);
                                }
                                @Override
                                public ProductCategory getCategory() {
                                    return curCases.get(0).getPlanPhaseSpecifier().getProductCategory();
                                }
                                @Override
                                public DateTime getChargedThroughDate() {
                                    for (EntitlementSubscriptionMigrationCaseWithCTD cur :curCases) {
                                        if (cur.getChargedThroughDate() != null) {
                                            return cur.getChargedThroughDate();
                                        }
                                    }
                                    return null;
                                }
                            };
                            result[i] = subscription;
                        }
                        return result;
                    }
                    @Override
                    public String getBundleKey() {
                        return "12345";
                    }
                };
                bundles.add(bundle0);
                return bundles.toArray(new EntitlementBundleMigration[bundles.size()]);
            }

            @Override
            public UUID getAccountKey() {
                return accountId;
            }
        };
    }

    private EntitlementAccountMigration createAccountWithRegularBasePlanAndAddons(final DateTime initialBPstart, final DateTime initalAddonStart) {

        List<EntitlementSubscriptionMigrationCaseWithCTD> cases = new LinkedList<EntitlementSubscriptionMigrationCaseWithCTD>();
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                initialBPstart,
                null,
                initialBPstart.plusYears(1)));

        List<EntitlementSubscriptionMigrationCaseWithCTD> firstAddOnCases = new LinkedList<EntitlementSubscriptionMigrationCaseWithCTD>();
        firstAddOnCases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.DISCOUNT),
                initalAddonStart,
                initalAddonStart.plusMonths(1),
                initalAddonStart.plusMonths(1)));
        firstAddOnCases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                initalAddonStart.plusMonths(1),
                null,
                null));


        List<List<EntitlementSubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<EntitlementSubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        input.add(firstAddOnCases);
        return createAccountTest(input);
    }

    private EntitlementAccountMigration createAccountWithRegularBasePlan(final DateTime startDate) {
        List<EntitlementSubscriptionMigrationCaseWithCTD> cases = new LinkedList<EntitlementSubscriptionMigrationCaseWithCTD>();
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                startDate,
                null,
                startDate.plusYears(1)));
        List<List<EntitlementSubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<EntitlementSubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        return createAccountTest(input);
    }

    private EntitlementAccountMigration createAccountWithRegularBasePlanFutreCancelled(final DateTime startDate) {
        List<EntitlementSubscriptionMigrationCaseWithCTD> cases = new LinkedList<EntitlementSubscriptionMigrationCaseWithCTD>();
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                startDate,
                startDate.plusYears(1),
                startDate.plusYears(1)));
        List<List<EntitlementSubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<EntitlementSubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        return createAccountTest(input);
    }


    private EntitlementAccountMigration createAccountFuturePendingPhase(final DateTime trialDate) {
        List<EntitlementSubscriptionMigrationCaseWithCTD> cases = new LinkedList<EntitlementSubscriptionMigrationCaseWithCTD>();
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.TRIAL),
                trialDate,
                trialDate.plusDays(30),
                trialDate.plusDays(30)));
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                trialDate.plusDays(30),
                null,
                null));
        List<List<EntitlementSubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<EntitlementSubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        return createAccountTest(input);
    }

    private EntitlementAccountMigration createAccountFuturePendingChange() {
        List<EntitlementSubscriptionMigrationCaseWithCTD> cases = new LinkedList<EntitlementSubscriptionMigrationCaseWithCTD>();
        final DateTime effectiveDate = clock.getUTCNow().minusDays(10);
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                effectiveDate,
                effectiveDate.plusMonths(1),
                effectiveDate.plusMonths(1)));
        cases.add(new EntitlementSubscriptionMigrationCaseWithCTD(
                new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN),
                effectiveDate.plusMonths(1).plusDays(1),
                null,
                null));
        List<List<EntitlementSubscriptionMigrationCaseWithCTD>> input = new ArrayList<List<EntitlementSubscriptionMigrationCaseWithCTD>>();
        input.add(cases);
        return createAccountTest(input);
    }


    public static class EntitlementSubscriptionMigrationCaseWithCTD implements EntitlementSubscriptionMigrationCase {

        private final PlanPhaseSpecifier pps;
        private final DateTime effDt;
        private final DateTime cancelDt;
        private final DateTime ctd;

        public EntitlementSubscriptionMigrationCaseWithCTD(PlanPhaseSpecifier pps, DateTime effDt, DateTime cancelDt, DateTime ctd) {
            this.pps = pps;
            this.cancelDt = cancelDt;
            this.effDt = effDt;
            this.ctd = ctd;
        }

        @Override
        public PlanPhaseSpecifier getPlanPhaseSpecifier() {
            return pps;
        }

        @Override
        public DateTime getEffectiveDate() {
            return effDt;
        }

        @Override
        public DateTime getCancelledDate() {
            return cancelDt;
        }

        public DateTime getChargedThroughDate() {
            return ctd;
        }
    }
}
