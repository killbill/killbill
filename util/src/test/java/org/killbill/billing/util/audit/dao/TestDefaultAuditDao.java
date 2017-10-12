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

package org.killbill.billing.util.audit.dao;

import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.Handle;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.ObjectType;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.killbill.billing.util.audit.AccountAuditLogsForObjectType;
import org.killbill.billing.util.audit.AuditLog;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.tag.DescriptiveTag;
import org.killbill.billing.util.tag.Tag;
import org.killbill.billing.util.tag.dao.TagDefinitionModelDao;
import org.killbill.billing.util.tag.dao.TagModelDao;

public class TestDefaultAuditDao extends UtilTestSuiteWithEmbeddedDB {

    private TagModelDao tag;

    @Test(groups = "slow")
    public void testRetrieveAuditsDirectly() throws Exception {
        addTag();

        // Verify we get an audit entry for the tag_history table
        final Handle handle = dbi.open();
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
            final List<AuditLog> auditLogs = auditDao.getAuditLogsForId(TableName.TAG, tag.getId(), level, internalCallContext);
            verifyAuditLogsForTag(auditLogs, level);

            final AccountAuditLogs accountAuditLogs = auditDao.getAuditLogsForAccountRecordId(level, internalCallContext);
            verifyAuditLogsForTag(accountAuditLogs.getAuditLogs(ObjectType.TAG).getAuditLogs(tag.getId()), level);

            final AccountAuditLogsForObjectType accountAuditLogsForObjectType = auditDao.getAuditLogsForAccountRecordId(TableName.TAG, level, internalCallContext);
            verifyAuditLogsForTag(accountAuditLogsForObjectType.getAuditLogs(tag.getId()), level);
        }
    }

    @Test(groups = "slow")
    public void testVerifyAuditCachesAreCleared() throws Exception {
        addTag();
        final List<AuditLog> firstAuditLogs = auditDao.getAuditLogsForId(TableName.TAG, tag.getId(), AuditLevel.FULL, internalCallContext);
        Assert.assertEquals(firstAuditLogs.size(), 1);
        Assert.assertEquals(firstAuditLogs.get(0).getChangeType(), ChangeType.INSERT);

        eventsListener.pushExpectedEvent(NextEvent.TAG);
        tagDao.deleteTag(tag.getObjectId(), tag.getObjectType(), tag.getTagDefinitionId(), internalCallContext);
        assertListenerStatus();

        final List<AuditLog> secondAuditLogs = auditDao.getAuditLogsForId(TableName.TAG, tag.getId(), AuditLevel.FULL, internalCallContext);
        Assert.assertEquals(secondAuditLogs.size(), 2);
        Assert.assertEquals(secondAuditLogs.get(0).getChangeType(), ChangeType.INSERT);
        Assert.assertEquals(secondAuditLogs.get(1).getChangeType(), ChangeType.DELETE);
    }

    private void addTag() throws TagDefinitionApiException, TagApiException {
        // Create a tag definition
        eventsListener.pushExpectedEvent(NextEvent.TAG_DEFINITION);
        final TagDefinitionModelDao tagDefinition = tagDefinitionDao.create(UUID.randomUUID().toString().substring(0, 5),
                                                                            UUID.randomUUID().toString().substring(0, 5),
                                                                            ObjectType.ACCOUNT.name(),
                                                                            internalCallContext);
        assertListenerStatus();

        Assert.assertEquals(tagDefinitionDao.getById(tagDefinition.getId(), internalCallContext), tagDefinition);

        // Create a tag
        final UUID objectId = UUID.randomUUID();

        final Tag theTag = new DescriptiveTag(tagDefinition.getId(), ObjectType.ACCOUNT, objectId, clock.getUTCNow());

        eventsListener.pushExpectedEvent(NextEvent.TAG);
        tagDao.create(new TagModelDao(theTag), internalCallContext);
        assertListenerStatus();

        final List<TagModelDao> tags = tagDao.getTagsForObject(objectId, ObjectType.ACCOUNT, false, internalCallContext);
        Assert.assertEquals(tags.size(), 1);
        tag = tags.get(0);
        Assert.assertEquals(tag.getTagDefinitionId(), tagDefinition.getId());
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
