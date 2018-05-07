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
import org.killbill.billing.usage.RawUsage;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestSubscriptionConsumableInArrear extends TestUsageInArrearBase {

    @Test(groups = "fast")
    public void testComputeInArrearUsageInterval() {

        final List<BillingEvent> billingEvents = Lists.newArrayList();

        final String usageName1 = "erw";
        final DefaultTieredBlock block1 = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier1 = createDefaultTierWithBlocks(block1);
        final Usage usage1 = createConsumableInArrearUsage(usageName1, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier1);

        final String usageName2 = "hghg";
        final DefaultTieredBlock block2 = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier2 = createDefaultTierWithBlocks(block2);
        final Usage usage2 = createConsumableInArrearUsage(usageName2, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier2);

        final DateTime dt1 = new DateTime(2013, 3, 23, 4, 34, 59, DateTimeZone.UTC);
        final BillingEvent evt1 = createMockBillingEvent(dt1, BillingPeriod.MONTHLY, ImmutableList.<Usage>builder().add(usage1).add(usage2).build());
        billingEvents.add(evt1);

        final DateTime dt2 = new DateTime(2013, 4, 23, 4, 34, 59, DateTimeZone.UTC);
        final BillingEvent evt2 = createMockBillingEvent(dt2, BillingPeriod.MONTHLY, ImmutableList.<Usage>builder().add(usage1).build());
        billingEvents.add(evt2);

        final DateTime dt3 = new DateTime(2013, 5, 23, 4, 34, 59, DateTimeZone.UTC);
        final BillingEvent evt3 = createMockBillingEvent(dt3, BillingPeriod.MONTHLY, ImmutableList.<Usage>builder().add(usage1).add(usage2).build());
        billingEvents.add(evt3);

        LocalDate targetDate = new LocalDate(2013, 6, 23);

        final SubscriptionUsageInArrear foo = new SubscriptionUsageInArrear(accountId, invoiceId, billingEvents, ImmutableList.<RawUsage>of(), targetDate, new LocalDate(dt1, DateTimeZone.UTC), usageDetailMode, internalCallContext);
        final List<ContiguousIntervalUsageInArrear> result = foo.computeInArrearUsageInterval();
        assertEquals(result.size(), 3);

        assertEquals(result.get(0).getUsage().getName(), usageName2);
        assertEquals(result.get(0).getTransitionTimes().size(), 3);
        assertTrue(result.get(0).getTransitionTimes().get(0).compareTo(new LocalDate(2013, 3, 23)) == 0);
        assertTrue(result.get(0).getTransitionTimes().get(1).compareTo(new LocalDate(2013, 4, 15)) == 0);
        assertTrue(result.get(0).getTransitionTimes().get(2).compareTo(new LocalDate(2013, 4, 23)) == 0);

        assertEquals(result.get(1).getUsage().getName(), usageName1);
        assertEquals(result.get(1).getTransitionTimes().size(), 4);
        assertTrue(result.get(1).getTransitionTimes().get(0).compareTo(new LocalDate(2013, 3, 23)) == 0);
        assertTrue(result.get(1).getTransitionTimes().get(1).compareTo(new LocalDate(2013, 4, 15)) == 0);
        assertTrue(result.get(1).getTransitionTimes().get(2).compareTo(new LocalDate(2013, 5, 15)) == 0);
        assertTrue(result.get(1).getTransitionTimes().get(3).compareTo(new LocalDate(2013, 6, 15)) == 0);

        assertEquals(result.get(2).getUsage().getName(), usageName2);
        assertEquals(result.get(2).getTransitionTimes().size(), 2);
        assertTrue(result.get(2).getTransitionTimes().get(0).compareTo(new LocalDate(2013, 5, 23)) == 0);
        assertTrue(result.get(2).getTransitionTimes().get(1).compareTo(new LocalDate(2013, 6, 15)) == 0);
    }
}
