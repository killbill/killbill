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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestCatalogFixedTermExpiry extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testCatalogFixedTermExpiry");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1930")
    public void testSimpleFixedTermExpiry() throws Exception {

        final LocalDate today = new LocalDate(2023, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-3-months");
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

        //Move to 2023-03-01 - Subscription is still active
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(2);
        assertListenerStatus();
        entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

        //Move to 2024-04-01 - Subscription has expired
        busHandler.pushExpectedEvents(NextEvent.EXPIRED, NextEvent.NULL_INVOICE);
        clock.addMonths(1);
        assertListenerStatus();
        entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.EXPIRED);

        checkNoMoreInvoiceToGenerate(account.getId(), callContext);

    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1930")
    public void testBaseAndAddOnExpiryOnSameDay() throws Exception {

        final LocalDate today = new LocalDate(2023, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create base
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-3-months");
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, Collections.emptyList(), callContext);
        Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertListenerStatus();

        //create addon
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final PlanPhaseSpecifier addonSpec = new PlanPhaseSpecifier("refurbish-maintenance-3-months");
        final UUID addonEntitlementId = entitlementApi.addEntitlement(entitlement.getBundleId(), new DefaultEntitlementSpecifier(addonSpec), null, null, false, Collections.emptyList(), callContext);
        Entitlement addonEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addonEntitlement.getState(), EntitlementState.ACTIVE);
        assertListenerStatus();

        //Move to 2023-03-01 - Both base and addon are active
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(2);//2023-03-01
        assertListenerStatus();
        entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        addonEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addonEntitlement.getState(), EntitlementState.ACTIVE);

        //Move to 2024-04-01 - Both base and addon have expired
        busHandler.pushExpectedEvents(NextEvent.EXPIRED, NextEvent.EXPIRED, NextEvent.NULL_INVOICE);
        clock.addMonths(1);//2024-04-01
        assertListenerStatus();
        entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.EXPIRED);
        addonEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addonEntitlement.getState(), EntitlementState.EXPIRED);
        checkNoMoreInvoiceToGenerate(account.getId(), callContext);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1930")
    public void testAddonExpiryBeforeBase() throws Exception {

        final LocalDate today = new LocalDate(2023, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //Create base
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-3-months");
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, Collections.emptyList(), callContext);
        Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertListenerStatus();

        //Create addon
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final PlanPhaseSpecifier addonSpec = new PlanPhaseSpecifier("refurbish-maintenance-2-months");
        final UUID addonEntitlementId = entitlementApi.addEntitlement(entitlement.getBundleId(), new DefaultEntitlementSpecifier(addonSpec), null, null, false, Collections.emptyList(), callContext);
        Entitlement addonEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addonEntitlement.getState(), EntitlementState.ACTIVE);
        assertListenerStatus();

        //Move to 2023-03-01 - Addon has expired, Base is still active
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.EXPIRED);
        clock.addMonths(2);
        assertListenerStatus();
        entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        addonEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addonEntitlement.getState(), EntitlementState.EXPIRED);

        //Move to 2024-04-01 - Both base and addon have expired
        busHandler.pushExpectedEvents(NextEvent.EXPIRED, NextEvent.NULL_INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.EXPIRED);
        addonEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addonEntitlement.getState(), EntitlementState.EXPIRED);
        checkNoMoreInvoiceToGenerate(account.getId(), callContext);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1930")
    public void testAddonExpiryAfterBase() throws Exception {

        final LocalDate today = new LocalDate(2023, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //Create base
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-3-months");
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, Collections.emptyList(), callContext);
        Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertListenerStatus();

        //Create addon
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final PlanPhaseSpecifier addonSpec = new PlanPhaseSpecifier("refurbish-maintenance-6-months");
        final UUID addonEntitlementId = entitlementApi.addEntitlement(entitlement.getBundleId(), new DefaultEntitlementSpecifier(addonSpec), null, null, false, Collections.emptyList(), callContext);
        Entitlement addonEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addonEntitlement.getState(), EntitlementState.ACTIVE);
        assertListenerStatus();

        //Move to 2023-03-01 - Both base and addon are active
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(2);
        assertListenerStatus();
        entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        addonEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addonEntitlement.getState(), EntitlementState.ACTIVE);
        checkNoMoreInvoiceToGenerate(account.getId(), callContext);

        //Move to 2024-04-01 - Both base and addon have expired. Though addon expiry date is not reached, it expires on base expiry date
        busHandler.pushExpectedEvents(NextEvent.EXPIRED, NextEvent.EXPIRED, NextEvent.NULL_INVOICE);
        clock.addMonths(1);//2023-04-01
        assertListenerStatus();

        entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.EXPIRED);
        addonEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addonEntitlement.getState(), EntitlementState.EXPIRED);
        checkNoMoreInvoiceToGenerate(account.getId(), callContext);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1930")
    //This test does not work,causes a NPE
    public void testFixedTermBaseEvergreenAddOn() throws Exception {

        final LocalDate today = new LocalDate(2023, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //Create base
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-3-months");
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, Collections.emptyList(), callContext);
        Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertListenerStatus();

        //create addon
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final PlanPhaseSpecifier addonSpec = new PlanPhaseSpecifier("refurbish-maintenance-no-trial");
        final UUID addonEntitlementId = entitlementApi.addEntitlement(entitlement.getBundleId(), new DefaultEntitlementSpecifier(addonSpec), null, null, false, Collections.emptyList(), callContext);
        Entitlement addonEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addonEntitlement.getState(), EntitlementState.ACTIVE);
        assertListenerStatus();

        //Move to 2023-03-01 - Both base and addon are active
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(2);
        assertListenerStatus();
        entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        addonEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addonEntitlement.getState(), EntitlementState.ACTIVE);

        //Move to 2023-03-01 - Both base and addon have expired. Although addon is recurring, it expires along with base
        busHandler.pushExpectedEvents(NextEvent.EXPIRED, NextEvent.EXPIRED, NextEvent.NULL_INVOICE);
        clock.addMonths(1);//2023-04-01
        assertListenerStatus();

        entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.EXPIRED);
        addonEntitlement = entitlementApi.getEntitlementForId(addonEntitlementId, false, callContext);
        assertEquals(addonEntitlement.getState(), EntitlementState.EXPIRED);

        checkNoMoreInvoiceToGenerate(account.getId(), callContext);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1930")
    public void testCancelSubscriptionBeforeExpiry() throws Exception {

        final LocalDate today = new LocalDate(2023, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create subscription
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-3-months");
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, Collections.emptyList(), callContext);
        Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertListenerStatus();

        //Move to 2023-01-11 and cancel subscription - subscription is in CANCELLED state
        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.INVOICE);
        clock.addDays(10);
        entitlement.cancelEntitlementWithDate(clock.getUTCNow(), clock.getUTCNow(), Collections.emptyList(), callContext);
        assertListenerStatus();

        entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.CANCELLED);

        //move to 2023-04-11 (datepast fixed term expiry) - subscription remains CANCELLED
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addMonths(3);
        assertListenerStatus();

        entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.CANCELLED);

        checkNoMoreInvoiceToGenerate(account.getId(), callContext);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1930")
    public void testTransitionToFixedTerm() throws Exception {

        final LocalDate today = new LocalDate(2023, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create subscription
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-trial-and-fixed-term-3-months");
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, Collections.emptyList(), callContext);
        Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertListenerStatus();

        //Move to 2023-02-01 - TRIAL ends
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();
        entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

        //Move to 2024-04-01 - subscription is active
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(2);
        assertListenerStatus();
        entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

        //Move to 2024-05-01 - Subscription has expired
        busHandler.pushExpectedEvents(NextEvent.EXPIRED, NextEvent.NULL_INVOICE);
        clock.addMonths(1);
        assertListenerStatus();
        entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.EXPIRED);

        checkNoMoreInvoiceToGenerate(account.getId(), callContext);

    }
}
