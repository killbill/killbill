/*
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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
import java.util.HashMap;
import java.util.Map;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class TestUnknownUsageUnits extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.putAll(DEFAULT_BEATRIX_PROPERTIES);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testUnknownUsageUnits");
        return getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1275")
    public void testWithUnknownUsage() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Catalog-v1.xml
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Server", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        // Record known usage for April
        recordUsageData(bpSubscription.getId(), "tracking-1", "server-hourly-type-1", new LocalDate(2012, 4, 1), 99L, callContext);
        recordUsageData(bpSubscription.getId(), "tracking-2", "bandwidth-type-1", new LocalDate(2012, 4, 15), 100L, callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("0")),
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("99")),
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("10")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("tracking-1", "tracking-2"), internalCallContext);

        Assert.assertFalse(parkedAccountsManager.isParked(internalCallContext));

        // Enable strict mode
        invoiceConfig.setShouldParkAccountsWithUnknownUsage(true);

        // Record known consumable usage but unknown capacity usage for May
        recordUsageData(bpSubscription.getId(), "tracking-3", "server-hourly-type-1", new LocalDate(2012, 5, 1), 99L, callContext);
        recordUsageData(bpSubscription.getId(), "tracking-4", "bandwidth-type-2", new LocalDate(2012, 5, 15), 100L, callContext);

        busHandler.pushExpectedEvents(NextEvent.TAG);
        clock.addMonths(1);
        assertListenerStatus();

        // Account is parked because of the unknown usage
        Assert.assertTrue(parkedAccountsManager.isParked(internalCallContext));

        // Trigger a change plan on the same plan, to force the new catalog version (Catalog-v2.xml)
        busHandler.pushExpectedEvents(NextEvent.CHANGE);
        bpSubscription.changePlanWithDate(new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("server-monthly")), new LocalDate("2012-05-01"), null, callContext);
        assertListenerStatus();

        // Trigger an invoice generation
        busHandler.pushExpectedEvents(NextEvent.TAG, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), clock.getUTCToday(), callContext);
        assertListenerStatus();

        // Now unparked
        Assert.assertFalse(parkedAccountsManager.isParked(internalCallContext));

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("0")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.USAGE, new BigDecimal("99")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.USAGE, new BigDecimal("0")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.USAGE, new BigDecimal("12")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("tracking-3", "tracking-4"), internalCallContext);

        // Record known capacity usage but unknown consumable usage for June
        recordUsageData(bpSubscription.getId(), "tracking-5", "server-hourly-type-2", new LocalDate(2012, 6, 1), 99L, callContext);
        recordUsageData(bpSubscription.getId(), "tracking-6", "bandwidth-type-1", new LocalDate(2012, 6, 15), 100L, callContext);

        busHandler.pushExpectedEvents(NextEvent.TAG);
        clock.addMonths(1);
        assertListenerStatus();

        // Account is parked because of the unknown usage
        Assert.assertTrue(parkedAccountsManager.isParked(internalCallContext));

        // Trigger a change plan on the same plan, to force the new catalog version (Catalog-v3.xml)
        busHandler.pushExpectedEvents(NextEvent.CHANGE);
        bpSubscription.changePlanWithDate(new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("server-monthly")), new LocalDate("2012-06-01"), null, callContext);
        assertListenerStatus();

        // Trigger an invoice generation
        busHandler.pushExpectedEvents(NextEvent.TAG, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), clock.getUTCToday(), callContext);
        assertListenerStatus();

        // Now unparked
        Assert.assertFalse(parkedAccountsManager.isParked(internalCallContext));

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("0")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.USAGE, new BigDecimal("198")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.USAGE, new BigDecimal("0")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.USAGE, new BigDecimal("0")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.USAGE, new BigDecimal("10")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("tracking-5", "tracking-6"), internalCallContext);

        // Disable strict mode
        invoiceConfig.setShouldParkAccountsWithUnknownUsage(false);

        // Record unknown usage for July
        recordUsageData(bpSubscription.getId(), "tracking-7", "server-hourly-type-3", new LocalDate(2012, 4, 1), 99L, callContext);
        recordUsageData(bpSubscription.getId(), "tracking-8", "bandwidth-type-3", new LocalDate(2012, 4, 15), 100L, callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 1), new LocalDate(2012, 8, 1), InvoiceItemType.RECURRING, new BigDecimal("0")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 1), new LocalDate(2012, 8, 1), InvoiceItemType.USAGE, new BigDecimal("0")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 1), new LocalDate(2012, 8, 1), InvoiceItemType.USAGE, new BigDecimal("0")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 1), new LocalDate(2012, 8, 1), InvoiceItemType.USAGE, new BigDecimal("0")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 1), new LocalDate(2012, 8, 1), InvoiceItemType.USAGE, new BigDecimal("0")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of(), internalCallContext);

        Assert.assertFalse(parkedAccountsManager.isParked(internalCallContext));

        // Re-enable strict mode
        invoiceConfig.setShouldParkAccountsWithUnknownUsage(true);

        // Trigger a change plan on the same plan, to force the new catalog version (Catalog-v4.xml)
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.NULL_INVOICE);
        bpSubscription.changePlanWithDate(new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("server-monthly")), new LocalDate("2012-08-01"), null, callContext);
        assertListenerStatus();

        // Record unknown usage for August (the unit has been retired)
        recordUsageData(bpSubscription.getId(), "tracking-9", "server-hourly-type-1", new LocalDate(2012, 8, 1), 99L, callContext);

        busHandler.pushExpectedEvents(NextEvent.TAG);
        clock.addMonths(1);
        assertListenerStatus();

        // Account is parked because of the unknown usage
        Assert.assertTrue(parkedAccountsManager.isParked(internalCallContext));

        // Record retroactively additional known usage for August
        recordUsageData(bpSubscription.getId(), "tracking-10", "server-hourly-type-2", new LocalDate(2012, 8, 1), 99L, callContext);
        recordUsageData(bpSubscription.getId(), "tracking-11", "bandwidth-type-2", new LocalDate(2012, 8, 15), 100L, callContext);

        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        entitlementApi.pause(bpSubscription.getBundleId(), new LocalDate(2012, 8, 1), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        entitlementApi.resume(bpSubscription.getBundleId(), new LocalDate(2012, 8, 2), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Disable strict mode
        invoiceConfig.setShouldParkAccountsWithUnknownUsage(false);

        // Trigger an invoice generation
        busHandler.pushExpectedEvents(NextEvent.TAG, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), clock.getUTCToday(), callContext);
        assertListenerStatus();

        Assert.assertFalse(parkedAccountsManager.isParked(internalCallContext));

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 8, 2), new LocalDate(2012, 9, 1), InvoiceItemType.RECURRING, new BigDecimal("0")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 8, 2), new LocalDate(2012, 9, 1), InvoiceItemType.USAGE, new BigDecimal("12")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 8, 2), new LocalDate(2012, 9, 1), InvoiceItemType.USAGE, new BigDecimal("0")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 8, 2), new LocalDate(2012, 9, 1), InvoiceItemType.USAGE, new BigDecimal("0")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("tracking-11"), internalCallContext);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/pull/1279")
    public void testWithUnknownUsageAndSeveralPeriods() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        Assert.assertFalse(parkedAccountsManager.isParked(internalCallContext));

        // Enable strict mode
        invoiceConfig.setShouldParkAccountsWithUnknownUsage(true);

        // Catalog-v1.xml
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Server", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        // Record known usage for April
        recordUsageData(bpSubscription.getId(), "tracking-1", "server-hourly-type-1", new LocalDate(2012, 4, 1), 99L, callContext);
        recordUsageData(bpSubscription.getId(), "tracking-2", "bandwidth-type-1", new LocalDate(2012, 4, 15), 100L, callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("0")),
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("99")),
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("10")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("tracking-1", "tracking-2"), internalCallContext);

        // Record known usage for May
        recordUsageData(bpSubscription.getId(), "tracking-3", "server-hourly-type-1", new LocalDate(2012, 5, 1), 99L, callContext);
        recordUsageData(bpSubscription.getId(), "tracking-4", "bandwidth-type-1", new LocalDate(2012, 5, 15), 100L, callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("0")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.USAGE, new BigDecimal("99")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.USAGE, new BigDecimal("10")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("tracking-3", "tracking-4"), internalCallContext);

        // Record known usage for June
        recordUsageData(bpSubscription.getId(), "tracking-5", "server-hourly-type-1", new LocalDate(2012, 6, 1), 99L, callContext);
        recordUsageData(bpSubscription.getId(), "tracking-6", "bandwidth-type-1", new LocalDate(2012, 6, 15), 100L, callContext);

        // Trigger a future change plan on the same plan, to force the new catalog version at the next billing cycle (Catalog-v4.xml)
        bpSubscription.changePlanWithDate(new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("server-monthly")), new LocalDate("2012-07-01"), null, callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.CHANGE, NextEvent.NULL_INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        // Check that we still invoiced for server-hourly-type-1 for June
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("0")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.USAGE, new BigDecimal("99")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.USAGE, new BigDecimal("10")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("tracking-5", "tracking-6"), internalCallContext);

        // Record known and unknown usage for July (server-hourly-type-1 doesn't exist anymore)
        recordUsageData(bpSubscription.getId(), "tracking-7", "server-hourly-type-1", new LocalDate(2012, 7, 1), 99L, callContext);
        recordUsageData(bpSubscription.getId(), "tracking-8", "bandwidth-type-1", new LocalDate(2012, 7, 15), 100L, callContext);

        busHandler.pushExpectedEvents(NextEvent.TAG);
        clock.addMonths(1);
        assertListenerStatus();

        // Account is parked because of the unknown usage
        Assert.assertTrue(parkedAccountsManager.isParked(internalCallContext));
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/pull/1279")
    public void testTrackingIdsWithUnknownUsage() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Catalog-v1.xml
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Server", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        // Record known and unknown usage for April
        recordUsageData(bpSubscription.getId(), "tracking-1", "server-hourly-type-1", new LocalDate(2012, 4, 1), 99L, callContext);
        recordUsageData(bpSubscription.getId(), "tracking-2", "bandwidth-type-1", new LocalDate(2012, 4, 15), 100L, callContext);
        recordUsageData(bpSubscription.getId(), "tracking-3", "server-hourly-type-2", new LocalDate(2012, 4, 20), 100L, callContext);

        // Strict mode if off by default
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        final Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                               new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("0")),
                                                               new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("99")),
                                                               new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("10")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("tracking-1", "tracking-2"), internalCallContext);
    }
}
