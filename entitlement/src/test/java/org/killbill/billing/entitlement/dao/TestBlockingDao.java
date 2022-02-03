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

package org.killbill.billing.entitlement.dao;

import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.audit.ChangeType;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class TestBlockingDao extends EntitlementTestSuiteWithEmbeddedDB {

    @Test(groups = "slow", description = "Check BlockingStateDao with a single service")
    public void testDaoWithOneService() throws AccountApiException {
        final UUID accountId = createAccount(getAccountData(1)).getId();
        final String overdueStateName = "WayPassedItMan";
        final String service = "TEST";

        final boolean blockChange = true;
        final boolean blockEntitlement = false;
        final boolean blockBilling = false;

        clock.setDay(new LocalDate(2012, 4, 1));

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState state1 = new DefaultBlockingState(accountId, BlockingStateType.ACCOUNT, overdueStateName, service, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(state1, Optional.<UUID>absent()), internalCallContext);
        assertListenerStatus();

        clock.addDays(1);

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final String overdueStateName2 = "NoReallyThisCantGoOn";
        final BlockingState state2 = new DefaultBlockingState(accountId, BlockingStateType.ACCOUNT, overdueStateName2, service, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(state2, Optional.<UUID>absent()), internalCallContext);
        assertListenerStatus();

        Assert.assertEquals(blockingStateDao.getBlockingStateForService(accountId, BlockingStateType.ACCOUNT, service, internalCallContext).getStateName(), state2.getStateName());

        final List<BlockingState> states = blockingStateDao.getBlockingAllForAccountRecordId(catalog, internalCallContext);
        Assert.assertEquals(states.size(), 2);

        Assert.assertEquals(states.get(0).getStateName(), overdueStateName);
        Assert.assertEquals(states.get(1).getStateName(), overdueStateName2);

        final List<BlockingState> states2 = blockingStateDao.getByBlockingIds(ImmutableList.of(accountId), internalCallContext);
        Assert.assertEquals(states2.size(), 2);

    }


    @Test(groups = "slow", description = "Check BlockingStateDao for a subscription with events at all level (subscription, bundle, account)")
    public void testWithMultipleAccountBlockingStates() throws AccountApiException {
        final UUID accountId = createAccount(getAccountData(1)).getId();
        final String overdueStateName = "WayPassedItMan";
        final String service = "TEST";

        final boolean blockChange = true;
        final boolean blockEntitlement = false;
        final boolean blockBilling = false;

        clock.setDay(new LocalDate(2012, 4, 1));

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState state1 = new DefaultBlockingState(accountId, BlockingStateType.ACCOUNT, overdueStateName, service, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(state1, Optional.<UUID>absent()), internalCallContext);
        assertListenerStatus();

        clock.addDays(1);

        final UUID bundleId = UUID.randomUUID();
        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final String overdueStateName2 = "NoReallyThisCantGoOn";
        final BlockingState state2 = new DefaultBlockingState(bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE, overdueStateName2, service, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(state2, Optional.<UUID>absent()), internalCallContext);
        assertListenerStatus();

        clock.addDays(1);

        final UUID subscriptionId = UUID.randomUUID();
        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final String overdueStateName3 = "OhBoy!";
        final BlockingState state3 = new DefaultBlockingState(subscriptionId, BlockingStateType.SUBSCRIPTION, overdueStateName3, service, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(state3, Optional.<UUID>absent()), internalCallContext);
        assertListenerStatus();

        clock.addDays(1);
        // Add a blocking state for a different subscription as well but for the same account
        final UUID subscriptionId2 = UUID.randomUUID();
        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState state4 = new DefaultBlockingState(subscriptionId2, BlockingStateType.SUBSCRIPTION, overdueStateName3, service, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(state4, Optional.<UUID>absent()), internalCallContext);
        assertListenerStatus();


        Assert.assertEquals(blockingStateDao.getBlockingStateForService(accountId, BlockingStateType.ACCOUNT, service, internalCallContext).getStateName(), state1.getStateName());

        final List<BlockingState> states = blockingStateDao.getBlockingAllForAccountRecordId(catalog, internalCallContext);
        Assert.assertEquals(states.size(), 4);

        Assert.assertEquals(states.get(0).getStateName(), overdueStateName);
        Assert.assertEquals(states.get(1).getStateName(), overdueStateName2);
        Assert.assertEquals(states.get(2).getStateName(), overdueStateName3);

        final List<BlockingState> states2 = blockingStateDao.getByBlockingIds(ImmutableList.of(accountId, bundleId, subscriptionId), internalCallContext);
        Assert.assertEquals(states2.size(), 3);

    }



    @Test(groups = "slow", description = "Verify active blocking states are being returned")
    public void testActiveBlockingStates() throws AccountApiException {

        final UUID accountId = createAccount(getAccountData(1)).getId();
        final String service = "Coco";

        clock.setDay(new LocalDate(2022, 1, 18));

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState stateA1 = new DefaultBlockingState(accountId, BlockingStateType.ACCOUNT, "warning", service, false, false, false, clock.getUTCNow());
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(stateA1, Optional.<UUID>absent()), internalCallContext);
        assertListenerStatus();

        clock.addDays(1);

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState stateA2 = new DefaultBlockingState(accountId, BlockingStateType.ACCOUNT, "warning+", service, false, false, false, clock.getUTCNow());
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(stateA2, Optional.<UUID>absent()), internalCallContext);
        assertListenerStatus();

        final UUID bundleId = UUID.randomUUID();
        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState stateB1 = new DefaultBlockingState(bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE, "block", service, true, true, true, clock.getUTCNow());
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(stateB1, Optional.<UUID>absent()), internalCallContext);
        assertListenerStatus();

        clock.addDays(1);

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState stateA3 = new DefaultBlockingState(accountId, BlockingStateType.ACCOUNT, "warning++", service, false, false, false, clock.getUTCNow());
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(stateA3, Optional.<UUID>absent()), internalCallContext);
        assertListenerStatus();

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState stateB2 = new DefaultBlockingState(bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE, "unblock", service, false, false, false, clock.getUTCNow());
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(stateB2, Optional.<UUID>absent()), internalCallContext);
        assertListenerStatus();


        List<BlockingState> states = blockingStateDao.getBlockingAllForAccountRecordId(catalog, internalCallContext);
        Assert.assertEquals(states.size(), 5);


        states = blockingStateDao.getBlockingActiveForAccount(catalog, null, internalCallContext);
        Assert.assertEquals(states.size(), 2);

    }


    @Test(groups = "slow", description = "Check BlockingStateDao with multiple services")
    public void testDaoWithMultipleServices() throws Exception {
        final UUID uuid = createAccount(getAccountData(1)).getId();
        final String overdueStateName = "WayPassedItMan";
        final String service1 = "TEST";

        final boolean blockChange = true;
        final boolean blockEntitlement = false;
        final boolean blockBilling = false;

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState state1 = new DefaultBlockingState(uuid, BlockingStateType.ACCOUNT, overdueStateName, service1, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(state1, Optional.<UUID>absent()), internalCallContext);
        assertListenerStatus();

        clock.setDeltaFromReality(1000 * 3600 * 24);

        final String service2 = "TEST2";

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final String overdueStateName2 = "NoReallyThisCantGoOn";
        final BlockingState state2 = new DefaultBlockingState(uuid, BlockingStateType.ACCOUNT, overdueStateName2, service2, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(state2, Optional.<UUID>absent()), internalCallContext);
        assertListenerStatus();

        final List<BlockingState> history2 = blockingStateDao.getBlockingAllForAccountRecordId(catalog, internalCallContext);
        Assert.assertEquals(history2.size(), 2);
        Assert.assertEquals(history2.get(0).getStateName(), overdueStateName);
        Assert.assertEquals(history2.get(1).getStateName(), overdueStateName2);
    }

    @Test(groups = "slow")
    public void testWithAuditAndHistory() throws Exception {

        final UUID uuid = createAccount(getAccountData(1)).getId();
        final String overdueStateName = "StateBlock";
        final String service = "auditAndHistory";

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState blockingState = new DefaultBlockingState(uuid, BlockingStateType.ACCOUNT, overdueStateName, service, false, true, false, clock.getUTCNow());
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(blockingState, Optional.<UUID>absent()), internalCallContext);
        assertListenerStatus();


        final List<AuditLogWithHistory> h1 = blockingStateDao.getBlockingStateAuditLogsWithHistoryForId(blockingState.getId(), AuditLevel.FULL, internalCallContext);
        Assert.assertEquals(h1.size(), 1);

        final AuditLogWithHistory firstHistoryRow = h1.get(0);
        Assert.assertEquals(firstHistoryRow.getChangeType(), ChangeType.INSERT);
        final BlockingStateModelDao firstBlockingState = (BlockingStateModelDao) firstHistoryRow.getEntity();
        Assert.assertFalse(firstBlockingState.getBlockChange());
        Assert.assertTrue(firstBlockingState.getBlockEntitlement());
        Assert.assertFalse(firstBlockingState.getBlockBilling());
        Assert.assertTrue(firstBlockingState.isActive());

        blockingStateDao.unactiveBlockingState(blockingState.getId(), internalCallContext);
        final List<AuditLogWithHistory> h2 = blockingStateDao.getBlockingStateAuditLogsWithHistoryForId(blockingState.getId(), AuditLevel.FULL, internalCallContext);
        Assert.assertEquals(h2.size(), 2);
        final AuditLogWithHistory secondHistoryRow = h2.get(1);

        Assert.assertEquals(secondHistoryRow.getChangeType(), ChangeType.DELETE);
        final BlockingStateModelDao secondBlockingState = (BlockingStateModelDao) secondHistoryRow.getEntity();
        Assert.assertFalse(secondBlockingState.getBlockChange());
        Assert.assertTrue(secondBlockingState.getBlockEntitlement());
        Assert.assertFalse(secondBlockingState.getBlockBilling());
        Assert.assertFalse(secondBlockingState.isActive());
    }
}
