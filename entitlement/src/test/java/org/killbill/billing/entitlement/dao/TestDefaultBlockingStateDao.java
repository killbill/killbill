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

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.payment.api.PluginProperty;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class TestDefaultBlockingStateDao extends EntitlementTestSuiteWithEmbeddedDB {

    private Account account;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        if (hasFailed()) {
            return;
        }
        account = createAccount(getAccountData(7));
    }

    @Test(groups = "slow", description = "Verify we don't insert extra add-on events")
    public void testUnnecessaryEventsAreNotAdded() throws Exception {
        // This is a simple smoke test at the dao level only to make sure we do sane
        // things in case there are no future add-on cancellation events to add in the stream.
        // See TestEntitlementUtils for a more comprehensive test
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        final BlockingStateType type = BlockingStateType.SUBSCRIPTION;
        final String state = "state";
        final String service = "service";

        // Verify initial state
        Assert.assertEquals(blockingStateDao.getBlockingAllForAccountRecordId(catalog, internalCallContext).size(), 1);

        // Set a state in the future so no event
        final DateTime stateDateTime = new DateTime(2013, 5, 6, 10, 11, 12, DateTimeZone.UTC);
        final BlockingState blockingState = new DefaultBlockingState(entitlement.getId(), type, state, service, false, false, false, stateDateTime);
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(blockingState, Optional.<UUID>of(entitlement.getBundleId())), internalCallContext);
        assertListenerStatus();

        Assert.assertEquals(blockingStateDao.getBlockingAllForAccountRecordId(catalog, internalCallContext).size(), 2);
    }

    // See https://github.com/killbill/killbill/issues/111
    @Test(groups = "slow", description = "Verify we don't insert duplicate blocking states")
    public void testSetBlockingState() throws Exception {
        final UUID blockableId = UUID.randomUUID();
        final BlockingStateType type = BlockingStateType.ACCOUNT;
        final String state = "state";
        final String state2 = "state-2";
        final String serviceA = "service-A";
        final String serviceB = "service-B";

        // Verify initial state
        Assert.assertEquals(blockingStateDao.getBlockingAllForAccountRecordId(catalog, internalCallContext).size(), 0);

        // Note: the checkers below rely on record_id ordering, not effective date

        // Set a state for service A
        final DateTime stateDateTime = new DateTime(2013, 5, 6, 10, 11, 12, DateTimeZone.UTC);
        final BlockingState blockingState1 = new DefaultBlockingState(blockableId, type, state, serviceA, false, false, false, stateDateTime);
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(blockingState1, Optional.<UUID>absent()), internalCallContext);
        final List<BlockingState> blockingStates1 = blockingStateDao.getBlockingAllForAccountRecordId(catalog, internalCallContext);
        Assert.assertEquals(blockingStates1.size(), 1);
        Assert.assertEquals(blockingStates1.get(0).getBlockedId(), blockableId);
        Assert.assertEquals(blockingStates1.get(0).getStateName(), state);
        Assert.assertEquals(blockingStates1.get(0).getService(), serviceA);
        Assert.assertEquals(blockingStates1.get(0).getEffectiveDate(), stateDateTime);

        // Set the same state again - no change
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(blockingState1, Optional.<UUID>absent()), internalCallContext);
        final List<BlockingState> blockingStates2 = blockingStateDao.getBlockingAllForAccountRecordId(catalog, internalCallContext);
        Assert.assertEquals(blockingStates2.size(), 1);
        Assert.assertEquals(blockingStates2.get(0).getBlockedId(), blockableId);
        Assert.assertEquals(blockingStates2.get(0).getStateName(), state);
        Assert.assertEquals(blockingStates2.get(0).getService(), serviceA);
        Assert.assertEquals(blockingStates2.get(0).getEffectiveDate(), stateDateTime);

        // Set the state for service B
        final BlockingState blockingState2 = new DefaultBlockingState(blockableId, type, state, serviceB, false, false, false, stateDateTime);
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(blockingState2, Optional.<UUID>absent()), internalCallContext);
        final List<BlockingState> blockingStates3 = blockingStateDao.getBlockingAllForAccountRecordId(catalog, internalCallContext);
        Assert.assertEquals(blockingStates3.size(), 2);
        Assert.assertEquals(blockingStates3.get(0).getBlockedId(), blockableId);
        Assert.assertEquals(blockingStates3.get(0).getStateName(), state);
        Assert.assertEquals(blockingStates3.get(0).getService(), serviceA);
        Assert.assertEquals(blockingStates3.get(0).getEffectiveDate(), stateDateTime);
        Assert.assertEquals(blockingStates3.get(1).getBlockedId(), blockableId);
        Assert.assertEquals(blockingStates3.get(1).getStateName(), state);
        Assert.assertEquals(blockingStates3.get(1).getService(), serviceB);
        Assert.assertEquals(blockingStates3.get(1).getEffectiveDate(), stateDateTime);

        // Set the state for service A in the future - there should be no change (already effective)
        final DateTime stateDateTime2 = new DateTime(2013, 6, 6, 10, 11, 12, DateTimeZone.UTC);
        final BlockingState blockingState3 = new DefaultBlockingState(blockableId, type, state, serviceA, false, false, false, stateDateTime2);
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(blockingState3, Optional.<UUID>absent()), internalCallContext);
        final List<BlockingState> blockingStates4 = blockingStateDao.getBlockingAllForAccountRecordId(catalog, internalCallContext);
        Assert.assertEquals(blockingStates4.size(), 2);
        Assert.assertEquals(blockingStates4.get(0).getBlockedId(), blockableId);
        Assert.assertEquals(blockingStates4.get(0).getStateName(), state);
        Assert.assertEquals(blockingStates4.get(0).getService(), serviceA);
        Assert.assertEquals(blockingStates4.get(0).getEffectiveDate(), stateDateTime);
        Assert.assertEquals(blockingStates4.get(1).getBlockedId(), blockableId);
        Assert.assertEquals(blockingStates4.get(1).getStateName(), state);
        Assert.assertEquals(blockingStates4.get(1).getService(), serviceB);
        Assert.assertEquals(blockingStates4.get(1).getEffectiveDate(), stateDateTime);

        // Set the state for service A in the past - the new effective date should be respected
        final DateTime stateDateTime3 = new DateTime(2013, 2, 6, 10, 11, 12, DateTimeZone.UTC);
        final BlockingState blockingState4 = new DefaultBlockingState(blockableId, type, state, serviceA, false, false, false, stateDateTime3);
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(blockingState4, Optional.<UUID>absent()), internalCallContext);
        final List<BlockingState> blockingStates5 = blockingStateDao.getBlockingAllForAccountRecordId(catalog, internalCallContext);
        Assert.assertEquals(blockingStates5.size(), 2);
        Assert.assertEquals(blockingStates5.get(0).getBlockedId(), blockableId);
        Assert.assertEquals(blockingStates5.get(0).getStateName(), state);
        Assert.assertEquals(blockingStates5.get(0).getService(), serviceA);
        Assert.assertEquals(blockingStates5.get(0).getEffectiveDate(), stateDateTime3);
        Assert.assertEquals(blockingStates5.get(1).getBlockedId(), blockableId);
        Assert.assertEquals(blockingStates5.get(1).getStateName(), state);
        Assert.assertEquals(blockingStates5.get(1).getService(), serviceB);
        Assert.assertEquals(blockingStates5.get(1).getEffectiveDate(), stateDateTime);

        // Set a new state for service A
        final DateTime state2DateTime = new DateTime(2013, 12, 6, 10, 11, 12, DateTimeZone.UTC);
        final BlockingState blockingState5 = new DefaultBlockingState(blockableId, type, state2, serviceA, false, false, false, state2DateTime);
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(blockingState5, Optional.<UUID>absent()), internalCallContext);
        final List<BlockingState> blockingStates6 = blockingStateDao.getBlockingAllForAccountRecordId(catalog, internalCallContext);
        Assert.assertEquals(blockingStates6.size(), 3);
        Assert.assertEquals(blockingStates6.get(0).getBlockedId(), blockableId);
        Assert.assertEquals(blockingStates6.get(0).getStateName(), state);
        Assert.assertEquals(blockingStates6.get(0).getService(), serviceA);
        Assert.assertEquals(blockingStates6.get(0).getEffectiveDate(), stateDateTime3);
        Assert.assertEquals(blockingStates6.get(1).getBlockedId(), blockableId);
        Assert.assertEquals(blockingStates6.get(1).getStateName(), state);
        Assert.assertEquals(blockingStates6.get(1).getService(), serviceB);
        Assert.assertEquals(blockingStates6.get(1).getEffectiveDate(), stateDateTime);
        Assert.assertEquals(blockingStates6.get(2).getBlockedId(), blockableId);
        Assert.assertEquals(blockingStates6.get(2).getStateName(), state2);
        Assert.assertEquals(blockingStates6.get(2).getService(), serviceA);
        Assert.assertEquals(blockingStates6.get(2).getEffectiveDate(), state2DateTime);
    }
}
