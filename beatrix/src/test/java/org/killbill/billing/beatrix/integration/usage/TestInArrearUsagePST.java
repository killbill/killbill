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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.BaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.DefaultBaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestInArrearUsagePST extends TestIntegrationBase {

    @Test(groups = "slow")
    public void testInArrearUsagePST() throws Exception {
        clock.setTime(new DateTime(2024, 2, 1, 13, 25));

        final AccountData accountData = getAccountData(31, DateTimeZone.forID("America/Los_Angeles"));
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final DateTime subStartDateTime = new DateTime(2024, 2, 1, 6, 30);

        final PlanPhaseSpecifier baseSpec = new PlanPhaseSpecifier("pistol-in-arrear-monthly-notrial");
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("bullets-usage-in-arrear");

        final List<EntitlementSpecifier> specifierList = List.of(new DefaultEntitlementSpecifier(baseSpec), new DefaultEntitlementSpecifier(addOnSpec));
        final BaseEntitlementWithAddOnsSpecifier cartSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(null, "key1", specifierList, subStartDateTime, subStartDateTime, false);
        final List<BaseEntitlementWithAddOnsSpecifier> entitlementWithAddOnsSpecifierList = List.of(cartSpecifier);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK,
                                      NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final List<UUID> allEntitlements = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), entitlementWithAddOnsSpecifierList, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Trigger invoice dry run
        final LocalDate targetDate = new LocalDate(2024, 02, 29);
        final DryRunArguments dryRunArgs = new TestDryRunArguments(DryRunType.TARGET_DATE);
        final Invoice invoice = invoiceUserApi.triggerDryRunInvoiceGeneration(account.getId(), targetDate, dryRunArgs, Collections.emptyList(), callContext);
        assertNotNull(invoice);
        assertNotNull(invoice.getInvoiceItems());
        assertEquals(invoice.getInvoiceItems().size(), 2);

        final List<ExpectedInvoiceItemCheck> toBeChecked =
                List.of(new ExpectedInvoiceItemCheck(new LocalDate(2024, 1, 31), new LocalDate(2024, 2, 29), InvoiceItemType.RECURRING, new BigDecimal("100")),
                        new ExpectedInvoiceItemCheck(new LocalDate(2024, 1, 31), new LocalDate(2024, 2, 29), InvoiceItemType.USAGE, BigDecimal.ZERO));
        invoiceChecker.checkInvoiceNoAudits(invoice, toBeChecked);
    }

    @Test(groups = "slow")
    public void testInArrearUsageCreateCancelUTC() throws Exception {
        DateTime today = new DateTime(2024, 2, 21, 6, 00); //2024-02-16T6:00 UTC=2024-02-15T10:30 PST (different days)
        clock.setTime(today);

        final AccountData accountData = getAccountData(21);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final PlanPhaseSpecifier baseSpec = new PlanPhaseSpecifier("pistol-in-arrear-monthly-notrial");
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("bullets-usage-in-arrear");

        final List<EntitlementSpecifier> specifierList = List.of(new DefaultEntitlementSpecifier(baseSpec), new DefaultEntitlementSpecifier(addOnSpec));
        final BaseEntitlementWithAddOnsSpecifier cartSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(null, "key1", specifierList, null, null, false);
        final List<BaseEntitlementWithAddOnsSpecifier> entitlementWithAddOnsSpecifierList = List.of(cartSpecifier);

        //create subscription at 6:05
        clock.setTime(today.plusMinutes(5));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final List<UUID> allEntitlements = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), entitlementWithAddOnsSpecifierList, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(allEntitlements.get(0), false, callContext);
        final Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(allEntitlements.get(1), false, callContext);

        //Record usage at 6:30
        clock.setTime(today.plusMinutes(30));
        recordUsageData(addOnEntitlement.getId(), "t1", "bullets", clock.getUTCNow(), BigDecimal.valueOf(85L), callContext);

        //cancel base at 6:40
        clock.setTime(today.plusMinutes(40));
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.CANCEL, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        baseEntitlement.cancelEntitlementWithPolicy(EntitlementActionPolicy.IMMEDIATE, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Invoice generated for the usage item
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2024, 2, 21), new LocalDate(2024, 2, 21), InvoiceItemType.USAGE, new BigDecimal("2.95")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("t1"), internalCallContext);

        //Trigger invoice dry run for next month - no invoice
        final LocalDate targetDate = new LocalDate(2024, 3, 21);
        final DryRunArguments dryRunArgs = new TestDryRunArguments(DryRunType.TARGET_DATE);
        try {
            final Invoice invoice = invoiceUserApi.triggerDryRunInvoiceGeneration(account.getId(), targetDate, dryRunArgs, Collections.emptyList(), callContext);
        }
        catch(InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.INVOICE_NOTHING_TO_DO.getCode());
        }

        //Move clock to 2024-03-21T6:40 - still no invoice
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addMonths(1);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testInArrearUsageCreateCancelPST() throws Exception { //PST and UTC on different days
        DateTime today = new DateTime(2024, 2, 21, 6, 00); //2024-02-16T6:00 UTC=2024-02-15T10:30 PST (different days)
        clock.setTime(today);

        DateTime referenceTime = new DateTime(2024, 2, 20, 9, 0);
        final AccountData accountData = getAccountData(20, DateTimeZone.forID("America/Los_Angeles"), referenceTime);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final PlanPhaseSpecifier baseSpec = new PlanPhaseSpecifier("pistol-in-arrear-monthly-notrial");
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("bullets-usage-in-arrear");

        final List<EntitlementSpecifier> specifierList = List.of(new DefaultEntitlementSpecifier(baseSpec), new DefaultEntitlementSpecifier(addOnSpec));
        final BaseEntitlementWithAddOnsSpecifier cartSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(null, "key1", specifierList, null, null, false);
        final List<BaseEntitlementWithAddOnsSpecifier> entitlementWithAddOnsSpecifierList = List.of(cartSpecifier);

        //create subscription at 6:05
        clock.setTime(today.plusMinutes(5));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final List<UUID> allEntitlements = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), entitlementWithAddOnsSpecifierList, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(allEntitlements.get(0), false, callContext);
        final Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(allEntitlements.get(1), false, callContext);

        //Record usage at 6:30
        clock.setTime(today.plusMinutes(30));
        recordUsageData(addOnEntitlement.getId(), "t1", "bullets", clock.getUTCNow(), BigDecimal.valueOf(85L), callContext);

        //cancel base at 6:40
        clock.setTime(today.plusMinutes(40));
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.CANCEL, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        baseEntitlement.cancelEntitlementWithPolicy(EntitlementActionPolicy.IMMEDIATE, Collections.emptyList(), callContext);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2024, 2, 20), new LocalDate(2024, 2, 20), InvoiceItemType.USAGE, new BigDecimal("2.95")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("t1"), internalCallContext);

        //Trigger invoice dry run for next month
        final LocalDate targetDate = new LocalDate(2024, 3, 20);
        final DryRunArguments dryRunArgs = new TestDryRunArguments(DryRunType.TARGET_DATE);
        try {
            final Invoice invoice = invoiceUserApi.triggerDryRunInvoiceGeneration(account.getId(), targetDate, dryRunArgs, Collections.emptyList(), callContext);
        }
        catch(InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.INVOICE_NOTHING_TO_DO.getCode());
        }

        //Move to next month - still no invoice
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addMonths(1);
        assertListenerStatus();
     }
}
