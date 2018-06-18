/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.entitlement.api;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementSourceType;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestDefaultEntitlement extends EntitlementTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testCancelWithEntitlementDate() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(planPhaseSpecifier), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement.getSourceType(), EntitlementSourceType.NATIVE);

        clock.addDays(5);

        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK);
        entitlement.cancelEntitlementWithDate(null, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        assertEquals(entitlement2.getState(), EntitlementState.CANCELLED);
        assertEquals(entitlement2.getEffectiveEndDate(), clock.getUTCToday());
        assertEquals(entitlement2.getSourceType(), EntitlementSourceType.NATIVE);
    }

    @Test(groups = "slow")
    public void testCancelWithEntitlementDateInFuture() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

        clock.addDays(5);

        final LocalDate cancelDate = new LocalDate(clock.getUTCToday().plusDays(1));
        entitlement.cancelEntitlementWithDate(cancelDate, true, ImmutableList.<PluginProperty>of(), callContext);

        final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        assertEquals(entitlement2.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement2.getEffectiveEndDate(), cancelDate);

        clock.addDays(1);
        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK);
        assertListenerStatus();

        final Entitlement entitlement3 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        assertEquals(entitlement3.getState(), EntitlementState.CANCELLED);
        assertEquals(entitlement3.getEffectiveEndDate(), cancelDate);
    }

    @Test(groups = "slow")
    public void testUncancel() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

        clock.addDays(5);

        final LocalDate cancelDate = new LocalDate(clock.getUTCToday().plusDays(1));
        entitlement.cancelEntitlementWithDate(cancelDate, true, ImmutableList.<PluginProperty>of(), callContext);

        final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        assertEquals(entitlement2.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement2.getEffectiveEndDate(), cancelDate);

        testListener.pushExpectedEvents(NextEvent.UNCANCEL);
        entitlement2.uncancelEntitlement(ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        clock.addDays(1);
        final Entitlement entitlement3 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        assertEquals(entitlement3.getState(), EntitlementState.ACTIVE);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/840")
    public void testUncancelEntitlementFor_STANDALONE_Product() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Knife", BillingPeriod.MONTHLY, "notrial", null);

        // Create entitlement and check each field
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

        clock.addDays(5);

        final LocalDate cancelDate = new LocalDate(clock.getUTCToday().plusDays(1));
        entitlement.cancelEntitlementWithDate(cancelDate, true, ImmutableList.<PluginProperty>of(), callContext);

        final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        assertEquals(entitlement2.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement2.getEffectiveEndDate(), cancelDate);

        testListener.pushExpectedEvents(NextEvent.UNCANCEL);
        entitlement2.uncancelEntitlement(ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        clock.addDays(1);
        final Entitlement entitlement3 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        assertEquals(entitlement3.getState(), EntitlementState.ACTIVE);
    }

    @Test(groups = "slow")
    public void testCancelWithEntitlementPolicyEOTAndNOCTD() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK);
        final Entitlement cancelledEntitlement = entitlement.cancelEntitlementWithPolicy(EntitlementActionPolicy.END_OF_TERM, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        assertEquals(cancelledEntitlement.getState(), EntitlementState.CANCELLED);
        assertEquals(cancelledEntitlement.getEffectiveEndDate(), initialDate);

        // Entitlement started in trial on 2013-08-07, which is when we want the billing cancellation to occur
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlement.getBaseEntitlementId(), callContext);
        assertEquals(subscription.getBillingEndDate(), new LocalDate(2013, 8, 7));
    }

    @Test(groups = "slow")
    public void testCancelWithEntitlementPolicyIMMAndCTD() throws AccountApiException, EntitlementApiException, SubscriptionApiException, SubscriptionBaseApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        final DateTime ctd = clock.getUTCNow().plusDays(30).plusMonths(1);
        testListener.pushExpectedEvent(NextEvent.PHASE);
        // Go to 2013-09-08
        clock.addDays(32);
        // Set manually since no invoice
        subscriptionInternalApi.setChargedThroughDate(entitlement.getId(), ctd, internalCallContext);
        assertListenerStatus();

        final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final Entitlement entitlement3 = entitlement2.cancelEntitlementWithPolicy(EntitlementActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        assertEquals(entitlement3.getState(), EntitlementState.CANCELLED);
        assertEquals(entitlement3.getEffectiveEndDate(), new LocalDate(2013, 9, 8));

        // Entitlement started in trial on 2013-08-07. The phase occurs at 2013-09-06. The CTD is 2013-10-06 which is when we want the billing cancellation to occur
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlement.getBaseEntitlementId(), callContext);
        assertEquals(subscription.getBillingEndDate(), new LocalDate(2013, 10, 6));

        testListener.pushExpectedEvent(NextEvent.CANCEL);
        clock.addMonths(1);
        assertListenerStatus();

        final Entitlement entitlement4 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        assertEquals(entitlement4.getState(), EntitlementState.CANCELLED);
        assertEquals(entitlement4.getEffectiveEndDate(), new LocalDate(2013, 9, 8));
    }

    @Test(groups = "slow")
    public void testCancelWithEntitlementPolicyEOTAndCTD() throws AccountApiException, EntitlementApiException, SubscriptionApiException, SubscriptionBaseApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        final DateTime ctd = clock.getUTCNow().plusDays(30).plusMonths(1);
        testListener.pushExpectedEvent(NextEvent.PHASE);
        clock.addDays(32);
        // Set manually since no invoice
        subscriptionInternalApi.setChargedThroughDate(entitlement.getId(), ctd, internalCallContext);
        assertListenerStatus();

        final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);

        final Entitlement entitlement3 = entitlement2.cancelEntitlementWithPolicy(EntitlementActionPolicy.END_OF_TERM, ImmutableList.<PluginProperty>of(), callContext);
        assertEquals(entitlement3.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement3.getEffectiveEndDate(), new LocalDate(ctd));

        // Entitlement started in trial on 2013-08-07. The phase occurs at 2013-09-06. The CTD is 2013-10-06 which is when we want the billing cancellation to occur
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlement.getBaseEntitlementId(), callContext);
        assertEquals(subscription.getBillingEndDate(), new LocalDate(2013, 10, 6));

        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK);
        clock.addMonths(1);
        assertListenerStatus();

        final Entitlement entitlement4 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        assertEquals(entitlement4.getState(), EntitlementState.CANCELLED);
        assertEquals(entitlement4.getEffectiveEndDate(), new LocalDate(ctd));
    }

    @Test(groups = "slow")
    public void testCancelWithEntitlementPolicyEOTNoCTDAndImmediateChange() throws AccountApiException, EntitlementApiException, SubscriptionApiException, InterruptedException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        // Immediate change during trial
        testListener.pushExpectedEvent(NextEvent.CREATE);
        final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier("Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
        entitlement.changePlan(new DefaultEntitlementSpecifier(planPhaseSpecifier), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Verify the change is immediate
        final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        assertEquals(entitlement2.getLastActivePlan().getProduct().getName(), "Assault-Rifle");

        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK);
        final Entitlement cancelledEntitlement = entitlement.cancelEntitlementWithPolicy(EntitlementActionPolicy.END_OF_TERM, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        assertEquals(cancelledEntitlement.getState(), EntitlementState.CANCELLED);
        assertEquals(cancelledEntitlement.getEffectiveEndDate(), initialDate);

        // Entitlement started in trial on 2013-08-07, which is when we want the billing cancellation date to occur
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlement.getBaseEntitlementId(), callContext);
        assertEquals(subscription.getBillingEndDate(), new LocalDate(2013, 8, 7));
    }

    @Test(groups = "slow")
    public void testEntitlementChangePlanOnPendingEntitlement() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final LocalDate startDate = initialDate.plusDays(10);

        final Account account = accountApi.createAccount(getAccountData(7), callContext);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), startDate, startDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        assertEquals(entitlement.getState(), EntitlementState.PENDING);

        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier("pistol-monthly", null);
        try {
            entitlement.changePlan(new DefaultEntitlementSpecifier(spec2), ImmutableList.<PluginProperty>of(), callContext);
            fail("Changing plan immediately prior the subscription is active is not allowed");
        } catch (EntitlementApiException e) {
            assertEquals(e.getCode(), ErrorCode.SUB_CHANGE_NON_ACTIVE.getCode());
        }

        try {
            entitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec2), startDate.minusDays(1), ImmutableList.<PluginProperty>of(), callContext);
            fail("Changing plan immediately prior the subscription is active is not allowed");
        } catch (EntitlementApiException e) {
            assertEquals(e.getCode(), ErrorCode.SUB_CHANGE_NON_ACTIVE.getCode());
        }

        entitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec2), startDate, ImmutableList.<PluginProperty>of(), callContext);

        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        clock.addDays(10);
        assertListenerStatus();

        final Entitlement entitlement1 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        assertEquals(entitlement1.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement1.getLastActiveProduct().getName(), "Pistol");

    }
}
