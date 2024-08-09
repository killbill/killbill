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

package org.killbill.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestAddonChangeAlignment extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testAddonChangeAlignment");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow") // Behaves the same way as testChangeAlignmentStartOfBundleImmediateChange
    public void testChangeAlignmentStartOfBundleEOTChange() throws Exception {
        final LocalDate today = new LocalDate(2023, 8, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create base 2023-08-01
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID baseEntId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        //Base CTD is 2023-08-01 as expected
        Subscription baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 1));

        clock.setDay(new LocalDate(2023, 8, 5));

        //create addon 2023-08-05
        final PlanPhaseSpecifier addonSpec = new PlanPhaseSpecifier("BasicAOStartOfBundleDefault-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID addonEntId = entitlementApi.addEntitlement(baseSub.getBundleId(), new DefaultEntitlementSpecifier(addonSpec), null, null, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 5), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        //ADDON CTD is 2023-08-05 as expected
        Subscription addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 05));

        clock.setDay(new LocalDate(2023, 8, 15));

        //change addon with start of bundle alignment on 2023-08-15 - change will happen immediately as subscription is in TRIAL phase
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE);
        final PlanPhaseSpecifier newAddOnSpec = new PlanPhaseSpecifier("BasicAOStartOfBundleCustom-monthly");
        addonSub.changePlan(new DefaultEntitlementSpecifier(newAddOnSpec), Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 15), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        //Base CTD is still 2023-08-01 as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 1));

        //ADDON CTD is 2023-08-15, though I think technically this should be 2023-08-01 (aligned with base CTD), I think 2023-08-15 is acceptable
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 15));

        //move clock to 2023-08-31 - Phase change for BASE, ADDON takes effect
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 8, 31));
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 31), new LocalDate(2023, 9, 1), InvoiceItemType.RECURRING, new BigDecimal("32.26")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 31), new LocalDate(2023, 9, 1), InvoiceItemType.RECURRING, new BigDecimal("3.23")));

        // BASE CTD set to 2023-09-01
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 9, 1));

        // ADDON CTD set to 2023-09-01
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 9, 1));

        //move clock to 2023-09-01 - recurring invoice for base and addon
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 9, 1));
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2023, 9, 1), new LocalDate(2023, 10, 1), InvoiceItemType.RECURRING, new BigDecimal("1000")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2023, 9, 1), new LocalDate(2023, 10, 1), InvoiceItemType.RECURRING, new BigDecimal("100")));

        // BASE CTD set to 2023-10-01
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 10, 1));

        // ADDON CTD set to 2023-10-01
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 10, 1));

    }

    @Test(groups = "slow") // Behaves the same way as testChangeAlignmentStartOfBundleEOTChange
    public void testChangeAlignmentStartOfBundleImmediateChange() throws Exception {
        final LocalDate today = new LocalDate(2023, 8, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create base 2023-08-01
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID baseEntId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        //Base CTD is 2023-08-01 as expected
        Subscription baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 1));

        clock.setDay(new LocalDate(2023, 8, 5));

        //create addon 2023-08-05
        final PlanPhaseSpecifier addonSpec = new PlanPhaseSpecifier("BasicAOStartOfBundleDefault-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID addonEntId = entitlementApi.addEntitlement(baseSub.getBundleId(), new DefaultEntitlementSpecifier(addonSpec), null, null, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 5), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        //ADDON CTD is 2023-08-05 as expected
        Subscription addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 5));

        clock.setDay(new LocalDate(2023, 8, 15));

        //change addon with start of bundle alignment and IMMEDIATE policy - change will happen immediately
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE);
        final PlanPhaseSpecifier newAddOnSpec = new PlanPhaseSpecifier("BasicAOStartOfBundleCustom-monthly");
        addonSub.changePlanOverrideBillingPolicy(new DefaultEntitlementSpecifier(newAddOnSpec), null, BillingActionPolicy.IMMEDIATE, Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 15), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        //Base CTD is still 2023-08-01 as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 1));

        //ADDON CTD is 2023-08-15, though I think technically this should be 2023-08-01 (aligned with base CTD), I think 2023-08-15 is acceptable
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 15));

        //move clock to 2023-08-31 - Phase change for BASE, ADDON takes effect
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 8, 31));
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 31), new LocalDate(2023, 9, 1), InvoiceItemType.RECURRING, new BigDecimal("32.26")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 31), new LocalDate(2023, 9, 1), InvoiceItemType.RECURRING, new BigDecimal("3.23")));

        // BASE CTD set to 2023-09-01
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 9, 1));

        // ADDON CTD set to 2023-09-01
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 9, 1));

        //move clock to 2023-09-01 - recurring invoice for base and addon
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 9, 1));
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2023, 9, 1), new LocalDate(2023, 10, 1), InvoiceItemType.RECURRING, new BigDecimal("1000")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2023, 9, 1), new LocalDate(2023, 10, 1), InvoiceItemType.RECURRING, new BigDecimal("100")));

        // BASE CTD set to 2023-10-01
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 10, 1));

        // ADDON CTD set to 2023-10-01
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 10, 1));
    }

    @Test(groups = "slow")
    public void testChangeAlignmentStartOfSubscriptionEOTChange() throws Exception {
        final LocalDate today = new LocalDate(2023, 8, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create base 2023-08-01
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID baseEntId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        //Base CTD is 2023-08-01 as expected
        Subscription baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 1));

        clock.setDay(new LocalDate(2023, 8, 5));

        //create addon 2023-08-05
        final PlanPhaseSpecifier addonSpec = new PlanPhaseSpecifier("BasicAOStartOfBundleDefault-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID addonEntId = entitlementApi.addEntitlement(baseSub.getBundleId(), new DefaultEntitlementSpecifier(addonSpec), null, null, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 5), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        //ADDON CTD is 2023-08-05 as expected
        Subscription addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 5));

        clock.setDay(new LocalDate(2023, 8, 15));

        //change addon with start of subscription alignment on 2023-08-15 - change will happen immediately as subscription is in TRIAL phase
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE);
        final PlanPhaseSpecifier newAddOnSpec = new PlanPhaseSpecifier("BasicAOStartOfSubscription-monthly");
        addonSub.changePlan(new DefaultEntitlementSpecifier(newAddOnSpec), Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 15), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        //Base CTD is still 2023-08-01 as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 1));

        //ADDON CTD is 2023-08-15, though I think technically this should be 2023-08-05 (aligned with old addon CTD), I think 2023-08-15 is acceptable
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 15));

        //move clock to 2023-08-31 - PHASE change for BASE
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 8, 31));
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 31), new LocalDate(2023, 9, 1), InvoiceItemType.RECURRING, new BigDecimal("32.26")));

        //Base CTD is 2023-09-01 - as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 9, 1));

        //ADDON CTD is still 2023-08-15 - as expected
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 15));

        //move clock to 2023-09-01 - RECURRING invoice for base
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 9, 1));
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 5, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 9, 1), new LocalDate(2023, 10, 1), InvoiceItemType.RECURRING, new BigDecimal("1000")));

        //Base CTD is 2023-10-01 - as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 10, 1));
        //ADDON CTD is still 2023-08-15 - as expected
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 15));

        // move clock to 2023-09-04  PHASE change for addon (since START_OF_SUBSCRIPTION change alignment is used)
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 9, 4));
        assertListenerStatus();

        //PRORATED invoice for ADDON since BCD is 1
        invoiceChecker.checkInvoice(account.getId(), 6, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 9, 4), new LocalDate(2023, 10, 1), InvoiceItemType.RECURRING, new BigDecimal("135")));

        //Base CTD is still 2023-10-01 - as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 10, 1));
        //ADDON CTD is 2023-10-01 - as expected
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 10, 1));
    }

    @Test(groups = "slow")
    public void testChangeAlignmentStartOfSubscriptionImmediateChange() throws Exception {
        final LocalDate today = new LocalDate(2023, 8, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create base 2023-08-01
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID baseEntId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        //Base CTD is 2023-08-01 as expected
        Subscription baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 1));

        clock.setDay(new LocalDate(2023, 8, 5));

        //create addon 2023-08-05
        final PlanPhaseSpecifier addonSpec = new PlanPhaseSpecifier("BasicAOStartOfBundleDefault-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID addonEntId = entitlementApi.addEntitlement(baseSub.getBundleId(), new DefaultEntitlementSpecifier(addonSpec), null, null, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 5), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        //ADDON CTD is 2023-08-05 as expected
        Subscription addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 5));

        clock.setDay(new LocalDate(2023, 8, 15));

        //change addon with start of subscription alignment and IMMEDIATE policy - change will happen immediately
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE);
        final PlanPhaseSpecifier newAddOnSpec = new PlanPhaseSpecifier("BasicAOStartOfSubscription-monthly");
        addonSub.changePlanOverrideBillingPolicy(new DefaultEntitlementSpecifier(newAddOnSpec), null, BillingActionPolicy.IMMEDIATE, Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 15), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        //Base CTD is still 2023-08-01 as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 1));

        //ADDON CTD is 2023-08-15, though I think technically this should be 2023-08-05 (aligned with old addon CTD), I think 2023-08-15 is acceptable
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 15));

        //move clock to 2023-08-31 - PHASE change for BASE
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 8, 31));
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 31), new LocalDate(2023, 9, 1), InvoiceItemType.RECURRING, new BigDecimal("32.26")));

        //Base CTD is 2023-09-01 - as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 9, 1));

        //ADDON CTD is still 2023-08-15 - as expected
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 15));

        //move clock to 2023-09-01 - RECURRING invoice for base
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 9, 1));
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 5, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 9, 1), new LocalDate(2023, 10, 1), InvoiceItemType.RECURRING, new BigDecimal("1000")));

        //Base CTD is 2023-10-01 - as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 10, 1));
        //ADDON CTD is still 2023-08-15 - as expected
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 15));

        // move clock to 2023-09-04  PHASE change for addon (since START_OF_SUBSCRIPTION change alignment is used)
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 9, 4));
        assertListenerStatus();

        //PRORATED invoice for ADDON since BCD is 1
        invoiceChecker.checkInvoice(account.getId(), 6, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 9, 4), new LocalDate(2023, 10, 1), InvoiceItemType.RECURRING, new BigDecimal("135")));

        //Base CTD is still 2023-10-01 - as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 10, 1));
        //ADDON CTD is 2023-10-01 - as expected
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 10, 1));
    }

    @Test(groups = "slow")
    public void testChangeAlignmentChangeOfPlanEOTChange() throws Exception {
        final LocalDate today = new LocalDate(2023, 8, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create base 2023-08-01
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID baseEntId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        //Base CTD is 2023-08-01 as expected
        Subscription baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 1));

        clock.setDay(new LocalDate(2023, 8, 5));

        //create addon 2023-08-05
        final PlanPhaseSpecifier addonSpec = new PlanPhaseSpecifier("BasicAOStartOfBundleDefault-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID addonEntId = entitlementApi.addEntitlement(baseSub.getBundleId(), new DefaultEntitlementSpecifier(addonSpec), null, null, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 5), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        //ADDON CTD is 2023-08-05 as expected
        Subscription addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 5));

        clock.setDay(new LocalDate(2023, 8, 15));

        //change addon with change of plan alignment on 2023-08-15 - change will happen immediately as subscription is in TRIAL phase
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE);
        final PlanPhaseSpecifier newAddOnSpec = new PlanPhaseSpecifier("BasicAOChangeOfPlan-monthly");
        addonSub.changePlan(new DefaultEntitlementSpecifier(newAddOnSpec), Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 15), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        //Base CTD is still 2023-08-01 as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 1));

        //ADDON CTD is 2023-08-15, though I think technically this should be 2023-08-05 (aligned with old addon CTD), I think 2023-08-15 is acceptable
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 15));

        //move clock to 2023-08-31 - PHASE change for BASE
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 8, 31));
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 31), new LocalDate(2023, 9, 1), InvoiceItemType.RECURRING, new BigDecimal("32.26")));

        //Base CTD is 2023-09-01 - as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 9, 1));

        //ADDON CTD is still 2023-08-15 - as expected
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 15));

        //move clock to 2023-09-01 - RECURRING invoice for base
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 9, 1));
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 5, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 9, 1), new LocalDate(2023, 10, 1), InvoiceItemType.RECURRING, new BigDecimal("1000")));

        //Base CTD is 2023-10-01 - as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 10, 1));
        //ADDON CTD is still 2023-08-15 - as expected
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 15));

        // move clock to 2023-09-14  PHASE change for addon (since CHANGE_OF_PLAN change alignment is used)
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 9, 14));
        assertListenerStatus();

        //PRORATED invoice for ADDON since BCD is 1
        invoiceChecker.checkInvoice(account.getId(), 6, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 9, 14), new LocalDate(2023, 10, 1), InvoiceItemType.RECURRING, new BigDecimal("113.33")));

        //Base CTD is still 2023-10-01 - as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 10, 1));
        //ADDON CTD is 2023-10-01 - as expected
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 10, 1));
    }


    @Test(groups = "slow")
    public void testChangeAlignmentChangeOfPlanImmediateChange() throws Exception {
        final LocalDate today = new LocalDate(2023, 8, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create base 2023-08-01
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID baseEntId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        //Base CTD is 2023-08-01 as expected
        Subscription baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 1));

        clock.setDay(new LocalDate(2023, 8, 5));

        //create addon 2023-08-05
        final PlanPhaseSpecifier addonSpec = new PlanPhaseSpecifier("BasicAOStartOfBundleDefault-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID addonEntId = entitlementApi.addEntitlement(baseSub.getBundleId(), new DefaultEntitlementSpecifier(addonSpec), null, null, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 5), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        //ADDON CTD is 2023-08-05 as expected
        Subscription addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 5));

        clock.setDay(new LocalDate(2023, 8, 15));

        //change addon with change of plan alignment and IMMEDIATE policy - change will happen immediately
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE);
        final PlanPhaseSpecifier newAddOnSpec = new PlanPhaseSpecifier("BasicAOChangeOfPlan-monthly");
        addonSub.changePlanOverrideBillingPolicy(new DefaultEntitlementSpecifier(newAddOnSpec), null, BillingActionPolicy.IMMEDIATE, Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 15), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        //Base CTD is still 2023-08-01 as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 1));

        //ADDON CTD is 2023-08-15, though I think technically this should be 2023-08-05 (aligned with old addon CTD), I think 2023-08-15 is acceptable
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 15));

        //move clock to 2023-08-31 - PHASE change for BASE
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 8, 31));
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 31), new LocalDate(2023, 9, 1), InvoiceItemType.RECURRING, new BigDecimal("32.26")));

        //Base CTD is 2023-09-01 - as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 9, 1));

        //ADDON CTD is still 2023-08-15 - as expected
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 15));

        //move clock to 2023-09-01 - RECURRING invoice for base
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 9, 1));
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 5, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 9, 1), new LocalDate(2023, 10, 1), InvoiceItemType.RECURRING, new BigDecimal("1000")));

        //Base CTD is 2023-10-01 - as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 10, 1));
        //ADDON CTD is still 2023-08-15 - as expected
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 15));

        // move clock to 2023-09-14  PHASE change for addon (since CHANGE_OF_PLAN change alignment is used)
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 9, 14));
        assertListenerStatus();

        //PRORATED invoice for ADDON since BCD is 1
        invoiceChecker.checkInvoice(account.getId(), 6, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 9, 14), new LocalDate(2023, 10, 1), InvoiceItemType.RECURRING, new BigDecimal("113.33")));

        //Base CTD is still 2023-10-01 - as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 10, 1));
        //ADDON CTD is 2023-10-01 - as expected
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 10, 1));
    }

}
