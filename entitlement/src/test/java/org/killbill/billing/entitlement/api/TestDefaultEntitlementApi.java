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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestDefaultEntitlementApi extends EntitlementTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testCheckStaleStates() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Keep the same object for the whole test, to make sure we refresh its state before r/w calls
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), null, null, null, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Add ADD_ON
        // Keep the same object for the whole test, to make sure we refresh its state before r/w calls
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final Entitlement addOnEntitlement = entitlementApi.addEntitlement(entitlement.getBundleId(), addOnSpec, null, initialDate, initialDate, false, ImmutableList.<PluginProperty>of(), callContext);
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
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), null, null, null, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

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
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), null, null, null, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
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
        final Entitlement baseEntitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), null, null, null, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Add ADD_ON
        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final Entitlement telescopicEntitlement = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), spec1, null, initialDate, initialDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

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
    public void testPauseUnpause() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final Entitlement baseEntitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), null, null, null, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        clock.addDays(1);
        final LocalDate effectiveDateSpec1 = new LocalDate(clock.getUTCNow(), account.getTimeZone());
        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final Entitlement telescopicEntitlement = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), spec1, null, effectiveDateSpec1, effectiveDateSpec1, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Block all entitlement in the bundle
        clock.addDays(5);

        testListener.pushExpectedEvents(NextEvent.BLOCK);
        entitlementApi.pause(baseEntitlement.getBundleId(), new LocalDate(clock.getUTCNow()), ImmutableList.<PluginProperty>of(), callContext);
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

        // Try to add an ADD_ON, it should fail
        try {
            final PlanPhaseSpecifier spec3 = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
            final Entitlement telescopicEntitlement3 = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), spec1, null, effectiveDateSpec1, effectiveDateSpec1, false, ImmutableList.<PluginProperty>of(), callContext);
        } catch (EntitlementApiException e) {
            assertEquals(e.getCode(), ErrorCode.SUB_GET_NO_SUCH_BASE_SUBSCRIPTION.getCode());
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
        final Entitlement baseEntitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), null, null, null, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

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
        final Entitlement baseEntitlement = entitlementApi.createBaseEntitlement(accountSrc.getId(), spec, accountSrc.getExternalKey(), null, null, null, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
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
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), null, initialDate, initialDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

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
    public void testCreateBaseEntitlementWithAddOns() throws AccountApiException, EntitlementApiException, SubscriptionBaseApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier baseSpec = new PlanPhaseSpecifier("Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("Cleaning", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        final String externalKey = "baseExternalKey";
        EntitlementSpecifier baseEntitlementSpecifier = new DefaultEntitlementSpecifier(baseSpec, null);
        EntitlementSpecifier addOnEntitlementSpecifier = new DefaultEntitlementSpecifier(addOnSpec, null);

        final List<EntitlementSpecifier> specifierList = new ArrayList<EntitlementSpecifier>();
        specifierList.add(baseEntitlementSpecifier);
        specifierList.add(addOnEntitlementSpecifier);

        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.CREATE, NextEvent.BLOCK);
        final Entitlement entitlement = entitlementApi.createBaseEntitlementWithAddOns(account.getId(), externalKey, specifierList, initialDate, initialDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        assertNotNull(entitlement);

        final List<Entitlement> allEntitlementsForBundle = entitlementApi.getAllEntitlementsForBundle(entitlement.getBundleId(), callContext);
        assertTrue(allEntitlementsForBundle.size() == 2);

        final Entitlement baseEntitlement = allEntitlementsForBundle.get(0);
        final Entitlement addOnEntitlement = allEntitlementsForBundle.get(1);

        assertEquals(baseEntitlement.getLastActiveProduct().getName(), "Pistol");
        assertEquals(baseEntitlement.getLastActiveProductCategory(), ProductCategory.BASE);

        assertEquals(addOnEntitlement.getLastActiveProduct().getName(), "Cleaning");
        assertEquals(addOnEntitlement.getLastActiveProductCategory(), ProductCategory.ADD_ON);

    }

    @Test(groups = "slow")
    public void testCreateBaseEntitlementWithInvalidAddOn() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier baseSpec = new PlanPhaseSpecifier("Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("Invalid",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        final String externalKey = "baseExternalKey";
        EntitlementSpecifier baseEntitlementSpecifier = new DefaultEntitlementSpecifier(baseSpec, null);
        EntitlementSpecifier addOnEntitlementSpecifier = new DefaultEntitlementSpecifier(addOnSpec, null);

        final List<EntitlementSpecifier> specifierList = new ArrayList<EntitlementSpecifier>();
        specifierList.add(baseEntitlementSpecifier);
        specifierList.add(addOnEntitlementSpecifier);

        try {
            entitlementApi.createBaseEntitlementWithAddOns(account.getId(), externalKey, specifierList, initialDate, initialDate, false, ImmutableList.<PluginProperty>of(), callContext);
            fail();
        } catch (EntitlementApiException e) {
            assertEquals(e.getMessage(), "Could not find any product named 'Invalid'");
        }

        final List<Entitlement> allEntitlementsForAccount = entitlementApi.getAllEntitlementsForAccountId(account.getId(), callContext);
        assertTrue(allEntitlementsForAccount.size() == 0);

    }

    @Test(groups = "slow")
    public void testCreateBaseEntitlementWithoutBaseEntitlement() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier baseSpec = new PlanPhaseSpecifier("Cleaning",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("Bullets",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        final String externalKey = "addOnExternalKey";
        EntitlementSpecifier addOnEntitlementSpecifier1 = new DefaultEntitlementSpecifier(baseSpec, null);
        EntitlementSpecifier addOnEntitlementSpecifier2 = new DefaultEntitlementSpecifier(addOnSpec, null);

        final List<EntitlementSpecifier> specifierList = new ArrayList<EntitlementSpecifier>();
        specifierList.add(addOnEntitlementSpecifier1);
        specifierList.add(addOnEntitlementSpecifier2);

        try {
            entitlementApi.createBaseEntitlementWithAddOns(account.getId(), externalKey, specifierList, initialDate, initialDate, false, ImmutableList.<PluginProperty>of(), callContext);
            fail();
        } catch (EntitlementApiException e) {
            assertEquals(e.getMessage(), "Missing Base Subscription.");
        }

        final List<Entitlement> allEntitlementsForAccount = entitlementApi.getAllEntitlementsForAccountId(account.getId(), callContext);
        assertTrue(allEntitlementsForAccount.size() == 0);
    }


    @Test(groups = "slow")
    public void testCreateBaseWithDifferentInTheFuture() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final LocalDate entitlementDate = initialDate.plusDays(3);
        final LocalDate billingDate = initialDate.plusDays(5);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), null, entitlementDate, billingDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        assertEquals(entitlement.getState(), EntitlementState.PENDING);
        assertEquals(entitlement.getEffectiveStartDate(), entitlementDate);

        testListener.pushExpectedEvents(NextEvent.BLOCK);
        clock.addDays(3);
        assertListenerStatus();

        testListener.pushExpectedEvents(NextEvent.CREATE);
        clock.addDays(2);
        assertListenerStatus();
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
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), null, entitlementDate, billingDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

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
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), null, entitlementDate, billingDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

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
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), null, entitlementDate, billingDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

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
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), null, entitlementDate, billingDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

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
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), null, entitlementDate, billingDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        assertEquals(entitlement.getState(), EntitlementState.ACTIVE);
        assertEquals(entitlement.getEffectiveStartDate(), initialDate);
    }

}
