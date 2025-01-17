/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.invoice.usage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.DefaultTier;
import org.killbill.billing.catalog.DefaultTieredBlock;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.TierBlockPolicy;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.junction.BillingEvent;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestSubscriptionConsumableInArrear extends TestUsageInArrearBase {

    @Test(groups = "fast")
    public void testComputeInArrearUsageInterval() throws Exception {

        final List<BillingEvent> billingEvents = new ArrayList<>();

        final String usageName1 = "erw";
        final DefaultTieredBlock block1 = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier1 = createDefaultTierWithBlocks(block1);
        final Usage usage1 = createConsumableInArrearUsage(usageName1, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier1);

        final String usageName2 = "hghg";
        final DefaultTieredBlock block2 = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier2 = createDefaultTierWithBlocks(block2);
        final Usage usage2 = createConsumableInArrearUsage(usageName2, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier2);

        final DateTime dt1 = new DateTime(2013, 3, 23, 4, 34, 59, DateTimeZone.UTC);
        final BillingEvent evt1 = createMockBillingEvent(dt1, BillingPeriod.MONTHLY, List.of(usage1, usage2), catalogEffectiveDate);
        billingEvents.add(evt1);

        final DateTime dt2 = new DateTime(2013, 4, 23, 4, 34, 59, DateTimeZone.UTC);
        final BillingEvent evt2 = createMockBillingEvent(dt2, BillingPeriod.MONTHLY, List.of(usage1), catalogEffectiveDate);
        billingEvents.add(evt2);

        final DateTime dt3 = new DateTime(2013, 5, 23, 4, 34, 59, DateTimeZone.UTC);
        final BillingEvent evt3 = createMockBillingEvent(dt3, BillingPeriod.MONTHLY, List.of(usage1, usage2), catalogEffectiveDate);
        billingEvents.add(evt3);

        final LocalDate targetDate = new LocalDate(2013, 6, 23);

        final SubscriptionUsageInArrear foo = new SubscriptionUsageInArrear(subscriptionId, accountId, invoiceId, billingEvents, targetDate, dt1, usageDetailMode, invoiceConfig, internalCallContext);
        final List<ContiguousIntervalUsageInArrear> result = foo.computeInArrearUsageInterval();
        assertEquals(result.size(), 3);

        final ContiguousIntervalUsageInArrear firstResult = result.stream()
                .filter(input -> input.getUsage().getName().equals(usageName2) && input.transitionTimes.size() == 3)
                .findFirst().get();

        assertEquals(firstResult.getUsage().getName(), usageName2);
        assertEquals(firstResult.getTransitionTimes().size(), 3);
        assertTrue(firstResult.getTransitionTimes().get(0).compareTo(dt1) == 0);
        assertTrue(internalCallContext.toLocalDate(firstResult.getTransitionTimes().get(1)).compareTo(new LocalDate(2013, 4, 15)) == 0);
        assertTrue(firstResult.getTransitionTimes().get(2).compareTo(dt2) == 0);

        final ContiguousIntervalUsageInArrear secondResult = result.stream()
                .filter(input -> input.getUsage().getName().equals(usageName1))
                .findFirst().get();

        assertEquals(secondResult.getUsage().getName(), usageName1);
        assertEquals(secondResult.getTransitionTimes().size(), 4);
        assertTrue(secondResult.getTransitionTimes().get(0).compareTo(dt1) == 0);
        assertTrue(internalCallContext.toLocalDate(secondResult.getTransitionTimes().get(1)).compareTo(new LocalDate(2013, 4, 15)) == 0);
        assertTrue(internalCallContext.toLocalDate(secondResult.getTransitionTimes().get(2)).compareTo(new LocalDate(2013, 5, 15)) == 0);
        assertTrue(internalCallContext.toLocalDate(secondResult.getTransitionTimes().get(3)).compareTo(new LocalDate(2013, 6, 15)) == 0);

        final ContiguousIntervalUsageInArrear thirdResult = result.stream()
                .filter(input -> input.getUsage().getName().equals(usageName2) && input.transitionTimes.size() == 2)
                .findFirst().get();

        assertEquals(thirdResult.getUsage().getName(), usageName2);
        assertEquals(thirdResult.getTransitionTimes().size(), 2);
        assertTrue(thirdResult.getTransitionTimes().get(0).compareTo(dt3) == 0);
        assertTrue(internalCallContext.toLocalDate(thirdResult.getTransitionTimes().get(1)).compareTo(new LocalDate(2013, 6, 15)) == 0);
    }
}
