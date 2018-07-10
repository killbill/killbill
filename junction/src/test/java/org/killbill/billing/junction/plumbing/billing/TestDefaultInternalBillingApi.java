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

package org.killbill.billing.junction.plumbing.billing;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.joda.time.ReadablePeriod;
import org.joda.time.Seconds;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.entitlement.EntitlementService;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlementApi;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.junction.JunctionTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestDefaultInternalBillingApi extends JunctionTestSuiteWithEmbeddedDB {

    // This test was originally for https://github.com/killbill/killbill/issues/123.
    // The invocationCount > 0 was to trigger an issue where events would come out-of-order randomly.
    // While the bug shouldn't occur anymore, we're keeping it just in case (the test will also try to insert the events out-of-order manually).
    // This test also checks we don't generate billing events for blocking durations less than a day (https://github.com/killbill/killbill/issues/267).
    @Test(groups = "slow", description = "Check blocking states with same effective date are correctly handled", invocationCount = 10)
    public void testBlockingStatesWithSameEffectiveDate() throws Exception {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        final SubscriptionBase subscription = subscriptionInternalApi.getSubscriptionFromId(entitlement.getId(), internalCallContext);
        assertListenerStatus();

        final DateTime block1Date = clock.getUTCNow();
        testListener.pushExpectedEvents(NextEvent.BLOCK, NextEvent.BLOCK);
        final DefaultBlockingState state1 = new DefaultBlockingState(account.getId(),
                                                                     BlockingStateType.ACCOUNT,
                                                                     DefaultEntitlementApi.ENT_STATE_BLOCKED,
                                                                     EntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                     true,
                                                                     true,
                                                                     true,
                                                                     block1Date);
        blockingInternalApi.setBlockingState(state1, internalCallContext);
        // Same date, we'll order by record id asc
        final DefaultBlockingState state2 = new DefaultBlockingState(account.getId(),
                                                                     BlockingStateType.ACCOUNT,
                                                                     DefaultEntitlementApi.ENT_STATE_CLEAR,
                                                                     EntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                     false,
                                                                     false,
                                                                     false,
                                                                     block1Date);
        blockingInternalApi.setBlockingState(state2, internalCallContext);
        assertListenerStatus();

        clock.addDays(5);

        final DateTime block2Date = clock.getUTCNow();
        testListener.pushExpectedEvents(NextEvent.BLOCK, NextEvent.BLOCK);
        final DefaultBlockingState state3 = new DefaultBlockingState(entitlement.getBundleId(),
                                                                     BlockingStateType.SUBSCRIPTION_BUNDLE,
                                                                     DefaultEntitlementApi.ENT_STATE_BLOCKED,
                                                                     EntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                     true,
                                                                     true,
                                                                     true,
                                                                     block2Date);
        blockingInternalApi.setBlockingState(state3, internalCallContext);
        // Same date, we'll order by record id asc
        final DefaultBlockingState state4 = new DefaultBlockingState(entitlement.getBundleId(),
                                                                     BlockingStateType.SUBSCRIPTION_BUNDLE,
                                                                     DefaultEntitlementApi.ENT_STATE_CLEAR,
                                                                     EntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                     false,
                                                                     false,
                                                                     false,
                                                                     block2Date);
        blockingInternalApi.setBlockingState(state4, internalCallContext);
        assertListenerStatus();

        final DateTime block3Date = block2Date.plusDays(3);

        // Pass the phase
        testListener.pushExpectedEvent(NextEvent.PHASE);
        clock.addDays(50);
        assertListenerStatus();

        final DateTime block4Date = clock.getUTCNow();
        final DateTime block5Date = block4Date.plusDays(3);
        // Only one event on the bus (for state5)
        testListener.pushExpectedEvents(NextEvent.BLOCK);
        // Insert the clear state first, to make sure the order in which we insert blocking states doesn't matter
        // Since we are already in an ENT_STATE_CLEAR state for service ENTITLEMENT_SERVICE_NAME, we need to use a different
        // state name to simulate this behavior (otherwise, by design, this event won't be created)
        final DefaultBlockingState state6 = new DefaultBlockingState(entitlement.getBundleId(),
                                                                     BlockingStateType.SUBSCRIPTION_BUNDLE,
                                                                     DefaultEntitlementApi.ENT_STATE_CLEAR + "-something",
                                                                     EntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                     false,
                                                                     false,
                                                                     false,
                                                                     block5Date);
        blockingInternalApi.setBlockingState(state6, internalCallContext);
        final DefaultBlockingState state5 = new DefaultBlockingState(entitlement.getBundleId(),
                                                                     BlockingStateType.SUBSCRIPTION_BUNDLE,
                                                                     DefaultEntitlementApi.ENT_STATE_BLOCKED + "-something",
                                                                     EntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                     true,
                                                                     true,
                                                                     true,
                                                                     block4Date);
        blockingInternalApi.setBlockingState(state5, internalCallContext);
        assertListenerStatus();

        // Now, add back blocking states at an earlier date, for a different blockable id, to make sure the effective
        // date ordering is correctly respected when computing blocking durations
        testListener.pushExpectedEvents(NextEvent.BLOCK, NextEvent.BLOCK);
        final DefaultBlockingState state7 = new DefaultBlockingState(account.getId(),
                                                                     BlockingStateType.ACCOUNT,
                                                                     DefaultEntitlementApi.ENT_STATE_BLOCKED + "-something2",
                                                                     EntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                     true,
                                                                     true,
                                                                     true,
                                                                     block3Date);
        blockingInternalApi.setBlockingState(state7, internalCallContext);
        final DefaultBlockingState state8 = new DefaultBlockingState(account.getId(),
                                                                     BlockingStateType.ACCOUNT,
                                                                     DefaultEntitlementApi.ENT_STATE_CLEAR + "-something2",
                                                                     EntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                     false,
                                                                     false,
                                                                     false,
                                                                     block4Date);
        blockingInternalApi.setBlockingState(state8, internalCallContext);
        assertListenerStatus();

        // Advance for state6 to be active
        testListener.pushExpectedEvents(NextEvent.BLOCK);
        clock.addDays(5);
        assertListenerStatus();

        // Expected blocking duration:
        // * 2013-08-15 to 2013-10-04 [2013-08-15 to 2013-10-01 (block3Date -> block4Date) and 2013-10-01 to 2013-10-04 (block4Date -> block5Date)]
        final List<BillingEvent> events = ImmutableList.<BillingEvent>copyOf(billingInternalApi.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), null, internalCallContext));
        Assert.assertEquals(events.size(), 3);
        Assert.assertEquals(events.get(0).getTransitionType(), SubscriptionBaseTransitionType.CREATE);
        Assert.assertEquals(events.get(0).getEffectiveDate(), subscription.getStartDate());
        Assert.assertEquals(events.get(1).getTransitionType(), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
        Assert.assertEquals(events.get(1).getEffectiveDate(), block3Date);
        Assert.assertEquals(events.get(2).getTransitionType(), SubscriptionBaseTransitionType.END_BILLING_DISABLED);
        Assert.assertEquals(events.get(2).getEffectiveDate(), block5Date);
    }

    // See https://github.com/killbill/killbill/commit/92042843e38a67f75495b207385e4c1f9ca60990#commitcomment-4749967
    @Test(groups = "slow", description = "Check unblock then block states with same effective date are correctly handled", invocationCount = 10)
    public void testUnblockThenBlockBlockingStatesWithSameEffectiveDate() throws Exception {
        testUnblockThenBlockBlockingStatesWithSimilarEffectiveDate(Seconds.ZERO);
    }

    @Test(groups = "slow", description = "Check unblock then block states with almost the same effective date are correctly handled", invocationCount = 10)
    public void testUnblockThenBlockBlockingStatesWithAlmostSameEffectiveDate() throws Exception {
        testUnblockThenBlockBlockingStatesWithSimilarEffectiveDate(Seconds.ONE);
    }

    private void testUnblockThenBlockBlockingStatesWithSimilarEffectiveDate(final ReadablePeriod delay) throws Exception {
        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = createAccount(getAccountData(7));

        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        final SubscriptionBase subscription = subscriptionInternalApi.getSubscriptionFromId(entitlement.getId(), internalCallContext);
        assertListenerStatus();

        final DateTime block1Date = subscription.getStartDate().plus(delay);
        // Make sure to update the clock here, because we don't disable for periods less than a day
        clock.setTime(block1Date);

        testListener.pushExpectedEvents(NextEvent.BLOCK);
        final DefaultBlockingState state1 = new DefaultBlockingState(account.getId(),
                                                                     BlockingStateType.ACCOUNT,
                                                                     DefaultEntitlementApi.ENT_STATE_BLOCKED,
                                                                     EntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                     true,
                                                                     true,
                                                                     true,
                                                                     block1Date);
        blockingInternalApi.setBlockingState(state1, internalCallContext);
        assertListenerStatus();

        clock.addDays(1);

        final DateTime block2Date = clock.getUTCNow();
        testListener.pushExpectedEvents(NextEvent.BLOCK, NextEvent.BLOCK);
        final DefaultBlockingState state2 = new DefaultBlockingState(account.getId(),
                                                                     BlockingStateType.ACCOUNT,
                                                                     DefaultEntitlementApi.ENT_STATE_CLEAR,
                                                                     EntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                     false,
                                                                     false,
                                                                     false,
                                                                     block2Date);
        blockingInternalApi.setBlockingState(state2, internalCallContext);
        // Same date
        final DefaultBlockingState state3 = new DefaultBlockingState(account.getId(),
                                                                     BlockingStateType.ACCOUNT,
                                                                     DefaultEntitlementApi.ENT_STATE_BLOCKED,
                                                                     EntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                     true,
                                                                     true,
                                                                     true,
                                                                     block2Date);
        blockingInternalApi.setBlockingState(state3, internalCallContext);
        assertListenerStatus();

        // Nothing should happen
        clock.addDays(3);
        assertListenerStatus();

        final List<BillingEvent> events = ImmutableList.<BillingEvent>copyOf(billingInternalApi.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), null, internalCallContext));
        if (delay.toPeriod().toStandardDuration().compareTo(Period.ZERO.toStandardDuration()) == 0) {
            Assert.assertEquals(events.size(), 0);
        } else {
            // Expected blocking duration:
            // * 2013-08-07 to now [2013-08-07 to 2013-08-08 then 2013-08-08 to now]
            Assert.assertEquals(events.size(), 2);
            Assert.assertEquals(events.get(0).getTransitionType(), SubscriptionBaseTransitionType.CREATE);
            Assert.assertEquals(events.get(0).getEffectiveDate(), subscription.getStartDate());
            Assert.assertEquals(events.get(1).getTransitionType(), SubscriptionBaseTransitionType.START_BILLING_DISABLED);
            Assert.assertEquals(events.get(1).getEffectiveDate(), block1Date);
        }
    }
}
