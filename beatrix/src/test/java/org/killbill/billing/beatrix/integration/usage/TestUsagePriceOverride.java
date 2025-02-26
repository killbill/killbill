/*
 * Copyright 2020-2024 Equinix, Inc
 * Copyright 2014-2024 The Billing Project, LLC
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

package org.killbill.billing.beatrix.integration.usage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.DefaultPlanPhasePriceOverride;
import org.killbill.billing.catalog.DefaultTierPriceOverride;
import org.killbill.billing.catalog.DefaultTieredBlockPriceOverride;
import org.killbill.billing.catalog.DefaultUsagePriceOverride;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.TierPriceOverride;
import org.killbill.billing.catalog.api.TieredBlockPriceOverride;
import org.killbill.billing.catalog.api.UsagePriceOverride;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestUsagePriceOverride extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testUsagePriceOverrides");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/2066")
    public void testInArrearUsageWithOverride() throws Exception {
        final LocalDate today = new LocalDate(2024, 11, 14);
        clock.setDay(today);

        final AccountData accountData = getAccountData(14);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        //create base
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("pistol-monthly"), null, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, Collections.emptyList(), callContext);
        final Subscription baseSubscription = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlementId, false, callContext);
        assertListenerStatus();

        //create addon with recurring+usage price overrides
        TieredBlockPriceOverride tieredBlockPriceOverride = new DefaultTieredBlockPriceOverride("bullets", BigDecimal.valueOf(1), new BigDecimal("1.25"), Currency.USD, BigDecimal.valueOf(-1));
        TierPriceOverride tierPriceOverride = new DefaultTierPriceOverride(List.of(tieredBlockPriceOverride));
        UsagePriceOverride usagePriceOverride = new DefaultUsagePriceOverride("bullets-usage-in-arrear-usage", UsageType.CONSUMABLE, List.of(tierPriceOverride));
        PlanPhasePriceOverride planPhaseOverride = new DefaultPlanPhasePriceOverride("bullets-usage-in-arrear-evergreen", Currency.USD, null, new BigDecimal("6.05"), List.of(usagePriceOverride));
        EntitlementSpecifier usageAddOnEntitlementSpecifier = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("bullets-usage-in-arrear", PhaseType.EVERGREEN), null, null, null, List.of(planPhaseOverride));

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID addOnEntitlementId = entitlementApi.addEntitlement(baseSubscription.getBundleId(), usageAddOnEntitlementSpecifier, null, null, false, Collections.emptyList(), callContext);
        Subscription aoSub = subscriptionApi.getSubscriptionForEntitlementId(addOnEntitlementId, false, callContext);
        assertListenerStatus();

        //overridden plan name
        Plan aoPlan = aoSub.getLastActivePlan();

        //record usage for 2024-11-14
        recordUsageData(aoSub.getId(), "t1", "bullets", clock.getUTCNow(), BigDecimal.valueOf(10L), callContext);

        //check invoice for targetDate=2024-12-14 - addon recurring/usage item as per overridden price
        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2024, 11, 14), new LocalDate(2024, 12, 14), InvoiceItemType.RECURRING, new BigDecimal("19.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2024, 11, 14), new LocalDate(2024, 12, 14), InvoiceItemType.RECURRING, new BigDecimal("6.05"))); //overridden recurring price
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2024, 11, 14), new LocalDate(2024, 12, 14), InvoiceItemType.USAGE, new BigDecimal("12.5"))); //10 units x 1.25 (overridden usage price)
        Invoice dryRunInvoice = invoiceUserApi.triggerDryRunInvoiceGeneration(account.getId(), clock.getUTCToday().plusMonths(1), new TestDryRunArguments(DryRunType.TARGET_DATE), Collections.emptyList(), callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);
        expectedInvoices.clear();

        //change addon with date=2024-12-14 and with recurring+usage price overrides
        tieredBlockPriceOverride = new DefaultTieredBlockPriceOverride("bullets", BigDecimal.valueOf(1), new BigDecimal("2.25"), Currency.USD, BigDecimal.valueOf(-1));
        tierPriceOverride = new DefaultTierPriceOverride(List.of(tieredBlockPriceOverride));
        usagePriceOverride = new DefaultUsagePriceOverride("bullets-usage-in-arrear-usage", UsageType.CONSUMABLE, List.of(tierPriceOverride));
        planPhaseOverride = new DefaultPlanPhasePriceOverride("bullets-usage-in-arrear-evergreen", Currency.USD, null, BigDecimal.TEN, List.of(usagePriceOverride));
        usageAddOnEntitlementSpecifier = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("bullets-usage-in-arrear", PhaseType.EVERGREEN), null, null, null, List.of(planPhaseOverride));
        final LocalDate changeDate = today.plusMonths(1);
        aoSub.changePlanWithDate(usageAddOnEntitlementSpecifier, changeDate, Collections.emptyList(), callContext);

        //Move clock to 2024-12-14
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(changeDate);
        assertListenerStatus();

        //overridden plan name
        aoSub = subscriptionApi.getSubscriptionForEntitlementId(addOnEntitlementId, false, callContext);
        aoPlan = aoSub.getLastActivePlan();

        recordUsageData(aoSub.getId(), "t2", "bullets", clock.getUTCNow(), BigDecimal.valueOf(20L), callContext);

        //check invoice
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2024, 12, 14), new LocalDate(2025, 1, 14), InvoiceItemType.RECURRING, new BigDecimal("19.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2024, 12, 14), new LocalDate(2025, 1, 14), InvoiceItemType.RECURRING, new BigDecimal("10"))); // overridden recurring price
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2024, 12, 14), new LocalDate(2025, 1, 14), InvoiceItemType.USAGE, new BigDecimal("45"))); //20 units x 2.25 (overridden udage price) - test fails here
        dryRunInvoice = invoiceUserApi.triggerDryRunInvoiceGeneration(account.getId(), clock.getUTCToday().plusMonths(1), new TestDryRunArguments(DryRunType.TARGET_DATE), Collections.emptyList(), callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);
    }
}
