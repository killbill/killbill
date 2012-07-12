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

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.BusEvent;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.io.IOUtils;
import com.ning.billing.util.tag.MockTagStoreModuleSql;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.TestTagStore;
import com.ning.billing.util.tag.api.TagDefinitionEvent;

@Guice(modules = MockTagStoreModuleSql.class)
public class TestDefaultTagDefinitionDao extends UtilTestSuiteWithEmbeddedDB {
    @Inject
    private MysqlTestingHelper helper;

    @Inject
    private TagDefinitionDao tagDefinitionDao;

    @Inject
    private Clock clock;

    @Inject
    private Bus bus;

    private CallContext context;
    private EventsListener eventsListener;

    @BeforeClass(groups = "slow")
    public void setup() throws IOException {
        context = new DefaultCallContextFactory(clock).createCallContext("TagDefinition DAO test", CallOrigin.TEST, UserType.TEST, UUID.randomUUID());
        bus.start();
    }

    @BeforeMethod(groups = "slow")
    public void cleanupBeforeMethod() throws Bus.EventBusException {
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
        final TagDefinition createdTagDefinition = tagDefinitionDao.create(definitionName, description, context);
        Assert.assertEquals(createdTagDefinition.getName(), definitionName);
        Assert.assertEquals(createdTagDefinition.getDescription(), description);

        // Make sure we can retrieve it via the DAO
        final TagDefinition foundTagDefinition = tagDefinitionDao.getByName(definitionName);
        Assert.assertEquals(foundTagDefinition, createdTagDefinition);

        // Verify we caught an event on the bus
        Assert.assertEquals(eventsListener.getEvents().size(), 1);
        Assert.assertEquals(eventsListener.getTagDefinitionEvents().size(), 1);
        final TagDefinitionEvent tagDefinitionFirstEventReceived = eventsListener.getTagDefinitionEvents().get(0);
        Assert.assertEquals(eventsListener.getEvents().get(0), tagDefinitionFirstEventReceived);
        Assert.assertEquals(tagDefinitionFirstEventReceived.getTagDefinitionId(), createdTagDefinition.getId());
        Assert.assertEquals(tagDefinitionFirstEventReceived.getTagDefinition(), createdTagDefinition);
        Assert.assertEquals(tagDefinitionFirstEventReceived.getBusEventType(), BusEvent.BusEventType.USER_TAGDEFINITION_CREATION);
        Assert.assertEquals(tagDefinitionFirstEventReceived.getUserToken(), context.getUserToken());

        // Delete the tag definition
        tagDefinitionDao.deleteTagDefinition(definitionName, context);

        // Make sure the tag definition is deleted
        Assert.assertNull(tagDefinitionDao.getByName(definitionName));

        // Verify we caught an event on the bus
        Assert.assertEquals(eventsListener.getEvents().size(), 2);
        Assert.assertEquals(eventsListener.getTagDefinitionEvents().size(), 2);
        final TagDefinitionEvent tagDefinitionSecondEventReceived = eventsListener.getTagDefinitionEvents().get(1);
        Assert.assertEquals(eventsListener.getEvents().get(1), tagDefinitionSecondEventReceived);
        Assert.assertEquals(tagDefinitionSecondEventReceived.getTagDefinitionId(), createdTagDefinition.getId());
        Assert.assertEquals(tagDefinitionSecondEventReceived.getTagDefinition(), createdTagDefinition);
        Assert.assertEquals(tagDefinitionSecondEventReceived.getBusEventType(), BusEvent.BusEventType.USER_TAGDEFINITION_DELETION);
        Assert.assertEquals(tagDefinitionSecondEventReceived.getUserToken(), context.getUserToken());
    }

    private static final class EventsListener {
        private final List<BusEvent> events = new ArrayList<BusEvent>();
        private final List<TagDefinitionEvent> tagDefinitionEvents = new ArrayList<TagDefinitionEvent>();

        @Subscribe
        public synchronized void processEvent(final BusEvent event) {
            events.add(event);
        }

        @Subscribe
        public synchronized void processTagDefinitionEvent(final TagDefinitionEvent tagDefinitionEvent) {
            tagDefinitionEvents.add(tagDefinitionEvent);
        }

        public List<BusEvent> getEvents() {
            return events;
        }

        public List<TagDefinitionEvent> getTagDefinitionEvents() {
            return tagDefinitionEvents;
        }
    }
}
