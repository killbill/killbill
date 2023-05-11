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

import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;

import org.killbill.billing.events.BusEventBase;
import org.killbill.bus.api.BusEvent;
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

    @Test(groups = "fast")
    public void testEquality() {
        final UUID userToken = UUID.randomUUID();
        final UUID customFID = UUID.randomUUID();
        final UUID oid = UUID.randomUUID();
        final ObjectType objType = ObjectType.ACCOUNT_EMAIL;

        final BusEventBase eventBase = new BusEventBase(1L, 2L, userToken);

        final DefaultCustomFieldDeletionEvent evt1 = new DefaultCustomFieldDeletionEvent(customFID, oid, objType, 1L, 2L, userToken);

        // EQ_OVERRIDING_EQUALS_NOT_SYMMETRIC is false alarm here.
        Assert.assertNotEquals(eventBase, evt1);
        Assert.assertNotEquals(evt1, eventBase);

        // different value of customFieldId
        final DefaultCustomFieldDeletionEvent evt2 = new DefaultCustomFieldDeletionEvent(UUID.randomUUID(), oid, objType, 1L, 2L, userToken);

        Assert.assertNotEquals(evt1, evt2);
        Assert.assertNotEquals(evt2, evt1);

        // Same as evt1.
        final DefaultCustomFieldDeletionEvent evt3 = new DefaultCustomFieldDeletionEvent(customFID, oid, objType, 1L, 2L, userToken);
        Assert.assertEquals(evt1, evt3);
        Assert.assertEquals(evt3, evt1);

        // Same as evt1, different eventBase attributes
        final DefaultCustomFieldDeletionEvent evt4 = new DefaultCustomFieldDeletionEvent(customFID, oid, objType, 100L, 200L, UUID.randomUUID());
        Assert.assertEquals(evt1, evt4);
        Assert.assertEquals(evt4, evt1);
        //
        Assert.assertEquals(evt3, evt4);
        Assert.assertEquals(evt4, evt3);

        final Collection<BusEvent> events = new HashSet<>();
        events.add(eventBase);
        events.add(evt1);
        events.add(evt2);
        events.add(evt3);
        events.add(evt4);
        // We have 5 events. evt3 and evt4 are equals to evt1, hence we should have 3 events: eventBase, evt1, and evt2.
        Assert.assertEquals(events.size(), 3);
    }
}
