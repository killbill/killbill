/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.analytics;

import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.Subscription;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestBusinessSubscriptionEvent
{
    private Product product;
    private Plan plan;
    private PlanPhase phase;
    private Subscription isubscription;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception
    {
        product = new MockProduct("platinium", "subscription", ProductCategory.BASE);
        plan = new MockPlan("platinum-monthly", product);
        phase = new MockPhase(PhaseType.EVERGREEN, plan, MockDuration.UNLIMITED(), 25.95);
        isubscription = new MockSubscription(Subscription.SubscriptionState.ACTIVE, plan, phase);
    }

    @Test(groups = "fast")
    public void testValueOf() throws Exception
    {
        BusinessSubscriptionEvent event;

        event = BusinessSubscriptionEvent.valueOf("ADD_ADD_ON");
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.ADD);
        Assert.assertEquals(event.getCategory(), ProductCategory.ADD_ON);

        event = BusinessSubscriptionEvent.valueOf("CANCEL_BASE");
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.CANCEL);
        Assert.assertEquals(event.getCategory(), ProductCategory.BASE);

        event = BusinessSubscriptionEvent.valueOf("PAUSE_MISC");
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.PAUSE);
        Assert.assertNull(event.getCategory());

        event = BusinessSubscriptionEvent.valueOf("SYSTEM_CANCEL_ADD_ON");
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.SYSTEM_CANCEL);
        Assert.assertEquals(event.getCategory(), ProductCategory.ADD_ON);
    }

    @Test(groups = "fast")
    public void testFromISubscription() throws Exception
    {
        BusinessSubscriptionEvent event;

        event = BusinessSubscriptionEvent.subscriptionCreated(isubscription.getCurrentPlan());
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.ADD);
        Assert.assertEquals(event.getCategory(), product.getCategory());
        Assert.assertEquals(event.toString(), "ADD_BASE");

        event = BusinessSubscriptionEvent.subscriptionCancelled(isubscription.getCurrentPlan());
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.CANCEL);
        Assert.assertEquals(event.getCategory(), product.getCategory());
        Assert.assertEquals(event.toString(), "CANCEL_BASE");

        event = BusinessSubscriptionEvent.subscriptionChanged(isubscription.getCurrentPlan());
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.CHANGE);
        Assert.assertEquals(event.getCategory(), product.getCategory());
        Assert.assertEquals(event.toString(), "CHANGE_BASE");

        event = BusinessSubscriptionEvent.subscriptionPaused(isubscription.getCurrentPlan());
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.PAUSE);
        Assert.assertEquals(event.getCategory(), product.getCategory());
        Assert.assertEquals(event.toString(), "PAUSE_BASE");

        event = BusinessSubscriptionEvent.subscriptionResumed(isubscription.getCurrentPlan());
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.RESUME);
        Assert.assertEquals(event.getCategory(), product.getCategory());
        Assert.assertEquals(event.toString(), "RESUME_BASE");

        event = BusinessSubscriptionEvent.subscriptionPhaseChanged(isubscription.getCurrentPlan(), isubscription.getState());
        // The subscription is still active, it's a system change
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.SYSTEM_CHANGE);
        Assert.assertEquals(event.getCategory(), product.getCategory());
        Assert.assertEquals(event.toString(), "SYSTEM_CHANGE_BASE");

        isubscription = new MockSubscription(Subscription.SubscriptionState.CANCELLED, plan, phase);
        event = BusinessSubscriptionEvent.subscriptionPhaseChanged(isubscription.getCurrentPlan(), isubscription.getState());
        // The subscription is cancelled, it's a system cancellation
        Assert.assertEquals(event.getEventType(), BusinessSubscriptionEvent.EventType.SYSTEM_CANCEL);
        Assert.assertEquals(event.getCategory(), product.getCategory());
        Assert.assertEquals(event.toString(), "SYSTEM_CANCEL_BASE");
    }

    @Test(groups = "fast")
    public void testEquals() throws Exception
    {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionChanged(isubscription.getCurrentPlan());
        Assert.assertSame(event, event);
        Assert.assertEquals(event, event);
        Assert.assertTrue(event.equals(event));

        final BusinessSubscriptionEvent otherEvent = BusinessSubscriptionEvent.subscriptionPaused(isubscription.getCurrentPlan());
        Assert.assertTrue(!event.equals(otherEvent));
    }
}
