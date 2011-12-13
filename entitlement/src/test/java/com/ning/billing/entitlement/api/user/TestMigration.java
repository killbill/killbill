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
import com.ning.billing.catalog.PriceListDefault;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.alignment.MigrationPlanAligner;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApiException;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementAccountMigration;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementBundleMigration;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementSubscriptionMigration;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi.EntitlementSubscriptionMigrationCase;
import com.ning.billing.entitlement.glue.MockEngineModuleSql;

public class TestMigration extends TestUserApiBase {

    @Override
    protected Injector getInjector() {
        return Guice.createInjector(Stage.DEVELOPMENT, new MockEngineModuleSql());
    }



    @Test(enabled=true, groups="sql")
    public void testSimple() {

        try {
            migrationApi.migrate(null);
        } catch (EntitlementMigrationApiException e) {
            Assert.fail("", e);
        }
    }


    private EntitlementAccountMigration createAccountWithSingleBasePlan(final List<EntitlementSubscriptionMigrationCase> cases) {

        return new EntitlementAccountMigration() {

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
                return UUID.randomUUID();
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
                return new DateTime().minusMonths(3);
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
        final DateTime effectiveDate = new DateTime().minusMonths(3);
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
        final DateTime trialDate = new DateTime().minusDays(10);
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
        final DateTime effectiveDate = new DateTime().minusDays(10);
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
