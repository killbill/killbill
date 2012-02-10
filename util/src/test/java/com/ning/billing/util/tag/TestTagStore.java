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
import org.apache.commons.io.IOUtils;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.account.api.ControlTagType;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;
import com.ning.billing.util.clock.MockClockModule;
import com.ning.billing.util.tag.dao.TagDefinitionDao;
import com.ning.billing.util.tag.dao.TagStoreSqlDao;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Test(groups={"util"})
public class TestTagStore {
    private final static String ACCOUNT_TYPE = "ACCOUNT";
    private final Clock clock = new DefaultClock();
    private IDBI dbi;
    private TagDefinition tag1;
    private TagDefinition tag2;
    private TagStoreModuleMock module;
    private TagStoreSqlDao tagStoreSqlDao;
    private TagDefinitionDao tagDefinitionDao;
    private final Logger log = LoggerFactory.getLogger(TestTagStore.class);

    @BeforeClass(alwaysRun = true)
    protected void setup() throws IOException {
        // Health check test to make sure MySQL is setup properly
        try {
            module = new TagStoreModuleMock();
            final String utilDdl = IOUtils.toString(TestTagStore.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));

            module.startDb();
            module.initDb(utilDdl);

            final Injector injector = Guice.createInjector(Stage.DEVELOPMENT, module, new MockClockModule());
            dbi = injector.getInstance(IDBI.class);

            tagStoreSqlDao = injector.getInstance(TagStoreSqlDao.class);
            tagStoreSqlDao.test();

            tagDefinitionDao = injector.getInstance(TagDefinitionDao.class);
            tag1 = tagDefinitionDao.create("tag1", "First tag", "test");
            tag2 = tagDefinitionDao.create("tag2", "Second tag", "test");
        }
        catch (Throwable t) {
            log.error("Failed to start tag store tests", t);
            fail(t.toString());
        }
    }

    @AfterClass(alwaysRun = true)
    public void stopMysql()
    {
        module.stopDb();
    }

    private void saveTags(final TagStoreSqlDao dao, final String objectType, final String accountId, final List<Tag> tagList)  {
        dao.inTransaction(new Transaction<Void, TagStoreSqlDao>() {
            @Override
            public Void inTransaction(TagStoreSqlDao transactional,
                    TransactionStatus status) throws Exception {
                dao.batchSaveFromTransaction(accountId.toString(), objectType, tagList);
                return null;
            }
        });
    }

    @Test
    public void testTagCreationAndRetrieval() {
        UUID accountId = UUID.randomUUID();

        TagStore tagStore = new DefaultTagStore(accountId, ACCOUNT_TYPE);
        Tag tag = new DescriptiveTag(tag2, "test", clock.getUTCNow());
        tagStore.add(tag);

        TagStoreSqlDao dao = dbi.onDemand(TagStoreSqlDao.class);
        saveTags(dao, ACCOUNT_TYPE, accountId.toString(), tagStore.getEntityList());

        List<Tag> savedTags = dao.load(accountId.toString(), ACCOUNT_TYPE);
        assertEquals(savedTags.size(), 1);

        Tag savedTag = savedTags.get(0);
        assertEquals(savedTag.getAddedBy(), tag.getAddedBy());
        assertEquals(savedTag.getAddedDate().compareTo(tag.getAddedDate()), 0);
        assertEquals(savedTag.getTagDefinitionName(), tag.getTagDefinitionName());
        assertEquals(savedTag.getId(), tag.getId());
    }


    @Test
    public void testControlTagCreation() {
        UUID accountId = UUID.randomUUID();
        TagStore tagStore = new DefaultTagStore(accountId, ACCOUNT_TYPE);

        ControlTag tag = new DefaultControlTag("testUser", clock.getUTCNow(), ControlTagType.AUTO_INVOICING_OFF);
        tagStore.add(tag);
        assertEquals(tagStore.generateInvoice(), false);

        List<Tag> tagList = tagStore.getEntityList();
        saveTags(tagStoreSqlDao, ACCOUNT_TYPE, accountId.toString(), tagList);

        tagStore.clear();
        assertEquals(tagStore.getEntityList().size(), 0);

        tagList = tagStoreSqlDao.load(accountId.toString(), ACCOUNT_TYPE);
        tagStore.add(tagList);
        assertEquals(tagList.size(), 1);

        assertEquals(tagStore.generateInvoice(), false);
    }

    @Test
    public void testDescriptiveTagCreation() {
        UUID accountId = UUID.randomUUID();
        TagStore tagStore = new DefaultTagStore(accountId, ACCOUNT_TYPE);

        String definitionName = "SomeTestTag";
        TagDefinition tagDefinition = null;
        try {
            tagDefinition = tagDefinitionDao.create(definitionName, "Test tag for some test purpose", "testUser");
        } catch (TagDefinitionApiException e) {
            fail("Tag definition creation failed.", e);
        }

        DescriptiveTag tag = new DescriptiveTag(tagDefinition, "testUser", clock.getUTCNow());
        tagStore.add(tag);
        assertEquals(tagStore.generateInvoice(), true);

        List<Tag> tagList = tagStore.getEntityList();
        saveTags(tagStoreSqlDao, ACCOUNT_TYPE, accountId.toString(), tagList);

        tagStore.clear();
        assertEquals(tagStore.getEntityList().size(), 0);

        tagList = tagStoreSqlDao.load(accountId.toString(), ACCOUNT_TYPE);
        tagStore.add(tagList);
        assertEquals(tagList.size(), 1);

        assertEquals(tagStore.generateInvoice(), true);
    }

    @Test
    public void testMixedTagCreation() {
        UUID accountId = UUID.randomUUID();
        TagStore tagStore = new DefaultTagStore(accountId, ACCOUNT_TYPE);

        String definitionName = "MixedTagTest";
        TagDefinition tagDefinition = null;
        try {
            tagDefinition = tagDefinitionDao.create(definitionName, "Test tag for some test purpose", "testUser");
        } catch (TagDefinitionApiException e) {
            fail("Tag definition creation failed.", e);
        }

        DescriptiveTag descriptiveTag = new DescriptiveTag(tagDefinition, "testUser", clock.getUTCNow());
        tagStore.add(descriptiveTag);
        assertEquals(tagStore.generateInvoice(), true);

        ControlTag controlTag = new DefaultControlTag("testUser", clock.getUTCNow(), ControlTagType.AUTO_INVOICING_OFF);
        tagStore.add(controlTag);
        assertEquals(tagStore.generateInvoice(), false);

        List<Tag> tagList = tagStore.getEntityList();
        saveTags(tagStoreSqlDao, ACCOUNT_TYPE, accountId.toString(), tagList);

        tagStore.clear();
        assertEquals(tagStore.getEntityList().size(), 0);

        tagList = tagStoreSqlDao.load(accountId.toString(), ACCOUNT_TYPE);
        tagStore.add(tagList);
        assertEquals(tagList.size(), 2);

        assertEquals(tagStore.generateInvoice(), false);
    }

    @Test
    public void testControlTags() {
        UUID accountId = UUID.randomUUID();
        TagStore tagStore = new DefaultTagStore(accountId, ACCOUNT_TYPE);
        assertEquals(tagStore.generateInvoice(), true);
        assertEquals(tagStore.processPayment(), true);

        ControlTag invoiceTag = new DefaultControlTag("testUser", clock.getUTCNow(), ControlTagType.AUTO_INVOICING_OFF);
        tagStore.add(invoiceTag);
        assertEquals(tagStore.generateInvoice(), false);
        assertEquals(tagStore.processPayment(), true);

        ControlTag paymentTag = new DefaultControlTag("testUser", clock.getUTCNow(), ControlTagType.AUTO_BILLING_OFF);
        tagStore.add(paymentTag);
        assertEquals(tagStore.generateInvoice(), false);
        assertEquals(tagStore.processPayment(), false);
    }

    @Test(expectedExceptions = TagDefinitionApiException.class)
    public void testTagDefinitionCreationWithControlTagName() throws TagDefinitionApiException {
        String definitionName = ControlTagType.AUTO_BILLING_OFF.toString();
        tagDefinitionDao.create(definitionName, "This should break", "test");
    }

    @Test
    public void testTagDefinitionDeletionForUnusedDefinition() throws TagDefinitionApiException {
        String definitionName = "TestTag1234";
        tagDefinitionDao.create(definitionName, "Some test tag", "test");

        TagDefinition tagDefinition = tagDefinitionDao.getByName(definitionName);
        assertNotNull(tagDefinition);

        tagDefinitionDao.deleteTagDefinition(definitionName);
        tagDefinition = tagDefinitionDao.getByName(definitionName);
        assertNull(tagDefinition);
    }

    @Test(expectedExceptions = TagDefinitionApiException.class)
    public void testTagDefinitionDeletionForDefinitionInUse() throws TagDefinitionApiException {
        String definitionName = "TestTag12345";
        tagDefinitionDao.create(definitionName, "Some test tag", "test");

        TagDefinition tagDefinition = tagDefinitionDao.getByName(definitionName);
        assertNotNull(tagDefinition);

        UUID objectId = UUID.randomUUID();
        String objectType = "TestType";
        TagStore tagStore = new DefaultTagStore(objectId, objectType);
        Tag tag = new DescriptiveTag(tagDefinition, "test", clock.getUTCNow());
        tagStore.add(tag);

        saveTags(tagStoreSqlDao, objectType, objectId.toString(), tagStore.getEntityList());

        List<Tag> tags = tagStoreSqlDao.load(objectId.toString(), objectType);
        assertEquals(tags.size(), 1);

        tagDefinitionDao.deleteTagDefinition(definitionName);
    }

    @Test
    public void testDeleteAllTagsForDefinitionInUse() {
        String definitionName = "TestTag1234567";
        try {
            tagDefinitionDao.create(definitionName, "Some test tag", "test");
        } catch (TagDefinitionApiException e) {
            fail("Could not create tag definition", e);
        }

        TagDefinition tagDefinition = tagDefinitionDao.getByName(definitionName);
        assertNotNull(tagDefinition);

        UUID objectId = UUID.randomUUID();
        String objectType = "TestType";
        TagStore tagStore = new DefaultTagStore(objectId, objectType);
        Tag tag = new DescriptiveTag(tagDefinition, "test", clock.getUTCNow());
        tagStore.add(tag);

        saveTags(tagStoreSqlDao, objectType, objectId.toString(), tagStore.getEntityList());

        List<Tag> tags = tagStoreSqlDao.load(objectId.toString(), objectType);
        assertEquals(tags.size(), 1);

        try {
            tagDefinitionDao.deleteAllTagsForDefinition(definitionName);
        } catch (TagDefinitionApiException e) {
            fail("Could not delete tags for tag definition", e);
        }

        try {
            tagDefinitionDao.deleteTagDefinition(definitionName);
        } catch (TagDefinitionApiException e) {
            fail("Could not delete tag definition", e);
        }
    }

    @Test
    public void testDeleteAllTagsForDefinitionNotInUse() {
        String definitionName = "TestTag4321";
        try {
            tagDefinitionDao.create(definitionName, "Some test tag", "test");
        } catch (TagDefinitionApiException e) {
            fail("Could not create tag definition", e);
        }

        TagDefinition tagDefinition = tagDefinitionDao.getByName(definitionName);
        assertNotNull(tagDefinition);

        try {
            tagDefinitionDao.deleteAllTagsForDefinition(definitionName);
        } catch (TagDefinitionApiException e) {
            fail("Could not delete tags for tag definition", e);
        }

        try {
            tagDefinitionDao.deleteTagDefinition(definitionName);
        } catch (TagDefinitionApiException e) {
            fail("Could not delete tag definition", e);
        }
    }

    @Test(expectedExceptions = TagDefinitionApiException.class)
    public void testDeleteAllTagsForDefinitionWithWrongName() throws TagDefinitionApiException {
        String definitionName = "TestTag654321";
        String wrongDefinitionName = "TestTag564321";
        try {
            tagDefinitionDao.create(definitionName, "Some test tag", "test");
        } catch (TagDefinitionApiException e) {
            fail("Could not create tag definition", e);
        }

        TagDefinition tagDefinition = tagDefinitionDao.getByName(definitionName);
        assertNotNull(tagDefinition);

        tagDefinitionDao.deleteAllTagsForDefinition(wrongDefinitionName);

        try {
            tagDefinitionDao.deleteTagDefinition(definitionName);
        } catch (TagDefinitionApiException e) {
            fail("Could not delete tag definition", e);
        }
    }

    @Test
    public void testGetTagDefinitions() {
        List<TagDefinition> definitionList = tagDefinitionDao.getTagDefinitions();
        assertTrue(definitionList.size() >= ControlTagType.values().length);
    }
}
