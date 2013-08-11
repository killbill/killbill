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

package com.ning.billing.jaxrs.json;

import java.util.List;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.JaxrsTestSuiteNoDB;

import static com.ning.billing.jaxrs.JaxrsTestUtils.createAuditLogsJson;

public class TestEntitlementJsonSimple extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String accountId = UUID.randomUUID().toString();
        final String bundleId = UUID.randomUUID().toString();
        final String entitlementId = UUID.randomUUID().toString();
        final String externalKey = "externalkey";
        final List<AuditLogJson> auditLogs = createAuditLogsJson(clock.getUTCNow());
        final EntitlementJsonSimple entitlementJsonSimple = new EntitlementJsonSimple(accountId, bundleId, entitlementId, externalKey, auditLogs);
        Assert.assertEquals(entitlementJsonSimple.getAccountId(), accountId);
        Assert.assertEquals(entitlementJsonSimple.getBundleId(), bundleId);
        Assert.assertEquals(entitlementJsonSimple.getEntitlementId(), entitlementId);
        Assert.assertEquals(entitlementJsonSimple.getExternalKey(), externalKey);
        Assert.assertEquals(entitlementJsonSimple.getAuditLogs(), auditLogs);

        final String asJson = mapper.writeValueAsString(entitlementJsonSimple);

        final EntitlementJsonSimple fromJson = mapper.readValue(asJson, EntitlementJsonSimple.class);
        Assert.assertEquals(fromJson, entitlementJsonSimple);
    }
}
