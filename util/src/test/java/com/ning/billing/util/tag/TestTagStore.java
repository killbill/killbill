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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.tag.dao.AuditedTagDao;
import com.ning.billing.util.tag.dao.TagDao;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
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
import com.ning.billing.util.tag.dao.TagSqlDao;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;


@Test(groups={"slow"})
@Guice(modules = TagStoreModuleMock.class)
public class TestTagStore {
    private final static String ACCOUNT_TYPE = "ACCOUNT";

    @Inject
    private MysqlTestingHelper helper;

    @Inject
    private IDBI dbi;

    private TagSqlDao tagSqlDao;

    @Inject
    private TagDefinitionDao tagDefinitionDao;

    @Inject
    private Clock clock;

    private TagDefinition tag1;
    private TagDefinition tag2;


    private final Logger log = LoggerFactory.getLogger(TestTagStore.class);
    private CallContext context;

    @BeforeClass(alwaysRun = true)
    protected void setup() throws IOException {
        // Health check test to make sure MySQL is setup properly
        try {
            final String utilDdl = IOUtils.toString(TestTagStore.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));

            helper.startMysql();
            helper.initDb(utilDdl);
            tagSqlDao.test();

            context = new DefaultCallContextFactory(clock).createCallContext("Tag store test", CallOrigin.TEST, UserType.TEST);
            tagSqlDao = dbi.onDemand(TagSqlDao.class);

            cleanupTags();
            tag1 = tagDefinitionDao.create("tag1", "First tag", context);
            tag2 = tagDefinitionDao.create("tag2", "Second tag", context);
        }
        catch (Throwable t) {
            log.error("Failed to start tag store tests", t);
            fail(t.toString());
        }
    }

    @AfterClass(alwaysRun = true)
    public void stopMysql()
    {
        helper.stopMysql();
    }

    private void saveTags(final TagSqlDao dao, final String objectType, final UUID accountId,
                          final List<Tag> tagList, final CallContext context)  {
        TagDao tagDao = new AuditedTagDao(dbi);
        tagDao.saveTags(dao, accountId, objectType, tagList, context);
    }


    private void cleanupTags() {
        try {
            helper.getDBI().withHandle(new HandleCallback<Void>() {
                @Override
                public Void withHandle(Handle handle) throws Exception {
                    handle.createScript("delete from tag_definitions").execute();
                    handle.createScript("delete from tag_definition_history").execute();
                    handle.createScript("delete from tags").execute();
                    handle.createScript("delete from tag_history").execute();
                    return null;
                }
            });
        } catch (Throwable ignore) {
        }
    }
    @Test
    public void testTagCreationAndRetrieval() {
        UUID accountId = UUID.randomUUID();

        TagStore tagStore = new DefaultTagStore(accountId, ACCOUNT_TYPE);
        Tag tag = new DescriptiveTag(tag2);
        tagStore.add(tag);

        TagSqlDao dao = dbi.onDemand(TagSqlDao.class);
        saveTags(dao, ACCOUNT_TYPE, accountId, tagStore.getEntityList(), context);

        List<Tag> savedTags = dao.load(accountId.toString(), ACCOUNT_TYPE);
        assertEquals(savedTags.size(), 1);

        Tag savedTag = savedTags.get(0);
        assertEquals(savedTag.getTagDefinitionName(), tag.getTagDefinitionName());
        assertEquals(savedTag.getId(), tag.getId());
    }

    @Test
    public void testControlTagCreation() {
        UUID accountId = UUID.randomUUID();
        TagStore tagStore = new DefaultTagStore(accountId, ACCOUNT_TYPE);

        ControlTag tag = new DefaultControlTag(ControlTagType.AUTO_INVOICING_OFF);
        tagStore.add(tag);
        assertEquals(tagStore.generateInvoice(), false);

        List<Tag> tagList = tagStore.getEntityList();
        saveTags(tagSqlDao, ACCOUNT_TYPE, accountId, tagList, context);

        tagStore.clear();
        assertEquals(tagStore.getEntityList().size(), 0);

        tagList = tagSqlDao.load(accountId.toString(), ACCOUNT_TYPE);
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
            tagDefinition = tagDefinitionDao.create(definitionName, "Test tag for some test purpose", context);
        } catch (TagDefinitionApiException e) {
            fail("Tag definition creation failed.", e);
        }

        DescriptiveTag tag = new DescriptiveTag(tagDefinition);
        tagStore.add(tag);
        assertEquals(tagStore.generateInvoice(), true);

        List<Tag> tagList = tagStore.getEntityList();
        saveTags(tagSqlDao, ACCOUNT_TYPE, accountId, tagList, context);

        tagStore.clear();
        assertEquals(tagStore.getEntityList().size(), 0);

        tagList = tagSqlDao.load(accountId.toString(), ACCOUNT_TYPE);
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

        List<Tag> tagList = tagStore.getEntityList();
        saveTags(tagSqlDao, ACCOUNT_TYPE, accountId, tagList, context);

        tagStore.clear();
        assertEquals(tagStore.getEntityList().size(), 0);

        tagList = tagSqlDao.load(accountId.toString(), ACCOUNT_TYPE);
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

        ControlTag invoiceTag = new DefaultControlTag(ControlTagType.AUTO_INVOICING_OFF);
        tagStore.add(invoiceTag);
        assertEquals(tagStore.generateInvoice(), false);
        assertEquals(tagStore.processPayment(), true);

        ControlTag paymentTag = new DefaultControlTag(ControlTagType.AUTO_PAY_OFF);
        tagStore.add(paymentTag);
        assertEquals(tagStore.generateInvoice(), false);
        assertEquals(tagStore.processPayment(), false);
    }

    @Test(expectedExceptions = TagDefinitionApiException.class)
    public void testTagDefinitionCreationWithControlTagName() throws TagDefinitionApiException {
        String definitionName = ControlTagType.AUTO_PAY_OFF.toString();
        tagDefinitionDao.create(definitionName, "This should break", context);
    }

    @Test
    public void testTagDefinitionDeletionForUnusedDefinition() throws TagDefinitionApiException {
        String definitionName = "TestTag1234";
        tagDefinitionDao.create(definitionName, "Some test tag", context);

        TagDefinition tagDefinition = tagDefinitionDao.getByName(definitionName);
        assertNotNull(tagDefinition);

        tagDefinitionDao.deleteTagDefinition(definitionName, context);
        tagDefinition = tagDefinitionDao.getByName(definitionName);
        assertNull(tagDefinition);
    }

    @Test(expectedExceptions = TagDefinitionApiException.class)
    public void testTagDefinitionDeletionForDefinitionInUse() throws TagDefinitionApiException {
        String definitionName = "TestTag12345";
        tagDefinitionDao.create(definitionName, "Some test tag", context);

        TagDefinition tagDefinition = tagDefinitionDao.getByName(definitionName);
        assertNotNull(tagDefinition);

        UUID objectId = UUID.randomUUID();
        String objectType = "TestType";
        TagStore tagStore = new DefaultTagStore(objectId, objectType);
        Tag tag = new DescriptiveTag(tagDefinition);
        tagStore.add(tag);

        saveTags(tagSqlDao, objectType, objectId, tagStore.getEntityList(), context);

        List<Tag> tags = tagSqlDao.load(objectId.toString(), objectType);
        assertEquals(tags.size(), 1);

        tagDefinitionDao.deleteTagDefinition(definitionName, context);
    }

    @Test
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
        String objectType = "TestType";
        TagStore tagStore = new DefaultTagStore(objectId, objectType);
        Tag tag = new DescriptiveTag(tagDefinition);
        tagStore.add(tag);

        saveTags(tagSqlDao, objectType, objectId, tagStore.getEntityList(), context);

        List<Tag> tags = tagSqlDao.load(objectId.toString(), objectType);
        assertEquals(tags.size(), 1);

        try {
            tagDefinitionDao.deleteAllTagsForDefinition(definitionName, context);
        } catch (TagDefinitionApiException e) {
            fail("Could not delete tags for tag definition", e);
        }

        try {
            tagDefinitionDao.deleteTagDefinition(definitionName, context);
        } catch (TagDefinitionApiException e) {
            fail("Could not delete tag definition", e);
        }
    }

    @Test
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
            fail("Could not delete tags for tag definition", e);
        }

        try {
            tagDefinitionDao.deleteTagDefinition(definitionName, context);
        } catch (TagDefinitionApiException e) {
            fail("Could not delete tag definition", e);
        }
    }

    @Test(expectedExceptions = TagDefinitionApiException.class)
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

    @Test
    public void testGetTagDefinitions() {
        List<TagDefinition> definitionList = tagDefinitionDao.getTagDefinitions();
        assertTrue(definitionList.size() >= ControlTagType.values().length);
    }

    @Test
    public void testTagInsertAudit() {
        UUID accountId = UUID.randomUUID();

        TagStore tagStore = new DefaultTagStore(accountId, ACCOUNT_TYPE);
        Tag tag = new DescriptiveTag(tag2);
        tagStore.add(tag);

        TagSqlDao dao = dbi.onDemand(TagSqlDao.class);
        saveTags(dao, ACCOUNT_TYPE, accountId, tagStore.getEntityList(), context);

        List<Tag> savedTags = dao.load(accountId.toString(), ACCOUNT_TYPE);
        assertEquals(savedTags.size(), 1);

        Tag savedTag = savedTags.get(0);
        assertEquals(savedTag.getTagDefinitionName(), tag.getTagDefinitionName());
        assertEquals(savedTag.getId(), tag.getId());

        Handle handle = dbi.open();
        String query = String.format("select * from audit_log where table_name = 'Tag' and record_id='%s'",
                                     tag.getId().toString());
        List<Map<String, Object>> result = handle.select(query);
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

        TagStore tagStore = new DefaultTagStore(accountId, ACCOUNT_TYPE);
        Tag tag = new DescriptiveTag(tag2);
        tagStore.add(tag);

        TagSqlDao dao = dbi.onDemand(TagSqlDao.class);
        saveTags(dao, ACCOUNT_TYPE, accountId, tagStore.getEntityList(), context);
        saveTags(dao, ACCOUNT_TYPE, accountId, new ArrayList<Tag>(), context);

        List<Tag> savedTags = dao.load(accountId.toString(), ACCOUNT_TYPE);
        assertEquals(savedTags.size(), 0);

        Handle handle = dbi.open();
        String query = String.format("select * from audit_log where table_name = 'Tag' and record_id='%s' and change_type='DELETE'",
                                     tag.getId().toString());
        List<Map<String, Object>> result = handle.select(query);
        assertNotNull(result);
        assertEquals(result.size(), 1);
        assertNotNull(result.get(0).get("change_date"));
        DateTime changeDate = new DateTime(result.get(0).get("change_date"));
        assertTrue(Seconds.secondsBetween(changeDate, context.getUpdatedDate()).getSeconds() < 2);
        assertEquals(result.get(0).get("changed_by"), context.getUserName());
    }

//    @Test
//    public void testTagDefinitionInsertAudit() {
//
//        fail();
//    }
//
//    @Test
//    public void testTagDefinitionUpdateAudit() {
//
//        fail();
//    }
//
//    @Test
//    public void testTagDefinitionDeleteAudit() {
//
//        fail();
//    }
}
