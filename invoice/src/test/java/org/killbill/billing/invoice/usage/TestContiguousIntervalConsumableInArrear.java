/*
 * Copyright 2010-2013 Ning, Inc.
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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.DefaultTier;
import org.killbill.billing.catalog.DefaultTieredBlock;
import org.killbill.billing.catalog.DefaultUsage;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.TierBlockPolicy;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.UsageInvoiceItem;
import org.killbill.billing.invoice.usage.ContiguousIntervalUsageInArrear.UsageInArrearItemsAndNextNotificationDate;
import org.killbill.billing.invoice.usage.details.UsageConsumableInArrearDetail;
import org.killbill.billing.invoice.usage.details.UsageConsumableInArrearTierUnitDetail;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.usage.RawUsage;
import org.killbill.billing.usage.api.RolledUpUsage;
import org.killbill.billing.usage.api.svcs.DefaultRawUsage;
import org.killbill.billing.util.config.definition.InvoiceConfig.UsageDetailMode;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestContiguousIntervalConsumableInArrear extends TestUsageInArrearBase {

    // Only works if the RolledUpUsage have at least one
    private static final Ordering<RolledUpUsage> TEST_ROLLED_UP_FIRST_USAGE_ORDERING = Ordering.natural()
                                                                                               .onResultOf(new Function<RolledUpUsage, Comparable>() {
                                                                                                   @Override
                                                                                                   public Comparable apply(final RolledUpUsage ru) {
                                                                                                       return ru.getRolledUpUnits().get(0).getUnitType();
                                                                                                   }
                                                                                               });

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

        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 1000, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

        final LocalDate targetDate = startDate.plusDays(1);
        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, ImmutableList.<RawUsage>of(), targetDate, false,
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

        final BigDecimal result = intervalConsumableInArrear.computeBilledUsage(intervalConsumableInArrear.getBilledItems(startDate, endDate, existingUsage));
        assertEquals(result.compareTo(BigDecimal.TEN.add(BigDecimal.TEN)), 0);
    }


    @Test(groups = "fast")
    public void testComputeBilledUsageSizeOneWith_ALL_TIERS() throws CatalogApiException {

        final DefaultTieredBlock block1 = createDefaultTieredBlock("unit", 1, 10, new BigDecimal("1.5"));
        final DefaultTier tier1 = createDefaultTierWithBlocks(block1);

        final DefaultTieredBlock block2 = createDefaultTieredBlock("unit", 1, 100, new BigDecimal("1.0"));
        final DefaultTier tier2 = createDefaultTierWithBlocks(block2);


        final DefaultTieredBlock block3 = createDefaultTieredBlock("unit", 1, 1000, new BigDecimal("0.5"));
        final DefaultTier tier3 = createDefaultTierWithBlocks(block3);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier1, tier2, tier3);

        final LocalDate targetDate = new LocalDate(2014, 03, 20);

        final ContiguousIntervalConsumableUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, ImmutableList.<RawUsage>of(), targetDate, false,
                                                                                                                      createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                             BillingPeriod.MONTHLY,
                                                                                                                                             Collections.<Usage>emptyList())
                                                                                                                     );

        List<UsageConsumableInArrearTierUnitDetail> result = intervalConsumableInArrear.computeToBeBilledConsumableInArrear(new DefaultRolledUpUnit("unit", 111L), ImmutableList.<UsageConsumableInArrearTierUnitDetail>of(), true);
        assertEquals(result.size(), 3);
        // 111 = 10 (tier1) + 100 (tier2) + 1 (tier3) => 10 * 1.5 + 100 * 1 + 1 * 0.5 = 115.5
        assertEquals(result.get(0).getAmount(), new BigDecimal("15.0"));
        assertEquals(result.get(1).getAmount(), new BigDecimal("100.0"));
        assertEquals(result.get(2).getAmount(), new BigDecimal("0.5"));
    }



    @Test(groups = "fast")
    public void testComputeBilledUsageWith_ALL_TIERS() throws CatalogApiException {

        final DefaultTieredBlock block1 = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier1 = createDefaultTierWithBlocks(block1);

        final DefaultTieredBlock block2 = createDefaultTieredBlock("unit", 1000, 100, BigDecimal.ONE);
        final DefaultTier tier2 = createDefaultTierWithBlocks(block2);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier1, tier2);

        final LocalDate targetDate = new LocalDate(2014, 03, 20);

        final ContiguousIntervalConsumableUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, ImmutableList.<RawUsage>of(), targetDate, false,
                                                                                                                      createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                             BillingPeriod.MONTHLY,
                                                                                                                                             Collections.<Usage>emptyList())
                                                                                                                     );

        List<UsageConsumableInArrearTierUnitDetail> result = intervalConsumableInArrear.computeToBeBilledConsumableInArrear(new DefaultRolledUpUnit("unit", 5325L), ImmutableList.<UsageConsumableInArrearTierUnitDetail>of(), true);
        assertEquals(result.size(), 2);

        // 5000 = 1000 (tier1) + 4325 (tier2) => 10 + 5 = 15
        assertEquals(result.get(0).getAmount(), new BigDecimal("10"));
        assertEquals(result.get(1).getAmount(), new BigDecimal("5"));
    }



    @Test(groups = "fast")
    public void testComputeBilledUsageWith_TOP_TIER() throws CatalogApiException {

        final DefaultTieredBlock block1 = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier1 = createDefaultTierWithBlocks(block1);

        final DefaultTieredBlock block2 = createDefaultTieredBlock("unit", 1000, 100, BigDecimal.ONE);
        final DefaultTier tier2 = createDefaultTierWithBlocks(block2);

        final DefaultTieredBlock block3 = createDefaultTieredBlock("unit", 1000, 100, new BigDecimal("0.5"));
        final DefaultTier tier3 = createDefaultTierWithBlocks(block3);

        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.TOP_TIER, tier1, tier2, tier3);

        final LocalDate targetDate = new LocalDate(2014, 03, 20);

        final ContiguousIntervalConsumableUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, ImmutableList.<RawUsage>of(), targetDate, false,
                                                                                                                      createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                             BillingPeriod.MONTHLY,
                                                                                                                                             Collections.<Usage>emptyList())
                                                                                                                     );
        //
        // In this model unit amount is first used to figure out which tier we are in, and then we price all unit at that 'target' tier
        //
        List<UsageConsumableInArrearTierUnitDetail> inputTier1 = intervalConsumableInArrear.computeToBeBilledConsumableInArrear(new DefaultRolledUpUnit("unit", 1000L), ImmutableList.<UsageConsumableInArrearTierUnitDetail>of(), true);
        assertEquals(inputTier1.size(), 1);
        // 1000 units => (tier1) : 1000 / 100 + 1000 % 100 = 10
        assertEquals(inputTier1.get(0).getAmount(), new BigDecimal("10"));

        List<UsageConsumableInArrearTierUnitDetail> inputTier2 = intervalConsumableInArrear.computeToBeBilledConsumableInArrear(new DefaultRolledUpUnit("unit", 101000L), ImmutableList.<UsageConsumableInArrearTierUnitDetail>of(), true);
        assertEquals(inputTier2.size(), 1);
        // 101000 units => (tier2) :  101000 / 1000 + 101000 % 1000 = 101 + 0 = 101
        assertEquals(inputTier2.get(0).getAmount(), new BigDecimal("101"));

        List<UsageConsumableInArrearTierUnitDetail> inputTier3 = intervalConsumableInArrear.computeToBeBilledConsumableInArrear(new DefaultRolledUpUnit("unit", 101001L), ImmutableList.<UsageConsumableInArrearTierUnitDetail>of(), true);
        assertEquals(inputTier3.size(), 1);
        // 101001 units => (tier3) : 101001 / 1000 + 101001 % 1000 = 101 + 1 = 102 units => $51
        assertEquals(inputTier3.get(0).getAmount(), new BigDecimal("51.0"));

        // If we pass the maximum of the last tier, we price all units at the last tier
        List<UsageConsumableInArrearTierUnitDetail> inputLastTier = intervalConsumableInArrear.computeToBeBilledConsumableInArrear(new DefaultRolledUpUnit("unit", 300000L), ImmutableList.<UsageConsumableInArrearTierUnitDetail>of(), true);
        assertEquals(inputLastTier.size(), 1);
        // 300000 units => (tier3) : 300000 / 1000 + 300000 % 1000 = 300 units => $150
        assertEquals(inputLastTier.get(0).getAmount(), new BigDecimal("150.0"));
    }


    @Test(groups = "fast")
    public void testComputeBilledUsageSizeOneWith_TOP_TIER() throws CatalogApiException {

        final DefaultTieredBlock block1 = createDefaultTieredBlock("unit", 1, 10, new BigDecimal("1.5"));
        final DefaultTier tier1 = createDefaultTierWithBlocks(block1);

        final DefaultTieredBlock block2 = createDefaultTieredBlock("unit", 1, 100, new BigDecimal("1.0"));
        final DefaultTier tier2 = createDefaultTierWithBlocks(block2);


        final DefaultTieredBlock block3 = createDefaultTieredBlock("unit", 1, 1000, new BigDecimal("0.5"));
        final DefaultTier tier3 = createDefaultTierWithBlocks(block3);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.TOP_TIER, tier1, tier2, tier3);

        final LocalDate targetDate = new LocalDate(2014, 03, 20);

        final ContiguousIntervalConsumableUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, ImmutableList.<RawUsage>of(), targetDate, false,
                                                                                                                      createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                             BillingPeriod.MONTHLY,
                                                                                                                                             Collections.<Usage>emptyList())
                                                                                                                     );

        List<UsageConsumableInArrearTierUnitDetail> result = intervalConsumableInArrear.computeToBeBilledConsumableInArrear(new DefaultRolledUpUnit("unit", 111L), ImmutableList.<UsageConsumableInArrearTierUnitDetail>of(), true);
        assertEquals(result.size(), 1);

        // 111 = 111 * 0.5 =
        assertEquals(result.get(0).getAmount(), new BigDecimal("55.5"));
    }




    @Test(groups = "fast")
    public void testComputeMissingItems() throws CatalogApiException, IOException, InvoiceApiException {

        final LocalDate startDate = new LocalDate(2014, 03, 20);
        final LocalDate firstBCDDate = new LocalDate(2014, 04, 15);
        final LocalDate endDate = new LocalDate(2014, 05, 15);

        // 2 items for startDate - firstBCDDate
        final List<RawUsage> rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "unit", 130L));
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "unit", 271L));
        // 1 items for firstBCDDate - endDate
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 04, 15), "unit", 199L));

        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);

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

        // Invoiced for 1 BTC and used 130 + 271 = 401 => 5 blocks => 5 BTC so remaining piece should be 4 BTC
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("4.0")), 0, String.format("%s != 4.0", result.get(0).getAmount()));
        assertEquals(result.get(0).getCurrency(), Currency.BTC);
        assertEquals(result.get(0).getAccountId(), accountId);
        assertEquals(result.get(0).getBundleId(), bundleId);
        assertEquals(result.get(0).getSubscriptionId(), subscriptionId);
        assertEquals(result.get(0).getPlanName(), planName);
        assertEquals(result.get(0).getPhaseName(), phaseName);
        assertEquals(result.get(0).getUsageName(), usage.getName());
        assertTrue(result.get(0).getStartDate().compareTo(startDate) == 0);
        assertTrue(result.get(0).getEndDate().compareTo(firstBCDDate) == 0);

        assertNotNull(result.get(0).getItemDetails());
        UsageConsumableInArrearDetail usageDetail = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<UsageConsumableInArrearDetail>() {});
        List<UsageConsumableInArrearTierUnitDetail> itemDetails = usageDetail.getTierDetails();
        assertEquals(itemDetails.size(), 1);
        // Because we did not have the details before, the new details don't take into account the
        assertEquals(itemDetails.get(0).getAmount().compareTo(new BigDecimal("5.0")), 0);

        // Invoiced for 1 BTC and used 199  => 2 blocks => 2 BTC so remaining piece should be 1 BTC
        assertEquals(result.get(1).getAmount().compareTo(new BigDecimal("1.0")), 0, String.format("%s != 1.0", result.get(0).getAmount()));
        assertEquals(result.get(1).getCurrency(), Currency.BTC);
        assertEquals(result.get(1).getAccountId(), accountId);
        assertEquals(result.get(1).getBundleId(), bundleId);
        assertEquals(result.get(1).getSubscriptionId(), subscriptionId);
        assertEquals(result.get(1).getPlanName(), planName);
        assertEquals(result.get(1).getPhaseName(), phaseName);
        assertEquals(result.get(1).getUsageName(), usage.getName());
        assertTrue(result.get(1).getStartDate().compareTo(firstBCDDate) == 0);
        assertTrue(result.get(1).getEndDate().compareTo(endDate) == 0);
        assertNotNull(result.get(1).getItemDetails());

        usageDetail = objectMapper.readValue(result.get(1).getItemDetails(), new TypeReference<UsageConsumableInArrearDetail>() {});
        itemDetails = usageDetail.getTierDetails();
        assertEquals(itemDetails.size(), 1);
        assertEquals(itemDetails.get(0).getAmount().compareTo(new BigDecimal("2.0")), 0);

    }

    @Test(groups = "fast")
    public void testGetRolledUpUsage() {

        final DefaultTieredBlock tieredBlock1 = createDefaultTieredBlock("unit", 100, 1000, BigDecimal.ONE);
        final DefaultTieredBlock tieredBlock2 = createDefaultTieredBlock("unit2", 10, 1000, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(tieredBlock1, tieredBlock2);


        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);


        final LocalDate t0 = new LocalDate(2015, 03, BCD);
        final BillingEvent eventT0 = createMockBillingEvent(t0.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList());

        final LocalDate t1 = new LocalDate(2015, 04, BCD);
        final BillingEvent eventT1 = createMockBillingEvent(t1.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList());

        final LocalDate t2 = new LocalDate(2015, 05, BCD);
        final BillingEvent eventT2 = createMockBillingEvent(t2.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList());

        final LocalDate t3 = new LocalDate(2015, 06, BCD);
        final BillingEvent eventT3 = createMockBillingEvent(t3.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList());

        final LocalDate targetDate = t3;


        // Prev t0
        final RawUsage raw1 = new DefaultRawUsage(subscriptionId, new LocalDate(2015, 03, 01), "unit", 12L);

        // t0 - t1
        final RawUsage raw2 = new DefaultRawUsage(subscriptionId, new LocalDate(2015, 03, 15), "unit", 6L);
        final RawUsage raw3 = new DefaultRawUsage(subscriptionId, new LocalDate(2015, 03, 25), "unit", 4L);

        // t1 - t2 nothing

        // t2 - t3
        final RawUsage raw4 = new DefaultRawUsage(subscriptionId, new LocalDate(2015, 05, 15), "unit", 13L);
        final RawUsage oraw1 = new DefaultRawUsage(subscriptionId, new LocalDate(2015, 05, 21), "unit2", 21L);
        final RawUsage raw5 = new DefaultRawUsage(subscriptionId, new LocalDate(2015, 05, 31), "unit", 7L);

        // after t3
        final RawUsage raw6 = new DefaultRawUsage(subscriptionId, new LocalDate(2015, 06, 15), "unit", 100L);

        final List<RawUsage> rawUsage = ImmutableList.of(raw1, raw2, raw3, raw4, oraw1, raw5, raw6);

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, rawUsage, targetDate, true, eventT0, eventT1, eventT2, eventT3);


        final List<RolledUpUsage> unsortedRolledUpUsage =  intervalConsumableInArrear.getRolledUpUsage();
        Assert.assertEquals(unsortedRolledUpUsage.size(), 2);

        final List<RolledUpUsage> rolledUpUsage = TEST_ROLLED_UP_FIRST_USAGE_ORDERING.sortedCopy(unsortedRolledUpUsage);

        Assert.assertEquals(rolledUpUsage.get(0).getStart().compareTo(t0), 0);
        Assert.assertEquals(rolledUpUsage.get(0).getEnd().compareTo(t1), 0);
        Assert.assertEquals(rolledUpUsage.get(0).getRolledUpUnits().size(),1);
        Assert.assertEquals(rolledUpUsage.get(0).getRolledUpUnits().get(0).getUnitType(), "unit");
        Assert.assertEquals(rolledUpUsage.get(0).getRolledUpUnits().get(0).getAmount(), new Long(10L));

        Assert.assertEquals(rolledUpUsage.get(1).getStart().compareTo(t2), 0);
        Assert.assertEquals(rolledUpUsage.get(1).getEnd().compareTo(t3), 0);
        Assert.assertEquals(rolledUpUsage.get(1).getRolledUpUnits().size(),2);
        Assert.assertEquals(rolledUpUsage.get(1).getRolledUpUnits().get(0).getUnitType(), "unit");
        Assert.assertEquals(rolledUpUsage.get(1).getRolledUpUnits().get(0).getAmount(), new Long(20L));
        Assert.assertEquals(rolledUpUsage.get(1).getRolledUpUnits().get(1).getUnitType(), "unit2");
        Assert.assertEquals(rolledUpUsage.get(1).getRolledUpUnits().get(1).getAmount(), new Long(21L));
    }



    @Test(groups = "fast", description="See https://github.com/killbill/killbill/issues/706")
    public void testWithRawUsageStartDateAfterEndDate() throws CatalogApiException {

        final LocalDate startDate = new LocalDate(2014, 10, 16);
        final LocalDate endDate = startDate;
        final LocalDate targetDate = endDate;

        final LocalDate rawUsageStartDate = new LocalDate(2015, 10, 16);

        final List<RawUsage> rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, startDate, "unit", 130L));

        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);


        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),BillingPeriod.MONTHLY, Collections.<Usage>emptyList());
        final BillingEvent event2 = createMockBillingEvent(new LocalDate(2014, 10, 16).toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList());



        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = usage.getUsageType() == UsageType.CAPACITY ?
                                                                           new ContiguousIntervalCapacityUsageInArrear(usage, accountId, invoiceId, rawUsages, targetDate, rawUsageStartDate, usageDetailMode, internalCallContext) :
                                                                           new ContiguousIntervalConsumableUsageInArrear(usage, accountId, invoiceId, rawUsages, targetDate, rawUsageStartDate, usageDetailMode, internalCallContext);

        intervalConsumableInArrear.addBillingEvent(event1);
        intervalConsumableInArrear.addBillingEvent(event2);

        final ContiguousIntervalUsageInArrear res = intervalConsumableInArrear.build(true);
        assertEquals(res.getTransitionTimes().size(), 0);
    }

    @Test(groups = "fast")
    public void testTobeBilledForUnit() throws CatalogApiException {

        final DefaultTieredBlock block1 = createDefaultTieredBlock("cell-phone-minutes", 1000, 10000, new BigDecimal("0.5"));
        final DefaultTieredBlock block2 = createDefaultTieredBlock("Mbytes", 512, 512000, new BigDecimal("0.3"));
        final DefaultTier tier = createDefaultTierWithBlocks(block1, block2);

        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);
        final LocalDate targetDate = new LocalDate(2014, 03, 20);
        final ContiguousIntervalConsumableUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, ImmutableList.<RawUsage>of(), targetDate, false,
                                                                                                                      createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                                                                                                                                             BillingPeriod.MONTHLY,
                                                                                                                                             Collections.<Usage>emptyList())
                                                                                                                     );
        List<UsageConsumableInArrearTierUnitDetail> results = Lists.newArrayList();
        results.addAll(intervalConsumableInArrear.computeToBeBilledConsumableInArrear(new DefaultRolledUpUnit("cell-phone-minutes", 1000L), ImmutableList.<UsageConsumableInArrearTierUnitDetail>of(), true));
        results.addAll(intervalConsumableInArrear.computeToBeBilledConsumableInArrear(new DefaultRolledUpUnit("Mbytes", 30720L), ImmutableList.<UsageConsumableInArrearTierUnitDetail>of(), true));
        assertEquals(results.size(), 2);

        assertEquals(intervalConsumableInArrear.toBeBilledForUnit(results, UsageType.CONSUMABLE), new BigDecimal("18.5"));
    }

    @Test(groups = "fast")
    public void testComputeMissingItemsAggregateModeAllTier() throws CatalogApiException, IOException, InvoiceApiException {

        // Case 1
        List<RawUsage> rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "FOO", 5L));
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "BAR", 99L));

        List<InvoiceItem> result = produceInvoiceItems(rawUsages, TierBlockPolicy.ALL_TIERS, UsageDetailMode.AGGREGATE, ImmutableList.<InvoiceItem>of());
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("203")),0);

        List<UsageConsumableInArrearTierUnitDetail> itemDetails = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<List<UsageConsumableInArrearTierUnitDetail>>() {});
        // BAR: 99 * 2 = 198
        assertEquals(itemDetails.get(0).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(0).getTier(), 1);
        assertEquals(itemDetails.get(0).getAmount().compareTo(new BigDecimal("198")), 0);
        assertEquals(itemDetails.get(0).getQuantity().intValue(), 99);
        assertEquals(itemDetails.get(0).getTierPrice().compareTo(new BigDecimal("2.0")), 0);
        // FOO: 5 * 1 = 5
        assertEquals(itemDetails.get(1).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(1).getTier(), 1);
        assertEquals(itemDetails.get(1).getAmount().compareTo(new BigDecimal("5")), 0);
        assertEquals(itemDetails.get(1).getQuantity().intValue(), 5);
        assertEquals(itemDetails.get(1).getTierPrice().compareTo(BigDecimal.ONE), 0);

        // Case 2
        rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "FOO", 5L));
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "BAR", 101L));

        result = produceInvoiceItems(rawUsages, TierBlockPolicy.ALL_TIERS, UsageDetailMode.AGGREGATE, ImmutableList.<InvoiceItem>of());
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("225")),0);

        itemDetails = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<List<UsageConsumableInArrearTierUnitDetail>>() {});
        // BAR: 100 * 2 = 200
        assertEquals(itemDetails.get(0).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(0).getTier(), 1);
        assertEquals(itemDetails.get(0).getAmount().compareTo(new BigDecimal("200.0")), 0);
        assertEquals(itemDetails.get(0).getQuantity().intValue(), 100);
        assertEquals(itemDetails.get(0).getTierPrice().compareTo(new BigDecimal("2.0")), 0);
        // BAR: 1 * 20 = 20
        assertEquals(itemDetails.get(1).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(1).getTier(), 2);
        assertEquals(itemDetails.get(1).getAmount().compareTo(new BigDecimal("20.0")), 0);
        assertEquals(itemDetails.get(1).getQuantity().intValue(), 1);
        assertEquals(itemDetails.get(1).getTierPrice().compareTo(new BigDecimal("20.0")), 0);
        // FOO: 5 * 1 = 5
        assertEquals(itemDetails.get(2).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(2).getTier(), 1);
        assertEquals(itemDetails.get(2).getAmount().compareTo(new BigDecimal("5")), 0);
        assertEquals(itemDetails.get(2).getQuantity().intValue(), 5);
        assertEquals(itemDetails.get(2).getTierPrice().compareTo(BigDecimal.ONE), 0);

        // Case 3
        rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "FOO", 75L));
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "BAR", 101L));

        result = produceInvoiceItems(rawUsages, TierBlockPolicy.ALL_TIERS, UsageDetailMode.AGGREGATE, ImmutableList.<InvoiceItem>of());
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("2230")),0);

        itemDetails = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<List<UsageConsumableInArrearTierUnitDetail>>() {});
        // BAR: 100 * 2 = 200
        assertEquals(itemDetails.get(0).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(0).getTier(), 1);
        assertEquals(itemDetails.get(0).getAmount().compareTo(new BigDecimal("200.0")), 0);
        assertEquals(itemDetails.get(0).getQuantity().intValue(), 100);
        assertEquals(itemDetails.get(0).getTierPrice().compareTo(new BigDecimal("2.0")), 0);
        // BAR: 1 * 20 = 20
        assertEquals(itemDetails.get(1).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(1).getTier(), 2);
        assertEquals(itemDetails.get(1).getAmount().compareTo(new BigDecimal("20.0")), 0);
        assertEquals(itemDetails.get(1).getQuantity().intValue(), 1);
        assertEquals(itemDetails.get(1).getTierPrice().compareTo(new BigDecimal("20.0")), 0);
        // FOO: 10 * 1 = 10
        assertEquals(itemDetails.get(2).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(2).getTier(), 1);
        assertEquals(itemDetails.get(2).getAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(itemDetails.get(2).getQuantity().intValue(), 10);
        assertEquals(itemDetails.get(2).getTierPrice().compareTo(BigDecimal.ONE), 0);
        // FOO: 50 * 10 = 500
        assertEquals(itemDetails.get(3).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(3).getTier(), 2);
        assertEquals(itemDetails.get(3).getAmount().compareTo(new BigDecimal("500")), 0);
        assertEquals(itemDetails.get(3).getQuantity().intValue(), 50);
        assertEquals(itemDetails.get(3).getTierPrice().compareTo(BigDecimal.TEN), 0);
        // FOO: 15 * 100 = 1500
        assertEquals(itemDetails.get(4).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(4).getTier(), 3);
        assertEquals(itemDetails.get(4).getAmount().compareTo(new BigDecimal("1500")), 0);
        assertEquals(itemDetails.get(4).getQuantity().intValue(), 15);
        assertEquals(itemDetails.get(4).getTierPrice().compareTo(new BigDecimal("100.0")), 0);
    }

    @Test(groups = "fast")
    public void testComputeMissingItemsDetailModeAllTier() throws CatalogApiException, IOException, InvoiceApiException {

        // Case 1
        List<RawUsage> rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "FOO", 5L));
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "BAR", 99L));

        List<InvoiceItem> result = produceInvoiceItems(rawUsages, TierBlockPolicy.ALL_TIERS, UsageDetailMode.DETAIL, ImmutableList.<InvoiceItem>of());
        assertEquals(result.size(), 2);
        // BAR: 99 * 2 = 198
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("198")), 0);
        assertEquals(result.get(0).getQuantity().intValue(), 99);
        assertEquals(result.get(0).getRate().compareTo(new BigDecimal("2.0")), 0);
        // FOO: 5 * 1 = 5
        assertEquals(result.get(1).getAmount().compareTo(new BigDecimal("5")), 0);
        assertEquals(result.get(1).getQuantity().intValue(), 5);
        assertEquals(result.get(1).getRate().compareTo(BigDecimal.ONE), 0);

        // Case 2
        rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "FOO", 5L));
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "BAR", 101L));

        result = produceInvoiceItems(rawUsages, TierBlockPolicy.ALL_TIERS, UsageDetailMode.DETAIL, ImmutableList.<InvoiceItem>of());
        assertEquals(result.size(), 3);
       // BAR: 100 * 2 = 200
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("200.0")), 0);
        assertEquals(result.get(0).getQuantity().intValue(), 100);
        assertEquals(result.get(0).getRate().compareTo(new BigDecimal("2.0")), 0);
        // BAR: 1 * 20 = 20
        assertEquals(result.get(1).getAmount().compareTo(new BigDecimal("20.0")), 0);
        assertEquals(result.get(1).getQuantity().intValue(), 1);
        assertEquals(result.get(1).getRate().compareTo(new BigDecimal("20.0")), 0);
        // FOO: 5 * 1 = 5
        assertEquals(result.get(2).getAmount().compareTo(new BigDecimal("5")), 0);
        assertEquals(result.get(2).getQuantity().intValue(), 5);
        assertEquals(result.get(2).getRate().compareTo(BigDecimal.ONE), 0);

        // Case 3
        rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "FOO", 75L));
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "BAR", 101L));

        result = produceInvoiceItems(rawUsages, TierBlockPolicy.ALL_TIERS, UsageDetailMode.DETAIL, ImmutableList.<InvoiceItem>of());
        assertEquals(result.size(), 5);
       // BAR: 100 * 2 = 200
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("200.0")), 0);
        assertEquals(result.get(0).getQuantity().intValue(), 100);
        assertEquals(result.get(0).getRate().compareTo(new BigDecimal("2.0")), 0);
        // BAR: 1 * 20 = 20
        assertEquals(result.get(1).getAmount().compareTo(new BigDecimal("20.0")), 0);
        assertEquals(result.get(1).getQuantity().intValue(), 1);
        assertEquals(result.get(1).getRate().compareTo(new BigDecimal("20.0")), 0);
        // FOO: 10 * 1 = 10
        assertEquals(result.get(2).getAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(result.get(2).getQuantity().intValue(), 10);
        assertEquals(result.get(2).getRate().compareTo(BigDecimal.ONE), 0);
        // FOO: 50 * 10 = 500
        assertEquals(result.get(3).getAmount().compareTo(new BigDecimal("500")), 0);
        assertEquals(result.get(3).getQuantity().intValue(), 50);
        assertEquals(result.get(3).getRate().compareTo(BigDecimal.TEN), 0);
        // FOO: 15 * 100 = 1500
        assertEquals(result.get(4).getAmount().compareTo(new BigDecimal("1500")), 0);
        assertEquals(result.get(4).getQuantity().intValue(), 15);
        assertEquals(result.get(4).getRate().compareTo(new BigDecimal("100.0")), 0);
    }

    @Test(groups = "fast")
    public void testComputeMissingItemsAggregateModeTopTier() throws CatalogApiException, IOException, InvoiceApiException {

        // Case 1
        List<RawUsage> rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "FOO", 5L));
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "BAR", 99L));

        List<InvoiceItem> result = produceInvoiceItems(rawUsages, TierBlockPolicy.TOP_TIER, UsageDetailMode.AGGREGATE, ImmutableList.<InvoiceItem>of());
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("203")), 0);

        UsageConsumableInArrearDetail usageDetail = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<UsageConsumableInArrearDetail>() {});
        List<UsageConsumableInArrearTierUnitDetail> itemDetails = usageDetail.getTierDetails();
        // BAR: 99 * 2 = 198
        assertEquals(itemDetails.get(0).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(0).getTier(), 1);
        assertEquals(itemDetails.get(0).getAmount().compareTo(new BigDecimal("198")), 0);
        assertEquals(itemDetails.get(0).getQuantity().intValue(), 99);
        assertEquals(itemDetails.get(0).getTierPrice().compareTo(new BigDecimal("2.0")), 0);
        // FOO: 5 * 1 = 5
        assertEquals(itemDetails.get(1).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(1).getTier(), 1);
        assertEquals(itemDetails.get(1).getAmount().compareTo(new BigDecimal("5")), 0);
        assertEquals(itemDetails.get(1).getQuantity().intValue(), 5);
        assertEquals(itemDetails.get(1).getTierPrice().compareTo(BigDecimal.ONE), 0);

        // Case 2
        rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "FOO", 5L));
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "BAR", 101L));

        result = produceInvoiceItems(rawUsages, TierBlockPolicy.TOP_TIER, UsageDetailMode.AGGREGATE, ImmutableList.<InvoiceItem>of());
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("2025")), 0);

        usageDetail = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<UsageConsumableInArrearDetail>() {});
        itemDetails = usageDetail.getTierDetails();

        // BAR: 101 * 20 = 2020
        assertEquals(itemDetails.get(0).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(0).getTier(), 2);
        assertEquals(itemDetails.get(0).getAmount().compareTo(new BigDecimal("2020.0")), 0);
        assertEquals(itemDetails.get(0).getQuantity().intValue(), 101);
        assertEquals(itemDetails.get(0).getTierPrice().compareTo(new BigDecimal("20.0")), 0);
        // FOO: 5 * 1 = 5
        assertEquals(itemDetails.get(1).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(1).getTier(), 1);
        assertEquals(itemDetails.get(1).getAmount().compareTo(new BigDecimal("5")), 0);
        assertEquals(itemDetails.get(1).getQuantity().intValue(), 5);
        assertEquals(itemDetails.get(1).getTierPrice().compareTo(BigDecimal.ONE), 0);

        // Case 3
        rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "FOO", 76L));
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "BAR", 101L));

        result = produceInvoiceItems(rawUsages, TierBlockPolicy.TOP_TIER, UsageDetailMode.AGGREGATE, ImmutableList.<InvoiceItem>of());
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("9620")), 0);

        usageDetail = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<UsageConsumableInArrearDetail>() {});
        itemDetails = usageDetail.getTierDetails();
        // BAR: 101 * 20 = 2020
        assertEquals(itemDetails.get(0).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(0).getTier(), 2);
        assertEquals(itemDetails.get(0).getAmount().compareTo(new BigDecimal("2020.0")), 0);
        assertEquals(itemDetails.get(0).getQuantity().intValue(), 101);
        assertEquals(itemDetails.get(0).getTierPrice().compareTo(new BigDecimal("20.0")), 0);
        // FOO: 76 * 100 = 7500
        assertEquals(itemDetails.get(1).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(1).getTier(), 3);
        assertEquals(itemDetails.get(1).getAmount().compareTo(new BigDecimal("7600")), 0);
        assertEquals(itemDetails.get(1).getQuantity().intValue(), 76);
        assertEquals(itemDetails.get(1).getTierPrice().compareTo(new BigDecimal("100.0")), 0);
    }

    @Test(groups = "fast")
    public void testComputeMissingItemsDetailModeTopTier() throws CatalogApiException, IOException, InvoiceApiException {

        // Case 1
        List<RawUsage> rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "FOO", 5L));
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "BAR", 99L));

        List<InvoiceItem> result = produceInvoiceItems(rawUsages, TierBlockPolicy.TOP_TIER, UsageDetailMode.DETAIL, ImmutableList.<InvoiceItem>of());
        assertEquals(result.size(), 2);
        // BAR: 99 * 2 = 198
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("198")), 0);
        assertEquals(result.get(0).getQuantity().intValue(), 99);
        assertEquals(result.get(0).getRate().compareTo(new BigDecimal("2.0")), 0);
        // FOO: 5 * 1 = 5
        assertEquals(result.get(1).getAmount().compareTo(new BigDecimal("5")), 0);
        assertEquals(result.get(1).getQuantity().intValue(), 5);
        assertEquals(result.get(1).getRate().compareTo(BigDecimal.ONE), 0);

        // Case 2
        rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "FOO", 5L));
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "BAR", 101L));

        result = produceInvoiceItems(rawUsages, TierBlockPolicy.TOP_TIER, UsageDetailMode.DETAIL, ImmutableList.<InvoiceItem>of());
        assertEquals(result.size(), 2);
        // BAR: 101 * 20 = 2020
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("2020.0")), 0);
        assertEquals(result.get(0).getQuantity().intValue(), 101);
        assertEquals(result.get(0).getRate().compareTo(new BigDecimal("20.0")), 0);
        // FOO: 5 * 1 = 5
        assertEquals(result.get(1).getAmount().compareTo(new BigDecimal("5")), 0);
        assertEquals(result.get(1).getQuantity().intValue(), 5);
        assertEquals(result.get(1).getRate().compareTo(BigDecimal.ONE), 0);

        // Case 3
        rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "FOO", 76L));
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "BAR", 101L));

        result = produceInvoiceItems(rawUsages, TierBlockPolicy.TOP_TIER, UsageDetailMode.DETAIL, ImmutableList.<InvoiceItem>of());
        assertEquals(result.size(), 2);
        // BAR: 101 * 20 = 2020
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("2020.0")), 0);
        assertEquals(result.get(0).getQuantity().intValue(), 101);
        assertEquals(result.get(0).getRate().compareTo(new BigDecimal("20.0")), 0);
        // FOO: 76 * 100 = 7500
        assertEquals(result.get(1).getAmount().compareTo(new BigDecimal("7600")), 0);
        assertEquals(result.get(1).getQuantity().intValue(), 76);
        assertEquals(result.get(1).getRate().compareTo(new BigDecimal("100.0")), 0);
    }



    @Test(groups = "fast")
    public void testMultipleItemsAndTiersWithExistingItemsAllTiers() throws CatalogApiException, IOException, InvoiceApiException {

        //
        // Let's assume we were already billed on the previous period
        //
        // FOO : 10 (tier 1) + 40 (tier 2) = 50
        final UsageConsumableInArrearTierUnitDetail existingFooUsageTier1 = new UsageConsumableInArrearTierUnitDetail(1, "FOO", BigDecimal.ONE, 1, 10, new BigDecimal("10.00"));
        final UsageConsumableInArrearTierUnitDetail existingFooUsageTier2 = new UsageConsumableInArrearTierUnitDetail(2, "FOO", BigDecimal.TEN, 1, 40, new BigDecimal("400.00"));
        // BAR : 10 (tier 1) + 40 (tier 2)
        final UsageConsumableInArrearTierUnitDetail existingBarUsageTier1 = new UsageConsumableInArrearTierUnitDetail(1, "BAR", new BigDecimal("2.00"), 1, 80, new BigDecimal("160.00"));

        final List<UsageConsumableInArrearTierUnitDetail> existingUsage = ImmutableList.of(existingFooUsageTier1, existingFooUsageTier2, existingBarUsageTier1);

        final UsageConsumableInArrearDetail tmp = new UsageConsumableInArrearDetail(existingUsage);

        final String existingUsageJson = tmp.toJson(objectMapper);

        //
        // Create usage data points (will include already billed + add new usage data)
        //
        List<RawUsage> rawUsages = new ArrayList<RawUsage>();
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 20), "FOO", 50L /* already built */ + 20L)); // tier 3
        rawUsages.add(new DefaultRawUsage(subscriptionId, new LocalDate(2014, 03, 21), "BAR", 80L /* already built */ + 120L)); // tier 2

        final List<InvoiceItem> existingItems = new ArrayList<InvoiceItem>();
        final InvoiceItem ii1 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, usageName, new LocalDate(2014, 03, 20), new LocalDate(2014, 04, 15), new BigDecimal("570.00"), null, currency, null, existingUsageJson);
        existingItems.add(ii1);

        List<InvoiceItem> result = produceInvoiceItems(rawUsages, TierBlockPolicy.ALL_TIERS, UsageDetailMode.AGGREGATE, existingItems);
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getAmount().compareTo(new BigDecimal("3140.00")), 0, String.format("%s != 3140.0", result.get(0).getAmount()));

        UsageConsumableInArrearDetail usageDetail = objectMapper.readValue(result.get(0).getItemDetails(), new TypeReference<UsageConsumableInArrearDetail>() {});
        List<UsageConsumableInArrearTierUnitDetail> itemDetails = usageDetail.getTierDetails();

        // BAR item detail
        assertEquals(itemDetails.get(0).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(0).getTier(), 1);
        assertEquals(itemDetails.get(0).getTierBlockSize(), 1);
        assertEquals(itemDetails.get(0).getQuantity().intValue(), 20);
        assertEquals(itemDetails.get(0).getTierPrice().compareTo(new BigDecimal("2.00")), 0);
        assertEquals(itemDetails.get(0).getAmount().compareTo(new BigDecimal("40.00")), 0);

        assertEquals(itemDetails.get(1).getTierUnit(), "BAR");
        assertEquals(itemDetails.get(1).getTier(), 2);
        assertEquals(itemDetails.get(1).getTierBlockSize(), 1);
        assertEquals(itemDetails.get(1).getQuantity().intValue(), 100);
        assertEquals(itemDetails.get(1).getTierPrice().compareTo(new BigDecimal("20.00")), 0);
        assertEquals(itemDetails.get(1).getAmount().compareTo(new BigDecimal("2000.00")), 0);

        // FOO item detail
        assertEquals(itemDetails.get(2).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(2).getTier(), 2);
        assertEquals(itemDetails.get(2).getTierBlockSize(), 1);
        assertEquals(itemDetails.get(2).getQuantity().intValue(), 10);
        assertEquals(itemDetails.get(2).getTierPrice().compareTo(new BigDecimal("10.00")), 0);
        assertEquals(itemDetails.get(2).getAmount().compareTo(new BigDecimal("100.00")), 0);

        assertEquals(itemDetails.get(3).getTierUnit(), "FOO");
        assertEquals(itemDetails.get(3).getTier(), 3);
        assertEquals(itemDetails.get(3).getTierBlockSize(), 1);
        assertEquals(itemDetails.get(3).getQuantity().intValue(), 10);
        assertEquals(itemDetails.get(3).getTierPrice().compareTo(new BigDecimal("100.00")), 0);
        assertEquals(itemDetails.get(3).getAmount().compareTo(new BigDecimal("1000.00")), 0);
    }


    @Test(groups = "fast")
    public void testMultipleItemsAndTiersWithExistingItemsTopTier() throws CatalogApiException, IOException {
        // TODO + code
    }

    private List<InvoiceItem> produceInvoiceItems(List<RawUsage> rawUsages, TierBlockPolicy tierBlockPolicy, UsageDetailMode usageDetailMode, final List<InvoiceItem> existingItems) throws CatalogApiException, InvoiceApiException {

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

        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC),BillingPeriod.MONTHLY, Collections.<Usage>emptyList());
        final BillingEvent event2 = createMockBillingEvent(endDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), BillingPeriod.MONTHLY, Collections.<Usage>emptyList());

        final ContiguousIntervalUsageInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, rawUsages, targetDate, true, usageDetailMode, event1, event2);

        final UsageInArrearItemsAndNextNotificationDate usageResult = intervalConsumableInArrear.computeMissingItemsAndNextNotificationDate(existingItems);
        final List<InvoiceItem> rawResults = usageResult.getInvoiceItems();
        final List<InvoiceItem> result = ImmutableList.copyOf(Iterables.filter(rawResults, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return input.getAmount().compareTo(BigDecimal.ZERO) > 0;
            }
        }));

        for (InvoiceItem item: result) {
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
