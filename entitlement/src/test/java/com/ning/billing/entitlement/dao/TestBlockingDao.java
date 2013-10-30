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

package com.ning.billing.entitlement.dao;

import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.api.BlockingStateType;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.junction.DefaultBlockingState;

public class TestBlockingDao extends EntitlementTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testDao() {


        final UUID uuid = UUID.randomUUID();
        final String overdueStateName = "WayPassedItMan";
        final String service = "TEST";

        final boolean blockChange = true;
        final boolean blockEntitlement = false;
        final boolean blockBilling = false;

        clock.setDay(new LocalDate(2012, 4, 1));

        final BlockingState state1 = new DefaultBlockingState(uuid, BlockingStateType.ACCOUNT, overdueStateName, service, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingStateDao.setBlockingState(state1, clock, internalCallContext);

        clock.addDays(1);

        final String overdueStateName2 = "NoReallyThisCantGoOn";
        final BlockingState state2 = new DefaultBlockingState(uuid, BlockingStateType.ACCOUNT, overdueStateName2, service, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingStateDao.setBlockingState(state2, clock, internalCallContext);

        final SubscriptionBaseBundle bundle = Mockito.mock(SubscriptionBaseBundle.class);
        Mockito.when(bundle.getId()).thenReturn(uuid);

        Assert.assertEquals(blockingStateDao.getBlockingStateForService(uuid, service, internalCallContext).getStateName(), state2.getStateName());

        final List<BlockingState> states = blockingStateDao.getBlockingHistoryForService(uuid, service, internalCallContext);
        Assert.assertEquals(states.size(), 2);

        Assert.assertEquals(states.get(0).getStateName(), overdueStateName);
        Assert.assertEquals(states.get(1).getStateName(), overdueStateName2);
    }


    @Test(groups = "slow")
    public void testDaoHistory() throws Exception {
        final UUID uuid = UUID.randomUUID();
        final String overdueStateName = "WayPassedItMan";
        final String service1 = "TEST";

        final boolean blockChange = true;
        final boolean blockEntitlement = false;
        final boolean blockBilling = false;

        final BlockingState state1 = new DefaultBlockingState(uuid, BlockingStateType.ACCOUNT, overdueStateName, service1, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingStateDao.setBlockingState(state1, clock, internalCallContext);
        clock.setDeltaFromReality(1000 * 3600 * 24);

        final String service2 = "TEST2";

        final String overdueStateName2 = "NoReallyThisCantGoOn";
        final BlockingState state2 = new DefaultBlockingState(uuid, BlockingStateType.ACCOUNT, overdueStateName2, service2, blockChange, blockEntitlement, blockBilling, clock.getUTCNow());
        blockingStateDao.setBlockingState(state2, clock, internalCallContext);

        final SubscriptionBaseBundle bundle = Mockito.mock(SubscriptionBaseBundle.class);
        Mockito.when(bundle.getId()).thenReturn(uuid);

        final List<BlockingState> history2 = blockingStateDao.getBlockingAll(bundle.getId(), internalCallContext);
        Assert.assertEquals(history2.size(), 2);
        Assert.assertEquals(history2.get(0).getStateName(), overdueStateName);
        Assert.assertEquals(history2.get(1).getStateName(), overdueStateName2);
    }
}
