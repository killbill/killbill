/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.subscription.SubscriptionTestSuiteNoDB;
import org.killbill.billing.subscription.api.user.DefaultEffectiveSubscriptionEvent;
import org.killbill.billing.util.jackson.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestEventJson extends SubscriptionTestSuiteNoDB {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast")
    public void testSubscriptionEvent() throws Exception {

        final EffectiveSubscriptionInternalEvent e = new DefaultEffectiveSubscriptionEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, new DateTime(),
                                                                                           EntitlementState.ACTIVE, "pro", "TRIAL", "DEFAULT", null, EntitlementState.CANCELLED, null, null, null, null, 3L,
                                                                                           SubscriptionBaseTransitionType.CANCEL, 0, new DateTime(), 1L, 2L, null);

        final String json = mapper.writeValueAsString(e);

        final Class<?> claz = Class.forName(DefaultEffectiveSubscriptionEvent.class.getName());
        final Object obj = mapper.readValue(json, claz);
        Assert.assertTrue(obj.equals(e));
    }

    // Verify deserialization will work when we miss fields (previousBillCycleDayLocal, nextBillCycleDayLocal)
    @Test(groups = "fast")
    public void testSubscriptionEventWithNoBillCycleDayLocal() throws Exception {

        final String json = "{\"eventId\":\"9e901bbc-bbcb-4f0a-8511-e58029bbea91\",\"subscriptionId\":\"c373056c-bb0c-4562-ab06-f595176aa4ae\",\"bundleId\":\"f61536b1-fc76-4337-b1e8-e38383894352\",\"effectiveTransitionTime\":\"2016-05-26T23:02:20.322Z\",\"previousState\":\"ACTIVE\",\"previousPlan\":\"pro\",\"previousPhase\":\"TRIAL\",\"previousPriceList\":\"DEFAULT\",\"nextState\":\"CANCELLED\",\"nextPlan\":null,\"nextPhase\":null,\"nextPriceList\":null,\"totalOrdering\":3,\"transitionType\":\"CANCEL\",\"remainingEventsForUserOperation\":0,\"startDate\":\"2016-05-26T23:02:20.322Z\",\"searchKey1\":1,\"searchKey2\":2,\"userToken\":null,\"requestedTransitionTime\":\"2016-05-26T23:02:20.322Z\"}";
        final Class<?> claz = Class.forName(DefaultEffectiveSubscriptionEvent.class.getName());
        final DefaultEffectiveSubscriptionEvent obj = (DefaultEffectiveSubscriptionEvent) mapper.readValue(json, claz);

        Assert.assertEquals(obj.getId(), UUID.fromString("9e901bbc-bbcb-4f0a-8511-e58029bbea91"));
        Assert.assertNull(obj.getPreviousBillCycleDayLocal());
        Assert.assertNull(obj.getNextBillCycleDayLocal());
    }
}
