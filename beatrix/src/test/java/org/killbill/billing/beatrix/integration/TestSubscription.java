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
import java.util.List;
import java.util.UUID;

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
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PluginProperty;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestSubscription extends TestIntegrationBase {

    @Test(groups = "slow")
    public void testForcePolicy() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        final LocalDate today = new LocalDate(2012, 4, 1);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(40);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);


        //
        // FORCE AN IMMEDIATE CHANGE OF THE BILLING PERIOD
        //
        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 11), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("169.32")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 11), new LocalDate(2013, 5, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2334.20")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 11), new LocalDate(2012, 5, 11), InvoiceItemType.CBA_ADJ, new BigDecimal("2164.88"), false /* Issue with test where created date for context is wrong*/));

        TestDryRunArguments dryRun = new TestDryRunArguments(DryRunType.SUBSCRIPTION_ACTION, productName, ProductCategory.BASE, BillingPeriod.MONTHLY, null, null,
                                                             SubscriptionEventType.CHANGE, bpEntitlement.getId(), bpEntitlement.getBundleId(), null, BillingActionPolicy.IMMEDIATE);
        Invoice dryRunInvoice = invoiceUserApi.triggerDryRunInvoiceGeneration(account.getId(), clock.getUTCToday(), dryRun, callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, toBeChecked);

        changeEntitlementAndCheckForCompletion(bpEntitlement, productName, BillingPeriod.MONTHLY, BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.INVOICE);

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 3);
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        //
        // FORCE ANOTHER CHANGE
        //
        changeEntitlementAndCheckForCompletion(bpEntitlement, productName, BillingPeriod.ANNUAL, BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.INVOICE);

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 4);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 11), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("169.32")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 11), new LocalDate(2013, 5, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2334.20")),
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 11), new LocalDate(2012, 5, 11), InvoiceItemType.CBA_ADJ, new BigDecimal("2164.88")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
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
        final BillingPeriod term = BillingPeriod.ANNUAL;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(40);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        //
        // FORCE AN IMMEDIATE CHANGE TO TEST THE CHANGE_OF_PLAN ALIGNMENT
        // (Note that, the catalog is configured to use  CHANGE_OF_PLAN when moving to that plan and Not CHANGE_OF_PRICELIST which has not been implement;
        // this is a bit misleading since we are changing pricelist, but in that case pricelist change has no effect)
        changeEntitlementAndCheckForCompletion(bpEntitlement, "Assault-Rifle", BillingPeriod.ANNUAL, "rescue", BillingActionPolicy.IMMEDIATE, NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 3);
        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
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
                                      NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE,
                                      NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE,
                                      NextEvent.NULL_INVOICE,
                                      NextEvent.INVOICE,
                                      NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT
                                     );
        final List<UUID> allEntitlements = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), entitlementWithAddOnsSpecifierList, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        checkNoMoreInvoiceToGenerate(account);

        assertNotNull(allEntitlements);
        assertEquals(allEntitlements.size(), 6);

        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(allEntitlements.get(0), callContext);
        final Entitlement addOnEntitlement1 = entitlementApi.getEntitlementForId(allEntitlements.get(1), callContext);
        final Entitlement addOnEntitlement2 = entitlementApi.getEntitlementForId(allEntitlements.get(2), callContext);

        assertEquals(baseEntitlement.getLastActiveProduct().getName(), "Shotgun");
        assertEquals(baseEntitlement.getLastActiveProductCategory(), ProductCategory.BASE);

        assertEquals(addOnEntitlement1.getLastActiveProduct().getName(), "Telescopic-Scope");
        assertEquals(addOnEntitlement1.getLastActiveProductCategory(), ProductCategory.ADD_ON);

        assertEquals(addOnEntitlement2.getLastActiveProduct().getName(), "Laser-Scope");
        assertEquals(addOnEntitlement2.getLastActiveProductCategory(), ProductCategory.ADD_ON);

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1); // ONLY ONE INVOICE
        assertEquals(invoices.get(0).getInvoiceItems().size(), 6);

        final ImmutableList<ExpectedInvoiceItemCheck> toBeChecked = ImmutableList.<ExpectedInvoiceItemCheck>of(
                new ExpectedInvoiceItemCheck(initialDate, new LocalDate(2015, 10, 31), InvoiceItemType.RECURRING, new BigDecimal("387.05")), // amount=387.05, rate=399.95 -> Telescopic-Scope
                new ExpectedInvoiceItemCheck(initialDate, new LocalDate(2015, 10, 31), InvoiceItemType.RECURRING, new BigDecimal("967.69")), // amount=967.69, rate=999.95 -> Laser-Scope
                new ExpectedInvoiceItemCheck(initialDate, null, InvoiceItemType.FIXED, new BigDecimal("0")), // Shotgun
                new ExpectedInvoiceItemCheck(initialDate, new LocalDate(2015, 10, 31), InvoiceItemType.RECURRING, new BigDecimal("387.05")), // amount=387.05, rate=399.95 -> Telescopic-Scope
                new ExpectedInvoiceItemCheck(initialDate, new LocalDate(2015, 10, 31), InvoiceItemType.RECURRING, new BigDecimal("967.69")), // amount=967.69, rate=999.95 -> Laser-Scope
                new ExpectedInvoiceItemCheck(initialDate, null, InvoiceItemType.FIXED, new BigDecimal("0"))); // Shotgun

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
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);


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
                                      NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE,
                                      NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE,
                                      NextEvent.INVOICE,
                                      NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT
                                     );

        final List<UUID> entitlements = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), entitlementWithAddOnsSpecifierList, true, ImmutableList.<PluginProperty>of(), callContext);
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

        final List<BaseEntitlementWithAddOnsSpecifier> entitlementWithAddOnsSpecifierList = new ArrayList<BaseEntitlementWithAddOnsSpecifier>();
        final BaseEntitlementWithAddOnsSpecifier cartSpecifierA = new DefaultBaseEntitlementWithAddOnsSpecifier(null, externalKeyA, specifierListA, null, null, false);
        entitlementWithAddOnsSpecifierList.add(cartSpecifierA);

        entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), entitlementWithAddOnsSpecifierList, true, ImmutableList.<PluginProperty>of(), callContext);
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
        entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec1), initialDate, initialDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Create second add_on subscription with the same plan
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec2), initialDate, initialDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Trying to add the third add_on with the same plan should throw an exception (the limit is 2 for this plan)
        try {
            entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec3), initialDate, initialDate, false, ImmutableList.<PluginProperty>of(), callContext);
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
        entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec1), initialDate, initialDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Create second add_on subscription with the same plan
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec2), initialDate, initialDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Create third add_on subscription with another plan
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final UUID addOn3Id = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec3), initialDate, initialDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement addOn3 = entitlementApi.getEntitlementForId(addOn3Id, callContext);

        // Trying to change the plan of the third add_on to 'Laser-Scope' plan, should throw an exception (the limit is 2 for this plan)
        try {
            final PlanPhaseSpecifier addOnSpecChangedPlan = new PlanPhaseSpecifier("Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
            addOn3.changePlan(new DefaultEntitlementSpecifier(addOnSpecChangedPlan), ImmutableList.<PluginProperty>of(), callContext);
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
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), futureDate, futureDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement createdEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, callContext);
        assertEquals(createdEntitlement.getEffectiveStartDate().compareTo(futureDate), 0);
        assertEquals(createdEntitlement.getEffectiveEndDate(), null);
        assertListenerStatus();


        final Entitlement cancelledEntitlement = createdEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, null, callContext);
        assertEquals(cancelledEntitlement.getEffectiveEndDate().compareTo(futureDate), 0);
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
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), futureDate, futureDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement createdEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, callContext);
        assertEquals(createdEntitlement.getState(), EntitlementState.PENDING);
        assertEquals(createdEntitlement.getEffectiveStartDate().compareTo(futureDate), 0);
        assertEquals(createdEntitlement.getEffectiveEndDate(), null);
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
        assertEquals(cancelledEntitlement.getEffectiveEndDate().compareTo(futureDate), 0);
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
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), initialDate, initialDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement createdEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, callContext);
        assertEquals(createdEntitlement.getEffectiveStartDate().compareTo(initialDate), 0);
        assertEquals(createdEntitlement.getEffectiveEndDate(), null);
        assertListenerStatus();

        // Move clock a bit to make sure START_OF_TERM brings us back to initialDate
        clock.addDays(5);

        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final Entitlement cancelledEntitlement = createdEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.START_OF_TERM, null, callContext);
        assertListenerStatus();

        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(cancelledEntitlement.getId(), callContext);

        assertEquals(subscription.getEffectiveEndDate().compareTo(new LocalDate(2015, 9, 6)), 0);
        assertEquals(subscription.getBillingEndDate().compareTo(initialDate), 0);

    }

    @Test(groups = "slow")
    public void testCancelSubscriptionAfterTrialWith_START_OF_TERM() throws Exception {
        final LocalDate initialDate = new LocalDate(2015, 8, 1);
        clock.setDay(initialDate);

        Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), initialDate, initialDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement createdEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, callContext);
        assertEquals(createdEntitlement.getEffectiveStartDate().compareTo(initialDate), 0);
        assertEquals(createdEntitlement.getEffectiveEndDate(), null);
        assertListenerStatus();

        // Move out of trial : 2015-8-31
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        account = accountUserApi.getAccountById(account.getId(), callContext);
        Assert.assertEquals(account.getBillCycleDayLocal().intValue(), 31);


        // Move clock a bit to make sure START_OF_TERM brings us back to last Phase date : 2015-9-5
        clock.addDays(5);

        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.INVOICE);
        final Entitlement cancelledEntitlement = createdEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.START_OF_TERM, null, callContext);
        assertListenerStatus();

        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(cancelledEntitlement.getId(), callContext);

        assertEquals(subscription.getEffectiveEndDate().compareTo(new LocalDate(2015, 9, 5)), 0);
        assertEquals(subscription.getBillingEndDate().compareTo(new LocalDate(2015, 8, 31)), 0);

    }

}
