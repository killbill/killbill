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

package org.killbill.billing.entitlement.block;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.payment.api.PluginProperty;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;

public class TestBlockingApi extends EntitlementTestSuiteWithEmbeddedDB {

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        clock.resetDeltaFromReality();
    }

    @Test(groups = "slow")
    public void testApi() {
        final UUID uuid = UUID.randomUUID();
        final String overdueStateName = "WayPassedItMan";
        final String service = "TEST";

        final boolean blockChange = true;
        final boolean blockEntitlement = false;
        final boolean blockBilling = false;

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState state1 = new DefaultBlockingState(uuid, BlockingStateType.ACCOUNT, overdueStateName, service, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingInternalApi.setBlockingState(state1, internalCallContext);
        assertListenerStatus();

        clock.setDeltaFromReality(1000 * 3600 * 24);

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final String overdueStateName2 = "NoReallyThisCantGoOn";
        final BlockingState state2 = new DefaultBlockingState(uuid, BlockingStateType.ACCOUNT, overdueStateName2, service, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingInternalApi.setBlockingState(state2, internalCallContext);
        assertListenerStatus();

        Assert.assertEquals(blockingInternalApi.getBlockingStateForService(uuid, BlockingStateType.ACCOUNT, service, internalCallContext).getStateName(), overdueStateName2);
    }

    @Test(groups = "slow")
    public void testApiHistory() throws Exception {
        final String overdueStateName = "WayPassedItMan";
        final String service = "TEST";

        final boolean blockChange = true;
        final boolean blockEntitlement = false;
        final boolean blockBilling = false;

        final Account account = createAccount(getAccountData(7));

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState state1 = new DefaultBlockingState(account.getId(), BlockingStateType.ACCOUNT, overdueStateName, service, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingInternalApi.setBlockingState(state1, internalCallContext);
        assertListenerStatus();

        clock.setDeltaFromReality(1000 * 3600 * 24);

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final String overdueStateName2 = "NoReallyThisCantGoOn";
        final BlockingState state2 = new DefaultBlockingState(account.getId(), BlockingStateType.ACCOUNT, overdueStateName2, service, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingInternalApi.setBlockingState(state2, internalCallContext);
        assertListenerStatus();

        final List<BlockingState> blockingAll = blockingInternalApi.getBlockingAllForAccount(internalCallContext);
        final List<BlockingState> history = ImmutableList.<BlockingState>copyOf(Collections2.<BlockingState>filter(blockingAll,
                                                                                                                   new Predicate<BlockingState>() {
                                                                                                                       @Override
                                                                                                                       public boolean apply(final BlockingState input) {
                                                                                                                           return input.getService().equals(service);
                                                                                                                       }
                                                                                                                   }));

        Assert.assertEquals(history.size(), 2);
        Assert.assertEquals(history.get(0).getStateName(), overdueStateName);
        Assert.assertEquals(history.get(1).getStateName(), overdueStateName2);
    }

    @Test(groups = "slow")
    public void testBlockingAcrossTypes() throws Exception {

        final String stateNameBlock = "stateBlock";
        final String stateNameUnBlock = "stateUnBlock";
        final String service = "SVC_BLOC_TYPES";

        final boolean blockChange = false;
        final boolean blockEntitlement = true;
        final boolean blockBilling = false;

        final Account account = createAccount(getAccountData(7));

        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState state1 = new DefaultBlockingState(account.getId(), BlockingStateType.ACCOUNT, stateNameBlock, service, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        subscriptionApi.addBlockingState(state1, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.ANNUAL, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        Entitlement baseEntitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), null, null, null, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        assertEquals(baseEntitlement.getState(), EntitlementState.BLOCKED);

        // Add blocking at bundle level.
        clock.addDays(1);
        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState state2 = new DefaultBlockingState(baseEntitlement.getBundleId(), BlockingStateType.SUBSCRIPTION_BUNDLE, stateNameBlock, service, blockChange, blockEntitlement, blockBilling, null);
        subscriptionApi.addBlockingState(state2, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();


        baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext);
        assertEquals(baseEntitlement.getState(), EntitlementState.BLOCKED);

        // Remove blocking at account level
        clock.addDays(1);
        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState state3 = new DefaultBlockingState(account.getId(), BlockingStateType.ACCOUNT, stateNameUnBlock, service, false, false, false, clock.getUTCNow());
        subscriptionApi.addBlockingState(state3, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext);
        assertEquals(baseEntitlement.getState(), EntitlementState.BLOCKED);


        // Remove blocking at bundle level.
        clock.addDays(1);
        testListener.pushExpectedEvent(NextEvent.BLOCK);
        final BlockingState state4 = new DefaultBlockingState(baseEntitlement.getBundleId(), BlockingStateType.SUBSCRIPTION_BUNDLE, stateNameUnBlock, service, false, false, false, null);
        subscriptionApi.addBlockingState(state4, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlement.getId(), callContext);
        assertEquals(baseEntitlement.getState(), EntitlementState.ACTIVE);

        final List<BlockingState> blockingAll = blockingInternalApi.getBlockingAllForAccount(internalCallContext);
        final List<BlockingState> history = ImmutableList.<BlockingState>copyOf(Collections2.<BlockingState>filter(blockingAll,
                                                                                                                   new Predicate<BlockingState>() {
                                                                                                                       @Override
                                                                                                                       public boolean apply(final BlockingState input) {
                                                                                                                           return input.getService().equals(service);
                                                                                                                       }
                                                                                                                   }));

        Assert.assertEquals(history.size(), 4);
    }
}
