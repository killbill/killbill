/*
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

package org.killbill.billing.invoice.usage;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.DefaultTier;
import org.killbill.billing.catalog.DefaultTieredBlock;
import org.killbill.billing.catalog.DefaultUsage;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.TierBlockPolicy;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestContiguousIntervalUsageInArrear extends TestUsageInArrearBase {

    @Test(groups = "fast")
    public void testVerifyTransitionTimes() throws Exception {
        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 1000, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        final LocalDate targetDate = new LocalDate(2019, 3, 10);

        final BillingEvent billingEvent1 = createMockBillingEvent(1,
                                                                  new LocalDate(2019, 1, 1).toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                  BillingPeriod.MONTHLY,
                                                                  Collections.<Usage>emptyList(), catalogEffectiveDate, SubscriptionBaseTransitionType.CREATE);
        final BillingEvent billingEvent2 = createMockBillingEvent(1,
                                                                  new LocalDate(2019, 1, 31).toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                  BillingPeriod.MONTHLY,
                                                                  Collections.<Usage>emptyList(), catalogEffectiveDate, SubscriptionBaseTransitionType.CHANGE);
        final BillingEvent billingEvent3 = createMockBillingEvent(5,
                                                                  new LocalDate(2019, 2, 5).toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                  BillingPeriod.MONTHLY,
                                                                  Collections.<Usage>emptyList(), catalogEffectiveDate, SubscriptionBaseTransitionType.CHANGE);
        final BillingEvent billingEvent4 = createMockBillingEvent(10,
                                                                  new LocalDate(2019, 3, 10).toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                  BillingPeriod.MONTHLY,
                                                                  Collections.<Usage>emptyList(), catalogEffectiveDate, SubscriptionBaseTransitionType.CANCEL);
        final ContiguousIntervalConsumableUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage,
                                                                                                                                targetDate,
                                                                                                                                false,
                                                                                                                                billingEvent1,
                                                                                                                                billingEvent2,
                                                                                                                                billingEvent3,
                                                                                                                                billingEvent4);

        // TODO Why don't we see the transition on 2019-1-31 (already the case prior the change ?)
        Assert.assertEquals(intervalConsumableInArrear.getTransitionTimes().size(), 5);
        Assert.assertEquals(intervalConsumableInArrear.getTransitionTimes().get(0), billingEvent1.getEffectiveDate());
        Assert.assertEquals(internalCallContext.toLocalDate(intervalConsumableInArrear.getTransitionTimes().get(1)), new LocalDate(2019, 2, 1));
        Assert.assertEquals(intervalConsumableInArrear.getTransitionTimes().get(2), billingEvent3.getEffectiveDate());
        Assert.assertEquals(internalCallContext.toLocalDate(intervalConsumableInArrear.getTransitionTimes().get(3)), new LocalDate(2019, 3, 5));
        Assert.assertEquals(intervalConsumableInArrear.getTransitionTimes().get(4), billingEvent4.getEffectiveDate());
    }


    @Test(groups = "fast")
    public void testVerifyTransitionTimes2() throws Exception {
        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 1000, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        final LocalDate targetDate = new LocalDate(2018, 1, 1);

        final BillingEvent billingEvent1 = createMockBillingEvent(1,
                                                                  new DateTime(2018, 1, 1, 8, 30, 45, DateTimeZone.UTC),
                                                                  BillingPeriod.MONTHLY,
                                                                  Collections.<Usage>emptyList(), catalogEffectiveDate, SubscriptionBaseTransitionType.CREATE);
        final BillingEvent billingEvent2 = createMockBillingEvent(1,
                                                                  new DateTime(2018, 1, 1, 10, 30, 45, DateTimeZone.UTC),
                                                                  BillingPeriod.MONTHLY,
                                                                  Collections.<Usage>emptyList(), catalogEffectiveDate, SubscriptionBaseTransitionType.CHANGE);

        final ContiguousIntervalConsumableUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage,
                                                                                                                                targetDate,
                                                                                                                                false,
                                                                                                                                billingEvent1,
                                                                                                                                billingEvent2);

        final List<DateTime> transitionTimes = intervalConsumableInArrear.getTransitionTimes();
        Assert.assertEquals(transitionTimes.size(), 2);
        Assert.assertEquals(transitionTimes.get(0), billingEvent1.getEffectiveDate());
        Assert.assertEquals(transitionTimes.get(1), billingEvent2.getEffectiveDate());
    }
}
