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

package org.killbill.billing.catalog.rules;

import org.killbill.billing.catalog.CatalogTestSuiteNoDB;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanAlignmentCreate;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestLoadRules extends CatalogTestSuiteNoDB {

    @Test(groups = "fast")
    public void test() throws Exception {
        final StandaloneCatalog catalog = getCatalog("WeaponsHireSmall.xml");
        Assert.assertNotNull(catalog);
        final DefaultPlanRules rules = (DefaultPlanRules) catalog.getPlanRules();

        final PlanSpecifier specifier = new PlanSpecifier("Laser-Scope", BillingPeriod.MONTHLY,
                                                          "DEFAULT");

        final PlanAlignmentCreate alignment = rules.getPlanCreateAlignment(specifier);
        Assert.assertEquals(alignment, PlanAlignmentCreate.START_OF_SUBSCRIPTION);

        final PlanSpecifier specifier2 = new PlanSpecifier("Extra-Ammo", BillingPeriod.MONTHLY,
                                                           "DEFAULT");

        final PlanAlignmentCreate alignment2 = rules.getPlanCreateAlignment(specifier2);
        Assert.assertEquals(alignment2, PlanAlignmentCreate.START_OF_BUNDLE);
    }
}
