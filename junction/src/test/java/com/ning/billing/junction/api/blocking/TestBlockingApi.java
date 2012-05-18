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

import java.io.IOException;
import java.util.SortedSet;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.MockModule;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.junction.api.DefaultBlockingState;
import com.ning.billing.junction.dao.TestBlockingDao;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.mock.glue.MockEntitlementModule;
import com.ning.billing.util.clock.ClockMock;

@Guice(modules = { MockModule.class, MockEntitlementModule.class })
public class TestBlockingApi {
    private Logger log = LoggerFactory.getLogger(TestBlockingDao.class);
    
    @Inject
    private MysqlTestingHelper helper;
    
    @Inject 
    private BlockingApi api;
    
    @Inject
    private ClockMock clock;

    @BeforeClass(groups={"slow"})
    public void setup() throws IOException {
        log.info("Starting set up TestBlockingApi");

        final String utilDdl = IOUtils.toString(TestBlockingDao.class.getResourceAsStream("/com/ning/billing/junction/ddl.sql"));

        helper.startMysql();
        helper.initDb(utilDdl);
     
    }
    
    @BeforeMethod(groups={"slow"})
    public void clean() {       
        helper.cleanupTable("blocking_states");
        clock.resetDeltaFromReality();
    }
    
    @AfterClass(groups = "slow")
    public void stopMysql()
    {
        helper.stopMysql();
    }

    @Test(groups={"slow"}, enabled=true)
    public void testApi() { 

        UUID uuid = UUID.randomUUID();
        String overdueStateName = "WayPassedItMan";
        String service = "TEST";
        
        boolean blockChange = true;
        boolean blockEntitlement = false;
        boolean blockBilling = false;

        BlockingState state1 = new DefaultBlockingState(uuid, overdueStateName, Blockable.Type.SUBSCRIPTION_BUNDLE, service, blockChange, blockEntitlement,blockBilling);
        api.setBlockingState(state1);
        clock.setDeltaFromReality(1000 * 3600 * 24);
        
        String overdueStateName2 = "NoReallyThisCantGoOn";
        BlockingState state2 = new DefaultBlockingState(uuid, overdueStateName2, Blockable.Type.SUBSCRIPTION_BUNDLE, service, blockChange, blockEntitlement,blockBilling);
        api.setBlockingState(state2);
        
        SubscriptionBundle bundle = BrainDeadProxyFactory.createBrainDeadProxyFor(SubscriptionBundle.class);
        ((ZombieControl)bundle).addResult("getId", uuid);
        
        Assert.assertEquals(api.getBlockingStateFor(bundle).getStateName(), overdueStateName2);
        Assert.assertEquals(api.getBlockingStateFor(bundle.getId()).getStateName(), overdueStateName2);
        
    }
    
    @Test(groups={"slow"}, enabled=true)
    public void testApiHistory() throws Exception { 
        UUID uuid = UUID.randomUUID();
        String overdueStateName = "WayPassedItMan";
        String service = "TEST";
        
        boolean blockChange = true;
        boolean blockEntitlement = false;
        boolean blockBilling = false;

        BlockingState state1 = new DefaultBlockingState(uuid, overdueStateName, Blockable.Type.SUBSCRIPTION_BUNDLE, service, blockChange, blockEntitlement,blockBilling);
        api.setBlockingState(state1);
        
        clock.setDeltaFromReality(1000 * 3600 * 24);

        String overdueStateName2 = "NoReallyThisCantGoOn";
        BlockingState state2 = new DefaultBlockingState(uuid, overdueStateName2, Blockable.Type.SUBSCRIPTION_BUNDLE, service, blockChange, blockEntitlement,blockBilling);
        api.setBlockingState(state2);
        
        SubscriptionBundle bundle = BrainDeadProxyFactory.createBrainDeadProxyFor(SubscriptionBundle.class);
        ((ZombieControl)bundle).addResult("getId", uuid);
        
     
        SortedSet<BlockingState> history1 = api.getBlockingHistory(bundle);
        SortedSet<BlockingState> history2 = api.getBlockingHistory(bundle.getId());
        
        Assert.assertEquals(history1.size(), 2);
        Assert.assertEquals(history1.first().getStateName(), overdueStateName);
        Assert.assertEquals(history1.last().getStateName(), overdueStateName2);
        
        Assert.assertEquals(history2.size(), 2);
        Assert.assertEquals(history2.first().getStateName(), overdueStateName);
        Assert.assertEquals(history2.last().getStateName(), overdueStateName2);
       
    }
    
}
