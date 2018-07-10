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
import java.util.List;
import java.util.UUID;

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
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.usage.RawUsage;
import org.killbill.billing.util.config.definition.InvoiceConfig.UsageDetailMode;
import org.killbill.billing.util.jackson.ObjectMapper;
import org.mockito.Mockito;
import org.testng.annotations.BeforeClass;

public abstract class TestUsageInArrearBase extends InvoiceTestSuiteNoDB {

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
        currency = Currency.BTC;
        usageDetailMode = invoiceConfig.getItemResultBehaviorMode(internalCallContext);
        objectMapper = new ObjectMapper();
    }


    protected ContiguousIntervalCapacityUsageInArrear createContiguousIntervalCapacityInArrear(final DefaultUsage usage, final List<RawUsage> rawUsages, final LocalDate targetDate, final boolean closedInterval, final BillingEvent... events) {
        return createContiguousIntervalCapacityInArrear(usage, rawUsages, targetDate, closedInterval, usageDetailMode, events);
    }

    protected ContiguousIntervalCapacityUsageInArrear createContiguousIntervalCapacityInArrear(final DefaultUsage usage, final List<RawUsage> rawUsages, final LocalDate targetDate, final boolean closedInterval, UsageDetailMode detailMode, final BillingEvent... events) {
        final ContiguousIntervalCapacityUsageInArrear intervalCapacityInArrear = new ContiguousIntervalCapacityUsageInArrear(usage, accountId, invoiceId, rawUsages, targetDate, new LocalDate(events[0].getEffectiveDate()),  detailMode, internalCallContext);
        for (final BillingEvent event : events) {
            intervalCapacityInArrear.addBillingEvent(event);
        }
        intervalCapacityInArrear.build(closedInterval);
        return intervalCapacityInArrear;
    }


    protected ContiguousIntervalConsumableUsageInArrear createContiguousIntervalConsumableInArrear(final DefaultUsage usage, final List<RawUsage> rawUsages, final LocalDate targetDate, final boolean closedInterval, final BillingEvent... events) {
        return createContiguousIntervalConsumableInArrear(usage, rawUsages, targetDate, closedInterval, usageDetailMode, events);
    }

    protected ContiguousIntervalConsumableUsageInArrear createContiguousIntervalConsumableInArrear(final DefaultUsage usage, final List<RawUsage> rawUsages, final LocalDate targetDate, final boolean closedInterval, UsageDetailMode detailMode, final BillingEvent... events) {
        final ContiguousIntervalConsumableUsageInArrear intervalConsumableInArrear = new ContiguousIntervalConsumableUsageInArrear(usage, accountId, invoiceId, rawUsages, targetDate, new LocalDate(events[0].getEffectiveDate()), detailMode, internalCallContext);
        for (final BillingEvent event : events) {
            intervalConsumableInArrear.addBillingEvent(event);
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

    protected DefaultTieredBlock createDefaultTieredBlock(final String unit, final int size, final int max, final BigDecimal btcPrice) {
        final DefaultTieredBlock block = new DefaultTieredBlock();
        block.setUnit(new DefaultUnit().setName(unit));
        block.setSize(new Double(size));

        final DefaultPrice[] prices = new DefaultPrice[1];
        prices[0] = new DefaultPrice();
        prices[0].setCurrency(Currency.BTC).setValue(btcPrice);

        block.setPrice(new DefaultInternationalPrice().setPrices(prices));
        block.setMax(new Double(max));
        return block;
    }

    protected BillingEvent createMockBillingEvent(final DateTime effectiveDate, final BillingPeriod billingPeriod, final List<Usage> usages) {
        final BillingEvent result = Mockito.mock(BillingEvent.class);
        Mockito.when(result.getCurrency()).thenReturn(Currency.BTC);
        Mockito.when(result.getBillCycleDayLocal()).thenReturn(BCD);
        Mockito.when(result.getEffectiveDate()).thenReturn(effectiveDate);
        Mockito.when(result.getBillingPeriod()).thenReturn(billingPeriod);


        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(accountId);

        final SubscriptionBase subscription = Mockito.mock(SubscriptionBase.class);
        Mockito.when(subscription.getId()).thenReturn(subscriptionId);
        Mockito.when(subscription.getBundleId()).thenReturn(bundleId);
        Mockito.when(result.getSubscription()).thenReturn(subscription);

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

}
