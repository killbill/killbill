/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.Price;
import org.killbill.billing.catalog.override.DefaultPriceOverride;
import org.killbill.xmlloader.XMLLoader;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

public class TestDefaultPriceOverride extends CatalogTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testBasic() throws Exception {

        final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarAdvanced.xml").toExternalForm(), StandaloneCatalog.class);
        catalog.initialize(catalog, null);
        final Plan plan = catalog.findCurrentPlan("discount-standard-monthly");

        final List<PlanPhasePriceOverride> overrides = new ArrayList<PlanPhasePriceOverride>();
        final PlanPhasePriceOverride phase1 = new DefaultPlanPhasePriceOverride(plan.getAllPhases()[0].getName(), Currency.USD, BigDecimal.ONE, null);
        overrides.add(phase1);
        final PlanPhasePriceOverride phase3 = new DefaultPlanPhasePriceOverride(plan.getAllPhases()[2].getName(), Currency.USD, null, new BigDecimal("142.41"));
        overrides.add(phase3);

        final DefaultPlan overriddenPlan = priceOverride.getOrCreateOverriddenPlan(catalog, plan, new DateTime(catalog.getEffectiveDate()), overrides, internalCallContext);

        final Matcher m = DefaultPriceOverride.CUSTOM_PLAN_NAME_PATTERN.matcher(overriddenPlan.getName());
        assertTrue(m.matches());
        assertEquals(m.group(1), plan.getName());

        assertEquals(overriddenPlan.getProduct().getName(), plan.getProduct().getName());
        assertEquals(overriddenPlan.getRecurringBillingPeriod(), plan.getRecurringBillingPeriod());
        if (plan.getEffectiveDateForExistingSubscriptions() != null) {
            assertEquals(overriddenPlan.getEffectiveDateForExistingSubscriptions().compareTo(plan.getEffectiveDateForExistingSubscriptions()), 0);
        }
        assertNotEquals(overriddenPlan.getFinalPhase().getName(), plan.getFinalPhase().getName());
        assertEquals(overriddenPlan.getPlansAllowedInBundle(), plan.getPlansAllowedInBundle());

