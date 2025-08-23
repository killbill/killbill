/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

import java.util.Collections;
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
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementSourceType;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.testng.Assert;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class TestDefaultEntitlementApi extends EntitlementTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testAddOnCreationTiming() throws AccountApiException, EntitlementApiException {

        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final String bundleExternalKey = UUID.randomUUID().toString();
        final String bpExternalKey = UUID.randomUUID().toString();
        final String aoExternalKey = UUID.randomUUID().toString();


        final Account account = createAccount(getAccountData(7));
        // Reference time of 2013-08-07T00:00:00.000
        Assert.assertEquals(account.getReferenceTime().compareTo(new DateTime(2013, 8, 7, 0, 0, 0, DateTimeZone.UTC)), 0);

        // Add 5 seconds
        clock.addDeltaFromReality(5000);

        // Create base entitlement (null LocalDate will map to now(), i.e. 2013-08-07T00:00:05.000Z)
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier baseSpec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(baseSpec, null, null, bpExternalKey, null), bundleExternalKey, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        final DefaultEntitlement baseEntitlement = (DefaultEntitlement) entitlementApi.getEntitlementForId(baseEntitlementId, false, callContext);
        Assert.assertEquals(baseEntitlement.getSubscriptionBase().getStartDate().compareTo(account.getReferenceTime().plusSeconds(5)), 0);
        Assert.assertEquals(baseEntitlement.getBundleExternalKey(), bundleExternalKey);
        Assert.assertEquals(baseEntitlement.getExternalKey(), bpExternalKey);

        // Add ADD_ON (verify date passed, i.e. initialDate, won't map to 2013-08-07T00:00:00.000Z)
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final UUID addOnEntitlementId = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec, null, null, aoExternalKey, null), initialDate, initialDate, false, Collections.emptyList(), callContext);
        assertListenerStatus();
        final DefaultEntitlement addOnEntitlement = (DefaultEntitlement) entitlementApi.getEntitlementForId(addOnEntitlementId, false, callContext);
        Assert.assertEquals(addOnEntitlement.getSubscriptionBase().getStartDate().compareTo(baseEntitlement.getSubscriptionBase().getStartDate()), 0);
        Assert.assertEquals(addOnEntitlement.getBundleExternalKey(), bundleExternalKey);
        Assert.assertEquals(addOnEntitlement.getExternalKey(), aoExternalKey);

    }

    @Test(groups = "slow")
    public void testCheckStaleStates() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Keep the same object for the whole test, to make sure we refresh its state before r/w calls
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);

        // Add ADD_ON
        // Keep the same object for the whole test, to make sure we refresh its state before r/w calls
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID addOnEntitlementId = entitlementApi.addEntitlement(entitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec), initialDate, initialDate, false, Collections.emptyList(), callContext);
        assertListenerStatus();
        try {
            entitlement.uncancelEntitlement(Collections.emptyList(), callContext);
            Assert.fail("Entitlement hasn't been cancelled yet");
        } catch (final EntitlementApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.ENT_UNCANCEL_BAD_STATE.getCode());
        }

        clock.addDays(3);

        // Cancelling the base entitlement will cancel the add-on
        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.BLOCK);
        entitlement.cancelEntitlementWithDateOverrideBillingPolicy(clock.getUTCToday(), BillingActionPolicy.IMMEDIATE, Collections.emptyList(), callContext);
        assertListenerStatus();

        try {
            entitlement.cancelEntitlementWithDateOverrideBillingPolicy(clock.getUTCToday(), BillingActionPolicy.IMMEDIATE, Collections.emptyList(), callContext);
            Assert.fail("Entitlement is already cancelled");
        } catch (final EntitlementApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.SUB_CANCEL_BAD_STATE.getCode());
        }

        final Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(addOnEntitlementId, false, callContext);
        try {
            addOnEntitlement.cancelEntitlementWithDateOverrideBillingPolicy(clock.getUTCToday(), BillingActionPolicy.IMMEDIATE, Collections.emptyList(), callContext);
            Assert.fail("Add-On Entitlement is already cancelled");
        } catch (final EntitlementApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.SUB_CANCEL_BAD_STATE.getCode());
        }

        try {
            entitlement.uncancelEntitlement(Collections.emptyList(), callContext);
            Assert.fail("Entitlement is already cancelled");
        } catch (final EntitlementApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.SUB_UNCANCEL_BAD_STATE.getCode());
        }

        try {
            addOnEntitlement.uncancelEntitlement(Collections.emptyList(), callContext);
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
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);

        testListener.pushExpectedEvent(NextEvent.PHASE);
        clock.addDays(30);
        assertListenerStatus();
        setChargedThroughDate(entitlement.getId(), clock.getUTCNow().plusMonths(1), internalCallContext);

        final LocalDate entitlementCancelledDate = clock.getToday(account.getTimeZone());
        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final Entitlement cancelledEntitlement = entitlement.cancelEntitlementWithDateOverrideBillingPolicy(clock.getToday(account.getTimeZone()), BillingActionPolicy.END_OF_TERM, Collections.emptyList(), callContext);
        assertListenerStatus();
        Assert.assertEquals(internalCallContext.toLocalDate(cancelledEntitlement.getEffectiveEndDate()), entitlementCancelledDate);

        testListener.pushExpectedEvent(NextEvent.UNCANCEL);
        cancelledEntitlement.uncancelEntitlement(Collections.emptyList(), callContext);
        assertListenerStatus();

        final Entitlement reactivatedEntitlement = entitlementApi.getEntitlementForId(cancelledEntitlement.getId(), false, callContext);
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
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertEquals(entitlement.getAccountId(), account.getId());
        assertEquals(entitlement.getBundleExternalKey(), account.getExternalKey());

        assertEquals(internalCallContext.toLocalDate(entitlement.getEffectiveStartDate()), initialDate);
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
        final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement.getId(), false, callContext);

        assertEquals(entitlement2.getAccountId(), account.getId());
        assertEquals(entitlement2.getBundleExternalKey(), account.getExternalKey());

        assertEquals(internalCallContext.toLocalDate(entitlement2.getEffectiveStartDate()), initialDate);
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
        assertEquals(entitlement3.getBundleExternalKey(), account.getExternalKey());

        assertEquals(internalCallContext.toLocalDate(entitlement3.getEffectiveStartDate()), initialDate);
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
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, false, callContext);

        // Add ADD_ON
        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID telescopicEntitlementId = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(spec1), initialDate, initialDate, false, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement telescopicEntitlement = entitlementApi.getEntitlementForId(telescopicEntitlementId, false, callContext);

        assertEquals(telescopicEntitlement.getAccountId(), account.getId());
        assertEquals(telescopicEntitlement.getBundleExternalKey(), account.getExternalKey());

        assertEquals(internalCallContext.toLocalDate(telescopicEntitlement.getEffectiveStartDate()), initialDate);
        assertNull(telescopicEntitlement.getEffectiveEndDate());

        assertEquals(telescopicEntitlement.getLastActivePriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
        assertEquals(telescopicEntitlement.getLastActiveProduct().getName(), "Telescopic-Scope");
        assertEquals(telescopicEntitlement.getLastActivePhase().getName(), "telescopic-scope-monthly-discount");
        assertEquals(telescopicEntitlement.getLastActivePlan().getName(), "telescopic-scope-monthly");
        assertEquals(telescopicEntitlement.getLastActiveProductCategory(), ProductCategory.ADD_ON);

        List<Entitlement> bundleEntitlements = entitlementApi.getAllEntitlementsForBundle(telescopicEntitlement.getBundleId(), callContext);
        assertEquals(bundleEntitlements.size(), 2);

        bundleEntitlements = entitlementApi.getAllEntitlementsForAccountIdAndBundleExternalKey(account.getId(), account.getExternalKey(), callContext);
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
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), startDate, startDate, false, true, Collections.emptyList(), callContext);
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, false, callContext);

        // Add ADD_ON immediately. Because BASE is PENDING should fail
        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        try {
            entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(spec1), initialDate, initialDate, false, Collections.emptyList(), callContext);
            fail("Should not succeed to create ADD_On prior BASE is active");
        } catch (final EntitlementApiException e) {
            assertEquals(e.getCode(), ErrorCode.SUB_GET_NO_SUCH_BASE_SUBSCRIPTION.getCode());
        }


        // Add ADD_ON with a startDate similar to BASE
        final UUID telescopicEntitlementId = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(spec1), startDate, startDate, false, Collections.emptyList(), callContext);
        final Entitlement telescopicEntitlement = entitlementApi.getEntitlementForId(telescopicEntitlementId, false, callContext);

        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.BLOCK);
        clock.addDays(10);
        assertListenerStatus();


        assertEquals(telescopicEntitlement.getAccountId(), account.getId());
        assertEquals(telescopicEntitlement.getBundleExternalKey(), account.getExternalKey());

        assertEquals(internalCallContext.toLocalDate(telescopicEntitlement.getEffectiveStartDate()), startDate);
        assertNull(telescopicEntitlement.getEffectiveEndDate());

        assertEquals(telescopicEntitlement.getLastActivePriceList().getName(), PriceListSet.DEFAULT_PRICELIST_NAME);
        assertEquals(telescopicEntitlement.getLastActiveProduct().getName(), "Telescopic-Scope");
        assertEquals(telescopicEntitlement.getLastActivePhase().getName(), "telescopic-scope-monthly-discount");
        assertEquals(telescopicEntitlement.getLastActivePlan().getName(), "telescopic-scope-monthly");
        assertEquals(telescopicEntitlement.getLastActiveProductCategory(), ProductCategory.ADD_ON);

        List<Entitlement> bundleEntitlements = entitlementApi.getAllEntitlementsForBundle(telescopicEntitlement.getBundleId(), callContext);
        assertEquals(bundleEntitlements.size(), 2);

        bundleEntitlements = entitlementApi.getAllEntitlementsForAccountIdAndBundleExternalKey(account.getId(), account.getExternalKey(), callContext);
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
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, false, callContext);

        clock.addDays(1);
        final LocalDate effectiveDateSpec1 = new LocalDate(clock.getUTCNow(), account.getTimeZone());
        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID telescopicEntitlementId = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(spec1), effectiveDateSpec1, effectiveDateSpec1, false, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement telescopicEntitlement = entitlementApi.getEntitlementForId(telescopicEntitlementId, false, callContext);

        // Block all entitlement in the bundle
        clock.addDays(5);

        testListener.pushExpectedEvents(NextEvent.BLOCK);
        final LocalDate blockingStateDate = new LocalDate(clock.getUTCNow());
        entitlementApi.pause(baseEntitlement.getBundleId(), blockingStateDate, Collections.emptyList(), callContext);
        assertListenerStatus();

        // Verify blocking state
        final Entitlement baseEntitlement2 = entitlementApi.getEntitlementForId(baseEntitlement.getId(), false, callContext);
        assertEquals(baseEntitlement2.getState(), EntitlementState.BLOCKED);

        final Entitlement telescopicEntitlement2 = entitlementApi.getEntitlementForId(telescopicEntitlement.getId(), false, callContext);
        assertEquals(telescopicEntitlement2.getState(), EntitlementState.BLOCKED);

        final List<Entitlement> bundleEntitlements2 = entitlementApi.getAllEntitlementsForBundle(telescopicEntitlement2.getBundleId(), callContext);
        assertEquals(bundleEntitlements2.size(), 2);
        for (final Entitlement cur : bundleEntitlements2) {
            assertEquals(cur.getState(), EntitlementState.BLOCKED);
        }

        // Try to add an ADD_ON, it should fail because BASE is blocked
        try {
            final PlanPhaseSpecifier spec3 = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
            entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(spec3), blockingStateDate, effectiveDateSpec1, false, Collections.emptyList(), callContext);
            fail("Should not be able to create ADD-ON because BP is paused");
        } catch (EntitlementApiException e) {
            assertEquals(e.getCode(), ErrorCode.BLOCK_BLOCKED_ACTION.getCode());
        }

        clock.addDays(3);

        testListener.pushExpectedEvents(NextEvent.BLOCK);
        entitlementApi.resume(baseEntitlement.getBundleId(), new LocalDate(clock.getUTCNow()), Collections.emptyList(), callContext);
        assertListenerStatus();

        // Verify call is idempotent
        entitlementApi.resume(baseEntitlement.getBundleId(), new LocalDate(clock.getUTCNow()), Collections.emptyList(), callContext);
        assertListenerStatus();

        // Verify blocking state
        final Entitlement baseEntitlement3 = entitlementApi.getEntitlementForId(baseEntitlement.getId(), false, callContext);
        assertEquals(baseEntitlement3.getState(), EntitlementState.ACTIVE);

        final Entitlement telescopicEntitlement3 = entitlementApi.getEntitlementForId(telescopicEntitlement.getId(), false, callContext);
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
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, false, callContext);

        // Get the phase event out of the way
        testListener.pushExpectedEvents(NextEvent.PHASE);
        clock.setDay(new LocalDate(2013, 9, 7));
        assertListenerStatus();

        final LocalDate pauseDate = new LocalDate(2013, 9, 17);
        entitlementApi.pause(baseEntitlement.getBundleId(), pauseDate, Collections.emptyList(), callContext);
        // No event yet
        assertListenerStatus();

        final Entitlement refreshedAfterFuturePause = entitlementApi.getEntitlementForId(baseEntitlement.getId(), false, callContext);
        assertEquals(refreshedAfterFuturePause.getState(), EntitlementState.ACTIVE);


        final LocalDate resumeDate = new LocalDate(2013, 12, 24);
        entitlementApi.resume(baseEntitlement.getBundleId(), resumeDate, Collections.emptyList(), callContext);
        // No event yet
        assertListenerStatus();

        // Not worth writing another test in TestDefaultSubscriptionApi just for that subscription call. We want to check that future PAUSE/RESUME events are visible
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlement.getId(), false, callContext);
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
        final Entitlement baseEntitlementPaused = entitlementApi.getEntitlementForId(baseEntitlement.getId(), false, callContext);
        assertEquals(baseEntitlementPaused.getState(), EntitlementState.BLOCKED);

        testListener.pushExpectedEvents(NextEvent.BLOCK);
        clock.setDay(resumeDate);
        assertListenerStatus();

        // Verify blocking state
        final Entitlement baseEntitlementUnpaused = entitlementApi.getEntitlementForId(baseEntitlement.getId(), false, callContext);
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
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(accountSrc.getId(), new DefaultEntitlementSpecifier(spec), accountSrc.getExternalKey(), null, null, true, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, false, callContext);
        assertEquals(baseEntitlement.getSourceType(), EntitlementSourceType.MIGRATED);
        // Again to make sure this flag is correctly wrote/set
        assertEquals(entitlementApi.getEntitlementForId(baseEntitlement.getId(), false, callContext).getSourceType(), EntitlementSourceType.MIGRATED);


        final DateTime ctd = clock.getUTCNow().plusDays(30).plusMonths(1);
        testListener.pushExpectedEvent(NextEvent.PHASE);
        clock.addDays(32);
        // Set manually since no invoice
        setChargedThroughDate(baseEntitlement.getId(), ctd, internalCallContext);
        assertListenerStatus();

        // Transfer bundle to dest account
        final LocalDate effectiveDate = new LocalDate(clock.getUTCNow(), accountSrc.getTimeZone());
        testListener.pushExpectedEvents(NextEvent.TRANSFER, NextEvent.BLOCK, NextEvent.BLOCK);
        final UUID newBundleId = entitlementApi.transferEntitlementsOverrideBillingPolicy(accountSrc.getId(), accountDesc.getId(), baseEntitlement.getBundleExternalKey(), effectiveDate, Collections.emptyMap(), BillingActionPolicy.END_OF_TERM, BcdTransfer.USE_EXISTING,  Collections.emptyList(), callContext);
        assertListenerStatus();

        final Entitlement oldBaseEntitlement = entitlementApi.getAllEntitlementsForAccountIdAndBundleExternalKey(accountSrc.getId(), accountSrc.getExternalKey(), callContext).get(0);
        assertEquals(internalCallContext.toLocalDate(oldBaseEntitlement.getEffectiveEndDate()), effectiveDate);
        assertEquals(oldBaseEntitlement.getState(), EntitlementState.CANCELLED);

        final List<Entitlement> entitlements = entitlementApi.getAllEntitlementsForBundle(newBundleId, callContext);
        assertEquals(entitlements.size(), 1);

        final Entitlement newBaseEntitlement = entitlements.get(0);
        assertEquals(newBaseEntitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(internalCallContext.toLocalDate(newBaseEntitlement.getEffectiveStartDate()), effectiveDate);
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
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), initialDate, initialDate, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);

        assertEquals(entitlement.getAccountId(), account.getId());
        assertEquals(entitlement.getBundleExternalKey(), account.getExternalKey());

        assertEquals(internalCallContext.toLocalDate(entitlement.getEffectiveStartDate()), initialDate);
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

        bundleEntitlements = entitlementApi.getAllEntitlementsForAccountIdAndBundleExternalKey(account.getId(), account.getExternalKey(), callContext);
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

        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), entitlementDate, billingDate, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);

        assertEquals(entitlement.getState(), EntitlementState.PENDING);
        assertEquals(internalCallContext.toLocalDate(entitlement.getEffectiveStartDate()), entitlementDate);



        // 2013-08-10 : entitlementDate
        testListener.pushExpectedEvents(NextEvent.BLOCK);
        clock.addDays(3);
        assertListenerStatus();


        // Once we pass entitlement startDate, state should be ACTIVE (although we did not pass billing startDate)
        entitlement = entitlementApi.getEntitlementForId(entitlement.getId(), false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

        // 2013-08-12 : billingDate
        testListener.pushExpectedEvents(NextEvent.CREATE);
        clock.addDays(2);
        assertListenerStatus();

        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier("Pistol",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        // effectiveDate is null (same as first case above), but **did**  reach the billing startDate (and entitlement startDate) so will succeed
        clock.addDeltaFromReality(1000); // Add one sec to make sure CHANGE event does not coincide with CREATE (realistic scenario), and therefore we do expect a CHANGE event
        testListener.pushExpectedEvents(NextEvent.CHANGE);
        final Entitlement result = entitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec2), (LocalDate) null, Collections.emptyList(), callContext);
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
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), entitlementDate, billingDate, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);

        assertEquals(entitlement.getState(), EntitlementState.PENDING);
        assertEquals(internalCallContext.toLocalDate(entitlement.getEffectiveStartDate()), entitlementDate);

        testListener.pushExpectedEvents(NextEvent.BLOCK);
        clock.addDays(3);
        assertListenerStatus();

        final Entitlement entitlementActive = entitlementApi.getEntitlementForId(entitlement.getId(), false, callContext);
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
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), entitlementDate, billingDate, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);

        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(internalCallContext.toLocalDate(entitlement.getEffectiveStartDate()), initialDate);

        testListener.pushExpectedEvents(NextEvent.CREATE);
        clock.addDays(5);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testCreateBaseEntitlementWithCurrentDateTime() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final DateTime initialDateTime = new DateTime(2013, 8, 7, 10, 30);
        clock.setTime(initialDateTime);

        final Account account = createAccount(getAccountData(7));

        final EntitlementSpecifier spec = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null));
        final List<EntitlementSpecifier> specs = List.of(spec);
        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(null, null, specs, initialDateTime, initialDateTime, false);
        final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers = List.of(baseEntitlementWithAddOnsSpecifier);

        //create entitlement with current datetime
        testListener.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CREATE); //entitlement created immediately
        final List<UUID> entitlementIds = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), baseEntitlementWithAddOnsSpecifiers, true, Collections.emptyList(), callContext);
        assertEquals(entitlementIds.size(), 1);
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementIds.get(0), false, callContext);
        assertListenerStatus();

        //Verify that state is ACTIVE
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

        //Verify entitlementStartDate and billingStartDate
        assertEquals(entitlement.getEffectiveStartDate().compareTo(initialDateTime), 0);
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlement.getId(), false, callContext);
        assertEquals(subscription.getBillingStartDate().compareTo(initialDateTime), 0);

    }

    @Test(groups = "slow")
    public void testCreateBaseEntitlementWithBillingDateTimeInTheFuture() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final DateTime initialDateTime = new DateTime(2013, 8, 7, 10, 30);
        clock.setTime(initialDateTime);

        final Account account = createAccount(getAccountData(7));

        final DateTime billingDatetime = initialDateTime.plusMinutes(30); //future datetime
        final DateTime entitlementDatetime = initialDateTime; //current datetime

        final EntitlementSpecifier spec = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null));
        final List<EntitlementSpecifier> specs = List.of(spec);
        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(null, null, specs, entitlementDatetime, billingDatetime, false);
        final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers = List.of(baseEntitlementWithAddOnsSpecifier);

        //create entitlement with future datetime for billing and current datetime for entitlement
        testListener.pushExpectedEvents(NextEvent.BLOCK);
        final List<UUID> entitlementIds = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), baseEntitlementWithAddOnsSpecifiers, true, Collections.emptyList(), callContext);
        assertEquals(entitlementIds.size(), 1);
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementIds.get(0), false, callContext);
        assertListenerStatus();

        //Verify that state is ACTIVE since entitlement is created with current datetime
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

        //Verify entitlementStartDate and billingStartDate
        assertEquals(entitlement.getEffectiveStartDate().compareTo(entitlementDatetime), 0);
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlement.getId(), false, callContext);
        assertEquals(subscription.getBillingStartDate().compareTo(billingDatetime), 0);

        //Move clock to billingStartDate and verify that billing starts
        testListener.pushExpectedEvents(NextEvent.CREATE);
        clock.setTime(billingDatetime);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testCreateBaseEntitlementWithEntitlementDateTimeInTheFuture() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final DateTime initialDateTime = new DateTime(2013, 8, 7, 10, 30);
        clock.setTime(initialDateTime);

        final Account account = createAccount(getAccountData(7));

        final DateTime entitlementDateTime = initialDateTime.plusMinutes(20); //future datetime
        final DateTime billingDatetime = null; //should default to now

        final EntitlementSpecifier spec = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null));
        final List<EntitlementSpecifier> specs = List.of(spec);
        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(null, null, specs, entitlementDateTime, billingDatetime, false);
        final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers = List.of(baseEntitlementWithAddOnsSpecifier);

        //create entitlement with future datetime for entitlement and current datetime for billing
        testListener.pushExpectedEvents(NextEvent.CREATE); ////Since entitlementDatetime is in the future, only CREATE event is specified here
        final List<UUID> entitlementIds = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), baseEntitlementWithAddOnsSpecifiers, true, Collections.emptyList(), callContext);
        assertEquals(entitlementIds.size(), 1);
        Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementIds.get(0), false, callContext);
        assertListenerStatus();

        //Verify that state is PENDING since entitlementDateTime is not reached
        assertEquals(entitlement.getState(), EntitlementState.PENDING);

        //Verify entitlementStartDate and billingStartDate
        assertEquals(entitlement.getEffectiveStartDate().compareTo(entitlementDateTime), 0);
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlement.getId(), false, callContext);
        assertEquals(subscription.getBillingStartDate().compareTo(initialDateTime), 0);

        //Move clock to entitlementStartDate and verify that entitlement starts
        testListener.pushExpectedEvents(NextEvent.BLOCK);
        clock.setTime(entitlementDateTime);
        assertListenerStatus();

        //Verify that state is now ACTIVE
        entitlement = entitlementApi.getEntitlementForId(entitlementIds.get(0), false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
    }

    @Test(groups = "slow")
    public void testCreateBaseEntitlementWithDateTimeInTheFuture() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final DateTime initialDateTime = new DateTime(2013, 8, 7, 10, 30);
        clock.setTime(initialDateTime);

        final Account account = createAccount(getAccountData(7));

        final DateTime entitlementDateTime = initialDateTime.plusMinutes(20); //future Datetime
        final DateTime billingDatetime = initialDateTime.plusMinutes(30); //future Datetime

        final EntitlementSpecifier spec = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null));
        final List<EntitlementSpecifier> specs = List.of(spec);
        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(null, null, specs, entitlementDateTime, billingDatetime, false);
        final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers = List.of(baseEntitlementWithAddOnsSpecifier);

        //create entitlement future datetime for entitlement and billing
        final List<UUID> entitlementIds = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), baseEntitlementWithAddOnsSpecifiers, true, Collections.emptyList(), callContext);
        assertEquals(entitlementIds.size(), 1);
        Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementIds.get(0), false, callContext);

        //Verify that state is PENDING since entitlementDateTime is not reached
        assertEquals(entitlement.getState(), EntitlementState.PENDING);

        //Verify entitlementStartDate and billingStartDate
        assertEquals(entitlement.getEffectiveStartDate().compareTo(entitlementDateTime), 0);
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlement.getId(), false, callContext);
        assertEquals(subscription.getBillingStartDate().compareTo(billingDatetime), 0);

        //Move clock to entitlementStartDate and verify that entitlement starts
        testListener.pushExpectedEvents(NextEvent.BLOCK);
        clock.setTime(entitlementDateTime);
        assertListenerStatus();

        //Verify that state is now ACTIVE
        entitlement = entitlementApi.getEntitlementForId(entitlementIds.get(0), false, callContext);
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

        //Move clock to billingStartDate and verify that subscription starts
        testListener.pushExpectedEvents(NextEvent.CREATE);
        clock.setTime(billingDatetime);
        assertListenerStatus();
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1158")
    public void testCreateBaseWithEntitlementInTheFutureAndChangeBCD() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final LocalDate entitlementDate = initialDate.plusMonths(1);
        final LocalDate billingDate = entitlementDate;

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun",  BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), entitlementDate, billingDate, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);

        assertEquals(entitlement.getState(), EntitlementState.PENDING);
        assertEquals(internalCallContext.toLocalDate(entitlement.getEffectiveStartDate()), entitlementDate);
        // shotgun-annual is SUBSCRIPTION aligned and has a 30 days trial (2013-09-07 + 30 days = 2013-10-07 for the phase)
        assertEquals(entitlement.getBillCycleDayLocal(), (Integer) 7);

        entitlement.updateBCD(11, null, callContext);

        // Still the 7
        assertEquals(entitlementApi.getEntitlementForId(entitlementId, false, callContext).getBillCycleDayLocal(), (Integer) 7);

        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        clock.addMonths(1);
        assertListenerStatus();

        final Entitlement entitlementActive = entitlementApi.getEntitlementForId(entitlement.getId(), false, callContext);
        assertEquals(entitlementActive.getState(), EntitlementState.ACTIVE);
        // Still the 7
        assertEquals(entitlementActive.getBillCycleDayLocal(), (Integer) 7);

        // Move clock to the 11th
        testListener.pushExpectedEvents(NextEvent.BCD_CHANGE);
        clock.addDays(5);
        assertListenerStatus();

        assertEquals(entitlementApi.getEntitlementForId(entitlementId, false, callContext).getBillCycleDayLocal(), (Integer) 11);
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
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), entitlementDate, billingDate, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);

        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(internalCallContext.toLocalDate(entitlement.getEffectiveStartDate()), entitlementDate);
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
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), entitlementDate, billingDate, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);

        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(internalCallContext.toLocalDate(entitlement.getEffectiveStartDate()), entitlementDate);
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
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), entitlementDate, billingDate, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, false, callContext);

        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(internalCallContext.toLocalDate(entitlement.getEffectiveStartDate()), initialDate);
    }

    @Test(groups = "slow")
    public void testCreateBaseSubscriptionsWithAddOns() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final String bundleKey2 = "bundleKey2";
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        testListener.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CREATE);
        entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), bundleKey2, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();


        // First bundle of EntitlementSpecifier will specify all new subscription
        final String bundleKey1 = "bundleKey1";
        final EntitlementSpecifier spec11 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("Shotgun",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null));
        final EntitlementSpecifier spec12 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("Telescopic-Scope",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null));
        final List<EntitlementSpecifier> specs1 = List.of(spec11, spec12);
        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier1 = new DefaultBaseEntitlementWithAddOnsSpecifier(null, bundleKey1, specs1, null, null, false);


        // Second bundle of EntitlementSpecifier will specify the previously created 'existingEntitlement'
        final EntitlementSpecifier spec22 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("Telescopic-Scope",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null));
        final List<EntitlementSpecifier> specs2 = List.of(spec22);
        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier2 = new DefaultBaseEntitlementWithAddOnsSpecifier(null, bundleKey2, specs2, null, null, false);

        final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers = List.of(baseEntitlementWithAddOnsSpecifier1, baseEntitlementWithAddOnsSpecifier2);


        // We expect 3 {BLOCK, CREATE} events for the 3 subscriptions created,.
        testListener.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.CREATE);
        final List<UUID> entitlementIds = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), baseEntitlementWithAddOnsSpecifiers, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        // Retun only the created subscriptions
        Assert.assertEquals(entitlementIds.size(), 3);

        final List<Entitlement> entitlementsForBundle1 = entitlementApi.getAllEntitlementsForAccountIdAndBundleExternalKey(account.getId(), bundleKey1, callContext);
        Assert.assertEquals(entitlementsForBundle1.size(), 2);

        // And yet we do have both the BASE and ADD_ON for bundleKey2
        final List<Entitlement> entitlementsForBundle2 = entitlementApi.getAllEntitlementsForAccountIdAndBundleExternalKey(account.getId(), bundleKey2, callContext);
        Assert.assertEquals(entitlementsForBundle2.size(), 2);

    }

    @Test(groups = "slow", expectedExceptions = EntitlementApiException.class)
    public void testCreateBaseSubscriptionsWithAddOnsMissingBase() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final String bundleKey2 = "bundleKey2";

        final EntitlementSpecifier spec22 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("Telescopic-Scope",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null));
        final List<EntitlementSpecifier> specs2 = List.of(spec22);
        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier2 = new DefaultBaseEntitlementWithAddOnsSpecifier(null, bundleKey2, specs2, null, null, false);

        final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers = List.of(baseEntitlementWithAddOnsSpecifier2);

        entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), baseEntitlementWithAddOnsSpecifiers, true, Collections.emptyList(), callContext);
    }

    @Test(groups = "slow")
    public void testCreateBaseSubscriptionsWithAddOnsBadOrdering() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final String bundleKey1 = "bundleKey1";
        final EntitlementSpecifier spec11 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("Telescopic-Scope",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null));
        final EntitlementSpecifier spec12 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("Shotgun",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null));
        final List<EntitlementSpecifier> specs1 = List.of(spec11, spec12);
        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier1 = new DefaultBaseEntitlementWithAddOnsSpecifier(null, bundleKey1, specs1, null, null, false);

        final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers = List.of(baseEntitlementWithAddOnsSpecifier1);


        testListener.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.CREATE);
        entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), baseEntitlementWithAddOnsSpecifiers, true, Collections.emptyList(), callContext);
        assertListenerStatus();

    }

    @Test(groups = "slow")
    public void testCreateBaseSubscriptionsWithAddOnsOnBasePendingChange() throws AccountApiException, EntitlementApiException
    {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        // Create base entitlement
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly", PhaseType.EVERGREEN);
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), initialDate, initialDate, false, true, Collections.emptyList(), callContext);
        Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, false, callContext);

        // Change base entitlement
        testListener.pushExpectedEvents(NextEvent.CHANGE);
        final LocalDate changeDate = initialDate.plusDays(10);
        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier("shotgun-monthly", PhaseType.EVERGREEN);
        baseEntitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec1), changeDate, Collections.emptyList(), callContext);

        // Add ADD_ON compatible with the new plan after the change
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier("telescopic-scope-monthly", PhaseType.EVERGREEN);
        final UUID addOnEntitlementId = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(spec2), changeDate, changeDate, false, Collections.emptyList(), callContext);

        List<Entitlement> bundleEntitlements = entitlementApi.getAllEntitlementsForBundle(baseEntitlement.getBundleId(), callContext);
        assertEquals(bundleEntitlements.size(), 2);

        assertEquals(baseEntitlement.getLastActivePlan().getName(), "pistol-monthly");

        clock.setDay(changeDate);

        final Entitlement baseEntitlementFinal = entitlementApi.getEntitlementForId(baseEntitlementId, false, callContext);
        assertEquals(baseEntitlementFinal.getLastActivePlan().getName(), "shotgun-monthly");
        assertEquals(baseEntitlementFinal.getEffectiveStartDate().toLocalDate(), initialDate);
        assertNull(baseEntitlementFinal.getEffectiveEndDate());

        final Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(addOnEntitlementId, false, callContext);
        assertEquals(addOnEntitlement.getLastActiveProductCategory(), ProductCategory.ADD_ON);
        assertEquals(addOnEntitlement.getLastActivePlan().getName(), "telescopic-scope-monthly");
        assertEquals(addOnEntitlement.getEffectiveStartDate().toLocalDate(), changeDate);
        assertNull(addOnEntitlement.getEffectiveEndDate());
    }

    @Test(groups = "slow", expectedExceptions = EntitlementApiException.class, expectedExceptionsMessageRegExp = "Can't create AddOn.*\\(Not available\\)")
    public void testCreateBaseSubscriptionsWithAddOnsOnBasePendingChangeUndone() throws AccountApiException, EntitlementApiException
    {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        // Create base entitlement
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly", PhaseType.EVERGREEN);
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), initialDate, initialDate, false, true, Collections.emptyList(), callContext);
        Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, false, callContext);

        // Change base entitlement
        final LocalDate changeDate = initialDate.plusDays(10);
        testListener.pushExpectedEvents(NextEvent.CHANGE);
        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier("shotgun-monthly", PhaseType.EVERGREEN);
        baseEntitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec1), changeDate, Collections.emptyList(), callContext);

        // Cancel the change of base entitlement
        final LocalDate changeDate2 = initialDate.plusDays(10);
        testListener.pushExpectedEvents(NextEvent.UNDO_CHANGE);
        baseEntitlement.undoChangePlan(Collections.emptyList(), callContext);

        // Add ADD_ON compatible with the new plan after undoing the change
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier("telescopic-scope-monthly", PhaseType.EVERGREEN);
        final UUID addOnEntitlementId = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(spec2), changeDate2, changeDate2, false, Collections.emptyList(), callContext);
        final Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(addOnEntitlementId, false, callContext);
    }

    @Test(groups = "slow")
    @Ignore(value = "This testcase shows a new misbehavior left after fixing GH#1355")
    public void testCreateBaseSubscriptionsWithAddOnsOnBaseChangeCanceled() throws AccountApiException, EntitlementApiException
    {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        // Create base entitlement
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly", PhaseType.EVERGREEN);
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), initialDate, initialDate, false, true, Collections.emptyList(), callContext);
        Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, false, callContext);

        // Change base entitlement
        final LocalDate changeDate = initialDate.plusDays(10);
        testListener.pushExpectedEvents(NextEvent.CHANGE);
        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier("shotgun-monthly", PhaseType.EVERGREEN);
        baseEntitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec1), changeDate, Collections.emptyList(), callContext);

        // Add ADD_ON compatible with the new plan after undoing the change
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier("telescopic-scope-monthly", PhaseType.EVERGREEN);
        final UUID addOnEntitlementId = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(spec2), changeDate, changeDate, false, Collections.emptyList(), callContext);
        final Entitlement addOnEntitlement = entitlementApi.getEntitlementForId(addOnEntitlementId, false, callContext);

        final LocalDate undoDate = changeDate.minusDays(5);
        clock.setDay(undoDate);

        // Cancel the change of base entitlement
        testListener.pushExpectedEvents(NextEvent.UNDO_CHANGE);
        baseEntitlement.undoChangePlan(Collections.emptyList(), callContext);

        clock.setDay(changeDate.plusDays(1));

        List<Entitlement> bundleEntitlements = entitlementApi.getAllEntitlementsForBundle(baseEntitlement.getBundleId(), callContext);
        assertEquals(bundleEntitlements.size(), 2);

        final Entitlement baseEntitlementFinal = entitlementApi.getEntitlementForId(baseEntitlementId, false, callContext);
        assertEquals(baseEntitlementFinal.getLastActivePlan().getName(), "pistol-monthly"); // Original plan
        assertEquals(baseEntitlementFinal.getEffectiveStartDate().toLocalDate(), initialDate); // Original start date
        assertNull(baseEntitlementFinal.getEffectiveEndDate());

        assertEquals(addOnEntitlement.getLastActiveProductCategory(), ProductCategory.ADD_ON);
        assertEquals(addOnEntitlement.getLastActivePlan().getName(), "telescopic-scope-monthly");
        assertEquals(addOnEntitlement.getEffectiveStartDate().toLocalDate(), changeDate);
        assertEquals(addOnEntitlement.getState(), EntitlementState.CANCELLED);
        assertNull(addOnEntitlement.getEffectiveEndDate());
    }

    @Test(groups = "slow")
    public void testCreatedBundledStandaloneEntitlements() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Knife", BillingPeriod.MONTHLY, "notrial", null);

        // Create STANDALONE entitlement
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, false, callContext);

        assertEquals(baseEntitlement.getAccountId(), account.getId());
        assertEquals(baseEntitlement.getBundleExternalKey(), account.getExternalKey());
        assertEquals(baseEntitlement.getLastActiveProduct().getName(), "Knife");
        assertEquals(baseEntitlement.getLastActivePlan().getName(), "knife-monthly-notrial");
        assertEquals(baseEntitlement.getLastActiveProductCategory(), ProductCategory.STANDALONE);

        // Add another STANDALONE entitlement
        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier("Knife", BillingPeriod.MONTHLY, "notrial", null);
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID anotherStandaloneEntitlementId = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(spec1), initialDate, initialDate, false, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement anotherStandaloneEntitlement = entitlementApi.getEntitlementForId(anotherStandaloneEntitlementId, false, callContext);

        assertEquals(anotherStandaloneEntitlement.getAccountId(), account.getId());
        assertEquals(anotherStandaloneEntitlement.getBundleExternalKey(), account.getExternalKey());
        assertEquals(anotherStandaloneEntitlement.getBundleId(), baseEntitlement.getBundleId());

        assertEquals(anotherStandaloneEntitlement.getLastActivePriceList().getName(), "notrial");
        assertEquals(anotherStandaloneEntitlement.getLastActiveProduct().getName(), "Knife");
        assertEquals(anotherStandaloneEntitlement.getLastActivePlan().getName(), "knife-monthly-notrial");
        assertEquals(anotherStandaloneEntitlement.getLastActiveProductCategory(), ProductCategory.STANDALONE);

        List<Entitlement> bundleEntitlements = entitlementApi.getAllEntitlementsForBundle(anotherStandaloneEntitlement.getBundleId(), callContext);
        assertEquals(bundleEntitlements.size(), 2);

        bundleEntitlements = entitlementApi.getAllEntitlementsForAccountIdAndBundleExternalKey(account.getId(), account.getExternalKey(), callContext);
        assertEquals(bundleEntitlements.size(), 2);
    }

    @Test(groups = "slow")
    public void testCreateBaseEntitlement() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final DateTime initialDateTime = new DateTime(2013, 8, 7, 10, 30);
        clock.setTime(initialDateTime);

        final Account account = createAccount(getAccountData(7));

        final EntitlementSpecifier spec = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null));
        final List<EntitlementSpecifier> specs = List.of(spec);
        final BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(null, null, specs, initialDateTime, initialDateTime, false);
        final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers = List.of(baseEntitlementWithAddOnsSpecifier);

        //create entitlement with current datetime
        testListener.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CREATE); //entitlement created immediately
        final List<UUID> entitlementIds = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), baseEntitlementWithAddOnsSpecifiers, true, Collections.emptyList(), callContext);
        assertEquals(entitlementIds.size(), 1);
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementIds.get(0), false, callContext);
        assertListenerStatus();

        //Verify that state is ACTIVE
        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

        //Verify entitlementStartDate and billingStartDate
        assertEquals(entitlement.getEffectiveStartDate().compareTo(initialDateTime), 0);
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlement.getId(), false, callContext);
        assertEquals(subscription.getBillingStartDate().compareTo(initialDateTime), 0);

    }
}
