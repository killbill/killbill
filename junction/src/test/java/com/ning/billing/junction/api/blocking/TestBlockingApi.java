/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.junction.api.blocking;

import java.util.List;
import java.util.UUID;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.JunctionTestSuiteWithEmbeddedDB;
import com.ning.billing.junction.MockModule;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.junction.api.DefaultBlockingState;
import com.ning.billing.mock.glue.MockEntitlementModule;
import com.ning.billing.util.clock.ClockMock;

import com.google.inject.Inject;

@Guice(modules = {MockModule.class, MockEntitlementModule.class})
public class TestBlockingApi extends JunctionTestSuiteWithEmbeddedDB {
    @Inject
    private BlockingApi api;

    @Inject
    private ClockMock clock;

    @BeforeMethod(groups = "slow")
    public void clean() {
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

        final BlockingState state1 = new DefaultBlockingState(uuid, overdueStateName, Blockable.Type.SUBSCRIPTION_BUNDLE, service, blockChange, blockEntitlement, blockBilling);
        api.setBlockingState(state1, callContext);
        clock.setDeltaFromReality(1000 * 3600 * 24);

        final String overdueStateName2 = "NoReallyThisCantGoOn";
        final BlockingState state2 = new DefaultBlockingState(uuid, overdueStateName2, Blockable.Type.SUBSCRIPTION_BUNDLE, service, blockChange, blockEntitlement, blockBilling);
        api.setBlockingState(state2, callContext);

        final SubscriptionBundle bundle = Mockito.mock(SubscriptionBundle.class);
        Mockito.when(bundle.getId()).thenReturn(uuid);

        Assert.assertEquals(api.getBlockingStateFor(bundle, callContext).getStateName(), overdueStateName2);
        Assert.assertEquals(api.getBlockingStateFor(bundle.getId(), callContext).getStateName(), overdueStateName2);
    }

    @Test(groups = "slow")
    public void testApiHistory() throws Exception {
        final UUID uuid = UUID.randomUUID();
        final String overdueStateName = "WayPassedItMan";
        final String service = "TEST";

        final boolean blockChange = true;
        final boolean blockEntitlement = false;
        final boolean blockBilling = false;

        final BlockingState state1 = new DefaultBlockingState(uuid, overdueStateName, Blockable.Type.SUBSCRIPTION_BUNDLE, service, blockChange, blockEntitlement, blockBilling);
        api.setBlockingState(state1, callContext);

        clock.setDeltaFromReality(1000 * 3600 * 24);

        final String overdueStateName2 = "NoReallyThisCantGoOn";
        final BlockingState state2 = new DefaultBlockingState(uuid, overdueStateName2, Blockable.Type.SUBSCRIPTION_BUNDLE, service, blockChange, blockEntitlement, blockBilling);
        api.setBlockingState(state2, callContext);

        final SubscriptionBundle bundle = Mockito.mock(SubscriptionBundle.class);
        Mockito.when(bundle.getId()).thenReturn(uuid);

        final List<BlockingState> history1 = api.getBlockingHistory(bundle, callContext);
        final List<BlockingState> history2 = api.getBlockingHistory(bundle.getId(), callContext);

        Assert.assertEquals(history1.size(), 2);
        Assert.assertEquals(history1.get(0).getStateName(), overdueStateName);
        Assert.assertEquals(history1.get(1).getStateName(), overdueStateName2);

        Assert.assertEquals(history2.size(), 2);
        Assert.assertEquals(history2.get(0).getStateName(), overdueStateName);
        Assert.assertEquals(history2.get(1).getStateName(), overdueStateName2);
    }
}
