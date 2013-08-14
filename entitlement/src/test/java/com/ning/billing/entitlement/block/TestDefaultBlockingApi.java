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

package com.ning.billing.entitlement.block;

import com.ning.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.api.BlockingStateType;
import com.ning.billing.util.svcapi.junction.DefaultBlockingState;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TestDefaultBlockingApi extends EntitlementTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testSetBlockingStateOnBundle() throws Exception {
        final UUID bundleId = UUID.randomUUID();
        getDBI().withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                handle.execute("insert into bundles (id, external_key, account_id, created_by, created_date, updated_by, updated_date, account_record_id, tenant_record_id) " +
                               "values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        bundleId.toString(),
                        "foo",
                        UUID.randomUUID().toString(),
                        "zozo",
                        internalCallContext.getCreatedDate().toDate(),
                        "zozo",
                        internalCallContext.getCreatedDate().toDate(),
                        internalCallContext.getAccountRecordId(),
                        internalCallContext.getTenantRecordId());
                return null;
            }
        });

        final BlockingState blockingState = new DefaultBlockingState(UUID.randomUUID(), bundleId, BlockingStateType.ACCOUNT, "BLOCKED", "myService", true, true, true, clock.getUTCNow(), internalCallContext.getCreatedDate(), null);
        blockingInternalApi.setBlockingState(blockingState, internalCallContext);

        // Verify the blocking state was applied
        final BlockingState resultState = blockingInternalApi.getBlockingStateForService(bundleId, "myService", internalCallContext);

        Assert.assertEquals(resultState.getStateName(), blockingState.getStateName());
        // Verify the account_record_id was populated
        getDBI().withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                final List<Map<String, Object>> values = handle.select("select account_record_id from blocking_states where blockable_id = ?", bundleId.toString());
                Assert.assertEquals(values.size(), 1);
                Assert.assertEquals(values.get(0).keySet().size(), 1);
                Assert.assertEquals(values.get(0).get("account_record_id"), internalCallContext.getAccountRecordId());
                return null;
            }
        });
    }
}
