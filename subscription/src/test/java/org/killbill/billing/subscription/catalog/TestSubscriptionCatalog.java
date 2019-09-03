/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.subscription.catalog;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.subscription.SubscriptionTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestSubscriptionCatalog extends SubscriptionTestSuiteNoDB {

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

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/subscriptionCatalog");
        return getConfigSource(null, allExtraProperties);
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }
        super.beforeMethod();
    }

    //
    // We use shotgun-quarterly only available from dt2 (v2) and with a price change in V2a
    //
    @Test(groups = "fast", description = "See https://github.com/killbill/killbill/issues/1110")
    public void testFindPlanAcrossVersions() throws Exception {

        // Existing subscription
        final DateTime subscriptionChangePlanDate = new DateTime("2011-02-02T00:01:00+00:00"); // dt2 (v2) < subscriptionChangePlanDate < dt2a (v2a)

        Plan plan = catalog.findPlan("shotgun-quarterly", dt2, subscriptionChangePlanDate);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("249.95"));

        // We still see old price because the requested date is >= dt2a and there is no effectiveDateForExistingSubscriptions
        plan = catalog.findPlan("shotgun-quarterly", dt2a, subscriptionChangePlanDate);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("249.95"));
    }

    //
    // We use pistol-monthly available from dt1 (v1) and with a first price change in V2a, and then in v3
    // Also  dt2a < effectiveDateForExistingSubscriptions < dt3
    //

    @Test(groups = "fast")
    public void testFindPlanAcrossVersionsUsingEffectiveDateForExistingSubscriptions() throws Exception {

        // Easy cases where subscriptionChangePlanDate = requestedDate -> fetch date from the only valid version
        Plan plan = catalog.findPlan("pistol-monthly", dt1, dt1);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("29.95"));

        plan = catalog.findPlan("pistol-monthly", dt2, dt2);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));

        plan = catalog.findPlan("pistol-monthly", dEffectiveDateForExistingSubscriptions, dEffectiveDateForExistingSubscriptions);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));

        plan = catalog.findPlan("pistol-monthly", dt3, dt3);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("49.95"));

        // Case with an existing subscription (prior first price change)
        final DateTime subscriptionChangePlanDate = new DateTime("2011-01-01T00:01:00+00:00"); // dt1 (v1) < subscriptionChangePlanDate < dt2 (v2)

        // Returns old price because of effectiveDateForExistingSubscriptions > requestedDate = dt2
        plan = catalog.findPlan("pistol-monthly", dt2, subscriptionChangePlanDate);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("29.95"));

        // Returns nw  price because of effectiveDateForExistingSubscriptions = requestedDate = dt2
        plan = catalog.findPlan("pistol-monthly", dEffectiveDateForExistingSubscriptions, subscriptionChangePlanDate);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));

        plan = catalog.findPlan("pistol-monthly", dt3, subscriptionChangePlanDate);
        Assert.assertEquals(plan.getAllPhases()[1].getRecurring().getRecurringPrice().getPrice(Currency.USD), new BigDecimal("39.95"));
    }

    @Test(groups = "fast")
    public void testWithDeletedPlan() throws CatalogApiException {
        // We find it because this is version 2 whose effectiveDate is "2011-02-02T00:00:00+00:00"
        final StaticCatalog catalogVersion2 = catalog.versionForDate(dt2);
        catalogVersion2.findPlan("shotgun-quarterly");

        try {
            // We **don't find it** because date provided matches version 3 where plan was removed
            final StaticCatalog catalogVersion3 = catalog.versionForDate(dt3);
            catalogVersion3.findPlan("shotgun-quarterly");
            Assert.fail("Plan has been removed");
        } catch (final CatalogApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.CAT_NO_SUCH_PLAN.getCode());
        }

        // Similar test but for existing subscription: we want to find the plan in the original catalog in this case.
        // This would be called for instance when computing billing events (dt3 could be a future PHASE event for instance)
        catalog.findPlan("shotgun-quarterly", dt3, dt1);
    }

}
