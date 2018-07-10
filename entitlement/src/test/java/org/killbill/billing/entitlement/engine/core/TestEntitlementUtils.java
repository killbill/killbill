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

package org.killbill.billing.entitlement.engine.core;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.entitlement.AccountEventsStreams;
import org.killbill.billing.entitlement.EntitlementService;
import org.killbill.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import org.killbill.billing.entitlement.EventsStream;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementApi;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.MoreObjects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class TestEntitlementUtils extends EntitlementTestSuiteWithEmbeddedDB {

    private DefaultEntitlement baseEntitlement;
    private DefaultEntitlement addOnEntitlement;
    // Dates for the base plan only
    private DateTime baseEffectiveEOTCancellationOrChangeDateTime;
    private LocalDate baseEffectiveCancellationOrChangeDate;

    private final LocalDate initialDate = new LocalDate(2013, 8, 8);

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        if (hasFailed()) {
            return;
        }
        clock.setDay(initialDate);
        final Account account = createAccount(getAccountData(7));


        // Create base entitlement
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier baseSpec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(baseSpec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        baseEntitlement = (DefaultEntitlement) entitlementApi.getEntitlementForId(baseEntitlementId, callContext);

        // Add ADD_ON
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final UUID addOnEntitlementId = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec), null, null, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        addOnEntitlement = (DefaultEntitlement) entitlementApi.getEntitlementForId(addOnEntitlementId, callContext);

        // Verify the initial state
        checkFutureBlockingStatesToCancel(baseEntitlement, null, null);
        checkFutureBlockingStatesToCancel(addOnEntitlement, null, null);
        checkFutureBlockingStatesToCancel(baseEntitlement, addOnEntitlement, null);

        testListener.pushExpectedEvents(NextEvent.PHASE, NextEvent.PHASE);
        // Phase for the base plan is 2013/09/07 (30 days trial) but it's 2013/09/08 for the add-on (1 month discount)
        clock.setDay(new LocalDate(2013, 9, 8));
        assertListenerStatus();

        // Note! Make sure to align CTD and cancellation/change effective time with the phase event effective time to avoid timing issues in comparisons
        baseEffectiveEOTCancellationOrChangeDateTime = baseEntitlement.getSubscriptionBase().getAllTransitions().get(1).getEffectiveTransitionTime().plusMonths(1);
        Assert.assertEquals(baseEffectiveEOTCancellationOrChangeDateTime.toLocalDate(), new LocalDate(2013, 10, 7));
        baseEffectiveCancellationOrChangeDate = baseEffectiveEOTCancellationOrChangeDateTime.toLocalDate();
        // Set manually since no invoice
        subscriptionInternalApi.setChargedThroughDate(baseEntitlement.getId(), baseEffectiveEOTCancellationOrChangeDateTime, internalCallContext);
    }

    @Test(groups = "slow", description = "Verify add-ons blocking states are added for EOT cancellations")
    public void testCancellationEOT() throws Exception {
        // Cancel the base plan
        final DefaultEntitlement cancelledBaseEntitlement = (DefaultEntitlement) baseEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.END_OF_TERM, BillingActionPolicy.END_OF_TERM, ImmutableList.<PluginProperty>of(), callContext);
        // No blocking event (EOT)
        assertListenerStatus();

        // Verify we compute the right blocking states for the "read" path...
        checkFutureBlockingStatesToCancel(addOnEntitlement, null, null);
        checkFutureBlockingStatesToCancel(cancelledBaseEntitlement, addOnEntitlement, baseEffectiveEOTCancellationOrChangeDateTime);
        // and for the "write" path (which will be exercised when the future notification kicks in).
        checkActualBlockingStatesToCancel(cancelledBaseEntitlement, addOnEntitlement, baseEffectiveEOTCancellationOrChangeDateTime, false);
        // Verify also the blocking states DAO adds events not on disk
        checkBlockingStatesDAO(baseEntitlement, addOnEntitlement, baseEffectiveCancellationOrChangeDate, true);

        // Verify the notification kicks in
        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.BLOCK);
        clock.addDays(30);
        assertListenerStatus();

        // Refresh the state
        final DefaultEntitlement cancelledAddOnEntitlement = (DefaultEntitlement) entitlementApi.getEntitlementForId(addOnEntitlement.getId(), callContext);

        // Verify we compute the right blocking states for the "read" path...
        checkFutureBlockingStatesToCancel(cancelledBaseEntitlement, null, null);
        checkFutureBlockingStatesToCancel(cancelledAddOnEntitlement, null, null);
        checkFutureBlockingStatesToCancel(cancelledBaseEntitlement, cancelledAddOnEntitlement, null);
        // ...and for the "write" path (which has been exercised when the notification kicked in).
        checkActualBlockingStatesToCancel(cancelledBaseEntitlement, cancelledAddOnEntitlement, baseEffectiveEOTCancellationOrChangeDateTime, false);
        // Verify also the blocking states API doesn't add too many events (now on disk)
        checkBlockingStatesDAO(cancelledBaseEntitlement, cancelledAddOnEntitlement, baseEffectiveCancellationOrChangeDate, true);
    }

    @Test(groups = "slow", description = "Verify add-ons blocking states are not impacted for add-on IMM cancellations")
    public void testCancellationBaseEOTAddOnIMM() throws Exception {
        // Cancel the base plan
        final DefaultEntitlement cancelledBaseEntitlement = (DefaultEntitlement) baseEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.END_OF_TERM, BillingActionPolicy.END_OF_TERM, ImmutableList.<PluginProperty>of(), callContext);
        // No blocking event (EOT)
        assertListenerStatus();

        // Cancel the add-on
        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK);
        final DefaultEntitlement cancelledAddOnEntitlement = (DefaultEntitlement) addOnEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Verify the blocking states API doesn't mix the dates (all blocking states are on disk)
        checkBlockingStatesDAO(cancelledBaseEntitlement, cancelledAddOnEntitlement, baseEffectiveCancellationOrChangeDate, clock.getUTCToday(), true);
    }

    @Test(groups = "slow", description = "Verify add-ons blocking states are added for IMM billing / EOT entitlement cancellations")
    public void testCancellationBillingIMMEntitlementEOT() throws Exception {
        // Cancel the base plan
        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.CANCEL);
        final DefaultEntitlement cancelledBaseEntitlement = (DefaultEntitlement) baseEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.END_OF_TERM, BillingActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Verify the blocking states API sees the EOT cancellation (add-on blocking state not on disk)
        checkBlockingStatesDAO(cancelledBaseEntitlement, addOnEntitlement, baseEffectiveCancellationOrChangeDate, true);
    }

    @Test(groups = "slow", description = "Verify add-ons blocking states are not impacted by IMM cancellations")
    public void testCancellationIMM() throws Exception {
        // Approximate check, as the blocking state check (checkBlockingStatesDAO) could be a bit off
        final DateTime cancellationDateTime = clock.getUTCNow();
        final LocalDate cancellationDate = clock.getUTCToday();

        // Cancel the base plan
        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.BLOCK);
        final DefaultEntitlement cancelledBaseEntitlement = (DefaultEntitlement) baseEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Refresh the add-on state
        final DefaultEntitlement cancelledAddOnEntitlement = (DefaultEntitlement) entitlementApi.getEntitlementForId(addOnEntitlement.getId(), callContext);

        // Verify we compute the right blocking states for the "read" path...
        checkFutureBlockingStatesToCancel(cancelledBaseEntitlement, null, null);
        checkFutureBlockingStatesToCancel(cancelledAddOnEntitlement, null, null);
        checkFutureBlockingStatesToCancel(cancelledBaseEntitlement, cancelledAddOnEntitlement, null);
        // ...and for the "write" path (which has been exercised in the cancel call above).
        checkActualBlockingStatesToCancel(cancelledBaseEntitlement, cancelledAddOnEntitlement, cancellationDateTime, true);
        // Verify also the blocking states DAO doesn't add too many events (all on disk)
        checkBlockingStatesDAO(cancelledBaseEntitlement, cancelledAddOnEntitlement, cancellationDate, true);

        clock.addDays(30);
        // No new event
        assertListenerStatus();

        checkFutureBlockingStatesToCancel(cancelledBaseEntitlement, null, null);
        checkFutureBlockingStatesToCancel(cancelledAddOnEntitlement, null, null);
        checkFutureBlockingStatesToCancel(cancelledBaseEntitlement, cancelledAddOnEntitlement, null);
        checkActualBlockingStatesToCancel(cancelledBaseEntitlement, cancelledAddOnEntitlement, cancellationDateTime, true);
        checkBlockingStatesDAO(cancelledBaseEntitlement, cancelledAddOnEntitlement, cancellationDate, true);
    }

    // See https://github.com/killbill/killbill/issues/121
    @Test(groups = "slow", description = "Verify add-ons blocking states are not impacted by EOT billing cancellations")
    public void testCancellationIMMBillingEOT() throws Exception {
        // Approximate check, as the blocking state check (checkBlockingStatesDAO) could be a bit off
        final DateTime cancellationDateTime = clock.getUTCNow();
        final LocalDate cancellationDate = clock.getUTCToday();

        // Cancel the base plan
        testListener.pushExpectedEvents(NextEvent.BLOCK, NextEvent.BLOCK);
        final DefaultEntitlement cancelledBaseEntitlement = (DefaultEntitlement) baseEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.END_OF_TERM, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Refresh the add-on state
        final DefaultEntitlement cancelledAddOnEntitlement = (DefaultEntitlement) entitlementApi.getEntitlementForId(addOnEntitlement.getId(), callContext);

        // Verify we compute the right blocking states for the "read" path...
        checkFutureBlockingStatesToCancel(cancelledBaseEntitlement, null, null);
        checkFutureBlockingStatesToCancel(cancelledAddOnEntitlement, null, null);
        checkFutureBlockingStatesToCancel(cancelledBaseEntitlement, cancelledAddOnEntitlement, null);
        // ...and for the "write" path (which has been exercised in the cancel call above).
        checkActualBlockingStatesToCancel(cancelledBaseEntitlement, cancelledAddOnEntitlement, cancellationDateTime, true);
        // Verify also the blocking states DAO doesn't add too many events (all on disk)
        checkBlockingStatesDAO(cancelledBaseEntitlement, cancelledAddOnEntitlement, cancellationDate, true);

        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.CANCEL);
        clock.addDays(30);
        assertListenerStatus();

        checkFutureBlockingStatesToCancel(cancelledBaseEntitlement, null, null);
        checkFutureBlockingStatesToCancel(cancelledAddOnEntitlement, null, null);
        checkFutureBlockingStatesToCancel(cancelledBaseEntitlement, cancelledAddOnEntitlement, null);
        checkActualBlockingStatesToCancel(cancelledBaseEntitlement, cancelledAddOnEntitlement, cancellationDateTime, true);
        checkBlockingStatesDAO(cancelledBaseEntitlement, cancelledAddOnEntitlement, cancellationDate, true);
    }

    @Test(groups = "slow", description = "Verify add-ons blocking states are added for EOT change plans")
    public void testChangePlanEOT() throws Exception {
        // Change plan EOT to Assault-Rifle (Telescopic-Scope is included)
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
        final DefaultEntitlement changedBaseEntitlement = (DefaultEntitlement) baseEntitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec), new LocalDate(2013, 10, 7), ImmutableList.<PluginProperty>of(), callContext);
        // No blocking event (EOT)
        assertListenerStatus();

        // Verify we compute the right blocking states for the "read" path...
        checkFutureBlockingStatesToCancel(addOnEntitlement, null, null);
        checkFutureBlockingStatesToCancel(changedBaseEntitlement, addOnEntitlement, baseEffectiveEOTCancellationOrChangeDateTime);
        // ...and for the "write" path (which will be exercised when the future notification kicks in).
        checkActualBlockingStatesToCancel(changedBaseEntitlement, addOnEntitlement, baseEffectiveEOTCancellationOrChangeDateTime, false);
        // Verify also the blocking states DAO adds events not on disk
        checkBlockingStatesDAO(changedBaseEntitlement, addOnEntitlement, baseEffectiveCancellationOrChangeDate, false);

        // Verify the notification kicks in
        testListener.pushExpectedEvents(NextEvent.CHANGE, NextEvent.CANCEL, NextEvent.BLOCK);
        clock.addDays(30);
        assertListenerStatus();

        // Refresh the state
        final DefaultEntitlement cancelledAddOnEntitlement = (DefaultEntitlement) entitlementApi.getEntitlementForId(addOnEntitlement.getId(), callContext);

        // Verify we compute the right blocking states for the "read" path...
        checkFutureBlockingStatesToCancel(changedBaseEntitlement, null, null);
        checkFutureBlockingStatesToCancel(cancelledAddOnEntitlement, null, null);
        checkFutureBlockingStatesToCancel(changedBaseEntitlement, cancelledAddOnEntitlement, null);
        // ...and for the "write" path (which has been exercised when the notification kicked in).
        checkActualBlockingStatesToCancel(changedBaseEntitlement, cancelledAddOnEntitlement, baseEffectiveEOTCancellationOrChangeDateTime, false);
        // Verify also the blocking states API doesn't add too many events (now on disk)
        checkBlockingStatesDAO(changedBaseEntitlement, cancelledAddOnEntitlement, baseEffectiveCancellationOrChangeDate, false);
    }

    @Test(groups = "slow", description = "Verify we don't mix add-ons for EOT changes")
    public void testChangePlanEOTWith2AddOns() throws Exception {
        // Add a second ADD_ON (Laser-Scope is available, not included)
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier secondAddOnSpec = new PlanPhaseSpecifier("Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final UUID secondAddOnEntitlementId = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(secondAddOnSpec), clock.getUTCToday(), clock.getUTCToday(), false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Change plan EOT to Assault-Rifle (Telescopic-Scope is included)
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
        final DefaultEntitlement changedBaseEntitlement = (DefaultEntitlement) baseEntitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec), new LocalDate(2013, 10, 7), ImmutableList.<PluginProperty>of(), callContext);
        // No blocking event (EOT)
        assertListenerStatus();

        // Verify the blocking states DAO adds events not on disk for the first add-on...
        checkBlockingStatesDAO(changedBaseEntitlement, addOnEntitlement, baseEffectiveCancellationOrChangeDate, false);
        // ...but not for the second one
        final List<BlockingState> blockingStatesForSecondAddOn = blockingStatesForBlockedId(secondAddOnEntitlementId);
        Assert.assertEquals(blockingStatesForSecondAddOn.size(), 1);
    }

    @Test(groups = "slow", description = "Verify add-ons blocking states are added for IMM change plans")
    public void testChangePlanIMM() throws Exception {
        // Approximate check, as the blocking state check (checkBlockingStatesDAO) could be a bit off
        final DateTime changeDateTime = clock.getUTCNow();
        final LocalDate changeDate = clock.getUTCToday();

        // Change plan IMM (upgrade) to Assault-Rifle (Telescopic-Scope is included)
        testListener.pushExpectedEvents(NextEvent.CHANGE, NextEvent.CANCEL, NextEvent.BLOCK);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
        final DefaultEntitlement changedBaseEntitlement = (DefaultEntitlement) baseEntitlement.changePlan(new DefaultEntitlementSpecifier(spec), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // We need to add a 1s delay before invoking the eventsStreamBuilder in the checks below, because
        // the ClockMock truncates milliseconds. Otherwise, utcNow is equal to the changeDateTime, and
        // the change is considered as pending (see DefaultEventsStream#getPendingSubscriptionEvents)
        clock.addDeltaFromReality(1000);

        // Refresh the add-on state
        final DefaultEntitlement cancelledAddOnEntitlement = (DefaultEntitlement) entitlementApi.getEntitlementForId(addOnEntitlement.getId(), callContext);

        // Verify we compute the right blocking states for the "read" path...
        checkFutureBlockingStatesToCancel(changedBaseEntitlement, null, null);
        checkFutureBlockingStatesToCancel(cancelledAddOnEntitlement, null, null);
        checkFutureBlockingStatesToCancel(changedBaseEntitlement, cancelledAddOnEntitlement, null);
        // ...and for the "write" path (which has been exercised in the change call above).
        checkActualBlockingStatesToCancel(changedBaseEntitlement, cancelledAddOnEntitlement, changeDateTime, true);
        // Verify also the blocking states DAO doesn't add too many events (all on disk)
        checkBlockingStatesDAO(changedBaseEntitlement, cancelledAddOnEntitlement, changeDate, false);

        clock.addDays(30);
        // No new event
        assertListenerStatus();

        checkFutureBlockingStatesToCancel(changedBaseEntitlement, null, null);
        checkFutureBlockingStatesToCancel(cancelledAddOnEntitlement, null, null);
        checkFutureBlockingStatesToCancel(changedBaseEntitlement, cancelledAddOnEntitlement, null);
        checkActualBlockingStatesToCancel(changedBaseEntitlement, cancelledAddOnEntitlement, changeDateTime, true);
        checkBlockingStatesDAO(changedBaseEntitlement, cancelledAddOnEntitlement, changeDate, false);
    }

    @Test(groups = "slow", description = "Verify add-ons are not active after base entitlement is cancelled")
    public void testCancelAddonsWhenBaseEntitlementIsCancelled() throws Exception {
        // Add a second ADD_ON
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.PHASE);
        final PlanPhaseSpecifier addOn2Spec = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final UUID addOn2EntitlementId = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOn2Spec), initialDate, initialDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement addOn2Entitlement = entitlementApi.getEntitlementForId(addOn2EntitlementId, callContext);

        // Date prior to the base cancellation date to verify it is not impacted by the base cancellation (in contrary to the second add-on)
        final LocalDate addOn1CancellationDate = new LocalDate(2013, 9, 9);
        addOnEntitlement.cancelEntitlementWithDate(addOn1CancellationDate, true, ImmutableList.<PluginProperty>of(), callContext);

        final LocalDate addOn2CancellationDate = new LocalDate(2013, 11, 11);
        addOn2Entitlement.cancelEntitlementWithDate(addOn2CancellationDate, true, ImmutableList.<PluginProperty>of(), callContext);

        // Before the base entitlement is cancelled, respect the specified cancellation date
        Assert.assertEquals(entitlementApi.getEntitlementForId(addOn2Entitlement.getId(), callContext).getEffectiveEndDate(), addOn2CancellationDate);

        final LocalDate baseCancellationDate = new LocalDate(2013, 10, 10);
        baseEntitlement.cancelEntitlementWithDate(baseCancellationDate, true, ImmutableList.<PluginProperty>of(), callContext);

        // After the base entitlement is cancelled, verify the date is overridden
        Assert.assertEquals(entitlementApi.getEntitlementForId(addOn2Entitlement.getId(), callContext).getEffectiveEndDate(), baseCancellationDate);

        // No further event yet
        assertListenerStatus();

        // Verify the cancellation dates
        Assert.assertEquals(entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext).getEffectiveEndDate(), baseCancellationDate);
        Assert.assertEquals(entitlementApi.getEntitlementForId(addOnEntitlement.getId(), callContext).getEffectiveEndDate(), addOn1CancellationDate);
        Assert.assertEquals(entitlementApi.getEntitlementForId(addOn2Entitlement.getId(), callContext).getEffectiveEndDate(), baseCancellationDate);

        // Move to addOn1CancellationDate
        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK);
        clock.setDay(new LocalDate(2013, 9, 9));
        assertListenerStatus();


        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.BLOCK);
        clock.setDay(new LocalDate(2013, 10, 10));
        assertListenerStatus();

        // Verify the cancellation dates
        Assert.assertEquals(entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext).getEffectiveEndDate(), baseCancellationDate);
        Assert.assertEquals(entitlementApi.getEntitlementForId(addOnEntitlement.getId(), callContext).getEffectiveEndDate(), addOn1CancellationDate);
        Assert.assertEquals(entitlementApi.getEntitlementForId(addOn2Entitlement.getId(), callContext).getEffectiveEndDate(), baseCancellationDate);
    }

    // Test the "read" path
    private void checkFutureBlockingStatesToCancel(final DefaultEntitlement baseEntitlement, @Nullable final DefaultEntitlement addOnEntitlement, @Nullable final DateTime effectiveCancellationDateTime) throws EntitlementApiException {
        final Collection<BlockingState> blockingStatesForCancellationViaEntitlement = computeFutureBlockingStatesForAssociatedAddonsViaEntitlement(baseEntitlement);
        doCheckFutureBlockingStatesToCancel(addOnEntitlement, effectiveCancellationDateTime, blockingStatesForCancellationViaEntitlement);

        final Collection<BlockingState> blockingStatesForCancellationViaAccount = computeFutureBlockingStatesForAssociatedAddonsViaAccount(baseEntitlement);
        doCheckFutureBlockingStatesToCancel(addOnEntitlement, effectiveCancellationDateTime, blockingStatesForCancellationViaAccount);
    }

    private void doCheckFutureBlockingStatesToCancel(final DefaultEntitlement addOnEntitlement, final DateTime effectiveCancellationDateTime, final Collection<BlockingState> blockingStatesForCancellation) {
        if (addOnEntitlement == null || effectiveCancellationDateTime == null) {
            Assert.assertEquals(blockingStatesForCancellation.size(), 0);
        } else {
            Assert.assertEquals(blockingStatesForCancellation.size(), 1);
            final BlockingState blockingState = blockingStatesForCancellation.iterator().next();
            Assert.assertEquals(blockingState.getBlockedId(), addOnEntitlement.getId());
            Assert.assertEquals(blockingState.getEffectiveDate(), effectiveCancellationDateTime);
            Assert.assertEquals(blockingState.getType(), BlockingStateType.SUBSCRIPTION);
            Assert.assertEquals(blockingState.getService(), EntitlementService.ENTITLEMENT_SERVICE_NAME);
            Assert.assertEquals(blockingState.getStateName(), DefaultEntitlementApi.ENT_STATE_CANCELLED);
        }
    }

    // Test the "write" path
    private void checkActualBlockingStatesToCancel(final DefaultEntitlement baseEntitlement, final DefaultEntitlement addOnEntitlement, @Nullable final DateTime effectiveCancellationDateTime, final boolean approximateDateCheck) throws EntitlementApiException {
        final Collection<BlockingState> blockingStatesForCancellationViaEntitlement = computeBlockingStatesForAssociatedAddonsViaEntitlement(baseEntitlement, MoreObjects.firstNonNull(effectiveCancellationDateTime, initialDate.toDateTimeAtStartOfDay()));
        doCheckActualBlockingStatesToCancel(addOnEntitlement, effectiveCancellationDateTime, approximateDateCheck, blockingStatesForCancellationViaEntitlement);

        final Collection<BlockingState> blockingStatesForCancellationViaAccount = computeBlockingStatesForAssociatedAddonsViaAccount(baseEntitlement, MoreObjects.firstNonNull(effectiveCancellationDateTime, initialDate.toDateTimeAtStartOfDay()));
        doCheckActualBlockingStatesToCancel(addOnEntitlement, effectiveCancellationDateTime, approximateDateCheck, blockingStatesForCancellationViaAccount);
    }

    private void doCheckActualBlockingStatesToCancel(final DefaultEntitlement addOnEntitlement, final DateTime effectiveCancellationDateTime, final boolean approximateDateCheck, final Collection<BlockingState> blockingStatesForCancellation) {
        if (effectiveCancellationDateTime == null) {
            Assert.assertEquals(blockingStatesForCancellation.size(), 0);
        } else {
            Assert.assertEquals(blockingStatesForCancellation.size(), 1);
            final BlockingState blockingState = blockingStatesForCancellation.iterator().next();
            Assert.assertEquals(blockingState.getBlockedId(), addOnEntitlement.getId());
            if (approximateDateCheck) {
                Assert.assertEquals(blockingState.getEffectiveDate().toLocalDate(), effectiveCancellationDateTime.toLocalDate());
                Assert.assertEquals(blockingState.getEffectiveDate().getMinuteOfDay(), effectiveCancellationDateTime.getMinuteOfDay());
            } else {
                Assert.assertEquals(blockingState.getEffectiveDate(), effectiveCancellationDateTime);
            }
            Assert.assertEquals(blockingState.getType(), BlockingStateType.SUBSCRIPTION);
            Assert.assertEquals(blockingState.getService(), EntitlementService.ENTITLEMENT_SERVICE_NAME);
            Assert.assertEquals(blockingState.getStateName(), DefaultEntitlementApi.ENT_STATE_CANCELLED);
        }
    }

    // Test the DAO
    private void checkBlockingStatesDAO(final DefaultEntitlement baseEntitlement, final DefaultEntitlement addOnEntitlement, final LocalDate effectiveCancellationDate, final boolean isBaseCancelled) {
        checkBlockingStatesDAO(baseEntitlement, addOnEntitlement, effectiveCancellationDate, effectiveCancellationDate, isBaseCancelled);
    }

    // Test the DAO
    private void checkBlockingStatesDAO(final DefaultEntitlement baseEntitlement, final DefaultEntitlement addOnEntitlement, final LocalDate effectiveBaseCancellationDate, final LocalDate effectiveAddOnCancellationDate, final boolean isBaseCancelled) {
        final List<BlockingState> blockingStatesForBaseEntitlement = blockingStatesForBlockedId(baseEntitlement.getId());
        Assert.assertEquals(blockingStatesForBaseEntitlement.size(), isBaseCancelled ? 2 : 1);
        if (isBaseCancelled) {
            Assert.assertEquals(blockingStatesForBaseEntitlement.get(1).getBlockedId(), baseEntitlement.getId());
            Assert.assertEquals(blockingStatesForBaseEntitlement.get(1).getEffectiveDate().toLocalDate(), effectiveBaseCancellationDate);
            Assert.assertEquals(blockingStatesForBaseEntitlement.get(1).getType(), BlockingStateType.SUBSCRIPTION);
            Assert.assertEquals(blockingStatesForBaseEntitlement.get(1).getService(), EntitlementService.ENTITLEMENT_SERVICE_NAME);
            Assert.assertEquals(blockingStatesForBaseEntitlement.get(1).getStateName(), DefaultEntitlementApi.ENT_STATE_CANCELLED);
        }

        final List<BlockingState> blockingStatesForAddOn = blockingStatesForBlockedId(addOnEntitlement.getId());
        Assert.assertEquals(blockingStatesForAddOn.size(), 2);
        Assert.assertEquals(blockingStatesForAddOn.get(1).getBlockedId(), addOnEntitlement.getId());
        Assert.assertEquals(blockingStatesForAddOn.get(1).getEffectiveDate().toLocalDate(), effectiveAddOnCancellationDate);
        Assert.assertEquals(blockingStatesForAddOn.get(1).getType(), BlockingStateType.SUBSCRIPTION);
        Assert.assertEquals(blockingStatesForAddOn.get(1).getService(), EntitlementService.ENTITLEMENT_SERVICE_NAME);
        Assert.assertEquals(blockingStatesForAddOn.get(1).getStateName(), DefaultEntitlementApi.ENT_STATE_CANCELLED);
    }

    private Collection<BlockingState> computeFutureBlockingStatesForAssociatedAddonsViaEntitlement(final DefaultEntitlement baseEntitlement) throws EntitlementApiException {
        final EventsStream eventsStream = eventsStreamBuilder.buildForEntitlement(baseEntitlement.getId(), callContext);
        return eventsStream.computeAddonsBlockingStatesForFutureSubscriptionBaseEvents();
    }

    private Collection<BlockingState> computeFutureBlockingStatesForAssociatedAddonsViaAccount(final DefaultEntitlement baseEntitlement) throws EntitlementApiException {
        final AccountEventsStreams accountEventsStreams = eventsStreamBuilder.buildForAccount(internalCallContext);

        final EventsStream eventsStream = Iterables.<EventsStream>find(Iterables.<EventsStream>concat(accountEventsStreams.getEventsStreams().values()),
                                                                       new Predicate<EventsStream>() {
                                                                           @Override
                                                                           public boolean apply(final EventsStream input) {
                                                                               return input.getSubscriptionBase().getId().equals(baseEntitlement.getId());
                                                                           }
                                                                       });
        return eventsStream.computeAddonsBlockingStatesForFutureSubscriptionBaseEvents();
    }

    private Collection<BlockingState> computeBlockingStatesForAssociatedAddonsViaEntitlement(final DefaultEntitlement baseEntitlement, final DateTime effectiveDate) throws EntitlementApiException {
        final EventsStream eventsStream = eventsStreamBuilder.buildForEntitlement(baseEntitlement.getId(), callContext);
        return eventsStream.computeAddonsBlockingStatesForNextSubscriptionBaseEvent(effectiveDate);
    }

    private Collection<BlockingState> computeBlockingStatesForAssociatedAddonsViaAccount(final DefaultEntitlement baseEntitlement, final DateTime effectiveDate) throws EntitlementApiException {
        final AccountEventsStreams accountEventsStreams = eventsStreamBuilder.buildForAccount(internalCallContext);

        final EventsStream eventsStream = Iterables.<EventsStream>find(Iterables.<EventsStream>concat(accountEventsStreams.getEventsStreams().values()),
                                                                       new Predicate<EventsStream>() {
                                                                           @Override
                                                                           public boolean apply(final EventsStream input) {
                                                                               return input.getSubscriptionBase().getId().equals(baseEntitlement.getId());
                                                                           }
                                                                       });
        return eventsStream.computeAddonsBlockingStatesForNextSubscriptionBaseEvent(effectiveDate);
    }

    private List<BlockingState> blockingStatesForBlockedId(final UUID blockedId) {
        return ImmutableList.<BlockingState>copyOf(Iterables.<BlockingState>filter(blockingStateDao.getBlockingAllForAccountRecordId(catalog, internalCallContext),
                                                                                   new Predicate<BlockingState>() {
                                                                                       @Override
                                                                                       public boolean apply(final BlockingState input) {
                                                                                           return input.getBlockedId().equals(blockedId);
                                                                                       }
                                                                                   }));
    }
}
