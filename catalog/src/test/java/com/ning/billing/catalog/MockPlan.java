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

package com.ning.billing.catalog;

public class MockPlan extends DefaultPlan {

    public static MockPlan createBicycleTrialEvergreen1USD(final int trialDurationInDays) {
        return new MockPlan("BicycleTrialEvergreen1USD",
                            MockProduct.createBicycle(),
                            new DefaultPlanPhase[]{MockPlanPhase.createTrial(trialDurationInDays)},
                            MockPlanPhase.create1USDMonthlyEvergreen(),
                            -1);
    }

    public static MockPlan createBicycleTrialEvergreen1USD() {
        return new MockPlan("BicycleTrialEvergreen1USD",
                            MockProduct.createBicycle(),
                            new DefaultPlanPhase[]{MockPlanPhase.create30DayTrial()},
                            MockPlanPhase.create1USDMonthlyEvergreen(),
                            -1);
    }

    public static MockPlan createSportsCarTrialEvergreen100USD() {
        return new MockPlan("SportsCarTrialEvergreen100USD",
                            MockProduct.createSportsCar(),
                            new DefaultPlanPhase[]{MockPlanPhase.create30DayTrial()},
                            MockPlanPhase.createUSDMonthlyEvergreen("100.00", null),
                            -1);
    }

    public static MockPlan createPickupTrialEvergreen10USD() {
        return new MockPlan("PickupTrialEvergreen10USD",
                            MockProduct.createPickup(),
                            new DefaultPlanPhase[]{MockPlanPhase.create30DayTrial()},
                            MockPlanPhase.createUSDMonthlyEvergreen("10.00", null),
                            -1);
    }

    public static MockPlan createJetTrialEvergreen1000USD() {
        return new MockPlan("JetTrialEvergreen1000USD",
                            MockProduct.createJet(),
                            new DefaultPlanPhase[]{MockPlanPhase.create30DayTrial()},
                            MockPlanPhase.create1USDMonthlyEvergreen(),
                            -1);
    }

    public static MockPlan createJetTrialFixedTermEvergreen1000USD() {
        return new MockPlan("JetTrialEvergreen1000USD",
                            MockProduct.createJet(),
                            new DefaultPlanPhase[]{MockPlanPhase.create30DayTrial(), MockPlanPhase.createUSDMonthlyFixedTerm("500.00", null, 6)},
                            MockPlanPhase.create1USDMonthlyEvergreen(),
                            -1);
    }

    public static MockPlan createHornMonthlyNoTrial1USD() {
        return new MockPlan("Horn1USD",
                            MockProduct.createHorn(),
                            new DefaultPlanPhase[]{},
                            MockPlanPhase.create1USDMonthlyEvergreen(),
                            -1);
    }

    public MockPlan() {
        this("BicycleTrialEvergreen1USD",
             MockProduct.createBicycle(),
             new DefaultPlanPhase[]{MockPlanPhase.create30DayTrial()},
             MockPlanPhase.create1USDMonthlyEvergreen(),
             -1);
    }

    public MockPlan(final String name, final DefaultProduct product, final DefaultPlanPhase[] planPhases, final DefaultPlanPhase finalPhase, final int plansAllowedInBundle) {
        setName(name);
        setProduct(product);
        setFinalPhase(finalPhase);
        setInitialPhases(planPhases);
        setPlansAllowedInBundle(plansAllowedInBundle);

        finalPhase.setPlan(this);
        for (final DefaultPlanPhase pp : planPhases) {
            pp.setPlan(this);
        }
    }

    public static MockPlan createBicycleNoTrialEvergreen1USD() {
        return new MockPlan("BicycleNoTrialEvergreen1USD",
                            MockProduct.createBicycle(),
                            new DefaultPlanPhase[]{},
                            MockPlanPhase.createUSDMonthlyEvergreen("1.0", null),
                            -1);
    }

    public MockPlan(final MockPlanPhase mockPlanPhase) {
        setName("Test");
        setProduct(MockProduct.createBicycle());
        setFinalPhase(mockPlanPhase);

        mockPlanPhase.setPlan(this);
    }

    public MockPlan(final String planName) {
        setName(planName);
        setProduct(new MockProduct());
        setFinalPhase(new MockPlanPhase(this));
        setInitialPhases(null);
        setPlansAllowedInBundle(1);
    }

    public static DefaultPlan[] createAll() {
        return new DefaultPlan[]{
                createBicycleTrialEvergreen1USD(),
                createBicycleNoTrialEvergreen1USD(),
                createPickupTrialEvergreen10USD(),
                createSportsCarTrialEvergreen100USD(),
                createJetTrialEvergreen1000USD(),
                createJetTrialFixedTermEvergreen1000USD(),
                createHornMonthlyNoTrial1USD()
        };
    }


}
