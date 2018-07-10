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

package org.killbill.billing.entitlement.api;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementSourceType;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class TestDefaultEntitlementApi extends EntitlementTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testAddOnCreationTiming() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);
        final Account account = createAccount(getAccountData(7));
        // Reference time of 2013-08-07T00:00:00.000
        Assert.assertEquals(account.getReferenceTime().compareTo(new DateTime(2013, 8, 7, 0, 0, 0, DateTimeZone.UTC)), 0);

        // Add 5 seconds
        clock.addDeltaFromReality(5000);

        // Create base entitlement (null LocalDate will map to now(), i.e. 2013-08-07T00:00:05.000Z)
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier baseSpec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(baseSpec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final DefaultEntitlement baseEntitlement = (DefaultEntitlement) entitlementApi.getEntitlementForId(baseEntitlementId, callContext);
        Assert.assertEquals(baseEntitlement.getSubscriptionBase().getStartDate().compareTo(account.getReferenceTime().plusSeconds(5)), 0);

        // Add ADD_ON (verify date passed, i.e. initialDate, won't map to 2013-08-07T00:00:00.000Z)
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final UUID addOnEntitlementId = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec), initialDate, initialDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final DefaultEntitlement addOnEntitlement = (DefaultEntitlement) entitlementApi.getEntitlementForId(addOnEntitlementId, callContext);
        Assert.assertEquals(addOnEntitlement.getSubscriptionBase().getStartDate().compareTo(baseEntitlement.getSubscriptionBase().getStartDate()), 0);
    }

    @Test(groups = "slow")
    public void testCheckStaleStates() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Keep the same object for the whole test, to make sure we refresh its state before r/w calls
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        // Add ADD_ON
        // Keep the same object for the whole test, to make sure we refresh its state before r/w calls
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID addOnEntitlementId = entitlementApi.addEntitlement(entitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec), initialDate, initialDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        try {
            entitlement.uncancelEntitlement(ImmutableList.<PluginProperty>of(), callContext);
            Assert.fail("Entitlement hasn't been cancelled yet");
        } catch (final EntitlementApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.ENT_UNCANCEL_BAD_STATE.getCode());
        }

        clock.addDays(3);

        // Cancelling the base entitlement will cancel the add-on
        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.BLOCK);
        entitlement.cancelEntitlementWithDateOverrideBillingPolicy(clock.getUTCToday(), BillingActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        try {
            entitlement.cancelEntitlementWithDateOverrideBillingPolicy(clock.getUTCToday(), BillingActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
            Assert.fail("Entitlement is already cancelled");
        } catch (final EntitlementApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.SUB_CANCEL_BAD_STATE.getCode());
        }

        final Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(addOnEntitlementId, callContext);
        try {
            addOnEntitlement.cancelEntitlementWithDateOverrideBillingPolicy(clock.getUTCToday(), BillingActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
            Assert.fail("Add-On Entitlement is already cancelled");
        } catch (final EntitlementApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.SUB_CANCEL_BAD_STATE.getCode());
        }

        try {
            entitlement.uncancelEntitlement(ImmutableList.<PluginProperty>of(), callContext);
            Assert.fail("Entitlement is already cancelled");
        } catch (final EntitlementApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.SUB_UNCANCEL_BAD_STATE.getCode());
        }

        try {
            addOnEntitlement.uncancelEntitlement(ImmutableList.<PluginProperty>of(), callContext);
            Assert.fail("Add-On Entitlement is already cancelled");
        } catch (final EntitlementApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.SUB_UNCANCEL_BAD_STATE.getCode());
        }
    }

    @Test(groups = "slow")
    public void testUncancelEffectiveCancelledEntitlement() throws AccountApiException, EntitlementApiException, SubscriptionBaseApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Keep the same object for the whole test, to make sure we refresh its state before r/w calls
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        testListener.pushExpectedEvent(NextEvent.PHASE);
        clock.addDays(30);
        assertListenerStatus();
        subscriptionInternalApi.setChargedThroughDate(entitlement.getId(), clock.getUTCNow().plusMonths(1), internalCallContext);

        final LocalDate entitlementCancelledDate = clock.getToday(account.getTimeZone());
        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final Entitlement cancelledEntitlement = entitlement.cancelEntitlementWithDateOverrideBillingPolicy(clock.getToday(account.getTimeZone()), BillingActionPolicy.END_OF_TERM, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        Assert.assertEquals(cancelledEntitlement.getEffectiveEndDate(), entitlementCancelledDate);

        testListener.pushExpectedEvent(NextEvent.UNCANCEL);
        cancelledEntitlement.uncancelEntitlement(ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        final Entitlement reactivatedEntitlement = entitlementApi.getEntitlementForId(cancelledEntitlement.getId(), callContext);
        Assert.assertNull(reactivatedEntitlement.getEffectiveEndDate());
    }

    @Test(groups = "slow")
    public void testCreateEntitlementWithCheck() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        assertEquals(entitlement.getAccountId(), account.getId());
        assertEquals(entitlement.getExternalKey(), account.getExternalKey());

        assertEquals(entitlement.getEffectiveStartDate(), initialDate);
        assertNull(entitlement.getEffectiveEndDate());

        assertEquals(entitlement.getLastActivePriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
        assertEquals(entitlement.getLastActiveProduct().getName(), "Shotgun");
        assertEquals(entitlement.getLastActivePhase().getName(), "shotgun-monthly-trial");
        assertEquals(entitlement.getLastActivePlan().getName(), "shotgun-monthly");
        assertEquals(entitlement.getLastActiveProductCategory(), ProductCategory.BASE);

        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement.getSourceType(), EntitlementSourceType.NATIVE);

        assertEquals(entitlement.getLastActivePlan().getName(), "shotgun-monthly");
        assertEquals(entitlement.getLastActivePriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
        assertEquals(entitlement.getLastActiveProduct().getName(), "Shotgun");
        assertEquals(entitlement.getLastActiveProductCategory(), ProductCategory.BASE);

        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement.getSourceType(), EntitlementSourceType.NATIVE);

        // Now retrieve entitlement by id and recheck everything
        final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);

        assertEquals(entitlement2.getAccountId(), account.getId());
        assertEquals(entitlement2.getExternalKey(), account.getExternalKey());

        assertEquals(entitlement2.getEffectiveStartDate(), initialDate);
        assertNull(entitlement2.getEffectiveEndDate());

        assertEquals(entitlement2.getLastActivePriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
        assertEquals(entitlement2.getLastActiveProduct().getName(), "Shotgun");
        assertEquals(entitlement2.getLastActivePhase().getName(), "shotgun-monthly-trial");
        assertEquals(entitlement2.getLastActivePlan().getName(), "shotgun-monthly");
        assertEquals(entitlement2.getLastActiveProductCategory(), ProductCategory.BASE);

        assertEquals(entitlement2.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement2.getSourceType(), EntitlementSourceType.NATIVE);

        assertEquals(entitlement2.getLastActivePlan().getName(), "shotgun-monthly");
        assertEquals(entitlement2.getLastActivePriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
        assertEquals(entitlement2.getLastActiveProduct().getName(), "Shotgun");
        assertEquals(entitlement2.getLastActiveProductCategory(), ProductCategory.BASE);

        assertEquals(entitlement2.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement2.getSourceType(), EntitlementSourceType.NATIVE);

        // Finally
        final List<Entitlement> accountEntitlements = entitlementApi.getAllEntitlementsForAccountId(account.getId(), callContext);
        assertEquals(accountEntitlements.size(), 1);

        final Entitlement entitlement3 = accountEntitlements.get(0);

        assertEquals(entitlement3.getAccountId(), account.getId());
        assertEquals(entitlement3.getExternalKey(), account.getExternalKey());

        assertEquals(entitlement3.getEffectiveStartDate(), initialDate);
        assertNull(entitlement3.getEffectiveEndDate());

        assertEquals(entitlement3.getLastActivePriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
        assertEquals(entitlement3.getLastActiveProduct().getName(), "Shotgun");
        assertEquals(entitlement3.getLastActivePhase().getName(), "shotgun-monthly-trial");
        assertEquals(entitlement3.getLastActivePlan().getName(), "shotgun-monthly");
        assertEquals(entitlement3.getLastActiveProductCategory(), ProductCategory.BASE);

        assertEquals(entitlement3.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement3.getSourceType(), EntitlementSourceType.NATIVE);

        assertEquals(entitlement3.getLastActivePlan().getName(), "shotgun-monthly");
        assertEquals(entitlement3.getLastActivePriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
        assertEquals(entitlement3.getLastActiveProduct().getName(), "Shotgun");
        assertEquals(entitlement3.getLastActiveProductCategory(), ProductCategory.BASE);

        assertEquals(entitlement3.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement3.getSourceType(), EntitlementSourceType.NATIVE);
    }

    @Test(groups = "slow")
    public void testAddEntitlement() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, callContext);

        // Add ADD_ON
        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID telescopicEntitlementId = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(spec1), initialDate, initialDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement telescopicEntitlement = entitlementApi.getEntitlementForId(telescopicEntitlementId, callContext);

        assertEquals(telescopicEntitlement.getAccountId(), account.getId());
        assertEquals(telescopicEntitlement.getExternalKey(), account.getExternalKey());

        assertEquals(telescopicEntitlement.getEffectiveStartDate(), initialDate);
        assertNull(telescopicEntitlement.getEffectiveEndDate());

        assertEquals(telescopicEntitlement.getLastActivePriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
        assertEquals(telescopicEntitlement.getLastActiveProduct().getName(), "Telescopic-Scope");
        assertEquals(telescopicEntitlement.getLastActivePhase().getName(), "telescopic-scope-monthly-discount");
        assertEquals(telescopicEntitlement.getLastActivePlan().getName(), "telescopic-scope-monthly");
        assertEquals(telescopicEntitlement.getLastActiveProductCategory(), ProductCategory.ADD_ON);

        List<Entitlement> bundleEntitlements = entitlementApi.getAllEntitlementsForBundle(telescopicEntitlement.getBundleId(), callContext);
        assertEquals(bundleEntitlements.size(), 2);

        bundleEntitlements = entitlementApi.getAllEntitlementsForAccountIdAndExternalKey(account.getId(), account.getExternalKey(), callContext);
        assertEquals(bundleEntitlements.size(), 2);
    }

    @Test(groups = "slow")
    public void testAddEntitlementOnPendingBase() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        final LocalDate startDate = initialDate.plusDays(10);
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), startDate, startDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, callContext);

        // Add ADD_ON immediately. Because BASE is PENDING should fail
        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        try {
            entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(spec1), initialDate, initialDate, false, ImmutableList.<PluginProperty>of(), callContext);
            fail("Should not succeed to create ADD_On prior BASE is active");
        } catch (final EntitlementApiException e) {
            assertEquals(e.getCode(), ErrorCode.SUB_GET_NO_SUCH_BASE_SUBSCRIPTION.getCode());
        }


        // Add ADD_ON with a startDate similar to BASE
        final UUID telescopicEntitlementId = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(spec1), startDate, startDate, false, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement telescopicEntitlement = entitlementApi.getEntitlementForId(telescopicEntitlementId, callContext);

        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.BLOCK);
        clock.addDays(10);
        assertListenerStatus();


        assertEquals(telescopicEntitlement.getAccountId(), account.getId());
        assertEquals(telescopicEntitlement.getExternalKey(), account.getExternalKey());

        assertEquals(telescopicEntitlement.getEffectiveStartDate(), startDate);
        assertNull(telescopicEntitlement.getEffectiveEndDate());

        assertEquals(telescopicEntitlement.getLastActivePriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
        assertEquals(telescopicEntitlement.getLastActiveProduct().getName(), "Telescopic-Scope");
        assertEquals(telescopicEntitlement.getLastActivePhase().getName(), "telescopic-scope-monthly-discount");
        assertEquals(telescopicEntitlement.getLastActivePlan().getName(), "telescopic-scope-monthly");
        assertEquals(telescopicEntitlement.getLastActiveProductCategory(), ProductCategory.ADD_ON);

        List<Entitlement> bundleEntitlements = entitlementApi.getAllEntitlementsForBundle(telescopicEntitlement.getBundleId(), callContext);
        assertEquals(bundleEntitlements.size(), 2);

        bundleEntitlements = entitlementApi.getAllEntitlementsForAccountIdAndExternalKey(account.getId(), account.getExternalKey(), callContext);
        assertEquals(bundleEntitlements.size(), 2);
    }

    @Test(groups = "slow")
    public void testPauseUnpause() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, callContext);

        clock.addDays(1);
        final LocalDate effectiveDateSpec1 = new LocalDate(clock.getUTCNow(), account.getTimeZone());
        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID telescopicEntitlementId = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(spec1), effectiveDateSpec1, effectiveDateSpec1, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement telescopicEntitlement = entitlementApi.getEntitlementForId(telescopicEntitlementId, callContext);

        // Block all entitlement in the bundle
        clock.addDays(5);

        testListener.pushExpectedEvents(NextEvent.BLOCK);
        final LocalDate blockingStateDate = new LocalDate(clock.getUTCNow());
        entitlementApi.pause(baseEntitlement.getBundleId(), blockingStateDate, ImmutableList
                .<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Verify blocking state
        final Entitlement baseEntitlement2 = entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext);
        assertEquals(baseEntitlement2.getState(), EntitlementState.BLOCKED);

        final Entitlement telescopicEntitlement2 = entitlementApi.getEntitlementForId(telescopicEntitlement.getId(), callContext);
        assertEquals(telescopicEntitlement2.getState(), EntitlementState.BLOCKED);

        final List<Entitlement> bundleEntitlements2 = entitlementApi.getAllEntitlementsForBundle(telescopicEntitlement2.getBundleId(), callContext);
        assertEquals(bundleEntitlements2.size(), 2);
        for (final Entitlement cur : bundleEntitlements2) {
            assertEquals(cur.getState(), EntitlementState.BLOCKED);
        }

        // Try to add an ADD_ON, it should fail because BASE is blocked
        try {
            final PlanPhaseSpecifier spec3 = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
            entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(spec3), blockingStateDate, effectiveDateSpec1, false, ImmutableList.<PluginProperty>of(), callContext);
            fail("Should not be able to create ADD-ON because BP is paused");
        } catch (EntitlementApiException e) {
            assertEquals(e.getCode(), ErrorCode.BLOCK_BLOCKED_ACTION.getCode());
        }

        clock.addDays(3);

        testListener.pushExpectedEvents(NextEvent.BLOCK);
        entitlementApi.resume(baseEntitlement.getBundleId(), new LocalDate(clock.getUTCNow()), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Verify call is idempotent
        entitlementApi.resume(baseEntitlement.getBundleId(), new LocalDate(clock.getUTCNow()), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Verify blocking state
        final Entitlement baseEntitlement3 = entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext);
        assertEquals(baseEntitlement3.getState(), EntitlementState.ACTIVE);

        final Entitlement telescopicEntitlement3 = entitlementApi.getEntitlementForId(telescopicEntitlement.getId(), callContext);
        assertEquals(telescopicEntitlement3.getState(), EntitlementState.ACTIVE);

        final List<Entitlement> bundleEntitlements3 = entitlementApi.getAllEntitlementsForBundle(telescopicEntitlement2.getBundleId(), callContext);
        assertEquals(bundleEntitlements3.size(), 2);
        for (Entitlement cur : bundleEntitlements3) {
            assertEquals(cur.getState(), EntitlementState.ACTIVE);
        }
    }

    @Test(groups = "slow", description = "Test pause / unpause in the future")
    public void testPauseUnpauseInTheFuture() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        // Create entitlement
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, callContext);

        // Get the phase event out of the way
        testListener.pushExpectedEvents(NextEvent.PHASE);
        clock.setDay(new LocalDate(2013, 9, 7));
        assertListenerStatus();

        final LocalDate pauseDate = new LocalDate(2013, 9, 17);
        entitlementApi.pause(baseEntitlement.getBundleId(), pauseDate, ImmutableList.<PluginProperty>of(), callContext);
        // No event yet
        assertListenerStatus();

        final Entitlement refreshedAfterFuturePause = entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext);
        assertEquals(refreshedAfterFuturePause.getState(), EntitlementState.ACTIVE);


        final LocalDate resumeDate = new LocalDate(2013, 12, 24);
        entitlementApi.resume(baseEntitlement.getBundleId(), resumeDate, ImmutableList.<PluginProperty>of(), callContext);
        // No event yet
        assertListenerStatus();

        // Not worth writing another test in TestDefaultSubscriptionApi just for that subscription call. We want to check that future PAUSE/RESUME events are visible
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlement.getId(), callContext);
        Assert.assertEquals(subscription.getSubscriptionEvents().size(), 7);

        Assert.assertEquals(subscription.getSubscriptionEvents().get(0).getServiceName(), "entitlement-service");
        Assert.assertEquals(subscription.getSubscriptionEvents().get(0).getServiceStateName(), "ENT_STARTED");
        Assert.assertNull(subscription.getSubscriptionEvents().get(0).getPrevPhase());
        Assert.assertEquals(subscription.getSubscriptionEvents().get(0).getNextPhase().getName(), "shotgun-annual-trial");

        Assert.assertEquals(subscription.getSubscriptionEvents().get(1).getServiceName(), "billing-service");
        Assert.assertEquals(subscription.getSubscriptionEvents().get(1).getServiceStateName(), "START_BILLING");
        Assert.assertNull(subscription.getSubscriptionEvents().get(1).getPrevPhase());
        Assert.assertEquals(subscription.getSubscriptionEvents().get(1).getNextPhase().getName(), "shotgun-annual-trial");

        Assert.assertEquals(subscription.getSubscriptionEvents().get(2).getServiceName(), "entitlement+billing-service");
        Assert.assertEquals(subscription.getSubscriptionEvents().get(2).getServiceStateName(), "PHASE");
        Assert.assertEquals(subscription.getSubscriptionEvents().get(2).getPrevPhase().getName(), "shotgun-annual-trial");
        Assert.assertEquals(subscription.getSubscriptionEvents().get(2).getNextPhase().getName(), "shotgun-annual-evergreen");

        Assert.assertEquals(subscription.getSubscriptionEvents().get(3).getServiceName(), "entitlement-service");
        Assert.assertEquals(subscription.getSubscriptionEvents().get(3).getServiceStateName(), "ENT_BLOCKED");
        Assert.assertEquals(subscription.getSubscriptionEvents().get(3).getPrevPhase().getName(), "shotgun-annual-evergreen");
        Assert.assertEquals(subscription.getSubscriptionEvents().get(3).getNextPhase().getName(), "shotgun-annual-evergreen");

        Assert.assertEquals(subscription.getSubscriptionEvents().get(4).getServiceName(), "billing-service");
        Assert.assertEquals(subscription.getSubscriptionEvents().get(4).getServiceStateName(), "ENT_BLOCKED");
        Assert.assertEquals(subscription.getSubscriptionEvents().get(4).getPrevPhase().getName(), "shotgun-annual-evergreen");
        Assert.assertEquals(subscription.getSubscriptionEvents().get(4).getNextPhase().getName(), "shotgun-annual-evergreen");

        Assert.assertEquals(subscription.getSubscriptionEvents().get(5).getServiceName(), "entitlement-service");
        Assert.assertEquals(subscription.getSubscriptionEvents().get(5).getServiceStateName(), "ENT_CLEAR");
        Assert.assertEquals(subscription.getSubscriptionEvents().get(5).getPrevPhase().getName(), "shotgun-annual-evergreen");
        Assert.assertEquals(subscription.getSubscriptionEvents().get(5).getNextPhase().getName(), "shotgun-annual-evergreen");

        Assert.assertEquals(subscription.getSubscriptionEvents().get(6).getServiceName(), "billing-service");
        Assert.assertEquals(subscription.getSubscriptionEvents().get(6).getServiceStateName(), "ENT_CLEAR");
        Assert.assertEquals(subscription.getSubscriptionEvents().get(6).getPrevPhase().getName(), "shotgun-annual-evergreen");
        Assert.assertEquals(subscription.getSubscriptionEvents().get(6).getNextPhase().getName(), "shotgun-annual-evergreen");

        testListener.pushExpectedEvents(NextEvent.BLOCK);
        clock.setDay(pauseDate);
        assertListenerStatus();

        // Verify blocking state
        final Entitlement baseEntitlementPaused = entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext);
        assertEquals(baseEntitlementPaused.getState(), EntitlementState.BLOCKED);

        testListener.pushExpectedEvents(NextEvent.BLOCK);
        clock.setDay(resumeDate);
        assertListenerStatus();

        // Verify blocking state
        final Entitlement baseEntitlementUnpaused = entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext);
        assertEquals(baseEntitlementUnpaused.getState(), EntitlementState.ACTIVE);
    }

    @Test(groups = "slow")
    public void testTransferBundle() throws AccountApiException, EntitlementApiException, SubscriptionBaseApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account accountDesc = createAccount(getAccountData(15));
        // internal context will be configured for accountSrc
        final Account accountSrc = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement (with migrated flag so we can check later that transferred subscription is in right status)
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(accountSrc.getId(), new DefaultEntitlementSpecifier(spec), accountSrc.getExternalKey(), null, null, true, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, callContext);
        assertEquals(baseEntitlement.getSourceType(), EntitlementSourceType.MIGRATED);
        // Again to make sure this flag is correctly wrote/set
        assertEquals(entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext).getSourceType(), EntitlementSourceType.MIGRATED);


        final DateTime ctd = clock.getUTCNow().plusDays(30).plusMonths(1);
        testListener.pushExpectedEvent(NextEvent.PHASE);
        clock.addDays(32);
        // Set manually since no invoice
        subscriptionInternalApi.setChargedThroughDate(baseEntitlement.getId(), ctd, internalCallContext);
        assertListenerStatus();

        // Transfer bundle to dest account
        final LocalDate effectiveDate = new LocalDate(clock.getUTCNow(), accountSrc.getTimeZone());
        testListener.pushExpectedEvents(NextEvent.TRANSFER, NextEvent.BLOCK, NextEvent.BLOCK);
        final UUID newBundleId = entitlementApi.transferEntitlementsOverrideBillingPolicy(accountSrc.getId(), accountDesc.getId(), baseEntitlement.getExternalKey(), effectiveDate, BillingActionPolicy.END_OF_TERM, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        final Entitlement oldBaseEntitlement = entitlementApi.getAllEntitlementsForAccountIdAndExternalKey(accountSrc.getId(), accountSrc.getExternalKey(), callContext).get(0);
        assertEquals(oldBaseEntitlement.getEffectiveEndDate(), effectiveDate);
        assertEquals(oldBaseEntitlement.getState(), EntitlementState.CANCELLED);

        final List<Entitlement> entitlements = entitlementApi.getAllEntitlementsForBundle(newBundleId, callContext);
        assertEquals(entitlements.size(), 1);

        final Entitlement newBaseEntitlement = entitlements.get(0);
        assertEquals(newBaseEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(newBaseEntitlement.getEffectiveStartDate(), effectiveDate);
        assertEquals(newBaseEntitlement.getEffectiveEndDate(), null);
        assertEquals(newBaseEntitlement.getSourceType(), EntitlementSourceType.TRANSFERRED);

    }

    @Test(groups = "slow")
    public void testCreateEntitlementInThePast() throws AccountApiException, EntitlementApiException, SubscriptionBaseApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        final LocalDate clockDate = new LocalDate(2013, 10, 7);
        clock.setDay(clockDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Keep the same object for the whole test, to make sure we refresh its state before r/w calls
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.PHASE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), initialDate, initialDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        assertEquals(entitlement.getAccountId(), account.getId());
        assertEquals(entitlement.getExternalKey(), account.getExternalKey());

        assertEquals(entitlement.getEffectiveStartDate(), initialDate);
        assertNull(entitlement.getEffectiveEndDate());

        assertEquals(entitlement.getLastActivePriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
        assertEquals(entitlement.getLastActiveProduct().getName(), "Shotgun");
        assertEquals(entitlement.getLastActivePhase().getName(), "shotgun-monthly-evergreen");
        assertEquals(entitlement.getLastActivePlan().getName(), "shotgun-monthly");
        assertEquals(entitlement.getLastActiveProductCategory(), ProductCategory.BASE);

        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement.getSourceType(), EntitlementSourceType.NATIVE);

        assertEquals(entitlement.getLastActivePlan().getName(), "shotgun-monthly");
        assertEquals(entitlement.getLastActivePriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
        assertEquals(entitlement.getLastActiveProduct().getName(), "Shotgun");
        assertEquals(entitlement.getLastActiveProductCategory(), ProductCategory.BASE);

        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement.getSourceType(), EntitlementSourceType.NATIVE);

        List<Entitlement> bundleEntitlements = entitlementApi.getAllEntitlementsForBundle(entitlement.getBundleId(), callContext);
        assertEquals(bundleEntitlements.size(), 1);

        bundleEntitlements = entitlementApi.getAllEntitlementsForAccountIdAndExternalKey(account.getId(), account.getExternalKey(), callContext);
        assertEquals(bundleEntitlements.size(), 1);

    }

    @Test(groups = "slow")
    public void testCreateBaseWithDifferentStartDate() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final LocalDate entitlementDate = initialDate.plusDays(3);
        final LocalDate billingDate = initialDate.plusDays(5);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), entitlementDate, billingDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        assertEquals(entitlement.getState(), EntitlementState.PENDING);
        assertEquals(entitlement.getEffectiveStartDate(), entitlementDate);



        // 2013-08-10 : entitlementDate
        testListener.pushExpectedEvents(NextEvent.BLOCK);
        clock.addDays(3);
        assertListenerStatus();


        // Once we pass entitlement startDate, state should be ACTIVE (although we did not pass billing startDate)
        entitlement = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

        // 2013-08-12 : billingDate
        testListener.pushExpectedEvents(NextEvent.CREATE);
        clock.addDays(2);
        assertListenerStatus();


        // effectiveDate = entitlementDate prior billingDate
        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier("Pistol",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        try {
            entitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec2), entitlementDate, ImmutableList.<PluginProperty>of(), callContext);
            Assert.fail("Change plan prior billingStartDate should fail");
        } catch (EntitlementApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.SUB_INVALID_REQUESTED_DATE.getCode());
        }

        // effectiveDate is null (same as first case above), but **did**  reach the billing startDate (and entitlement startDate) so will succeed
        clock.addDeltaFromReality(1000); // Add one sec to make sure CHANGE event does not coincide with CREATE (realistic scenario), and therefore we do expect a CHANGE event
        testListener.pushExpectedEvents(NextEvent.CHANGE);
        final Entitlement result = entitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec2), null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        assertEquals(result.getState(), EntitlementState.ACTIVE);
        assertEquals(result.getLastActiveProduct().getName(), "Pistol");

    }

    @Test(groups = "slow")
    public void testCreateBaseWithEntitlementInTheFuture() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final LocalDate entitlementDate = initialDate.plusDays(3);
        final LocalDate billingDate = null;

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        testListener.pushExpectedEvents(NextEvent.CREATE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), entitlementDate, billingDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        assertEquals(entitlement.getState(), EntitlementState.PENDING);
        assertEquals(entitlement.getEffectiveStartDate(), entitlementDate);

        testListener.pushExpectedEvents(NextEvent.BLOCK);
        clock.addDays(3);
        assertListenerStatus();

        final Entitlement entitlementActive = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        assertEquals(entitlementActive.getState(), EntitlementState.ACTIVE);
    }

    @Test(groups = "slow")
    public void testCreateBaseWithBillingInTheFuture() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final LocalDate entitlementDate = null;
        final LocalDate billingDate = initialDate.plusDays(5);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        testListener.pushExpectedEvents(NextEvent.BLOCK);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), entitlementDate, billingDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement.getEffectiveStartDate(), initialDate);

        testListener.pushExpectedEvents(NextEvent.CREATE);
        clock.addDays(5);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testCreateBaseWithDifferentInThePast() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final LocalDate entitlementDate = initialDate.minusDays(3);
        final LocalDate billingDate = initialDate.minusDays(5);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        testListener.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CREATE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), entitlementDate, billingDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement.getEffectiveStartDate(), entitlementDate);
    }

    @Test(groups = "slow")
    public void testCreateBaseWithEntitlementInThePast() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final LocalDate entitlementDate = initialDate.minusDays(3);
        final LocalDate billingDate = null;

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        testListener.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CREATE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), entitlementDate, billingDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement.getEffectiveStartDate(), entitlementDate);
    }

    @Test(groups = "slow")
    public void testCreateBaseWithBillingInThePast() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final LocalDate entitlementDate = null;
        final LocalDate billingDate = initialDate.minusDays(5);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        testListener.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CREATE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), entitlementDate, billingDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement.getEffectiveStartDate(), initialDate);
    }

    @Test(groups = "slow")
    public void testCreateBaseSubscriptionsWithAddOns() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final String bundleKey2 = "bundleKey2";
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        testListener.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CREATE);
        entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), bundleKey2, null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();


        // First bundle of EntitlementSpecifier will specify all new subscription
        final String bundleKey1 = "bundleKey1";
        final EntitlementSpecifier spec11 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("Shotgun",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null));
        final EntitlementSpecifier spec12 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("Telescopic-Scope",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null));
        final List<EntitlementSpecifier> specs1 = ImmutableList.of(spec11, spec12);
        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier1 = new DefaultBaseEntitlementWithAddOnsSpecifier(null, bundleKey1, specs1, null, null, false);


        // Second bundle of EntitlementSpecifier will specify the previously created 'existingEntitlement'
        final EntitlementSpecifier spec22 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("Telescopic-Scope",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null));
        final List<EntitlementSpecifier> specs2 = ImmutableList.of(spec22);
        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier2 = new DefaultBaseEntitlementWithAddOnsSpecifier(null, bundleKey2, specs2, null, null, false);

        final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers = ImmutableList.of(baseEntitlementWithAddOnsSpecifier1, baseEntitlementWithAddOnsSpecifier2);


        // We expect 3 {BLOCK, CREATE} events for the 3 subscriptions created,.
        testListener.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.CREATE);
        final List<UUID> entitlementIds = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), baseEntitlementWithAddOnsSpecifiers, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Retun only the created subscriptions
        Assert.assertEquals(entitlementIds.size(), 3);

        final List<Entitlement> entitlementsForBundle1 = entitlementApi.getAllEntitlementsForAccountIdAndExternalKey(account.getId(), bundleKey1, callContext);
        Assert.assertEquals(entitlementsForBundle1.size(), 2);

        // And yet we do have both the BASE and ADD_ON for bundleKey2
        final List<Entitlement> entitlementsForBundle2 = entitlementApi.getAllEntitlementsForAccountIdAndExternalKey(account.getId(), bundleKey2, callContext);
        Assert.assertEquals(entitlementsForBundle2.size(), 2);

    }

    @Test(groups = "slow", expectedExceptions = EntitlementApiException.class)
    public void testCreateBaseSubscriptionsWithAddOnsMissingBase() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final String bundleKey2 = "bundleKey2";

        final EntitlementSpecifier spec22 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("Telescopic-Scope",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null));
        final List<EntitlementSpecifier> specs2 = ImmutableList.of(spec22);
        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier2 = new DefaultBaseEntitlementWithAddOnsSpecifier(null, bundleKey2, specs2, null, null, false);

        final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers = ImmutableList.of(baseEntitlementWithAddOnsSpecifier2);

        entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), baseEntitlementWithAddOnsSpecifiers, true, ImmutableList.<PluginProperty>of(), callContext);
    }

    @Test(groups = "slow")
    public void testCreateBaseSubscriptionsWithAddOnsBadOrdering() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final String bundleKey1 = "bundleKey1";
        final EntitlementSpecifier spec11 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("Telescopic-Scope",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null));
        final EntitlementSpecifier spec12 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("Shotgun",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null));
        final List<EntitlementSpecifier> specs1 = ImmutableList.of(spec11, spec12);
        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier1 = new DefaultBaseEntitlementWithAddOnsSpecifier(null, bundleKey1, specs1, null, null, false);

        final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers = ImmutableList.of(baseEntitlementWithAddOnsSpecifier1);


        testListener.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.CREATE);
        entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), baseEntitlementWithAddOnsSpecifiers, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

    }

    @Test(groups = "slow")
    public void testCreatedBundledStandaloneEntitlements() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Knife", BillingPeriod.MONTHLY, "notrial", null);

        // Create STANDALONE entitlement
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, callContext);

        assertEquals(baseEntitlement.getAccountId(), account.getId());
        assertEquals(baseEntitlement.getExternalKey(), account.getExternalKey());
        assertEquals(baseEntitlement.getLastActiveProduct().getName(), "Knife");
        assertEquals(baseEntitlement.getLastActivePlan().getName(), "knife-monthly-notrial");
        assertEquals(baseEntitlement.getLastActiveProductCategory(), ProductCategory.STANDALONE);

        // Add another STANDALONE entitlement
        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier("Knife", BillingPeriod.MONTHLY, "notrial", null);
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID anotherStandaloneEntitlementId = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(spec1), initialDate, initialDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement anotherStandaloneEntitlement = entitlementApi.getEntitlementForId(anotherStandaloneEntitlementId, callContext);

        assertEquals(anotherStandaloneEntitlement.getAccountId(), account.getId());
        assertEquals(anotherStandaloneEntitlement.getExternalKey(), account.getExternalKey());
        assertEquals(anotherStandaloneEntitlement.getBundleId(), baseEntitlement.getBundleId());

        assertEquals(anotherStandaloneEntitlement.getLastActivePriceList().getName(), "notrial");
        assertEquals(anotherStandaloneEntitlement.getLastActiveProduct().getName(), "Knife");
        assertEquals(anotherStandaloneEntitlement.getLastActivePlan().getName(), "knife-monthly-notrial");
        assertEquals(anotherStandaloneEntitlement.getLastActiveProductCategory(), ProductCategory.STANDALONE);

        List<Entitlement> bundleEntitlements = entitlementApi.getAllEntitlementsForBundle(anotherStandaloneEntitlement.getBundleId(), callContext);
        assertEquals(bundleEntitlements.size(), 2);

        bundleEntitlements = entitlementApi.getAllEntitlementsForAccountIdAndExternalKey(account.getId(), account.getExternalKey(), callContext);
        assertEquals(bundleEntitlements.size(), 2);
    }
}
