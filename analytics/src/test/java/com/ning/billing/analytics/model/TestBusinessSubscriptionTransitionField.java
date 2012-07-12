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

public class TestBusinessSubscriptionTransitionField extends AnalyticsTestSuite {
    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final String accountKey = UUID.randomUUID().toString();
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = UUID.randomUUID().toString();
        final String name = UUID.randomUUID().toString();
        final String value = UUID.randomUUID().toString();
        final BusinessSubscriptionTransitionField subscriptionTransitionField = new BusinessSubscriptionTransitionField(accountKey,
                                                                                                                        bundleId,
                                                                                                                        externalKey,
                                                                                                                        name,
                                                                                                                        value);
        Assert.assertSame(subscriptionTransitionField, subscriptionTransitionField);
        Assert.assertEquals(subscriptionTransitionField, subscriptionTransitionField);
        Assert.assertTrue(subscriptionTransitionField.equals(subscriptionTransitionField));
        Assert.assertEquals(subscriptionTransitionField.getAccountKey(), accountKey);
        Assert.assertEquals(subscriptionTransitionField.getBundleId(), bundleId);
        Assert.assertEquals(subscriptionTransitionField.getExternalKey(), externalKey);
        Assert.assertEquals(subscriptionTransitionField.getName(), name);
        Assert.assertEquals(subscriptionTransitionField.getValue(), value);

        final BusinessSubscriptionTransitionField otherSubscriptionField = new BusinessSubscriptionTransitionField(UUID.randomUUID().toString(),
                                                                                                                   UUID.randomUUID(),
                                                                                                                   UUID.randomUUID().toString(),
                                                                                                                   UUID.randomUUID().toString(),
                                                                                                                   UUID.randomUUID().toString());
        Assert.assertFalse(subscriptionTransitionField.equals(otherSubscriptionField));
    }
}
