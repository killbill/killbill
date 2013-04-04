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

package com.ning.billing.osgi.bundles.analytics.dao.model;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteNoDB;

public class TestBusinessSubscriptionEvent extends AnalyticsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testValueOf() throws Exception {
        BusinessSubscriptionEvent event;

        event = BusinessSubscriptionEvent.valueOf("ADD_ADD_ON");
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.ADD);
        Assert.assertEquals(event.getCategory(), ProductCategory.ADD_ON);

        event = BusinessSubscriptionEvent.valueOf("CANCEL_BASE");
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.CANCEL);
        Assert.assertEquals(event.getCategory(), ProductCategory.BASE);

        event = BusinessSubscriptionEvent.valueOf("SYSTEM_CANCEL_ADD_ON");
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.SYSTEM_CANCEL);
        Assert.assertEquals(event.getCategory(), ProductCategory.ADD_ON);
    }

    @Test(groups = "fast")
    public void testFromSubscription() throws Exception {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.fromTransition(subscriptionTransition);
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.ADD);
        Assert.assertEquals(event.getCategory(), subscriptionTransition.getNextPlan().getProduct().getCategory());
        Assert.assertEquals(event.toString(), "ADD_BASE");
    }
}
