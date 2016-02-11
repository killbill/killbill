/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.tenant.dao;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.tenant.TenantTestSuiteWithEmbeddedDb;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestNoCachingTenantBroadcastDao extends TenantTestSuiteWithEmbeddedDb {

    @Test(groups = "slow")
    public void testBasic() throws Exception {
        final TenantBroadcastModelDao model = new TenantBroadcastModelDao(0L, "foo", UUID.randomUUID());

        internalCallContext.setTenantRecordId(79L);
        tenantBroadcastDao.create(model, internalCallContext);

        final TenantBroadcastModelDao result1 = tenantBroadcastDao.getById(model.getId(), internalCallContext);
        Assert.assertEquals(result1.getTenantRecordId(), new Long(79L));
        Assert.assertEquals(result1.getType(), "foo");

        internalCallContext.reset();

        final TenantBroadcastModelDao resultNull = tenantBroadcastDao.getById(model.getId(), internalCallContext);
        Assert.assertNull(resultNull);

        final TenantBroadcastModelDao result2 = noCachingTenantBroadcastDao.getLatestEntry();
        Assert.assertEquals(result2.getTenantRecordId(), new Long(79L));
        Assert.assertEquals(result2.getType(), "foo");
    }

    @Test(groups = "slow")
    public void testLatestEntries() throws Exception {
        internalCallContext.setTenantRecordId(81L);

        TenantBroadcastModelDao latestInsert = null;
        for (int i = 0; i < 100; i++) {
            final TenantBroadcastModelDao model = new TenantBroadcastModelDao(0L, "foo-" + i, UUID.randomUUID());
            tenantBroadcastDao.create(model, internalCallContext);
            latestInsert = model;
        }
        final TenantBroadcastModelDao latestInsertRefreshed = tenantBroadcastDao.getById(latestInsert.getId(), internalCallContext);
        final TenantBroadcastModelDao lastEntry = noCachingTenantBroadcastDao.getLatestEntry();

        Assert.assertEquals(lastEntry.getRecordId(), latestInsertRefreshed.getRecordId());

        final int expectedEntries = 25;
        final Long fromRecordId = lastEntry.getRecordId() - expectedEntries;
        final List<TenantBroadcastModelDao> result = noCachingTenantBroadcastDao.getLatestEntriesFrom(fromRecordId);
        Assert.assertEquals(result.size(), expectedEntries);

        long i = 0;
        for (final TenantBroadcastModelDao cur : result) {
            Assert.assertEquals(cur.getRecordId().longValue(), (fromRecordId + i++ + 1L));
        }
    }
}
