/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.util.broadcast.dao;

import java.util.List;

import org.joda.time.DateTime;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestBroadcastDao extends UtilTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testBasic() throws Exception {
        final DateTime now = clock.getUTCNow();

        final BroadcastModelDao b1 = new BroadcastModelDao("svc1", "type1", "{attribute: kewl}", now, "tester");
        broadcastDao.create(b1);

        final BroadcastModelDao res = broadcastDao.getLatestEntry();
        assertEquals(res.getEvent(), b1.getEvent());
        assertEquals(res.getServiceName(), b1.getServiceName());
        assertEquals(res.getType(), b1.getType());

        final List<BroadcastModelDao> all = broadcastDao.getLatestEntriesFrom(0L);
        assertEquals(all.size(), 1);

        final List<BroadcastModelDao> none = broadcastDao.getLatestEntriesFrom(res.getRecordId());
        assertEquals(none.size(), 0, "Invalid entries: " + none.toString());
    }
}
