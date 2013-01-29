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

package com.ning.billing.entitlement.api;

import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.GuicyKillbillTestSuiteNoDB;
import com.ning.billing.api.TestListenerStatus;
import com.ning.billing.entitlement.api.timeline.DefaultRepairEntitlementEvent;
import com.ning.billing.entitlement.api.user.DefaultEffectiveSubscriptionEvent;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;
import com.ning.billing.util.events.RepairEntitlementInternalEvent;
import com.ning.billing.util.jackson.ObjectMapper;

public class TestEventJson extends GuicyKillbillTestSuiteNoDB  {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast")
    public void testSubscriptionEvent() throws Exception {

        final EffectiveSubscriptionInternalEvent e = new DefaultEffectiveSubscriptionEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new DateTime(), new DateTime(),
                                                                                   SubscriptionState.ACTIVE, "pro", "TRIAL", "DEFAULT", SubscriptionState.CANCELLED, null, null, null, 3L,
                                                                                   UUID.randomUUID(), SubscriptionTransitionType.CANCEL, 0, new DateTime(), 1L, 1L);

        final String json = mapper.writeValueAsString(e);

        final Class<?> claz = Class.forName(DefaultEffectiveSubscriptionEvent.class.getName());
        final Object obj = mapper.readValue(json, claz);
        Assert.assertTrue(obj.equals(e));
    }

    @Test(groups = "fast")
    public void testRepairEntitlementEvent() throws Exception {
        final RepairEntitlementInternalEvent e = new DefaultRepairEntitlementEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new DateTime(), 1L, 1L);

        final String json = mapper.writeValueAsString(e);

        final Class<?> claz = Class.forName(DefaultRepairEntitlementEvent.class.getName());
        final Object obj = mapper.readValue(json, claz);
        Assert.assertTrue(obj.equals(e));
    }
}
