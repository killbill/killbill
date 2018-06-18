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

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.OrderingType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.entitlement.EntitlementService;
import org.killbill.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLog;
import org.killbill.billing.util.audit.ChangeType;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestDefaultSubscriptionApi extends EntitlementTestSuiteWithEmbeddedDB {

    @Test(groups = "slow", description = "Verify blocking states are exposed in SubscriptionBundle")
    public void testBlockingStatesInTimelineApi() throws Exception {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlement1Id = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), UUID.randomUUID().toString(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement entitlement1 = entitlementApi.getEntitlementForId(entitlement1Id, callContext);
        // Sleep 1 sec so created date are apart from each other and ordering in the bundle does not default on the UUID which is random.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignore) {
        }
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlement2Id = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), UUID.randomUUID().toString(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement2Id, callContext);

        testListener.pushExpectedEvents(NextEvent.BLOCK);
        entitlementUtils.setBlockingStateAndPostBlockingTransitionEvent(new DefaultBlockingState(account.getId(), BlockingStateType.ACCOUNT, "stateName", "service", false, false, false, clock.getUTCNow()),
                                                                        internalCallContextFactory.createInternalCallContext(account.getId(), callContext));
        assertListenerStatus();

        final List<SubscriptionBundle> bundles = subscriptionApi.getSubscriptionBundlesForAccountId(account.getId(), callContext);
        Assert.assertEquals(bundles.size(), 2);

        // This will test the ordering as well
        subscriptionBundleChecker(bundles, initialDate, entitlement1, 0);
        subscriptionBundleChecker(bundles, initialDate, entitlement2, 1);
    }

    private void subscriptionBundleChecker(final List<SubscriptionBundle> bundles, final LocalDate initialDate, final Entitlement entitlement, final int idx) {
        Assert.assertEquals(bundles.get(idx).getId(), entitlement.getBundleId());
        Assert.assertEquals(bundles.get(idx).getSubscriptions().size(), 1);
        Assert.assertEquals(bundles.get(idx).getSubscriptions().get(0).getId(), entitlement.getId());
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().size(), 4);
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(0).getEffectiveDate(), initialDate);
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(1).getEffectiveDate(), initialDate);
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(2).getEffectiveDate(), initialDate);
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(2).getSubscriptionEventType(), SubscriptionEventType.SERVICE_STATE_CHANGE);
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(2).getServiceName(), "service");
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(2).getServiceStateName(), "stateName");
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(3).getEffectiveDate(), new LocalDate(2013, 9, 6));
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(3).getSubscriptionEventType(), SubscriptionEventType.PHASE);
    }

    @Test(groups = "slow")
    public void testWithMultipleBundle() throws AccountApiException, SubscriptionApiException, EntitlementApiException {
        final String externalKey = "fooXXX";

        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), externalKey, null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        assertEquals(entitlement.getAccountId(), account.getId());
        assertEquals(entitlement.getExternalKey(), externalKey);

        assertEquals(entitlement.getEffectiveStartDate(), initialDate);
        assertNull(entitlement.getEffectiveEndDate());

        final List<SubscriptionBundle> bundles = subscriptionApi.getSubscriptionBundlesForExternalKey(externalKey, callContext);
        assertEquals(bundles.size(), 1);

        final SubscriptionBundle activeBundle = subscriptionApi.getActiveSubscriptionBundleForExternalKey(externalKey, callContext);
        assertEquals(activeBundle.getId(), entitlement.getBundleId());

        // Cancel entitlement
        clock.addDays(3);
        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK);
        entitlement.cancelEntitlementWithDate(new LocalDate(clock.getUTCNow(), account.getTimeZone()), true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        try {
            subscriptionApi.getActiveSubscriptionBundleForExternalKey(externalKey, callContext);
            Assert.fail("Expected getActiveSubscriptionBundleForExternalKey to fail after cancellation");
        } catch (SubscriptionApiException e) {
            assertEquals(e.getCode(), ErrorCode.SUB_GET_INVALID_BUNDLE_KEY.getCode());

        }

        clock.addDays(1);
        // Re-create a new bundle with same externalKey
        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier("Pistol", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlement2Id = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec2), externalKey, null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement2Id, callContext);
        assertEquals(entitlement2.getAccountId(), account.getId());
        assertEquals(entitlement2.getExternalKey(), externalKey);

        final List<SubscriptionBundle> bundles2 = subscriptionApi.getSubscriptionBundlesForExternalKey(externalKey, callContext);
        assertEquals(bundles2.size(), 2);

        SubscriptionBundle firstbundle = bundles2.get(0);
        assertEquals(firstbundle.getSubscriptions().size(), 1);
        assertEquals(firstbundle.getSubscriptions().get(0).getEffectiveStartDate(), new LocalDate(2013, 8, 7));
        assertEquals(firstbundle.getSubscriptions().get(0).getBillingStartDate(), new LocalDate(2013, 8, 7));
        assertEquals(firstbundle.getSubscriptions().get(0).getEffectiveEndDate(), new LocalDate(2013, 8, 10));
        assertEquals(firstbundle.getSubscriptions().get(0).getBillingEndDate(), new LocalDate(2013, 8, 10));

        SubscriptionBundle secondbundle = bundles2.get(1);
        assertEquals(secondbundle.getSubscriptions().size(), 1);
        assertEquals(secondbundle.getSubscriptions().get(0).getEffectiveStartDate(), new LocalDate(2013, 8, 11));
        assertEquals(secondbundle.getSubscriptions().get(0).getBillingStartDate(), new LocalDate(2013, 8, 11));
        assertNull(secondbundle.getSubscriptions().get(0).getEffectiveEndDate());
        assertNull(secondbundle.getSubscriptions().get(0).getBillingEndDate());
        assertEquals(secondbundle.getOriginalCreatedDate().compareTo(firstbundle.getCreatedDate()), 0);

        final List<SubscriptionBundle> bundles2Again = subscriptionApi.getSubscriptionBundlesForAccountIdAndExternalKey(account.getId(), externalKey, callContext);
        assertEquals(bundles2Again.size(), 2);

        clock.addDays(3);

        final Account account2 = createAccount(getAccountData(7));

        testListener.pushExpectedEvents(NextEvent.TRANSFER, NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.BLOCK);
        entitlementApi.transferEntitlements(account.getId(), account2.getId(), externalKey, new LocalDate(clock.getUTCNow(), account.getTimeZone()), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        final List<SubscriptionBundle> bundles3 = subscriptionApi.getSubscriptionBundlesForExternalKey(externalKey, callContext);
        assertEquals(bundles3.size(), 3);

        firstbundle = bundles3.get(0);
        assertEquals(firstbundle.getSubscriptions().size(), 1);
        assertEquals(firstbundle.getSubscriptions().get(0).getEffectiveStartDate(), new LocalDate(2013, 8, 7));
        assertEquals(firstbundle.getSubscriptions().get(0).getBillingStartDate(), new LocalDate(2013, 8, 7));
        assertEquals(firstbundle.getSubscriptions().get(0).getEffectiveEndDate(), new LocalDate(2013, 8, 10));
        assertEquals(firstbundle.getSubscriptions().get(0).getBillingEndDate(), new LocalDate(2013, 8, 10));

        secondbundle = bundles3.get(1);
        assertEquals(secondbundle.getSubscriptions().size(), 1);
        assertEquals(secondbundle.getSubscriptions().get(0).getEffectiveStartDate(), new LocalDate(2013, 8, 11));
        assertEquals(secondbundle.getSubscriptions().get(0).getBillingStartDate(), new LocalDate(2013, 8, 11));
        assertEquals(secondbundle.getSubscriptions().get(0).getEffectiveEndDate(), new LocalDate(2013, 8, 14));
        assertEquals(secondbundle.getSubscriptions().get(0).getBillingEndDate(), new LocalDate(2013, 8, 14));
        assertEquals(secondbundle.getOriginalCreatedDate().compareTo(firstbundle.getCreatedDate()), 0);

        SubscriptionBundle thirdBundle = bundles3.get(2);
        assertEquals(thirdBundle.getSubscriptions().size(), 1);
        assertEquals(thirdBundle.getSubscriptions().get(0).getEffectiveStartDate(), new LocalDate(2013, 8, 14));
        assertEquals(thirdBundle.getSubscriptions().get(0).getBillingStartDate(), new LocalDate(2013, 8, 14));
        assertNull(thirdBundle.getSubscriptions().get(0).getEffectiveEndDate());
        assertNull(thirdBundle.getSubscriptions().get(0).getBillingEndDate());
        assertEquals(thirdBundle.getOriginalCreatedDate().compareTo(firstbundle.getCreatedDate()), 0);
    }

    @Test(groups = "slow", description = "Test for https://github.com/killbill/killbill/issues/136")
    public void testAuditLogsForEntitlementAndSubscriptionBaseObjects() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
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

        testListener.pushExpectedEvents(NextEvent.BLOCK);
        clock.setDay(pauseDate);
        assertListenerStatus();

        final LocalDate resumeDate = new LocalDate(2013, 12, 24);
        entitlementApi.resume(baseEntitlement.getBundleId(), resumeDate, ImmutableList.<PluginProperty>of(), callContext);

        testListener.pushExpectedEvents(NextEvent.BLOCK);
        clock.setDay(resumeDate);
        assertListenerStatus();

        final LocalDate cancelDate = new LocalDate(2013, 12, 27);
        baseEntitlement.cancelEntitlementWithDate(cancelDate, true, ImmutableList.<PluginProperty>of(), callContext);

        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK);
        clock.setDay(cancelDate);
        assertListenerStatus();

        final SubscriptionBundle bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);
        final List<SubscriptionEvent> transitions = bundle.getTimeline().getSubscriptionEvents();
        assertEquals(transitions.size(), 9);
        checkSubscriptionEventAuditLog(transitions, 0, SubscriptionEventType.START_ENTITLEMENT);
        checkSubscriptionEventAuditLog(transitions, 1, SubscriptionEventType.START_BILLING);
        checkSubscriptionEventAuditLog(transitions, 2, SubscriptionEventType.PHASE);
        checkSubscriptionEventAuditLog(transitions, 3, SubscriptionEventType.PAUSE_ENTITLEMENT);
        checkSubscriptionEventAuditLog(transitions, 4, SubscriptionEventType.PAUSE_BILLING);
        checkSubscriptionEventAuditLog(transitions, 5, SubscriptionEventType.RESUME_ENTITLEMENT);
        checkSubscriptionEventAuditLog(transitions, 6, SubscriptionEventType.RESUME_BILLING);
        checkSubscriptionEventAuditLog(transitions, 7, SubscriptionEventType.STOP_ENTITLEMENT);
        checkSubscriptionEventAuditLog(transitions, 8, SubscriptionEventType.STOP_BILLING);
    }

    @Test(groups = "slow")
    public void testSubscriptionCreationWithFutureDate() throws AccountApiException, SubscriptionApiException, EntitlementApiException {
        final String externalKey = "vritti";

        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        //2013-08-07
        final LocalDate effectiveDate = initialDate.plusMonths(1);

        // Create entitlement and check each field
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), externalKey, effectiveDate, effectiveDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlement.getId(), callContext);

        final List<SubscriptionEvent> events = subscription.getSubscriptionEvents();
        assertEquals(events.size(), 3);

        assertEquals(events.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(0).getEffectiveDate().compareTo(effectiveDate), 0);

        assertEquals(events.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(1).getEffectiveDate().compareTo(effectiveDate), 0);

        assertEquals(events.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);
        assertEquals(events.get(2).getEffectiveDate().compareTo(effectiveDate.plusMonths(1)), 0);

        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testCancelFutureSubscription() throws AccountApiException, EntitlementApiException, SubscriptionApiException {

        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        final LocalDate futureDate = new LocalDate(2013, 9, 1);

        // No CREATE event as this is set in the future
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), futureDate, futureDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement createdEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, callContext);
        assertEquals(createdEntitlement.getEffectiveStartDate().compareTo(futureDate), 0);
        assertEquals(createdEntitlement.getEffectiveEndDate(), null);

        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(createdEntitlement.getId(), callContext);
        assertEquals(baseEntitlement.getEffectiveStartDate().compareTo(futureDate), 0);
        assertEquals(baseEntitlement.getEffectiveEndDate(), null);

        final Entitlement cancelledEntitlement = baseEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.IMMEDIATE, null, callContext);
        assertEquals(cancelledEntitlement.getEffectiveEndDate().compareTo(futureDate), 0);

        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(cancelledEntitlement.getId(), callContext);
        assertEquals(subscription.getEffectiveEndDate().compareTo(futureDate), 0);

        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testAddBlockingState() throws AccountApiException, EntitlementApiException, SubscriptionApiException {

        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = accountApi.createAccount(getAccountData(7), callContext);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), initialDate, initialDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement createdEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, callContext);

        final Iterable<BlockingState> iterableForCreateState = subscriptionApi.getBlockingStates(account.getId(), ImmutableList.of(BlockingStateType.SUBSCRIPTION), null, OrderingType.ASCENDING, SubscriptionApi.ALL_EVENTS, callContext);
        assertTrue(iterableForCreateState.iterator().hasNext());
        final BlockingState createState = iterableForCreateState.iterator().next();
        assertEquals(createState.getService(), EntitlementService.ENTITLEMENT_SERVICE_NAME);
        assertListenerStatus();

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState state1 = new DefaultBlockingState(account.getId(), BlockingStateType.ACCOUNT, "accountBlock", "svc1", false, true, false, clock.getUTCNow());
        subscriptionApi.addBlockingState(state1, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        Entitlement updateEntitlement = entitlementApi.getEntitlementForId(createdEntitlement.getId(), callContext);
        Assert.assertEquals(updateEntitlement.getState(), EntitlementState.BLOCKED);

        clock.addDays(1);

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState state2 = new DefaultBlockingState(createdEntitlement.getId(), BlockingStateType.SUBSCRIPTION, "subscriptionBlock", "svc2", false, false, false, clock.getUTCNow());
        subscriptionApi.addBlockingState(state2, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Still blocked because this is a different service
        updateEntitlement = entitlementApi.getEntitlementForId(createdEntitlement.getId(), callContext);
        Assert.assertEquals(updateEntitlement.getState(), EntitlementState.BLOCKED);

        // Now we remove the blocking state for the same service but at the SUBSCRIPTION level
        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState state3 = new DefaultBlockingState(createdEntitlement.getId(), BlockingStateType.SUBSCRIPTION, "subscriptionUnBlock", "svc1", false, false, false, clock.getUTCNow());
        subscriptionApi.addBlockingState(state3, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        updateEntitlement = entitlementApi.getEntitlementForId(createdEntitlement.getId(), callContext);
        Assert.assertEquals(updateEntitlement.getState(), EntitlementState.BLOCKED);

        final DateTime futureEffectiveDate = clock.getUTCNow().plusDays(1);
        final BlockingState state4 = new DefaultBlockingState(createdEntitlement.getBundleId(), BlockingStateType.SUBSCRIPTION_BUNDLE, "blockBilling", "svc1", true, false, false, futureEffectiveDate);
        subscriptionApi.addBlockingState(state4, internalCallContext.toLocalDate(futureEffectiveDate), ImmutableList.<PluginProperty>of(), callContext);

        final Iterable<BlockingState> blockingStates1 = subscriptionApi.getBlockingStates(account.getId(), ImmutableList.of(BlockingStateType.ACCOUNT, BlockingStateType.SUBSCRIPTION), ImmutableList.of("svc1", "svc2"), OrderingType.ASCENDING, SubscriptionApi.PAST_OR_PRESENT_EVENTS, callContext);
        verifyBlockingStates(blockingStates1, ImmutableList.<BlockingState>of(state1, state2, state3));

        final Iterable<BlockingState> blockingStates2 = subscriptionApi.getBlockingStates(account.getId(), ImmutableList.of(BlockingStateType.SUBSCRIPTION), ImmutableList.of("svc1", "svc2"), OrderingType.DESCENDING, SubscriptionApi.PAST_OR_PRESENT_EVENTS, callContext);
        verifyBlockingStates(blockingStates2, ImmutableList.<BlockingState>of(state3, state2));

        final Iterable<BlockingState> blockingStates3 = subscriptionApi.getBlockingStates(account.getId(), ImmutableList.of(BlockingStateType.SUBSCRIPTION), ImmutableList.of("svc2"), OrderingType.DESCENDING, SubscriptionApi.PAST_OR_PRESENT_EVENTS, callContext);
        verifyBlockingStates(blockingStates3, ImmutableList.<BlockingState>of(state2));

        final Iterable<BlockingState> blockingStates4 = subscriptionApi.getBlockingStates(account.getId(), null, null, OrderingType.DESCENDING, SubscriptionApi.ALL_EVENTS, callContext);
        verifyBlockingStates(blockingStates4, ImmutableList.<BlockingState>of(state4, state3, state2, state1, createState));

        final Iterable<BlockingState> blockingStates5 = subscriptionApi.getBlockingStates(account.getId(), ImmutableList.of(BlockingStateType.SUBSCRIPTION_BUNDLE), null, OrderingType.ASCENDING, SubscriptionApi.FUTURE_EVENTS, callContext);
        verifyBlockingStates(blockingStates5, ImmutableList.<BlockingState>of(state4));

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        clock.addDays(1);
        assertListenerStatus();

        final Iterable<BlockingState> blockingStates6 = subscriptionApi.getBlockingStates(account.getId(), null, null, OrderingType.ASCENDING, SubscriptionApi.PAST_OR_PRESENT_EVENTS, callContext);
        verifyBlockingStates(blockingStates6, ImmutableList.<BlockingState>of(createState, state1, state2, state3, state4));

    }

    @Test(groups = "slow")
    public void testBlockBundle() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, callContext);

        clock.addDays(5);

        testListener.pushExpectedEvents(NextEvent.BLOCK);
        final BlockingState state1 = new DefaultBlockingState(baseEntitlement.getBundleId(), BlockingStateType.SUBSCRIPTION_BUNDLE, "BLOCK", "foo", true, true, true, null);
        subscriptionApi.addBlockingState(state1, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        List<Entitlement> bundleEntitlements = entitlementApi.getAllEntitlementsForBundle(baseEntitlement.getBundleId(), callContext);
        assertEquals(bundleEntitlements.size(), 1);
        assertEquals(bundleEntitlements.get(0).getState(), EntitlementState.BLOCKED);

        final BlockingState blockingState = blockingInternalApi.getBlockingStateForService(baseEntitlement.getBundleId(), BlockingStateType.SUBSCRIPTION_BUNDLE, "foo", internalCallContext);
        assertTrue(blockingState.isBlockBilling());
        assertTrue(blockingState.isBlockChange());
        assertTrue(blockingState.isBlockEntitlement());

        // Check unblocking on another service will not bring the state back to ACTIVE
        clock.addDays(1);
        testListener.pushExpectedEvents(NextEvent.BLOCK);
        final BlockingState state2 = new DefaultBlockingState(baseEntitlement.getBundleId(), BlockingStateType.SUBSCRIPTION_BUNDLE, "UNBLOCK", "bar", false, false, false, null);
        subscriptionApi.addBlockingState(state2, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        bundleEntitlements = entitlementApi.getAllEntitlementsForBundle(baseEntitlement.getBundleId(), callContext);
        assertEquals(bundleEntitlements.size(), 1);
        assertEquals(bundleEntitlements.get(0).getState(), EntitlementState.BLOCKED);

        testListener.pushExpectedEvents(NextEvent.BLOCK);
        final BlockingState state3 = new DefaultBlockingState(baseEntitlement.getBundleId(), BlockingStateType.SUBSCRIPTION_BUNDLE, "UNBLOCK", "foo", false, false, false, null);
        subscriptionApi.addBlockingState(state3, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        bundleEntitlements = entitlementApi.getAllEntitlementsForBundle(baseEntitlement.getBundleId(), callContext);
        assertEquals(bundleEntitlements.size(), 1);
        assertEquals(bundleEntitlements.get(0).getState(), EntitlementState.ACTIVE);

        blockingInternalApi.getBlockingStateForService(baseEntitlement.getBundleId(), BlockingStateType.SUBSCRIPTION_BUNDLE, "foo", internalCallContext);
        clock.addDays(1);

        testListener.pushExpectedEvents(NextEvent.BLOCK);
        final BlockingState state4 = new DefaultBlockingState(baseEntitlement.getBundleId(), BlockingStateType.SUBSCRIPTION_BUNDLE, "BLOCK", "foo", true, true, true, null);
        subscriptionApi.addBlockingState(state4, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        bundleEntitlements = entitlementApi.getAllEntitlementsForBundle(baseEntitlement.getBundleId(), callContext);
        assertEquals(bundleEntitlements.size(), 1);
        assertEquals(bundleEntitlements.get(0).getState(), EntitlementState.BLOCKED);

        // Same day but happened after so should take precedence
        testListener.pushExpectedEvents(NextEvent.BLOCK);
        final BlockingState state5 = new DefaultBlockingState(baseEntitlement.getBundleId(), BlockingStateType.SUBSCRIPTION_BUNDLE, "UNBLOCK", "foo", false, false, false, null);
        subscriptionApi.addBlockingState(state5, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        bundleEntitlements = entitlementApi.getAllEntitlementsForBundle(baseEntitlement.getBundleId(), callContext);
        assertEquals(bundleEntitlements.size(), 1);
        assertEquals(bundleEntitlements.get(0).getState(), EntitlementState.ACTIVE);
    }


    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/452")
    public void testBlockedEntitlementChange() throws AccountApiException, EntitlementApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        clock.addDays(1);
        clock.addDeltaFromReality(5000);
        assertListenerStatus();

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState state = new DefaultBlockingState(entitlement.getBundleId(), BlockingStateType.SUBSCRIPTION_BUNDLE, "MY_BLOCK", "test", true, false, false, null);
        subscriptionApi.addBlockingState(state, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        try {
            final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier("Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
            entitlement.changePlan(new DefaultEntitlementSpecifier(spec1), ImmutableList.<PluginProperty>of(), callContext);
            fail();
        } catch (final EntitlementApiException e) {
            assertEquals(e.getCode(), ErrorCode.BLOCK_BLOCKED_ACTION.getCode());
            final Entitlement latestEntitlement = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
            assertEquals(latestEntitlement.getLastActivePlan().getProduct().getName(), "Shotgun");
        }

        // If a LocalDate is passed, it will align with the reference time (2013-08-08T00:00:00.000Z), which will fall before the blocking state above (+5s added above)
        testListener.pushExpectedEvent(NextEvent.CHANGE);
        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier("Assault-Rifle", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
        entitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec2), clock.getUTCToday(), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement latestEntitlement = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        assertEquals(latestEntitlement.getLastActivePlan().getProduct().getName(), "Assault-Rifle");
    }

    @Test(groups = "slow")
    public void testCancellationEntitlementDifferentThanBilling() throws AccountApiException, EntitlementApiException, SubscriptionApiException {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        // Create entitlement
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, callContext);

        // 2013-08-10 : Stay in TRIAL to ensure IMMEDIATE billing policy is used
        clock.addDays(3);
        assertListenerStatus();


        final LocalDate cancelDate = new LocalDate(2013, 8, 14);
        testListener.pushExpectedEvents(NextEvent.CANCEL);
        baseEntitlement.cancelEntitlementWithDate(cancelDate, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        final Subscription result1 = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlement.getId(), callContext);
        assertEquals(result1.getBillingEndDate().compareTo(new LocalDate(2013, 8, 10)), 0);
        assertEquals(result1.getEffectiveEndDate().compareTo(new LocalDate(2013, 8, 14)), 0);
        assertEquals(result1.getLastActiveProduct().getName(), "Shotgun");
        assertEquals(result1.getState(), EntitlementState.ACTIVE);

        // 2013-08-14: entitlement cancelDate
        testListener.pushExpectedEvents(NextEvent.BLOCK);
        clock.addDays(4);
        assertListenerStatus();

        final Subscription result2 = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlement.getId(), callContext);
        assertEquals(result2.getBillingEndDate().compareTo(new LocalDate(2013, 8, 10)), 0);
        assertEquals(result2.getEffectiveEndDate().compareTo(new LocalDate(2013, 8, 14)), 0);
        assertEquals(result2.getLastActiveProduct().getName(), "Shotgun");
        assertEquals(result2.getState(), EntitlementState.CANCELLED);
    }





    @Test(groups = "slow")
    public void testSubscriptionCreationWithExternalKeyOverLimit() throws AccountApiException, SubscriptionApiException, EntitlementApiException {
        final String externalKey = "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean massa. Cum sociis natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Donec quam felis, ultricies nec, pellentesque eu, pretium quis,.";

        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        //2013-08-07
        final LocalDate effectiveDate = initialDate.plusMonths(1);

        try {
            entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), externalKey, effectiveDate, effectiveDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
            Assert.fail();
        } catch (final EntitlementApiException e) {
            assertEquals(e.getCode(), ErrorCode.EXTERNAL_KEY_LIMIT_EXCEEDED.getCode());
        }
    }

    private void verifyBlockingStates(final Iterable<BlockingState> result, final List<BlockingState> expected) {
        int i = 0;
        final Iterator<BlockingState> iterator = result.iterator();
        while (iterator.hasNext()) {
            final BlockingState cur = iterator.next();
            final BlockingState expectedItem = expected.get(i);
            assertEquals(cur.isBlockBilling(), expectedItem.isBlockBilling());
            assertEquals(cur.isBlockEntitlement(), expectedItem.isBlockEntitlement());
            assertEquals(cur.isBlockChange(), expectedItem.isBlockChange());
            assertEquals(cur.getService(), expectedItem.getService());
            assertEquals(cur.getStateName(), expectedItem.getStateName());
            assertEquals(cur.getBlockedId(), expectedItem.getBlockedId());
            assertEquals(internalCallContext.toLocalDate(cur.getEffectiveDate()).compareTo(internalCallContext.toLocalDate(expectedItem.getEffectiveDate())), 0);
            i++;
        }
        assertEquals(i, expected.size());
    }

    private void checkSubscriptionEventAuditLog(final List<SubscriptionEvent> transitions, final int idx, final SubscriptionEventType expectedType) {
        assertEquals(transitions.get(idx).getSubscriptionEventType(), expectedType);
        final List<AuditLog> auditLogs = auditUserApi.getAuditLogs(transitions.get(idx).getId(), transitions.get(idx).getSubscriptionEventType().getObjectType(), AuditLevel.FULL, callContext);
        assertEquals(auditLogs.size(), 1);
        assertEquals(auditLogs.get(0).getChangeType(), ChangeType.INSERT);
    }
}
