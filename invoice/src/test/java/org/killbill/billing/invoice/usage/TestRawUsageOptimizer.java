/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.DefaultTier;
import org.killbill.billing.catalog.DefaultTieredBlock;
import org.killbill.billing.catalog.DefaultUsage;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.TierBlockPolicy;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.model.UsageInvoiceItem;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestRawUsageOptimizer extends TestUsageInArrearBase {

    @Test(groups = "fast")
    public void testWithNoItems() {

        final LocalDate firstEventStartDate = new LocalDate(2014, 03, 15);

        final List<InvoiceItem> invoiceItems = new ArrayList<InvoiceItem>();

        final Map<String, Usage> knownUsage = new HashMap<String, Usage>();
        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 1000, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);
        knownUsage.put(usageName, usage);

        final LocalDate result = rawUsageOptimizer.getOptimizedRawUsageStartDate(firstEventStartDate, firstEventStartDate.plusDays(1), invoiceItems, knownUsage, internalCallContext);
        Assert.assertEquals(result.compareTo(firstEventStartDate), 0);
    }

    @Test(groups = "fast")
    public void testWithOneMonthlyUsageSectionTooFewItems() {

        final LocalDate firstEventStartDate = new LocalDate(2014, 03, 15);

        final List<InvoiceItem> invoiceItems = new ArrayList<InvoiceItem>();
        invoiceItems.add(createUsageItem(firstEventStartDate));
        final LocalDate targetDate = invoiceItems.get(invoiceItems.size() - 1).getEndDate();

        final Map<String, Usage> knownUsage = new HashMap<String, Usage>();
        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 1000, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);
        knownUsage.put(usageName, usage);

        final LocalDate result = rawUsageOptimizer.getOptimizedRawUsageStartDate(firstEventStartDate, targetDate, invoiceItems, knownUsage, internalCallContext);
        // The largest endDate for ii is 2014-04-15, and by default org.killbill.invoice.readMaxRawUsagePreviousPeriod == 2 => targetDate =>  2014-02-15,
        // so we default to firstEventStartDate = 2014-03-15
        Assert.assertEquals(result.compareTo(firstEventStartDate), 0);
    }

    @Test(groups = "fast")
    public void testWithOneMonthlyUsageSectionAndEnoughUsageItems() {

        final LocalDate firstEventStartDate = new LocalDate(2014, 03, 15);

        final List<InvoiceItem> invoiceItems = new ArrayList<InvoiceItem>();
        for (int i = 0; i < 5; i++) {
            invoiceItems.add(createUsageItem(firstEventStartDate.plusMonths(i)));
        }
        final LocalDate targetDate = invoiceItems.get(invoiceItems.size() - 1).getEndDate();

        final Map<String, Usage> knownUsage = new HashMap<String, Usage>();
        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 1000, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);
        knownUsage.put(usageName, usage);

        final LocalDate result = rawUsageOptimizer.getOptimizedRawUsageStartDate(firstEventStartDate, targetDate, invoiceItems, knownUsage, internalCallContext);
        // The largest endDate for ii is 2014-08-15, and by default org.killbill.invoice.readMaxRawUsagePreviousPeriod == 2 => targetDate =>  2014-06-15
        Assert.assertEquals(result.compareTo(new LocalDate(2014, 06, 15)), 0, "112 got " + result);
    }

    @Test(groups = "fast")
    public void testWithOneMonthlyAndOneNonActiveAnnualUsageSectionAndEnoughUsageItems() {

        final LocalDate firstEventStartDate = new LocalDate(2014, 03, 15);

        final List<InvoiceItem> invoiceItems = new ArrayList<InvoiceItem>();
        for (int i = 0; i < 5; i++) {
            invoiceItems.add(createUsageItem(firstEventStartDate.plusMonths(i)));
        }
        final LocalDate targetDate = invoiceItems.get(invoiceItems.size() - 1).getEndDate();

        final Map<String, Usage> knownUsage = new HashMap<String, Usage>();
        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 1000, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTierWithBlocks(block);
        final DefaultUsage usage = createConsumableInArrearUsage(usageName, BillingPeriod.MONTHLY, TierBlockPolicy.ALL_TIERS, tier);
        knownUsage.put(usageName, usage);

        final DefaultTieredBlock block2 = createDefaultTieredBlock("unit2", 10, 10000, BigDecimal.TEN);
        final DefaultTier tier2 = createDefaultTierWithBlocks(block2);
        final DefaultUsage usage2 = createConsumableInArrearUsage("usageName2", BillingPeriod.ANNUAL, TierBlockPolicy.ALL_TIERS, tier2);
        knownUsage.put("usageName2", usage2);

        final LocalDate result = rawUsageOptimizer.getOptimizedRawUsageStartDate(firstEventStartDate, targetDate, invoiceItems, knownUsage, internalCallContext);
        // The same reasoning applies as previously because there is no usage items against the annual and
        // so, the largest endDate for ii is 2014-08-15, and by default org.killbill.invoice.readMaxRawUsagePreviousPeriod == 2 => targetDate =>  2014-06-15
        Assert.assertEquals(result.compareTo(new LocalDate(2014, 06, 15)), 0, "142 got " + result);
    }

    private InvoiceItem createUsageItem(final LocalDate startDate) {
        return new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usageName, startDate, startDate.plusMonths(1), BigDecimal.TEN, Currency.USD);
    }
}