        assertEquals(overriddenPlan.getAllPhases().length, overriddenPlan.getAllPhases().length);
        for (int i = 0; i < overriddenPlan.getAllPhases().length; i++) {

            final DefaultPlanPhase initialPhase = (DefaultPlanPhase) plan.getAllPhases()[i];
            final DefaultPlanPhase newPhase = (DefaultPlanPhase) overriddenPlan.getAllPhases()[i];

            final PlanPhasePriceOverride override = Iterables.tryFind(overrides, new Predicate<PlanPhasePriceOverride>() {
                @Override
                public boolean apply(final PlanPhasePriceOverride input) {
                    return input.getPhaseName().equals(initialPhase.getName());
                }
            }).orNull();

            assertNotEquals(newPhase.getName(), initialPhase.getName());
            assertEquals(newPhase.getDuration(), initialPhase.getDuration());
            assertEquals(newPhase.getPhaseType(), initialPhase.getPhaseType());
            assertEquals(newPhase.getUsages().length, initialPhase.getUsages().length);
            if (initialPhase.getFixed() != null) {
                assertEquals(newPhase.getFixed().getType(), initialPhase.getFixed().getType());
                assertInternationalPrice(newPhase.getFixed().getPrice(), initialPhase.getFixed().getPrice(), override, true);
            }
            if (initialPhase.getRecurring() != null) {
                assertInternationalPrice(newPhase.getRecurring().getRecurringPrice(), initialPhase.getRecurring().getRecurringPrice(), override, false);
            }
        }
    }

    @Test(groups = "slow", expectedExceptions = CatalogApiException.class)
    public void testWithInvalidPriceOverride() throws Exception {

        final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarAdvanced.xml").toExternalForm(), StandaloneCatalog.class);
        catalog.initialize(catalog, null);

        final Plan plan = catalog.findCurrentPlan("discount-standard-monthly");

        final List<PlanPhasePriceOverride> overrides = new ArrayList<PlanPhasePriceOverride>();
        final PlanPhasePriceOverride phase1 = new DefaultPlanPhasePriceOverride(plan.getAllPhases()[0].getName(), Currency.USD, null, BigDecimal.ONE);
        overrides.add(phase1);

        priceOverride.getOrCreateOverriddenPlan(catalog, plan, new DateTime(catalog.getEffectiveDate()), overrides, internalCallContext);

    }

    @Test(groups = "slow")
    public void testGetOverriddenPlan() throws Exception {

        final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarAdvanced.xml").toExternalForm(), StandaloneCatalog.class);
        catalog.initialize(catalog, null);

        final Plan plan = catalog.findCurrentPlan("discount-standard-monthly");

        final List<PlanPhasePriceOverride> overrides = new ArrayList<PlanPhasePriceOverride>();
        final PlanPhasePriceOverride phase1 = new DefaultPlanPhasePriceOverride(plan.getAllPhases()[0].getName(), Currency.USD, BigDecimal.ONE, null);
        overrides.add(phase1);
        final PlanPhasePriceOverride phase3 = new DefaultPlanPhasePriceOverride(plan.getAllPhases()[2].getName(), Currency.USD, null, new BigDecimal("142.41"));
        overrides.add(phase3);

        final DefaultPlan overriddenPlanCreated = priceOverride.getOrCreateOverriddenPlan(catalog, plan, new DateTime(catalog.getEffectiveDate()), overrides, internalCallContext);

        System.out.println("overriddenPlanCreated = " + overriddenPlanCreated.getName());

        final DefaultPlan overriddenPlan = priceOverride.getOverriddenPlan(overriddenPlanCreated.getName(), catalog, internalCallContext);

        assertEquals(overriddenPlan.getProduct().getName(), plan.getProduct().getName());
        assertEquals(overriddenPlan.getRecurringBillingPeriod(), plan.getRecurringBillingPeriod());
        if (plan.getEffectiveDateForExistingSubscriptions() != null) {
            assertEquals(overriddenPlan.getEffectiveDateForExistingSubscriptions().compareTo(plan.getEffectiveDateForExistingSubscriptions()), 0);
        }
        assertNotEquals(overriddenPlan.getFinalPhase().getName(), plan.getFinalPhase().getName());
        assertEquals(overriddenPlan.getPlansAllowedInBundle(), plan.getPlansAllowedInBundle());

        assertEquals(overriddenPlan.getAllPhases().length, overriddenPlan.getAllPhases().length);
        for (int i = 0; i < overriddenPlan.getAllPhases().length; i++) {

            final DefaultPlanPhase initialPhase = (DefaultPlanPhase) plan.getAllPhases()[i];
            final DefaultPlanPhase newPhase = (DefaultPlanPhase) overriddenPlan.getAllPhases()[i];

            final PlanPhasePriceOverride override = Iterables.tryFind(overrides, new Predicate<PlanPhasePriceOverride>() {
                @Override
                public boolean apply(final PlanPhasePriceOverride input) {
                    return input.getPhaseName().equals(initialPhase.getName());
                }
            }).orNull();

            assertNotEquals(newPhase.getName(), initialPhase.getName());
            assertEquals(newPhase.getName(), overriddenPlan.getName() + "-" +  initialPhase.getName().split("-")[initialPhase.getName().split("-").length -1]);
            assertEquals(newPhase.getDuration(), initialPhase.getDuration());
            assertEquals(newPhase.getPhaseType(), initialPhase.getPhaseType());
            assertEquals(newPhase.getUsages().length, initialPhase.getUsages().length);
            if (initialPhase.getFixed() != null) {
                assertEquals(newPhase.getFixed().getType(), initialPhase.getFixed().getType());
                assertInternationalPrice(newPhase.getFixed().getPrice(), initialPhase.getFixed().getPrice(), override, true);
            }
            if (initialPhase.getRecurring() != null) {
                assertInternationalPrice(newPhase.getRecurring().getRecurringPrice(), initialPhase.getRecurring().getRecurringPrice(), override, false);
            }
        }
    }

    private void assertInternationalPrice(final InternationalPrice newInternationalPrice, final InternationalPrice initInternationalPrice, final PlanPhasePriceOverride override, final boolean isFixed) throws CatalogApiException {

        if (initInternationalPrice.getPrices().length == 0) {
            if (override != null) {
                assertEquals(newInternationalPrice.getPrices().length, 1);
                assertEquals(newInternationalPrice.getPrice(override.getCurrency()).compareTo(isFixed ? override.getFixedPrice() : override.getRecurringPrice()), 0);
            }
        } else {
            assertEquals(newInternationalPrice.getPrices().length, initInternationalPrice.getPrices().length);
            for (int i = 0; i < newInternationalPrice.getPrices().length; i++) {
                final Price initPrice = initInternationalPrice.getPrices()[i];
                final Price newPrice = newInternationalPrice.getPrices()[i];
                if (override != null && override.getCurrency() == initPrice.getCurrency() &&
                    ((isFixed && override.getFixedPrice() != null) || (!isFixed && override.getRecurringPrice() != null))) {
                    assertEquals(newPrice.getValue().compareTo(isFixed ? override.getFixedPrice() : override.getRecurringPrice()), 0);
                } else {
                    if (initPrice != null && initPrice.getValue() != null) {
                        assertEquals(newPrice.getValue().compareTo(initPrice.getValue()), 0);
                    }
                }
            }
        }
    }
}
