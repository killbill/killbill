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

package org.killbill.billing.subscription.api;

import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.GuicyKillbillTestSuiteNoDB;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.api.user.DefaultEffectiveSubscriptionEvent;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.util.jackson.ObjectMapper;

public class TestEventJson extends GuicyKillbillTestSuiteNoDB {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast")
    public void testSubscriptionEvent() throws Exception {

        final EffectiveSubscriptionInternalEvent e = new DefaultEffectiveSubscriptionEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new DateTime(), new DateTime(),
                                                                                           EntitlementState.ACTIVE, "pro", "TRIAL", "DEFAULT", EntitlementState.CANCELLED, null, null, null, 3L,
                                                                                           SubscriptionBaseTransitionType.CANCEL, 0, new DateTime(), 1L, 2L, null);

        final String json = mapper.writeValueAsString(e);

        final Class<?> claz = Class.forName(DefaultEffectiveSubscriptionEvent.class.getName());
        final Object obj = mapper.readValue(json, claz);
        Assert.assertTrue(obj.equals(e));
    }
}
