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

import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.catalog.api.UsageType;
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
        pp.initialize(catalog);

        ValidationErrors errors = pp.validate(catalog, new ValidationErrors());
        errors.log(log);
        Assert.assertEquals(errors.size(), 1);

        pp = MockPlanPhase.createUSDMonthlyEvergreen("1.00", null).setRecurring(new MockRecurring(BillingPeriod.NO_BILLING_PERIOD, MockInternationalPrice.createUSD("1.00")).setPhase(pp)).setPlan(MockPlan.createBicycleNoTrialEvergreen1USD());
        pp.initialize(catalog);
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


    @Test(groups = "fast")
    public void testEqualsWithDifferentUsageSection() {
        final DefaultPlanPhase trialPhase = new DefaultPlanPhase();
        trialPhase.setPhaseType(PhaseType.TRIAL);
        trialPhase.setDuration(new DefaultDuration().setUnit(TimeUnit.DAYS).setNumber(14));
        trialPhase.setFixed(new DefaultFixed().setFixedPrice(new DefaultInternationalPrice().setPrices(new DefaultPrice[]{new DefaultPrice().setCurrency(Currency.USD).setValue(BigDecimal.ZERO)})));
        final DefaultUsage usage = new DefaultUsage();
        usage.setName("usage");
        usage.setBillingMode(BillingMode.IN_ARREAR);
        usage.setUsageType(UsageType.CONSUMABLE);
        final DefaultTieredBlock block = new DefaultTieredBlock();
        block.setUnit(new DefaultUnit().setName("unit"));
        block.setSize(12.0);
        final DefaultTier tier = new DefaultTier();
        tier.setBlocks(new DefaultTieredBlock[]{block});
        usage.setTiers(new DefaultTier[]{tier});
        trialPhase.setUsages(new DefaultUsage[]{usage});

        final DefaultPlanPhase trialPhase2 = new DefaultPlanPhase();
        trialPhase2.setPhaseType(trialPhase.getPhaseType());
        trialPhase2.setDuration((DefaultDuration) trialPhase.getDuration());
        trialPhase2.setFixed((DefaultFixed) trialPhase.getFixed());
        trialPhase2.setUsages(null);

        Assert.assertNotEquals(trialPhase, trialPhase2, "Phases should be different as their usage section differs");
    }
}
