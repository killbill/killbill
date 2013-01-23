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

package com.ning.billing.util.tag.dao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.mock.glue.MockDbHelperModule;
import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;
import com.ning.billing.util.bus.InMemoryBusModule;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.events.BusInternalEvent;
import com.ning.billing.util.events.TagDefinitionInternalEvent;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.ClockModule;
import com.ning.billing.util.glue.NonEntityDaoModule;
import com.ning.billing.util.glue.TagStoreModule;
import com.ning.billing.util.svcsapi.bus.InternalBus;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

@Guice(modules = {TagStoreModule.class, CacheModule.class, ClockModule.class, InMemoryBusModule.class, MockDbHelperModule.class, NonEntityDaoModule.class})
public class TestDefaultTagDefinitionDao extends UtilTestSuiteWithEmbeddedDB {

    @Inject
    private TagDefinitionDao tagDefinitionDao;

    @Inject
    private Clock clock;

    @Inject
    private InternalBus bus;

    private EventsListener eventsListener;

    @BeforeClass(groups = "slow")
    public void setup() throws IOException {
        bus.start();
    }

    @BeforeMethod(groups = "slow")
    public void cleanupBeforeMethod() throws InternalBus.EventBusException {
        eventsListener = new EventsListener();
        bus.register(eventsListener);
    }

    @AfterClass(groups = "slow")
    public void tearDown() {
        bus.stop();
    }

    @Test(groups = "slow")
    public void testCatchEventsOnCreateAndDelete() throws Exception {
        final String definitionName = UUID.randomUUID().toString().substring(0, 5);
        final String description = UUID.randomUUID().toString().substring(0, 5);

        // Verify the initial state
        Assert.assertEquals(eventsListener.getEvents().size(), 0);
        Assert.assertEquals(eventsListener.getTagDefinitionEvents().size(), 0);

        // Make sure we can create a tag definition
        final TagDefinitionModelDao createdTagDefinition = tagDefinitionDao.create(definitionName, description, internalCallContext);
        Assert.assertEquals(createdTagDefinition.getName(), definitionName);
        Assert.assertEquals(createdTagDefinition.getDescription(), description);

        // Make sure we can retrieve it via the DAO
        final TagDefinitionModelDao foundTagDefinition = tagDefinitionDao.getByName(definitionName, internalCallContext);
        Assert.assertEquals(foundTagDefinition, createdTagDefinition);

        // Verify we caught an event on the bus
        Assert.assertEquals(eventsListener.getEvents().size(), 1);
        Assert.assertEquals(eventsListener.getTagDefinitionEvents().size(), 1);
        final TagDefinitionInternalEvent tagDefinitionFirstEventReceived = eventsListener.getTagDefinitionEvents().get(0);
        Assert.assertEquals(eventsListener.getEvents().get(0), tagDefinitionFirstEventReceived);
        Assert.assertEquals(tagDefinitionFirstEventReceived.getTagDefinitionId(), createdTagDefinition.getId());
        Assert.assertEquals(tagDefinitionFirstEventReceived.getTagDefinition().getName(), createdTagDefinition.getName());
        Assert.assertEquals(tagDefinitionFirstEventReceived.getTagDefinition().getDescription(), createdTagDefinition.getDescription());
        Assert.assertEquals(tagDefinitionFirstEventReceived.getBusEventType(), BusInternalEvent.BusInternalEventType.USER_TAGDEFINITION_CREATION);
        Assert.assertEquals(tagDefinitionFirstEventReceived.getUserToken(), internalCallContext.getUserToken());

        // Delete the tag definition
        tagDefinitionDao.deleteById(foundTagDefinition.getId(), internalCallContext);

        // Make sure the tag definition is deleted
        Assert.assertNull(tagDefinitionDao.getByName(definitionName, internalCallContext));

        // Verify we caught an event on the bus
        Assert.assertEquals(eventsListener.getEvents().size(), 2);
        Assert.assertEquals(eventsListener.getTagDefinitionEvents().size(), 2);
        final TagDefinitionInternalEvent tagDefinitionSecondEventReceived = eventsListener.getTagDefinitionEvents().get(1);
        Assert.assertEquals(eventsListener.getEvents().get(1), tagDefinitionSecondEventReceived);
        Assert.assertEquals(tagDefinitionSecondEventReceived.getTagDefinitionId(), createdTagDefinition.getId());
        Assert.assertEquals(tagDefinitionSecondEventReceived.getTagDefinition().getName(), createdTagDefinition.getName());
        Assert.assertEquals(tagDefinitionSecondEventReceived.getTagDefinition().getDescription(), createdTagDefinition.getDescription());
        Assert.assertEquals(tagDefinitionSecondEventReceived.getBusEventType(), BusInternalEvent.BusInternalEventType.USER_TAGDEFINITION_DELETION);
        Assert.assertEquals(tagDefinitionSecondEventReceived.getUserToken(), internalCallContext.getUserToken());
    }

    private static final class EventsListener {

        private final List<BusInternalEvent> events = new ArrayList<BusInternalEvent>();
        private final List<TagDefinitionInternalEvent> tagDefinitionEvents = new ArrayList<TagDefinitionInternalEvent>();

        @Subscribe
        public synchronized void processEvent(final BusInternalEvent event) {
            events.add(event);
        }

        @Subscribe
        public synchronized void processTagDefinitionEvent(final TagDefinitionInternalEvent tagDefinitionEvent) {
            tagDefinitionEvents.add(tagDefinitionEvent);
        }

        public List<BusInternalEvent> getEvents() {
            return events;
        }

        public List<TagDefinitionInternalEvent> getTagDefinitionEvents() {
            return tagDefinitionEvents;
        }
    }
}
