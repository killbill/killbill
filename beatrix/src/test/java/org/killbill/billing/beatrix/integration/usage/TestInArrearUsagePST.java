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
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.BaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.DefaultBaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
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

        //The recurring item has start date as 2024-01-31 (as per PST timezone)
        // while the usage item has start date as 2024-02-01 (as per UTC timezone).
        // So looks like the start date is not converted to PST for the usage invoice item.
        // This looks like a bug to me
        final List<ExpectedInvoiceItemCheck> toBeChecked =
                List.of(new ExpectedInvoiceItemCheck(new LocalDate(2024, 1, 31), new LocalDate(2024, 2, 29), InvoiceItemType.RECURRING, new BigDecimal("100")),
                        new ExpectedInvoiceItemCheck(new LocalDate(2024, 1, 31), new LocalDate(2024, 2, 29), InvoiceItemType.USAGE, BigDecimal.ZERO));
        invoiceChecker.checkInvoiceNoAudits(invoice, toBeChecked);

    }

}
