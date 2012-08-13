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

package com.ning.billing.util.audit.dao;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.util.ChangeType;
import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.glue.AuditModule;
import com.ning.billing.util.tag.MockTagStoreModuleSql;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.dao.AuditedTagDao;
import com.ning.billing.util.tag.dao.TagDefinitionDao;

import com.google.inject.Inject;

@Guice(modules = {MockTagStoreModuleSql.class, AuditModule.class})
public class TestDefaultAuditDao extends UtilTestSuiteWithEmbeddedDB {

    @Inject
    private TagDefinitionDao tagDefinitionDao;

    @Inject
    private AuditedTagDao tagDao;

    @Inject
    private AuditDao auditDao;

    @Inject
    private Clock clock;

    @Inject
    private Bus bus;

    @Inject
    private IDBI dbi;

    private CallContext context;
    private UUID tagId;

    @BeforeClass(groups = "slow")
    public void setup() throws IOException {
        context = new DefaultCallContextFactory(clock).createCallContext("Audit DAO test", CallOrigin.TEST, UserType.TEST, UUID.randomUUID());
        bus.start();
    }

    @AfterClass(groups = "slow")
    public void tearDown() {
        bus.stop();
    }

    @Test(groups = "slow")
    public void testRetrieveAuditsDirectly() throws Exception {
        addTag();

        // Verify we get an audit entry for the tag_history table
        final Handle handle = dbi.open();
        final String tagHistoryString = (String) handle.select("select id from tag_history limit 1").get(0).get("id");
        handle.close();

        final List<AuditLog> auditLogs = auditDao.getAuditLogsForId(TableName.TAG_HISTORY, UUID.fromString(tagHistoryString));
        verifyAuditLogsForTag(auditLogs);
    }

    @Test(groups = "slow")
    public void testRetrieveAuditsViaHistory() throws Exception {
        addTag();

        final List<AuditLog> auditLogs = auditDao.getAuditLogsForId(TableName.TAG, tagId);
        verifyAuditLogsForTag(auditLogs);
    }

    private void addTag() throws TagDefinitionApiException, TagApiException {
        // Create a tag definition
        final TagDefinition tagDefinition = tagDefinitionDao.create(UUID.randomUUID().toString().substring(0, 5),
                                                                    UUID.randomUUID().toString().substring(0, 5),
                                                                    context);
        Assert.assertEquals(tagDefinitionDao.getById(tagDefinition.getId()), tagDefinition);

        // Create a tag
        final UUID objectId = UUID.randomUUID();
        tagDao.insertTag(objectId, ObjectType.ACCOUNT, tagDefinition.getId(), context);
        final Map<String, Tag> tags = tagDao.loadEntities(objectId, ObjectType.ACCOUNT);
        Assert.assertEquals(tags.size(), 1);
        final Tag tag = tags.values().iterator().next();
        Assert.assertEquals(tag.getTagDefinitionId(), tagDefinition.getId());
        tagId = tag.getId();
    }

    private void verifyAuditLogsForTag(final List<AuditLog> auditLogs) {
        Assert.assertEquals(auditLogs.size(), 1);
        Assert.assertEquals(auditLogs.get(0).getUserToken(), context.getUserToken().toString());
        Assert.assertEquals(auditLogs.get(0).getChangeType(), ChangeType.INSERT);
        Assert.assertNull(auditLogs.get(0).getComment());
        Assert.assertNull(auditLogs.get(0).getReasonCode());
        Assert.assertEquals(auditLogs.get(0).getUserName(), context.getUserName());
        Assert.assertNotNull(auditLogs.get(0).getCreatedDate());
    }
}
