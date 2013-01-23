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

import com.ning.billing.ObjectType;
import com.ning.billing.mock.glue.MockDbHelperModule;
import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.bus.InMemoryBusModule;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.events.BusInternalEvent;
import com.ning.billing.util.events.TagInternalEvent;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.ClockModule;
import com.ning.billing.util.glue.NonEntityDaoModule;
import com.ning.billing.util.glue.TagStoreModule;
import com.ning.billing.util.svcsapi.bus.InternalBus;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

import static org.testng.Assert.assertEquals;

@Guice(modules = {TagStoreModule.class, CacheModule.class, ClockModule.class, InMemoryBusModule.class, MockDbHelperModule.class, NonEntityDaoModule.class})
public class TestDefaultTagDao extends UtilTestSuiteWithEmbeddedDB {

    @Inject
    private TagDefinitionDao tagDefinitionDao;

    @Inject
    private DefaultTagDao tagDao;

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
    public void testGetByIds() throws TagDefinitionApiException {
        final List<UUID> uuids = new ArrayList<UUID>();

        // Check with a empty Collection first
        List<TagDefinitionModelDao> result = tagDefinitionDao.getByIds(uuids, internalCallContext);
        assertEquals(result.size(), 0);

        final TagDefinitionModelDao defYo = tagDefinitionDao.create(UUID.randomUUID().toString().substring(0, 5), "defintion yo", internalCallContext);
        uuids.add(defYo.getId());
        final TagDefinitionModelDao defBah = tagDefinitionDao.create(UUID.randomUUID().toString().substring(0, 5), "defintion bah", internalCallContext);
        uuids.add(defBah.getId());
        final TagDefinitionModelDao defZoo = tagDefinitionDao.create(UUID.randomUUID().toString().substring(0, 5), "defintion zoo", internalCallContext);
        uuids.add(defZoo.getId());

        result = tagDefinitionDao.getByIds(uuids, internalCallContext);
        assertEquals(result.size(), 3);

        // Add control tag and retry
        uuids.add(ControlTagType.AUTO_PAY_OFF.getId());
        result = tagDefinitionDao.getByIds(uuids, internalCallContext);
        assertEquals(result.size(), 4);

        result = tagDefinitionDao.getTagDefinitions(internalCallContext);
        assertEquals(result.size(), 3 + ControlTagType.values().length);
    }

    @Test(groups = "slow")
    public void testGetById() throws TagDefinitionApiException {
        // User Tag
        final TagDefinitionModelDao defYo = tagDefinitionDao.create(UUID.randomUUID().toString().substring(0, 5), "defintion yo", internalCallContext);
        final TagDefinitionModelDao resDefYo = tagDefinitionDao.getById(defYo.getId(), internalCallContext);
        assertEquals(defYo, resDefYo);

        // Control Tag
        try {
            tagDefinitionDao.create(ControlTagType.AUTO_INVOICING_OFF.name(), ControlTagType.AUTO_INVOICING_OFF.name(), internalCallContext);
            Assert.fail("Should not be able to create a control tag");
        } catch (TagDefinitionApiException ignore) {
        }
        final TagDefinitionModelDao resdef_AUTO_INVOICING_OFF = tagDefinitionDao.getById(ControlTagType.AUTO_INVOICING_OFF.getId(), internalCallContext);
        assertEquals(resdef_AUTO_INVOICING_OFF.getId(), ControlTagType.AUTO_INVOICING_OFF.getId());
        assertEquals(resdef_AUTO_INVOICING_OFF.getName(), ControlTagType.AUTO_INVOICING_OFF.name());
        assertEquals(resdef_AUTO_INVOICING_OFF.getDescription(), ControlTagType.AUTO_INVOICING_OFF.getDescription());
    }

    @Test(groups = "slow")
    public void testGetByName() throws TagDefinitionApiException {
        // User Tag
        final TagDefinitionModelDao defYo = tagDefinitionDao.create(UUID.randomUUID().toString().substring(0, 5), "defintion yo", internalCallContext);
        final TagDefinitionModelDao resDefYo = tagDefinitionDao.getByName(defYo.getName(), internalCallContext);
        assertEquals(defYo, resDefYo);

        // Control Tag
        try {
            tagDefinitionDao.create(ControlTagType.AUTO_PAY_OFF.name(), ControlTagType.AUTO_INVOICING_OFF.name(), internalCallContext);
            Assert.fail("Should not be able to create a control tag");
        } catch (TagDefinitionApiException ignore) {
        }
        final TagDefinitionModelDao resdef_AUTO_PAY_OFF = tagDefinitionDao.getByName(ControlTagType.AUTO_PAY_OFF.name(), internalCallContext);
        assertEquals(resdef_AUTO_PAY_OFF.getId(), ControlTagType.AUTO_PAY_OFF.getId());
        assertEquals(resdef_AUTO_PAY_OFF.getName(), ControlTagType.AUTO_PAY_OFF.name());
        assertEquals(resdef_AUTO_PAY_OFF.getDescription(), ControlTagType.AUTO_PAY_OFF.getDescription());
    }

