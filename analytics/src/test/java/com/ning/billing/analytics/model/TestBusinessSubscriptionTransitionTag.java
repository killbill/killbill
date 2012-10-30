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

package com.ning.billing.analytics.model;

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.analytics.AnalyticsTestSuite;

public class TestBusinessSubscriptionTransitionTag extends AnalyticsTestSuite {
    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final String accountKey = UUID.randomUUID().toString();
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = UUID.randomUUID().toString();
        final String name = UUID.randomUUID().toString();
        final BusinessSubscriptionTransitionTagModelDao subscriptionTransitionTag = new BusinessSubscriptionTransitionTagModelDao(accountKey,
                                                                                                                  bundleId,
                                                                                                                  externalKey,
                                                                                                                  name);
        Assert.assertSame(subscriptionTransitionTag, subscriptionTransitionTag);
        Assert.assertEquals(subscriptionTransitionTag, subscriptionTransitionTag);
        Assert.assertTrue(subscriptionTransitionTag.equals(subscriptionTransitionTag));
        Assert.assertEquals(subscriptionTransitionTag.getAccountKey(), accountKey);
        Assert.assertEquals(subscriptionTransitionTag.getBundleId(), bundleId);
        Assert.assertEquals(subscriptionTransitionTag.getExternalKey(), externalKey);
        Assert.assertEquals(subscriptionTransitionTag.getName(), name);

        final BusinessSubscriptionTransitionTagModelDao otherTransitionTag = new BusinessSubscriptionTransitionTagModelDao(UUID.randomUUID().toString(),
                                                                                                           UUID.randomUUID(),
                                                                                                           UUID.randomUUID().toString(),
                                                                                                           UUID.randomUUID().toString());
        Assert.assertFalse(subscriptionTransitionTag.equals(otherTransitionTag));
    }
}
