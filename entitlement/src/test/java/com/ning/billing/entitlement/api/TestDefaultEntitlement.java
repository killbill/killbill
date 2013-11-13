/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.entitlement.api;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import com.ning.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import com.ning.billing.entitlement.api.Entitlement.EntitlementState;

import static org.testng.Assert.assertEquals;

public class TestDefaultEntitlement extends EntitlementTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testCancelWithEntitlementDate() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = accountApi.createAccount(getAccountData(7), callContext);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvent(NextEvent.CREATE);
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), initialDate, callContext);
        assertListenerStatus();
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

        clock.addDays(5);

        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK);
        final LocalDate cancelDate = new LocalDate(clock.getUTCNow());
        entitlement.cancelEntitlementWithDate(cancelDate, true, callContext);
        assertListenerStatus();

        final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        assertEquals(entitlement2.getState(), EntitlementState.CANCELLED);
        assertEquals(entitlement2.getEffectiveEndDate(), cancelDate);
    }

    @Test(groups = "slow")
    public void testCancelWithEntitlementDateInFuture() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = accountApi.createAccount(getAccountData(7), callContext);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvent(NextEvent.CREATE);
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), initialDate, callContext);
        assertListenerStatus();
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

        clock.addDays(5);

        final LocalDate cancelDate = new LocalDate(clock.getUTCToday().plusDays(1));
        entitlement.cancelEntitlementWithDate(cancelDate, true, callContext);

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

        final Account account = accountApi.createAccount(getAccountData(7), callContext);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvent(NextEvent.CREATE);
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), initialDate, callContext);
        assertListenerStatus();
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

        clock.addDays(5);

        final LocalDate cancelDate = new LocalDate(clock.getUTCToday().plusDays(1));
        entitlement.cancelEntitlementWithDate(cancelDate, true, callContext);

        final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        assertEquals(entitlement2.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement2.getEffectiveEndDate(), cancelDate);

        testListener.pushExpectedEvents(NextEvent.UNCANCEL);
        entitlement2.uncancelEntitlement(callContext);
        assertListenerStatus();

        clock.addDays(1);
        final Entitlement entitlement3 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        assertEquals(entitlement3.getState(), EntitlementState.ACTIVE);
    }

    @Test(groups = "slow")
    public void testCancelWithEntitlementPolicyEOTAndNOCTD() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = accountApi.createAccount(getAccountData(7), callContext);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvent(NextEvent.CREATE);
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), initialDate, callContext);
        assertListenerStatus();

        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK);
        final Entitlement cancelledEntitlement = entitlement.cancelEntitlementWithPolicy(EntitlementActionPolicy.END_OF_TERM, callContext);
        assertListenerStatus();
        assertEquals(cancelledEntitlement.getState(), EntitlementState.CANCELLED);
        assertEquals(cancelledEntitlement.getEffectiveEndDate(), initialDate);

        // Entitlement started in trial on 2013-08-07, which is when we want the billing cancellation to occur
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlement.getBaseEntitlementId(), callContext);
        assertEquals(subscription.getBillingEndDate(), new LocalDate(2013, 8, 7));
    }

    @Test(groups = "slow")
    public void testCancelWithEntitlementPolicyIMMAndCTD() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = accountApi.createAccount(getAccountData(7), callContext);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvent(NextEvent.CREATE);
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), initialDate, callContext);
        assertListenerStatus();

        final DateTime ctd = clock.getUTCNow().plusDays(30).plusMonths(1);
        testListener.pushExpectedEvent(NextEvent.PHASE);
        // Go to 2013-09-08
        clock.addDays(32);
        // Set manually since no invoice
        subscriptionInternalApi.setChargedThroughDate(entitlement.getId(), ctd, internalCallContext);
        assertListenerStatus();

        final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final Entitlement entitlement3 = entitlement2.cancelEntitlementWithPolicy(EntitlementActionPolicy.IMMEDIATE, callContext);
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
    public void testCancelWithEntitlementPolicyEOTAndCTD() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = accountApi.createAccount(getAccountData(7), callContext);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvent(NextEvent.CREATE);
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), initialDate, callContext);
        assertListenerStatus();

        final DateTime ctd = clock.getUTCNow().plusDays(30).plusMonths(1);
        testListener.pushExpectedEvent(NextEvent.PHASE);
        clock.addDays(32);
        // Set manually since no invoice
        subscriptionInternalApi.setChargedThroughDate(entitlement.getId(), ctd, internalCallContext);
        assertListenerStatus();

        final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);

        final Entitlement entitlement3 = entitlement2.cancelEntitlementWithPolicy(EntitlementActionPolicy.END_OF_TERM, callContext);
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

        final Account account = accountApi.createAccount(getAccountData(7), callContext);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvent(NextEvent.CREATE);
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), initialDate, callContext);
        assertListenerStatus();

        // Immediate change during trial
        testListener.pushExpectedEvent(NextEvent.CHANGE);
        entitlement.changePlan("Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, callContext);
        assertListenerStatus();

        // Verify the change is immediate
        final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        assertEquals(entitlement2.getLastActivePhase().getPlan().getProduct().getName(), "Assault-Rifle");

        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK);
        final Entitlement cancelledEntitlement = entitlement.cancelEntitlementWithPolicy(EntitlementActionPolicy.END_OF_TERM, callContext);
        assertListenerStatus();
        assertEquals(cancelledEntitlement.getState(), EntitlementState.CANCELLED);
        assertEquals(cancelledEntitlement.getEffectiveEndDate(), initialDate);

        // Entitlement started in trial on 2013-08-07, which is when we want the billing cancellation date to occur
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlement.getBaseEntitlementId(), callContext);
        assertEquals(subscription.getBillingEndDate(), new LocalDate(2013, 8, 7));
    }
}
