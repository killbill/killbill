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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.rules.DefaultPlanRules;
import org.redisson.client.codec.Codec;
import org.redisson.codec.SerializationCodec;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import io.netty.buffer.ByteBuf;

public class TestVersionedCatalog extends CatalogTestSuiteNoDB {

    // WeaponsHireSmall-1.xml
    final DateTime dt1 = new DateTime("2010-01-01T00:00:00+00:00");

    // WeaponsHireSmall-2.xml
    final DateTime dt2 = new DateTime("2011-02-02T00:01:00+00:00");

    // WeaponsHireSmall-2a.xml
    final DateTime dt2a = new DateTime("2011-02-03T00:01:00+00:00");

    // effectiveDateForExistingSubscriptions from the catalogs 2 and 2a
    final DateTime dEffectiveDateForExistingSubscriptions = new DateTime("2011-02-14T00:01:00+00:00");

    // WeaponsHireSmall-3.xml
    final DateTime dt3 = new DateTime("2011-03-03T00:01:00+00:00");

    private DefaultVersionedCatalog vc;

    @BeforeClass(groups = "fast")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        vc = loader.loadDefaultCatalog("versionedCatalog");
    }



    //
    // We use shotgun-quarterly only available from dt2 (v2) and with a price change in V2a
    //
    @Test(groups = "fast", description = "See https://github.com/killbill/killbill/issues/1110")
    public void testFindPlanAcrossVersions() throws Exception {

        // Existing subscription
        final DateTime subscriptionChangePlanDate = new DateTime("2011-02-02T00:01:00+00:00"); // dt2 (v2) < subscriptionChangePlanDate < dt2a (v2a)


        Plan plan = vc.findPlan("shotgun-quarterly", dt2, subscriptionChangePlanDate);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("249.95"));

        // We still see old price because the requested date is >= dt2a and there is no effectiveDateForExistingSubscriptions
        plan = vc.findPlan("shotgun-quarterly", dt2a, subscriptionChangePlanDate);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("249.95"));
    }



    //
    // We use pistol-monthly available from dt1 (v1) and with a first price change in V2a, and then in v3
    // Also  dt2a < effectiveDateForExistingSubscriptions < dt3
    //
    @Test(groups = "fast")
    public void testFindPlanAcrossVersionsUsingEffectiveDateForExistingSubscriptions() throws Exception {

        // Easy cases where subscriptionChangePlanDate = requestedDate -> fetch date from the only valid version
        Plan plan = vc.findPlan("pistol-monthly", dt1, dt1);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("29.95"));

        plan = vc.findPlan("pistol-monthly", dt2, dt2);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));

        plan = vc.findPlan("pistol-monthly", dEffectiveDateForExistingSubscriptions, dEffectiveDateForExistingSubscriptions);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));

        plan = vc.findPlan("pistol-monthly", dt3, dt3);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("49.95"));

        // Case with an existing subscription (prior first price change)
        final DateTime subscriptionChangePlanDate = new DateTime("2011-01-01T00:01:00+00:00"); // dt1 (v1) < subscriptionChangePlanDate < dt2 (v2)

        // Returns old price because of effectiveDateForExistingSubscriptions > requestedDate = dt2
        plan = vc.findPlan("pistol-monthly", dt2, subscriptionChangePlanDate);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("29.95"));

        // Returns nw  price because of effectiveDateForExistingSubscriptions = requestedDate = dt2
        plan = vc.findPlan("pistol-monthly", dEffectiveDateForExistingSubscriptions, subscriptionChangePlanDate);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));

        plan = vc.findPlan("pistol-monthly", dt3, subscriptionChangePlanDate);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));
    }



    // Similar to testFindPlanWithDates, but use the API with PlanSpecifier
    @Test(groups = "fast")
    public void testFindPlanWithDatesAndPlanSpecifier() throws Exception {

        final PlanSpecifier pistolMonthly = new PlanSpecifier("Pistol", BillingPeriod.MONTHLY, "DEFAULT");

        // Easy cases where subscriptionChangePlanDate = requestedDate -> fetch date from the only valid version
        Plan plan = vc.createOrFindPlan(pistolMonthly, null, dt1, dt1);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("29.95"));

        plan = vc.createOrFindPlan(pistolMonthly, null, dt2, dt2);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));

        plan = vc.createOrFindPlan(pistolMonthly, null, dEffectiveDateForExistingSubscriptions, dEffectiveDateForExistingSubscriptions);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));

        plan = vc.createOrFindPlan(pistolMonthly, null, dt3, dt3);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("49.95"));

        // Cases for existing subscription
        plan = vc.createOrFindPlan(pistolMonthly, null, dt2, dt1);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("29.95"));

        plan = vc.createOrFindPlan(pistolMonthly, null, dEffectiveDateForExistingSubscriptions, dt1);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));

        plan = vc.createOrFindPlan(pistolMonthly, null, dt3, dt1);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));

    }

    @Test(groups = "fast")
    public void testErrorOnDateTooEarly() throws CatalogApiException {
        // We find it although the date provided is too early because we default to first catalog version
        vc.findPlan("shotgun-monthly", dt1);

        try {
            // We **don't find it** because date is too early and not part of first catalog version
            vc.findPlan("shotgun-quarterly", dt1);
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

    @Test(groups = "fast")
    public void testDefaultPlanRulesExternalizable() throws IOException {
        final Codec codec = new SerializationCodec();
        final ByteBuf byteBuf = codec.getValueEncoder().encode(vc.getVersions().get(0).getPlanRules());
        final DefaultPlanRules planRules = (DefaultPlanRules) codec.getValueDecoder().decode(byteBuf, null);
        Assert.assertEquals(planRules, vc.getVersions().get(0).getPlanRules());
    }

    @Test(groups = "fast")
    public void testProductExternalizable() throws IOException {
        final Codec codec = new SerializationCodec();
        for (final Product product : vc.getVersions().get(0).getCatalogEntityCollectionProduct().getEntries()) {
            final ByteBuf byteBuf = codec.getValueEncoder().encode(product);
            final Product product2 = (Product) codec.getValueDecoder().decode(byteBuf, null);
            Assert.assertEquals(product2, product);
        }
    }

    @Test(groups = "fast")
    public void testCatalogEntityCollectionProductExternalizable() throws IOException {
        final Codec codec = new SerializationCodec();
        final ByteBuf byteBuf = codec.getValueEncoder().encode(vc.getVersions().get(0).getCatalogEntityCollectionProduct());
        final Collection products = (CatalogEntityCollection) codec.getValueDecoder().decode(byteBuf, null);
        Assert.assertEquals(products, vc.getVersions().get(0).getCatalogEntityCollectionProduct());
    }

    @Test(groups = "fast")
    public void testStandaloneCatalogExternalizable() throws IOException {
        final Codec codec = new SerializationCodec();
        final ByteBuf byteBuf = codec.getValueEncoder().encode(vc.getVersions().get(0));
        final StandaloneCatalog standaloneCatalog = (StandaloneCatalog) codec.getValueDecoder().decode(byteBuf, null);
        Assert.assertEquals(standaloneCatalog, vc.getVersions().get(0));
    }
}
