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

import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.xmlloader.ValidationException;
import org.killbill.xmlloader.XMLLoader;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

public class TestStandaloneCatalog extends CatalogTestSuiteNoDB {

    @Test(groups = "fast")
    public void testLoadCatalogWithValidationIssues() throws Exception {
        try {
            XMLLoader.getObjectFromString(Resources.getResource("CatalogWithValidationErrors.xml").toExternalForm(), StandaloneCatalog.class);
            Assert.fail();
        } catch (final ValidationException e) {
            Assert.assertEquals(e.getErrors().size(), 13);
            Assert.assertEquals(e.getErrors().get(0).getDescription(), "Invalid product for plan 'standard'");
            Assert.assertEquals(e.getErrors().get(1).getDescription(), "Duration can only have 'UNLIMITED' unit if the number is omitted");
            Assert.assertEquals(e.getErrors().get(2).getDescription(), "Finite Duration must have a well defined length");
            Assert.assertEquals(e.getErrors().get(3).getDescription(), "Initial Phase standard-trial-evergreen of plan standard-trial cannot be of type EVERGREEN");
            Assert.assertEquals(e.getErrors().get(4).getDescription(), "Final Phase standard-trial-trial of plan standard-trial cannot be of type TRIAL");
            Assert.assertEquals(e.getErrors().get(5).getDescription(), "Duplicate rule for change plan DefaultCaseChangePlanPolicy {policy=IMMEDIATE, phaseType=null, fromProduct=DefaultProduct{name='Standard', category=BASE, included=org.killbill.billing.catalog.CatalogEntityCollection@0, available=org.killbill.billing.catalog.CatalogEntityCollection@0, limits=[], catalogName='CatalogWithValidationErrors'}, fromProductCategory=null, fromBillingPeriod=null, fromPriceList=null, toProduct=null, toProductCategory=null, toBillingPeriod=null, toPriceList=null}");
            Assert.assertEquals(e.getErrors().get(6).getDescription(), "Missing default rule case for plan change");
            Assert.assertEquals(e.getErrors().get(7).getDescription(), "Duplicate rule for plan cancellation DefaultCaseCancelPolicy{policy =IMMEDIATE, phaseType =null, product=DefaultProduct{name='Standard', category=BASE, included=org.killbill.billing.catalog.CatalogEntityCollection@0, available=org.killbill.billing.catalog.CatalogEntityCollection@0, limits=[], catalogName='CatalogWithValidationErrors'}, productCategory=null, billingPeriod=null, priceList=null}");
            Assert.assertEquals(e.getErrors().get(8).getDescription(), "Missing default rule case for plan cancellation");
            Assert.assertEquals(e.getErrors().get(9).getDescription(), "Duplicate rule for plan change alignment DefaultCaseChangePlanAlignment {alignment=START_OF_BUNDLE, phaseType=null, fromProduct=null, fromProductCategory=null, fromBillingPeriod=null, fromPriceList=null, toProduct=null, toProductCategory=null, toBillingPeriod=null, toPriceList=null}");
            Assert.assertEquals(e.getErrors().get(10).getDescription(), "Duplicate rule for create plan alignment DefaultCaseCreateAlignment {alignment =START_OF_BUNDLE, product=null, productCategory=null, billingPeriod=null, priceList=null}");
            Assert.assertEquals(e.getErrors().get(11).getDescription(), "Duplicate rule for billing alignment DefaultCaseBillingAlignment {alignment=ACCOUNT, phaseType=null, product=null, productCategory=null, billingPeriod=null, priceList=null}");
            Assert.assertEquals(e.getErrors().get(12).getDescription(), "Duplicate rule for price list transition DefaultCasePriceList {fromProduct=null, fromProductCategory=null, fromBillingPeriod=null, fromPriceList=null, toPriceList=DefaultPriceList{name='DEFAULT}}");

        }
    }

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
