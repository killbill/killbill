/*
 * Copyright 2010-2011 Ning, Inc.
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

package org.killbill.billing.catalog.io;

import java.math.BigDecimal;

import org.killbill.billing.catalog.CatalogTestSuiteNoDB;
import org.killbill.billing.catalog.DefaultTieredBlock;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.TierBlockPolicy;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.xmlloader.XMLLoader;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.io.Resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestXMLReader extends CatalogTestSuiteNoDB {

    @Test(groups = "fast")
    public void testCatalogLoad() {
        try {
            XMLLoader.getObjectFromString(Resources.getResource("SpyCarBasic.xml").toExternalForm(), StandaloneCatalog.class);
            XMLLoader.getObjectFromString(Resources.getResource("SpyCarAdvanced.xml").toExternalForm(), StandaloneCatalog.class);
            XMLLoader.getObjectFromString(Resources.getResource("WeaponsHire.xml").toExternalForm(), StandaloneCatalog.class);
            XMLLoader.getObjectFromString(Resources.getResource("WeaponsHireSmall.xml").toExternalForm(), StandaloneCatalog.class);

            XMLLoader.getObjectFromString(Resources.getResource("catalogTest.xml").toExternalForm(), StandaloneCatalog.class);

            XMLLoader.getObjectFromString(Resources.getResource("UsageExperimental.xml").toExternalForm(), StandaloneCatalog.class);
        } catch (Exception e) {
            Assert.fail(e.toString());
        }
    }


    @Test(groups = "fast")
    public void testUsageCapacityInAdvance() {

        try {
            final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("UsageExperimental.xml").toExternalForm(), StandaloneCatalog.class);

            final Usage[] usages = getUsages(catalog, "capacity-in-advance-monthly");
            assertEquals(usages.length, 1);
            final Usage usage = usages[0];

            assertEquals(usage.getName(), "capacity-in-advance-monthly-usage1");
            assertEquals(usage.getBillingPeriod(), BillingPeriod.MONTHLY);
            assertEquals(usage.getUsageType(), UsageType.CAPACITY);
            assertEquals(usage.getBillingMode(), BillingMode.IN_ADVANCE);
            assertEquals(usage.getTierBlockPolicy(), TierBlockPolicy.ALL_TIERS);

            assertEquals(usage.getBlocks().length, 0);
            assertEquals(usage.getTiers().length, 0);

            assertEquals(usage.getLimits().length, 1);
            assertEquals(usage.getLimits()[0].getUnit().getName(), "members");
            assertEquals(usage.getLimits()[0].getMax(), new Double("100"));

            assertEquals(usage.getRecurringPrice().getPrices().length, 1);
            assertEquals(usage.getRecurringPrice().getPrices()[0].getCurrency(), Currency.BTC);
            assertEquals(usage.getRecurringPrice().getPrices()[0].getValue(), new BigDecimal("100.00"));
        } catch (Exception e) {
            Assert.fail(e.toString());
        }
    }

    @Test(groups = "fast")
    public void testUsageConsumableInAdvancePrepayCredit() {

        try {
            final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("UsageExperimental.xml").toExternalForm(), StandaloneCatalog.class);

            final Usage[] usages = getUsages(catalog, "consumable-in-advance-prepay-credit-monthly");
            assertEquals(usages.length, 1);
            final Usage usage = usages[0];

            assertEquals(usage.getName(), "consumable-in-advance-prepay-credit-monthly-usage1");
            assertEquals(usage.getBillingPeriod(), BillingPeriod.MONTHLY);
            assertEquals(usage.getUsageType(), UsageType.CONSUMABLE);
            assertEquals(usage.getBillingMode(), BillingMode.IN_ADVANCE);

            assertEquals(usage.getLimits().length, 0);
            assertEquals(usage.getTiers().length, 0);

            assertEquals(usage.getBlocks().length, 1);

            assertEquals(usage.getBlocks()[0].getUnit().getName(), "cell-phone-minutes");
            assertEquals(usage.getBlocks()[0].getSize(), new Double("1000"));

            assertEquals(usage.getBlocks()[0].getPrice().getPrices().length, 1);
            assertEquals(usage.getBlocks()[0].getPrice().getPrices()[0].getCurrency(), Currency.BTC);
            assertEquals(usage.getBlocks()[0].getPrice().getPrices()[0].getValue(), new BigDecimal("0.10"));
        } catch (Exception e) {
            Assert.fail(e.toString());
        }
    }


    @Test(groups = "fast")
    public void testUsageConsumableInAdvanceTopUp() {

        try {
            final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("UsageExperimental.xml").toExternalForm(), StandaloneCatalog.class);

            final Usage[] usages = getUsages(catalog, "consumable-in-advance-topup");
            assertEquals(usages.length, 1);
            final Usage usage = usages[0];

            assertEquals(usage.getName(), "consumable-in-advance-topup-usage1");
            assertEquals(usage.getBillingPeriod(), BillingPeriod.NO_BILLING_PERIOD);
            assertEquals(usage.getUsageType(), UsageType.CONSUMABLE);
            assertEquals(usage.getBillingMode(), BillingMode.IN_ADVANCE);

            assertEquals(usage.getLimits().length, 0);
            assertEquals(usage.getTiers().length, 0);

            assertEquals(usage.getBlocks().length, 1);

            assertEquals(usage.getBlocks()[0].getUnit().getName(), "fastrack-tokens");
            assertEquals(usage.getBlocks()[0].getSize(), new Double("10"));

            assertEquals(usage.getBlocks()[0].getPrice().getPrices().length, 1);
            assertEquals(usage.getBlocks()[0].getPrice().getPrices()[0].getCurrency(), Currency.BTC);
            assertEquals(usage.getBlocks()[0].getPrice().getPrices()[0].getValue(), new BigDecimal("0.10"));

            assertEquals(usage.getBlocks()[0].getMinTopUpCredit(), new Double("5"));

        } catch (Exception e) {
            Assert.fail(e.toString());
        }
    }


    @Test(groups = "fast")
    public void testUsageCapacityInArrear() {

        try {
            final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("UsageExperimental.xml").toExternalForm(), StandaloneCatalog.class);

            final Usage[] usages = getUsages(catalog, "capacity-in-arrear");
            assertEquals(usages.length, 1);
            final Usage usage = usages[0];

            assertEquals(usage.getName(), "capacity-in-arrear-usage1");
            assertEquals(usage.getBillingPeriod(), BillingPeriod.MONTHLY);
            assertEquals(usage.getUsageType(), UsageType.CAPACITY);
            assertEquals(usage.getBillingMode(), BillingMode.IN_ARREAR);

            assertEquals(usage.getLimits().length, 0);
            assertEquals(usage.getBlocks().length, 0);

            assertEquals(usage.getTiers().length, 2);


            assertEquals(usage.getTiers()[0].getLimits().length, 2);
            assertEquals(usage.getTiers()[0].getLimits()[0].getUnit().getName(), "bandwith-meg-sec");
            assertEquals(usage.getTiers()[0].getLimits()[0].getMax(), new Double("100"));
            assertEquals(usage.getTiers()[0].getLimits()[1].getUnit().getName(), "members");
            assertEquals(usage.getTiers()[0].getLimits()[1].getMax(), new Double("500"));
            assertEquals(usage.getTiers()[0].getFixedPrice().getPrices().length, 1);
            assertEquals(usage.getTiers()[0].getFixedPrice().getPrices()[0].getCurrency(), Currency.BTC);
            assertEquals(usage.getTiers()[0].getFixedPrice().getPrices()[0].getValue(), new BigDecimal("0.007"));
            assertEquals(usage.getTiers()[0].getRecurringPrice().getPrices().length, 1);
            assertEquals(usage.getTiers()[0].getRecurringPrice().getPrices()[0].getCurrency(), Currency.BTC);
            assertEquals(usage.getTiers()[0].getRecurringPrice().getPrices()[0].getValue(), new BigDecimal("0.8"));

            assertEquals(usage.getTiers()[1].getLimits()[0].getUnit().getName(), "bandwith-meg-sec");
            assertEquals(usage.getTiers()[1].getLimits()[0].getMax(), new Double("100"));
            assertEquals(usage.getTiers()[1].getLimits()[1].getUnit().getName(), "members");
            assertEquals(usage.getTiers()[1].getLimits()[1].getMax(), new Double("1000"));
            assertEquals(usage.getTiers()[1].getFixedPrice().getPrices().length, 1);
            assertEquals(usage.getTiers()[1].getFixedPrice().getPrices()[0].getCurrency(), Currency.BTC);
            assertEquals(usage.getTiers()[1].getFixedPrice().getPrices()[0].getValue(), new BigDecimal("0.4"));
            assertEquals(usage.getTiers()[1].getRecurringPrice().getPrices().length, 1);
            assertEquals(usage.getTiers()[1].getRecurringPrice().getPrices()[0].getCurrency(), Currency.BTC);
            assertEquals(usage.getTiers()[1].getRecurringPrice().getPrices()[0].getValue(), new BigDecimal("1.2"));

        } catch (Exception e) {
            Assert.fail(e.toString());
        }
    }


    @Test(groups = "fast")
    public void testUsageConsumableInArrear() {

        try {
            final StandaloneCatalog catalog = XMLLoader.getObjectFromString(Resources.getResource("UsageExperimental.xml").toExternalForm(), StandaloneCatalog.class);

            final Usage[] usages = getUsages(catalog, "consumable-in-arrear");
            assertEquals(usages.length, 1);
            final Usage usage = usages[0];

            assertEquals(usage.getName(), "consumable-in-arrear-usage1");
            assertEquals(usage.getBillingPeriod(), BillingPeriod.MONTHLY);
            assertEquals(usage.getUsageType(), UsageType.CONSUMABLE);
            assertEquals(usage.getBillingMode(), BillingMode.IN_ARREAR);

            assertEquals(usage.getLimits().length, 0);
            assertEquals(usage.getBlocks().length, 0);

            assertEquals(usage.getTiers().length, 1);

            assertEquals(usage.getTiers()[0].getTieredBlocks().length, 2);
            assertEquals(usage.getTiers()[0].getTieredBlocks()[0].getUnit().getName(), "cell-phone-minutes");

            assertEquals(usage.getTiers()[0].getTieredBlocks()[0].getSize(), new Double("1000"));
            assertEquals(usage.getTiers()[0].getTieredBlocks()[0].getMax(), new Double("10000"));
            assertEquals(usage.getTiers()[0].getTieredBlocks()[0].getPrice().getPrices().length, 1);
            assertEquals(usage.getTiers()[0].getTieredBlocks()[0].getPrice().getPrices()[0].getCurrency(), Currency.BTC);
            assertEquals(usage.getTiers()[0].getTieredBlocks()[0].getPrice().getPrices()[0].getValue(), new BigDecimal("0.5"));

            assertEquals(usage.getTiers()[0].getTieredBlocks()[1].getUnit().getName(), "Mbytes");
            assertEquals(usage.getTiers()[0].getTieredBlocks()[1].getSize(), new Double("512"));
            assertEquals(usage.getTiers()[0].getTieredBlocks()[1].getMax(), new Double("512000"));
            assertEquals(usage.getTiers()[0].getTieredBlocks()[1].getPrice().getPrices().length, 1);
            assertEquals(usage.getTiers()[0].getTieredBlocks()[1].getPrice().getPrices()[0].getCurrency(), Currency.BTC);
            assertEquals(usage.getTiers()[0].getTieredBlocks()[1].getPrice().getPrices()[0].getValue(), new BigDecimal("0.3"));
        } catch (Exception e) {
            Assert.fail(e.toString());
        }
    }


    private Usage[] getUsages(final StandaloneCatalog catalog, final String planName) throws CatalogApiException {
        final Plan plan  = catalog.findCurrentPlan(planName);
        assertNotNull(plan);
        final PlanPhase phase = plan.getFinalPhase();
        assertNotNull(phase);
        final Usage[] usages = phase.getUsages();
        return usages;
    }
}
