/*
 * Copyright 2010-2012 Ning, Inc.
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

package org.killbill.billing.util.tag.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.events.BusInternalEvent;
import org.killbill.billing.events.TagDefinitionInternalEvent;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.eventbus.Subscribe;

public class TestDefaultTagDefinitionDao extends UtilTestSuiteWithEmbeddedDB {


    @Test(groups = "slow")
    public void testCatchEventsOnCreateAndDelete() throws Exception {
        final String definitionName = UUID.randomUUID().toString().substring(0, 5);
        final String description = UUID.randomUUID().toString().substring(0, 5);

        // Make sure we can create a tag definition
        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        final TagDefinitionModelDao createdTagDefinition = tagDefinitionDao.create(definitionName, description, ObjectType.ACCOUNT.name(), internalCallContext);
        Assert.assertEquals(createdTagDefinition.getName(), definitionName);
        Assert.assertEquals(createdTagDefinition.getDescription(), description);
        Assert.assertEquals(createdTagDefinition.getApplicableObjectTypes(), ObjectType.ACCOUNT.name());
        assertListenerStatus();

        // Make sure we can retrieve it via the DAO
        final TagDefinitionModelDao foundTagDefinition = tagDefinitionDao.getByName(definitionName, internalCallContext);
        Assert.assertEquals(foundTagDefinition, createdTagDefinition);

        /*
        // Verify we caught an event on the bus
        final TagDefinitionInternalEvent tagDefinitionFirstEventReceived = eventsListener.getTagDefinitionEvents().get(0);
        Assert.assertEquals(tagDefinitionFirstEventReceived.getTagDefinitionId(), createdTagDefinition.getId());
        Assert.assertEquals(tagDefinitionFirstEventReceived.getTagDefinition().getName(), createdTagDefinition.getName());
        Assert.assertEquals(tagDefinitionFirstEventReceived.getTagDefinition().getDescription(), createdTagDefinition.getDescription());
        Assert.assertEquals(tagDefinitionFirstEventReceived.getBusEventType(), BusInternalEvent.BusInternalEventType.USER_TAGDEFINITION_CREATION);
        Assert.assertEquals(tagDefinitionFirstEventReceived.getUserToken(), internalCallContext.getUserToken());

        */
        // Delete the tag definition
        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        tagDefinitionDao.deleteById(foundTagDefinition.getId(), internalCallContext);
        assertListenerStatus();

        // Make sure the tag definition is deleted
        try {
            tagDefinitionDao.getByName(definitionName, internalCallContext);
            Assert.fail("Retrieving tag definition should fail");
        } catch (final TagDefinitionApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.TAG_DEFINITION_DOES_NOT_EXIST.getCode());
        }

        try {
            tagDefinitionDao.getById(UUID.randomUUID(), internalCallContext);
            Assert.fail("Retrieving random tag definition should fail");
        } catch (final TagDefinitionApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.TAG_DEFINITION_DOES_NOT_EXIST.getCode());
        }



        /*
        // Verify we caught an event on the bus
        final TagDefinitionInternalEvent tagDefinitionSecondEventReceived = eventsListener.getTagDefinitionEvents().get(1);
        Assert.assertEquals(tagDefinitionSecondEventReceived.getTagDefinitionId(), createdTagDefinition.getId());
        Assert.assertEquals(tagDefinitionSecondEventReceived.getTagDefinition().getName(), createdTagDefinition.getName());
        Assert.assertEquals(tagDefinitionSecondEventReceived.getTagDefinition().getDescription(), createdTagDefinition.getDescription());
        Assert.assertEquals(tagDefinitionSecondEventReceived.getBusEventType(), BusInternalEvent.BusInternalEventType.USER_TAGDEFINITION_DELETION);
        Assert.assertEquals(tagDefinitionSecondEventReceived.getUserToken(), internalCallContext.getUserToken());
        */
    }

    private static final class EventsListener {

        private final List<BusInternalEvent> events = new ArrayList<BusInternalEvent>();
        private final List<TagDefinitionInternalEvent> tagDefinitionEvents = new ArrayList<TagDefinitionInternalEvent>();

        @Subscribe
        public synchronized void processEvent(final BusInternalEvent event) {
            events.add(event);
        }

        @Subscribe
        public synchronized void processTagDefinitionEvent(final TagDefinitionInternalEvent event) {
            tagDefinitionEvents.add(event);
        }

        public List<BusInternalEvent> getEvents() {
            return events;
        }

        public List<TagDefinitionInternalEvent> getTagDefinitionEvents() {
            return tagDefinitionEvents;
        }
    }
}
