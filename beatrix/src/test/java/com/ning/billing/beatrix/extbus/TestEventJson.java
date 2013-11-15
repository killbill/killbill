/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.beatrix.extbus;

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.beatrix.BeatrixTestSuite;
import com.ning.billing.notification.plugin.api.ExtBusEventType;
import com.ning.billing.util.jackson.ObjectMapper;

public class TestEventJson extends BeatrixTestSuite {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast")
    public void testBusExternalEvent() throws Exception {
        final UUID objectId = UUID.randomUUID();
        final UUID userToken = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final UUID tenantId = UUID.randomUUID();
        final ObjectType objectType = ObjectType.ACCOUNT;
        final ExtBusEventType extBusEventType = ExtBusEventType.ACCOUNT_CREATION;

        final DefaultBusExternalEvent e = new DefaultBusExternalEvent(objectId, objectType, extBusEventType, accountId, tenantId, 1L, 2L, UUID.randomUUID());
        final String json = mapper.writeValueAsString(e);

        final Class<?> claz = Class.forName(DefaultBusExternalEvent.class.getName());
        final Object obj = mapper.readValue(json, claz);
        Assert.assertTrue(obj.equals(e));
    }
}
