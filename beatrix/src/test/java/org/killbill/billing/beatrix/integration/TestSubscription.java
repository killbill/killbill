/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.BaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.DefaultBaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.entitlement.api.SubscriptionEvent;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestSubscription extends TestIntegrationBase {

    @Test(groups = "slow")
    public void testForcePolicy() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        final LocalDate today = new LocalDate(2012, 4, 1);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final String productName = "Shotgun";

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(40);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);
        List<ExpectedInvoiceItemCheck> toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        //
        // FORCE AN IMMEDIATE CHANGE OF THE BILLING PERIOD
        //
        toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 11), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("169.32")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 11), new LocalDate(2013, 5, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2334.20")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 11), new LocalDate(2012, 5, 11), InvoiceItemType.CBA_ADJ, new BigDecimal("2164.88"), false /* Issue with test where created date for context is wrong*/));

        final TestDryRunArguments dryRun = new TestDryRunArguments(DryRunType.SUBSCRIPTION_ACTION, productName, ProductCategory.BASE, BillingPeriod.MONTHLY, null, null,
                                                                   SubscriptionEventType.CHANGE, bpEntitlement.getId(), bpEntitlement.getBundleId(), null, BillingActionPolicy.IMMEDIATE);
        final Invoice dryRunInvoice = invoiceUserApi.triggerDryRunInvoiceGeneration(account.getId(), clock.getUTCToday(), dryRun, Collections.emptyList(), callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, toBeChecked);

        changeEntitlementAndCheckForCompletion(bpEntitlement, productName, BillingPeriod.MONTHLY, BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.INVOICE);

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 3);
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        //
        // FORCE ANOTHER CHANGE
        //
        changeEntitlementAndCheckForCompletion(bpEntitlement, productName, BillingPeriod.ANNUAL, BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.INVOICE);

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 4);

        toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 11), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("169.32")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 11), new LocalDate(2013, 5, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2334.20")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 11), new LocalDate(2012, 5, 11), InvoiceItemType.CBA_ADJ, new BigDecimal("2164.88")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 11), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2334.20")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 11), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-169.32")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 11), new LocalDate(2012, 5, 11), InvoiceItemType.CBA_ADJ, new BigDecimal("-2164.88")));
        invoiceChecker.checkInvoice(invoices.get(3).getId(), callContext, toBeChecked);

        checkNoMoreInvoiceToGenerate(account);

    }

    @Test(groups = "slow")
    public void testChangeOfPlan() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        final LocalDate today = new LocalDate(2012, 4, 1);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final String productName = "Shotgun";

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(40);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);
        List<ExpectedInvoiceItemCheck> toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        //
        // FORCE AN IMMEDIATE CHANGE TO TEST THE CHANGE_OF_PLAN ALIGNMENT
        // (Note that, the catalog is configured to use  CHANGE_OF_PLAN when moving to that plan and Not CHANGE_OF_PRICELIST which has not been implement;
        // this is a bit misleading since we are changing pricelist, but in that case pricelist change has no effect)
        changeEntitlementAndCheckForCompletion(bpEntitlement, "Assault-Rifle", BillingPeriod.ANNUAL, "rescue", BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 3);
        toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 11), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("5835.57")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 11), new LocalDate(2013, 5, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2334.20")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow")
    public void testCreateMultipleSubscriptionsWithAddOns() throws Exception {
        final LocalDate initialDate = new LocalDate(2015, 10, 1);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier baseSpec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final PlanPhaseSpecifier addOnSpec1 = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final PlanPhaseSpecifier addOnSpec2 = new PlanPhaseSpecifier("Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        final String externalKeyA = "baseExternalKeyAAA";
        final EntitlementSpecifier baseEntitlementSpecifier = new DefaultEntitlementSpecifier(baseSpec);
        final EntitlementSpecifier addOnEntitlementSpecifier1 = new DefaultEntitlementSpecifier(addOnSpec1);
        final EntitlementSpecifier addOnEntitlementSpecifier2 = new DefaultEntitlementSpecifier(addOnSpec2);

        final List<EntitlementSpecifier> specifierListA = new ArrayList<EntitlementSpecifier>();
        specifierListA.add(baseEntitlementSpecifier);
        specifierListA.add(addOnEntitlementSpecifier1);
        specifierListA.add(addOnEntitlementSpecifier2);

        final String externalKeyB = "baseExternalKeyBBB";

        final List<EntitlementSpecifier> specifierListB = new ArrayList<EntitlementSpecifier>();
        specifierListB.add(baseEntitlementSpecifier);
        specifierListB.add(addOnEntitlementSpecifier1);
        specifierListB.add(addOnEntitlementSpecifier2);

        final List<BaseEntitlementWithAddOnsSpecifier> entitlementWithAddOnsSpecifierList = new ArrayList<BaseEntitlementWithAddOnsSpecifier>();
        final BaseEntitlementWithAddOnsSpecifier cartSpecifierA = new DefaultBaseEntitlementWithAddOnsSpecifier(null, externalKeyA, specifierListA, null, null, false);
        final BaseEntitlementWithAddOnsSpecifier cartSpecifierB = new DefaultBaseEntitlementWithAddOnsSpecifier(null, externalKeyB, specifierListB, null, null, false);
        entitlementWithAddOnsSpecifierList.add(cartSpecifierA);
        entitlementWithAddOnsSpecifierList.add(cartSpecifierB);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK,
                                      NextEvent.CREATE, NextEvent.BLOCK,
                                      NextEvent.CREATE, NextEvent.BLOCK,
                                      NextEvent.CREATE, NextEvent.BLOCK,
                                      NextEvent.CREATE, NextEvent.BLOCK,
                                      NextEvent.CREATE, NextEvent.BLOCK,
                                      NextEvent.INVOICE,
                                      NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT
                                     );
        final List<UUID> allEntitlements = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), entitlementWithAddOnsSpecifierList, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        checkNoMoreInvoiceToGenerate(account);

        assertNotNull(allEntitlements);
        assertEquals(allEntitlements.size(), 6);

        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(allEntitlements.get(0), false, callContext);
        final Entitlement addOnEntitlement1 = entitlementApi.getEntitlementForId(allEntitlements.get(1), false, callContext);
        final Entitlement addOnEntitlement2 = entitlementApi.getEntitlementForId(allEntitlements.get(2), false, callContext);

        assertEquals(baseEntitlement.getLastActiveProduct().getName(), "Shotgun");
        assertEquals(baseEntitlement.getLastActiveProductCategory(), ProductCategory.BASE);

        assertEquals(addOnEntitlement1.getLastActiveProduct().getName(), "Telescopic-Scope");
        assertEquals(addOnEntitlement1.getLastActiveProductCategory(), ProductCategory.ADD_ON);

        assertEquals(addOnEntitlement2.getLastActiveProduct().getName(), "Laser-Scope");
        assertEquals(addOnEntitlement2.getLastActiveProductCategory(), ProductCategory.ADD_ON);

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 1); // ONLY ONE INVOICE
        assertEquals(invoices.get(0).getInvoiceItems().size(), 6);

        final List<ExpectedInvoiceItemCheck> toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(initialDate, new LocalDate(2015, 10, 31), InvoiceItemType.RECURRING, new BigDecimal("387.05")), // amount=387.05, rate=399.95 -> Telescopic-Scope
                new ExpectedInvoiceItemCheck(initialDate, new LocalDate(2015, 10, 31), InvoiceItemType.RECURRING, new BigDecimal("967.69")), // amount=967.69, rate=999.95 -> Laser-Scope
                new ExpectedInvoiceItemCheck(initialDate, new LocalDate(2015, 10, 31), InvoiceItemType.FIXED, new BigDecimal("0")), // Shotgun
                new ExpectedInvoiceItemCheck(initialDate, new LocalDate(2015, 10, 31), InvoiceItemType.RECURRING, new BigDecimal("387.05")), // amount=387.05, rate=399.95 -> Telescopic-Scope
                new ExpectedInvoiceItemCheck(initialDate, new LocalDate(2015, 10, 31), InvoiceItemType.RECURRING, new BigDecimal("967.69")), // amount=967.69, rate=999.95 -> Laser-Scope
                new ExpectedInvoiceItemCheck(initialDate, new LocalDate(2015, 10, 31), InvoiceItemType.FIXED, new BigDecimal("0"))); // Shotgun

        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, toBeChecked);
    }

    @Test(groups = "slow")
    public void testCreateMultipleSubscriptionsWithoutBase() throws Exception {
        final LocalDate initialDate = new LocalDate(2015, 10, 1);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final String externalKeyB = "baseExternalKeyBBB";

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), externalKeyB, "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 1);

        final PlanPhaseSpecifier baseSpec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final PlanPhaseSpecifier addOnSpec1 = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final PlanPhaseSpecifier addOnSpec2 = new PlanPhaseSpecifier("Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        final String externalKeyA = "baseExternalKeyAAA";
        final EntitlementSpecifier baseEntitlementSpecifier = new DefaultEntitlementSpecifier(baseSpec);
        final EntitlementSpecifier addOnEntitlementSpecifier1 = new DefaultEntitlementSpecifier(addOnSpec1);
        final EntitlementSpecifier addOnEntitlementSpecifier2 = new DefaultEntitlementSpecifier(addOnSpec2);

        final List<EntitlementSpecifier> specifierListA = new ArrayList<EntitlementSpecifier>();
        specifierListA.add(baseEntitlementSpecifier);
        specifierListA.add(addOnEntitlementSpecifier1);
        specifierListA.add(addOnEntitlementSpecifier2);

        final List<EntitlementSpecifier> specifierListB = new ArrayList<EntitlementSpecifier>();
        specifierListB.add(addOnEntitlementSpecifier1);
        specifierListB.add(addOnEntitlementSpecifier2);

        final List<BaseEntitlementWithAddOnsSpecifier> entitlementWithAddOnsSpecifierList = new ArrayList<BaseEntitlementWithAddOnsSpecifier>();
        final BaseEntitlementWithAddOnsSpecifier cartSpecifierA = new DefaultBaseEntitlementWithAddOnsSpecifier(null, externalKeyA, specifierListA, null, null, false);
        final BaseEntitlementWithAddOnsSpecifier cartSpecifierB = new DefaultBaseEntitlementWithAddOnsSpecifier(null, externalKeyB, specifierListB, null, null, false);
        entitlementWithAddOnsSpecifierList.add(cartSpecifierA);
        entitlementWithAddOnsSpecifierList.add(cartSpecifierB);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK,
                                      NextEvent.CREATE, NextEvent.BLOCK,
                                      NextEvent.CREATE, NextEvent.BLOCK,
                                      NextEvent.CREATE, NextEvent.BLOCK,
                                      NextEvent.CREATE, NextEvent.BLOCK,
                                      NextEvent.INVOICE,
                                      NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT
                                     );

        final List<UUID> entitlements = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), entitlementWithAddOnsSpecifierList, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        Assert.assertEquals(entitlements.size(), 5);

    }

    @Test(groups = "slow", expectedExceptions = EntitlementApiException.class,
            expectedExceptionsMessageRegExp = "Can't create AddOn laser-scope-monthly, the number of Subscriptions allowed by this Plan and Bundle has reached its limit")
    public void testCreateMultipleSubscriptionsExceedLimit() throws Exception {
        final LocalDate initialDate = new LocalDate(2015, 10, 1);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier baseSpec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final PlanPhaseSpecifier addOnSpec1 = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final PlanPhaseSpecifier addOnSpec2 = new PlanPhaseSpecifier("Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        final String externalKeyA = "baseExternalKeyAAA";
        final EntitlementSpecifier baseEntitlementSpecifier = new DefaultEntitlementSpecifier(baseSpec);
        final EntitlementSpecifier addOnEntitlementSpecifier1 = new DefaultEntitlementSpecifier(addOnSpec1);
        final EntitlementSpecifier addOnEntitlementSpecifier2 = new DefaultEntitlementSpecifier(addOnSpec2);

        final List<EntitlementSpecifier> specifierListA = new ArrayList<EntitlementSpecifier>();
        specifierListA.add(baseEntitlementSpecifier);
        specifierListA.add(addOnEntitlementSpecifier1);
        specifierListA.add(addOnEntitlementSpecifier2);
        specifierListA.add(addOnEntitlementSpecifier2);
        specifierListA.add(addOnEntitlementSpecifier2);

        final List<BaseEntitlementWithAddOnsSpecifier> entitlementWithAddOnsSpecifierList = new ArrayList<>();
        final BaseEntitlementWithAddOnsSpecifier cartSpecifierA = new DefaultBaseEntitlementWithAddOnsSpecifier(null, externalKeyA, specifierListA, null, null, false);
        entitlementWithAddOnsSpecifierList.add(cartSpecifierA);

        entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), entitlementWithAddOnsSpecifierList, true, Collections.emptyList(), callContext);
    }

    @Test(groups = "slow")
    public void testCreateBaseSubscriptionAndAddOnsWithLimitException() throws Exception {
        final LocalDate initialDate = new LocalDate(2015, 10, 1);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier addOnSpec1 = new PlanPhaseSpecifier("Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final PlanPhaseSpecifier addOnSpec2 = new PlanPhaseSpecifier("Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final PlanPhaseSpecifier addOnSpec3 = new PlanPhaseSpecifier("Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create base subscription
        final Entitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), account.getExternalKey(), "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(baseEntitlement);

        // Create first add_on subscription
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec1), initialDate, initialDate, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        // Create second add_on subscription with the same plan
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec2), initialDate, initialDate, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        // Trying to add the third add_on with the same plan should throw an exception (the limit is 2 for this plan)
        try {
            entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec3), initialDate, initialDate, false, Collections.emptyList(), callContext);
        } catch (final EntitlementApiException e) {
            assertEquals(e.getCode(), ErrorCode.SUB_CREATE_AO_MAX_PLAN_ALLOWED_BY_BUNDLE.getCode());
        }
    }

    @Test(groups = "slow")
    public void testChangePlanWithLimitException() throws Exception {
        final LocalDate initialDate = new LocalDate(2015, 10, 1);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier addOnSpec1 = new PlanPhaseSpecifier("Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final PlanPhaseSpecifier addOnSpec2 = new PlanPhaseSpecifier("Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final PlanPhaseSpecifier addOnSpec3 = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create base subscription
        final Entitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), account.getExternalKey(), "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(baseEntitlement);

        // Create first add_on subscription
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec1), initialDate, initialDate, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        // Create second add_on subscription with the same plan
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec2), initialDate, initialDate, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        // Create third add_on subscription with another plan
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final UUID addOn3Id = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec3), initialDate, initialDate, false, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement addOn3 = entitlementApi.getEntitlementForId(addOn3Id, false, callContext);

        // Trying to change the plan of the third add_on to 'Laser-Scope' plan, should throw an exception (the limit is 2 for this plan)
        try {
            final PlanPhaseSpecifier addOnSpecChangedPlan = new PlanPhaseSpecifier("Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
            addOn3.changePlan(new DefaultEntitlementSpecifier(addOnSpecChangedPlan), Collections.emptyList(), callContext);
        } catch (final EntitlementApiException e) {
            assertEquals(e.getCode(), ErrorCode.SUB_CHANGE_AO_MAX_PLAN_ALLOWED_BY_BUNDLE.getCode());
        }
    }

    @Test(groups = "slow")
    public void testCancelFutureSubscriptionWithPolicy() throws Exception {
        final LocalDate initialDate = new LocalDate(2015, 9, 1);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        final LocalDate futureDate = new LocalDate(2015, 10, 1);

        // No CREATE event as this is set in the future
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), futureDate, futureDate, false, true, Collections.emptyList(), callContext);
        final Entitlement createdEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, false, callContext);
        assertEquals(internalCallContext.toLocalDate(createdEntitlement.getEffectiveStartDate()).compareTo(futureDate), 0);
        assertNull(createdEntitlement.getEffectiveEndDate());
        assertListenerStatus();

        final Entitlement cancelledEntitlement = createdEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, null, callContext);
        assertEquals(internalCallContext.toLocalDate(cancelledEntitlement.getEffectiveEndDate()).compareTo(futureDate), 0);
        assertListenerStatus();

        // Move off trial and reach start/cancellation date
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE);
        clock.addDays(30);
        assertListenerStatus();

        // Just to make sure we really don't invoice for anything move to next month
        clock.addMonths(1);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testCancelFutureSubscriptionWithRequestedDate() throws Exception {
        final LocalDate initialDate = new LocalDate(2015, 9, 1);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        final LocalDate futureDate = new LocalDate(2015, 10, 1);

        // No CREATE event as this is set in the future
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), futureDate, futureDate, false, true, Collections.emptyList(), callContext);
        final Entitlement createdEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, false, callContext);
        assertEquals(createdEntitlement.getState(), EntitlementState.PENDING);
        assertEquals(internalCallContext.toLocalDate(createdEntitlement.getEffectiveStartDate()).compareTo(futureDate), 0);
        assertNull(createdEntitlement.getEffectiveEndDate());
        assertEquals(createdEntitlement.getLastActiveProduct().getName(), "Shotgun");
        assertEquals(createdEntitlement.getLastActivePlan().getName(), "shotgun-annual");
        assertEquals(createdEntitlement.getLastActiveProductCategory(), ProductCategory.BASE);
        assertListenerStatus();

        final LocalDate invalidCancelDate = initialDate.plusDays(1);
        try {
            createdEntitlement.cancelEntitlementWithDate(invalidCancelDate, true, null, callContext);
            Assert.fail("Should not succeed to cancel subscription prior startDate");
        } catch (final EntitlementApiException e) {
            assertEquals(e.getCode(), ErrorCode.SUB_INVALID_REQUESTED_DATE.getCode());
        }

        final Entitlement cancelledEntitlement = createdEntitlement.cancelEntitlementWithDate(futureDate, true, null, callContext);
        assertEquals(internalCallContext.toLocalDate(cancelledEntitlement.getEffectiveEndDate()).compareTo(futureDate), 0);
        assertListenerStatus();

        // Move off trial and reach start/cancellation date
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE);
        clock.addDays(30);
        assertListenerStatus();

        // Just to make sure we really don't invoice for anything move to next month
        clock.addMonths(1);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testCancelSubscriptionInTrialWith_START_OF_TERM() throws Exception {
        final LocalDate initialDate = new LocalDate(2015, 9, 1);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), initialDate, initialDate, false, true, Collections.emptyList(), callContext);
        final Entitlement createdEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, false, callContext);
        assertEquals(internalCallContext.toLocalDate(createdEntitlement.getEffectiveStartDate()).compareTo(initialDate), 0);
        assertNull(createdEntitlement.getEffectiveEndDate());
        assertListenerStatus();

        // Move clock a bit to make sure START_OF_TERM brings us back to initialDate
        clock.addDays(5);

        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final Entitlement cancelledEntitlement = createdEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.START_OF_TERM, null, callContext);
        assertListenerStatus();

        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(cancelledEntitlement.getId(), false, callContext);

        assertEquals(internalCallContext.toLocalDate(subscription.getEffectiveEndDate()).compareTo(new LocalDate(2015, 9, 6)), 0);
        assertEquals(internalCallContext.toLocalDate(subscription.getBillingEndDate()).compareTo(initialDate), 0);

    }

    @Test(groups = "slow")
    public void testCancelSubscriptionAfterTrialWith_START_OF_TERM() throws Exception {
        final LocalDate initialDate = new LocalDate(2015, 8, 1);
        clock.setDay(initialDate);

        Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), initialDate, initialDate, false, true, Collections.emptyList(), callContext);
        final Entitlement createdEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, false, callContext);
        assertEquals(internalCallContext.toLocalDate(createdEntitlement.getEffectiveStartDate()).compareTo(initialDate), 0);
        assertNull(createdEntitlement.getEffectiveEndDate());
        assertListenerStatus();

        // Move out of trial : 2015-8-31
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        // SUBSCRIPTION alignment: no account BCD
        account = accountUserApi.getAccountById(account.getId(), callContext);
        Assert.assertEquals(account.getBillCycleDayLocal().intValue(), 0);

        // Move clock a bit to make sure START_OF_TERM brings us back to last Phase date : 2015-9-5
        clock.addDays(5);

        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.INVOICE);
        final Entitlement cancelledEntitlement = createdEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.START_OF_TERM, null, callContext);
        assertListenerStatus();

        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(cancelledEntitlement.getId(), false, callContext);

        assertEquals(internalCallContext.toLocalDate(subscription.getEffectiveEndDate()).compareTo(new LocalDate(2015, 9, 5)), 0);
        assertEquals(internalCallContext.toLocalDate(subscription.getBillingEndDate()).compareTo(new LocalDate(2015, 8, 31)), 0);

    }

    @Test(groups = "slow")
    public void testCreateChangeCancelSubscriptionWithDateTime() throws Exception {

        final DateTime initialDateTime = new DateTime(2012, 5, 1, 10, 0);
        clock.setTime(initialDateTime);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final DateTime createDateTime = initialDateTime.plusMinutes(30); //2012-05-01T10:30

        final EntitlementSpecifier spec = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("pistol-monthly-notrial"));
        final List<EntitlementSpecifier> specs = List.of(spec);
        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(null, null, specs, createDateTime, createDateTime, false);
        final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers = List.of(baseEntitlementWithAddOnsSpecifier);

        //create subscription:2012-05-01T10:30
        final List<UUID> entitlementIds = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), baseEntitlementWithAddOnsSpecifiers, true, Collections.emptyList(), callContext);
        assertEquals(entitlementIds.size(), 1);
        Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementIds.get(0), false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.PENDING);

        //move clock to 2012-05-01T10:30 and verify that the subscription creation is successful
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.setTime(createDateTime);
        assertListenerStatus();
        entitlement = entitlementApi.getEntitlementForId(entitlementIds.get(0), false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement.getLastActivePlan().getName(), "pistol-monthly-notrial");
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("19.95")));

        // change plan:2012-05-01T11:30
        final DateTime changeDateTime = createDateTime.plusHours(1);
        final EntitlementSpecifier newSpec = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("blowdart-monthly-notrial"));
        entitlement.changePlanWithDate(newSpec, changeDateTime, Collections.emptyList(), callContext);

        //move clock to 2012-05-01T11:30 and verify that plan change is successful
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.setTime(changeDateTime);
        assertListenerStatus();
        entitlement = entitlementApi.getEntitlementForId(entitlementIds.get(0), false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement.getLastActivePlan().getName(), "blowdart-monthly-notrial");

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")), new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-19.95")));

        //cancel subscription: 2012-05-01T13:30
        final DateTime cancelDateTime = changeDateTime.plusHours(2);
        entitlement.cancelEntitlementWithDate(cancelDateTime, cancelDateTime, Collections.emptyList(), callContext);

        // Move clock to 2012-05-01T11:30 and verify that cancel is successful
        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.INVOICE);
        clock.setTime(cancelDateTime);
        assertListenerStatus();
        entitlement = entitlementApi.getEntitlementForId(entitlementIds.get(0), false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.CANCELLED);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-29.95")), new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("29.95")));

    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1477")
    public void testChangeBPWithPendingAddonAndAddOnAvailableOnNewPlan() throws Exception {

        final LocalDate initialDate = new LocalDate(2015, 8, 1);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));

        //CREATE BASE PLAN: 2015-08-01
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID bpEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, Collections.emptyList(), callContext);
        Entitlement baseEntitlement = entitlementApi.getEntitlementForId(bpEntitlementId, false, callContext);
        assertEquals(baseEntitlement.getState(), EntitlementState.ACTIVE);
        assertListenerStatus();

        //MOVE PAST TRIAL PHASE: 2015-09-01
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        //CREATE ADDON WITH FUTURE DATE OF 2015-09-10
        final LocalDate addOnDate = new LocalDate(2015, 9, 10);
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final UUID addOnEntitlementId = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec), addOnDate, addOnDate, false, Collections.emptyList(), callContext);
        Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(addOnEntitlementId, false, callContext);
        assertEquals(addOnEntitlement.getState(), EntitlementState.PENDING);

        //MOVE CLOCK TO 2015-09-06 AND CHANGE BASE PLAN SUCH THAT THE ADDOS IS AVAILABLE ON THE NEW PLAN
        clock.addDays(5);
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final PlanPhaseSpecifier newPlanSpec = new PlanPhaseSpecifier("Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        baseEntitlement.changePlan(new DefaultEntitlementSpecifier(newPlanSpec), Collections.emptyList(), callContext);
        assertListenerStatus();

        //BASE PLAN CHANGED SUCCESSFULLY, ADDON STILL PENDING
        baseEntitlement = entitlementApi.getEntitlementForId(bpEntitlementId, false, callContext);
        assertEquals(baseEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(baseEntitlement.getLastActiveProduct().getName(), "Assault-Rifle");
        addOnEntitlement = entitlementApi.getEntitlementForId(addOnEntitlementId, false, callContext);
        assertEquals(addOnEntitlement.getState(), EntitlementState.PENDING);

        //MOVE CLOCK TO 2015-09-10 AND VERIFY THAT ADDON IS ACTIVE
        clock.addDays(5);
        addOnEntitlement = entitlementApi.getEntitlementForId(addOnEntitlementId, false, callContext);
        assertEquals(addOnEntitlement.getState(), EntitlementState.ACTIVE);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        assertListenerStatus();

    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1477")
    public void testChangeBPWithPendingAddonAndAddOnNotAvailableOnNewPlan() throws Exception {

        final LocalDate initialDate = new LocalDate(2015, 8, 1);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));

        //CREATE BASE PLAN: 2015-08-01
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID bpEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, Collections.emptyList(), callContext);
        Entitlement baseEntitlement = entitlementApi.getEntitlementForId(bpEntitlementId, false, callContext);
        assertEquals(baseEntitlement.getState(), EntitlementState.ACTIVE);
        assertListenerStatus();

        //MOVE PAST TRIAL PHASE: 2015-09-01
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        //CREATE ADDON WITH FUTURE DATE OF 2015-09-10
        final LocalDate addOnDate = new LocalDate(2015, 9, 10);
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("Holster", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final UUID addOnEntitlementId = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec), addOnDate, addOnDate, false, Collections.emptyList(), callContext);
        Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(addOnEntitlementId, false, callContext);
        assertEquals(addOnEntitlement.getState(), EntitlementState.PENDING);

        //MOVE CLOCK TO 2015-09-06 AND CHANGE BASE PLAN SUCH THAT THE ADDOS IS NOT AVAILABLE ON THE NEW PLAN
        clock.addDays(5);
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final PlanPhaseSpecifier newPlanSpec = new PlanPhaseSpecifier("Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        baseEntitlement.changePlan(new DefaultEntitlementSpecifier(newPlanSpec), Collections.emptyList(), callContext);
        assertListenerStatus();

        //BASE PLAN CHANGED SUCCESSFULLY, ADDON CANCELLED
        baseEntitlement = entitlementApi.getEntitlementForId(bpEntitlementId, false, callContext);
        assertEquals(baseEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(baseEntitlement.getLastActiveProduct().getName(), "Assault-Rifle");
        addOnEntitlement = entitlementApi.getEntitlementForId(addOnEntitlementId, false, callContext);
        assertEquals(addOnEntitlement.getState(), EntitlementState.CANCELLED);

    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1631")
    public void testChangePlanWithStartDate() throws Exception {

        final LocalDate initialDate = new LocalDate(2015, 8, 1);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));

        //MOVE CLOCK BY A FEW MINUTES SO THAT SUBSCRIPTION START DATETIME IS A LITTLE AFTER INITIAL DATE TIME
        clock.setTime(clock.getUTCNow().plusMinutes(2));

        //CREATE PLAN
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT,
                                      NextEvent.PAYMENT);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-notrial", null);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "something", initialDate, initialDate, false, true, Collections.emptyList(), callContext);
        assertNotNull(entitlementId);
        Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement.getLastActiveProduct().getName(), "Pistol");
        assertListenerStatus();

        clock.addDays(10);

        //CHANGE PLAN WITH START DATE
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final PlanPhaseSpecifier newPlanSpec = new PlanPhaseSpecifier("blowdart-monthly-notrial", null);
        entitlement.changePlanWithDate(new DefaultEntitlementSpecifier(newPlanSpec), initialDate, Collections.emptyList(), callContext);
        entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);

        //PLAN CHANGED SUCCESSFULLY
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement.getLastActiveProduct().getName(), "Blowdart");
        assertEquals(internalCallContext.toLocalDate(entitlement.getEffectiveStartDate()), initialDate);
        assertListenerStatus();
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1030")
    public void testCreateSubscriptionChangePlanAndRetrieveEvents() throws Exception {
        final LocalDate today = new LocalDate(2012, 4, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create subscription
        final PlanPhaseSpecifier planSpec = new PlanPhaseSpecifier("pistol-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(planSpec, null, null, null, null), "externalKey", null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Retrieve subscription without deleted events and verify subscription events
        Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        assertNotNull(subscription);
        List<SubscriptionEvent> events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 3);
        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);

        //Retrieve subscription with deleted events and verify that subscription events are the same as above
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, true, callContext);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);
        events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 3);
        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);

        clock.addDays(2);

        //change plan, this will result in deletion of the PhaseEvent
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier("pistol-monthly-notrial");
        subscription.changePlan(new DefaultEntitlementSpecifier(planPhaseSpecifier), Collections.emptyList(), callContext);
        assertListenerStatus();

        //Retrieve subscription without deleted events and verify subscription events
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);
        events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 3);
        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.CHANGE);

        //Retrieve subscription with deleted events and verify that deleted PHASE event is returned
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, true, callContext);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);
        events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 4);
        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.CHANGE);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.PHASE);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1030")
    public void testCreateCancelSubscriptionInTheFutureAndRetrieveEvents() throws Exception {
        final LocalDate initialDate = new LocalDate(2012, 4, 1);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create subscription in the future
        final LocalDate creationDate = initialDate.plusDays(2);
        final PlanPhaseSpecifier planSpec = new PlanPhaseSpecifier("pistol-monthly");
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(planSpec, null, null, null, null), "externalKey", creationDate, creationDate, false, false, Collections.emptyList(), callContext);

        //Retrieve subscription without deleted events and verify subscription events
        Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.PENDING);
        List<SubscriptionEvent> events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 3);
        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);

        //Retrieve subscription with deleted events and verify that subscription events are the same as above
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, true, callContext);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.PENDING);
        events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 3);
        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);

        //move to creation date and verify there is no change in events
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        clock.setDay(creationDate);
        assertListenerStatus();
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, true, callContext);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);
        events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 3);
        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);

        clock.addDays(1);

        //cancel in the future - results in deletion of PHASE event
        final LocalDate cancelDate = creationDate.plusDays(2);
        subscription.cancelEntitlementWithDate(cancelDate, true, Collections.emptyList(), callContext);

        //Retrieve subscription without deleted events (using external key) and verify subscription events
        subscription = subscriptionApi.getSubscriptionForExternalKey(subscription.getExternalKey(), false, callContext);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);
        events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 4);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.STOP_ENTITLEMENT);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);

        //Retrieve subscription with deleted events (using external key) and and verify that deleted PHASE event is returned
        subscription = subscriptionApi.getSubscriptionForExternalKey(subscription.getExternalKey(), true, callContext);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);
        events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 5);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.STOP_ENTITLEMENT);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);
        assertEquals(events.get(4).getSubscriptionEventType(), SubscriptionEventType.PHASE);

        //move to cancel date and verify that deleted event is still returned
        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        clock.setDay(cancelDate);
        assertListenerStatus();
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, true, callContext);
        assertEquals(subscription.getState(), EntitlementState.CANCELLED);
        events = subscription.getSubscriptionEvents();
        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.STOP_ENTITLEMENT);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);
        assertEquals(events.get(4).getSubscriptionEventType(), SubscriptionEventType.PHASE);

    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1030")
    public void testCreateCancelUncancelAndRetrieveEvents() throws Exception {
        final LocalDate initialDate = new LocalDate(2012, 4, 1);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create subscription
        final PlanPhaseSpecifier planSpec = new PlanPhaseSpecifier("pistol-monthly-notrial");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(planSpec, null, null, null, null), "externalKey", null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Retrieve subscription without deleted events and verify subscription events
        Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        assertNotNull(subscription);
        List<SubscriptionEvent> events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 2);
        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);

        //Retrieve subscription with deleted events and verify that subscription events are the same as above
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, true, callContext);
        assertNotNull(subscription);
        events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 2);
        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);

        //cancel in the future
        final LocalDate cancelDate = initialDate.plusDays(3);
        subscription.cancelEntitlementWithDate(cancelDate, true, Collections.emptyList(), callContext);

        //Retrieve subscription without deleted events and verify subscription events
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);
        events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 4);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.STOP_ENTITLEMENT);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);

        //Retrieve subscription with deleted events and verify that subscription events are the same as above
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, true, callContext);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);
        events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 4);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.STOP_ENTITLEMENT);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);

        //uncancel the subscription, results in deletion of the CANCEL event
        busHandler.pushExpectedEvents(NextEvent.UNCANCEL);
        subscription.uncancelEntitlement(Collections.emptyList(), callContext);
        assertListenerStatus();

        //Retrieve subscription without deleted events and verify subscription events
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);
        events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 2);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);

        //Retrieve subscription with deleted events and verify that deleted cancel events (STOP_ENTITLEMENT and STOP_BILLING) are returned
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, true, callContext);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);
        events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 4);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.STOP_ENTITLEMENT);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1030")
    public void testCreateBaseAndAddOnCancelBaseAndRetrieveEvents() throws Exception {
        final LocalDate initialDate = new LocalDate(2012, 4, 1);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create subscription
        final PlanPhaseSpecifier planSpec = new PlanPhaseSpecifier("pistol-monthly-notrial");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(planSpec, null, null, null, null), "externalKey", null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        final PlanPhaseSpecifier addOnplanSpec = new PlanPhaseSpecifier("cleaning-monthly");
        final UUID addOnEntitlementId = entitlementApi.addEntitlement(subscription.getBundleId(), new DefaultEntitlementSpecifier(addOnplanSpec, null, null, null, null), null, null, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Retrieve base subscription and verify subscription events
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        assertNotNull(subscription);
        List<SubscriptionEvent> events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 2);
        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);

        //Retrieve addon subscription and verify subscription events
        Subscription addOnSubscription = subscriptionApi.getSubscriptionForEntitlementId(addOnEntitlementId, false, callContext);
        events = addOnSubscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 2);
        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);

        //cancel base with future date
        final LocalDate cancelDate = initialDate.plusDays(3);
        subscription.cancelEntitlementWithDate(cancelDate, true, Collections.emptyList(), callContext);

        //Retrieve base subscription and verify subscription events
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);
        events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 4);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.STOP_ENTITLEMENT);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);

        //retrieve addon subscription and verify subscription events for addon
        addOnSubscription = subscriptionApi.getSubscriptionForEntitlementId(addOnEntitlementId, false, callContext);
        events = addOnSubscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 4);
        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.STOP_ENTITLEMENT);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);

        //uncancel base
        busHandler.pushExpectedEvents(NextEvent.UNCANCEL);
        subscription.uncancelEntitlement(Collections.emptyList(), callContext);
        assertListenerStatus();

        //Retrieve base subscription without deleted events and verify subscription events
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE);
        events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 2);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);

        //Retrieve base subscription with deleted events
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, true, callContext);
        assertNotNull(subscription);
        assertEquals(subscription.getState(), EntitlementState.ACTIVE); //verify that state is returned correctly even after the deleted events change
        events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 4);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.STOP_ENTITLEMENT);
        assertEquals(events.get(3).getSubscriptionEventType(), SubscriptionEventType.STOP_BILLING);

        //retrieve addon subscription without deleted events and verify subscription events
        addOnSubscription = subscriptionApi.getSubscriptionForEntitlementId(addOnEntitlementId, false, callContext);
        events = addOnSubscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 2);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);

        //retrieve addon subscription with deleted events and verify subscription events
        addOnSubscription = subscriptionApi.getSubscriptionForEntitlementId(addOnEntitlementId, true, callContext);
        events = addOnSubscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 2);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1913")
    public void testCreateFixedTermSubscriptionAndRetrieveEvents() throws Exception {
        final LocalDate initialDate = new LocalDate(2012, 4, 1);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create subscription
        final PlanPhaseSpecifier planSpec = new PlanPhaseSpecifier("pistol-monthly-fixedterm");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(planSpec, null, null, null, null), "externalKey", null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //fetch subscription
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        final List<SubscriptionEvent> events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 3);
        assertTrue(events.stream().anyMatch(e -> e.getSubscriptionEventType().equals(SubscriptionEventType.START_ENTITLEMENT)));
        assertTrue(events.stream().anyMatch(e -> e.getSubscriptionEventType().equals(SubscriptionEventType.START_BILLING)));
        assertTrue(events.stream().anyMatch(e -> e.getSubscriptionEventType().equals(SubscriptionEventType.STOP_BILLING)));

    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1913")
    public void testCreateSubscriptionFutureCancelAndRetrieveEvents() throws Exception {
        final LocalDate initialDate = new LocalDate(2012, 4, 1);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create subscription
        final PlanPhaseSpecifier planSpec = new PlanPhaseSpecifier("pistol-monthly-notrial");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(planSpec, null, null, null, null), "externalKey", null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //fetch subscription
        Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        List<SubscriptionEvent> events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 2);
        assertTrue(events.stream().anyMatch(e -> e.getSubscriptionEventType().equals(SubscriptionEventType.START_ENTITLEMENT)));
        assertTrue(events.stream().anyMatch(e -> e.getSubscriptionEventType().equals(SubscriptionEventType.START_BILLING)));

        //future cancel
        final LocalDate cancelDate = initialDate.plusDays(3);
        subscription.cancelEntitlementWithDate(cancelDate, true, Collections.emptyList(), callContext);

        //returns the STOP_BILLING and STOP_ENTITLEMENT events too
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        events = subscription.getSubscriptionEvents();
        assertNotNull(events);
        assertEquals(events.size(), 4);
        assertTrue(events.stream().anyMatch(e -> e.getSubscriptionEventType().equals(SubscriptionEventType.START_ENTITLEMENT)));
        assertTrue(events.stream().anyMatch(e -> e.getSubscriptionEventType().equals(SubscriptionEventType.START_BILLING)));
        assertTrue(events.stream().anyMatch(e -> e.getSubscriptionEventType().equals(SubscriptionEventType.STOP_ENTITLEMENT)));
        assertTrue(events.stream().anyMatch(e -> e.getSubscriptionEventType().equals(SubscriptionEventType.STOP_BILLING)));

    }
}
