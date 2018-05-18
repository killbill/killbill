/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class TestVersionedCatalog extends CatalogTestSuiteNoDB {

    final DateTime dt0 = new DateTime("2010-01-01T00:00:00+00:00");
    // WeaponsHireSmall-1.xml
    final DateTime dt1 = new DateTime("2011-01-01T00:01:00+00:00");
    // WeaponsHireSmall-2.xml
    final DateTime dt2 = new DateTime("2011-02-02T00:01:00+00:00");
    // WeaponsHireSmall-2a.xml
    final DateTime dt2a = new DateTime("2011-02-03T00:01:00+00:00");
    // effectiveDateForExistingSubscriptions from the catalogs 2 and 2a
    final DateTime dt214 = new DateTime("2011-02-14T00:01:00+00:00");
    // WeaponsHireSmall-3.xml
    final DateTime dt3 = new DateTime("2011-03-03T00:01:00+00:00");

    private VersionedCatalog vc;

    @BeforeClass(groups = "fast")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        vc = loader.loadDefaultCatalog("versionedCatalog");
    }

    @Test(groups = "fast")
    public void testFindPlanWithDates() throws Exception {
        // We find it although the date provided is too early because we default to first catalog version (see also testErrorOnDateTooEarly below)
        final Plan newSubPlan0 = vc.findPlan("pistol-monthly", dt0, dt0);

        final Plan newSubPlan1 = vc.findPlan("pistol-monthly", dt1, dt1);
        final Plan newSubPlan2 = vc.findPlan("pistol-monthly", dt2, dt2);
        final Plan newSubPlan214 = vc.findPlan("pistol-monthly", dt214, dt214);
        final Plan newSubPlan3 = vc.findPlan("pistol-monthly", dt3, dt3);

        Assert.assertEquals(newSubPlan1.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("29.95"));
        Assert.assertEquals(newSubPlan2.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));
        Assert.assertEquals(newSubPlan214.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));
        Assert.assertEquals(newSubPlan3.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("49.95"));

        // Existing subscription

        final Plan exSubPlan2 = vc.findPlan("pistol-monthly", dt2, dt1);
        final Plan exSubPlan214 = vc.findPlan("pistol-monthly", dt214, dt1);
        final Plan exSubPlan3 = vc.findPlan("pistol-monthly", dt3, dt1);
        // Plan added in subsequent catalog (at dt2)
        final Plan exSubPlan4 = vc.findPlan("shotgun-quarterly", dt2, dt1);
        final Plan exSubPlan5 = vc.findPlan("shotgun-quarterly", dt2a, dt1);
        final Plan exSubPlan6 = vc.findPlan("shotgun-quarterly", dt214, dt1);
        final Plan exSubPlan7 = vc.findPlan("shotgun-quarterly", dt214, dt214);

        Assert.assertEquals(exSubPlan2.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("29.95"));
        Assert.assertEquals(exSubPlan214.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));
        Assert.assertEquals(exSubPlan3.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));
        Assert.assertEquals(exSubPlan4.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("249.95"));
        // Old price
        Assert.assertEquals(exSubPlan5.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("249.95"));
        // New price
        Assert.assertEquals(exSubPlan6.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("259.95"));
        Assert.assertEquals(exSubPlan7.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("259.95"));
    }

    // Similar to testFindPlanWithDates, but use the API with PlanSpecifier
    @Test(groups = "fast")
    public void testFindPlanWithDatesAndPlanSpecifier() throws Exception {
        final PlanSpecifier pistolMonthly = new PlanSpecifier("Pistol", BillingPeriod.MONTHLY, "DEFAULT");
        final PlanSpecifier shotgunQuarterly = new PlanSpecifier("Shotgun", BillingPeriod.QUARTERLY, "DEFAULT");

        // We find it although the date provided is too early because we default to first catalog version (see also testErrorOnDateTooEarly below)
        final Plan newSubPlan0 = vc.createOrFindPlan(pistolMonthly, null, dt0, dt0);

        final Plan newSubPlan1 = vc.createOrFindPlan(pistolMonthly, null, dt1, dt1);
        final Plan newSubPlan2 = vc.createOrFindPlan(pistolMonthly, null, dt2, dt2);
        final Plan newSubPlan214 = vc.createOrFindPlan(pistolMonthly, null, dt214, dt214);
        final Plan newSubPlan3 = vc.createOrFindPlan(pistolMonthly, null, dt3, dt3);

        Assert.assertEquals(newSubPlan1.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("29.95"));
        Assert.assertEquals(newSubPlan2.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));
        Assert.assertEquals(newSubPlan214.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));
        Assert.assertEquals(newSubPlan3.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("49.95"));

        // Existing subscription

        final Plan exSubPlan2 = vc.createOrFindPlan(pistolMonthly, null, dt2, dt1);
        final Plan exSubPlan214 = vc.createOrFindPlan(pistolMonthly, null, dt214, dt1);
        final Plan exSubPlan3 = vc.createOrFindPlan(pistolMonthly, null, dt3, dt1);
        // Plan added in subsequent catalog (at dt2)
        final Plan exSubPlan4 = vc.createOrFindPlan(shotgunQuarterly, null, dt2, dt1);
        final Plan exSubPlan5 = vc.createOrFindPlan(shotgunQuarterly, null, dt2a, dt1);
        final Plan exSubPlan6 = vc.createOrFindPlan(shotgunQuarterly, null, dt214, dt1);
        final Plan exSubPlan7 = vc.createOrFindPlan(shotgunQuarterly, null, dt214, dt214);

        Assert.assertEquals(exSubPlan2.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("29.95"));
        Assert.assertEquals(exSubPlan214.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));
        Assert.assertEquals(exSubPlan3.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));
        Assert.assertEquals(exSubPlan4.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("249.95"));
        // Old price
        Assert.assertEquals(exSubPlan5.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("249.95"));
        // New price
        Assert.assertEquals(exSubPlan6.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("259.95"));
        Assert.assertEquals(exSubPlan7.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("259.95"));
    }

    @Test(groups = "fast")
    public void testErrorOnDateTooEarly() throws CatalogApiException {
        // We find it although the date provided is too early because we default to first catalog version
        vc.findPlan("shotgun-monthly", dt0);

        try {
            // We **don't find it** because date is too early and not part of first catalog version
            vc.findPlan("shotgun-quarterly", dt0);
            Assert.fail("Date is too early an exception should have been thrown");
        } catch (final CatalogApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.CAT_NO_SUCH_PLAN.getCode());
        }
    }

    @Test(groups = "fast")
    public void testWithDeletedPlan() throws CatalogApiException {
        // We find it because this is version 2 whose effectiveDate is "2011-02-02T00:00:00+00:00"
        vc.findPlan("shotgun-quarterly", dt2);

        try {
            // We **don't find it** because date provided matches version 3 where plan was removed
            vc.findPlan("shotgun-quarterly", dt3);
            Assert.fail("Plan has been removed");
        } catch (final CatalogApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.CAT_NO_SUCH_PLAN.getCode());
        }

        // Similar test but for existing subscription: we want to find the plan in the original catalog in this case.
        // This would be called for instance when computing billing events (dt3 could be a future PHASE event for instance)
        vc.findPlan("shotgun-quarterly", dt3, dt1);
    }
}
