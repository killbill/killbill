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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.DefaultInternationalPrice;
import org.killbill.billing.catalog.DefaultLimit;
import org.killbill.billing.catalog.DefaultPrice;
import org.killbill.billing.catalog.DefaultTier;
import org.killbill.billing.catalog.DefaultTieredBlock;
import org.killbill.billing.catalog.DefaultUnit;
import org.killbill.billing.catalog.DefaultUsage;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.TierBlockPolicy;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.TrackingRecordId;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.usage.api.RawUsageRecord;
import org.killbill.billing.util.config.definition.InvoiceConfig.UsageDetailMode;
import org.killbill.billing.util.jackson.ObjectMapper;
import org.mockito.Mockito;
import org.testng.annotations.BeforeClass;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public abstract class TestUsageInArrearBase extends InvoiceTestSuiteNoDB {

    protected static final Set<TrackingRecordId> EMPTY_EXISTING_TRACKING_IDS = Collections.emptySet();

    protected int BCD;
    protected UUID accountId;
    protected UUID bundleId;
    protected UUID subscriptionId;
    protected UUID invoiceId;
    protected String productName;
    protected String planName;
    protected String phaseName;
    protected Currency currency;
    protected String usageName;
    protected ObjectMapper objectMapper;
    protected DateTime catalogEffectiveDate;

    @BeforeClass(groups = "fast")
    protected void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        BCD = 15;
        usageName = "foo";
        accountId = UUID.randomUUID();
        bundleId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();
        productName = "productName";
        planName = "planName";
        phaseName = "phaseName";
        catalogEffectiveDate = clock.getUTCNow();

        currency = Currency.USD;
        usageDetailMode = invoiceConfig.getItemResultBehaviorMode(internalCallContext);
        objectMapper = new ObjectMapper();
    }

    protected ContiguousIntervalCapacityUsageInArrear createContiguousIntervalCapacityInArrear(final DefaultUsage usage, final LocalDate targetDate, final boolean closedInterval, final BillingEvent... events) {
        return createContiguousIntervalCapacityInArrear(usage, targetDate, closedInterval, usageDetailMode, events);
    }

    protected ContiguousIntervalCapacityUsageInArrear createContiguousIntervalCapacityInArrear(final DefaultUsage usage, final LocalDate targetDate, final boolean closedInterval, UsageDetailMode detailMode, final BillingEvent... events) {
        final ContiguousIntervalCapacityUsageInArrear intervalCapacityInArrear = new ContiguousIntervalCapacityUsageInArrear(usage, accountId, invoiceId, targetDate, events[0].getEffectiveDate(), detailMode, invoiceConfig,  internalCallContext);
        for (final BillingEvent event : events) {
            intervalCapacityInArrear.addBillingEvent(event);
            intervalCapacityInArrear.addAllSeenUnitTypesForBillingEvent(event, intervalCapacityInArrear.getUnitTypes());
        }
        intervalCapacityInArrear.build(closedInterval);
        return intervalCapacityInArrear;
    }

    protected ContiguousIntervalConsumableUsageInArrear createContiguousIntervalConsumableInArrear(final DefaultUsage usage, final LocalDate targetDate, final boolean closedInterval, final BillingEvent... events) {
        return createContiguousIntervalConsumableInArrear(usage, targetDate, closedInterval, usageDetailMode, events);
    }

    protected ContiguousIntervalConsumableUsageInArrear createContiguousIntervalConsumableInArrear(final DefaultUsage usage, final LocalDate targetDate, final boolean closedInterval, UsageDetailMode detailMode, final BillingEvent... events) {
        final ContiguousIntervalConsumableUsageInArrear intervalConsumableInArrear = new ContiguousIntervalConsumableUsageInArrear(usage, accountId, invoiceId, targetDate, events[0].getEffectiveDate(), detailMode, invoiceConfig, internalCallContext);
        for (final BillingEvent event : events) {
            intervalConsumableInArrear.addBillingEvent(event);
            intervalConsumableInArrear.addAllSeenUnitTypesForBillingEvent(event, intervalConsumableInArrear.getUnitTypes());
        }
        intervalConsumableInArrear.build(closedInterval);
        return intervalConsumableInArrear;
    }

    protected DefaultUsage createConsumableInArrearUsage(final String usageName, final BillingPeriod billingPeriod, final TierBlockPolicy tierBlockPolicy, final DefaultTier... tiers) {
        final DefaultUsage usage = new DefaultUsage();
        usage.setName(usageName);
        usage.setBillingMode(BillingMode.IN_ARREAR);
        usage.setUsageType(UsageType.CONSUMABLE);
        usage.setTierBlockPolicy(tierBlockPolicy);
        usage.setBillingPeriod(billingPeriod);
        usage.setTiers(tiers);
        return usage;
    }

    protected DefaultUsage createCapacityInArrearUsage(final String usageName, final BillingPeriod billingPeriod, final DefaultTier... tiers) {
        final DefaultUsage usage = new DefaultUsage();
        usage.setName(usageName);
        usage.setBillingMode(BillingMode.IN_ARREAR);
        usage.setUsageType(UsageType.CAPACITY);
        usage.setBillingPeriod(billingPeriod);
        usage.setTiers(tiers);
        return usage;
    }

    protected DefaultTier createDefaultTierWithBlocks(final DefaultTieredBlock... blocks) {
        final DefaultTier tier = new DefaultTier();
        tier.setBlocks(blocks);
        return tier;
    }

    protected DefaultTier createDefaultTierWithLimits(final BigDecimal recurringAmountInCurrency, final DefaultLimit... limits) {
        final DefaultTier tier = new DefaultTier();
        tier.setLimits(limits);

        final DefaultPrice[] prices = new DefaultPrice[1];
        prices[0] = new DefaultPrice().setCurrency(currency).setValue(recurringAmountInCurrency);
        final DefaultInternationalPrice price = new DefaultInternationalPrice().setPrices(prices);
        tier.setRecurringPrice(price);
        return tier;
    }

    protected DefaultTieredBlock createDefaultTieredBlock(final String unit, final int size, final int max, final BigDecimal price) {
        final DefaultTieredBlock block = new DefaultTieredBlock();
        block.setUnit(new DefaultUnit().setName(unit));
        block.setSize(BigDecimal.valueOf(size));

        final DefaultPrice[] prices = new DefaultPrice[1];
        prices[0] = new DefaultPrice();
        prices[0].setCurrency(currency).setValue(price);

        block.setPrice(new DefaultInternationalPrice().setPrices(prices));
        block.setMax(new BigDecimal(max));
        return block;
    }

    protected BillingEvent createMockBillingEvent(final DateTime effectiveDate, final BillingPeriod billingPeriod, final List<Usage> usages, final DateTime catalogEffectiveDate) throws Exception {
        return createMockBillingEvent(BCD, effectiveDate, billingPeriod, usages, catalogEffectiveDate, SubscriptionBaseTransitionType.CREATE);
    }

    protected BillingEvent createMockBillingEvent(final int bcd, final DateTime effectiveDate, final BillingPeriod billingPeriod, final List<Usage> usages, final DateTime catalogEffectiveDate, SubscriptionBaseTransitionType beType) throws Exception {
        final BillingEvent result = Mockito.mock(BillingEvent.class);
        Mockito.when(result.getCurrency()).thenReturn(currency);
        Mockito.when(result.getBillCycleDayLocal()).thenReturn(bcd);
        Mockito.when(result.getEffectiveDate()).thenReturn(effectiveDate);
        Mockito.when(result.getBillingPeriod()).thenReturn(billingPeriod);
        Mockito.when(result.getSubscriptionId()).thenReturn(subscriptionId);
        Mockito.when(result.getBundleId()).thenReturn(bundleId);
        Mockito.when(result.getCatalogEffectiveDate()).thenReturn(catalogEffectiveDate);
        Mockito.when(result.getTransitionType()).thenReturn(beType);

        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(accountId);

        final SubscriptionBase subscription = Mockito.mock(SubscriptionBase.class);
        Mockito.when(subscription.getId()).thenReturn(subscriptionId);
        Mockito.when(subscription.getBundleId()).thenReturn(bundleId);

        final Product product = Mockito.mock(Product.class);
        Mockito.when(product.getName()).thenReturn(productName);

        final Plan plan = Mockito.mock(Plan.class);
        Mockito.when(plan.getName()).thenReturn(planName);
        Mockito.when(plan.getProduct()).thenReturn(product);
        Mockito.when(result.getPlan()).thenReturn(plan);

        final PlanPhase phase = Mockito.mock(PlanPhase.class);
        Mockito.when(phase.getName()).thenReturn(phaseName);
        Mockito.when(result.getPlanPhase()).thenReturn(phase);

        Mockito.when(result.getUsages()).thenReturn(usages);
        return result;
    }

    //
    // Each input `RawUsage` should end up creating one TrackingRecordId
    // Regardless of how test records trackingId -- grouped in one, or multiple calls-- and regardless of test matrix,  the logics below should remain true.
    //
    protected void checkTrackingIds(final List<RawUsageRecord> rawUsages, final Set<TrackingRecordId> trackingRecords) {

        // Verify we have same input and output
        assertEquals(rawUsages.size(), trackingRecords.size());

        final Map<String, List<RawUsageRecord>> trackingIdMapping = new HashMap<>();
        for (final RawUsageRecord u : rawUsages) {
            if (!trackingIdMapping.containsKey(u.getTrackingId())) {
                trackingIdMapping.put(u.getTrackingId(), new ArrayList<>());
            }
            trackingIdMapping.get(u.getTrackingId()).add(u);
        }

        final Set<String> trackingIds = trackingRecords.stream()
                .map(TrackingRecordId::getTrackingId)
                .collect(Collectors.toUnmodifiableSet());

        // Verify the per trackingId input matches the per trackingId output
        assertEquals(trackingIdMapping.size(), trackingIds.size());

        for (final String id : trackingIdMapping.keySet()) {

            final List<RawUsageRecord> rawUsageForId = trackingIdMapping.get(id);
            for (RawUsageRecord u : rawUsageForId) {
                final TrackingRecordId found = trackingRecords.stream()
                        .filter(input -> input.getTrackingId().equals(u.getTrackingId()) &&
                                         input.getRecordDate().equals(internalCallContext.toLocalDate(u.getDate())) &&
                                         input.getUnitType().equals(u.getUnitType()))
                        .findFirst().orElse(null);
                assertNotNull(found, "Cannot find tracking Id " + u.getTrackingId());

                assertEquals(found.getSubscriptionId(), subscriptionId);
                assertEquals(found.getInvoiceId(), invoiceId);
                assertEquals(found.getRecordDate(), internalCallContext.toLocalDate(u.getDate()));
                assertEquals(found.getUnitType(), u.getUnitType());
            }
        }
    }

}
