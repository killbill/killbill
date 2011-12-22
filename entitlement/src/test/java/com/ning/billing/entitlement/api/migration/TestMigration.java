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
import org.testng.annotations.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.TestApiBase;
import com.ning.billing.entitlement.api.ApiTestListener.NextEvent;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApiException;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementAccountMigration;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementBundleMigration;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementSubscriptionMigration;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementSubscriptionMigrationCase;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.glue.MockEngineModuleSql;

public abstract class TestMigration extends TestApiBase {


    public void testSingleBasePlan() {

        try {
            DateTime beforeMigration = clock.getUTCNow();
            EntitlementAccountMigration toBeMigrated = createAccountWithRegularBasePlan();
            DateTime afterMigration = clock.getUTCNow();

            testListener.pushExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated);
            assertTrue(testListener.isCompleted(5000));

            List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey());
            assertEquals(bundles.size(), 1);
            SubscriptionBundle bundle = bundles.get(0);

            List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId());
            assertEquals(subscriptions.size(), 1);
            Subscription subscription = subscriptions.get(0);
            assertDateWithin(subscription.getStartDate(), beforeMigration, afterMigration);
            assertEquals(subscription.getEndDate(), null);
            assertEquals(subscription.getCurrentPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "assault-rifle-annual");
        } catch (EntitlementMigrationApiException e) {
            Assert.fail("", e);
        }
    }


    public void testSingleBasePlanFutureCancelled() {

        try {

            DateTime beforeMigration = clock.getUTCNow();
            EntitlementAccountMigration toBeMigrated = createAccountWithRegularBasePlanFutreCancelled();
            DateTime afterMigration = clock.getUTCNow();

            testListener.pushExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated);
            assertTrue(testListener.isCompleted(5000));

            List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey());
            assertEquals(bundles.size(), 1);
            SubscriptionBundle bundle = bundles.get(0);
            //assertEquals(bundle.getStartDate(), effectiveDate);

            List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId());
            assertEquals(subscriptions.size(), 1);
            Subscription subscription = subscriptions.get(0);
            assertDateWithin(subscription.getStartDate(), beforeMigration, afterMigration);
            assertEquals(subscription.getCurrentPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "assault-rifle-annual");

            testListener.pushExpectedEvent(NextEvent.CANCEL);
            Duration oneYear = getDurationYear(1);
            clock.setDeltaFromReality(oneYear, 0);
            assertTrue(testListener.isCompleted(5000));

            assertDateWithin(subscription.getStartDate(), beforeMigration, afterMigration);
            assertNotNull(subscription.getEndDate());
            assertTrue(subscription.getEndDate().isAfterNow());
            assertEquals(subscription.getCurrentPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase(), null);
            assertEquals(subscription.getState(), SubscriptionState.CANCELLED);
            assertNull(subscription.getCurrentPlan());

        } catch (EntitlementMigrationApiException e) {
            Assert.fail("", e);
        }
    }

    public void testSingleBasePlanWithPendingPhase() {

        try {
            DateTime beforeMigration = clock.getUTCNow();
            EntitlementAccountMigration toBeMigrated = createAccountFuturePendingPhase();
            DateTime afterMigration = clock.getUTCNow();

            testListener.pushExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated);
            assertTrue(testListener.isCompleted(5000));

            List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey());
            assertEquals(bundles.size(), 1);
            SubscriptionBundle bundle = bundles.get(0);

            List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId());
            assertEquals(subscriptions.size(), 1);
            Subscription subscription = subscriptions.get(0);
            assertDateWithin(subscription.getStartDate(), beforeMigration, afterMigration);
            assertEquals(subscription.getEndDate(), null);
            assertEquals(subscription.getCurrentPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.TRIAL);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "assault-rifle-monthly");

            testListener.pushExpectedEvent(NextEvent.PHASE);
            Duration thirtyDays = getDurationDay(30);
            clock.setDeltaFromReality(thirtyDays, 0);
            assertTrue(testListener.isCompleted(5000));

            assertDateWithin(subscription.getStartDate(), beforeMigration, afterMigration);
            assertEquals(subscription.getEndDate(), null);
            assertEquals(subscription.getCurrentPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME);
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

            testListener.pushExpectedEvent(NextEvent.MIGRATE_ENTITLEMENT);
            migrationApi.migrate(toBeMigrated);
            assertTrue(testListener.isCompleted(5000));

            List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(toBeMigrated.getAccountKey());
            assertEquals(bundles.size(), 1);
            SubscriptionBundle bundle = bundles.get(0);

            List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId());
            assertEquals(subscriptions.size(), 1);
            Subscription subscription = subscriptions.get(0);
            assertDateWithin(subscription.getStartDate(), beforeMigration, afterMigration);
            assertEquals(subscription.getEndDate(), null);
            assertEquals(subscription.getCurrentPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME);
            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "assault-rifle-monthly");

            testListener.pushExpectedEvent(NextEvent.CHANGE);
            Duration oneMonth = getDurationMonth(1);
            clock.setDeltaFromReality(oneMonth, 0);
            assertTrue(testListener.isCompleted(5000));

            assertDateWithin(subscription.getStartDate(), beforeMigration, afterMigration);
            assertEquals(subscription.getEndDate(), null);
            assertEquals(subscription.getCurrentPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME);

            assertEquals(subscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
            assertEquals(subscription.getState(), SubscriptionState.ACTIVE);
            assertEquals(subscription.getCurrentPlan().getName(), "shotgun-annual");

        } catch (EntitlementMigrationApiException e) {
            Assert.fail("", e);
        }
    }


    private EntitlementAccountMigration createAccountWithSingleBasePlan(final List<EntitlementSubscriptionMigrationCase> cases) {

        return new EntitlementAccountMigration() {

            private final UUID accountId = UUID.randomUUID();

            @Override
            public EntitlementBundleMigration[] getBundles() {
                List<EntitlementBundleMigration> bundles = new ArrayList<EntitlementBundleMigration>();
                EntitlementBundleMigration bundle0 = new EntitlementBundleMigration() {

                    @Override
                    public EntitlementSubscriptionMigration[] getSubscriptions() {
                        EntitlementSubscriptionMigration subscription = new EntitlementSubscriptionMigration() {
                            @Override
                            public EntitlementSubscriptionMigrationCase[] getSubscriptionCases() {
                                return cases.toArray(new EntitlementSubscriptionMigrationCase[cases.size()]);
                            }
                            @Override
                            public ProductCategory getCategory() {
                                return ProductCategory.BASE;
                            }
                        };
                        EntitlementSubscriptionMigration[] result = new EntitlementSubscriptionMigration[1];
                        result[0] = subscription;
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

    private EntitlementAccountMigration createAccountWithRegularBasePlan() {
        List<EntitlementSubscriptionMigrationCase> cases = new LinkedList<EntitlementSubscriptionMigrationCase>();
        cases.add(new EntitlementSubscriptionMigrationCase() {
            @Override
            public PlanPhaseSpecifier getPlanPhaseSpecifer() {
                return new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
            }
            @Override
            public DateTime getEffectiveDate() {
                return clock.getUTCNow().minusMonths(3);
            }
            @Override
            public DateTime getCancelledDate() {
                return null;
            }
        });
        return createAccountWithSingleBasePlan(cases);
    }

    private EntitlementAccountMigration createAccountWithRegularBasePlanFutreCancelled() {
        List<EntitlementSubscriptionMigrationCase> cases = new LinkedList<EntitlementSubscriptionMigrationCase>();
        final DateTime effectiveDate = clock.getUTCNow().minusMonths(3);
        cases.add(new EntitlementSubscriptionMigrationCase() {
            @Override
            public PlanPhaseSpecifier getPlanPhaseSpecifer() {
                return new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
            }
            @Override
            public DateTime getEffectiveDate() {
                return effectiveDate;
            }
            @Override
            public DateTime getCancelledDate() {
                return effectiveDate.plusYears(1);
            }
        });
        return createAccountWithSingleBasePlan(cases);
    }


    private EntitlementAccountMigration createAccountFuturePendingPhase() {
        List<EntitlementSubscriptionMigrationCase> cases = new LinkedList<EntitlementSubscriptionMigrationCase>();
        final DateTime trialDate = clock.getUTCNow().minusDays(10);
        cases.add(new EntitlementSubscriptionMigrationCase() {
            @Override
            public PlanPhaseSpecifier getPlanPhaseSpecifer() {
                return new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.TRIAL);
            }
            @Override
            public DateTime getEffectiveDate() {
                return trialDate;
            }
            @Override
            public DateTime getCancelledDate() {
                return trialDate.plusDays(30);
            }
        });
        cases.add(new EntitlementSubscriptionMigrationCase() {
            @Override
            public PlanPhaseSpecifier getPlanPhaseSpecifer() {
                return new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
            }
            @Override
            public DateTime getEffectiveDate() {
                return trialDate.plusDays(31);
            }
            @Override
            public DateTime getCancelledDate() {
                return null;
            }
        });
        return createAccountWithSingleBasePlan(cases);
    }

    private EntitlementAccountMigration createAccountFuturePendingChange() {
        List<EntitlementSubscriptionMigrationCase> cases = new LinkedList<EntitlementSubscriptionMigrationCase>();
        final DateTime effectiveDate = clock.getUTCNow().minusDays(10);
        cases.add(new EntitlementSubscriptionMigrationCase() {
            @Override
            public PlanPhaseSpecifier getPlanPhaseSpecifer() {
                return new PlanPhaseSpecifier("Assault-Rifle", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
            }
            @Override
            public DateTime getEffectiveDate() {
                return effectiveDate;
            }
            @Override
            public DateTime getCancelledDate() {
                return effectiveDate.plusMonths(1);
            }
        });
        cases.add(new EntitlementSubscriptionMigrationCase() {
            @Override
            public PlanPhaseSpecifier getPlanPhaseSpecifer() {
                return new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
            }
            @Override
            public DateTime getEffectiveDate() {
                return effectiveDate.plusMonths(1).plusDays(1);
            }
            @Override
            public DateTime getCancelledDate() {
                return null;
            }
        });
        return createAccountWithSingleBasePlan(cases);
    }

}