    @Test(groups = "slow")
    public void testCatchEventsOnCreateAndDelete() throws Exception {
        final String definitionName = UUID.randomUUID().toString().substring(0, 5);
        final String description = UUID.randomUUID().toString().substring(0, 5);
        final UUID objectId = UUID.randomUUID();
        final ObjectType objectType = ObjectType.INVOICE_ITEM;

        // Verify the initial state
        Assert.assertEquals(eventsListener.getEvents().size(), 0);
        Assert.assertEquals(eventsListener.getTagEvents().size(), 0);

        // Create a tag definition
        final TagDefinitionModelDao createdTagDefinition = tagDefinitionDao.create(definitionName, description, internalCallContext);
        Assert.assertEquals(createdTagDefinition.getName(), definitionName);
        Assert.assertEquals(createdTagDefinition.getDescription(), description);

        // Make sure we can create a tag
        final Tag tag = new DescriptiveTag(createdTagDefinition.getId(), objectType, objectId, internalCallContext.getCreatedDate());
        tagDao.create(new TagModelDao(tag), internalCallContext);

        // Make sure we can retrieve it via the DAO
        final List<TagModelDao> foundTags = tagDao.getTags(objectId, objectType, internalCallContext);
        Assert.assertEquals(foundTags.size(), 1);
        Assert.assertEquals(foundTags.get(0).getTagDefinitionId(), createdTagDefinition.getId());

        // Verify we caught an event on the bus -  we got 2 total (one for the tag definition, one for the tag)
        Assert.assertEquals(eventsListener.getEvents().size(), 2);
        Assert.assertEquals(eventsListener.getTagEvents().size(), 1);
        final TagInternalEvent tagFirstEventReceived = eventsListener.getTagEvents().get(0);
        Assert.assertEquals(eventsListener.getEvents().get(1), tagFirstEventReceived);
        Assert.assertEquals(tagFirstEventReceived.getObjectId(), objectId);
        Assert.assertEquals(tagFirstEventReceived.getObjectType(), objectType);
        Assert.assertEquals(tagFirstEventReceived.getTagDefinition().getName(), createdTagDefinition.getName());
        Assert.assertEquals(tagFirstEventReceived.getTagDefinition().getDescription(), createdTagDefinition.getDescription());
        Assert.assertEquals(tagFirstEventReceived.getBusEventType(), BusInternalEvent.BusInternalEventType.USER_TAG_CREATION);
        Assert.assertEquals(tagFirstEventReceived.getUserToken(), internalCallContext.getUserToken());

        // Delete the tag
        tagDao.deleteTag(objectId, objectType, createdTagDefinition.getId(), internalCallContext);

        // Make sure the tag is deleted
        Assert.assertEquals(tagDao.getTags(objectId, objectType, internalCallContext).size(), 0);

        // Verify we caught an event on the bus
        Assert.assertEquals(eventsListener.getEvents().size(), 3);
        Assert.assertEquals(eventsListener.getTagEvents().size(), 2);
        final TagInternalEvent tagSecondEventReceived = eventsListener.getTagEvents().get(1);
        Assert.assertEquals(eventsListener.getEvents().get(2), tagSecondEventReceived);
        Assert.assertEquals(tagSecondEventReceived.getObjectId(), objectId);
        Assert.assertEquals(tagSecondEventReceived.getObjectType(), objectType);
        Assert.assertEquals(tagSecondEventReceived.getTagDefinition().getName(), createdTagDefinition.getName());
        Assert.assertEquals(tagSecondEventReceived.getTagDefinition().getDescription(), createdTagDefinition.getDescription());
        Assert.assertEquals(tagSecondEventReceived.getBusEventType(), BusInternalEvent.BusInternalEventType.USER_TAG_DELETION);
        Assert.assertEquals(tagSecondEventReceived.getUserToken(), internalCallContext.getUserToken());
    }

    private static final class EventsListener {

        private final List<BusInternalEvent> events = new ArrayList<BusInternalEvent>();
        private final List<TagInternalEvent> tagEvents = new ArrayList<TagInternalEvent>();

        @Subscribe
        public synchronized void processEvent(final BusInternalEvent event) {
            events.add(event);
        }

        @Subscribe
        public synchronized void processTagDefinitionEvent(final TagInternalEvent tagEvent) {
            tagEvents.add(tagEvent);
        }

        public List<BusInternalEvent> getEvents() {
            return events;
        }

        public List<TagInternalEvent> getTagEvents() {
            return tagEvents;
        }
    }
}
