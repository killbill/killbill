/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.catalog;

import java.util.Collection;
import java.util.Date;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.Plan;

import com.google.common.collect.ImmutableList;

public class MockPlan extends DefaultPlan {

    public static MockPlan createBicycleTrialEvergreen1USD(final int trialDurationInDays) {
        return new MockPlan("1-BicycleTrialEvergreen1USD",
                            MockProduct.createBicycle(),
                            new DefaultPlanPhase[]{MockPlanPhase.createTrial(trialDurationInDays)},
                            MockPlanPhase.create1USDMonthlyEvergreen(),
                            -1);
    }

    public static MockPlan createBicycleTrialEvergreen1USD() {
        return new MockPlan("1-BicycleTrialEvergreen1USD",
                            MockProduct.createBicycle(),
                            new DefaultPlanPhase[]{MockPlanPhase.create30DayTrial()},
                            MockPlanPhase.create1USDMonthlyEvergreen(),
                            -1);
    }

    public static MockPlan createSportsCarTrialEvergreen100USD() {
        return new MockPlan("4-SportsCarTrialEvergreen100USD",
                            MockProduct.createSportsCar(),
                            new DefaultPlanPhase[]{MockPlanPhase.create30DayTrial()},
                            MockPlanPhase.createUSDMonthlyEvergreen("100.00", null),
                            -1);
    }

    public static MockPlan createPickupTrialEvergreen10USD() {
        return new MockPlan("3-PickupTrialEvergreen10USD",
                            MockProduct.createPickup(),
                            new DefaultPlanPhase[]{MockPlanPhase.create30DayTrial()},
                            MockPlanPhase.createUSDMonthlyEvergreen("10.00", null),
                            -1);
    }

    public static MockPlan createJetTrialEvergreen1000USD() {
        return new MockPlan("5-JetTrialEvergreen1000USD",
                            MockProduct.createJet(),
                            new DefaultPlanPhase[]{MockPlanPhase.create30DayTrial()},
                            MockPlanPhase.create1USDMonthlyEvergreen(),
                            -1);
    }

    public static MockPlan createJetTrialFixedTermEvergreen1000USD() {
        return new MockPlan("6-JetTrialEvergreen1000USD",
                            MockProduct.createJet(),
                            new DefaultPlanPhase[]{MockPlanPhase.create30DayTrial(), MockPlanPhase.createUSDMonthlyFixedTerm("500.00", null, 6)},
                            MockPlanPhase.create1USDMonthlyEvergreen(),
                            -1);
    }

    public static MockPlan createHornMonthlyNoTrial1USD() {
        return new MockPlan("7-Horn1USD",
                            MockProduct.createHorn(),
                            new DefaultPlanPhase[]{},
                            MockPlanPhase.create1USDMonthlyEvergreen(),
                            -1);
    }

    public MockPlan() {
        this("1-BicycleTrialEvergreen1USD",
             MockProduct.createBicycle(),
             new DefaultPlanPhase[]{MockPlanPhase.create30DayTrial()},
             MockPlanPhase.create1USDMonthlyEvergreen(),
             -1);
    }

    public MockPlan(final String name, final DefaultProduct product, final DefaultPlanPhase[] planPhases, final DefaultPlanPhase finalPhase, final int plansAllowedInBundle) {
        this.staticCatalog = new StandaloneCatalog(new Date());
        setName(name);
        setProduct(product);
        setFinalPhase(finalPhase);
        setInitialPhases(planPhases);
        setPlansAllowedInBundle(plansAllowedInBundle);
        setRecurringBillingMode(BillingMode.IN_ADVANCE);
        setPriceListName(DefaultPriceListSet.DEFAULT_PRICELIST_NAME);

        finalPhase.setPlan(this);
        for (final DefaultPlanPhase pp : planPhases) {
            pp.setPlan(this);
        }
    }

    public static MockPlan createBicycleNoTrialEvergreen1USD() {
        return new MockPlan("2-BicycleNoTrialEvergreen1USD",
                            MockProduct.createBicycle(),
                            new DefaultPlanPhase[]{},
                            MockPlanPhase.createUSDMonthlyEvergreen("1.0", null),
                            -1);
    }

    public MockPlan(final MockPlanPhase mockPlanPhase) {
        setName("Test");
        setProduct(MockProduct.createBicycle());
        setFinalPhase(mockPlanPhase);
        setRecurringBillingMode(BillingMode.IN_ADVANCE);
        mockPlanPhase.setPlan(this);
    }

    public MockPlan(final String planName) {
        setName(planName);
        setProduct(new MockProduct());
        setFinalPhase(new MockPlanPhase(this));
        setInitialPhases(null);
        setRecurringBillingMode(BillingMode.IN_ADVANCE);
        setPlansAllowedInBundle(1);
    }

    public static Collection<Plan> createAll() {
        return ImmutableList.<Plan>of(createBicycleTrialEvergreen1USD(),
                               createBicycleNoTrialEvergreen1USD(),
                               createPickupTrialEvergreen10USD(),
                               createSportsCarTrialEvergreen100USD(),
                               createJetTrialEvergreen1000USD(),
                               createJetTrialFixedTermEvergreen1000USD(),
                               createHornMonthlyNoTrial1USD());
    }


}
