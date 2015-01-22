/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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
import org.killbill.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.junction.DefaultBlockingState;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

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
        final UUID uuid = UUID.randomUUID();
        final String overdueStateName = "WayPassedItMan";
        final String service = "TEST";

        final boolean blockChange = true;
        final boolean blockEntitlement = false;
        final boolean blockBilling = false;

        final Account account = accountApi.createAccount(getAccountData(7), callContext);
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);

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
}
