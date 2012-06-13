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
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.BusEvent;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.tag.MockTagStoreModuleSql;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.TestTagStore;
import com.ning.billing.util.tag.api.TagEvent;

@Guice(modules = MockTagStoreModuleSql.class)
public class TestAuditedTagDao {
    @Inject
    private MysqlTestingHelper helper;

    @Inject
    private TagDefinitionDao tagDefinitionDao;

    @Inject
    private AuditedTagDao tagDao;

    @Inject
    private Clock clock;

    @Inject
    private Bus bus;

    private CallContext context;
    private EventsListener eventsListener;

    @BeforeClass(groups = "slow")
    public void setup() throws IOException {
        final String utilDdl = IOUtils.toString(TestTagStore.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));

        helper.startMysql();
        helper.initDb(utilDdl);

        context = new DefaultCallContextFactory(clock).createCallContext("Tag DAO test", CallOrigin.TEST, UserType.TEST, UUID.randomUUID());
        bus.start();
    }

    @BeforeMethod(groups = "slow")
    public void cleanup() throws Bus.EventBusException {
        eventsListener = new EventsListener();
        bus.register(eventsListener);
    }

    @AfterClass(groups = "slow")
    public void stopMysql() {
        bus.stop();
        helper.stopMysql();
    }

    @Test(groups = "slow")
    public void testCatchEventsOnCreateAndDelete() throws Exception {
        final String definitionName = UUID.randomUUID().toString().substring(0, 5);
        final String description = UUID.randomUUID().toString().substring(0, 5);
        final UUID objectId = UUID.randomUUID();
        final ObjectType objectType = ObjectType.RECURRING_INVOICE_ITEM;

        // Verify the initial state
        Assert.assertEquals(eventsListener.getEvents().size(), 0);
        Assert.assertEquals(eventsListener.getTagEvents().size(), 0);

        // Create a tag definition
        final TagDefinition createdTagDefinition = tagDefinitionDao.create(definitionName, description, context);
        Assert.assertEquals(createdTagDefinition.getName(), definitionName);
        Assert.assertEquals(createdTagDefinition.getDescription(), description);

        // Make sure we can create a tag
        tagDao.insertTag(objectId, objectType, createdTagDefinition, context);

        // Make sure we can retrieve it via the DAO
        final Map<String, Tag> foundTags = tagDao.loadEntities(objectId, objectType);
        Assert.assertEquals(foundTags.keySet().size(), 1);
        Assert.assertEquals(foundTags.get(definitionName).getTagDefinitionName(), definitionName);

        // Verify we caught an event on the bus -  we got 2 total (one for the tag definition, one for the tag)
        Assert.assertEquals(eventsListener.getEvents().size(), 2);
        Assert.assertEquals(eventsListener.getTagEvents().size(), 1);
        final TagEvent tagFirstEventReceived = eventsListener.getTagEvents().get(0);
        Assert.assertEquals(eventsListener.getEvents().get(1), tagFirstEventReceived);
        Assert.assertEquals(tagFirstEventReceived.getObjectId(), objectId);
        Assert.assertEquals(tagFirstEventReceived.getObjectType(), objectType);
        Assert.assertEquals(tagFirstEventReceived.getTagDefinition().getName(), createdTagDefinition.getName());
        Assert.assertEquals(tagFirstEventReceived.getTagDefinition().getDescription(), createdTagDefinition.getDescription());
        Assert.assertEquals(tagFirstEventReceived.getBusEventType(), BusEvent.BusEventType.USER_TAG_CREATION);
        Assert.assertEquals(tagFirstEventReceived.getUserToken(), context.getUserToken());

        // Delete the tag
        tagDao.deleteTag(objectId, objectType, createdTagDefinition, context);

        // Make sure the tag is deleted
        Assert.assertEquals(tagDao.loadEntities(objectId, objectType).keySet().size(), 0);

        // Verify we caught an event on the bus
        Assert.assertEquals(eventsListener.getEvents().size(), 3);
        Assert.assertEquals(eventsListener.getTagEvents().size(), 2);
        final TagEvent tagSecondEventReceived = eventsListener.getTagEvents().get(1);
        Assert.assertEquals(eventsListener.getEvents().get(2), tagSecondEventReceived);
        Assert.assertEquals(tagSecondEventReceived.getObjectId(), objectId);
        Assert.assertEquals(tagSecondEventReceived.getObjectType(), objectType);
        Assert.assertEquals(tagSecondEventReceived.getTagDefinition().getName(), createdTagDefinition.getName());
        Assert.assertEquals(tagSecondEventReceived.getTagDefinition().getDescription(), createdTagDefinition.getDescription());
        Assert.assertEquals(tagSecondEventReceived.getBusEventType(), BusEvent.BusEventType.USER_TAG_DELETION);
        Assert.assertEquals(tagSecondEventReceived.getUserToken(), context.getUserToken());
    }

    private static final class EventsListener {
        private final List<BusEvent> events = new ArrayList<BusEvent>();
        private final List<TagEvent> tagEvents = new ArrayList<TagEvent>();

        @Subscribe
        public synchronized void processEvent(final BusEvent event) {
            events.add(event);
        }

        @Subscribe
        public synchronized void processTagDefinitionEvent(final TagEvent tagEvent) {
            tagEvents.add(tagEvent);
        }

        public List<BusEvent> getEvents() {
            return events;
        }

        public List<TagEvent> getTagEvents() {
            return tagEvents;
        }
    }
}
