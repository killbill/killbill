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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.MockModule;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.junction.api.DefaultBlockingState;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.mock.glue.MockEntitlementModule;
import com.ning.billing.util.clock.ClockMock;

@Guice(modules = {MockModule.class, MockEntitlementModule.class})
public class TestBlockingDao {
    private final Logger log = LoggerFactory.getLogger(TestBlockingDao.class);

    @Inject
    private MysqlTestingHelper helper;

    @Inject
    private BlockingStateDao dao;

    @BeforeClass(groups = {"slow"})
    public void setup() throws IOException {
        log.info("Starting set up TestBlockingDao");

        final String utilDdl = IOUtils.toString(TestBlockingDao.class.getResourceAsStream("/com/ning/billing/junction/ddl.sql"));

        helper.startMysql();
        helper.initDb(utilDdl);

    }

    @AfterClass(groups = "slow")
    public void stopMysql() {
        if (helper != null) {
            helper.stopMysql();
        }
    }

    @Test(groups = {"slow"}, enabled = true)
    public void testDao() {
        final ClockMock clock = new ClockMock();
        final UUID uuid = UUID.randomUUID();
        final String overdueStateName = "WayPassedItMan";
        final String service = "TEST";

        final boolean blockChange = true;
        final boolean blockEntitlement = false;
        final boolean blockBilling = false;

        final BlockingState state1 = new DefaultBlockingState(uuid, overdueStateName, Blockable.Type.SUBSCRIPTION_BUNDLE, service, blockChange, blockEntitlement, blockBilling);
        dao.setBlockingState(state1, clock);
        clock.setDeltaFromReality(1000 * 3600 * 24);

        final String overdueStateName2 = "NoReallyThisCantGoOn";
        final BlockingState state2 = new DefaultBlockingState(uuid, overdueStateName2, Blockable.Type.SUBSCRIPTION_BUNDLE, service, blockChange, blockEntitlement, blockBilling);
        dao.setBlockingState(state2, clock);

        final SubscriptionBundle bundle = BrainDeadProxyFactory.createBrainDeadProxyFor(SubscriptionBundle.class);
        ((ZombieControl) bundle).addResult("getId", uuid);

        Assert.assertEquals(dao.getBlockingStateFor(bundle).getStateName(), state2.getStateName());
        Assert.assertEquals(dao.getBlockingStateFor(bundle.getId()).getStateName(), overdueStateName2);

    }

    @Test(groups = {"slow"}, enabled = true)
    public void testDaoHistory() throws Exception {
        final ClockMock clock = new ClockMock();
        final UUID uuid = UUID.randomUUID();
        final String overdueStateName = "WayPassedItMan";
        final String service = "TEST";

        final boolean blockChange = true;
        final boolean blockEntitlement = false;
        final boolean blockBilling = false;

        final BlockingState state1 = new DefaultBlockingState(uuid, overdueStateName, Blockable.Type.SUBSCRIPTION_BUNDLE, service, blockChange, blockEntitlement, blockBilling);
        dao.setBlockingState(state1, clock);
        clock.setDeltaFromReality(1000 * 3600 * 24);

        final String overdueStateName2 = "NoReallyThisCantGoOn";
        final BlockingState state2 = new DefaultBlockingState(uuid, overdueStateName2, Blockable.Type.SUBSCRIPTION_BUNDLE, service, blockChange, blockEntitlement, blockBilling);
        dao.setBlockingState(state2, clock);

        final SubscriptionBundle bundle = BrainDeadProxyFactory.createBrainDeadProxyFor(SubscriptionBundle.class);
        ((ZombieControl) bundle).addResult("getId", uuid);


        final SortedSet<BlockingState> history1 = dao.getBlockingHistoryFor(bundle);
        final SortedSet<BlockingState> history2 = dao.getBlockingHistoryFor(bundle.getId());

        Assert.assertEquals(history1.size(), 2);
        Assert.assertEquals(history1.first().getStateName(), overdueStateName);
        Assert.assertEquals(history1.last().getStateName(), overdueStateName2);

        Assert.assertEquals(history2.size(), 2);
        Assert.assertEquals(history2.first().getStateName(), overdueStateName);
        Assert.assertEquals(history2.last().getStateName(), overdueStateName2);

    }

}
