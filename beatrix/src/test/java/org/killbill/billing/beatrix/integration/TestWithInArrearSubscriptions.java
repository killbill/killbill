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

package org.killbill.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestWithInArrearSubscriptions extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testWithInArrearSubscriptions");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow")
    public void testWithCancelation() throws Exception {
        final DateTime initialCreationDate = new DateTime(2020, 1, 1, 0, 0, 0, 0, testTimeZone);
        clock.setTime(initialCreationDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-support-monthly-notrial", null);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null, null), "bundleExternalKey", null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);

        // 2020-02-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 1, 1), new LocalDate(2020, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

        // 2020-03-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 2, 1), new LocalDate(2020, 3, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

        // Cancel 2020-03-01
        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        entitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, Collections.emptyList(), callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        for (int i = 0; i < 7; i++) {
            clock.addMonths(1);
            assertListenerStatus();
        }

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow")
    public void testWithPauseResume() throws Exception {
        final DateTime initialDate = new DateTime(2020, 1, 1, 0, 3, 42, 0, testTimeZone);

        // set clock to the initial start date
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));
        final UUID accountId = account.getId();
        assertNotNull(account);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-support-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null, null), "bundleExternalKey", null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        // 2020-01-15
        clock.addDays(14);

        // Pause subscription. System will invoice for 2020-01-01 -> 2020-01-15
        final DefaultEntitlement entitlement = (DefaultEntitlement) entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        entitlementApi.pause(entitlement.getBundleId(), clock.getUTCNow().toLocalDate(), Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 1, 1), new LocalDate(2020, 1, 15), InvoiceItemType.RECURRING, new BigDecimal("45.16")));

        // 2020-01-20
        clock.addDays(5);

        // Resume subscription.  System will invoice for remaining 2020-01-20 -> 2020-02-1
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        entitlementApi.resume(entitlement.getBundleId(), clock.getUTCNow().toLocalDate(), Collections.emptyList(), callContext);
        assertListenerStatus();

        // 2020-02-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(12);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 1, 20), new LocalDate(2020, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("38.71")));

        // 2020-03-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow")
    public void testChangeBPToPlanWithoutAddOnWithEndOfTermPolicyDuringFirstMonth() throws Exception {

        final LocalDate today = new LocalDate(2012, 4, 1);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        // CREATE BASE SUBSCRIPTION. SINCE IN_ARREAR BILLING IS USED, INVOICE IS NOT GENERATED
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("premium-support-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID bpEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null, null), "bundleExternalKey", null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        Entitlement bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlementId, false, callContext);
        assertNotNull(bpEntitlement);
        assertEquals(bpEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(bpEntitlement.getLastActivePlan().getName(), "premium-support-monthly-notrial");
        checkNoMoreInvoiceToGenerate(account);

        //CREATE ADDON SUBSCRIPTION. SINCE IN_ARREAR BILLING IS USED, INVOICE IS NOT GENERATED
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("premium-support-addon-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID addonEntitlementId = entitlementApi.addEntitlement(bpEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec), today, today, false, Collections.emptyList(), callContext);
        Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addOnEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(addOnEntitlement.getLastActivePlan().getName(), "premium-support-addon-monthly-notrial");
        assertListenerStatus();
        checkNoMoreInvoiceToGenerate(account);

        //MOVE CLOCK BY FEW DAYS
        clock.addDays(10);

        //CHANGE PLAN WITH END_OF_TERM POLICY
        final PlanPhaseSpecifier newPlanSpec = new PlanPhaseSpecifier("basic-support-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.NULL_INVOICE); //TODO Why is NULL_INVOICE requires here? AFAIK, this event is required only when an invoice run does not generate an invoice and in this case an invoice is generated
        bpEntitlement.changePlanOverrideBillingPolicy(new DefaultEntitlementSpecifier(newPlanSpec), null, BillingActionPolicy.END_OF_TERM, null, callContext);
        assertListenerStatus();
        bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlement.getId(), false, callContext);
        assertEquals(bpEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(bpEntitlement.getLastActivePlan().getName(), "basic-support-monthly-notrial");
        addOnEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addOnEntitlement.getState(), EntitlementState.CANCELLED);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 4, 11), InvoiceItemType.RECURRING, new BigDecimal("300.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 4, 11), InvoiceItemType.RECURRING, new BigDecimal("333.33")));

        checkNoMoreInvoiceToGenerate(account);

    }

    @Test(groups = "slow")
    public void testChangeBPToPlanWithoutAddOnWithEndOfTermPolicyAfterFirstMonth() throws Exception {

        final LocalDate today = new LocalDate(2012, 4, 1);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        // CREATE BASE SUBSCRIPTION. SINCE IN_ARREAR BILLING IS USED, INVOICE IS NOT GENERATED
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("premium-support-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID bpEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null, null), "bundleExternalKey", null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        Entitlement bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlementId, false, callContext);
        assertNotNull(bpEntitlement);
        assertEquals(bpEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(bpEntitlement.getLastActivePlan().getName(), "premium-support-monthly-notrial");
        checkNoMoreInvoiceToGenerate(account);

        //CREATE ADDON SUBSCRIPTION. SINCE IN_ARREAR BILLING IS USED, INVOICE IS NOT GENERATED
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("premium-support-addon-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID addonEntitlementId = entitlementApi.addEntitlement(bpEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec), today, today, false, Collections.emptyList(), callContext);
        Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addOnEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(addOnEntitlement.getLastActivePlan().getName(), "premium-support-addon-monthly-notrial");
        assertListenerStatus();
        checkNoMoreInvoiceToGenerate(account);

        //MOVE CLOCK BY 1 MONTH. INVOICE GENERATED FOR BOTH BASE AND ADDON SUBSCRIPTION
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("900.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("1000.00")));

        //MOVE CLOCK BY FEW DAYS
        clock.addDays(10);

        //CHANGE PLAN WITH END_OF_TERM POLICY
        final PlanPhaseSpecifier newPlanSpec = new PlanPhaseSpecifier("basic-support-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.NULL_INVOICE); //TODO Why is NULL_INVOICE requires here? AFAIK, this event is required only when an invoice run does not generate an invoice and in this case an invoice is generated
        bpEntitlement.changePlanOverrideBillingPolicy(new DefaultEntitlementSpecifier(newPlanSpec), null, BillingActionPolicy.END_OF_TERM, null, callContext);
        assertListenerStatus();
        bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlement.getId(), false, callContext);
        assertEquals(bpEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(bpEntitlement.getLastActivePlan().getName(), "basic-support-monthly-notrial");
        addOnEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addOnEntitlement.getState(), EntitlementState.CANCELLED);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 11), InvoiceItemType.RECURRING, new BigDecimal("290.32")), //TODO verify these amounts
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 11), InvoiceItemType.RECURRING, new BigDecimal("322.58")));
    }

    @Test(groups = "slow")
    public void testChangeBPToPlanWithoutAddOnWithEndOfTermDate() throws Exception {

        final LocalDate today = new LocalDate(2012, 4, 1);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        // CREATE BASE SUBSCRIPTION. SINCE IN_ARREAR BILLING IS USED, INVOICE IS NOT GENERATED
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("premium-support-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID bpEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null, null), "bundleExternalKey", null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        Entitlement bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlementId, false, callContext);
        assertNotNull(bpEntitlement);
        assertEquals(bpEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(bpEntitlement.getLastActivePlan().getName(), "premium-support-monthly-notrial");
        checkNoMoreInvoiceToGenerate(account);

        //CREATE ADDON SUBSCRIPTION. SINCE IN_ARREAR BILLING IS USED, INVOICE IS NOT GENERATED
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("premium-support-addon-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID addonEntitlementId = entitlementApi.addEntitlement(bpEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec), today, today, false, Collections.emptyList(), callContext);
        Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addOnEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(addOnEntitlement.getLastActivePlan().getName(), "premium-support-addon-monthly-notrial");
        assertListenerStatus();
        checkNoMoreInvoiceToGenerate(account);

        //MOVE CLOCK
        clock.addDays(10);

        //CHANGE PLAN WITH DATE AS END OF TERM DATE
        final PlanPhaseSpecifier newPlanSpec = new PlanPhaseSpecifier("basic-support-monthly-notrial", null);
        final LocalDate effectiveDate = new LocalDate(2012, 5, 1);
        bpEntitlement.changePlanWithDate(new DefaultEntitlementSpecifier(newPlanSpec), effectiveDate, null, callContext);

        //MOVE CLOCK TO EFFECTIVE DATE
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.NULL_INVOICE); //TODO Why is NULL_INVOICE requires here? AFAIK, this event is required only when an invoice run does not generate an invoice and in this case an invoice is generated
        clock.addDays(20);
        assertListenerStatus();

        bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlement.getId(), false, callContext);
        assertEquals(bpEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(bpEntitlement.getLastActivePlan().getName(), "basic-support-monthly-notrial");
        addOnEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addOnEntitlement.getState(), EntitlementState.CANCELLED);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("900.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("1000.00")));

    }

    @Test(groups = "slow", enabled = false)
    public void testChangeBPToPlanWithoutAddOnWithStartOfTermPolicyDuringFirstMonth() throws Exception {

        final LocalDate today = new LocalDate(2012, 4, 1);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        // CREATE BASE SUBSCRIPTION. SINCE IN_ARREAR BILLING IS USED, INVOICE IS NOT GENERATED
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("premium-support-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID bpEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null, null), "bundleExternalKey", null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        Entitlement bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlementId, false, callContext);
        assertNotNull(bpEntitlement);
        assertEquals(bpEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(bpEntitlement.getLastActivePlan().getName(), "premium-support-monthly-notrial");
        checkNoMoreInvoiceToGenerate(account);

        //CREATE ADDON SUBSCRIPTION. SINCE IN_ARREAR BILLING IS USED, INVOICE IS NOT GENERATED
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("premium-support-addon-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID addonEntitlementId = entitlementApi.addEntitlement(bpEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec), today, today, false, Collections.emptyList(), callContext);
        Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addOnEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(addOnEntitlement.getLastActivePlan().getName(), "premium-support-addon-monthly-notrial");
        assertListenerStatus();
        checkNoMoreInvoiceToGenerate(account);

        //MOVE CLOCK
        clock.addDays(10);

        //CHANGE PLAN WITH START_OF_TERM POLICY
        final PlanPhaseSpecifier newPlanSpec = new PlanPhaseSpecifier("basic-support-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE); //TODO: should this be CHANGE instead of CREATE? Also, need to check if other events are correct once bug is fixed
        bpEntitlement.changePlanOverrideBillingPolicy(new DefaultEntitlementSpecifier(newPlanSpec), null, BillingActionPolicy.START_OF_TERM, null, callContext);
        assertListenerStatus();
        bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlement.getId(), false, callContext);
        assertEquals(bpEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(bpEntitlement.getLastActivePlan().getName(), "basic-support-monthly-notrial");
        addOnEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addOnEntitlement.getState(), EntitlementState.CANCELLED);
        checkNoMoreInvoiceToGenerate(account);

    }

    @Test(groups = "slow", enabled = false)
    public void testChangeBPToPlanWithoutAddOnWithStartOfTermPolicyAfterFirstMonth() throws Exception {

        final LocalDate today = new LocalDate(2012, 4, 1);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        // CREATE BASE SUBSCRIPTION. SINCE IN_ARREAR BILLING IS USED, INVOICE IS NOT GENERATED
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("premium-support-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID bpEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null, null), "bundleExternalKey", null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        Entitlement bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlementId, false, callContext);
        assertNotNull(bpEntitlement);
        assertEquals(bpEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(bpEntitlement.getLastActivePlan().getName(), "premium-support-monthly-notrial");
        checkNoMoreInvoiceToGenerate(account);

        //CREATE ADDON SUBSCRIPTION. SINCE IN_ARREAR BILLING IS USED, INVOICE IS NOT GENERATED
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("premium-support-addon-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID addonEntitlementId = entitlementApi.addEntitlement(bpEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec), today, today, false, Collections.emptyList(), callContext);
        Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addOnEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(addOnEntitlement.getLastActivePlan().getName(), "premium-support-addon-monthly-notrial");
        assertListenerStatus();
        checkNoMoreInvoiceToGenerate(account);

        //MOVE CLOCK BY 1 MONTH
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("900.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("1000.00")));

        //MOVE CLOCK
        clock.addDays(10);

        //CHANGE PLAN WITH START_OF_TERM POLICY
        final PlanPhaseSpecifier newPlanSpec = new PlanPhaseSpecifier("basic-support-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE); //TODO: should this be CHANGE instead of CREATE? Also, need to check if other events are correct once bug is fixed
        bpEntitlement.changePlanOverrideBillingPolicy(new DefaultEntitlementSpecifier(newPlanSpec), null, BillingActionPolicy.START_OF_TERM, null, callContext);
        assertListenerStatus();
        bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlement.getId(), false, callContext);
        assertEquals(bpEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(bpEntitlement.getLastActivePlan().getName(), "basic-support-monthly-notrial");
        addOnEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addOnEntitlement.getState(), EntitlementState.CANCELLED);
        checkNoMoreInvoiceToGenerate(account);

    }

    @Test(groups = "slow", enabled = false)
    public void testChangeBPToPlanWithoutAddOnWithStartOfTermDate() throws Exception {

        final LocalDate today = new LocalDate(2012, 4, 1);
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime(DateTimeZone.UTC).getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        // CREATE BASE SUBSCRIPTION. SINCE IN_ARREAR BILLING IS USED, INVOICE IS NOT GENERATED
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("premium-support-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID bpEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null, null), "bundleExternalKey", null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        Entitlement bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlementId, false, callContext);
        assertNotNull(bpEntitlement);
        assertEquals(bpEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(bpEntitlement.getLastActivePlan().getName(), "premium-support-monthly-notrial");
        checkNoMoreInvoiceToGenerate(account);

        //CREATE ADDON SUBSCRIPTION. SINCE IN_ARREAR BILLING IS USED, INVOICE IS NOT GENERATED
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("premium-support-addon-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID addonEntitlementId = entitlementApi.addEntitlement(bpEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec), today, today, false, Collections.emptyList(), callContext);
        Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addOnEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(addOnEntitlement.getLastActivePlan().getName(), "premium-support-addon-monthly-notrial");
        assertListenerStatus();
        checkNoMoreInvoiceToGenerate(account);

        //MOVE CLOCK
        clock.addDays(10);

        //CHANGE PLAN WITH DATE AS START OF TERM DATE
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.CANCEL, NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE); //TODO: should this be CHANGE instead of CREATE? Also, need to check if other events are correct once bug is fixed
        final PlanPhaseSpecifier newPlanSpec = new PlanPhaseSpecifier("basic-support-monthly-notrial", null);
        final LocalDate effectiveDate = new LocalDate(2012, 4, 1);
        bpEntitlement.changePlanWithDate(new DefaultEntitlementSpecifier(newPlanSpec), effectiveDate, null, callContext);
        assertListenerStatus();

        bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlement.getId(), false, callContext);
        assertEquals(bpEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(bpEntitlement.getLastActivePlan().getName(), "basic-support-monthly-notrial");
        addOnEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addOnEntitlement.getState(), EntitlementState.CANCELLED);
        checkNoMoreInvoiceToGenerate(account);
    }

}
