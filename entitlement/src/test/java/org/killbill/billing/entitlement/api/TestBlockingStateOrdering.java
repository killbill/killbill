/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.entitlement.DefaultEntitlementService;
import org.killbill.billing.entitlement.EntitlementTestSuiteNoDB;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.platform.api.KillbillService.KILLBILL_SERVICES;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransition;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

// invocationCount > 1 to verify flakiness
public class TestBlockingStateOrdering extends EntitlementTestSuiteNoDB {

    private long globalOrdering = 0;

    @Test(groups = "fast", invocationCount = 10)
    public void testIgnore_ENTITLEMENT_SERVICE_NAME_WithNoFlag() throws Exception {
        final DateTime now = clock.getUTCNow();
        final UUID subscriptionId1 = UUID.randomUUID();

        final Collection<BlockingState> blockingStates = new LinkedList<BlockingState>();
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_START, KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName(), false, false, now));
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, "stuff", KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName(), false, false, now.plusDays(1)));

        final LinkedList<SubscriptionEvent> allEvents = new LinkedList<SubscriptionEvent>();
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.START_BILLING, now));
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.PHASE, now.plusDays(30)));

        computeEvents(allEvents, blockingStates);

        Assert.assertEquals(allEvents.size(), 3);
        Assert.assertEquals(allEvents.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        Assert.assertEquals(allEvents.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);
    }

    @Test(groups = "fast", invocationCount = 10)
    public void test_ENT_STATE_IsNotInterpreted() throws Exception {
        final DateTime now = clock.getUTCNow();
        final UUID subscriptionId1 = UUID.randomUUID();

        final Collection<BlockingState> blockingStates = new LinkedList<BlockingState>();
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_START, KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName(), false, false, now));
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_START, "svc1", false, false, now.plusDays(1)));
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_CANCELLED, "svc1", false, false, now.plusDays(2)));

        final LinkedList<SubscriptionEvent> allEvents = new LinkedList<SubscriptionEvent>();
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.START_BILLING, now));
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.PHASE, now.plusDays(30)));

        computeEvents(allEvents, blockingStates);

        Assert.assertEquals(allEvents.size(), 5);
        Assert.assertEquals(allEvents.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        Assert.assertEquals(allEvents.get(2).getSubscriptionEventType(), SubscriptionEventType.SERVICE_STATE_CHANGE);
        Assert.assertEquals(allEvents.get(3).getSubscriptionEventType(), SubscriptionEventType.SERVICE_STATE_CHANGE);
        Assert.assertEquals(allEvents.get(4).getSubscriptionEventType(), SubscriptionEventType.PHASE);
    }

    @Test(groups = "fast", invocationCount = 10)
    public void testPauseAtStart() throws Exception {
        final DateTime now = clock.getUTCNow();
        final UUID subscriptionId1 = UUID.randomUUID();

        final Collection<BlockingState> blockingStates = new LinkedList<BlockingState>();
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_START, KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName(), false, false, now));
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, "stuff", "svc1", true, true, now));

        final LinkedList<SubscriptionEvent> allEvents = new LinkedList<SubscriptionEvent>();
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.START_BILLING, now));
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.PHASE, now.plusDays(30)));

        computeEvents(allEvents, blockingStates);

        Assert.assertEquals(allEvents.size(), 5);
        Assert.assertEquals(allEvents.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        Assert.assertEquals(allEvents.get(2).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(3).getSubscriptionEventType(), SubscriptionEventType.PAUSE_BILLING);
        Assert.assertEquals(allEvents.get(4).getSubscriptionEventType(), SubscriptionEventType.PHASE);
    }

    @Test(groups = "fast", invocationCount = 10)
    public void testPausePostPhase_0_17_X() throws Exception {
        final DateTime now = clock.getUTCNow();
        final UUID subscriptionId1 = UUID.randomUUID();

        final Collection<BlockingState> blockingStates = new LinkedList<BlockingState>();
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, "stuff", "svc1", true, true, now.plusDays(40)));

        final LinkedList<SubscriptionEvent> allEvents = new LinkedList<SubscriptionEvent>();
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.START_BILLING, now));
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.PHASE, now.plusDays(30)));

        computeEvents(allEvents, blockingStates);

        Assert.assertEquals(allEvents.size(), 5);
        Assert.assertEquals(allEvents.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        Assert.assertEquals(allEvents.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);
        Assert.assertEquals(allEvents.get(3).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(4).getSubscriptionEventType(), SubscriptionEventType.PAUSE_BILLING);
    }

    @Test(groups = "fast", invocationCount = 10)
    public void testPausePostPhase() throws Exception {
        final DateTime now = clock.getUTCNow();
        final UUID subscriptionId1 = UUID.randomUUID();

        final Collection<BlockingState> blockingStates = new LinkedList<BlockingState>();
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_START, KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName(), false, false, now));
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, "stuff", "svc1", true, true, now.plusDays(40)));

        final LinkedList<SubscriptionEvent> allEvents = new LinkedList<SubscriptionEvent>();
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.START_BILLING, now));
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.PHASE, now.plusDays(30)));

        computeEvents(allEvents, blockingStates);

        Assert.assertEquals(allEvents.size(), 5);
        Assert.assertEquals(allEvents.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        Assert.assertEquals(allEvents.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);
        Assert.assertEquals(allEvents.get(3).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(4).getSubscriptionEventType(), SubscriptionEventType.PAUSE_BILLING);
    }

    @Test(groups = "fast", invocationCount = 10)
    public void testPauseAtPhase() throws Exception {
        final DateTime now = clock.getUTCNow();
        final UUID subscriptionId1 = UUID.randomUUID();

        final Collection<BlockingState> blockingStates = new LinkedList<BlockingState>();
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_START, KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName(), false, false, now));
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, "stuff", "svc1", true, true, now.plusDays(30)));

        final LinkedList<SubscriptionEvent> allEvents = new LinkedList<SubscriptionEvent>();
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.START_BILLING, now));
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.PHASE, now.plusDays(30)));

        computeEvents(allEvents, blockingStates);

        Assert.assertEquals(allEvents.size(), 5);
        Assert.assertEquals(allEvents.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        Assert.assertEquals(allEvents.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);
        Assert.assertEquals(allEvents.get(3).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(4).getSubscriptionEventType(), SubscriptionEventType.PAUSE_BILLING);
    }

    @Test(groups = "fast", invocationCount = 10)
    public void testPauseResumeAtPhase() throws Exception {
        final DateTime now = clock.getUTCNow();
        final UUID subscriptionId1 = UUID.randomUUID();

        final Collection<BlockingState> blockingStates = new LinkedList<BlockingState>();
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_START, KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName(), false, false, now));
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, "stuff", "svc1", true, true, now.plusDays(30)));
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, "stuff", "svc1", false, false, now.plusDays(30)));

        final LinkedList<SubscriptionEvent> allEvents = new LinkedList<SubscriptionEvent>();
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.START_BILLING, now));
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.PHASE, now.plusDays(30)));

        computeEvents(allEvents, blockingStates);

        Assert.assertEquals(allEvents.size(), 7);
        Assert.assertEquals(allEvents.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        Assert.assertEquals(allEvents.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);
        Assert.assertEquals(allEvents.get(3).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(4).getSubscriptionEventType(), SubscriptionEventType.PAUSE_BILLING);
        Assert.assertEquals(allEvents.get(5).getSubscriptionEventType(), SubscriptionEventType.RESUME_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(6).getSubscriptionEventType(), SubscriptionEventType.RESUME_BILLING);
    }

    @Test(groups = "fast", invocationCount = 10)
    public void testPauseAccountAtPhase() throws Exception {
        final DateTime now = clock.getUTCNow();
        final UUID subscriptionId1 = UUID.randomUUID();

        final Collection<BlockingState> blockingStates = new LinkedList<BlockingState>();
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_START, KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName(), false, false, now));
        blockingStates.add(createBlockingState(UUID.randomUUID(), BlockingStateType.ACCOUNT, "stuff", "svc1", true, true, now.plusDays(30)));

        final LinkedList<SubscriptionEvent> allEvents = new LinkedList<SubscriptionEvent>();
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.START_BILLING, now));
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.PHASE, now.plusDays(30)));

        computeEvents(allEvents, blockingStates);

        Assert.assertEquals(allEvents.size(), 5);
        Assert.assertEquals(allEvents.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        Assert.assertEquals(allEvents.get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);
        Assert.assertEquals(allEvents.get(3).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(4).getSubscriptionEventType(), SubscriptionEventType.PAUSE_BILLING);
    }

    @Test(groups = "fast", invocationCount = 10)
    public void testDifferentTypesOfBlockingSameService() throws Exception {
        final DateTime now = clock.getUTCNow();
        final UUID subscriptionId1 = UUID.randomUUID();

        final Collection<BlockingState> blockingStates = new LinkedList<BlockingState>();
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_START, KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName(), false, false, now));
        blockingStates.add(createBlockingState(UUID.randomUUID(), BlockingStateType.ACCOUNT, "stuff", "svc1", false, true, now.plusDays(10)));
        // Same service
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, "stuff", "svc1", true, false, now.plusDays(15)));
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, "stuff", "svc1", false, false, now.plusDays(20)));
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.ACCOUNT, "stuff", "svc1", false, false, now.plusDays(30)));

        final LinkedList<SubscriptionEvent> allEvents = new LinkedList<SubscriptionEvent>();
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.START_BILLING, now));
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.PHASE, now.plusDays(30)));

        computeEvents(allEvents, blockingStates);

        Assert.assertEquals(allEvents.size(), 8);
        Assert.assertEquals(allEvents.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        Assert.assertEquals(allEvents.get(2).getSubscriptionEventType(), SubscriptionEventType.PAUSE_BILLING);
        Assert.assertEquals(allEvents.get(3).getSubscriptionEventType(), SubscriptionEventType.RESUME_BILLING);
        Assert.assertEquals(allEvents.get(4).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(5).getSubscriptionEventType(), SubscriptionEventType.RESUME_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(6).getSubscriptionEventType(), SubscriptionEventType.PHASE);
        Assert.assertEquals(allEvents.get(7).getSubscriptionEventType(), SubscriptionEventType.SERVICE_STATE_CHANGE);
    }

    @Test(groups = "fast", invocationCount = 10)
    public void testDifferentTypesOfBlockingDifferentServices() throws Exception {
        final DateTime now = clock.getUTCNow();
        final UUID subscriptionId1 = UUID.randomUUID();

        final Collection<BlockingState> blockingStates = new LinkedList<BlockingState>();
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_START, KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName(), false, false, now));
        blockingStates.add(createBlockingState(UUID.randomUUID(), BlockingStateType.ACCOUNT, "stuff", "svc1", false, true, now.plusDays(10)));
        // Different service
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, "stuff", "svc2", true, false, now.plusDays(15)));
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, "stuff", "svc2", false, false, now.plusDays(20)));
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.ACCOUNT, "stuff", "svc1", false, false, now.plusDays(30)));

        final LinkedList<SubscriptionEvent> allEvents = new LinkedList<SubscriptionEvent>();
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.START_BILLING, now));
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.PHASE, now.plusDays(30)));

        computeEvents(allEvents, blockingStates);

        Assert.assertEquals(allEvents.size(), 7);
        Assert.assertEquals(allEvents.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        Assert.assertEquals(allEvents.get(2).getSubscriptionEventType(), SubscriptionEventType.PAUSE_BILLING);
        Assert.assertEquals(allEvents.get(3).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(4).getSubscriptionEventType(), SubscriptionEventType.RESUME_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(5).getSubscriptionEventType(), SubscriptionEventType.PHASE);
        Assert.assertEquals(allEvents.get(6).getSubscriptionEventType(), SubscriptionEventType.RESUME_BILLING);
    }

    @Test(groups = "fast", invocationCount = 10)
    public void testPauseAccountAtPhaseAndPauseOtherSubscriptionFutureStartedV1() throws Exception {
        final UUID subscriptionId1 = UUID.randomUUID();
        UUID subscriptionId2 = UUID.randomUUID();
        while (subscriptionId2.compareTo(subscriptionId1) <= 0) {
            subscriptionId2 = UUID.randomUUID();
        }
        testPauseAccountAtPhaseAndPauseOtherSubscriptionFutureStarted(subscriptionId1, subscriptionId2);
    }

    @Test(groups = "fast", invocationCount = 10)
    public void testPauseAccountAtPhaseAndPauseOtherSubscriptionFutureStartedV2() throws Exception {
        final UUID subscriptionId1 = UUID.randomUUID();
        UUID subscriptionId2 = UUID.randomUUID();
        while (subscriptionId2.compareTo(subscriptionId1) >= 0) {
            subscriptionId2 = UUID.randomUUID();
        }
        testPauseAccountAtPhaseAndPauseOtherSubscriptionFutureStarted(subscriptionId1, subscriptionId2);
    }

    private void testPauseAccountAtPhaseAndPauseOtherSubscriptionFutureStarted(final UUID subscriptionId1, final UUID subscriptionId2) throws Exception {
        final DateTime now = clock.getUTCNow();

        final Collection<BlockingState> blockingStates = new LinkedList<BlockingState>();
        blockingStates.add(createBlockingState(subscriptionId1, BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_START, KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName(), false, false, now));
        blockingStates.add(createBlockingState(subscriptionId2, BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_START, KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName(), false, false, now));
        blockingStates.add(createBlockingState(UUID.randomUUID(), BlockingStateType.ACCOUNT, "stuff", "svc1", true, true, now.plusDays(30)));

        final LinkedList<SubscriptionEvent> allEvents = new LinkedList<SubscriptionEvent>();
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.START_BILLING, now));
        allEvents.add(createEvent(subscriptionId1, SubscriptionEventType.PHASE, now.plusDays(30)));
        allEvents.add(createEvent(subscriptionId2, SubscriptionEventType.START_BILLING, now.plusDays(40)));

        computeEvents(allEvents, blockingStates);

        Assert.assertEquals(allEvents.size(), 8);
        Assert.assertEquals(allEvents.get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(1).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        Assert.assertEquals(allEvents.get(2).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        Assert.assertEquals(allEvents.get(3).getSubscriptionEventType(), SubscriptionEventType.PHASE);
        if (subscriptionId1.compareTo(subscriptionId2) >= 0) {
            Assert.assertEquals(allEvents.get(4).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
            Assert.assertEquals(allEvents.get(5).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
            Assert.assertEquals(allEvents.get(6).getSubscriptionEventType(), SubscriptionEventType.PAUSE_BILLING);
        } else {
            Assert.assertEquals(allEvents.get(4).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
            Assert.assertEquals(allEvents.get(5).getSubscriptionEventType(), SubscriptionEventType.PAUSE_BILLING);
            Assert.assertEquals(allEvents.get(6).getSubscriptionEventType(), SubscriptionEventType.PAUSE_ENTITLEMENT);
        }
        Assert.assertEquals(allEvents.get(7).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
    }

    private BlockingState createBlockingState(final UUID blockedId,
                                              final BlockingStateType blockingStateType,
                                              final String stateName,
                                              final String service,
                                              final boolean blockEntitlement,
                                              final boolean blockBilling,
                                              final DateTime effectiveDate) {
        return new DefaultBlockingState(UUID.randomUUID(),
                                        blockedId,
                                        blockingStateType,
                                        stateName,
                                        service,
                                        false,
                                        blockEntitlement,
                                        blockBilling,
                                        effectiveDate,
                                        effectiveDate,
                                        effectiveDate,
                                        globalOrdering++);
    }

    // Re-use SubscriptionEventOrdering method, as it's the input of BlockingStateOrdering
    private SubscriptionEvent createEvent(final UUID subscriptionId, final SubscriptionEventType type, final DateTime effectiveDate) {
        final SubscriptionBaseTransition subscriptionBaseTransition = Mockito.mock(SubscriptionBaseTransition.class);
        Mockito.when(subscriptionBaseTransition.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(subscriptionBaseTransition.getSubscriptionId()).thenReturn(subscriptionId);
        Mockito.when(subscriptionBaseTransition.getEffectiveTransitionTime()).thenReturn(effectiveDate);
        return SubscriptionEventOrdering.toSubscriptionEvent(subscriptionBaseTransition, type, internalCallContext);
    }

    private void computeEvents(final LinkedList<SubscriptionEvent> allEvents, final Collection<BlockingState> blockingStates) {
        final Collection<UUID> allEntitlementUUIDs = new HashSet<UUID>();
        for (final SubscriptionEvent subscriptionEvent : allEvents) {
            allEntitlementUUIDs.add(subscriptionEvent.getEntitlementId());
        }
        for (final BlockingState blockingState : blockingStates) {
            if (blockingState.getType() == BlockingStateType.SUBSCRIPTION) {
                allEntitlementUUIDs.add(blockingState.getBlockedId());
            }
        }

        BlockingStateOrdering.INSTANCE.computeEvents(new LinkedList<UUID>(allEntitlementUUIDs), blockingStates, internalCallContext, allEvents);
    }
}
