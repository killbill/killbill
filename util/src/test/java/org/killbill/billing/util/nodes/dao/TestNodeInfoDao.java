/*
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

package org.killbill.billing.util.nodes.dao;

import java.util.List;

import org.joda.time.DateTime;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestNodeInfoDao extends UtilTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testBasic() throws Exception {

        DateTime now = clock.getUTCNow();

        final DateTime initialBootTime1 = clock.getUTCNow().minusDays(1);
        final NodeInfoModelDao node1 = new NodeInfoModelDao(-1L, "node1", initialBootTime1, now, "nodeInfo", true);
        nodeInfoDao.create(node1);

        List<NodeInfoModelDao> all = nodeInfoDao.getAll();
        assertEquals(all.size(), 1);
        assertEquals(all.get(0), node1);

        final DateTime secondBootTime1 = clock.getUTCNow();
        now = clock.getUTCNow().plusSeconds(1);
        final NodeInfoModelDao newNode1 = new NodeInfoModelDao(-1L, "node1", secondBootTime1, now, "nodeInfo", true);
        nodeInfoDao.create(newNode1);

        all = nodeInfoDao.getAll();
        assertEquals(all.size(), 1);
        assertEquals(all.get(0), newNode1);

        final DateTime initialBootTime2 = clock.getUTCNow();
        final NodeInfoModelDao node2 = new NodeInfoModelDao(-1L, "node2", initialBootTime2, now, "nodeInfo", true);
        nodeInfoDao.create(node2);

        all = nodeInfoDao.getAll();
        assertEquals(all.size(), 2);
        assertEquals(all.get(0), newNode1);
        assertEquals(all.get(1), node2);

        final NodeInfoModelDao newNode2 = new NodeInfoModelDao(-1L, "node2", initialBootTime2, now, "nodeInfo2", true);

        nodeInfoDao.updateNodeInfo(newNode2.getNodeName(), newNode2.getNodeInfo());

        all = nodeInfoDao.getAll();
        assertEquals(all.size(), 2);
        assertEquals(all.get(0), newNode1);
        assertEquals(all.get(1), newNode2);

        clock.addDeltaFromReality(5000);
        nodeInfoDao.setUpdatedDate(newNode1.getNodeName());
        nodeInfoDao.setUpdatedDate(newNode2.getNodeName());
        all = nodeInfoDao.getAll();
        assertEquals(all.size(), 2);
        assertTrue(all.get(0).getUpdatedDate().compareTo(now) > 0);
        assertTrue(all.get(1).getUpdatedDate().compareTo(now) > 0);
    }

}
