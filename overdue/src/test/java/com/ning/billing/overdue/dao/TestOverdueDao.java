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

package com.ning.billing.overdue.dao;

import java.io.IOException;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.TimeUnit;
import com.ning.billing.catalog.api.overdue.OverdueState;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.overdue.glue.OverdueModule;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.glue.OverdueAccessModule;
import com.ning.billing.util.overdue.dao.OverdueAccessDao;

@Guice(modules = {MockModule.class, OverdueModule.class, OverdueAccessModule.class})
public class TestOverdueDao {
    private Logger log = LoggerFactory.getLogger(TestOverdueDao.class);
    
    @Inject
    private MysqlTestingHelper helper;
    
    @Inject
    private OverdueDao dao;

    @Inject
    private OverdueAccessDao accessDao;

    @BeforeClass(groups={"slow"})
    public void setup() throws IOException {
        log.info("Starting set up");

        final String utilDdl = IOUtils.toString(TestOverdueDao.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));

        helper.startMysql();
        helper.initDb(utilDdl);

    }
    
    
    @Test(groups={"slow"}, enabled=true)
    public void testDao() { 
        ClockMock clock = new ClockMock();
        SubscriptionBundle bundle = BrainDeadProxyFactory.createBrainDeadProxyFor(SubscriptionBundle.class);
        UUID bundleId = UUID.randomUUID();
        ((ZombieControl)bundle).addResult("getId", bundleId);
        
        String overdueStateName = "WayPassedItMan";
        @SuppressWarnings("unchecked")
        OverdueState<SubscriptionBundle> state = BrainDeadProxyFactory.createBrainDeadProxyFor(OverdueState.class);
        ((ZombieControl)state).addResult("getName", overdueStateName);
        
        dao.setOverdueState(bundle, state, clock);
        clock.setDeltaFromReality(1000 * 3600 * 24);
        
        String overdueStateName2 = "NoReallyThisCantGoOn";
        ((ZombieControl)state).addResult("getName", overdueStateName2);
        dao.setOverdueState(bundle, state, clock);
        
        Assert.assertEquals(accessDao.getOverdueStateNameFor(bundle), overdueStateName2);
        
    }
    
}
