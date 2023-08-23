/*
 * Copyright 2020-2023 Equinix, Inc
 * Copyright 2014-2023 The Billing Project, LLC
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
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.annotations.Test;

public class TestInArrearChangeCancel extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testInArrearChangeCancel");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1907")
    public void testCancelAddOnWithoutChange() throws Exception { //Create and cancel add on - Works as expected

        final LocalDate today = new LocalDate(2023, 8, 11);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(11));

        // CREATE BASE SUBSCRIPTION. SINCE IN_ARREAR BILLING IS USED, INVOICE IS NOT GENERATED
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("premium-support-monthly-notrial");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID bpEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null, null), "bundleExternalKey", null, null, false, true, Collections.emptyList(), callContext);
        final Entitlement bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlementId, false, callContext);
        assertListenerStatus();

        //CREATE ADDON SUBSCRIPTION. SINCE IN_ARREAR BILLING IS USED, INVOICE IS NOT GENERATED
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("premium-support-addon-monthly-notrial");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID addonEntitlementId = entitlementApi.addEntitlement(bpEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec), today, today, false, Collections.emptyList(), callContext);
        Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertListenerStatus();
        checkNoMoreInvoiceToGenerate(account);

        //move clock to 2023-08-20 and cancel ADDON - Invoice is generated as expected
        clock.setDay(new LocalDate(2023, 8, 20));
        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        addOnEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        addOnEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 11), new LocalDate(2023, 8, 20), InvoiceItemType.RECURRING, new BigDecimal("261.29")));

    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1907")
    public void testCancelAddOnAfterChangePlan() throws Exception {

        final LocalDate today = new LocalDate(2023, 8, 11);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(11));

        // CREATE BASE SUBSCRIPTION. SINCE IN_ARREAR BILLING IS USED, INVOICE IS NOT GENERATED
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("premium-support-monthly-notrial");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID bpEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null, null), "bundleExternalKey", null, null, false, true, Collections.emptyList(), callContext);
        final Entitlement bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlementId, false, callContext);
        assertListenerStatus();

        //CREATE ADDON SUBSCRIPTION. SINCE IN_ARREAR BILLING IS USED, INVOICE IS NOT GENERATED
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("premium-support-addon-monthly-notrial");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID addonEntitlementId = entitlementApi.addEntitlement(bpEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec), today, today, false, Collections.emptyList(), callContext);
        final Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertListenerStatus();
        checkNoMoreInvoiceToGenerate(account);

        //move clock to 2023-08-15 and upgrade ADDON - Prorated invoice is generated
        clock.setDay(new LocalDate(2023, 8, 15));
        final PlanPhaseSpecifier addOnSpec2 = new PlanPhaseSpecifier("premium-support-addon2-monthly-notrial");
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        addOnEntitlement.changePlanOverrideBillingPolicy(new DefaultEntitlementSpecifier(addOnSpec2), null, BillingActionPolicy.IMMEDIATE, Collections.emptyList(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 11), new LocalDate(2023, 8, 15), InvoiceItemType.RECURRING, new BigDecimal("116.13")));

        // Move clock to 2023-08-20 and cancel ADDON
        clock.setDay(new LocalDate(2023, 8, 20));
        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final Entitlement addOnEntitlement2 = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        addOnEntitlement2.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, Collections.emptyList(), callContext);
        assertListenerStatus();
    }

}
