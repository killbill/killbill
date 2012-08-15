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

package com.ning.billing.util.audit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestDefaultAuditLogsForBundles extends AuditLogsTestBase {

    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final Map<UUID, List<AuditLog>> bundlesAuditLogs = createAuditLogsAssociation();
        final Map<UUID, List<AuditLog>> subscriptionsAuditLogs = createAuditLogsAssociation();
        final Map<UUID, List<AuditLog>> subscriptionEventsAuditLogs = createAuditLogsAssociation();
        Assert.assertEquals(new DefaultAuditLogsForBundles(bundlesAuditLogs, subscriptionsAuditLogs, subscriptionEventsAuditLogs).getBundlesAuditLogs(), bundlesAuditLogs);
        Assert.assertEquals(new DefaultAuditLogsForBundles(bundlesAuditLogs, subscriptionsAuditLogs, subscriptionEventsAuditLogs).getSubscriptionsAuditLogs(), subscriptionsAuditLogs);
        Assert.assertEquals(new DefaultAuditLogsForBundles(bundlesAuditLogs, subscriptionsAuditLogs, subscriptionEventsAuditLogs).getSubscriptionEventsAuditLogs(), subscriptionEventsAuditLogs);

        Assert.assertNotEquals(new DefaultAuditLogsForBundles(createAuditLogsAssociation(), subscriptionsAuditLogs, subscriptionEventsAuditLogs).getBundlesAuditLogs(), bundlesAuditLogs);
        Assert.assertEquals(new DefaultAuditLogsForBundles(createAuditLogsAssociation(), subscriptionsAuditLogs, subscriptionEventsAuditLogs).getSubscriptionsAuditLogs(), subscriptionsAuditLogs);
        Assert.assertEquals(new DefaultAuditLogsForBundles(createAuditLogsAssociation(), subscriptionsAuditLogs, subscriptionEventsAuditLogs).getSubscriptionEventsAuditLogs(), subscriptionEventsAuditLogs);

        Assert.assertEquals(new DefaultAuditLogsForBundles(bundlesAuditLogs, createAuditLogsAssociation(), subscriptionEventsAuditLogs).getBundlesAuditLogs(), bundlesAuditLogs);
        Assert.assertNotEquals(new DefaultAuditLogsForBundles(bundlesAuditLogs, createAuditLogsAssociation(), subscriptionEventsAuditLogs).getSubscriptionsAuditLogs(), subscriptionsAuditLogs);
        Assert.assertEquals(new DefaultAuditLogsForBundles(bundlesAuditLogs, createAuditLogsAssociation(), subscriptionEventsAuditLogs).getSubscriptionEventsAuditLogs(), subscriptionEventsAuditLogs);

        Assert.assertEquals(new DefaultAuditLogsForBundles(bundlesAuditLogs, subscriptionsAuditLogs, createAuditLogsAssociation()).getBundlesAuditLogs(), bundlesAuditLogs);
        Assert.assertEquals(new DefaultAuditLogsForBundles(bundlesAuditLogs, subscriptionsAuditLogs, createAuditLogsAssociation()).getSubscriptionsAuditLogs(), subscriptionsAuditLogs);
        Assert.assertNotEquals(new DefaultAuditLogsForBundles(bundlesAuditLogs, subscriptionsAuditLogs, createAuditLogsAssociation()).getSubscriptionEventsAuditLogs(), subscriptionEventsAuditLogs);
    }
}
