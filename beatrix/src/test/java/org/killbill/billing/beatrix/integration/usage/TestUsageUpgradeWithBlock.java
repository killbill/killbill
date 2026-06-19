/*
 * Copyright 2020-2026 Equinix, Inc
 * Copyright 2014-2026 The Billing Project, LLC
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.annotations.Test;


/**
 * Integration tests for usage-based plan upgrade scenarios using the VoltMetered catalog.
 *
 * Catalog plans:
 *   volt-usage-low      (ULow):  pure consumable usage IN_ARREAR, $1.00/unit
 *   volt-usage-high     (UHigh): pure consumable usage IN_ARREAR, $3.00/unit
 *   volt-recurring-low  (URLow): $20/month IN_ADVANCE recurring + $1.00/unit usage IN_ARREAR
 *   volt-recurring-high (URHigh):$20/month IN_ADVANCE recurring + $3.00/unit usage IN_ARREAR
 */
public class TestUsageUpgradeWithBlock extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testUsageUpgradeWithBlock");
        return super.getConfigSource(null, allExtraProperties);
    }

    /**
     * Scenario 1: Mid-period plan upgrade (volt-usage-low → volt-usage-high) using a billing
     * block to prevent an immediate partial invoice at the time of the plan change.
     *
     * The trick is to block billing retroactively from the subscription start date before the
     * plan change, then unblock at the same date after the change. Because both the block and
     * the unblock have the same effective date the billing period is never actually suppressed,
     * and the single invoice at the end of the billing cycle correctly reflects two line items —
     * one for each plan at the correct per-unit rate.
     *
     * Timeline:
     *  2026-05-28  Create subscription on volt-usage-low (ULow, $1.00/unit)
     *  2026-05-28  Record tracking-1: 10 units
     *  2026-06-01  Record tracking-2:  5 units
     *  2026-06-05  Record tracking-3:  8 units
     *  2026-06-10  Record tracking-4:  3 units
     *  2026-06-15  Record tracking-5:  7 units
     *  2026-06-18  Record tracking-6:  4 units  (on the day of the plan change → billed at UHigh)
     *
     *
     *  2026-06-18  Move clock; add billing block (effective 2026-05-28);
     *              change plan to volt-usage-high (no invoice — billing blocked)
     *
     *  2026-06-28  Clock reaches billing date; billing still blocked → NULL_INVOICE
     *              Unblock billing (effective 2026-05-28) → single invoice:
     *                  ULow  2026-05-28→2026-06-18: 33 units × $1.00 = $33.00
     *                  UHigh 2026-06-18→2026-06-28:  4 units × $3.00 = $12.00
     */
    @Test(groups = "slow")
    public void testUsagePlanUpgradeWithBillingBlock() throws Exception {

        final DryRunArguments dryRunTargetDateArg = new TestDryRunArguments(DryRunType.TARGET_DATE);

        // -----------------------------------------------------------------------
        // Step 1: Create account (BCD=28) and subscription on volt-usage-low
        // -----------------------------------------------------------------------
        clock.setDay(new LocalDate(2026, 5, 28));

        final AccountData accountData = getAccountData(28);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // volt-usage-low is pure usage IN_ARREAR: no invoice generated at subscription creation
        final PlanPhaseSpecifier lowSpec = new PlanPhaseSpecifier("volt-usage-low");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(
                account.getId(),
                new DefaultEntitlementSpecifier(lowSpec),
                "voltBundle",
                null, null,
                false, true,
                Collections.emptyList(), callContext);
        final Entitlement bpSubscription = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertListenerStatus();

        // -----------------------------------------------------------------------
        // Step 2: Record 6 usage points spread across the billing period
        //         (2026-05-28 → 2026-06-28, BCD=28)
        //
        //   Points on or before 2026-06-17 → billed under volt-usage-low  ($1.00/unit)
        //   Point  on           2026-06-18 → billed under volt-usage-high ($3.00/unit)
        // -----------------------------------------------------------------------
        recordUsageData(bpSubscription.getId(), "tracking-1", "unit", bpSubscription.getEffectiveStartDate().plusSeconds(1), BigDecimal.valueOf(10L), callContext);
        recordUsageData(bpSubscription.getId(), "tracking-2", "unit", new LocalDate(2026, 6, 1),  BigDecimal.valueOf(5L),  callContext);
        recordUsageData(bpSubscription.getId(), "tracking-3", "unit", new LocalDate(2026, 6, 5),  BigDecimal.valueOf(8L),  callContext);
        recordUsageData(bpSubscription.getId(), "tracking-4", "unit", new LocalDate(2026, 6, 10), BigDecimal.valueOf(3L),  callContext);
        recordUsageData(bpSubscription.getId(), "tracking-5", "unit", new LocalDate(2026, 6, 15), BigDecimal.valueOf(7L),  callContext);
        recordUsageData(bpSubscription.getId(), "tracking-6", "unit", new LocalDate(2026, 6, 18), BigDecimal.valueOf(4L),  callContext);


        // -----------------------------------------------------------------------
        // Step 3: Advance clock to 2026-06-18 (plan-change day).
        //         Still within the same billing period — no invoice events.
        // -----------------------------------------------------------------------
        clock.setDay(new LocalDate(2026, 6, 18));
        assertListenerStatus();

        // -----------------------------------------------------------------------
        // Step 4: Block billing retroactively from the subscription start date.
        //
        //         Using an effective date of the subscription start date ensures
        //         that the entire elapsed period is covered by the block, so that
        //         the plan change in Step 5 does not trigger a partial invoice.
        //
        //         Since no invoice has yet been generated for this period (usage is
        //         IN_ARREAR and the billing date 2026-06-28 has not been reached),
        //         no repair or adjustment invoice is produced — only a NULL_INVOICE
        //         indicating the invoicer ran but found nothing to charge.
        // -----------------------------------------------------------------------
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final BlockingState blockBilling = new DefaultBlockingState(
                bpSubscription.getId(),
                BlockingStateType.SUBSCRIPTION,
                "BLOCK_BILLING",
                "test-service",
                false, false, true,   // blockChange=false, blockEntitlement=false, blockBilling=true
                null);
        subscriptionApi.addBlockingState(blockBilling, bpSubscription.getEffectiveStartDate(), Collections.emptyList(), callContext);
        assertListenerStatus();

        // -----------------------------------------------------------------------
        // Step 5: Change plan to volt-usage-high on 2026-06-18.
        //         Billing is blocked → no invoice generated (NULL_INVOICE).
        // -----------------------------------------------------------------------
        final Entitlement refreshedSub = entitlementApi.getEntitlementForId(bpSubscription.getId(), false, callContext);
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.NULL_INVOICE);
        refreshedSub.changePlanWithDate(
                new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("volt-usage-high")),
                new LocalDate(2026, 6, 18),
                Collections.emptyList(),
                callContext);
        assertListenerStatus();


        // -----------------------------------------------------------------------
        // Step 6: Advance clock to 2026-06-28 (end of first billing period).
        // -----------------------------------------------------------------------
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2026, 6, 28));
        assertListenerStatus();

        // -----------------------------------------------------------------------
        // Step 7: Unblock billing from the same date (subscription start date).
        //
        // -----------------------------------------------------------------------
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final BlockingState unblockBilling = new DefaultBlockingState(
                bpSubscription.getId(),
                BlockingStateType.SUBSCRIPTION,
                "UNBLOCK_BILLING",
                "test-service",
                false, false, false,  // blockBilling=false → unblock
                null);
        subscriptionApi.addBlockingState(unblockBilling, bpSubscription.getEffectiveStartDate(), Collections.emptyList(), callContext);
        assertListenerStatus();


        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2026, 5, 28), new LocalDate(2026, 6, 18),
                                                                 InvoiceItemType.USAGE, new BigDecimal("33.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2026, 6, 18), new LocalDate(2026, 6, 28),
                                                                 InvoiceItemType.USAGE, new BigDecimal("12.00")));
    }

    /**
     * Scenario 2: Mid-period plan upgrade (volt-recurring-low → volt-recurring-high) using a billing
     * block to prevent an immediate partial invoice at the time of the plan change.
     *
     * Both plans have an IN_ADVANCE recurring charge ($20/month) plus IN_ARREAR usage.  The same
     * block/unblock trick from Scenario 1 is used: billing is blocked retroactively from the
     * subscription start date before the plan change, then unblocked from the same date after.
     *
     * Because the block is retroactive, the original $20 recurring invoice is voided by a repair
     * invoice (REPAIR_ADJ -$20 + CBA_ADJ +$20) when the block is applied.  When billing is
     * unblocked on the billing date, the invoicer re-bills the entire period with the correct
     * prorated recurring amounts for each plan and the full IN_ARREAR usage, plus the next period's
     * IN_ADVANCE recurring charge; the $20 credit is consumed by a CBA_ADJ on the same invoice.
     *
     * BCD=1 with a 30-day period (Jun 1 → Jul 1) and the plan change on Jun 16 gives a clean
     * 15/15-day proration: $20 × 15/30 = $10.00 per plan.
     *
     * Timeline:
     *  2026-06-01  Create subscription on volt-recurring-low ($20/mo + $1.00/unit)
     *              → Invoice 1: RECURRING $20.00 (Jun 1→Jul 1)
     *  2026-06-01  Record tracking-1: 10 units
     *  2026-06-05  Record tracking-2:  5 units
     *  2026-06-10  Record tracking-3:  8 units
     *  2026-06-16  Record tracking-4:  3 units  (plan change date → billed at URHigh)
     *  2026-06-22  Record tracking-5:  4 units  (billed at URHigh)
     *
     *  2026-06-16  Move clock; add billing block (effective 2026-06-01)
     *              → Invoice 2: REPAIR_ADJ -$20.00 + CBA_ADJ +$20.00
     *              Change plan to volt-recurring-high (no invoice — billing blocked)
     *
     *  2026-07-01  Clock reaches billing date (billing blocked → NULL_INVOICE);
     *              Unblock billing (effective 2026-06-01)
     *              → Invoice 3:
     *                  RECURRING low  $10.00 (Jun 1→Jun 16, prorated 15/30)
     *                  RECURRING high $10.00 (Jun 16→Jul 1, prorated 15/30)
     *                  USAGE low      $23.00 (10+5+8 units × $1.00)
     *                  USAGE high     $21.00 (3+4 units × $3.00)
     *                  RECURRING high $20.00 (Jul 1→Aug 1, next period IN_ADVANCE)
     *                  CBA_ADJ       -$20.00 (credit from Invoice 2 consumed)
     *                  Net payment:   $64.00
     */
    @Test(groups = "slow")
    public void testRecurringPlanUpgradeWithBillingBlock() throws Exception {

        // -----------------------------------------------------------------------
        // Step 1: Create account (BCD=1) and subscription on volt-recurring-low.
        //         The plan is IN_ADVANCE recurring: an invoice for $20 is issued immediately.
        // -----------------------------------------------------------------------
        clock.setDay(new LocalDate(2026, 6, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final PlanPhaseSpecifier lowSpec = new PlanPhaseSpecifier("volt-recurring-low");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(
                account.getId(),
                new DefaultEntitlementSpecifier(lowSpec),
                "voltBundle",
                null, null,
                false, true,
                Collections.emptyList(), callContext);
        final Entitlement bpSubscription = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2026, 6, 1), new LocalDate(2026, 7, 1),
                                                                 InvoiceItemType.RECURRING, new BigDecimal("20.00")));

        // -----------------------------------------------------------------------
        // Step 2: Record usage data across the billing period (Jun 1 → Jul 1, BCD=1).
        //
        //   Tracking 1-3 fall before Jun 16 → billed under volt-recurring-low  ($1.00/unit)
        //   Tracking 4-5 fall on/after Jun 16 → billed under volt-recurring-high ($3.00/unit)
        //
        //   Low usage:  10 + 5 + 8 = 23 units × $1.00 = $23.00
        //   High usage:  3 + 4     =  7 units × $3.00 = $21.00
        // -----------------------------------------------------------------------
        recordUsageData(bpSubscription.getId(), "tracking-1", "unit", bpSubscription.getEffectiveStartDate().plusSeconds(1),  BigDecimal.valueOf(10L), callContext);
        recordUsageData(bpSubscription.getId(), "tracking-2", "unit", new LocalDate(2026, 6, 5),  BigDecimal.valueOf(5L),  callContext);
        recordUsageData(bpSubscription.getId(), "tracking-3", "unit", new LocalDate(2026, 6, 10), BigDecimal.valueOf(8L),  callContext);
        recordUsageData(bpSubscription.getId(), "tracking-4", "unit", new LocalDate(2026, 6, 16), BigDecimal.valueOf(3L),  callContext);
        recordUsageData(bpSubscription.getId(), "tracking-5", "unit", new LocalDate(2026, 6, 22), BigDecimal.valueOf(4L),  callContext);

        // -----------------------------------------------------------------------
        // Step 3: Advance clock to 2026-06-16 (plan-change day).
        //         Still within the same billing period — no invoice events.
        // -----------------------------------------------------------------------
        clock.setDay(new LocalDate(2026, 6, 16));
        assertListenerStatus();

        // -----------------------------------------------------------------------
        // Step 4: Block billing retroactively from the subscription start date.
        //
        //         Because Invoice 1 ($20 recurring) was already generated IN_ADVANCE,
        //         the retroactive block repairs it: REPAIR_ADJ -$20 + CBA_ADJ +$20.
        //         No payment event is emitted for a repair (net-zero) invoice.
        // -----------------------------------------------------------------------
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE);
        final BlockingState blockBilling = new DefaultBlockingState(
                bpSubscription.getId(),
                BlockingStateType.SUBSCRIPTION,
                "BLOCK_BILLING",
                "test-service",
                false, false, true,   // blockChange=false, blockEntitlement=false, blockBilling=true
                null);
        subscriptionApi.addBlockingState(blockBilling, bpSubscription.getEffectiveStartDate(), Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2026, 6, 1), new LocalDate(2026, 7, 1),
                                                                 InvoiceItemType.REPAIR_ADJ, new BigDecimal("-20.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2026, 6, 16), new LocalDate(2026, 6, 16),
                                                                 InvoiceItemType.CBA_ADJ, new BigDecimal("20.00")));

        // -----------------------------------------------------------------------
        // Step 5: Change plan to volt-recurring-high on 2026-06-16.
        //         Billing is blocked → no invoice generated (NULL_INVOICE).
        // -----------------------------------------------------------------------
        final Entitlement refreshedSub = entitlementApi.getEntitlementForId(bpSubscription.getId(), false, callContext);
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.NULL_INVOICE);
        refreshedSub.changePlanWithDate(
                new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("volt-recurring-high")),
                new LocalDate(2026, 6, 16),
                Collections.emptyList(),
                callContext);
        assertListenerStatus();

        // -----------------------------------------------------------------------
        // Step 6: Advance clock to 2026-07-01 (billing date).
        //         Billing is still blocked → NULL_INVOICE.
        // -----------------------------------------------------------------------
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2026, 7, 1));
        assertListenerStatus();

        // -----------------------------------------------------------------------
        // Step 7: Unblock billing from the subscription start date (2026-06-01).
        //
        //         Kill Bill re-invoices the entire period from Jun 1:
        //           - Prorated RECURRING for volt-recurring-low  (Jun 1→Jun 16, 15/30 days): $10.00
        //           - Prorated RECURRING for volt-recurring-high (Jun 16→Jul 1, 15/30 days): $10.00
        //           - USAGE for volt-recurring-low  (Jun 1→Jun 16):  23 units × $1.00 = $23.00
        //           - USAGE for volt-recurring-high (Jun 16→Jul 1):   7 units × $3.00 = $21.00
        //           - RECURRING for volt-recurring-high next period (Jul 1→Aug 1) IN_ADVANCE: $20.00
        //           - CBA_ADJ consuming the $20 credit from Invoice 2: -$20.00
        //           Net payment: $10 + $10 + $23 + $21 + $20 - $20 = $64.00
        // -----------------------------------------------------------------------
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final BlockingState unblockBilling = new DefaultBlockingState(
                bpSubscription.getId(),
                BlockingStateType.SUBSCRIPTION,
                "UNBLOCK_BILLING",
                "test-service",
                false, false, false,  // blockBilling=false → unblock
                null);
        subscriptionApi.addBlockingState(unblockBilling, bpSubscription.getEffectiveStartDate(), Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2026, 6, 1),  new LocalDate(2026, 6, 16),
                                                                 InvoiceItemType.RECURRING, new BigDecimal("10.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2026, 6, 16), new LocalDate(2026, 7, 1),
                                                                 InvoiceItemType.RECURRING, new BigDecimal("10.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2026, 6, 1),  new LocalDate(2026, 6, 16),
                                                                 InvoiceItemType.USAGE, new BigDecimal("23.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2026, 6, 16), new LocalDate(2026, 7, 1),
                                                                 InvoiceItemType.USAGE, new BigDecimal("21.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2026, 7, 1),  new LocalDate(2026, 8, 1),
                                                                 InvoiceItemType.RECURRING, new BigDecimal("20.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2026, 7, 1),  new LocalDate(2026, 7, 1),
                                                                 InvoiceItemType.CBA_ADJ, new BigDecimal("-20.00")));
    }
}
