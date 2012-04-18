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

package com.ning.billing.junction.dao;

import java.io.IOException;
import java.util.SortedSet;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.junction.dao.BlockingStateDao;
import com.ning.billing.junction.glue.NEModule;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.util.clock.ClockMock;

@Guice(modules = {MockModule.class,  NEModule.class})
public class TestBlockingDao {
    private Logger log = LoggerFactory.getLogger(TestBlockingDao.class);
    
    @Inject
    private MysqlTestingHelper helper;
    
    @Inject
    private BlockingStateDao dao;

    @Inject
    private BlockingStateDao accessDao;

    @BeforeClass(groups={"slow"})
    public void setup() throws IOException {
        log.info("Starting set up");

        final String utilDdl = IOUtils.toString(TestBlockingDao.class.getResourceAsStream("/com/ning/billing/junction/ddl.sql"));

        helper.startMysql();
        helper.initDb(utilDdl);

    }
    
    
    @Test(groups={"slow"}, enabled=true)
    public void testDao() { 
        ClockMock clock = new ClockMock();
        UUID uuid = UUID.randomUUID();
        String overdueStateName = "WayPassedItMan";
        String service = "TEST";
        
        boolean blockChange = true;
        boolean blockEntitlement = false;
        boolean blockBilling = false;

        BlockingState state1 = new BlockingState(uuid, overdueStateName, Blockable.Type.SUBSCRIPTION_BUNDLE, service, blockChange, blockEntitlement,blockBilling);
        dao.setBlockingState(state1, clock);
        clock.setDeltaFromReality(1000 * 3600 * 24);
        
        String overdueStateName2 = "NoReallyThisCantGoOn";
        BlockingState state2 = new BlockingState(uuid, overdueStateName2, Blockable.Type.SUBSCRIPTION_BUNDLE, service, blockChange, blockEntitlement,blockBilling);
        dao.setBlockingState(state2, clock);
        
        SubscriptionBundle bundle = BrainDeadProxyFactory.createBrainDeadProxyFor(SubscriptionBundle.class);
        ((ZombieControl)bundle).addResult("getId", uuid);
        
        Assert.assertEquals(accessDao.getBlockingStateFor(bundle), overdueStateName2);
        Assert.assertEquals(accessDao.getBlockingStateForIdAndType(bundle.getId(), Blockable.Type.SUBSCRIPTION_BUNDLE), overdueStateName2);
        
    }
    
    @Test(groups={"slow"}, enabled=true)
    public void testDaoHistory() throws CatalogApiException { 
        ClockMock clock = new ClockMock();
        UUID uuid = UUID.randomUUID();
        String overdueStateName = "WayPassedItMan";
        String service = "TEST";
        
        boolean blockChange = true;
        boolean blockEntitlement = false;
        boolean blockBilling = false;

        BlockingState state1 = new BlockingState(uuid, overdueStateName, Blockable.Type.SUBSCRIPTION_BUNDLE, service, blockChange, blockEntitlement,blockBilling);
        dao.setBlockingState(state1, clock);
        clock.setDeltaFromReality(1000 * 3600 * 24);
        
        String overdueStateName2 = "NoReallyThisCantGoOn";
        BlockingState state2 = new BlockingState(uuid, overdueStateName2, Blockable.Type.SUBSCRIPTION_BUNDLE, service, blockChange, blockEntitlement,blockBilling);
        dao.setBlockingState(state2, clock);
        
        SubscriptionBundle bundle = BrainDeadProxyFactory.createBrainDeadProxyFor(SubscriptionBundle.class);
        ((ZombieControl)bundle).addResult("getId", uuid);
        
     
        SortedSet<BlockingState> history1 = accessDao.getBlockingHistoryFor(bundle);
        SortedSet<BlockingState> history2 = accessDao.getBlockingHistoryForIdAndType(bundle.getId(), Blockable.Type.get(bundle));
        
        Assert.assertEquals(history1.size(), 2);
        Assert.assertEquals(history1.first().getStateName(), overdueStateName);
        Assert.assertEquals(history1.last().getStateName(), overdueStateName2);
        
        Assert.assertEquals(history2.size(), 2);
        Assert.assertEquals(history2.first().getStateName(), overdueStateName);
        Assert.assertEquals(history2.last().getStateName(), overdueStateName2);
       
    }
    
}
