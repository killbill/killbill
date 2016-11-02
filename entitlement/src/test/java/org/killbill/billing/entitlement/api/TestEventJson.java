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

package org.killbill.billing.entitlement.api;

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.entitlement.EntitlementTestSuiteNoDB;
import org.killbill.billing.events.BlockingTransitionInternalEvent;
import org.killbill.billing.util.jackson.ObjectMapper;

public class TestEventJson extends EntitlementTestSuiteNoDB {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast", description = "Test Blocking event deserialization")
    public void testDefaultBlockingTransitionInternalEvent() throws Exception {
        final BlockingTransitionInternalEvent e = new DefaultBlockingTransitionInternalEvent(UUID.randomUUID(), "state", "svc",
                                                                                             clock.getUTCNow(), BlockingStateType.ACCOUNT,
                                                                                             true, false, false, true, 1L, 2L, null);

        final String json = mapper.writeValueAsString(e);

        final Class<?> claz = Class.forName("org.killbill.billing.entitlement.api.DefaultBlockingTransitionInternalEvent");
        final Object obj = mapper.readValue(json, claz);
        Assert.assertTrue(obj.equals(e));
    }

}
