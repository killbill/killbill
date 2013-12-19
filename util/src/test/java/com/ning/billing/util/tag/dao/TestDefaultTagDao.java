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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;

import static org.testng.Assert.assertEquals;

public class TestDefaultTagDao extends UtilTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testGetByIds() throws TagDefinitionApiException {
        final List<UUID> uuids = new ArrayList<UUID>();

        // Check with a empty Collection first
        List<TagDefinitionModelDao> result = tagDefinitionDao.getByIds(uuids, internalCallContext);
        assertEquals(result.size(), 0);

        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        final TagDefinitionModelDao defYo = tagDefinitionDao.create(UUID.randomUUID().toString().substring(0, 5), "defintion yo", internalCallContext);
        assertListenerStatus();
        uuids.add(defYo.getId());

        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        final TagDefinitionModelDao defBah = tagDefinitionDao.create(UUID.randomUUID().toString().substring(0, 5), "defintion bah", internalCallContext);
        assertListenerStatus();
        uuids.add(defBah.getId());

        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        final TagDefinitionModelDao defZoo = tagDefinitionDao.create(UUID.randomUUID().toString().substring(0, 5), "defintion zoo", internalCallContext);
        assertListenerStatus();
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
        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        final TagDefinitionModelDao defYo = tagDefinitionDao.create(UUID.randomUUID().toString().substring(0, 5), "defintion yo", internalCallContext);
        assertListenerStatus();

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
        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        final TagDefinitionModelDao defYo = tagDefinitionDao.create(UUID.randomUUID().toString().substring(0, 5), "defintion yo", internalCallContext);
        assertListenerStatus();

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

        // Create a tag definition
        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        final TagDefinitionModelDao createdTagDefinition = tagDefinitionDao.create(definitionName, description, internalCallContext);
        Assert.assertEquals(createdTagDefinition.getName(), definitionName);
        Assert.assertEquals(createdTagDefinition.getDescription(), description);
        assertListenerStatus();

        // Make sure we can create a tag
        eventsListener.pushExpectedEvent(NextEvent.TAG);
        final Tag tag = new DescriptiveTag(createdTagDefinition.getId(), objectType, objectId, internalCallContext.getCreatedDate());
        tagDao.create(new TagModelDao(tag), internalCallContext);
        assertListenerStatus();

        // Make sure we can retrieve it via the DAO
        final List<TagModelDao> foundTags = tagDao.getTagsForObject(objectId, objectType, false, internalCallContext);
        Assert.assertEquals(foundTags.size(), 1);
        Assert.assertEquals(foundTags.get(0).getTagDefinitionId(), createdTagDefinition.getId());
        final List<TagModelDao> foundTagsForAccount = tagDao.getTagsForAccount(false, internalCallContext);
        Assert.assertEquals(foundTagsForAccount.size(), 1);
        Assert.assertEquals(foundTagsForAccount.get(0).getTagDefinitionId(), createdTagDefinition.getId());

        // Delete the tag
        eventsListener.pushExpectedEvent(NextEvent.TAG);
        tagDao.deleteTag(objectId, objectType, createdTagDefinition.getId(), internalCallContext);
        assertListenerStatus();

        // Make sure the tag is deleted
        Assert.assertEquals(tagDao.getTagsForObject(objectId, objectType, false, internalCallContext).size(), 0);
        Assert.assertEquals(tagDao.getTagsForAccount(false, internalCallContext).size(), 0);
        Assert.assertEquals(tagDao.getTagsForObject(objectId, objectType, true, internalCallContext).size(), 1);
        Assert.assertEquals(tagDao.getTagsForAccount(true, internalCallContext).size(), 1);
    }
}
