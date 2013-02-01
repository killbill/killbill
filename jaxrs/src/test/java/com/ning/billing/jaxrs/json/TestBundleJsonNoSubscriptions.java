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

package com.ning.billing.jaxrs.json;

import java.util.List;
import java.util.UUID;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.jaxrs.JaxrsTestSuiteNoDB;

import static com.ning.billing.jaxrs.JaxrsTestUtils.createAuditLogsJson;

public class TestBundleJsonNoSubscriptions extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String bundleId = UUID.randomUUID().toString();
        final String accountId = UUID.randomUUID().toString();
        final String externalKey = UUID.randomUUID().toString();
        final List<AuditLogJson> auditLogs = createAuditLogsJson(clock.getUTCNow());
        final BundleJsonNoSubscriptions bundleJsonNoSubscriptions = new BundleJsonNoSubscriptions(bundleId, accountId, externalKey, null, auditLogs);
        Assert.assertEquals(bundleJsonNoSubscriptions.getBundleId(), bundleId);
        Assert.assertEquals(bundleJsonNoSubscriptions.getAccountId(), accountId);
        Assert.assertEquals(bundleJsonNoSubscriptions.getExternalKey(), externalKey);
        Assert.assertEquals(bundleJsonNoSubscriptions.getAuditLogs(), auditLogs);

        final String asJson = mapper.writeValueAsString(bundleJsonNoSubscriptions);
        final BundleJsonNoSubscriptions fromJson = mapper.readValue(asJson, BundleJsonNoSubscriptions.class);
        Assert.assertEquals(fromJson, bundleJsonNoSubscriptions);
    }

    @Test(groups = "fast")
    public void testFromSubscriptionBundle() throws Exception {
        final SubscriptionBundle bundle = Mockito.mock(SubscriptionBundle.class);
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = UUID.randomUUID().toString();
        final UUID accountId = UUID.randomUUID();
        Mockito.when(bundle.getId()).thenReturn(bundleId);
        Mockito.when(bundle.getExternalKey()).thenReturn(externalKey);
        Mockito.when(bundle.getAccountId()).thenReturn(accountId);

        final BundleJsonNoSubscriptions bundleJsonNoSubscriptions = new BundleJsonNoSubscriptions(bundle);
        Assert.assertEquals(bundleJsonNoSubscriptions.getBundleId(), bundleId.toString());
        Assert.assertEquals(bundleJsonNoSubscriptions.getExternalKey(), externalKey);
        Assert.assertEquals(bundleJsonNoSubscriptions.getAccountId(), accountId.toString());
        Assert.assertNull(bundleJsonNoSubscriptions.getAuditLogs());
    }
}
