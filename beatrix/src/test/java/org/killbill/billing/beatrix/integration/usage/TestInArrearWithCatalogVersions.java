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

package org.killbill.billing.beatrix.integration.usage;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class TestInArrearWithCatalogVersions extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource() {
        return super.getConfigSource(null, ImmutableMap.of("org.killbill.catalog.uri", "catalogs/testInArrearWithCatalogVersions",
                                                           "org.killbill.invoice.readMaxRawUsagePreviousPeriod", "0"));
    }

    @Test(groups = "slow")
    public void testWithChangeAcrossCatalogs() throws Exception {
        // 30 days month
        clock.setDay(new LocalDate(2016, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("electricity-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 4, 1), 143L, callContext);
        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 4, 18), 57L, callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 4, 1), new LocalDate(2016, 5, 1), InvoiceItemType.USAGE, new BigDecimal("300.00")));

        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 5, 2), 100L, callContext); // -> Uses v1 : $150

        // Catalog change with new price on 2016-05-08
        // Schedule CHANGE_PLAN on 2016-05-09
        final Entitlement bp = entitlementApi.getEntitlementForId(entitlementId, callContext);

        bp.changePlanWithDate(new DefaultEntitlementSpecifier(spec), new LocalDate("2016-05-09"), null, callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(8);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 1), new LocalDate(2016, 5, 9), InvoiceItemType.USAGE, new BigDecimal("150.00")));

        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 5, 10), 100L, callContext); // -> Uses v2 : $250

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(23);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 9), new LocalDate(2016, 6, 1), InvoiceItemType.USAGE, new BigDecimal("250.00")));

    }

    @Test(groups = "slow")
    public void testWithChangeWithinCatalog() throws Exception {
        // 30 days month
        clock.setDay(new LocalDate(2016, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier("electricity-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec1), null, null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 4, 1), 143L, callContext);
        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 4, 18), 57L, callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 4, 1), new LocalDate(2016, 5, 1), InvoiceItemType.USAGE, new BigDecimal("300.00")));

        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 5, 2), 100L, callContext); // -> Uses v1 : $150

        final Entitlement bp = entitlementApi.getEntitlementForId(entitlementId, callContext);

        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier("electricity-monthly-special");

        bp.changePlanWithDate(new DefaultEntitlementSpecifier(spec2), new LocalDate("2016-05-09"), null, callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(8);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 1), new LocalDate(2016, 5, 9), InvoiceItemType.USAGE, new BigDecimal("150.00")));

        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 5, 10), 100L, callContext); // -> Uses special plan : $100

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(23);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 9), new LocalDate(2016, 6, 1), InvoiceItemType.USAGE, new BigDecimal("100.00")));

    }

    // We are not using catalog versions in this test but testing the overridden value of 'readMaxRawUsagePreviousPeriod = 0'
    @Test(groups = "slow")
    public void testWithRemovedData() throws Exception {
        // 30 days month
        clock.setDay(new LocalDate(2016, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("electricity-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 4, 5), 1L, callContext);
        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 4, 5), 99L, callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 4, 1), new LocalDate(2016, 5, 1), InvoiceItemType.USAGE, new BigDecimal("150.00")));

        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 5, 5), 100L, callContext);
        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 5, 8), 900L, callContext);
        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 5, 9), 200L, callContext); // Move to tier 2.

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 1), new LocalDate(2016, 6, 1), InvoiceItemType.USAGE, new BigDecimal("1900.00")));

        // Remove Usage data from period 2016-5-1 -> 2016-6-1 and verify there is no issue (readMaxRawUsagePreviousPeriod = 0 => We ignore any past invoiced period)
        // Full deletion on the second tier
        removeUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 5, 9));

        //
        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 6, 5), 100L, callContext);
        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 6, 8), 900L, callContext);
        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 6, 12), 50L, callContext); // Move to tier 2.
        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 6, 13), 50L, callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 1), new LocalDate(2016, 7, 1), InvoiceItemType.USAGE, new BigDecimal("1700.00"))));

        // Remove Usage data from period 2016-6-1 -> 2016-7-1 and verify there is no issue (readMaxRawUsagePreviousPeriod = 0 => We ignore any past invoiced period)
        // Partial deletion on the second tier
        removeUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 6, 13));

        // No usage this MONTH
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        // Check invoicing occurred and - i.e system did not detect deletion of passed invoiced data.
        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 7, 1), new LocalDate(2016, 8, 1), InvoiceItemType.USAGE, BigDecimal.ZERO));
    }

    @Test(groups = "slow")
    public void testWithSubscriptionBCD() throws Exception {
        // 30 days month
        clock.setDay(new LocalDate(2016, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("electricity-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 4, 5), 1L, callContext);
        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 4, 5), 99L, callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 4, 1), new LocalDate(2016, 5, 1), InvoiceItemType.USAGE, new BigDecimal("150.00")));

        final Entitlement bp = entitlementApi.getEntitlementForId(entitlementId, callContext);

        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 5, 2), 100L, callContext);

        // Update subscription BCD
        bp.updateBCD(9, new LocalDate(2016, 5, 9), callContext);

        recordUsageData(entitlementId, "kilowatt-hour", new LocalDate(2016, 5, 10), 10L, callContext);

        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(8);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 1), new LocalDate(2016, 5, 9), InvoiceItemType.USAGE, new BigDecimal("150.00")));

        // Original notification before we change BCD
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addDays(23);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(8);
        assertListenerStatus();

        // NOTE: Is using the new version of the catalog Utility-v2 (effectiveDateForExistingSubscriptions = 2016-05-08T00:00:00+00:00) what we want or is this a bug?
        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 5, 9), new LocalDate(2016, 6, 9), InvoiceItemType.USAGE, new BigDecimal("25.00")));

    }
}