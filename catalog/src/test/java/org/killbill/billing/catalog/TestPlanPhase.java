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

import java.math.BigDecimal;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.xmlloader.ValidationErrors;

public class TestPlanPhase extends CatalogTestSuiteNoDB {

    @Test(groups = "fast")
    public void testValidation() {
        final MockCatalog catalog = new MockCatalog();

        DefaultPlanPhase pp = MockPlanPhase.createUSDMonthlyEvergreen(null, "1.00").setPlan(MockPlan.createBicycleNoTrialEvergreen1USD());
        pp.initialize(catalog, null);

        ValidationErrors errors = pp.validate(catalog, new ValidationErrors());
        errors.log(log);
        Assert.assertEquals(errors.size(), 1);

        pp = MockPlanPhase.createUSDMonthlyEvergreen("1.00", null).setRecurring(new MockRecurring(BillingPeriod.NO_BILLING_PERIOD, MockInternationalPrice.createUSD("1.00")).setPhase(pp)).setPlan(MockPlan.createBicycleNoTrialEvergreen1USD());
        pp.initialize(catalog, null);
        errors = pp.validate(catalog, new ValidationErrors());
        errors.log(log);
        Assert.assertEquals(errors.size(), 1);
    }

    @Test(groups = "fast")
    public void testPhaseNames() throws CatalogApiException {
        final String planName = "Foo";
        final String planNameExt = planName + "-";

        final DefaultPlan p = MockPlan.createBicycleNoTrialEvergreen1USD().setName(planName);
        final DefaultPlanPhase ppDiscount = MockPlanPhase.create1USDMonthlyEvergreen().setPhaseType(PhaseType.DISCOUNT).setPlan(p);
        final DefaultPlanPhase ppTrial = MockPlanPhase.create30DayTrial().setPhaseType(PhaseType.TRIAL).setPlan(p);
        final DefaultPlanPhase ppEvergreen = MockPlanPhase.create1USDMonthlyEvergreen().setPhaseType(PhaseType.EVERGREEN).setPlan(p);
        final DefaultPlanPhase ppFixedTerm = MockPlanPhase.create1USDMonthlyEvergreen().setPhaseType(PhaseType.FIXEDTERM).setPlan(p);

        final String ppnDiscount = DefaultPlanPhase.phaseName(p.getName(), ppDiscount.getPhaseType());
        final String ppnTrial = DefaultPlanPhase.phaseName(p.getName(), ppTrial.getPhaseType());
        final String ppnEvergreen = DefaultPlanPhase.phaseName(p.getName(), ppEvergreen.getPhaseType());
        final String ppnFixedTerm = DefaultPlanPhase.phaseName(p.getName(), ppFixedTerm.getPhaseType());

        Assert.assertEquals(ppnTrial, planNameExt + "trial");
        Assert.assertEquals(ppnEvergreen, planNameExt + "evergreen");
        Assert.assertEquals(ppnFixedTerm, planNameExt + "fixedterm");
        Assert.assertEquals(ppnDiscount, planNameExt + "discount");

        Assert.assertEquals(DefaultPlanPhase.planName(ppnDiscount), planName);
        Assert.assertEquals(DefaultPlanPhase.planName(ppnTrial), planName);
        Assert.assertEquals(DefaultPlanPhase.planName(ppnEvergreen), planName);
        Assert.assertEquals(DefaultPlanPhase.planName(ppnFixedTerm), planName);
    }
}
