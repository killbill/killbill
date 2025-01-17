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
import java.util.Set;
import java.util.stream.Collectors;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.DefaultLimit;
import org.killbill.billing.catalog.DefaultTier;
import org.killbill.billing.catalog.DefaultUnit;
import org.killbill.billing.catalog.DefaultUsage;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.TrackingRecordId;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.UsageInvoiceItem;
import org.killbill.billing.invoice.usage.ContiguousIntervalUsageInArrear.UsageInArrearItemsAndNextNotificationDate;
import org.killbill.billing.invoice.usage.details.UsageCapacityInArrearAggregate;
import org.killbill.billing.invoice.usage.details.UsageInArrearTierUnitDetail;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.usage.api.RawUsageRecord;
import org.killbill.billing.usage.api.svcs.DefaultRawUsage;
import org.killbill.billing.util.config.definition.InvoiceConfig.UsageDetailMode;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.type.TypeReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestContiguousIntervalCapacityInArrear extends TestUsageInArrearBase {

    @Test(groups = "fast")
    public void testComputeToBeBilledUsage() throws Exception {

        final LocalDate startDate = new LocalDate(2014, 03, 20);
        final LocalDate endDate = new LocalDate(2014, 04, 20);

        final DefaultUnit unit = new DefaultUnit().setName("unit");
        final DefaultLimit limit = new DefaultLimit().setUnit(unit).setMax(BigDecimal.valueOf(100));

        final DefaultTier tier = createDefaultTierWithLimits(BigDecimal.TEN, limit);

        final DefaultUsage usage = createCapacityInArrearUsage(usageName, BillingPeriod.MONTHLY, tier);
        final LocalDate targetDate = startDate.plusDays(1);

        final ContiguousIntervalUsageInArrear intervalCapacityInArrear = createContiguousIntervalCapacityInArrear(usage, targetDate, false,
                                                                                                                  createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                         BillingPeriod.MONTHLY,
                                                                                                                                         Collections.<Usage>emptyList(), catalogEffectiveDate)
                                                                                                                 );

        final List<InvoiceItem> existingUsage = new ArrayList<>();
        final UsageInvoiceItem ii1 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usage.getName(), null, startDate, endDate, BigDecimal.TEN, currency);
        existingUsage.add(ii1);
        final UsageInvoiceItem ii2 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usage.getName(), null, startDate, endDate, BigDecimal.TEN, currency);
        existingUsage.add(ii2);

        // Will be ignored as is starts one day earlier.
        final UsageInvoiceItem ii3 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usage.getName(), null, startDate.minusDays(1), endDate, BigDecimal.TEN, currency);
        existingUsage.add(ii3);

        // Will be ignored as it is for a different udsage section
        final UsageInvoiceItem ii4 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, "other", null, startDate, endDate, BigDecimal.TEN, currency);
        existingUsage.add(ii4);

        // Will be ignored because non usage item
        final FixedPriceInvoiceItem ii5 = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, null, startDate, BigDecimal.TEN, currency);
        existingUsage.add(ii5);

        final Iterable<InvoiceItem> billedItems = intervalCapacityInArrear.getBilledItems(startDate, endDate, existingUsage);
        final BigDecimal result = intervalCapacityInArrear.computeBilledUsage(billedItems);
        assertEquals(result.compareTo(BigDecimal.TEN.add(BigDecimal.TEN)), 0);
    }

    @Test(groups = "fast")
    public void testComputeBilledUsage() throws Exception {

        final DefaultUnit unit1 = new DefaultUnit().setName("unit1");
        final DefaultUnit unit2 = new DefaultUnit().setName("unit2");
        final DefaultUnit unit3 = new DefaultUnit().setName("unit3");

        final DefaultLimit limit1_1 = new DefaultLimit().setUnit(unit1).setMax(BigDecimal.valueOf(100)).setMin(BigDecimal.valueOf(-1));
        final DefaultLimit limit1_2 = new DefaultLimit().setUnit(unit2).setMax(BigDecimal.valueOf(1000)).setMin(BigDecimal.valueOf(-1));
        final DefaultLimit limit1_3 = new DefaultLimit().setUnit(unit3).setMax(BigDecimal.valueOf(50)).setMin(BigDecimal.valueOf(-1));
        final DefaultTier tier1 = createDefaultTierWithLimits(BigDecimal.TEN, limit1_1, limit1_2, limit1_3);

        final DefaultLimit limit2_1 = new DefaultLimit().setUnit(unit1).setMax(BigDecimal.valueOf(200)).setMin(BigDecimal.valueOf(-1));
        final DefaultLimit limit2_2 = new DefaultLimit().setUnit(unit2).setMax(BigDecimal.valueOf(2000)).setMin(BigDecimal.valueOf(-1));
        final DefaultLimit limit2_3 = new DefaultLimit().setUnit(unit3).setMax(BigDecimal.valueOf(100)).setMin(BigDecimal.valueOf(-1));
        final DefaultTier tier2 = createDefaultTierWithLimits(new BigDecimal("20.0"), limit2_1, limit2_2, limit2_3);

        // Don't define any max for last tier to allow any number
        final DefaultLimit limit3_1 = new DefaultLimit().setUnit(unit1).setMin(BigDecimal.valueOf(-1)).setMax(BigDecimal.valueOf(-1));
        final DefaultLimit limit3_2 = new DefaultLimit().setUnit(unit2).setMin(BigDecimal.valueOf(-1)).setMax(BigDecimal.valueOf(-1));
        final DefaultLimit limit3_3 = new DefaultLimit().setUnit(unit3).setMax(BigDecimal.valueOf(-1)).setMin(BigDecimal.valueOf(-1));
        final DefaultTier tier3 = createDefaultTierWithLimits(new BigDecimal("30.0"), limit3_1, limit3_2, limit3_3);

        final DefaultUsage usage = createCapacityInArrearUsage(usageName, BillingPeriod.MONTHLY, tier1, tier2, tier3);

        final LocalDate targetDate = new LocalDate(2014, 03, 20);

        final ContiguousIntervalCapacityUsageInArrear intervalCapacityInArrear = createContiguousIntervalCapacityInArrear(usage, targetDate, false,
                                                                                                                          createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                                 BillingPeriod.MONTHLY,
                                                                                                                                                 Collections.<Usage>emptyList(), catalogEffectiveDate)
                                                                                                                         );
        // Tier 1 (both units from tier 1)
        UsageCapacityInArrearAggregate result = intervalCapacityInArrear.computeToBeBilledCapacityInArrear(List.of(new DefaultRolledUpUnit("unit1", BigDecimal.valueOf(100L)),
                                                                                                                   new DefaultRolledUpUnit("unit2", BigDecimal.valueOf(1000L)),
                                                                                                                   new DefaultRolledUpUnit("unit3", BigDecimal.valueOf(50L))));
        assertEquals(result.getTierDetails().size(), 3);
        assertTrue(result.getAmount().compareTo(BigDecimal.TEN) == 0);

        // Tier 2 (only one unit from tier 1)
        result = intervalCapacityInArrear.computeToBeBilledCapacityInArrear(List.of(new DefaultRolledUpUnit("unit1", BigDecimal.valueOf(100L)),
                                                                                    new DefaultRolledUpUnit("unit2", BigDecimal.valueOf(1001L))));
        assertTrue(result.getAmount().compareTo(new BigDecimal("20.0")) == 0);

        // Tier 2 (only one unit from tier 1)
        result = intervalCapacityInArrear.computeToBeBilledCapacityInArrear(List.of(new DefaultRolledUpUnit("unit1", BigDecimal.valueOf(101L)),
                                                                                    new DefaultRolledUpUnit("unit2", BigDecimal.valueOf(1000L))));
        assertTrue(result.getAmount().compareTo(new BigDecimal("20.0")) == 0);

        // Tier 2 (both units from tier 2)
        result = intervalCapacityInArrear.computeToBeBilledCapacityInArrear(List.of(new DefaultRolledUpUnit("unit1", BigDecimal.valueOf(101L)),
                                                                                    new DefaultRolledUpUnit("unit2", BigDecimal.valueOf(1001L))));
        assertTrue(result.getAmount().compareTo(new BigDecimal("20.0")) == 0);

        // Tier 3 (only one unit from tier 3)
        result = intervalCapacityInArrear.computeToBeBilledCapacityInArrear(List.of(new DefaultRolledUpUnit("unit1", BigDecimal.valueOf(10L)),
                                                                                    new DefaultRolledUpUnit("unit2", BigDecimal.valueOf(2001L))));
        assertTrue(result.getAmount().compareTo(new BigDecimal("30.0")) == 0);
    }

    @Test(groups = "fast")
    public void testComputeMissingItems() throws Exception {

        final LocalDate startDate = new LocalDate(2014, 03, 20);
        final LocalDate firstBCDDate = new LocalDate(2014, 04, 15);
        final LocalDate endDate = new LocalDate(2014, 05, 15);

        final List<RawUsageRecord> rawUsageRecords = new ArrayList<>();

        //
        // First period: startDate - firstBCDDate
        //
        // 2 items for unit1
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "unit1", BigDecimal.valueOf(130L), "tracking-1"));
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "unit1", BigDecimal.valueOf(271L), "tracking-2"));
        // 1 items for unit2
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 24).toDateTimeAtStartOfDay(), "unit2", BigDecimal.valueOf(10L), "tracking-1"));

        //
        // Second period: firstBCDDate - endDate
        //
        // 1 items unit1
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 04, 15).toDateTimeAtStartOfDay(), "unit1", BigDecimal.valueOf(199L), "tracking-4"));
        // 1 items unit2
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 04, 15).toDateTimeAtStartOfDay(), "unit2", BigDecimal.valueOf(20L), "tracking-5"));

        final DefaultUnit unit1 = new DefaultUnit().setName("unit1");
        final DefaultLimit limit1 = new DefaultLimit().setUnit(unit1).setMax(BigDecimal.valueOf(-1));

        final DefaultUnit unit2 = new DefaultUnit().setName("unit2");
        final DefaultLimit limit2 = new DefaultLimit().setUnit(unit2).setMax(BigDecimal.valueOf(-1));

        final DefaultTier tier = createDefaultTierWithLimits(BigDecimal.TEN, limit1, limit2);

        final DefaultUsage usage = createCapacityInArrearUsage(usageName, BillingPeriod.MONTHLY, tier);

        final LocalDate targetDate = endDate;
        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);
        final BillingEvent event2 = createMockBillingEvent(endDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);

        final ContiguousIntervalCapacityUsageInArrear intervalCapacityInArrear = createContiguousIntervalCapacityInArrear(usage, targetDate, true, event1, event2);

        final List<InvoiceItem> invoiceItems = new ArrayList<InvoiceItem>();
        final InvoiceItem ii1 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usage.getName(), null, startDate, firstBCDDate, BigDecimal.ONE, currency);
        invoiceItems.add(ii1);

        final InvoiceItem ii2 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usage.getName(), null, firstBCDDate, endDate, BigDecimal.ONE, currency);
        invoiceItems.add(ii2);

        final UsageInArrearItemsAndNextNotificationDate usageResult = intervalCapacityInArrear.computeMissingItemsAndNextNotificationDate(rawUsageRecords, EMPTY_EXISTING_TRACKING_IDS, invoiceItems, false);
        final List<InvoiceItem> result = usageResult.getInvoiceItems();
        assertEquals(result.size(), 2);

        final Set<TrackingRecordId> trackingIds = usageResult.getTrackingIds();
        checkTrackingIds(rawUsageRecords, usageResult.getTrackingIds());

        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("9.0")), 0, String.format("%s != 9.0", result.get(0).getAmount()));
        assertEquals(result.get(0).getCurrency(), Currency.USD);
        assertEquals(result.get(0).getAccountId(), accountId);
        assertEquals(result.get(0).getBundleId(), bundleId);
        assertEquals(result.get(0).getSubscriptionId(), subscriptionId);
        assertEquals(result.get(0).getPlanName(), planName);
        assertEquals(result.get(0).getPhaseName(), phaseName);
        assertEquals(result.get(0).getUsageName(), usage.getName());
        assertTrue(result.get(0).getStartDate().compareTo(startDate) == 0);
        assertTrue(result.get(0).getEndDate().compareTo(firstBCDDate) == 0);

        assertEquals(result.get(1).getAmount().compareTo(new BigDecimal("9.0")), 0, String.format("%s != 9.0", result.get(0).getAmount()));
        assertEquals(result.get(1).getCurrency(), Currency.USD);
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
    public void testMultipleItemsAndTiersAggregateMode() throws Exception {
        testMultipleItemsAndTiers(UsageDetailMode.AGGREGATE);
    }

    @Test(groups = "fast")
    public void testMultipleItemsAndTiersDetailMode() throws Exception {
        testMultipleItemsAndTiers(UsageDetailMode.DETAIL);
    }

    private void testMultipleItemsAndTiers(UsageDetailMode usageDetailMode) throws Exception {

        // Case 1
        List<RawUsageRecord> rawUsageRecords = new ArrayList<>();
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "FOO", BigDecimal.valueOf(5L), "tracking-1"));
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "BAR", BigDecimal.valueOf(99L), "tracking-2"));

        List<InvoiceItem> result = produceInvoiceItems(rawUsageRecords, usageDetailMode, Collections.emptyList());
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
        rawUsageRecords = new ArrayList<RawUsageRecord>();
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "FOO", BigDecimal.valueOf(5L), "tracking-1"));
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "BAR", BigDecimal.valueOf(101L), "tracking-2"));
        result = produceInvoiceItems(rawUsageRecords, usageDetailMode, Collections.emptyList());
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
        rawUsageRecords = new ArrayList<RawUsageRecord>();
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "FOO", BigDecimal.valueOf(75L), "tracking-3"));
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "BAR", BigDecimal.valueOf(101L), "tracking-3"));
        result = produceInvoiceItems(rawUsageRecords, usageDetailMode, Collections.emptyList());
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
    public void testMultipleItemsAndTiersWithExistingItems() throws Exception {

        // let's assume we have some existing usage
        final UsageInArrearTierUnitDetail existingFooUsageTier1 = new UsageInArrearTierUnitDetail(1, "FOO", BigDecimal.ONE, BigDecimal.valueOf(9L));
        final UsageInArrearTierUnitDetail existingBarUsageTier2 = new UsageInArrearTierUnitDetail(2, "BAR", BigDecimal.TEN, BigDecimal.valueOf(200L));

        List<RawUsageRecord> rawUsageRecords = new ArrayList<RawUsageRecord>();
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "FOO", BigDecimal.valueOf(60L), "tracking-1")); // tier 3
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "BAR", BigDecimal.valueOf(200L), "tracking-1")); // tier 2

        final List<UsageInArrearTierUnitDetail> existingUsage = List.of(existingFooUsageTier1, existingBarUsageTier2);

        final String existingUsageJson = objectMapper.writeValueAsString(new UsageCapacityInArrearAggregate(existingUsage, BigDecimal.TEN));

        final List<InvoiceItem> existingItems = new ArrayList<InvoiceItem>();
        final InvoiceItem ii1 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usageName, null, new LocalDate(2014, 03, 20), new LocalDate(2014, 04, 15), BigDecimal.TEN, null, currency, null, existingUsageJson);
        existingItems.add(ii1);

        List<InvoiceItem> result = produceInvoiceItems(rawUsageRecords, UsageDetailMode.AGGREGATE, existingItems);
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

    private List<InvoiceItem> produceInvoiceItems(List<RawUsageRecord> rawUsageRecords, UsageDetailMode usageDetailMode, List<InvoiceItem> existingItems) throws Exception {

        final LocalDate startDate = new LocalDate(2014, 03, 20);
        final LocalDate firstBCDDate = new LocalDate(2014, 04, 15);
        final LocalDate endDate = new LocalDate(2014, 05, 15);

        final DefaultUnit unitFoo = new DefaultUnit().setName("FOO");
        final DefaultUnit unitBar = new DefaultUnit().setName("BAR");

        final DefaultLimit unitFooLimitTier1 = new DefaultLimit().setUnit(unitFoo).setMax(BigDecimal.valueOf(10));
        final DefaultLimit unitBarLimitTier1 = new DefaultLimit().setUnit(unitBar).setMax(BigDecimal.valueOf(100));
        final DefaultTier tier1 = createDefaultTierWithLimits(BigDecimal.ONE, unitFooLimitTier1, unitBarLimitTier1);

        final DefaultLimit unitFooLimitTier2 = new DefaultLimit().setUnit(unitFoo).setMax(BigDecimal.valueOf(50));
        final DefaultLimit unitBarLimitTier2 = new DefaultLimit().setUnit(unitBar).setMax(BigDecimal.valueOf(500));
        final DefaultTier tier2 = createDefaultTierWithLimits(BigDecimal.TEN, unitFooLimitTier2, unitBarLimitTier2);

        final DefaultLimit unitFooLimitTier3 = new DefaultLimit().setUnit(unitFoo).setMax(BigDecimal.valueOf(75));
        final DefaultLimit unitBarLimitTier3 = new DefaultLimit().setUnit(unitBar).setMax(BigDecimal.valueOf(750));
        final DefaultTier tier3 = createDefaultTierWithLimits(new BigDecimal("100.0"), unitFooLimitTier3, unitBarLimitTier3);

        final DefaultUsage usage = createCapacityInArrearUsage(usageName, BillingPeriod.MONTHLY, tier1, tier2, tier3);

        final LocalDate targetDate = endDate;

        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);
        final BillingEvent event2 = createMockBillingEvent(endDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);

        final ContiguousIntervalCapacityUsageInArrear intervalCapacityInArrear = createContiguousIntervalCapacityInArrear(usage, targetDate, true, usageDetailMode, event1, event2);

        final UsageInArrearItemsAndNextNotificationDate usageResult = intervalCapacityInArrear.computeMissingItemsAndNextNotificationDate(rawUsageRecords, EMPTY_EXISTING_TRACKING_IDS, existingItems, false);

        checkTrackingIds(rawUsageRecords, usageResult.getTrackingIds());

        final List<InvoiceItem> rawResults = usageResult.getInvoiceItems();
        final List<InvoiceItem> result = rawResults.stream()
                                                   .filter(input -> input.getAmount().compareTo(BigDecimal.ZERO) > 0)
                                                   .collect(Collectors.toUnmodifiableList());

        for (final InvoiceItem item : result) {
            assertEquals(item.getCurrency(), Currency.USD);
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
