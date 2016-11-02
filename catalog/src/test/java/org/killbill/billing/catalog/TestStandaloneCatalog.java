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

import org.killbill.billing.catalog.api.Plan;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PhaseType;

import com.google.common.collect.ImmutableList;

public class TestStandaloneCatalog extends CatalogTestSuiteNoDB {

    @Test(groups = "fast")
    public void testFindPhase() throws CatalogApiException {
        final DefaultPlanPhase phaseTrial1 = new MockPlanPhase().setPhaseType(PhaseType.TRIAL);
        final DefaultPlanPhase phaseTrial2 = new MockPlanPhase().setPhaseType(PhaseType.TRIAL);
        final DefaultPlanPhase phaseDiscount1 = new MockPlanPhase().setPhaseType(PhaseType.DISCOUNT);
        final DefaultPlanPhase phaseDiscount2 = new MockPlanPhase().setPhaseType(PhaseType.DISCOUNT);

        final DefaultPlan plan1 = new MockPlan().setName("TestPlan1").setFinalPhase(phaseDiscount1).setInitialPhases(new DefaultPlanPhase[]{phaseTrial1});
        final DefaultPlan plan2 = new MockPlan().setName("TestPlan2").setFinalPhase(phaseDiscount2).setInitialPhases(new DefaultPlanPhase[]{phaseTrial2});
        phaseTrial1.setPlan(plan1);
        phaseTrial2.setPlan(plan2);
        phaseDiscount1.setPlan(plan1);
        phaseDiscount2.setPlan(plan2);

        final StandaloneCatalog cat = new MockCatalog().setPlans(ImmutableList.<Plan>of(plan1, plan2));

        Assert.assertEquals(cat.findCurrentPhase("TestPlan1-discount"), phaseDiscount1);
        Assert.assertEquals(cat.findCurrentPhase("TestPlan2-discount"), phaseDiscount2);
        Assert.assertEquals(cat.findCurrentPhase("TestPlan1-trial"), phaseTrial1);
        Assert.assertEquals(cat.findCurrentPhase("TestPlan2-trial"), phaseTrial2);
    }
}
