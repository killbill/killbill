/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.DefaultLimit;
import org.killbill.billing.catalog.DefaultTier;
import org.killbill.billing.catalog.DefaultTieredBlock;
import org.killbill.billing.catalog.DefaultUnit;
import org.killbill.billing.catalog.DefaultUsage;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.UsageInvoiceItem;
import org.killbill.billing.invoice.usage.ContiguousIntervalUsageInArrear.UsageInArrearItemsAndNextNotificationDate;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.usage.RawUsage;
import org.killbill.billing.usage.api.RolledUpUnit;
import org.killbill.billing.usage.api.RolledUpUsage;
import org.killbill.billing.usage.api.svcs.DefaultRawUsage;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestContiguousIntervalCapacityInArrear extends TestUsageInArrearBase {

    @BeforeClass(groups = "fast")
    protected void beforeClass() throws Exception {
        super.beforeClass();
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() {
        super.beforeMethod();
    }

    @Test(groups = "fast")
    public void testComputeToBeBilledUsage() {

        final LocalDate startDate = new LocalDate(2014, 03, 20);
        final LocalDate endDate = new LocalDate(2014, 04, 20);

        final DefaultUnit unit = new DefaultUnit().setName("unit");
        final DefaultLimit limit = new DefaultLimit().setUnit(unit).setMax((double) 100);

        final DefaultTier tier = createDefaultTierWithLimits(BigDecimal.TEN, limit);

        final DefaultUsage usage = createCapacityInArrearUsage(usageName, BillingPeriod.MONTHLY, tier);

        final LocalDate targetDate = startDate.plusDays(1);

        final ContiguousIntervalUsageInArrear intervalCapacityInArrear = createContiguousIntervalConsumableInArrear(usage, ImmutableList.<RawUsage>of(), targetDate, false,
                                                                                                                      createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                             BillingPeriod.MONTHLY,
                                                                                                                                             Collections.<Usage>emptyList())
                                                                                                                     );

        final List<InvoiceItem> existingUsage = Lists.newArrayList();
        final UsageInvoiceItem ii1 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, usage.getName(), startDate, endDate, BigDecimal.TEN, currency);
        existingUsage.add(ii1);
        final UsageInvoiceItem ii2 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, usage.getName(), startDate, endDate, BigDecimal.TEN, currency);
        existingUsage.add(ii2);

        // Will be ignored as is starts one day earlier.
        final UsageInvoiceItem ii3 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, usage.getName(), startDate.minusDays(1), endDate, BigDecimal.TEN, currency);
        existingUsage.add(ii3);

        // Will be ignored as it is for a different udsage section
        final UsageInvoiceItem ii4 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, "other", startDate, endDate, BigDecimal.TEN, currency);
        existingUsage.add(ii4);

        // Will be ignored because non usage item
        final FixedPriceInvoiceItem ii5 = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, BigDecimal.TEN, currency);
        existingUsage.add(ii5);

        final Iterable<InvoiceItem> billedItems = intervalCapacityInArrear.getBilledItems(startDate, endDate, existingUsage);
        final BigDecimal result = intervalCapacityInArrear.computeBilledUsage(billedItems);
        assertEquals(result.compareTo(BigDecimal.TEN.add(BigDecimal.TEN)), 0);
    }

    @Test(groups = "fast")
    public void testComputeBilledUsage() throws CatalogApiException {


        final DefaultUnit unit1 = new DefaultUnit().setName("unit1");
        final DefaultUnit unit2 = new DefaultUnit().setName("unit2");

        final DefaultLimit limit1_1 = new DefaultLimit().setUnit(unit1).setMax((double) 100).setMin((double) -1);
        final DefaultLimit limit1_2 = new DefaultLimit().setUnit(unit2).setMax((double) 1000).setMin((double) -1);
        final DefaultTier tier1 = createDefaultTierWithLimits(BigDecimal.TEN, limit1_1, limit1_2);

        final DefaultLimit limit2_1 = new DefaultLimit().setUnit(unit1).setMax((double) 200).setMin((double) -1);
        final DefaultLimit limit2_2 = new DefaultLimit().setUnit(unit2).setMax((double) 2000).setMin((double) -1);
        final DefaultTier tier2 = createDefaultTierWithLimits(new BigDecimal("20.0"), limit2_1, limit2_2);

        // Don't define any max for last tier to allow any number
        final DefaultLimit limit3_1 = new DefaultLimit().setUnit(unit1).setMin((double) -1).setMax((double) -1);
        final DefaultLimit limit3_2 = new DefaultLimit().setUnit(unit2).setMin((double) -1).setMax((double) -1);
        final DefaultTier tier3 = createDefaultTierWithLimits(new BigDecimal("30.0"), limit3_1, limit3_2);


        final DefaultUsage usage = createCapacityInArrearUsage(usageName, BillingPeriod.MONTHLY, tier1, tier2, tier3);

        final LocalDate targetDate = new LocalDate(2014, 03, 20);

        final ContiguousIntervalUsageInArrear intervalCapacityInArrear = createContiguousIntervalConsumableInArrear(usage, ImmutableList.<RawUsage>of(), targetDate, false,
                                                                                                                      createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                                  BillingPeriod.MONTHLY,
                                                                                                                                                  Collections.<Usage>emptyList())
                                                                                                                     );
        // Tier 1 (both units from tier 1)
        BigDecimal result = intervalCapacityInArrear.computeToBeBilledCapacityInArrear(ImmutableList.<RolledUpUnit>of(new DefaultRolledUpUnit("unit1", 100L),
                                                                                                                      new DefaultRolledUpUnit("unit2", 1000L)));
        assertEquals(result, BigDecimal.TEN);

        // Tier 2 (only one unit from tier 1)
        result = intervalCapacityInArrear.computeToBeBilledCapacityInArrear(ImmutableList.<RolledUpUnit>of(new DefaultRolledUpUnit("unit1", 100L),
                                                                                                           new DefaultRolledUpUnit("unit2", 1001L)));
        assertEquals(result, new BigDecimal("20.0"));

        // Tier 2 (only one unit from tier 1)
        result = intervalCapacityInArrear.computeToBeBilledCapacityInArrear(ImmutableList.<RolledUpUnit>of(new DefaultRolledUpUnit("unit1", 101L),
                                                                                                           new DefaultRolledUpUnit("unit2", 1000L)));
        assertEquals(result, new BigDecimal("20.0"));


        // Tier 2 (both units from tier 2)
        result = intervalCapacityInArrear.computeToBeBilledCapacityInArrear(ImmutableList.<RolledUpUnit>of(new DefaultRolledUpUnit("unit1", 101L),
                                                                                                           new DefaultRolledUpUnit("unit2", 1001L)));
        assertEquals(result, new BigDecimal("20.0"));

        // Tier 3 (only one unit from tier 3)
        result = intervalCapacityInArrear.computeToBeBilledCapacityInArrear(ImmutableList.<RolledUpUnit>of(new DefaultRolledUpUnit("unit1", 10L),
                                                                                                           new DefaultRolledUpUnit("unit2", 2001L)));
        assertEquals(result, new BigDecimal("30.0"));
    }

    @Test(groups = "fast")
    public void testComputeMissingItems() throws CatalogApiException {

        final LocalDate startDate = new LocalDate(2014, 03, 20);
        final LocalDate firstBCDDate = new LocalDate(2014, 04, 15);
        final LocalDate endDate = new LocalDate(2014, 05, 15);

        // 2 items for startDate - firstBCDDate
        final List<RawUsage> rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "unit", 130L));
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "unit", 271L));
        // 1 items for firstBCDDate - endDate
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 04, 15), "unit", 199L));

        final DefaultUnit unit = new DefaultUnit().setName("unit");
        final DefaultLimit limit = new DefaultLimit().setUnit(unit).setMax((double) -1);

        final DefaultTier tier = createDefaultTierWithLimits(BigDecimal.TEN, limit);

        final DefaultUsage usage = createCapacityInArrearUsage(usageName, BillingPeriod.MONTHLY, tier);


        final LocalDate targetDate = endDate;

        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),BillingPeriod.MONTHLY, Collections.<Usage>emptyList());
        final BillingEvent event2 = createMockBillingEvent(endDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList());

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, rawUsages, targetDate, true, event1, event2);

        final List<InvoiceItem> invoiceItems = new ArrayList<InvoiceItem>();
        final InvoiceItem ii1 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, usage.getName(), startDate, firstBCDDate, BigDecimal.ONE, currency);
        invoiceItems.add(ii1);

        final InvoiceItem ii2 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, usage.getName(), firstBCDDate, endDate, BigDecimal.ONE, currency);
        invoiceItems.add(ii2);

        final UsageInArrearItemsAndNextNotificationDate usageResult = intervalConsumableInArrear.computeMissingItemsAndNextNotificationDate(invoiceItems);
        final List<InvoiceItem> rawResults = usageResult.getInvoiceItems();
        assertEquals(rawResults.size(), 4);

        final List<InvoiceItem> result = ImmutableList.copyOf(Iterables.filter(rawResults, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return input.getAmount().compareTo(BigDecimal.ZERO) > 0;
            }
        }));


        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("9.0")), 0, String.format("%s != 9.0", result.get(0).getAmount()));
        assertEquals(result.get(0).getCurrency(), Currency.BTC);
        assertEquals(result.get(0).getAccountId(), accountId);
        assertEquals(result.get(0).getBundleId(), bundleId);
        assertEquals(result.get(0).getSubscriptionId(), subscriptionId);
        assertEquals(result.get(0).getPlanName(), planName);
        assertEquals(result.get(0).getPhaseName(), phaseName);
        assertEquals(result.get(0).getUsageName(), usage.getName());
        assertTrue(result.get(0).getStartDate().compareTo(startDate) == 0);
        assertTrue(result.get(0).getEndDate().compareTo(firstBCDDate) == 0);

        assertEquals(result.get(1).getAmount().compareTo(new BigDecimal("9.0")), 0, String.format("%s != 9.0", result.get(0).getAmount()));
        assertEquals(result.get(1).getCurrency(), Currency.BTC);
        assertEquals(result.get(1).getAccountId(), accountId);
        assertEquals(result.get(1).getBundleId(), bundleId);
        assertEquals(result.get(1).getSubscriptionId(), subscriptionId);
        assertEquals(result.get(1).getPlanName(), planName);
        assertEquals(result.get(1).getPhaseName(), phaseName);
        assertEquals(result.get(1).getUsageName(), usage.getName());
        assertTrue(result.get(1).getStartDate().compareTo(firstBCDDate) == 0);
        assertTrue(result.get(1).getEndDate().compareTo(endDate) == 0);
    }



}
