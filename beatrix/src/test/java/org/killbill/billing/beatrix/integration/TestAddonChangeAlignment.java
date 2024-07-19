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
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Subscription;
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

    @Test(groups = "slow")
    public void testChangeAlignmentStartOfBundleEOTChange() throws Exception {
        final LocalDate today = new LocalDate(2023, 8, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create base 2024-08-01
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID baseEntId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Base CTD is 2023-08-31 as expected
        Subscription baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        clock.setDay(new LocalDate(2023, 8, 5));

        //create addon 2023-08-05
        final PlanPhaseSpecifier addonSpec = new PlanPhaseSpecifier("BasicAO-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID addonEntId = entitlementApi.addEntitlement(baseSub.getBundleId(), new DefaultEntitlementSpecifier(addonSpec), null, null, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //ADDON CTD is 2023-08-31 as expected
        Subscription addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        clock.setDay(new LocalDate(2023, 8, 15));

        //change addon with start of bundle alignment on 2023-08-15 - change will not happen immediately
        final PlanPhaseSpecifier newAddOnSpec = new PlanPhaseSpecifier("BasicAOStartOfBundle-monthly");
        addonSub.changePlan(new DefaultEntitlementSpecifier(newAddOnSpec), Collections.emptyList(), callContext);

        //Base CTD is still 2023-08-31 as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        //ADDON CTD is still 2023-08-31 as expected
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        //move clock to 2023-08-31 - Phase change for BASE, ADDON plan CHANGE takes effect
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 8, 31));
        assertListenerStatus();

        // BASE CTD set to 2023-09-01 - Not sure if this is correct, shouldn't it be 2023-08-31??
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 9, 1));

        // TEST fails here, ADDON CTD is 2023-09-04, I would expect it to be 2023-08-31 since START_OF_BUNDLE is used
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

    }

    @Test(groups = "slow")
    public void testChangeAlignmentStartOfBundleImmediateChange() throws Exception {
        final LocalDate today = new LocalDate(2023, 8, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create base 2024-08-01
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID baseEntId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Base CTD is 2023-08-31 as expected
        Subscription baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        clock.setDay(new LocalDate(2023, 8, 5));

        //create addon 2023-08-05
        final PlanPhaseSpecifier addonSpec = new PlanPhaseSpecifier("BasicAO-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID addonEntId = entitlementApi.addEntitlement(baseSub.getBundleId(), new DefaultEntitlementSpecifier(addonSpec), null, null, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //ADDON CTD is 2023-08-31 as expected
        Subscription addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        clock.setDay(new LocalDate(2023, 8, 15));

        //change addon with start of bundle alignment with requestedDate=2023-08-15 - change will happen immediately
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE);
        final PlanPhaseSpecifier newAddOnSpec = new PlanPhaseSpecifier("BasicAOStartOfBundle-monthly");
        //addonSub.changePlanWithDate(new DefaultEntitlementSpecifier(newAddOnSpec), clock.getUTCToday(), Collections.emptyList(), callContext);
        addonSub.changePlanOverrideBillingPolicy(new DefaultEntitlementSpecifier(newAddOnSpec), null, BillingActionPolicy.IMMEDIATE, Collections.emptyList(), callContext);
        assertListenerStatus();

        // BASE CTD set to 2023-08-31 - as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        // TEST fails here, ADDON CTD is 2023-09-04, I would expect it to be 2023-08-31 since START_OF_BUNDLE is used
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));
    }


    @Test(groups = "slow")
    public void testChangeAlignmentStartOfSubscriptionEOTChange() throws Exception {
        final LocalDate today = new LocalDate(2023, 8, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create base 2024-08-01
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID baseEntId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Base CTD is 2023-08-31 as expected
        Subscription baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        clock.setDay(new LocalDate(2023, 8, 5));

        //create addon 2023-08-05
        final PlanPhaseSpecifier addonSpec = new PlanPhaseSpecifier("BasicAO-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID addonEntId = entitlementApi.addEntitlement(baseSub.getBundleId(), new DefaultEntitlementSpecifier(addonSpec), null, null, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //ADDON CTD is 2023-08-31 as expected
        Subscription addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        clock.setDay(new LocalDate(2023, 8, 15));

        //change addon with start of subscription alignment on 2023-08-15 - change will not happen immediately
        final PlanPhaseSpecifier newAddOnSpec = new PlanPhaseSpecifier("BasicAOStartOfSubscription-monthly");
        addonSub.changePlan(new DefaultEntitlementSpecifier(newAddOnSpec), Collections.emptyList(), callContext);

        //Base CTD is still 2023-08-31 as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        //ADDON CTD is still 2023-08-31 as expected
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        //move clock to 2023-08-31 - PHASE change for BASE, Plan CHANGE for ADDON
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 8, 31));
        assertListenerStatus();

        //Base CTD is 2023-09-01 - Not sure if this is correct
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 9, 1));

        //ADDON CTD is 2023-09-04 - as expected
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 9, 4));

    }

    @Test(groups = "slow")
    public void testChangeAlignmentStartOfSubscriptionImmediateChange() throws Exception {
        final LocalDate today = new LocalDate(2023, 8, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create base 2024-08-01
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID baseEntId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Base CTD is 2023-08-31 as expected
        Subscription baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        clock.setDay(new LocalDate(2023, 8, 5));

        //create addon 2023-08-05
        final PlanPhaseSpecifier addonSpec = new PlanPhaseSpecifier("BasicAO-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID addonEntId = entitlementApi.addEntitlement(baseSub.getBundleId(), new DefaultEntitlementSpecifier(addonSpec), null, null, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //ADDON CTD is 2023-08-31 as expected
        Subscription addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        clock.setDay(new LocalDate(2023, 8, 15));

        //change addon with start of subscription alignment with requestedDate=2023-08-15 - change will happen immediately
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE);
        final PlanPhaseSpecifier newAddOnSpec = new PlanPhaseSpecifier("BasicAOStartOfSubscription-monthly");
        addonSub.changePlanWithDate(new DefaultEntitlementSpecifier(newAddOnSpec), clock.getUTCToday(), Collections.emptyList(), callContext);
        assertListenerStatus();

        //Base CTD is still 2023-08-31 as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        //ADDON CTD is 2023-09-04 - as expected
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 9, 4));

        //move clock to 2023-08-31 - PHASE change for BASE
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT); //Need another NextEvent.PHASE for ADDON??
        clock.setDay(new LocalDate(2023, 8, 31));
        assertListenerStatus();

        //Base CTD is 2023-09-01 - Not sure if this is correct
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 9, 1));

        //ADDON CTD is 2023-09-04 - Not sure if this is correct
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 9, 4));

    }


    @Test(groups = "slow")
    public void testChangeAlignmentChangeOfPlanEOTChange() throws Exception {
        final LocalDate today = new LocalDate(2023, 8, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create base 2024-08-01
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID baseEntId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Base CTD is 2023-08-31 as expected
        Subscription baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        clock.setDay(new LocalDate(2023, 8, 5));

        //create addon 2023-08-05
        final PlanPhaseSpecifier addonSpec = new PlanPhaseSpecifier("BasicAO-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID addonEntId = entitlementApi.addEntitlement(baseSub.getBundleId(), new DefaultEntitlementSpecifier(addonSpec), null, null, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //ADDON CTD is 2023-08-31 as expected
        Subscription addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        clock.setDay(new LocalDate(2023, 8, 15));

        //change addon with change of plan alignment on 2023-08-15 - change will not happen immediately
        final PlanPhaseSpecifier newAddOnSpec = new PlanPhaseSpecifier("BasicAOChangeOfPlan-monthly");
        addonSub.changePlan(new DefaultEntitlementSpecifier(newAddOnSpec), Collections.emptyList(), callContext);

        //Base CTD is still 2023-08-31 as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        //ADDON CTD is still 2023-08-31 as expected
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        //move clock to 2023-08-31 - PHASE change for BASE, Plan CHANGE for ADDON
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 8, 31));
        assertListenerStatus();

        //Base CTD is 2023-09-01 - Not sure if this is correct
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 9, 1));

        //ADDON CTD is 2023-09-30 - as expected (although CHANGE_OF_PLAN is used, the plan change takes effect at END_OF_TERM that is on 2023-08-31, hence ADDON CTD is 2023-09-30)
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 9, 30));

    }

    @Test(groups = "slow")
    public void testChangeAlignmentChangeOfPlanImmediateChange() throws Exception {
        final LocalDate today = new LocalDate(2023, 8, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create base 2024-08-01
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID baseEntId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Base CTD is 2023-08-31 as expected
        Subscription baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        clock.setDay(new LocalDate(2023, 8, 5));

        //create addon 2023-08-05
        final PlanPhaseSpecifier addonSpec = new PlanPhaseSpecifier("BasicAO-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID addonEntId = entitlementApi.addEntitlement(baseSub.getBundleId(), new DefaultEntitlementSpecifier(addonSpec), null, null, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //ADDON CTD is 2023-08-31 as expected
        Subscription addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        clock.setDay(new LocalDate(2023, 8, 15));

        //change addon with change of plan alignment with requestedDate=2023-08-15 - change will happen immediately
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE);
        final PlanPhaseSpecifier newAddOnSpec = new PlanPhaseSpecifier("BasicAOChangeOfPlan-monthly");
        addonSub.changePlanWithDate(new DefaultEntitlementSpecifier(newAddOnSpec), clock.getUTCToday(), Collections.emptyList(), callContext);
        assertListenerStatus();

        //Base CTD is still 2023-08-31 as expected
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        //ADDON CTD is still 2023-09-14 as expected
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 9, 14));

        //move clock to 2023-08-31 - PHASE change for BASE
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT); //Need another NextEvent.PHASE for ADDON??
        clock.setDay(new LocalDate(2023, 8, 31));
        assertListenerStatus();

        //Base CTD is 2023-09-01 - Not sure if this is correct
        baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 9, 1));

        //ADDON CTD is 2023-09-14 - as expected
        addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 9, 14));

    }



}
