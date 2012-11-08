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

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.svcsapi.bus.InternalBus;
import com.ning.billing.util.tag.dao.TagDao;
import com.ning.billing.util.tag.dao.TagDefinitionDao;

import com.google.inject.Inject;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Test(groups = {"slow"})
@Guice(modules = MockTagStoreModuleSql.class)
public class TestTagStore extends UtilTestSuiteWithEmbeddedDB {

    @Inject
    private MysqlTestingHelper helper;

    @Inject
    private IDBI dbi;

    @Inject
    private TagDao tagDao;

    @Inject
    private TagDefinitionDao tagDefinitionDao;

    @Inject
    private Clock clock;

    @Inject
    private InternalBus bus;

    private TagDefinition testTagDefinition;

    private final Logger log = LoggerFactory.getLogger(TestTagStore.class);

    @BeforeClass(groups = "slow")
    protected void setup() throws IOException {
        try {
            bus.start();

            tagDefinitionDao.create("tag1", "First tag", internalCallContext);
            testTagDefinition = tagDefinitionDao.create("testTagDefinition", "Second tag", internalCallContext);
        } catch (Throwable t) {
            log.error("Failed to start tag store tests", t);
            fail(t.toString());
        }
    }

    @AfterClass(groups = "slow")
    public void tearDown() {
        bus.stop();
    }

    @Test(groups = "slow")
    public void testTagCreationAndRetrieval() throws TagApiException {
        final UUID accountId = UUID.randomUUID();
        final Tag tag = new DescriptiveTag(testTagDefinition.getId(), UUID.randomUUID(), ObjectType.ACCOUNT, accountId, clock.getUTCNow());

        tagDao.insertTag(accountId, ObjectType.ACCOUNT, tag.getTagDefinitionId(), internalCallContext);

        final Tag savedTag = tagDao.getTagById(tag.getId(), internalCallContext);
        assertEquals(savedTag.getTagDefinitionId(), tag.getTagDefinitionId());
        assertEquals(savedTag.getId(), tag.getId());
    }


    @Test(groups = "slow")
    public void testControlTagCreation() throws TagApiException {
        final UUID accountId = UUID.randomUUID();

        final ControlTag tag = new DefaultControlTag(ControlTagType.AUTO_INVOICING_OFF, ObjectType.ACCOUNT, accountId, clock.getUTCNow());

        tagDao.insertTag(accountId, ObjectType.ACCOUNT, tag.getTagDefinitionId(), internalCallContext);

        final Tag savedTag = tagDao.getTagById(tag.getId(), internalCallContext);
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

        TagDefinition tagDefinition = tagDefinitionDao.getByName(definitionName, internalCallContext);
        assertNotNull(tagDefinition);

        tagDefinitionDao.deleteById(tagDefinition.getId(), internalCallContext);
        tagDefinition = tagDefinitionDao.getByName(definitionName, internalCallContext);
        assertNull(tagDefinition);
    }

    @Test(groups = "slow", expectedExceptions = TagDefinitionApiException.class)
    public void testTagDefinitionDeletionForDefinitionInUse() throws TagDefinitionApiException, TagApiException {
        final String definitionName = "TestTag12345";
        tagDefinitionDao.create(definitionName, "Some test tag", internalCallContext);

        final TagDefinition tagDefinition = tagDefinitionDao.getByName(definitionName, internalCallContext);
        assertNotNull(tagDefinition);

        final UUID objectId = UUID.randomUUID();

        tagDao.insertTag(tagDefinition.getId(), ObjectType.ACCOUNT, objectId, internalCallContext);

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

        final TagDefinition tagDefinition = tagDefinitionDao.getByName(definitionName, internalCallContext);
        assertNotNull(tagDefinition);

        final UUID objectId = UUID.randomUUID();

        tagDao.insertTag(tagDefinition.getId(), ObjectType.ACCOUNT, objectId, internalCallContext);

        tagDao.deleteTag(objectId, ObjectType.ACCOUNT, tagDefinition.getId(), internalCallContext);

        try {
            tagDefinitionDao.deleteById(tagDefinition.getId(), internalCallContext);
        } catch (TagDefinitionApiException e) {
            fail("Could not delete tag definition", e);
        }
    }

    @Test(groups = "slow")
    public void testGetTagDefinitions() {
        final List<TagDefinition> definitionList = tagDefinitionDao.getTagDefinitions(internalCallContext);
        assertTrue(definitionList.size() >= ControlTagType.values().length);
    }
}
