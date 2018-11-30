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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.DefaultLimit;
import org.killbill.billing.catalog.DefaultTier;
import org.killbill.billing.catalog.DefaultUnit;
import org.killbill.billing.catalog.DefaultUsage;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.UsageInvoiceItem;
import org.killbill.billing.invoice.usage.ContiguousIntervalUsageInArrear.UsageInArrearItemsAndNextNotificationDate;
import org.killbill.billing.invoice.usage.details.UsageCapacityInArrearAggregate;
import org.killbill.billing.invoice.usage.details.UsageInArrearTierUnitDetail;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.usage.RawUsage;
import org.killbill.billing.usage.api.RolledUpUnit;
import org.killbill.billing.usage.api.svcs.DefaultRawUsage;
import org.killbill.billing.util.config.definition.InvoiceConfig.UsageDetailMode;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestContiguousIntervalCapacityInArrear extends TestUsageInArrearBase {

    @Test(groups = "fast")
    public void testComputeToBeBilledUsage() {

        final LocalDate startDate = new LocalDate(2014, 03, 20);
        final LocalDate endDate = new LocalDate(2014, 04, 20);

        final DefaultUnit unit = new DefaultUnit().setName("unit");
        final DefaultLimit limit = new DefaultLimit().setUnit(unit).setMax((double) 100);

        final DefaultTier tier = createDefaultTierWithLimits(BigDecimal.TEN, limit);

        final DefaultUsage usage = createCapacityInArrearUsage(usageName, BillingPeriod.MONTHLY, tier);

        final LocalDate targetDate = startDate.plusDays(1);

        final ContiguousIntervalUsageInArrear intervalCapacityInArrear = createContiguousIntervalCapacityInArrear(usage, ImmutableList.<RawUsage>of(), targetDate, false,
                                                                                                                  createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                         BillingPeriod.MONTHLY,
                                                                                                                                         Collections.<Usage>emptyList())
                                                                                                                 );

        final List<InvoiceItem> existingUsage = Lists.newArrayList();
        final UsageInvoiceItem ii1 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usage.getName(), startDate, endDate, BigDecimal.TEN, currency);
        existingUsage.add(ii1);
        final UsageInvoiceItem ii2 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usage.getName(), startDate, endDate, BigDecimal.TEN, currency);
        existingUsage.add(ii2);

        // Will be ignored as is starts one day earlier.
        final UsageInvoiceItem ii3 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usage.getName(), startDate.minusDays(1), endDate, BigDecimal.TEN, currency);
        existingUsage.add(ii3);

        // Will be ignored as it is for a different udsage section
        final UsageInvoiceItem ii4 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, "other", startDate, endDate, BigDecimal.TEN, currency);
        existingUsage.add(ii4);

        // Will be ignored because non usage item
        final FixedPriceInvoiceItem ii5 = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, startDate, BigDecimal.TEN, currency);
        existingUsage.add(ii5);

        final Iterable<InvoiceItem> billedItems = intervalCapacityInArrear.getBilledItems(startDate, endDate, existingUsage);
        final BigDecimal result = intervalCapacityInArrear.computeBilledUsage(billedItems);
        assertEquals(result.compareTo(BigDecimal.TEN.add(BigDecimal.TEN)), 0);
    }

    @Test(groups = "fast")
    public void testComputeBilledUsage() throws CatalogApiException {

        final DefaultUnit unit1 = new DefaultUnit().setName("unit1");
        final DefaultUnit unit2 = new DefaultUnit().setName("unit2");
        final DefaultUnit unit3 = new DefaultUnit().setName("unit3");

        final DefaultLimit limit1_1 = new DefaultLimit().setUnit(unit1).setMax((double) 100).setMin((double) -1);
        final DefaultLimit limit1_2 = new DefaultLimit().setUnit(unit2).setMax((double) 1000).setMin((double) -1);
        final DefaultLimit limit1_3 = new DefaultLimit().setUnit(unit3).setMax((double) 50).setMin((double) -1);
        final DefaultTier tier1 = createDefaultTierWithLimits(BigDecimal.TEN, limit1_1, limit1_2, limit1_3);

        final DefaultLimit limit2_1 = new DefaultLimit().setUnit(unit1).setMax((double) 200).setMin((double) -1);
        final DefaultLimit limit2_2 = new DefaultLimit().setUnit(unit2).setMax((double) 2000).setMin((double) -1);
        final DefaultLimit limit2_3 = new DefaultLimit().setUnit(unit3).setMax((double) 100).setMin((double) -1);
        final DefaultTier tier2 = createDefaultTierWithLimits(new BigDecimal("20.0"), limit2_1, limit2_2, limit2_3);

        // Don't define any max for last tier to allow any number
        final DefaultLimit limit3_1 = new DefaultLimit().setUnit(unit1).setMin((double) -1).setMax((double) -1);
        final DefaultLimit limit3_2 = new DefaultLimit().setUnit(unit2).setMin((double) -1).setMax((double) -1);
        final DefaultLimit limit3_3 = new DefaultLimit().setUnit(unit3).setMax((double) -1).setMin((double) -1);
        final DefaultTier tier3 = createDefaultTierWithLimits(new BigDecimal("30.0"), limit3_1, limit3_2, limit3_3);

        final DefaultUsage usage = createCapacityInArrearUsage(usageName, BillingPeriod.MONTHLY, tier1, tier2, tier3);

        final LocalDate targetDate = new LocalDate(2014, 03, 20);

        final ContiguousIntervalCapacityUsageInArrear intervalCapacityInArrear = createContiguousIntervalCapacityInArrear(usage, ImmutableList.<RawUsage>of(), targetDate, false,
                                                                                                                  createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                         BillingPeriod.MONTHLY,
                                                                                                                                         Collections.<Usage>emptyList())
                                                                                                                 );
        // Tier 1 (both units from tier 1)
        UsageCapacityInArrearAggregate result = intervalCapacityInArrear.computeToBeBilledCapacityInArrear(ImmutableList.<RolledUpUnit>of(new DefaultRolledUpUnit("unit1", 100L),
                                                                                                                                          new DefaultRolledUpUnit("unit2", 1000L),
                                                                                                                                          new DefaultRolledUpUnit("unit3", 50L)));
        assertEquals(result.getTierDetails().size(), 3);
        assertTrue(result.getAmount().compareTo(BigDecimal.TEN) == 0);

        // Tier 2 (only one unit from tier 1)
        result = intervalCapacityInArrear.computeToBeBilledCapacityInArrear(ImmutableList.<RolledUpUnit>of(new DefaultRolledUpUnit("unit1", 100L),
                                                                                                           new DefaultRolledUpUnit("unit2", 1001L)));
        assertTrue(result.getAmount().compareTo(new BigDecimal("20.0")) == 0);

        // Tier 2 (only one unit from tier 1)
        result = intervalCapacityInArrear.computeToBeBilledCapacityInArrear(ImmutableList.<RolledUpUnit>of(new DefaultRolledUpUnit("unit1", 101L),
                                                                                                           new DefaultRolledUpUnit("unit2", 1000L)));
        assertTrue(result.getAmount().compareTo(new BigDecimal("20.0")) == 0);

        // Tier 2 (both units from tier 2)
        result = intervalCapacityInArrear.computeToBeBilledCapacityInArrear(ImmutableList.<RolledUpUnit>of(new DefaultRolledUpUnit("unit1", 101L),
                                                                                                           new DefaultRolledUpUnit("unit2", 1001L)));
        assertTrue(result.getAmount().compareTo(new BigDecimal("20.0")) == 0);

        // Tier 3 (only one unit from tier 3)
        result = intervalCapacityInArrear.computeToBeBilledCapacityInArrear(ImmutableList.<RolledUpUnit>of(new DefaultRolledUpUnit("unit1", 10L),
                                                                                                           new DefaultRolledUpUnit("unit2", 2001L)));
        assertTrue(result.getAmount().compareTo(new BigDecimal("30.0")) == 0);
    }

    @Test(groups = "fast")
    public void testComputeMissingItems() throws CatalogApiException, InvoiceApiException {

        final LocalDate startDate = new LocalDate(2014, 03, 20);
        final LocalDate firstBCDDate = new LocalDate(2014, 04, 15);
        final LocalDate endDate = new LocalDate(2014, 05, 15);

        final List<RawUsage> rawUsages = new ArrayList<RawUsage>();
        //
        // First period: startDate - firstBCDDate
        //
        // 2 items for unit1
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "unit1", 130L));
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "unit1", 271L));
        // 1 items for unit2
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 24), "unit2", 10L));

        //
        // Second period: firstBCDDate - endDate
        //
        // 1 items unit1
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 04, 15), "unit1", 199L));
        // 1 items unit2
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 04, 15), "unit2", 20L));

        final DefaultUnit unit1 = new DefaultUnit().setName("unit1");
        final DefaultLimit limit1 = new DefaultLimit().setUnit(unit1).setMax((double) -1);

        final DefaultUnit unit2 = new DefaultUnit().setName("unit2");
        final DefaultLimit limit2 = new DefaultLimit().setUnit(unit2).setMax((double) -1);

        final DefaultTier tier = createDefaultTierWithLimits(BigDecimal.TEN, limit1, limit2);

        final DefaultUsage usage = createCapacityInArrearUsage(usageName, BillingPeriod.MONTHLY, tier);

        final LocalDate targetDate = endDate;
        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList());
        final BillingEvent event2 = createMockBillingEvent(endDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList());

        final ContiguousIntervalCapacityUsageInArrear intervalCapacityInArrear = createContiguousIntervalCapacityInArrear(usage, rawUsages, targetDate, true, event1, event2);

        final List<InvoiceItem> invoiceItems = new ArrayList<InvoiceItem>();
        final InvoiceItem ii1 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usage.getName(), startDate, firstBCDDate, BigDecimal.ONE, currency);
        invoiceItems.add(ii1);

        final InvoiceItem ii2 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usage.getName(), firstBCDDate, endDate, BigDecimal.ONE, currency);
        invoiceItems.add(ii2);

        final UsageInArrearItemsAndNextNotificationDate usageResult = intervalCapacityInArrear.computeMissingItemsAndNextNotificationDate(invoiceItems);
        final List<InvoiceItem> result = usageResult.getInvoiceItems();
        assertEquals(result.size(), 2);


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

    @Test(groups = "fast")
    public void testMultipleItemsAndTiersAggregateMode() throws CatalogApiException, IOException, InvoiceApiException {
        testMultipleItemsAndTiers(UsageDetailMode.AGGREGATE);
    }

    @Test(groups = "fast")
    public void testMultipleItemsAndTiersDetailMode() throws CatalogApiException, IOException, InvoiceApiException {
        testMultipleItemsAndTiers(UsageDetailMode.DETAIL);
    }

    private void testMultipleItemsAndTiers(UsageDetailMode usageDetailMode) throws CatalogApiException, IOException, InvoiceApiException {

        // Case 1
        List<RawUsage> rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "FOO", 5L));
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "BAR", 99L));

        List<InvoiceItem> result = produceInvoiceItems(rawUsages, usageDetailMode, ImmutableList.<InvoiceItem>of());
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getAmount().compareTo(BigDecimal.ONE), 0, String.format("%s != 1.0", result.get(0).getAmount()));

        UsageCapacityInArrearAggregate itemDetails = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<UsageCapacityInArrearAggregate>() {});
        assertEquals(itemDetails.getAmount().compareTo(BigDecimal.ONE), 0);
        assertEquals(itemDetails.getTierDetails().size(), 2);

        List<UsageInArrearTierUnitDetail> itemUnitDetails = itemDetails.getTierDetails();
        // BAR item detail
        assertEquals(itemUnitDetails.get(0).getTierUnit(), "BAR");
        assertEquals(itemUnitDetails.get(0).getTier(), 1);
        assertEquals(itemUnitDetails.get(0).getQuantity().intValue(), 99);
        assertEquals(itemUnitDetails.get(0).getTierPrice().compareTo(BigDecimal.ONE), 0);
        // FOO item detail
        assertEquals(itemUnitDetails.get(1).getTierUnit(), "FOO");
        assertEquals(itemUnitDetails.get(1).getTier(), 1);
        assertEquals(itemUnitDetails.get(1).getQuantity().intValue(), 5);
        assertEquals(itemUnitDetails.get(1).getTierPrice().compareTo(BigDecimal.ONE), 0);

        // Case 2
        rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "FOO", 5L));
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "BAR", 101L));
        result = produceInvoiceItems(rawUsages, usageDetailMode, ImmutableList.<InvoiceItem>of());
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getAmount().compareTo(BigDecimal.TEN), 0, String.format("%s != 10.0", result.get(0).getAmount()));

        itemDetails = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<UsageCapacityInArrearAggregate>() {});
        assertEquals(itemDetails.getAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(itemDetails.getTierDetails().size(), 2);
        itemUnitDetails = itemDetails.getTierDetails();

        // FOO item detail
        assertEquals(itemUnitDetails.get(0).getTierUnit(), "FOO");
        assertEquals(itemUnitDetails.get(0).getTier(), 1);
        assertEquals(itemUnitDetails.get(0).getQuantity().intValue(), 5);
        assertEquals(itemUnitDetails.get(0).getTierPrice().compareTo(BigDecimal.ONE), 0);

        // BAR item detail
        assertEquals(itemUnitDetails.get(1).getTierUnit(), "BAR");
        assertEquals(itemUnitDetails.get(1).getTier(), 2);
        assertEquals(itemUnitDetails.get(1).getQuantity().intValue(), 101);
        assertEquals(itemUnitDetails.get(1).getTierPrice().compareTo(BigDecimal.TEN), 0);

        // Case 3
        rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "FOO", 75L));
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "BAR", 101L));
        result = produceInvoiceItems(rawUsages, usageDetailMode, ImmutableList.<InvoiceItem>of());
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("100.0")), 0, String.format("%s != 100.0", result.get(0).getAmount()));

        itemDetails = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<UsageCapacityInArrearAggregate>() {});
        assertEquals(itemDetails.getAmount().compareTo(new BigDecimal("100.0")), 0);
        assertEquals(itemDetails.getTierDetails().size(), 2);
        itemUnitDetails = itemDetails.getTierDetails();

        // BAR item detail
        assertEquals(itemUnitDetails.get(0).getTierUnit(), "BAR");
        assertEquals(itemUnitDetails.get(0).getTier(), 2);
        assertEquals(itemUnitDetails.get(0).getQuantity().intValue(), 101);
        assertEquals(itemUnitDetails.get(0).getTierPrice().compareTo(new BigDecimal("10.0")), 0);

        // FOO item detail
        assertEquals(itemUnitDetails.get(1).getTierUnit(), "FOO");
        assertEquals(itemUnitDetails.get(1).getTier(), 3);
        assertEquals(itemUnitDetails.get(1).getQuantity().intValue(), 75);
        assertEquals(itemUnitDetails.get(1).getTierPrice().compareTo(new BigDecimal("100.0")), 0);

    }

    @Test(groups = "fast")
    public void testMultipleItemsAndTiersWithExistingItems() throws CatalogApiException, IOException, InvoiceApiException {

        // let's assume we have some existing usage
        final UsageInArrearTierUnitDetail existingFooUsageTier1 = new UsageInArrearTierUnitDetail(1, "FOO", BigDecimal.ONE, 9);
        final UsageInArrearTierUnitDetail existingBarUsageTier2 = new UsageInArrearTierUnitDetail(2, "BAR", BigDecimal.TEN, 200);


        List<RawUsage> rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "FOO", 60L)); // tier 3
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "BAR", 200L)); // tier 2

        final List<UsageInArrearTierUnitDetail> existingUsage = ImmutableList.of(existingFooUsageTier1, existingBarUsageTier2);

        final String existingUsageJson = objectMapper.writeValueAsString(new UsageCapacityInArrearAggregate(existingUsage, BigDecimal.TEN));

        final List<InvoiceItem> existingItems = new ArrayList<InvoiceItem>();
        final InvoiceItem ii1 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usageName, new LocalDate(2014, 03, 20), new LocalDate(2014, 04, 15), BigDecimal.TEN, null, currency, null, existingUsageJson);
        existingItems.add(ii1);

        List<InvoiceItem> result = produceInvoiceItems(rawUsages, UsageDetailMode.AGGREGATE, existingItems);
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("90.00")), 0, String.format("%s != 90.0", result.get(0).getAmount()));

        UsageCapacityInArrearAggregate itemDetails = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<UsageCapacityInArrearAggregate>() {});
        assertEquals(itemDetails.getAmount().compareTo(new BigDecimal("100.00")), 0);
        assertEquals(itemDetails.getTierDetails().size(), 2);

        List<UsageInArrearTierUnitDetail> itemUnitDetails = itemDetails.getTierDetails();

        // BAR item detail
        assertEquals(itemUnitDetails.get(0).getTierUnit(), "BAR");
        assertEquals(itemUnitDetails.get(0).getTier(), 2);
        assertEquals(itemUnitDetails.get(0).getQuantity().intValue(), 200);
        assertEquals(itemUnitDetails.get(0).getTierPrice().compareTo(BigDecimal.TEN), 0);
        // FOO item detail
        assertEquals(itemUnitDetails.get(1).getTierUnit(), "FOO");
        assertEquals(itemUnitDetails.get(1).getTier(), 3);
        assertEquals(itemUnitDetails.get(1).getQuantity().intValue(), 60);
        assertEquals(itemUnitDetails.get(1).getTierPrice().compareTo(new BigDecimal("100.00")), 0);
    }

    private List<InvoiceItem> produceInvoiceItems(List<RawUsage> rawUsages, UsageDetailMode usageDetailMode, List<InvoiceItem> existingItems) throws CatalogApiException, InvoiceApiException {

        final LocalDate startDate = new LocalDate(2014, 03, 20);
        final LocalDate firstBCDDate = new LocalDate(2014, 04, 15);
        final LocalDate endDate = new LocalDate(2014, 05, 15);

        final DefaultUnit unitFoo = new DefaultUnit().setName("FOO");
        final DefaultUnit unitBar = new DefaultUnit().setName("BAR");

        final DefaultLimit unitFooLimitTier1 = new DefaultLimit().setUnit(unitFoo).setMax((double) 10);
        final DefaultLimit unitBarLimitTier1 = new DefaultLimit().setUnit(unitBar).setMax((double) 100);
        final DefaultTier tier1 = createDefaultTierWithLimits(BigDecimal.ONE, unitFooLimitTier1, unitBarLimitTier1);

        final DefaultLimit unitFooLimitTier2 = new DefaultLimit().setUnit(unitFoo).setMax((double) 50);
        final DefaultLimit unitBarLimitTier2 = new DefaultLimit().setUnit(unitBar).setMax((double) 500);
        final DefaultTier tier2 = createDefaultTierWithLimits(BigDecimal.TEN, unitFooLimitTier2, unitBarLimitTier2);

        final DefaultLimit unitFooLimitTier3 = new DefaultLimit().setUnit(unitFoo).setMax((double) 75);
        final DefaultLimit unitBarLimitTier3 = new DefaultLimit().setUnit(unitBar).setMax((double) 750);
        final DefaultTier tier3 = createDefaultTierWithLimits(new BigDecimal("100.0"), unitFooLimitTier3, unitBarLimitTier3);

        final DefaultUsage usage = createCapacityInArrearUsage(usageName, BillingPeriod.MONTHLY, tier1, tier2, tier3);

        final LocalDate targetDate = endDate;

        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList());
        final BillingEvent event2 = createMockBillingEvent(endDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList());

        final ContiguousIntervalCapacityUsageInArrear intervalCapacityInArrear = createContiguousIntervalCapacityInArrear(usage, rawUsages, targetDate, true, usageDetailMode, event1, event2);

        final UsageInArrearItemsAndNextNotificationDate usageResult = intervalCapacityInArrear.computeMissingItemsAndNextNotificationDate(existingItems);
        final List<InvoiceItem> rawResults = usageResult.getInvoiceItems();
        final List<InvoiceItem> result = ImmutableList.copyOf(Iterables.filter(rawResults, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return input.getAmount().compareTo(BigDecimal.ZERO) > 0;
            }
        }));

        for (InvoiceItem item : result) {
            assertEquals(item.getCurrency(), Currency.BTC);
            assertEquals(item.getAccountId(), accountId);
            assertEquals(item.getBundleId(), bundleId);
            assertEquals(item.getSubscriptionId(), subscriptionId);
            assertEquals(item.getPlanName(), planName);
            assertEquals(item.getPhaseName(), phaseName);
            assertEquals(item.getUsageName(), usage.getName());
            assertTrue(item.getStartDate().compareTo(startDate) == 0);
            assertTrue(item.getEndDate().compareTo(firstBCDDate) == 0);
        }

        return result;
    }

}
