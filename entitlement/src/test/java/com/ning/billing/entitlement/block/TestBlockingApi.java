/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.entitlement.block;

import java.util.List;
import java.util.UUID;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.api.BlockingStateType;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.util.svcapi.junction.DefaultBlockingState;

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

        final BlockingState state1 = new DefaultBlockingState(uuid, BlockingStateType.ACCOUNT,overdueStateName, service, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingInternalApi.setBlockingState(state1, internalCallContext);
        clock.setDeltaFromReality(1000 * 3600 * 24);

        final String overdueStateName2 = "NoReallyThisCantGoOn";
        final BlockingState state2 = new DefaultBlockingState(uuid, BlockingStateType.ACCOUNT, overdueStateName2, service, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingInternalApi.setBlockingState(state2, internalCallContext);

        final SubscriptionBaseBundle bundle = Mockito.mock(SubscriptionBaseBundle.class);
        Mockito.when(bundle.getId()).thenReturn(uuid);

        Assert.assertEquals(blockingInternalApi.getBlockingStateForService(bundle, service, internalCallContext).getStateName(), overdueStateName2);
        Assert.assertEquals(blockingInternalApi.getBlockingStateForService(bundle.getId(), service, internalCallContext).getStateName(), overdueStateName2);


    }

    @Test(groups = "slow")
    public void testApiHistory() throws Exception {
        final UUID uuid = UUID.randomUUID();
        final String overdueStateName = "WayPassedItMan";
        final String service = "TEST";

        final boolean blockChange = true;
        final boolean blockEntitlement = false;
        final boolean blockBilling = false;

        final BlockingState state1 = new DefaultBlockingState(uuid, BlockingStateType.ACCOUNT, overdueStateName, service, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingInternalApi.setBlockingState(state1, internalCallContext);

        clock.setDeltaFromReality(1000 * 3600 * 24);

        final String overdueStateName2 = "NoReallyThisCantGoOn";
        final BlockingState state2 = new DefaultBlockingState(uuid, BlockingStateType.ACCOUNT, overdueStateName2, service, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingInternalApi.setBlockingState(state2, internalCallContext);

        final SubscriptionBaseBundle bundle = Mockito.mock(SubscriptionBaseBundle.class);
        Mockito.when(bundle.getId()).thenReturn(uuid);

        final List<BlockingState> history1 = blockingInternalApi.getBlockingHistoryForService(bundle, service, internalCallContext);
        final List<BlockingState> history2 = blockingInternalApi.getBlockingHistoryForService(bundle.getId(), service, internalCallContext);

        Assert.assertEquals(history1.size(), 2);
        Assert.assertEquals(history1.get(0).getStateName(), overdueStateName);
        Assert.assertEquals(history1.get(1).getStateName(), overdueStateName2);

        Assert.assertEquals(history2.size(), 2);
        Assert.assertEquals(history2.get(0).getStateName(), overdueStateName);
        Assert.assertEquals(history2.get(1).getStateName(), overdueStateName2);
    }
}
