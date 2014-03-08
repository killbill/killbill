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

package org.killbill.billing.util.customfield.api;

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.ObjectType;
import org.killbill.billing.events.BusInternalEvent.BusInternalEventType;
import org.killbill.billing.util.jackson.ObjectMapper;

public class TestDefaultCustomFieldDeletionEvent {


    @Test(groups = "fast")
    public void testPojo() throws Exception {
        final UUID customFieldId = UUID.randomUUID();
        final UUID objectId = UUID.randomUUID();
        final ObjectType objectType = ObjectType.ACCOUNT_EMAIL;
        final UUID userToken = UUID.randomUUID();

        final DefaultCustomFieldDeletionEvent event = new DefaultCustomFieldDeletionEvent(customFieldId, objectId, objectType, 1L, 2L, UUID.randomUUID());
        Assert.assertEquals(event.getBusEventType(), BusInternalEventType.CUSTOM_FIELD_DELETION);

        Assert.assertEquals(event.getObjectId(), objectId);
        Assert.assertEquals(event.getObjectType(), objectType);

        Assert.assertEquals(event, event);
        Assert.assertEquals(event, new DefaultCustomFieldDeletionEvent(customFieldId, objectId, objectType, 1L, 2L, UUID.randomUUID()));
    }

    @Test(groups = "fast")
    public void testSerialization() throws Exception {

        final ObjectMapper objectMapper = new ObjectMapper();

        final UUID customFieldId = UUID.randomUUID();
        final UUID objectId = UUID.randomUUID();
        final ObjectType objectType = ObjectType.ACCOUNT_EMAIL;
        final UUID userToken = UUID.randomUUID();

        final DefaultCustomFieldDeletionEvent event = new DefaultCustomFieldDeletionEvent(customFieldId, objectId, objectType, 1L, 2L, UUID.randomUUID());

        final String json = objectMapper.writeValueAsString(event);
        final DefaultCustomFieldDeletionEvent fromJson = objectMapper.readValue(json, DefaultCustomFieldDeletionEvent.class);
        Assert.assertEquals(fromJson, event);
    }
}
