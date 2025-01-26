/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.killbill.billing.catalog.DefaultTier;
import org.killbill.billing.catalog.DefaultTieredBlock;
import org.killbill.billing.catalog.DefaultUsage;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.TierBlockPolicy;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.UsageInvoiceItem;
import org.killbill.billing.invoice.usage.ContiguousIntervalUsageInArrear.UsageInArrearItemsAndNextNotificationDate;
import org.killbill.billing.invoice.usage.details.UsageConsumableInArrearAggregate;
import org.killbill.billing.invoice.usage.details.UsageConsumableInArrearTierUnitAggregate;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.usage.api.RawUsageRecord;
import org.killbill.billing.usage.api.svcs.DefaultRawUsage;
import org.killbill.billing.util.config.definition.InvoiceConfig.UsageDetailMode;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.type.TypeReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestContiguousIntervalConsumableInArrear extends TestUsageInArrearBase {

    // Only works if the RolledUpUsage have at least one
    private static final Comparator<RolledUpUsageWithMetadata> TEST_ROLLED_UP_FIRST_USAGE_COMPARATOR = Comparator.comparing(rolledUpUsageWithMetadata -> rolledUpUsageWithMetadata.getRolledUpUnits().get(0).getUnitType());

    @Test(groups = "fast")
    public void testBilledDetailsForUnitType() throws Exception {

        final LocalDate startDate = new LocalDate(2014, 03, 20);
        final LocalDate targetDate = startDate.plusDays(1);


        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 1000, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);
        final ContiguousIntervalConsumableUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, targetDate, false,
                                                                                                                                createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                                       BillingPeriod.MONTHLY,
                                                                                                                                                       Collections.emptyList(), catalogEffectiveDate));

        final UsageConsumableInArrearTierUnitAggregate detail1 = new UsageConsumableInArrearTierUnitAggregate(3, "FOO", new BigDecimal("0.50"), BigDecimal.ONE, BigDecimal.valueOf(700L));
        final UsageConsumableInArrearTierUnitAggregate detail2 = new UsageConsumableInArrearTierUnitAggregate(2, "FOO", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.valueOf(500L));
        final UsageConsumableInArrearTierUnitAggregate detail3 = new UsageConsumableInArrearTierUnitAggregate(1, "FOO", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.valueOf(10L));
        final UsageConsumableInArrearTierUnitAggregate detail4 = new UsageConsumableInArrearTierUnitAggregate(2, "FOO", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.valueOf(50L));
        final UsageConsumableInArrearTierUnitAggregate detail5 = new UsageConsumableInArrearTierUnitAggregate(1, "FOO", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.valueOf(100L));

        final List<UsageConsumableInArrearTierUnitAggregate> existingUsage = List.of(detail1, detail2, detail3, detail4, detail5);

        final UsageConsumableInArrearAggregate usageConsumableInArrearDetail = new UsageConsumableInArrearAggregate(existingUsage);

        final String existingUsageJson = objectMapper.writeValueAsString(usageConsumableInArrearDetail);

        final List<InvoiceItem> existingItems = new ArrayList<>();
        final InvoiceItem ii1 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usageName, null,
                                                     new LocalDate(2014, 03, 20), new LocalDate(2014, 04, 15), new BigDecimal("570.00"), null, currency, null, existingUsageJson);
        existingItems.add(ii1);

        final List<UsageConsumableInArrearTierUnitAggregate> aggregateDetails = intervalConsumableInArrear.getBilledDetailsForUnitType(existingItems, "FOO");
        assertEquals(aggregateDetails.size(), 3);
        assertEquals(aggregateDetails.get(0).getTier(), 1);
        assertEquals(aggregateDetails.get(0).getQuantity(), BigDecimal.valueOf(110));
        assertEquals(aggregateDetails.get(1).getTier(), 2);
        assertEquals(aggregateDetails.get(1).getQuantity(), BigDecimal.valueOf(550));
        assertEquals(aggregateDetails.get(2).getTier(), 3);
        assertEquals(aggregateDetails.get(2).getQuantity(), BigDecimal.valueOf(700));

    }

    @Test(groups = "fast")
    public void testComputeToBeBilledUsage() throws Exception {

        final LocalDate startDate = new LocalDate(2014, 03, 20);
        final LocalDate endDate = new LocalDate(2014, 04, 20);

        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 1000, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        final LocalDate targetDate = startDate.plusDays(1);
        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, targetDate, false,
                                                                                                                      createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                             BillingPeriod.MONTHLY,
                                                                                                                                             Collections.emptyList(), catalogEffectiveDate)
                                                                                                                     );

        final List<InvoiceItem> existingUsage = new ArrayList<>();
        final UsageInvoiceItem ii1 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usage.getName(), null,
                                                          startDate, endDate, BigDecimal.TEN, currency);
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

        final BigDecimal result = intervalConsumableInArrear.computeBilledUsage(intervalConsumableInArrear.getBilledItems(startDate, endDate, existingUsage));
        assertEquals(result.compareTo(BigDecimal.TEN.add(BigDecimal.TEN)), 0);
    }

    @Test(groups = "fast")
    public void testComputeBilledUsageSizeOneWith_ALL_TIERS() throws Exception {

        final DefaultTieredBlock block1 = createDefaultTieredBlock("unit", 1, 10, new BigDecimal("1.5"));
        final DefaultTier tier1 = createDefaultTierWithBlocks(block1);

        final DefaultTieredBlock block2 = createDefaultTieredBlock("unit", 1, 100, new BigDecimal("1.0"));
        final DefaultTier tier2 = createDefaultTierWithBlocks(block2);

        final DefaultTieredBlock block3 = createDefaultTieredBlock("unit", 1, 1000, new BigDecimal("0.5"));
        final DefaultTier tier3 = createDefaultTierWithBlocks(block3);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier1, tier2, tier3);

        final LocalDate targetDate = new LocalDate(2014, 03, 20);

        final ContiguousIntervalConsumableUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, targetDate, false,
                                                                                                                                createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                                       BillingPeriod.MONTHLY,
                                                                                                                                                       Collections.emptyList(), catalogEffectiveDate)
                                                                                                                               );

        List<UsageConsumableInArrearTierUnitAggregate> result = intervalConsumableInArrear.computeToBeBilledConsumableInArrear(null, null, new DefaultRolledUpUnit("unit", BigDecimal.valueOf(111L)), Collections.emptyList(), false);
        assertEquals(result.size(), 3);
        // 111 = 10 (tier1) + 100 (tier2) + 1 (tier3) => 10 * 1.5 + 100 * 1 + 1 * 0.5 = 115.5
        assertEquals(result.get(0).getAmount(), new BigDecimal("15.0"));
        assertEquals(result.get(1).getAmount(), new BigDecimal("100.0"));
        assertEquals(result.get(2).getAmount(), new BigDecimal("0.5"));
    }

    @Test(groups = "fast")
    public void testComputeBilledUsageWith_ALL_TIERS() throws Exception {

        final DefaultTieredBlock block1 = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier1 = createDefaultTierWithBlocks(block1);

        final DefaultTieredBlock block2 = createDefaultTieredBlock("unit", 1000, 100, BigDecimal.ONE);
        final DefaultTier tier2 = createDefaultTierWithBlocks(block2);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier1, tier2);

        final LocalDate targetDate = new LocalDate(2014, 03, 20);

        final ContiguousIntervalConsumableUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, targetDate, false,
                                                                                                                                createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                                       BillingPeriod.MONTHLY,
                                                                                                                                                       Collections.emptyList(), catalogEffectiveDate)
                                                                                                                               );

        List<UsageConsumableInArrearTierUnitAggregate> result = intervalConsumableInArrear.computeToBeBilledConsumableInArrear(null, null, new DefaultRolledUpUnit("unit", BigDecimal.valueOf(5325L)), Collections.emptyList(), false);
        assertEquals(result.size(), 2);

        // 5000 = 1000 (tier1) + 4325 (tier2) => 10 + 5 = 15
        assertEquals(result.get(0).getAmount(), new BigDecimal("10"));
        assertEquals(result.get(1).getAmount(), new BigDecimal("5"));
    }

    @Test(groups = "fast")
    public void testComputeBilledUsageWithUnlimitedMaxWith_ALL_TIERS() throws Exception {

        final DefaultTieredBlock block1 = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier1 = createDefaultTierWithBlocks(block1);

        final DefaultTieredBlock block2 = createDefaultTieredBlock("unit", 1000, -1, BigDecimal.ONE);
        final DefaultTier tier2 = createDefaultTierWithBlocks(block2);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier1, tier2);

        final LocalDate targetDate = new LocalDate(2014, 03, 20);

        final ContiguousIntervalConsumableUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, targetDate, false,
                                                                                                                                createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                                       BillingPeriod.MONTHLY,
                                                                                                                                                       Collections.emptyList(), catalogEffectiveDate)
                                                                                                                               );

        List<UsageConsumableInArrearTierUnitAggregate> result = intervalConsumableInArrear.computeToBeBilledConsumableInArrear(null, null, new DefaultRolledUpUnit("unit", BigDecimal.valueOf(5325L)), Collections.emptyList(), false);
        assertEquals(result.size(), 2);

        // 5000 = 1000 (tier1) + 4325 (tier2) => 10 + 5 = 15
        assertEquals(result.get(0).getAmount(), new BigDecimal("10"));
        assertEquals(result.get(1).getAmount(), new BigDecimal("5"));
    }

    @Test(groups = "fast")
    public void testComputeBilledUsageWith_TOP_TIER() throws Exception {

        final DefaultTieredBlock block1 = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier1 = createDefaultTierWithBlocks(block1);

        final DefaultTieredBlock block2 = createDefaultTieredBlock("unit", 1000, 100, BigDecimal.ONE);
        final DefaultTier tier2 = createDefaultTierWithBlocks(block2);

        final DefaultTieredBlock block3 = createDefaultTieredBlock("unit", 1000, 100, new BigDecimal("0.5"));
        final DefaultTier tier3 = createDefaultTierWithBlocks(block3);

        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.TOP_TIER, tier1, tier2, tier3);

        final LocalDate targetDate = new LocalDate(2014, 03, 20);

        final ContiguousIntervalConsumableUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, targetDate, false,
                                                                                                                                createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                                       BillingPeriod.MONTHLY,
                                                                                                                                                       Collections.emptyList(), catalogEffectiveDate)
                                                                                                                               );
        //
        // In this model unit amount is first used to figure out which tier we are in, and then we price all unit at that 'target' tier
        //
        List<UsageConsumableInArrearTierUnitAggregate> inputTier1 = intervalConsumableInArrear.computeToBeBilledConsumableInArrear(null, null, new DefaultRolledUpUnit("unit", BigDecimal.valueOf(1000L)), Collections.emptyList(), false);
        assertEquals(inputTier1.size(), 1);
        // 1000 units => (tier1) : 1000 / 100 + 1000 % 100 = 10
        assertEquals(inputTier1.get(0).getAmount(), new BigDecimal("10"));

        List<UsageConsumableInArrearTierUnitAggregate> inputTier2 = intervalConsumableInArrear.computeToBeBilledConsumableInArrear(null, null, new DefaultRolledUpUnit("unit", BigDecimal.valueOf(101000L)), Collections.emptyList(), false);
        assertEquals(inputTier2.size(), 1);
        // 101000 units => (tier2) :  101000 / 1000 + 101000 % 1000 = 101 + 0 = 101
        assertEquals(inputTier2.get(0).getAmount(), new BigDecimal("101"));

        List<UsageConsumableInArrearTierUnitAggregate> inputTier3 = intervalConsumableInArrear.computeToBeBilledConsumableInArrear(null, null, new DefaultRolledUpUnit("unit", BigDecimal.valueOf(101001L)), Collections.emptyList(), false);
        assertEquals(inputTier3.size(), 1);
        // 101001 units => (tier3) : 101001 / 1000 + 101001 % 1000 = 101 + 1 = 102 units => $51
        assertEquals(inputTier3.get(0).getAmount(), new BigDecimal("51.0"));

        // If we pass the maximum of the last tier, we price all units at the last tier
        List<UsageConsumableInArrearTierUnitAggregate> inputLastTier = intervalConsumableInArrear.computeToBeBilledConsumableInArrear(null, null, new DefaultRolledUpUnit("unit", BigDecimal.valueOf(300000L)), Collections.emptyList(), false);
        assertEquals(inputLastTier.size(), 1);
        // 300000 units => (tier3) : 300000 / 1000 + 300000 % 1000 = 300 units => $150
        assertEquals(inputLastTier.get(0).getAmount(), new BigDecimal("150.0"));
    }

    @Test(groups = "fast")
    public void testComputeBilledUsageWithUnlimitedMaxWith_TOP_TIER() throws Exception {

        final DefaultTieredBlock block1 = createDefaultTieredBlock("unit", 100, 10, BigDecimal.TEN);
        final DefaultTier tier1 = createDefaultTierWithBlocks(block1);

        final DefaultTieredBlock block2 = createDefaultTieredBlock("unit", 100, -1, BigDecimal.ONE);
        final DefaultTier tier2 = createDefaultTierWithBlocks(block2);

        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.TOP_TIER, tier1, tier2);

        final LocalDate targetDate = new LocalDate(2014, 03, 20);

        final ContiguousIntervalConsumableUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, targetDate, false,
                                                                                                                                createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                                       BillingPeriod.MONTHLY,
                                                                                                                                                       Collections.<Usage>emptyList(), catalogEffectiveDate)
                                                                                                                               );
        //
        // In this model unit amount is first used to figure out which tier we are in, and then we price all unit at that 'target' tier
        //
        List<UsageConsumableInArrearTierUnitAggregate> inputTier1 = intervalConsumableInArrear.computeToBeBilledConsumableInArrear(null, null, new DefaultRolledUpUnit("unit", BigDecimal.valueOf(2000L)), Collections.emptyList(), false);
        // Target tier 2:
        assertEquals(inputTier1.size(), 1);
        assertEquals(inputTier1.get(0).getAmount(), new BigDecimal("20"));
    }

    @Test(groups = "fast")
    public void testComputeBilledUsageSizeOneWith_TOP_TIER() throws Exception {

        final DefaultTieredBlock block1 = createDefaultTieredBlock("unit", 1, 10, new BigDecimal("1.5"));
        final DefaultTier tier1 = createDefaultTierWithBlocks(block1);

        final DefaultTieredBlock block2 = createDefaultTieredBlock("unit", 1, 100, new BigDecimal("1.0"));
        final DefaultTier tier2 = createDefaultTierWithBlocks(block2);

        final DefaultTieredBlock block3 = createDefaultTieredBlock("unit", 1, 1000, new BigDecimal("0.5"));
        final DefaultTier tier3 = createDefaultTierWithBlocks(block3);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.TOP_TIER, tier1, tier2, tier3);

        final LocalDate targetDate = new LocalDate(2014, 03, 20);

        final ContiguousIntervalConsumableUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, targetDate, false,
                                                                                                                                createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                                       BillingPeriod.MONTHLY,
                                                                                                                                                       Collections.emptyList(), catalogEffectiveDate)
                                                                                                                               );

        List<UsageConsumableInArrearTierUnitAggregate> result = intervalConsumableInArrear.computeToBeBilledConsumableInArrear(null, null, new DefaultRolledUpUnit("unit", BigDecimal.valueOf(111L)), Collections.emptyList(), false);
        assertEquals(result.size(), 1);

        // 111 = 111 * 0.5 =
        assertEquals(result.get(0).getAmount(), new BigDecimal("55.5"));
    }

    @Test(groups = "fast")
    public void testComputeMissingItems() throws Exception {

        final LocalDate startDate = new LocalDate(2014, 03, 20);
        final LocalDate firstBCDDate = new LocalDate(2014, 04, 15);
        final LocalDate endDate = new LocalDate(2014, 05, 15);

        // 2 items for startDate - firstBCDDate
        final List<RawUsageRecord> rawUsageRecords = new ArrayList<RawUsageRecord>();
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "unit", BigDecimal.valueOf(130L), "tracking-1"));
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "unit", BigDecimal.valueOf(271L), "tracking-1"));
        // 1 items for firstBCDDate - endDate
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 04, 15).toDateTimeAtStartOfDay(), "unit", BigDecimal.valueOf(199L), "tracking-2"));

        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        final LocalDate targetDate = endDate;

        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);
        final BillingEvent event2 = createMockBillingEvent(endDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, targetDate, true, event1, event2);

        final List<InvoiceItem> invoiceItems = new ArrayList<InvoiceItem>();
        final InvoiceItem ii1 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName,
                                                     usage.getName(), null, startDate, firstBCDDate, BigDecimal.ONE, currency);
        invoiceItems.add(ii1);

        final InvoiceItem ii2 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName,
                                                     usage.getName(), null, firstBCDDate, endDate, BigDecimal.ONE, currency);
        invoiceItems.add(ii2);

        final UsageInArrearItemsAndNextNotificationDate usageResult = intervalConsumableInArrear.computeMissingItemsAndNextNotificationDate(rawUsageRecords, EMPTY_EXISTING_TRACKING_IDS, invoiceItems, false);
        checkTrackingIds(rawUsageRecords, usageResult.getTrackingIds());

        final List<InvoiceItem> result = usageResult.getInvoiceItems();
        assertEquals(result.size(), 2);

        // Invoiced for 1 USD and used 130 + 271 = 401 => 5 blocks => 5 USD so remaining piece should be 4 USD
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("4.0")), 0, String.format("%s != 4.0", result.get(0).getAmount()));
        assertEquals(result.get(0).getCurrency(), Currency.USD);
        assertEquals(result.get(0).getAccountId(), accountId);
        assertEquals(result.get(0).getBundleId(), bundleId);
        assertEquals(result.get(0).getSubscriptionId(), subscriptionId);
        assertEquals(result.get(0).getPlanName(), planName);
        assertEquals(result.get(0).getPhaseName(), phaseName);
        assertEquals(result.get(0).getUsageName(), usage.getName());
        assertTrue(result.get(0).getStartDate().compareTo(startDate) == 0);
        assertTrue(result.get(0).getEndDate().compareTo(firstBCDDate) == 0);

        assertNotNull(result.get(0).getItemDetails());
        UsageConsumableInArrearAggregate usageDetail = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<UsageConsumableInArrearAggregate>() {});
        List<UsageConsumableInArrearTierUnitAggregate> itemDetails = usageDetail.getTierDetails();
        assertEquals(itemDetails.size(), 1);
        // Because we did not have the details before, the new details don't take into account the
        assertEquals(itemDetails.get(0).getAmount().compareTo(new BigDecimal("5.0")), 0);

        // Invoiced for 1 USD and used 199  => 2 blocks => 2 USD so remaining piece should be 1 USD
        assertEquals(result.get(1).getAmount().compareTo(new BigDecimal("1.0")), 0, String.format("%s != 1.0", result.get(0).getAmount()));
        assertEquals(result.get(1).getCurrency(), Currency.USD);
        assertEquals(result.get(1).getAccountId(), accountId);
        assertEquals(result.get(1).getBundleId(), bundleId);
        assertEquals(result.get(1).getSubscriptionId(), subscriptionId);
        assertEquals(result.get(1).getPlanName(), planName);
        assertEquals(result.get(1).getPhaseName(), phaseName);
        assertEquals(result.get(1).getUsageName(), usage.getName());
        assertTrue(result.get(1).getStartDate().compareTo(firstBCDDate) == 0);
        assertTrue(result.get(1).getEndDate().compareTo(endDate) == 0);
        assertNotNull(result.get(1).getItemDetails());

        usageDetail = objectMapper.readValue(result.get(1).getItemDetails(), new TypeReference<UsageConsumableInArrearAggregate>() {});
        itemDetails = usageDetail.getTierDetails();
        assertEquals(itemDetails.size(), 1);
        assertEquals(itemDetails.get(0).getAmount().compareTo(new BigDecimal("2.0")), 0);

    }

    @Test(groups = "fast")
    public void testGetRolledUpUsage() throws Exception {

        final DefaultTieredBlock tieredBlock1 = createDefaultTieredBlock("unit", 100, 1000, BigDecimal.ONE);
        final DefaultTieredBlock tieredBlock2 = createDefaultTieredBlock("unit2", 10, 1000, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(tieredBlock1, tieredBlock2);

        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        final LocalDate t0 = new LocalDate(2015, 03, BCD);
        final BillingEvent eventT0 = createMockBillingEvent(t0.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);

        final LocalDate t1 = new LocalDate(2015, 04, BCD);
        final BillingEvent eventT1 = createMockBillingEvent(t1.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);

        final LocalDate t2 = new LocalDate(2015, 05, BCD);
        final BillingEvent eventT2 = createMockBillingEvent(t2.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);

        final LocalDate t3 = new LocalDate(2015, 06, BCD);
        final BillingEvent eventT3 = createMockBillingEvent(t3.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);

        final LocalDate targetDate = t3;

        // Prev t0
        final RawUsageRecord raw1 = new DefaultRawUsage(subscriptionId, new LocalDate(2015, 03, 01).toDateTimeAtStartOfDay(), "unit", BigDecimal.valueOf(12L), "tracking-1");

        // t0 - t1
        final RawUsageRecord raw2 = new DefaultRawUsage(subscriptionId, new LocalDate(2015, 03, 15).toDateTimeAtStartOfDay(), "unit", BigDecimal.valueOf(6L), "tracking-1");
        final RawUsageRecord raw3 = new DefaultRawUsage(subscriptionId, new LocalDate(2015, 03, 25).toDateTimeAtStartOfDay(), "unit", BigDecimal.valueOf(4L), "tracking-1");

        // t1 - t2 nothing

        // t2 - t3
        final RawUsageRecord raw4 = new DefaultRawUsage(subscriptionId, new LocalDate(2015, 05, 15).toDateTimeAtStartOfDay(), "unit", BigDecimal.valueOf(13L), "tracking-2");
        final RawUsageRecord oraw1 = new DefaultRawUsage(subscriptionId, new LocalDate(2015, 05, 21).toDateTimeAtStartOfDay(), "unit2", BigDecimal.valueOf(21L), "tracking-2");
        final RawUsageRecord raw5 = new DefaultRawUsage(subscriptionId, new LocalDate(2015, 05, 31).toDateTimeAtStartOfDay(), "unit", BigDecimal.valueOf(7L), "tracking-2");

        // after t3
        final RawUsageRecord raw6 = new DefaultRawUsage(subscriptionId, new LocalDate(2015, 06, 15).toDateTimeAtStartOfDay(), "unit", BigDecimal.valueOf(100L), "tracking-3");

        final List<RawUsageRecord> rawUsageRecord = List.of(raw1, raw2, raw3, raw4, oraw1, raw5, raw6);

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, targetDate, true, eventT0, eventT1, eventT2, eventT3);

        final List<RolledUpUsageWithMetadata> unsortedRolledUpUsage = intervalConsumableInArrear.getRolledUpUsage(rawUsageRecord).getUsage();
        Assert.assertEquals(unsortedRolledUpUsage.size(), 3);

        final List<RolledUpUsageWithMetadata> rolledUpUsage = unsortedRolledUpUsage.stream()
                .sorted(TEST_ROLLED_UP_FIRST_USAGE_COMPARATOR)
                .collect(Collectors.toUnmodifiableList());

        Assert.assertEquals(rolledUpUsage.get(0).getStart().toLocalDate().compareTo(t0), 0);
        Assert.assertEquals(rolledUpUsage.get(0).getEnd().toLocalDate().compareTo(t1), 0);
        Assert.assertEquals(rolledUpUsage.get(0).getRolledUpUnits().size(), 2);
        Assert.assertEquals(rolledUpUsage.get(0).getRolledUpUnits().get(0).getUnitType(), "unit");
        Assert.assertEquals(rolledUpUsage.get(0).getRolledUpUnits().get(0).getAmount(), BigDecimal.valueOf(10L));
        Assert.assertEquals(rolledUpUsage.get(0).getRolledUpUnits().get(1).getUnitType(), "unit2");
        Assert.assertEquals(rolledUpUsage.get(0).getRolledUpUnits().get(1).getAmount(), BigDecimal.valueOf(0L));

        Assert.assertEquals(rolledUpUsage.get(1).getStart().toLocalDate().compareTo(t1), 0);
        Assert.assertEquals(rolledUpUsage.get(1).getEnd().toLocalDate().compareTo(t2), 0);
        Assert.assertEquals(rolledUpUsage.get(1).getRolledUpUnits().size(), 2);
        Assert.assertEquals(rolledUpUsage.get(1).getRolledUpUnits().get(0).getUnitType(), "unit");
        Assert.assertEquals(rolledUpUsage.get(1).getRolledUpUnits().get(0).getAmount(), BigDecimal.valueOf(0L));
        Assert.assertEquals(rolledUpUsage.get(1).getRolledUpUnits().get(1).getUnitType(), "unit2");
        Assert.assertEquals(rolledUpUsage.get(1).getRolledUpUnits().get(1).getAmount(), BigDecimal.valueOf(0L));

        Assert.assertEquals(rolledUpUsage.get(2).getStart().toLocalDate().compareTo(t2), 0);
        Assert.assertEquals(rolledUpUsage.get(2).getEnd().toLocalDate().compareTo(t3), 0);
        Assert.assertEquals(rolledUpUsage.get(2).getRolledUpUnits().size(), 2);
        Assert.assertEquals(rolledUpUsage.get(2).getRolledUpUnits().get(0).getUnitType(), "unit");
        Assert.assertEquals(rolledUpUsage.get(2).getRolledUpUnits().get(0).getAmount(), BigDecimal.valueOf(20L));
        Assert.assertEquals(rolledUpUsage.get(2).getRolledUpUnits().get(1).getUnitType(), "unit2");
        Assert.assertEquals(rolledUpUsage.get(2).getRolledUpUnits().get(1).getAmount(), BigDecimal.valueOf(21L));
    }

    @Test(groups = "fast")
    public void testBilledUsage() throws Exception {

        final DefaultTieredBlock block1 = createDefaultTieredBlock("cell-phone-minutes", 1000, 10000, new BigDecimal("0.5"));
        final DefaultTieredBlock block2 = createDefaultTieredBlock("Mbytes", 512, 512000, new BigDecimal("0.3"));
        final DefaultTier tier = createDefaultTierWithBlocks(block1, block2);

        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);
        final LocalDate targetDate = new LocalDate(2014, 03, 20);
        final ContiguousIntervalConsumableUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, targetDate, false,
                                                                                                                                createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                                       BillingPeriod.MONTHLY,
                                                                                                                                                       Collections.emptyList(), catalogEffectiveDate)
                                                                                                                               );
        final List<UsageConsumableInArrearTierUnitAggregate> tierUnitDetails = new ArrayList<>();
        tierUnitDetails.addAll(intervalConsumableInArrear.computeToBeBilledConsumableInArrear(null, null, new DefaultRolledUpUnit("cell-phone-minutes", BigDecimal.valueOf(1000L)), Collections.emptyList(), false));
        tierUnitDetails.addAll(intervalConsumableInArrear.computeToBeBilledConsumableInArrear(null, null, new DefaultRolledUpUnit("Mbytes", BigDecimal.valueOf(30720L)), Collections.emptyList(), false));
        assertEquals(tierUnitDetails.size(), 2);

        final UsageConsumableInArrearAggregate details = new UsageConsumableInArrearAggregate(tierUnitDetails);

        assertEquals(details.getAmount().compareTo(new BigDecimal("18.5")), 0);
    }

    @Test(groups = "fast")
    public void testComputeMissingItemsAggregateModeAllTier_AGGREGATE() throws Exception {

        // Case 1
        List<RawUsageRecord> rawUsageRecords = new ArrayList<RawUsageRecord>();
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "FOO", BigDecimal.valueOf(5L), "tracking-1"));
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "BAR", BigDecimal.valueOf(99L), "tracking-1"));

        List<InvoiceItem> result = produceInvoiceItems(rawUsageRecords, TierBlockPolicy.ALL_TIERS, UsageDetailMode.AGGREGATE, Collections.emptyList());
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("203")), 0);

        UsageConsumableInArrearAggregate usageDetail = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<UsageConsumableInArrearAggregate>() {});
        List<UsageConsumableInArrearTierUnitAggregate> itemDetails = usageDetail.getTierDetails();

        // BAR: 99 * 2 = 198
        assertEquals(itemDetails.get(0).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(0).getTier(), 1);
        assertEquals(itemDetails.get(0).getAmount().compareTo(new BigDecimal("198")), 0);
        assertEquals(itemDetails.get(0).getQuantity(), BigDecimal.valueOf(99));
        assertEquals(itemDetails.get(0).getTierPrice().compareTo(new BigDecimal("2.0")), 0);
        // FOO: 5 * 1 = 5
        assertEquals(itemDetails.get(1).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(1).getTier(), 1);
        assertEquals(itemDetails.get(1).getAmount().compareTo(new BigDecimal("5")), 0);
        assertEquals(itemDetails.get(1).getQuantity(), BigDecimal.valueOf(5));
        assertEquals(itemDetails.get(1).getTierPrice().compareTo(BigDecimal.ONE), 0);

        // Case 2
        rawUsageRecords = new ArrayList<RawUsageRecord>();
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "FOO", BigDecimal.valueOf(5L), "tracking-2"));
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "BAR", BigDecimal.valueOf(101L), "tracking-2"));

        result = produceInvoiceItems(rawUsageRecords, TierBlockPolicy.ALL_TIERS, UsageDetailMode.AGGREGATE, Collections.emptyList());
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("225")), 0);

        usageDetail = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<UsageConsumableInArrearAggregate>() {});
        itemDetails = usageDetail.getTierDetails();

        // BAR: 100 * 2 = 200
        assertEquals(itemDetails.get(0).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(0).getTier(), 1);
        assertEquals(itemDetails.get(0).getAmount().compareTo(new BigDecimal("200.0")), 0);
        assertEquals(itemDetails.get(0).getQuantity(), new BigDecimal("100"));
        assertEquals(itemDetails.get(0).getTierPrice().compareTo(new BigDecimal("2.0")), 0);
        // BAR: 1 * 20 = 20
        assertEquals(itemDetails.get(1).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(1).getTier(), 2);
        assertEquals(itemDetails.get(1).getAmount().compareTo(new BigDecimal("20.0")), 0);
        assertEquals(itemDetails.get(1).getQuantity(), BigDecimal.valueOf(1));
        assertEquals(itemDetails.get(1).getTierPrice().compareTo(new BigDecimal("20.0")), 0);
        // FOO: 5 * 1 = 5
        assertEquals(itemDetails.get(2).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(2).getTier(), 1);
        assertEquals(itemDetails.get(2).getAmount().compareTo(new BigDecimal("5")), 0);
        assertEquals(itemDetails.get(2).getQuantity(), BigDecimal.valueOf(5));
        assertEquals(itemDetails.get(2).getTierPrice().compareTo(BigDecimal.ONE), 0);

        // Case 3
        rawUsageRecords = new ArrayList<RawUsageRecord>();
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "FOO", BigDecimal.valueOf(75L), "tracking-3"));
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "BAR", BigDecimal.valueOf(101L), "tracking-4"));

        result = produceInvoiceItems(rawUsageRecords, TierBlockPolicy.ALL_TIERS, UsageDetailMode.AGGREGATE, Collections.emptyList());
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("2230")), 0);

        usageDetail = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<UsageConsumableInArrearAggregate>() {});
        itemDetails = usageDetail.getTierDetails();
        // BAR: 100 * 2 = 200
        assertEquals(itemDetails.get(0).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(0).getTier(), 1);
        assertEquals(itemDetails.get(0).getAmount().compareTo(new BigDecimal("200.0")), 0);
        assertEquals(itemDetails.get(0).getQuantity(), BigDecimal.valueOf(100));
        assertEquals(itemDetails.get(0).getTierPrice().compareTo(new BigDecimal("2.0")), 0);
        // BAR: 1 * 20 = 20
        assertEquals(itemDetails.get(1).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(1).getTier(), 2);
        assertEquals(itemDetails.get(1).getAmount().compareTo(new BigDecimal("20.0")), 0);
        assertEquals(itemDetails.get(1).getQuantity(), BigDecimal.valueOf(1));
        assertEquals(itemDetails.get(1).getTierPrice().compareTo(new BigDecimal("20.0")), 0);
        // FOO: 10 * 1 = 10
        assertEquals(itemDetails.get(2).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(2).getTier(), 1);
        assertEquals(itemDetails.get(2).getAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(itemDetails.get(2).getQuantity(), BigDecimal.valueOf(10));
        assertEquals(itemDetails.get(2).getTierPrice().compareTo(BigDecimal.ONE), 0);
        // FOO: 50 * 10 = 500
        assertEquals(itemDetails.get(3).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(3).getTier(), 2);
        assertEquals(itemDetails.get(3).getAmount().compareTo(new BigDecimal("500")), 0);
        assertEquals(itemDetails.get(3).getQuantity(), BigDecimal.valueOf(50));
        assertEquals(itemDetails.get(3).getTierPrice().compareTo(BigDecimal.TEN), 0);
        // FOO: 15 * 100 = 1500
        assertEquals(itemDetails.get(4).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(4).getTier(), 3);
        assertEquals(itemDetails.get(4).getAmount().compareTo(new BigDecimal("1500")), 0);
        assertEquals(itemDetails.get(4).getQuantity(), BigDecimal.valueOf(15));
        assertEquals(itemDetails.get(4).getTierPrice().compareTo(new BigDecimal("100.0")), 0);
    }

    @Test(groups = "fast")
    public void testComputeMissingItemsDetailModeAllTier_DETAIL() throws Exception {

        // Case 1
        List<RawUsageRecord> rawUsageRecords = new ArrayList<RawUsageRecord>();
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "FOO", BigDecimal.valueOf(5L), "tracking-1"));
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "BAR", BigDecimal.valueOf(99L), "tracking-1"));

        List<InvoiceItem> result = produceInvoiceItems(rawUsageRecords, TierBlockPolicy.ALL_TIERS, UsageDetailMode.DETAIL, Collections.emptyList());
        assertEquals(result.size(), 2);
        // BAR: 99 * 2 = 198
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("198")), 0);
        assertEquals(result.get(0).getQuantity(), BigDecimal.valueOf(99));
        assertEquals(result.get(0).getRate().compareTo(new BigDecimal("2.0")), 0);
        // FOO: 5 * 1 = 5
        assertEquals(result.get(1).getAmount().compareTo(new BigDecimal("5")), 0);
        assertEquals(result.get(1).getQuantity(), BigDecimal.valueOf(5));
        assertEquals(result.get(1).getRate().compareTo(BigDecimal.ONE), 0);

        // Case 2
        rawUsageRecords = new ArrayList<RawUsageRecord>();
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "FOO", BigDecimal.valueOf(5L), "tracking-1"));
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "BAR", BigDecimal.valueOf(101L), "tracking-1"));

        result = produceInvoiceItems(rawUsageRecords, TierBlockPolicy.ALL_TIERS, UsageDetailMode.DETAIL, Collections.emptyList());
        assertEquals(result.size(), 3);
        // BAR: 100 * 2 = 200
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("200.0")), 0);
        assertEquals(result.get(0).getQuantity(), BigDecimal.valueOf(100));
        assertEquals(result.get(0).getRate().compareTo(new BigDecimal("2.0")), 0);
        // BAR: 1 * 20 = 20
        assertEquals(result.get(1).getAmount().compareTo(new BigDecimal("20.0")), 0);
        assertEquals(result.get(1).getQuantity(), BigDecimal.valueOf(1));
        assertEquals(result.get(1).getRate().compareTo(new BigDecimal("20.0")), 0);
        // FOO: 5 * 1 = 5
        assertEquals(result.get(2).getAmount().compareTo(new BigDecimal("5")), 0);
        assertEquals(result.get(2).getQuantity(), BigDecimal.valueOf(5));
        assertEquals(result.get(2).getRate().compareTo(BigDecimal.ONE), 0);

        // Case 3
        rawUsageRecords = new ArrayList<RawUsageRecord>();
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "FOO", BigDecimal.valueOf(75L), "tracking-2"));
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "BAR", BigDecimal.valueOf(101L), "tracking-2"));

        result = produceInvoiceItems(rawUsageRecords, TierBlockPolicy.ALL_TIERS, UsageDetailMode.DETAIL, Collections.emptyList());
        assertEquals(result.size(), 5);
        // BAR: 100 * 2 = 200
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("200.0")), 0);
        assertEquals(result.get(0).getQuantity(), BigDecimal.valueOf(100));
        assertEquals(result.get(0).getRate().compareTo(new BigDecimal("2.0")), 0);
        // BAR: 1 * 20 = 20
        assertEquals(result.get(1).getAmount().compareTo(new BigDecimal("20.0")), 0);
        assertEquals(result.get(1).getQuantity(), BigDecimal.valueOf(1));
        assertEquals(result.get(1).getRate().compareTo(new BigDecimal("20.0")), 0);
        // FOO: 10 * 1 = 10
        assertEquals(result.get(2).getAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(result.get(2).getQuantity(), BigDecimal.valueOf(10));
        assertEquals(result.get(2).getRate().compareTo(BigDecimal.ONE), 0);
        // FOO: 50 * 10 = 500
        assertEquals(result.get(3).getAmount().compareTo(new BigDecimal("500")), 0);
        assertEquals(result.get(3).getQuantity(), BigDecimal.valueOf(50));
        assertEquals(result.get(3).getRate().compareTo(BigDecimal.TEN), 0);
        // FOO: 15 * 100 = 1500
        assertEquals(result.get(4).getAmount().compareTo(new BigDecimal("1500")), 0);
        assertEquals(result.get(4).getQuantity(), BigDecimal.valueOf(15));
        assertEquals(result.get(4).getRate().compareTo(new BigDecimal("100.0")), 0);
    }

    @Test(groups = "fast")
    public void testComputeMissingItemsAggregateModeTopTier_AGGREGATE() throws Exception {

        // Case 1
        List<RawUsageRecord> rawUsageRecords = new ArrayList<RawUsageRecord>();
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "FOO", BigDecimal.valueOf(5L), "tracking-1"));
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "BAR", BigDecimal.valueOf(99L), "tracking-1"));

        List<InvoiceItem> result = produceInvoiceItems(rawUsageRecords, TierBlockPolicy.TOP_TIER, UsageDetailMode.AGGREGATE, Collections.emptyList());
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("203")), 0);

        UsageConsumableInArrearAggregate usageDetail = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<UsageConsumableInArrearAggregate>() {});
        List<UsageConsumableInArrearTierUnitAggregate> itemDetails = usageDetail.getTierDetails();
        // BAR: 99 * 2 = 198
        assertEquals(itemDetails.get(0).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(0).getTier(), 1);
        assertEquals(itemDetails.get(0).getAmount().compareTo(new BigDecimal("198")), 0);
        assertEquals(itemDetails.get(0).getQuantity(), BigDecimal.valueOf(99));
        assertEquals(itemDetails.get(0).getTierPrice().compareTo(new BigDecimal("2.0")), 0);
        // FOO: 5 * 1 = 5
        assertEquals(itemDetails.get(1).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(1).getTier(), 1);
        assertEquals(itemDetails.get(1).getAmount().compareTo(new BigDecimal("5")), 0);
        assertEquals(itemDetails.get(1).getQuantity(), BigDecimal.valueOf(5));
        assertEquals(itemDetails.get(1).getTierPrice().compareTo(BigDecimal.ONE), 0);

        // Case 2
        rawUsageRecords = new ArrayList<RawUsageRecord>();
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "FOO", BigDecimal.valueOf(5L), "tracking-2"));
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "BAR", BigDecimal.valueOf(101L), "tracking-2"));

        result = produceInvoiceItems(rawUsageRecords, TierBlockPolicy.TOP_TIER, UsageDetailMode.AGGREGATE, Collections.emptyList());
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("2025")), 0);

        usageDetail = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<UsageConsumableInArrearAggregate>() {});
        itemDetails = usageDetail.getTierDetails();

        // BAR: 101 * 20 = 2020
        assertEquals(itemDetails.get(0).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(0).getTier(), 2);
        assertEquals(itemDetails.get(0).getAmount().compareTo(new BigDecimal("2020.0")), 0);
        assertEquals(itemDetails.get(0).getQuantity(), BigDecimal.valueOf(101));
        assertEquals(itemDetails.get(0).getTierPrice().compareTo(new BigDecimal("20.0")), 0);
        // FOO: 5 * 1 = 5
        assertEquals(itemDetails.get(1).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(1).getTier(), 1);
        assertEquals(itemDetails.get(1).getAmount().compareTo(new BigDecimal("5")), 0);
        assertEquals(itemDetails.get(1).getQuantity(), BigDecimal.valueOf(5));
        assertEquals(itemDetails.get(1).getTierPrice().compareTo(BigDecimal.ONE), 0);

        // Case 3
        rawUsageRecords = new ArrayList<RawUsageRecord>();
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "FOO", BigDecimal.valueOf(76L), "tracking-3"));
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "BAR", BigDecimal.valueOf(101L), "tracking-3"));

        result = produceInvoiceItems(rawUsageRecords, TierBlockPolicy.TOP_TIER, UsageDetailMode.AGGREGATE, Collections.emptyList());
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("9620")), 0);

        usageDetail = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<UsageConsumableInArrearAggregate>() {});
        itemDetails = usageDetail.getTierDetails();
        // BAR: 101 * 20 = 2020
        assertEquals(itemDetails.get(0).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(0).getTier(), 2);
        assertEquals(itemDetails.get(0).getAmount().compareTo(new BigDecimal("2020.0")), 0);
        assertEquals(itemDetails.get(0).getQuantity(), BigDecimal.valueOf(101));
        assertEquals(itemDetails.get(0).getTierPrice().compareTo(new BigDecimal("20.0")), 0);
        // FOO: 76 * 100 = 7500
        assertEquals(itemDetails.get(1).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(1).getTier(), 3);
        assertEquals(itemDetails.get(1).getAmount().compareTo(new BigDecimal("7600")), 0);
        assertEquals(itemDetails.get(1).getQuantity(), BigDecimal.valueOf(76));
        assertEquals(itemDetails.get(1).getTierPrice().compareTo(new BigDecimal("100.0")), 0);
    }

    @Test(groups = "fast")
    public void testComputeMissingItemsDetailModeTopTier_DETAIL() throws Exception {

        // Case 1
        List<RawUsageRecord> rawUsageRecords = new ArrayList<RawUsageRecord>();
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "FOO", BigDecimal.valueOf(5L), "tracking-1"));
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "BAR", BigDecimal.valueOf(99L), "tracking-2"));

        List<InvoiceItem> result = produceInvoiceItems(rawUsageRecords, TierBlockPolicy.TOP_TIER, UsageDetailMode.DETAIL, Collections.emptyList());
        assertEquals(result.size(), 2);
        // BAR: 99 * 2 = 198
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("198")), 0);
        assertEquals(result.get(0).getQuantity(), BigDecimal.valueOf(99));
        assertEquals(result.get(0).getRate().compareTo(new BigDecimal("2.0")), 0);
        // FOO: 5 * 1 = 5
        assertEquals(result.get(1).getAmount().compareTo(new BigDecimal("5")), 0);
        assertEquals(result.get(1).getQuantity(), BigDecimal.valueOf(5));
        assertEquals(result.get(1).getRate().compareTo(BigDecimal.ONE), 0);

        // Case 2
        rawUsageRecords = new ArrayList<RawUsageRecord>();
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "FOO", BigDecimal.valueOf(5L), "tracking-3"));
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "BAR", BigDecimal.valueOf(101L), "tracking-4"));

        result = produceInvoiceItems(rawUsageRecords, TierBlockPolicy.TOP_TIER, UsageDetailMode.DETAIL, Collections.emptyList());
        assertEquals(result.size(), 2);
        // BAR: 101 * 20 = 2020
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("2020.0")), 0);
        assertEquals(result.get(0).getQuantity(), BigDecimal.valueOf(101));
        assertEquals(result.get(0).getRate().compareTo(new BigDecimal("20.0")), 0);
        // FOO: 5 * 1 = 5
        assertEquals(result.get(1).getAmount().compareTo(new BigDecimal("5")), 0);
        assertEquals(result.get(1).getQuantity(), BigDecimal.valueOf(5));
        assertEquals(result.get(1).getRate().compareTo(BigDecimal.ONE), 0);

        // Case 3
        rawUsageRecords = new ArrayList<RawUsageRecord>();
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "FOO", BigDecimal.valueOf(76L), "tracking-5"));
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "BAR", BigDecimal.valueOf(101L), "tracking-6"));

        result = produceInvoiceItems(rawUsageRecords, TierBlockPolicy.TOP_TIER, UsageDetailMode.DETAIL, Collections.emptyList());
        assertEquals(result.size(), 2);
        // BAR: 101 * 20 = 2020
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("2020.0")), 0);
        assertEquals(result.get(0).getQuantity(), BigDecimal.valueOf(101));
        assertEquals(result.get(0).getRate().compareTo(new BigDecimal("20.0")), 0);
        // FOO: 76 * 100 = 7500
        assertEquals(result.get(1).getAmount().compareTo(new BigDecimal("7600")), 0);
        assertEquals(result.get(1).getQuantity(), BigDecimal.valueOf(76));
        assertEquals(result.get(1).getRate().compareTo(new BigDecimal("100.0")), 0);
    }

    @Test(groups = "fast")
    public void testMultipleItemsAndTiersWithExistingItemsAllTiers_AGGREGATE() throws Exception {

        //
        // Let's assume we were already billed on the previous period
        //
        // FOO : 10 (tier 1) + 40 (tier 2) = 50
        final BigDecimal existingFooQuantity1 = BigDecimal.valueOf(10L);
        final BigDecimal existingFooQuantity2 = BigDecimal.valueOf(40L);
        final UsageConsumableInArrearTierUnitAggregate existingFooUsageTier1 = new UsageConsumableInArrearTierUnitAggregate(1, "FOO", BigDecimal.ONE, BigDecimal.ONE, existingFooQuantity1, new BigDecimal("10.00"));
        final UsageConsumableInArrearTierUnitAggregate existingFooUsageTier2 = new UsageConsumableInArrearTierUnitAggregate(2, "FOO", BigDecimal.TEN, BigDecimal.ONE, existingFooQuantity2, new BigDecimal("400.00"));
        // BAR : 10 (tier 1) + 40 (tier 2)
        final BigDecimal existingBarUsageQty1 = BigDecimal.valueOf(80L);
        final UsageConsumableInArrearTierUnitAggregate existingBarUsageTier1 = new UsageConsumableInArrearTierUnitAggregate(1, "BAR", new BigDecimal("2.00"), BigDecimal.ONE, existingBarUsageQty1, new BigDecimal("160.00"));

        final List<UsageConsumableInArrearTierUnitAggregate> existingUsage = List.of(existingFooUsageTier1, existingFooUsageTier2, existingBarUsageTier1);

        final UsageConsumableInArrearAggregate usageConsumableInArrearDetail = new UsageConsumableInArrearAggregate(existingUsage);

        final String existingUsageJson = objectMapper.writeValueAsString(usageConsumableInArrearDetail);

        //
        // Create usage data points (will include already billed + add new usage data)
        //
        List<RawUsageRecord> rawUsageRecords = new ArrayList<RawUsageRecord>();
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "FOO", BigDecimal.valueOf(50L /* already built */ + 20L), "tracking-1")); // tier 3
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "BAR", BigDecimal.valueOf(80L /* already built */ + 120L), "tracking-1")); // tier 2

        final List<InvoiceItem> existingItems = new ArrayList<InvoiceItem>();
        final InvoiceItem ii1 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usageName, null,
                                                     new LocalDate(2014, 03, 20), new LocalDate(2014, 04, 15), new BigDecimal("570.00"), null, currency, null, existingUsageJson);
        existingItems.add(ii1);

        List<InvoiceItem> result = produceInvoiceItems(rawUsageRecords, TierBlockPolicy.ALL_TIERS, UsageDetailMode.AGGREGATE, existingItems);
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("3140.00")), 0, String.format("%s != 3140.0", result.get(0).getAmount()));

        UsageConsumableInArrearAggregate usageDetail = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<UsageConsumableInArrearAggregate>() {});
        List<UsageConsumableInArrearTierUnitAggregate> itemDetails = usageDetail.getTierDetails();

        // We get same total than AGGREGATE : 3140

        // BAR item detail
        assertEquals(itemDetails.get(0).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(0).getTier(), 1);
        assertEquals(itemDetails.get(0).getTierBlockSize(), BigDecimal.ONE);
        assertEquals(itemDetails.get(0).getQuantity(), new BigDecimal("20"));
        assertEquals(itemDetails.get(0).getTierPrice().compareTo(new BigDecimal("2.00")), 0);
        assertEquals(itemDetails.get(0).getAmount().compareTo(new BigDecimal("40.00")), 0);

        assertEquals(itemDetails.get(1).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(1).getTier(), 2);
        assertEquals(itemDetails.get(1).getTierBlockSize(), BigDecimal.ONE);
        assertEquals(itemDetails.get(1).getQuantity(), new BigDecimal("100"));
        assertEquals(itemDetails.get(1).getTierPrice().compareTo(new BigDecimal("20.00")), 0);
        assertEquals(itemDetails.get(1).getAmount().compareTo(new BigDecimal("2000.00")), 0);

        // FOO item detail
        assertEquals(itemDetails.get(2).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(2).getTier(), 1);
        assertEquals(itemDetails.get(2).getTierBlockSize(), BigDecimal.ONE);
        assertEquals(itemDetails.get(2).getQuantity(), BigDecimal.ZERO);
        assertEquals(itemDetails.get(2).getTierPrice().compareTo(new BigDecimal("1.00")), 0);
        assertEquals(itemDetails.get(2).getAmount().compareTo(new BigDecimal("0.00")), 0);

        assertEquals(itemDetails.get(3).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(3).getTier(), 2);
        assertEquals(itemDetails.get(3).getTierBlockSize(), BigDecimal.ONE);
        assertEquals(itemDetails.get(3).getQuantity(), BigDecimal.TEN);
        assertEquals(itemDetails.get(3).getTierPrice().compareTo(new BigDecimal("10.00")), 0);
        assertEquals(itemDetails.get(3).getAmount().compareTo(new BigDecimal("100.00")), 0);

        assertEquals(itemDetails.get(4).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(4).getTier(), 3);
        assertEquals(itemDetails.get(4).getTierBlockSize(), BigDecimal.ONE);
        assertEquals(itemDetails.get(4).getQuantity(), BigDecimal.TEN);
        assertEquals(itemDetails.get(4).getTierPrice().compareTo(new BigDecimal("100.00")), 0);
        assertEquals(itemDetails.get(4).getAmount().compareTo(new BigDecimal("1000.00")), 0);
    }

    private UsageInvoiceItem newUsageInvoiceItem(final LocalDate startDate, final LocalDate endDate,
                                                 final BigDecimal amount, final BigDecimal rateTierPrice, final BigDecimal quantity,
                                                 final String usageInArrearDetail) {
        return new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usageName, null,
                                    startDate, endDate,
                                    amount,
                                    rateTierPrice, currency,
                                    quantity,
                                    usageInArrearDetail);
    }

    @Test(groups = "fast")
    public void testMultipleItemsAndTiersWithExistingItemsAllTiers_DETAIL() throws Exception {

        //
        // Create usage data points (will include already billed + add new usage data)
        //
        final List<RawUsageRecord> rawUsageRecords = new ArrayList<>();
        final BigDecimal rawUsageAmount1 = BigDecimal.valueOf(50L /* already built */ + 20L);
        final BigDecimal rawUsageAmount2 = BigDecimal.valueOf(80L /* already built */ + 120L);
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20).toDateTimeAtStartOfDay(), "FOO", rawUsageAmount1, "tracking-1")); // tier 3
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21).toDateTimeAtStartOfDay(), "BAR", rawUsageAmount2, "tracking-1")); // tier 2

        // FOO : 10 (tier 1) + 40 (tier 2) = 50
        final BigDecimal existingFooUsageAmount1 = BigDecimal.valueOf(10L);
        final BigDecimal existingFooUsageAmount2 = BigDecimal.valueOf(40L);
        final UsageConsumableInArrearTierUnitAggregate existingFooUsageTier1 = new UsageConsumableInArrearTierUnitAggregate(1, "FOO", BigDecimal.ONE, BigDecimal.ONE, existingFooUsageAmount1, new BigDecimal("10.00"));
        final String usageInArrearDetail1 = objectMapper.writeValueAsString(existingFooUsageTier1);

        final UsageConsumableInArrearTierUnitAggregate existingFooUsageTier2 = new UsageConsumableInArrearTierUnitAggregate(2, "FOO", BigDecimal.TEN, BigDecimal.ONE, existingFooUsageAmount2, new BigDecimal("400.00"));
        final String usageInArrearDetail2 = objectMapper.writeValueAsString(existingFooUsageTier2);

        // BAR : 10 (tier 1) + 40 (tier 2)
        final BigDecimal existingBarUsageAmount1 = BigDecimal.valueOf(80L);
        final UsageConsumableInArrearTierUnitAggregate existingBarUsageTier1 = new UsageConsumableInArrearTierUnitAggregate(1, "BAR", new BigDecimal("2.00"), BigDecimal.ONE, existingBarUsageAmount1, new BigDecimal("160.00"));
        final String usageInArrearDetail3 = objectMapper.writeValueAsString(existingBarUsageTier1);

        // Same as previous example bu instead of creating JSON we create one item per type/tier
        final List<InvoiceItem> existingItems = new ArrayList<>();

        final InvoiceItem i1 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usageName, null,
                                                    new LocalDate(2014, 03, 20), new LocalDate(2014, 04, 15), new BigDecimal("10.00") /* amount */, new BigDecimal("1.00") /* rate = tierPrice*/, currency, BigDecimal.valueOf(10) /* # units*/, usageInArrearDetail1);
        final InvoiceItem i2 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usageName, null,
                                                    new LocalDate(2014, 03, 20), new LocalDate(2014, 04, 15), new BigDecimal("400.00"), new BigDecimal("10.00"), currency, BigDecimal.valueOf(40), usageInArrearDetail2);
        final InvoiceItem i3 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usageName, null,
                                                    new LocalDate(2014, 03, 20), new LocalDate(2014, 04, 15), new BigDecimal("160.00"), new BigDecimal("2.00"), currency, BigDecimal.valueOf(80), usageInArrearDetail3);
        existingItems.addAll(List.of(i1, i2, i3));

        List<InvoiceItem> result = produceInvoiceItems(rawUsageRecords, TierBlockPolicy.ALL_TIERS, UsageDetailMode.DETAIL, existingItems);
        assertEquals(result.size(), 4);
        final UsageConsumableInArrearTierUnitAggregate resultUsageInArrearDetail0 = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<UsageConsumableInArrearTierUnitAggregate>() {});
        assertEquals(resultUsageInArrearDetail0.getTierUnit(), "BAR");
        assertEquals(resultUsageInArrearDetail0.getTier(), 1);
        assertEquals(resultUsageInArrearDetail0.getQuantity(), BigDecimal.valueOf(20));
        assertEquals(resultUsageInArrearDetail0.getTierPrice().compareTo(new BigDecimal("2.00")), 0);

        assertEquals(result.get(0).getRate().compareTo(new BigDecimal("2.00")), 0);
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("40.00")), 0);
        assertEquals(result.get(0).getQuantity(), BigDecimal.valueOf(20));

        final UsageConsumableInArrearTierUnitAggregate resultUsageInArrearDetail1 = objectMapper.readValue(result.get(1).getItemDetails(), new TypeReference<UsageConsumableInArrearTierUnitAggregate>() {});
        assertEquals(resultUsageInArrearDetail1.getTierUnit(), "BAR");
        assertEquals(resultUsageInArrearDetail1.getTier(), 2);
        assertEquals(resultUsageInArrearDetail1.getQuantity(), BigDecimal.valueOf(100));
        assertEquals(resultUsageInArrearDetail1.getTierPrice().compareTo(new BigDecimal("20.00")), 0);

        assertEquals(result.get(1).getRate().compareTo(new BigDecimal("20.00")), 0);
        assertEquals(result.get(1).getAmount().compareTo(new BigDecimal("2000.00")), 0);
        assertEquals(result.get(1).getQuantity(), BigDecimal.valueOf(100));

        final UsageConsumableInArrearTierUnitAggregate resultUsageInArrearDetail2 = objectMapper.readValue(result.get(2).getItemDetails(), new TypeReference<UsageConsumableInArrearTierUnitAggregate>() {});
        assertEquals(resultUsageInArrearDetail2.getTierUnit(), "FOO");
        assertEquals(resultUsageInArrearDetail2.getTier(), 2);
        assertEquals(resultUsageInArrearDetail2.getQuantity(), BigDecimal.valueOf(10));
        assertEquals(resultUsageInArrearDetail2.getTierPrice().compareTo(new BigDecimal("10.00")), 0);

        assertEquals(result.get(2).getRate().compareTo(new BigDecimal("10.00")), 0);
        assertEquals(result.get(2).getAmount().compareTo(new BigDecimal("100.00")), 0);
        assertEquals(result.get(2).getQuantity(), BigDecimal.valueOf(10));

        final UsageConsumableInArrearTierUnitAggregate resultUsageInArrearDetail3 = objectMapper.readValue(result.get(3).getItemDetails(), new TypeReference<UsageConsumableInArrearTierUnitAggregate>() {});
        assertEquals(resultUsageInArrearDetail3.getTierUnit(), "FOO");
        assertEquals(resultUsageInArrearDetail3.getTier(), 3);
        assertEquals(resultUsageInArrearDetail3.getQuantity(), BigDecimal.valueOf(10));
        assertEquals(resultUsageInArrearDetail3.getTierPrice().compareTo(new BigDecimal("100.00")), 0);

        assertEquals(result.get(3).getRate().compareTo(new BigDecimal("100.00")), 0);
        assertEquals(result.get(3).getAmount().compareTo(new BigDecimal("1000.00")), 0);
        assertEquals(result.get(3).getQuantity(), BigDecimal.valueOf(10));

    }

    @Test(groups = "fast")
    public void testMultipleItemsAndTiersWithExistingItemsTopTier() throws Exception {
        // TODO + code
    }

    @Test(groups = "fast")
    public void testGetRolledUpUsageOnlyUsageBeforeTransitionTime() throws Exception {
        final DefaultTieredBlock tieredBlock1 = createDefaultTieredBlock("unit", 100, 1000, BigDecimal.ONE);
        final DefaultTieredBlock tieredBlock2 = createDefaultTieredBlock("unit2", 10, 1000, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(tieredBlock1, tieredBlock2);

        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        final LocalDate t0 = new LocalDate(2015, 03, BCD);
        final BillingEvent eventT0 = createMockBillingEvent(t0.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);

        final LocalDate t1 = new LocalDate(2015, 04, BCD);
        final BillingEvent eventT1 = createMockBillingEvent(t1.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);

        final LocalDate targetDate = t1;

        // Prev t0
        final RawUsageRecord raw1 = new DefaultRawUsage(subscriptionId, new LocalDate(2015, 03, 01).toDateTimeAtStartOfDay(), "unit", BigDecimal.valueOf(12L), "tracking-1");

        final List<RawUsageRecord> rawUsageRecord = List.of(raw1);

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, targetDate, true, eventT0, eventT1);

        final List<RolledUpUsageWithMetadata> unsortedRolledUpUsage = intervalConsumableInArrear.getRolledUpUsage(rawUsageRecord).getUsage();
        assertEquals(unsortedRolledUpUsage.size(), 2);
        assertEquals(unsortedRolledUpUsage.get(0).getRolledUpUnits().size(), 1);
        assertEquals(unsortedRolledUpUsage.get(0).getRolledUpUnits().get(0).getAmount(), BigDecimal.ZERO);
        assertEquals(unsortedRolledUpUsage.get(1).getRolledUpUnits().size(), 1);
        assertEquals(unsortedRolledUpUsage.get(1).getRolledUpUnits().get(0).getAmount(), BigDecimal.ZERO);
    }

    @Test(groups = "fast")
    public void testGetRolledUpUsageUsageAtTransitionTimes() throws Exception {
        final DefaultTieredBlock tieredBlock1 = createDefaultTieredBlock("unit", 100, 1000, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(tieredBlock1);

        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        // 2015-03-15T18:10:17Z
        final DateTime t0 = new DateTime(2015, 03, BCD, 18, 10, 17, DateTimeZone.UTC);
        final BillingEvent eventT0 = createMockBillingEvent(t0, BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);
        Mockito.when(eventT0.getTransitionType()).thenReturn(SubscriptionBaseTransitionType.CREATE);

        // 2015-04-01T11:22:30Z
        final DateTime t1 = new DateTime(2015, 04, 01, 11, 22, 30, DateTimeZone.UTC);
        final BillingEvent eventT1 = createMockBillingEvent(t1, BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);
        Mockito.when(eventT1.getTransitionType()).thenReturn(SubscriptionBaseTransitionType.CANCEL);

        final LocalDate targetDate = internalCallContext.toLocalDate(t1);

        // At t0
        final RawUsageRecord raw1 = new DefaultRawUsage(subscriptionId, t0, "unit", BigDecimal.valueOf(12L), "tracking-1");

        // At t1
        final RawUsageRecord raw2 = new DefaultRawUsage(subscriptionId, t1, "unit", BigDecimal.valueOf(10L), "tracking-2");

        // Should be ignored
        final RawUsageRecord raw3 = new DefaultRawUsage(subscriptionId, new LocalDate(2015, 04, 02).toDateTimeAtStartOfDay(), "unit", BigDecimal.valueOf(100L), "tracking-3");

        final List<RawUsageRecord> rawUsageRecord = List.of(raw1, raw2, raw3);

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, targetDate, true, eventT0, eventT1);

        final List<RolledUpUsageWithMetadata> rolledUpUsage = intervalConsumableInArrear.getRolledUpUsage(rawUsageRecord).getUsage();
        assertEquals(rolledUpUsage.size(), 1);
        assertEquals(rolledUpUsage.get(0).getSubscriptionId(), subscriptionId);
        assertEquals(rolledUpUsage.get(0).getStart().compareTo(t0), 0);
        assertEquals(rolledUpUsage.get(0).getEnd().compareTo(t1), 0);
        assertEquals(rolledUpUsage.get(0).getRolledUpUnits().size(), 1);
        assertEquals(rolledUpUsage.get(0).getRolledUpUnits().get(0).getUnitType(), "unit");
        assertEquals(rolledUpUsage.get(0).getRolledUpUnits().get(0).getAmount(), new BigDecimal("22"));
    }

    @Test(groups = "fast")
    public void testGetRolledUpUsageUsageAtTransitionTimesCancelAtBCD() throws Exception {
        final DefaultTieredBlock tieredBlock1 = createDefaultTieredBlock("unit", 100, 1000, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(tieredBlock1);

        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        // 2015-03-15T18:10:17Z
        final DateTime t0 = new DateTime(2015, 03, BCD, 18, 10, 17, DateTimeZone.UTC);
        final BillingEvent eventT0 = createMockBillingEvent(t0, BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);
        Mockito.when(eventT0.getTransitionType()).thenReturn(SubscriptionBaseTransitionType.CREATE);

        // 2015-04-01T11:22:30Z
        final DateTime t1 = new DateTime(2015, 04, 01, 11, 22, 30, DateTimeZone.UTC);
        final BillingEvent eventT1 = createMockBillingEvent(t1, BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);
        Mockito.when(eventT1.getTransitionType()).thenReturn(SubscriptionBaseTransitionType.CANCEL);

        final LocalDate targetDate = internalCallContext.toLocalDate(t1);


        // At t0
        final RawUsageRecord raw1 = new DefaultRawUsage(subscriptionId, t0, "unit", BigDecimal.valueOf(12L), "tracking-1");

        // At t1
        final RawUsageRecord raw2 = new DefaultRawUsage(subscriptionId, t1, "unit", BigDecimal.valueOf(10L), "tracking-2");

        // Should be ignored
        final RawUsageRecord raw3 = new DefaultRawUsage(subscriptionId, new LocalDate(2015, 05, BCD).toDateTimeAtStartOfDay(), "unit", BigDecimal.valueOf(100L), "tracking-3");

        final List<RawUsageRecord> rawUsageRecord = List.of(raw1, raw2, raw3);

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, targetDate, true, eventT0, eventT1);

        // Verify transition times
        assertEquals(intervalConsumableInArrear.transitionTimes.size(), 2);
        assertEquals(intervalConsumableInArrear.transitionTimes.get(0).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CREATE);
        assertEquals(intervalConsumableInArrear.transitionTimes.get(1).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CANCEL);

        final List<RolledUpUsageWithMetadata> rolledUpUsage = intervalConsumableInArrear.getRolledUpUsage(rawUsageRecord).getUsage();
        assertEquals(rolledUpUsage.size(), 1);
        assertEquals(rolledUpUsage.get(0).getSubscriptionId(), subscriptionId);
        assertEquals(rolledUpUsage.get(0).getStart().compareTo(t0), 0);
        assertEquals(rolledUpUsage.get(0).getEnd().compareTo(t1), 0);
        assertEquals(rolledUpUsage.get(0).getRolledUpUnits().size(), 1);
        assertEquals(rolledUpUsage.get(0).getRolledUpUnits().get(0).getUnitType(), "unit");
        assertEquals(rolledUpUsage.get(0).getRolledUpUnits().get(0).getAmount(), new BigDecimal("22"));
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/1124")
    public void testComputeWithFractionOfCents() throws Exception {
        final LocalDate startDate = new LocalDate(2014, 03, 20);
        final LocalDate firstBCDDate = new LocalDate(2014, 04, 15);

        final List<RawUsageRecord> rawUsages = new ArrayList<RawUsageRecord>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, startDate.toDateTimeAtStartOfDay(), "unit", BigDecimal.valueOf(55L), "tracking-1"));

        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 1, 100, new BigDecimal("0.067"));
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, startDate.plusMonths(1), false, event1);

        final List<InvoiceItem> invoiceItems = new ArrayList<InvoiceItem>();
        final InvoiceItem ii1 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usage.getName(), null, startDate, firstBCDDate, new BigDecimal("3.69"), currency);
        invoiceItems.add(ii1);

        final UsageInArrearItemsAndNextNotificationDate usageResult = intervalConsumableInArrear.computeMissingItemsAndNextNotificationDate(rawUsages, EMPTY_EXISTING_TRACKING_IDS, invoiceItems, false);
        // Nothing to bill
        Assert.assertTrue(usageResult.getInvoiceItems().isEmpty());
        checkTrackingIds(rawUsages, usageResult.getTrackingIds());
    }

    private List<InvoiceItem> produceInvoiceItems(List<RawUsageRecord> rawUsageRecords, TierBlockPolicy tierBlockPolicy, UsageDetailMode usageDetailMode, final List<InvoiceItem> existingItems) throws Exception {
        final LocalDate startDate = new LocalDate(2014, 03, 20);
        final LocalDate firstBCDDate = new LocalDate(2014, 04, 15);
        final LocalDate endDate = new LocalDate(2014, 05, 15);

        final DefaultTieredBlock blockFooTier1 = createDefaultTieredBlock("FOO", 1, 10, BigDecimal.ONE);
        final DefaultTieredBlock blockBarTier1 = createDefaultTieredBlock("BAR", 1, 100, new BigDecimal("2"));
        final DefaultTier tier1 = createDefaultTierWithBlocks(blockFooTier1, blockBarTier1);

        final DefaultTieredBlock blockFooTier2 = createDefaultTieredBlock("FOO", 1, 50, BigDecimal.TEN);
        final DefaultTieredBlock blockBarTier2 = createDefaultTieredBlock("BAR", 1, 500, new BigDecimal("20"));
        final DefaultTier tier2 = createDefaultTierWithBlocks(blockFooTier2, blockBarTier2);

        final DefaultTieredBlock blockFooTier3 = createDefaultTieredBlock("FOO", 1, 75, new BigDecimal("100"));
        final DefaultTieredBlock blockBarTier3 = createDefaultTieredBlock("BAR", 1, 750, new BigDecimal("200"));
        final DefaultTier tier3 = createDefaultTierWithBlocks(blockFooTier3, blockBarTier3);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, tierBlockPolicy, tier1, tier2, tier3);

        final LocalDate targetDate = endDate;

        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);
        final BillingEvent event2 = createMockBillingEvent(endDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, targetDate, true, usageDetailMode, event1, event2);

        final UsageInArrearItemsAndNextNotificationDate usageResult = intervalConsumableInArrear.computeMissingItemsAndNextNotificationDate(rawUsageRecords, EMPTY_EXISTING_TRACKING_IDS, existingItems, false);

        checkTrackingIds(rawUsageRecords, usageResult.getTrackingIds());

        final List<InvoiceItem> rawResults = usageResult.getInvoiceItems();
        final List<InvoiceItem> result = rawResults.stream()
                .filter(invoiceItem -> invoiceItem.getAmount().compareTo(BigDecimal.ZERO) > 0)
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

    @Test(groups = "fast", description = "See https://github.com/killbill/killbill/issues/706")
    public void testWithRawUsageRecordStartDateAfterEndDate() throws Exception {

        final LocalDate startDate = new LocalDate(2014, 10, 16);
        final LocalDate endDate = startDate;
        final LocalDate targetDate = endDate;

        final LocalDate rawUsageRecordStartDate = new LocalDate(2015, 10, 16);

        final List<RawUsageRecord> rawUsageRecords = new ArrayList<RawUsageRecord>();
        rawUsageRecords.add(new DefaultRawUsage(subscriptionId, startDate.toDateTimeAtStartOfDay(), "unit", BigDecimal.valueOf(130L), "tracking-1"));

        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);
        final BillingEvent event2 = createMockBillingEvent(new LocalDate(2014, 10, 16).toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = new ContiguousIntervalConsumableUsageInArrear(usage, accountId, invoiceId, targetDate, rawUsageRecordStartDate.toDateTimeAtStartOfDay(), usageDetailMode, invoiceConfig, internalCallContext);

        intervalConsumableInArrear.addBillingEvent(event1);
        intervalConsumableInArrear.addAllSeenUnitTypesForBillingEvent(event1, Set.of("unit"));
        intervalConsumableInArrear.addBillingEvent(event2);
        intervalConsumableInArrear.addAllSeenUnitTypesForBillingEvent(event2, Set.of("unit"));

        final ContiguousIntervalUsageInArrear res = intervalConsumableInArrear.build(true);
        assertEquals(res.getTransitionTimes().size(), 0);
    }

    // One (CREATE) billing event, aligned on BCD (=15) with targetDt < 1 month
    @Test(groups = "fast")
    public void testTransitionsTimes1() throws Exception {

        final LocalDate startDate = new LocalDate(2021, 5, 15);
        final LocalDate targetDate = new LocalDate(2021, 6, 14);;

        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.emptyList(), catalogEffectiveDate);

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = new ContiguousIntervalConsumableUsageInArrear(usage, accountId, invoiceId, targetDate, startDate.toDateTimeAtStartOfDay(), usageDetailMode, invoiceConfig, internalCallContext);

        intervalConsumableInArrear.addBillingEvent(event1);
        intervalConsumableInArrear.addAllSeenUnitTypesForBillingEvent(event1, Set.of("unit"));

        final ContiguousIntervalUsageInArrear res = intervalConsumableInArrear.build(false);
        assertEquals(res.transitionTimes.size(), 1);
        // We won't bill anything because we don't an interval -- only one transition
        assertEquals(res.transitionTimes.get(0).getDate().toLocalDate().compareTo(startDate), 0);
        assertEquals(res.transitionTimes.get(0).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CREATE);
    }

    // One (CREATE) billing event, aligned on the 1st and BCD (=15) with targetDt < 1 month
    @Test(groups = "fast")
    public void testTransitionsTimes2() throws Exception {

        final LocalDate startDate = new LocalDate(2021, 5, 1);
        final LocalDate targetDate = new LocalDate(2021, 6, 14);;

        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.emptyList(), catalogEffectiveDate);

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = new ContiguousIntervalConsumableUsageInArrear(usage, accountId, invoiceId, targetDate, startDate.toDateTimeAtStartOfDay(), usageDetailMode, invoiceConfig, internalCallContext);

        intervalConsumableInArrear.addBillingEvent(event1);
        intervalConsumableInArrear.addAllSeenUnitTypesForBillingEvent(event1, Set.of("unit"));

        final ContiguousIntervalUsageInArrear res = intervalConsumableInArrear.build(false);
        assertEquals(res.transitionTimes.size(), 2);
        assertEquals(res.transitionTimes.get(0).getDate().toLocalDate().compareTo(startDate), 0);
        assertEquals(res.transitionTimes.get(0).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CREATE);
        assertEquals(res.transitionTimes.get(1).getDate().toLocalDate().compareTo(new LocalDate(2021, 5, 15)), 0);
        assertEquals(res.transitionTimes.get(1).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CREATE);
    }

    // One (CREATE) billing event, aligned on BCD (=15) with targetDt = 1 month
    @Test(groups = "fast")
    public void testTransitionsTimes3() throws Exception {

        final LocalDate startDate = new LocalDate(2021, 5, 15);
        final LocalDate targetDate = new LocalDate(2021, 6, 15);;

        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.emptyList(), catalogEffectiveDate);

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = new ContiguousIntervalConsumableUsageInArrear(usage, accountId, invoiceId, targetDate, startDate.toDateTimeAtStartOfDay(), usageDetailMode, invoiceConfig, internalCallContext);

        intervalConsumableInArrear.addBillingEvent(event1);
        intervalConsumableInArrear.addAllSeenUnitTypesForBillingEvent(event1, Set.of("unit"));

        final ContiguousIntervalUsageInArrear res = intervalConsumableInArrear.build(false);
        assertEquals(res.transitionTimes.size(), 2);
        assertEquals(res.transitionTimes.get(0).getDate().toLocalDate().compareTo(startDate), 0);
        assertEquals(res.transitionTimes.get(0).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CREATE);
        assertEquals(res.transitionTimes.get(1).getDate().toLocalDate().compareTo(targetDate), 0);
        assertEquals(res.transitionTimes.get(1).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CREATE);
    }

    // One (CREATE) billing event, aligned on the 1st and BCD (=15) with targetDt = 1 month after BCD date
    @Test(groups = "fast")
    public void testTransitionsTimes4() throws Exception {

        final LocalDate startDate = new LocalDate(2021, 5, 1);
        final LocalDate targetDate = new LocalDate(2021, 6, 15);;

        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.emptyList(), catalogEffectiveDate);

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = new ContiguousIntervalConsumableUsageInArrear(usage, accountId, invoiceId, targetDate, startDate.toDateTimeAtStartOfDay(), usageDetailMode, invoiceConfig,  internalCallContext);

        intervalConsumableInArrear.addBillingEvent(event1);
        intervalConsumableInArrear.addAllSeenUnitTypesForBillingEvent(event1, Set.of("unit"));

        final ContiguousIntervalUsageInArrear res = intervalConsumableInArrear.build(false);
        assertEquals(res.transitionTimes.size(), 3);
        assertEquals(res.transitionTimes.get(0).getDate().toLocalDate().compareTo(startDate), 0);
        assertEquals(res.transitionTimes.get(0).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CREATE);
        assertEquals(res.transitionTimes.get(1).getDate().toLocalDate().compareTo(new LocalDate(2021, 5, 15)), 0);
        assertEquals(res.transitionTimes.get(1).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CREATE);
        assertEquals(res.transitionTimes.get(2).getDate().toLocalDate().compareTo(targetDate), 0);
        assertEquals(res.transitionTimes.get(2).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CREATE);
    }

    // One (CREATE) + one (CANCEL) billing event, aligned on BCD (=15) with targetDt = 1 month
    @Test(groups = "fast")
    public void testTransitionsTimes5() throws Exception {

        final LocalDate startDate = new LocalDate(2021, 5, 15);
        final LocalDate targetDate = new LocalDate(2021, 6, 15);;
        final LocalDate endDate = new LocalDate(2021, 6, 15);;

        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.emptyList(), catalogEffectiveDate);
        final BillingEvent event2 = createMockBillingEvent(BCD, endDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.emptyList(), catalogEffectiveDate, SubscriptionBaseTransitionType.CANCEL);

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = new ContiguousIntervalConsumableUsageInArrear(usage, accountId, invoiceId, targetDate, startDate.toDateTimeAtStartOfDay(), usageDetailMode, invoiceConfig, internalCallContext);

        intervalConsumableInArrear.addBillingEvent(event1);
        intervalConsumableInArrear.addBillingEvent(event2);
        intervalConsumableInArrear.addAllSeenUnitTypesForBillingEvent(event1, Set.of("unit"));

        final ContiguousIntervalUsageInArrear res = intervalConsumableInArrear.build(true);
        assertEquals(res.transitionTimes.size(), 2);
        assertEquals(res.transitionTimes.get(0).getDate().toLocalDate().compareTo(startDate), 0);
        assertEquals(res.transitionTimes.get(0).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CREATE);
        assertEquals(res.transitionTimes.get(1).getDate().toLocalDate().compareTo(targetDate), 0);
        // Verify this points to the correct CANCEL event
        assertEquals(res.transitionTimes.get(1).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CANCEL);
    }

    // One (CREATE) + one (CHANGE) not on boundary + one (CANCEL) billing event, aligned on BCD (=15) with targetDt = 1 month
    // The CHANGE is not part of the transitions which is expected since it has the same usage section so part of the same interval
    @Test(groups = "fast")
    public void testTransitionsTimes6() throws Exception {

        final LocalDate startDate = new LocalDate(2021, 5, 15);
        final LocalDate targetDate = new LocalDate(2021, 6, 15);;
        final LocalDate changeDate = new LocalDate(2021, 5, 28);;
        final LocalDate endDate = new LocalDate(2021, 6, 15);;

        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.emptyList(), catalogEffectiveDate);
        final BillingEvent event2 = createMockBillingEvent(BCD, changeDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.emptyList(), catalogEffectiveDate, SubscriptionBaseTransitionType.CHANGE);
        final BillingEvent event3 = createMockBillingEvent(BCD, endDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.emptyList(), catalogEffectiveDate, SubscriptionBaseTransitionType.CANCEL);

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = new ContiguousIntervalConsumableUsageInArrear(usage, accountId, invoiceId, targetDate, startDate.toDateTimeAtStartOfDay(), usageDetailMode, invoiceConfig, internalCallContext);

        intervalConsumableInArrear.addBillingEvent(event1);
        intervalConsumableInArrear.addBillingEvent(event2);
        intervalConsumableInArrear.addBillingEvent(event3);
        intervalConsumableInArrear.addAllSeenUnitTypesForBillingEvent(event1, Set.of("unit"));

        final ContiguousIntervalUsageInArrear res = intervalConsumableInArrear.build(true);
        assertEquals(res.transitionTimes.size(), 2);
        assertEquals(res.transitionTimes.get(0).getDate().toLocalDate().compareTo(startDate), 0);
        assertEquals(res.transitionTimes.get(0).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CREATE);
        assertEquals(res.transitionTimes.get(1).getDate().toLocalDate().compareTo(endDate), 0);
        assertEquals(res.transitionTimes.get(1).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CANCEL);
    }

    // One (CREATE) + one (CHANGE) not on boundary + one (CANCEL) billing event, aligned on BCD (=15) with targetDt = 2 month, same as CANCEL
    // The second transition is correctly pointing the CHANGE billing event
    @Test(groups = "fast")
    public void testTransitionsTimes7() throws Exception {

        final LocalDate startDate = new LocalDate(2021, 5, 15);
        final LocalDate changeDate = new LocalDate(2021, 5, 28);;
        final LocalDate targetDate = new LocalDate(2021, 7, 15);;
        final LocalDate endDate = targetDate;

        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.emptyList(), catalogEffectiveDate);
        final BillingEvent event2 = createMockBillingEvent(BCD, changeDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.emptyList(), catalogEffectiveDate, SubscriptionBaseTransitionType.CHANGE);
        final BillingEvent event3 = createMockBillingEvent(BCD, endDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.emptyList(), catalogEffectiveDate, SubscriptionBaseTransitionType.CANCEL);

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = new ContiguousIntervalConsumableUsageInArrear(usage, accountId, invoiceId, targetDate, startDate.toDateTimeAtStartOfDay(), usageDetailMode, invoiceConfig, internalCallContext);

        intervalConsumableInArrear.addBillingEvent(event1);
        intervalConsumableInArrear.addBillingEvent(event2);
        intervalConsumableInArrear.addBillingEvent(event3);
        intervalConsumableInArrear.addAllSeenUnitTypesForBillingEvent(event1, Set.of("unit"));

        final ContiguousIntervalUsageInArrear res = intervalConsumableInArrear.build(true);
        assertEquals(res.transitionTimes.size(), 3);
        assertEquals(res.transitionTimes.get(0).getDate().toLocalDate().compareTo(startDate), 0);
        assertEquals(res.transitionTimes.get(0).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CREATE);
        assertEquals(res.transitionTimes.get(1).getDate().toLocalDate().compareTo(new LocalDate(2021, 6, 15)), 0);
        assertEquals(res.transitionTimes.get(1).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CHANGE);
        assertEquals(res.transitionTimes.get(2).getDate().toLocalDate().compareTo(endDate), 0);
        assertEquals(res.transitionTimes.get(2).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CANCEL);
    }

    // One (CREATE) + one (CHANGE) not on boundary + one (CANCEL) billing event, aligned on BCD (=15) with targetDt = 3 month
    // Note we use 'build(false)' to indicate this is an open interval whose endDate is the targetDate
    @Test(groups = "fast")
    public void testTransitionsTimes8() throws Exception {

        final LocalDate startDate = new LocalDate(2021, 5, 15);
        final LocalDate changeDate = new LocalDate(2021, 5, 28);;
        final LocalDate endDate = new LocalDate(2021, 7, 15);
        final LocalDate targetDate = new LocalDate(2021, 8, 15);

        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.emptyList(), catalogEffectiveDate);
        final BillingEvent event2 = createMockBillingEvent(BCD, changeDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.emptyList(), catalogEffectiveDate, SubscriptionBaseTransitionType.CHANGE);
        final BillingEvent event3 = createMockBillingEvent(BCD, endDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.emptyList(), catalogEffectiveDate, SubscriptionBaseTransitionType.CANCEL);

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = new ContiguousIntervalConsumableUsageInArrear(usage, accountId, invoiceId, targetDate, startDate.toDateTimeAtStartOfDay(), usageDetailMode, invoiceConfig, internalCallContext);

        intervalConsumableInArrear.addBillingEvent(event1);
        intervalConsumableInArrear.addBillingEvent(event2);
        intervalConsumableInArrear.addBillingEvent(event3);
        intervalConsumableInArrear.addAllSeenUnitTypesForBillingEvent(event1, Set.of("unit"));

        final ContiguousIntervalUsageInArrear res = intervalConsumableInArrear.build(false);
        assertEquals(res.transitionTimes.size(), 4);
        assertEquals(res.transitionTimes.get(0).getDate().toLocalDate().compareTo(startDate), 0);
        assertEquals(res.transitionTimes.get(0).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CREATE);
        assertEquals(res.transitionTimes.get(1).getDate().toLocalDate().compareTo(new LocalDate(2021, 6, 15)), 0);
        assertEquals(res.transitionTimes.get(1).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CHANGE);
        assertEquals(res.transitionTimes.get(2).getDate().toLocalDate().compareTo(new LocalDate(2021, 7, 15)), 0);
        assertEquals(res.transitionTimes.get(2).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CANCEL);
        // In practise this does not happen because we don't end up with 2 CANCEL for same subscriptions but usage code (mostly) disregard the
        // billing event type, so this transition is expected.
        assertEquals(res.transitionTimes.get(3).getDate().toLocalDate().compareTo(new LocalDate(2021, 8, 15)), 0);
        assertEquals(res.transitionTimes.get(3).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CANCEL);
    }

    // One (CREATE) + one (CANCEL) billing event, aligned on start of subscription with targetDt = 1 month
    @Test(groups = "fast")
    public void testTransitionsTimes9() throws Exception {
        final LocalDate startDate = new LocalDate(2021, 5, 15);
        final LocalDate targetDate = new LocalDate(2021, 6, 15);
        final LocalDate endDate = startDate;

        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate);
        final BillingEvent event2 = createMockBillingEvent(BCD, endDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList(), catalogEffectiveDate, SubscriptionBaseTransitionType.CANCEL);

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = new ContiguousIntervalConsumableUsageInArrear(usage, accountId, invoiceId, targetDate, startDate.toDateTimeAtStartOfDay(), usageDetailMode, invoiceConfig, internalCallContext);

        intervalConsumableInArrear.addBillingEvent(event1);
        intervalConsumableInArrear.addBillingEvent(event2);
        intervalConsumableInArrear.addAllSeenUnitTypesForBillingEvent(event1, Set.of("unit"));

        final ContiguousIntervalUsageInArrear res = intervalConsumableInArrear.build(true);
        assertEquals(res.transitionTimes.size(), 2);
        assertEquals(res.transitionTimes.get(0).getDate().toLocalDate().compareTo(startDate), 0);
        assertEquals(res.transitionTimes.get(0).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CREATE);
        assertEquals(res.transitionTimes.get(1).getDate().toLocalDate().compareTo(startDate), 0);
        assertEquals(res.transitionTimes.get(1).getTargetBillingEvent().getTransitionType(), SubscriptionBaseTransitionType.CANCEL);
    }
}
