/*
 * Copyright 2010-2011 Ning, Inc.
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

package org.killbill.billing.util.tag;

import java.util.List;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.ObjectType;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.tag.dao.TagDefinitionModelDao;
import org.killbill.billing.util.tag.dao.TagModelDao;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestTagStore extends UtilTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testTagCreationAndRetrieval() throws TagApiException, TagDefinitionApiException {
        final UUID accountId = UUID.randomUUID();

        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        tagDefinitionDao.create("tag1", "First tag", ObjectType.ACCOUNT.name(), internalCallContext);
        assertListenerStatus();

        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        final TagDefinitionModelDao testTagDefinition = tagDefinitionDao.create("testTagDefinition", "Second tag", ObjectType.ACCOUNT.name(), internalCallContext);
        assertListenerStatus();

        final Tag tag = new DescriptiveTag(testTagDefinition.getId(), ObjectType.ACCOUNT, accountId, clock.getUTCNow());

        eventsListener.pushExpectedEvent(NextEvent.TAG);
        tagDao.create(new TagModelDao(tag), internalCallContext);
        assertListenerStatus();

        final TagModelDao savedTag = tagDao.getById(tag.getId(), internalCallContext);
        assertEquals(savedTag.getTagDefinitionId(), tag.getTagDefinitionId());
        assertEquals(savedTag.getId(), tag.getId());
    }

    @Test(groups = "slow")
    public void testControlTagCreation() throws TagApiException {
        final UUID accountId = UUID.randomUUID();

        final ControlTag tag = new DefaultControlTag(ControlTagType.AUTO_INVOICING_OFF, ObjectType.ACCOUNT, accountId, clock.getUTCNow());
        eventsListener.pushExpectedEvent(NextEvent.TAG);
        tagDao.create(new TagModelDao(tag), internalCallContext);
        assertListenerStatus();

        final TagModelDao savedTag = tagDao.getById(tag.getId(), internalCallContext);
        assertEquals(savedTag.getTagDefinitionId(), tag.getTagDefinitionId());
        assertEquals(savedTag.getId(), tag.getId());
    }

    @Test(groups = "slow", expectedExceptions = TagDefinitionApiException.class)
    public void testTagDefinitionCreationWithControlTagName() throws TagDefinitionApiException {
        final String definitionName = ControlTagType.AUTO_PAY_OFF.toString();
        tagDefinitionDao.create(definitionName, "This should break", ObjectType.ACCOUNT.name(), internalCallContext);
    }

    @Test(groups = "slow")
    public void testTagDefinitionDeletionForUnusedDefinition() throws TagDefinitionApiException {
        final String definitionName = "TestTag1234";
        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        tagDefinitionDao.create(definitionName, "Some test tag", ObjectType.ACCOUNT.name(), internalCallContext);
        assertListenerStatus();

        TagDefinitionModelDao tagDefinition = tagDefinitionDao.getByName(definitionName, internalCallContext);
        assertNotNull(tagDefinition);

        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        tagDefinitionDao.deleteById(tagDefinition.getId(), internalCallContext);
        assertListenerStatus();

        try {
            tagDefinitionDao.getByName(definitionName, internalCallContext);
            Assert.fail("Call should fail");
        } catch (TagDefinitionApiException expected) {
        }
    }

    @Test(groups = "slow", expectedExceptions = TagDefinitionApiException.class)
    public void testTagDefinitionDeletionForDefinitionInUse() throws TagDefinitionApiException, TagApiException {
        final String definitionName = "TestTag12345";
        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        tagDefinitionDao.create(definitionName, "Some test tag", ObjectType.ACCOUNT.name(), internalCallContext);
        assertListenerStatus();

        final TagDefinitionModelDao tagDefinition = tagDefinitionDao.getByName(definitionName, internalCallContext);
        assertNotNull(tagDefinition);

        final UUID objectId = UUID.randomUUID();
        final Tag tag = new DescriptiveTag(tagDefinition.getId(), ObjectType.ACCOUNT, objectId, internalCallContext.getCreatedDate());
        eventsListener.pushExpectedEvent(NextEvent.TAG);
        tagDao.create(new TagModelDao(tag), internalCallContext);
        assertListenerStatus();

        tagDefinitionDao.deleteById(tagDefinition.getId(), internalCallContext);
    }

    @Test(groups = "slow")
    public void testDeleteTagBeforeDeleteTagDefinition() throws TagDefinitionApiException, TagApiException {
        final String definitionName = "TestTag1234567";
        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        tagDefinitionDao.create(definitionName, "Some test tag", ObjectType.ACCOUNT.name(), internalCallContext);
        assertListenerStatus();

        final TagDefinitionModelDao tagDefinition = tagDefinitionDao.getByName(definitionName, internalCallContext);
        assertNotNull(tagDefinition);

        final UUID objectId = UUID.randomUUID();

        final Tag tag = new DescriptiveTag(tagDefinition.getId(), ObjectType.ACCOUNT, objectId, internalCallContext.getCreatedDate());
        eventsListener.pushExpectedEvent(NextEvent.TAG);
        tagDao.create(new TagModelDao(tag), internalCallContext);
        assertListenerStatus();

        eventsListener.pushExpectedEvent(NextEvent.TAG);
        tagDao.deleteTag(objectId, ObjectType.ACCOUNT, tagDefinition.getId(), internalCallContext);
        assertListenerStatus();

        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        tagDefinitionDao.deleteById(tagDefinition.getId(), internalCallContext);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testGetTagDefinitions() {
        final List<TagDefinitionModelDao> definitionList = tagDefinitionDao.getTagDefinitions(false, internalCallContext);
        assertTrue(definitionList.size() >= ControlTagType.values().length);
    }
}
