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

import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.Handle;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.util.UtilTestSuiteWithEmbeddedDB;
import com.ning.billing.util.api.AuditLevel;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.dao.TagDefinitionModelDao;
import com.ning.billing.util.tag.dao.TagModelDao;

public class TestDefaultAuditDao extends UtilTestSuiteWithEmbeddedDB {

    private UUID tagId;

    @Test(groups = "slow")
    public void testRetrieveAuditsDirectly() throws Exception {
        addTag();

        // Verify we get an audit entry for the tag_history table
        final Handle handle = getDBI().open();
        final String tagHistoryString = (String) handle.select("select id from tag_history limit 1").get(0).get("id");
        handle.close();

        for (final AuditLevel level : AuditLevel.values()) {
            final List<AuditLog> auditLogs = auditDao.getAuditLogsForId(TableName.TAG_HISTORY, UUID.fromString(tagHistoryString), level, internalCallContext);
            verifyAuditLogsForTag(auditLogs, level);
        }
    }

    @Test(groups = "slow")
    public void testRetrieveAuditsViaHistory() throws Exception {
        addTag();

        for (final AuditLevel level : AuditLevel.values()) {
            final List<AuditLog> auditLogs = auditDao.getAuditLogsForId(TableName.TAG, tagId, level, internalCallContext);
            verifyAuditLogsForTag(auditLogs, level);
        }
    }

    private void addTag() throws TagDefinitionApiException, TagApiException {
        // Create a tag definition
        final TagDefinitionModelDao tagDefinition = tagDefinitionDao.create(UUID.randomUUID().toString().substring(0, 5),
                                                                            UUID.randomUUID().toString().substring(0, 5),
                                                                            internalCallContext);
        Assert.assertEquals(tagDefinitionDao.getById(tagDefinition.getId(), internalCallContext), tagDefinition);

        // Create a tag
        final UUID objectId = UUID.randomUUID();

        final Tag tag = new DescriptiveTag(tagDefinition.getId(), ObjectType.ACCOUNT, objectId, clock.getUTCNow());

        tagDao.create(new TagModelDao(tag), internalCallContext);
        final List<TagModelDao> tags = tagDao.getTags(objectId, ObjectType.ACCOUNT, internalCallContext);
        Assert.assertEquals(tags.size(), 1);
        final TagModelDao savedTag = tags.get(0);
        Assert.assertEquals(savedTag.getTagDefinitionId(), tagDefinition.getId());
        tagId = savedTag.getId();
    }

    private void verifyAuditLogsForTag(final List<AuditLog> auditLogs, final AuditLevel level) {
        if (AuditLevel.NONE.equals(level)) {
            Assert.assertEquals(auditLogs.size(), 0);
            return;
        }

        Assert.assertEquals(auditLogs.size(), 1);
        Assert.assertEquals(auditLogs.get(0).getUserToken(), internalCallContext.getUserToken().toString());
        Assert.assertEquals(auditLogs.get(0).getChangeType(), ChangeType.INSERT);
        Assert.assertEquals(auditLogs.get(0).getComment(), internalCallContext.getComments());
        Assert.assertEquals(auditLogs.get(0).getReasonCode(), internalCallContext.getReasonCode());
        Assert.assertEquals(auditLogs.get(0).getUserName(), internalCallContext.getCreatedBy());
        Assert.assertNotNull(auditLogs.get(0).getCreatedDate());
    }
}
