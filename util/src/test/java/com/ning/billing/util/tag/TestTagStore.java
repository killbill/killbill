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
import java.util.Map;
import java.util.UUID;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.tag.dao.TagDao;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Guice;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.dbi.MysqlTestingHelper;

import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.tag.dao.TagDefinitionDao;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Test(groups={"slow"})
@Guice(modules = MockTagStoreModuleSql.class)
public class TestTagStore {
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

    private TagDefinition testTag;
    private TestSqlDao dao;

    private final Logger log = LoggerFactory.getLogger(TestTagStore.class);
    private CallContext context;

    @BeforeClass(groups="slow")
    protected void setup() throws IOException {
        // Health check test to make sure MySQL is setup properly
        try {
            final String utilDdl = IOUtils.toString(TestTagStore.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));

            helper.startMysql();
            helper.initDb(utilDdl);

            context = new DefaultCallContextFactory(clock).createCallContext("Tag store test", CallOrigin.TEST, UserType.TEST);
            dao = dbi.onDemand(TestSqlDao.class);
            
            cleanupTags();
            tagDefinitionDao.create("tag1", "First tag", context);
            testTag = tagDefinitionDao.create("testTag", "Second tag", context);
        }
        catch (Throwable t) {
            log.error("Failed to start tag store tests", t);
            fail(t.toString());
        }
    }

    @AfterClass(groups="slow")
    public void stopMysql()
    {
        if (helper != null) {
            helper.stopMysql();
        }
    }

    private void cleanupTags() {
        try {
            helper.getDBI().withHandle(new HandleCallback<Void>() {
                @Override
                public Void withHandle(Handle handle) throws Exception {
                    handle.createScript("delete from tag_definitions").execute();
                    handle.createScript("delete from tag_definition_history").execute();
                    handle.createScript("delete from tagStore").execute();
                    handle.createScript("delete from tag_history").execute();
                    return null;
                }
            });
        } catch (Throwable ignore) {
        }
    }
    @Test(groups="slow")
    public void testTagCreationAndRetrieval() {
        UUID accountId = UUID.randomUUID();

        TagStore tagStore = new DefaultTagStore(accountId, ObjectType.ACCOUNT);
        Tag tag = new DescriptiveTag(testTag);
        tagStore.add(tag);

        tagDao.saveEntitiesFromTransaction(dao, accountId, ObjectType.ACCOUNT, tagStore.getEntityList(), context);

        List<Tag> savedTags = tagDao.loadEntities(accountId, ObjectType.ACCOUNT);
        assertEquals(savedTags.size(), 1);

        Tag savedTag = savedTags.get(0);
        assertEquals(savedTag.getTagDefinitionName(), tag.getTagDefinitionName());
        assertEquals(savedTag.getId(), tag.getId());
    }


    @Test(groups="slow")
    public void testControlTagCreation() {
        UUID accountId = UUID.randomUUID();
        TagStore tagStore = new DefaultTagStore(accountId, ObjectType.ACCOUNT);

        ControlTag tag = new DefaultControlTag(ControlTagType.AUTO_INVOICING_OFF);
        tagStore.add(tag);
        assertEquals(tagStore.generateInvoice(), false);

        List<Tag> tagList = tagStore.getEntityList();
        tagDao.saveEntitiesFromTransaction(dao, accountId, ObjectType.ACCOUNT, tagList, context);

        tagStore.clear();
        assertEquals(tagStore.getEntityList().size(), 0);

        tagList = tagDao.loadEntities(accountId, ObjectType.ACCOUNT);
        tagStore.add(tagList);
        assertEquals(tagList.size(), 1);

        assertEquals(tagStore.generateInvoice(), false);
    }

    @Test(groups="slow")
    public void testDescriptiveTagCreation() {
        UUID accountId = UUID.randomUUID();
        TagStore tagStore = new DefaultTagStore(accountId, ObjectType.ACCOUNT);

        String definitionName = "SomeTestTag";
        TagDefinition tagDefinition = null;
        try {
            tagDefinition = tagDefinitionDao.create(definitionName, "Test tag for some test purpose", context);
        } catch (TagDefinitionApiException e) {
            fail("Tag definition creation failed.", e);
        }

        DescriptiveTag tag = new DescriptiveTag(tagDefinition);
        tagStore.add(tag);
        assertEquals(tagStore.generateInvoice(), true);

        tagDao.saveEntitiesFromTransaction(dao, accountId, ObjectType.ACCOUNT, tagStore.getEntityList(), context);

        tagStore.clear();
        assertEquals(tagStore.getEntityList().size(), 0);

        List<Tag> tagList = tagDao.loadEntities(accountId, ObjectType.ACCOUNT);
        tagStore.add(tagList);
        assertEquals(tagList.size(), 1);

        assertEquals(tagStore.generateInvoice(), true);
    }

    @Test(groups="slow")
    public void testMixedTagCreation() {
        UUID accountId = UUID.randomUUID();
        TagStore tagStore = new DefaultTagStore(accountId, ObjectType.ACCOUNT);

        String definitionName = "MixedTagTest";
        TagDefinition tagDefinition = null;
        try {
            tagDefinition = tagDefinitionDao.create(definitionName, "Test tag for some test purpose", context);
        } catch (TagDefinitionApiException e) {
            fail("Tag definition creation failed.", e);
        }

        DescriptiveTag descriptiveTag = new DescriptiveTag(tagDefinition);
        tagStore.add(descriptiveTag);
        assertEquals(tagStore.generateInvoice(), true);

        ControlTag controlTag = new DefaultControlTag(ControlTagType.AUTO_INVOICING_OFF);
        tagStore.add(controlTag);
        assertEquals(tagStore.generateInvoice(), false);

        tagDao.saveEntitiesFromTransaction(dao, accountId, ObjectType.ACCOUNT, tagStore.getEntityList(), context);

        tagStore.clear();
        assertEquals(tagStore.getEntityList().size(), 0);

        List<Tag> tagList = tagDao.loadEntities(accountId, ObjectType.ACCOUNT);
        tagStore.add(tagList);
        assertEquals(tagList.size(), 2);

        assertEquals(tagStore.generateInvoice(), false);
    }

    @Test(groups="slow")
    public void testControlTags() {
        UUID accountId = UUID.randomUUID();
        TagStore tagStore = new DefaultTagStore(accountId, ObjectType.ACCOUNT);
        assertEquals(tagStore.generateInvoice(), true);
        assertEquals(tagStore.processPayment(), true);

        ControlTag invoiceTag = new DefaultControlTag(ControlTagType.AUTO_INVOICING_OFF);
        tagStore.add(invoiceTag);
        assertEquals(tagStore.generateInvoice(), false);
        assertEquals(tagStore.processPayment(), true);

        ControlTag paymentTag = new DefaultControlTag(ControlTagType.AUTO_PAY_OFF);
        tagStore.add(paymentTag);
        assertEquals(tagStore.generateInvoice(), false);
        assertEquals(tagStore.processPayment(), false);
    }

    @Test(groups="slow", expectedExceptions = TagDefinitionApiException.class)
    public void testTagDefinitionCreationWithControlTagName() throws TagDefinitionApiException {
        String definitionName = ControlTagType.AUTO_PAY_OFF.toString();
        tagDefinitionDao.create(definitionName, "This should break", context);
    }

    @Test(groups="slow")
    public void testTagDefinitionDeletionForUnusedDefinition() throws TagDefinitionApiException {
        String definitionName = "TestTag1234";
        tagDefinitionDao.create(definitionName, "Some test tag", context);

        TagDefinition tagDefinition = tagDefinitionDao.getByName(definitionName);
        assertNotNull(tagDefinition);

        tagDefinitionDao.deleteTagDefinition(definitionName, context);
        tagDefinition = tagDefinitionDao.getByName(definitionName);
        assertNull(tagDefinition);
    }

    @Test(groups="slow", expectedExceptions = TagDefinitionApiException.class)
    public void testTagDefinitionDeletionForDefinitionInUse() throws TagDefinitionApiException {
        String definitionName = "TestTag12345";
        tagDefinitionDao.create(definitionName, "Some test tag", context);

        TagDefinition tagDefinition = tagDefinitionDao.getByName(definitionName);
        assertNotNull(tagDefinition);

        UUID objectId = UUID.randomUUID();
        TagStore tagStore = new DefaultTagStore(objectId, ObjectType.ACCOUNT);
        Tag tag = new DescriptiveTag(tagDefinition);
        tagStore.add(tag);

        tagDao.saveEntitiesFromTransaction(dao, objectId, ObjectType.ACCOUNT, tagStore.getEntityList(), context);

        List<Tag> tags = tagDao.loadEntities(objectId, ObjectType.ACCOUNT);
        assertEquals(tags.size(), 1);

        tagDefinitionDao.deleteTagDefinition(definitionName, context);
    }

    @Test(groups="slow")
    public void testDeleteAllTagsForDefinitionInUse() {
        String definitionName = "TestTag1234567";
        try {
            tagDefinitionDao.create(definitionName, "Some test tag", context);
        } catch (TagDefinitionApiException e) {
            fail("Could not create tag definition", e);
        }

        TagDefinition tagDefinition = tagDefinitionDao.getByName(definitionName);
        assertNotNull(tagDefinition);

        UUID objectId = UUID.randomUUID();
        TagStore tagStore = new DefaultTagStore(objectId, ObjectType.ACCOUNT);
        Tag tag = new DescriptiveTag(tagDefinition);
        tagStore.add(tag);

        tagDao.saveEntitiesFromTransaction(dao, objectId, ObjectType.ACCOUNT, tagStore.getEntityList(), context);

        List<Tag> tags = tagDao.loadEntities(objectId, ObjectType.ACCOUNT);
        assertEquals(tags.size(), 1);

        try {
            tagDefinitionDao.deleteAllTagsForDefinition(definitionName, context);
        } catch (TagDefinitionApiException e) {
            fail("Could not delete tagStore for tag definition", e);
        }

        try {
            tagDefinitionDao.deleteTagDefinition(definitionName, context);
        } catch (TagDefinitionApiException e) {
            fail("Could not delete tag definition", e);
        }
    }

    @Test(groups="slow")
    public void testDeleteAllTagsForDefinitionNotInUse() {
        String definitionName = "TestTag4321";
        try {
            tagDefinitionDao.create(definitionName, "Some test tag", context);
        } catch (TagDefinitionApiException e) {
            fail("Could not create tag definition", e);
        }

        TagDefinition tagDefinition = tagDefinitionDao.getByName(definitionName);
        assertNotNull(tagDefinition);

        try {
            tagDefinitionDao.deleteAllTagsForDefinition(definitionName, context);
        } catch (TagDefinitionApiException e) {
            fail("Could not delete tagStore for tag definition", e);
        }

        try {
            tagDefinitionDao.deleteTagDefinition(definitionName, context);
        } catch (TagDefinitionApiException e) {
            fail("Could not delete tag definition", e);
        }
    }

    @Test(groups="slow", expectedExceptions = TagDefinitionApiException.class)
    public void testDeleteAllTagsForDefinitionWithWrongName() throws TagDefinitionApiException {
        String definitionName = "TestTag654321";
        String wrongDefinitionName = "TestTag564321";
        try {
            tagDefinitionDao.create(definitionName, "Some test tag", context);
        } catch (TagDefinitionApiException e) {
            fail("Could not create tag definition", e);
        }

        TagDefinition tagDefinition = tagDefinitionDao.getByName(definitionName);
        assertNotNull(tagDefinition);

        tagDefinitionDao.deleteAllTagsForDefinition(wrongDefinitionName, context);

        try {
            tagDefinitionDao.deleteTagDefinition(definitionName, context);
        } catch (TagDefinitionApiException e) {
            fail("Could not delete tag definition", e);
        }
    }

    @Test(groups="slow")
    public void testGetTagDefinitions() {
        List<TagDefinition> definitionList = tagDefinitionDao.getTagDefinitions();
        assertTrue(definitionList.size() >= ControlTagType.values().length);
    }

    @Test
    public void testTagInsertAudit() {
        UUID accountId = UUID.randomUUID();

        TagStore tagStore = new DefaultTagStore(accountId, ObjectType.ACCOUNT);
        Tag tag = new DescriptiveTag(testTag);
        tagStore.add(tag);

        tagDao.saveEntitiesFromTransaction(dao, accountId, ObjectType.ACCOUNT, tagStore.getEntityList(), context);

        List<Tag> savedTags = tagDao.loadEntities(accountId, ObjectType.ACCOUNT);
        assertEquals(savedTags.size(), 1);

        Tag savedTag = savedTags.get(0);
        assertEquals(savedTag.getTagDefinitionName(), tag.getTagDefinitionName());
        assertEquals(savedTag.getId(), tag.getId());

        Handle handle = dbi.open();
        String query = String.format("select * from audit_log a inner join tag_history th on a.record_id = th.history_record_id where a.table_name = 'tag_history' and th.id='%s' and a.change_type='INSERT'",
                                     tag.getId().toString());
        List<Map<String, Object>> result = handle.select(query);
        handle.close();

        assertNotNull(result);
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).get("change_type"), "INSERT");
        assertNotNull(result.get(0).get("change_date"));
        DateTime changeDate = new DateTime(result.get(0).get("change_date"));
        assertTrue(Seconds.secondsBetween(changeDate, context.getCreatedDate()).getSeconds() < 2);
        assertEquals(result.get(0).get("changed_by"), context.getUserName());
    }

    @Test
    public void testTagDeleteAudit() {
        UUID accountId = UUID.randomUUID();

        TagStore tagStore = new DefaultTagStore(accountId, ObjectType.ACCOUNT);
        Tag tag = new DescriptiveTag(testTag);
        tagStore.add(tag);

        tagDao.saveEntitiesFromTransaction(dao, accountId, ObjectType.ACCOUNT, tagStore.getEntityList(), context);

        tagStore.remove(tag);
        tagDao.saveEntitiesFromTransaction(dao, accountId, ObjectType.ACCOUNT, tagStore.getEntityList(), context);

        List<Tag> savedTags = tagDao.loadEntities(accountId, ObjectType.ACCOUNT);
        assertEquals(savedTags.size(), 0);

        Handle handle = dbi.open();
        String query = String.format("select * from audit_log a inner join tag_history th on a.record_id = th.history_record_id where a.table_name = 'tag_history' and th.id='%s' and a.change_type='DELETE'",
                                     tag.getId().toString());
        List<Map<String, Object>> result = handle.select(query);
        handle.close();

        assertNotNull(result);
        assertEquals(result.size(), 1);
        assertNotNull(result.get(0).get("change_date"));
        DateTime changeDate = new DateTime(result.get(0).get("change_date"));
        assertTrue(Seconds.secondsBetween(changeDate, context.getUpdatedDate()).getSeconds() < 2);
        assertEquals(result.get(0).get("changed_by"), context.getUserName());
    }
    
    public interface TestSqlDao extends Transmogrifier {}
}
