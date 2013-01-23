/*
 * Copyright 2010-2012 Ning, Inc.
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
import java.util.Map;
import java.util.UUID;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.junction.JunctionTestSuiteWithEmbeddedDB;
import com.ning.billing.junction.api.Blockable.Type;
import com.ning.billing.junction.api.svcs.DefaultInternalBlockingApi;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.junction.dao.BlockingStateDao;
import com.ning.billing.junction.dao.DefaultBlockingStateDao;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.dao.DefaultNonEntityDao;
import com.ning.billing.util.svcapi.junction.DefaultBlockingState;

public class TestDefaultBlockingApi extends JunctionTestSuiteWithEmbeddedDB {

    private final ClockMock clock = new ClockMock();
    private final CacheControllerDispatcher controllerDispatcher = new CacheControllerDispatcher();


    private DefaultInternalBlockingApi blockingApi;


    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final BlockingStateDao blockingStateDao = new DefaultBlockingStateDao(getDBI(), clock, controllerDispatcher, new DefaultNonEntityDao(getDBI()));
        blockingApi = new DefaultInternalBlockingApi(blockingStateDao, clock);
    }

    @Test(groups = "slow", enabled=false)
    public void testSetBlockingStateOnBundle() throws Exception {
        final UUID bundleId = UUID.randomUUID();
        final Long accountRecordId = 123049714L;
        getDBI().withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                handle.execute("DROP TABLE IF EXISTS bundles;\n" +
                               "CREATE TABLE bundles (\n" +
                               "    record_id int(11) unsigned NOT NULL AUTO_INCREMENT,\n" +
                               "    id char(36) NOT NULL,\n" +
                               "    external_key varchar(64) NOT NULL,\n" +
                               "    account_id char(36) NOT NULL,\n" +
                               "    last_sys_update_date datetime,\n" +
                               "    account_record_id int(11) unsigned default null,\n" +
                               "    tenant_record_id int(11) unsigned default null,\n" +
                               "    PRIMARY KEY(record_id)\n" +
                               ") ENGINE=innodb;");
                handle.execute("insert into bundles (id, external_key, account_id, account_record_id) values (?, 'foo', ?, ?)",
                               bundleId.toString(), UUID.randomUUID().toString(), accountRecordId);
                return null;
            }
        });

        final BlockingState blockingState = new DefaultBlockingState(UUID.randomUUID(), bundleId, "BLOCKED", Type.SUBSCRIPTION_BUNDLE, "myService", true, true, true, internalCallContext.getCreatedDate(), null);
        blockingApi.setBlockingState(blockingState, internalCallContext);

        // Verify the blocking state was applied
        final BlockingState resultState = blockingApi.getBlockingStateFor(bundleId, internalCallContext);

        Assert.assertEquals(resultState.getStateName(), blockingState.getStateName());
        // Verify the account_record_id was populated
        getDBI().withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                final List<Map<String, Object>> values = handle.select("select account_record_id from blocking_states where id = ?", bundleId.toString());
                Assert.assertEquals(values.size(), 1);
                Assert.assertEquals(values.get(0).keySet().size(), 1);
                Assert.assertEquals(values.get(0).get("account_record_id"), accountRecordId);
                return null;
            }
        });
    }
}
