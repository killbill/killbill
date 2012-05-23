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

import com.ning.billing.util.jackson.ObjectMapper;
import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.entitlement.api.timeline.DefaultRepairEntitlementEvent;
import com.ning.billing.entitlement.api.timeline.RepairEntitlementEvent;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionEvent;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.SubscriptionEvent;

public class TestEventJson {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test(groups= {"fast"})
    public void testSubscriptionEvent() throws Exception {
        

        SubscriptionEvent e = new DefaultSubscriptionEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new DateTime(), new DateTime(),
                SubscriptionState.ACTIVE, "pro", "TRIAL", "DEFAULT", SubscriptionState.CANCELLED, null, null, null, 3L, UUID.randomUUID(), SubscriptionTransitionType.CANCEL, 0, new DateTime());
            
        String json = mapper.writeValueAsString(e);

        Class<?> claz = Class.forName(DefaultSubscriptionEvent.class.getName());
        Object obj =  mapper.readValue(json, claz);
        Assert.assertTrue(obj.equals(e));

    }
    
    @Test(groups= {"fast"})
    public void testRepairEntitlementEvent() throws Exception {
        RepairEntitlementEvent e = new DefaultRepairEntitlementEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new DateTime());
        
        String json = mapper.writeValueAsString(e);

        Class<?> claz = Class.forName(DefaultRepairEntitlementEvent.class.getName());
        Object obj =  mapper.readValue(json, claz);
        Assert.assertTrue(obj.equals(e));
    }
        

    
    
}
