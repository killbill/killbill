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

package com.ning.billing.util.tag;

import java.util.List;
import java.util.UUID;

import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.tag.dao.TagDefinitionModelDao;
import com.ning.billing.util.tag.dao.TagModelDao;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestTagStore extends UtilTestSuiteWithEmbeddedDB {


    @Test(groups = "slow")
    public void testTagCreationAndRetrieval() throws TagApiException, TagDefinitionApiException {
        final UUID accountId = UUID.randomUUID();

        TagDefinitionModelDao testTagDefinition;
        tagDefinitionDao.create("tag1", "First tag", internalCallContext);
        testTagDefinition = tagDefinitionDao.create("testTagDefinition", "Second tag", internalCallContext);


        final Tag tag = new DescriptiveTag(testTagDefinition.getId(), ObjectType.ACCOUNT, accountId, clock.getUTCNow());

        tagDao.create(new TagModelDao(tag), internalCallContext);

        final TagModelDao savedTag = tagDao.getById(tag.getId(), internalCallContext);
        assertEquals(savedTag.getTagDefinitionId(), tag.getTagDefinitionId());
        assertEquals(savedTag.getId(), tag.getId());
    }

    @Test(groups = "slow")
    public void testControlTagCreation() throws TagApiException {
        final UUID accountId = UUID.randomUUID();

        final ControlTag tag = new DefaultControlTag(ControlTagType.AUTO_INVOICING_OFF, ObjectType.ACCOUNT, accountId, clock.getUTCNow());
        tagDao.create(new TagModelDao(tag), internalCallContext);

        final TagModelDao savedTag = tagDao.getById(tag.getId(), internalCallContext);
        assertEquals(savedTag.getTagDefinitionId(), tag.getTagDefinitionId());
        assertEquals(savedTag.getId(), tag.getId());
    }

    @Test(groups = "slow", expectedExceptions = TagDefinitionApiException.class)
    public void testTagDefinitionCreationWithControlTagName() throws TagDefinitionApiException {
        final String definitionName = ControlTagType.AUTO_PAY_OFF.toString();
        tagDefinitionDao.create(definitionName, "This should break", internalCallContext);
    }

    @Test(groups = "slow")
    public void testTagDefinitionDeletionForUnusedDefinition() throws TagDefinitionApiException {
        final String definitionName = "TestTag1234";
        tagDefinitionDao.create(definitionName, "Some test tag", internalCallContext);

        TagDefinitionModelDao tagDefinition = tagDefinitionDao.getByName(definitionName, internalCallContext);
        assertNotNull(tagDefinition);

        tagDefinitionDao.deleteById(tagDefinition.getId(), internalCallContext);
        tagDefinition = tagDefinitionDao.getByName(definitionName, internalCallContext);
        assertNull(tagDefinition);
    }

    @Test(groups = "slow", expectedExceptions = TagDefinitionApiException.class)
    public void testTagDefinitionDeletionForDefinitionInUse() throws TagDefinitionApiException, TagApiException {
        final String definitionName = "TestTag12345";
        tagDefinitionDao.create(definitionName, "Some test tag", internalCallContext);

        final TagDefinitionModelDao tagDefinition = tagDefinitionDao.getByName(definitionName, internalCallContext);
        assertNotNull(tagDefinition);

        final UUID objectId = UUID.randomUUID();
        final Tag tag = new DescriptiveTag(tagDefinition.getId(), ObjectType.ACCOUNT, objectId, internalCallContext.getCreatedDate());
        tagDao.create(new TagModelDao(tag), internalCallContext);

        tagDefinitionDao.deleteById(tagDefinition.getId(), internalCallContext);
    }

    @Test(groups = "slow")
    public void testDeleteTagBeforeDeleteTagDefinition() throws TagApiException {
        final String definitionName = "TestTag1234567";
        try {
            tagDefinitionDao.create(definitionName, "Some test tag", internalCallContext);
        } catch (TagDefinitionApiException e) {
            fail("Could not create tag definition", e);
        }

        final TagDefinitionModelDao tagDefinition = tagDefinitionDao.getByName(definitionName, internalCallContext);
        assertNotNull(tagDefinition);

        final UUID objectId = UUID.randomUUID();

        final Tag tag = new DescriptiveTag(tagDefinition.getId(), ObjectType.ACCOUNT, objectId, internalCallContext.getCreatedDate());
        tagDao.create(new TagModelDao(tag), internalCallContext);
        tagDao.deleteTag(objectId, ObjectType.ACCOUNT, tagDefinition.getId(), internalCallContext);

        try {
            tagDefinitionDao.deleteById(tagDefinition.getId(), internalCallContext);
        } catch (TagDefinitionApiException e) {
            fail("Could not delete tag definition", e);
        }
    }

    @Test(groups = "slow")
    public void testGetTagDefinitions() {
        final List<TagDefinitionModelDao> definitionList = tagDefinitionDao.getTagDefinitions(internalCallContext);
        assertTrue(definitionList.size() >= ControlTagType.values().length);
    }
}
