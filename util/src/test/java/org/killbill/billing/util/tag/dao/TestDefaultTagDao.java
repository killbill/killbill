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

import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.DescriptiveTag;
import org.killbill.billing.util.tag.Tag;

import static org.testng.Assert.assertEquals;

public class TestDefaultTagDao extends UtilTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testGetByIds() throws TagDefinitionApiException {
        final List<UUID> uuids = new ArrayList<UUID>();

        // Check with a empty Collection first
        List<TagDefinitionModelDao> result = tagDefinitionDao.getByIds(uuids, internalCallContext);
        assertEquals(result.size(), 0);

        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        final TagDefinitionModelDao defYo = tagDefinitionDao.create(UUID.randomUUID().toString().substring(0, 5), "defintion yo", ObjectType.ACCOUNT.name(), internalCallContext);
        assertListenerStatus();
        uuids.add(defYo.getId());

        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        final TagDefinitionModelDao defBah = tagDefinitionDao.create(UUID.randomUUID().toString().substring(0, 5), "defintion bah", ObjectType.ACCOUNT.name(), internalCallContext);
        assertListenerStatus();
        uuids.add(defBah.getId());

        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        final TagDefinitionModelDao defZoo = tagDefinitionDao.create(UUID.randomUUID().toString().substring(0, 5), "defintion zoo", ObjectType.ACCOUNT.name(), internalCallContext);
        assertListenerStatus();
        uuids.add(defZoo.getId());

        result = tagDefinitionDao.getByIds(uuids, internalCallContext);
        assertEquals(result.size(), 3);

        // Add invoice tag and retry
        uuids.add(ControlTagType.AUTO_PAY_OFF.getId());
        result = tagDefinitionDao.getByIds(uuids, internalCallContext);
        assertEquals(result.size(), 4);

        result = tagDefinitionDao.getTagDefinitions(true, internalCallContext);
        assertEquals(result.size(), 3 + SystemTags.get(true).size());
    }

    @Test(groups = "slow")
    public void testGetById() throws TagDefinitionApiException {
        // User Tag
        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        final TagDefinitionModelDao defYo = tagDefinitionDao.create(UUID.randomUUID().toString().substring(0, 5), "defintion yo", ObjectType.ACCOUNT.name(),internalCallContext);
        assertListenerStatus();

        final TagDefinitionModelDao resDefYo = tagDefinitionDao.getById(defYo.getId(), internalCallContext);
        assertEquals(defYo, resDefYo);

        // Control Tag
        try {
            tagDefinitionDao.create(ControlTagType.AUTO_INVOICING_OFF.name(), ControlTagType.AUTO_INVOICING_OFF.name(), ObjectType.ACCOUNT.name(),internalCallContext);
            Assert.fail("Should not be able to create a invoice tag");
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
        final TagDefinitionModelDao defYo = tagDefinitionDao.create(UUID.randomUUID().toString().substring(0, 5), "defintion yo", ObjectType.ACCOUNT.name(),internalCallContext);
        assertListenerStatus();

        final TagDefinitionModelDao resDefYo = tagDefinitionDao.getByName(defYo.getName(), internalCallContext);
        assertEquals(defYo, resDefYo);

        // Control Tag
        try {
            tagDefinitionDao.create(ControlTagType.AUTO_PAY_OFF.name(), ControlTagType.AUTO_INVOICING_OFF.name(), ObjectType.ACCOUNT.name(), internalCallContext);
            Assert.fail("Should not be able to create a invoice tag");
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
        final TagDefinitionModelDao createdTagDefinition = tagDefinitionDao.create(definitionName, description, ObjectType.ACCOUNT.name(), internalCallContext);
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

        // Delete tag again, check correct error
        try {
            tagDao.deleteTag(objectId, objectType, createdTagDefinition.getId(), internalCallContext);
            Assert.fail("Deleting same tag again should fail");
        } catch (final TagApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.TAG_DOES_NOT_EXIST.getCode());
        }

    }

    @Test(groups = "slow")
    public void testInsertMultipleTags() throws TagApiException {
        final UUID objectId = UUID.randomUUID();
        final ObjectType objectType = ObjectType.INVOICE_ITEM;

        eventsListener.pushExpectedEvent(NextEvent.TAG);
        final Tag tag = new DescriptiveTag(ControlTagType.AUTO_INVOICING_OFF.getId(), objectType, objectId, internalCallContext.getCreatedDate());
        tagDao.create(new TagModelDao(tag), internalCallContext);
        assertListenerStatus();

        try {
            final Tag tag2 = new DescriptiveTag(ControlTagType.AUTO_INVOICING_OFF.getId(), objectType, objectId, internalCallContext.getCreatedDate());
            tagDao.create(new TagModelDao(tag2), internalCallContext);
            Assert.fail("Should not be able to create twice the same tag");
            assertListenerStatus();
        } catch (final TagApiException e) {
            Assert.assertEquals(ErrorCode.TAG_ALREADY_EXISTS.getCode(), e.getCode());
        }
    }

}
