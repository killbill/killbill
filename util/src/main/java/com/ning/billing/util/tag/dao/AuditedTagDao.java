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

package com.ning.billing.util.tag.dao;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.util.ChangeType;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.dao.AuditedCollectionDaoBase;
import com.ning.billing.util.dao.EntityAudit;
import com.ning.billing.util.dao.EntityHistory;
import com.ning.billing.util.dao.Mapper;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.collection.dao.UpdatableEntityCollectionSqlDao;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.DefaultControlTag;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AuditedTagDao extends AuditedCollectionDaoBase<Tag> implements TagDao {
    private final TagSqlDao tagSqlDao;

    @Inject
    public AuditedTagDao(final IDBI dbi) {
        this.tagSqlDao = dbi.onDemand(TagSqlDao.class);
    }

    @Override
    public void insertTag(final UUID objectId, final ObjectType objectType,
                          final TagDefinition tagDefinition, final CallContext context) {
        tagSqlDao.inTransaction(new Transaction<Void, TagSqlDao>() {
            @Override
            public Void inTransaction(final TagSqlDao tagSqlDao, final TransactionStatus status) throws Exception {
                String tagId = UUID.randomUUID().toString();
                String tagName = tagDefinition.getName();
                tagSqlDao.addTagFromTransaction(tagId, tagName, objectId.toString(), objectType, context);

                Tag tag = tagSqlDao.findTag(tagName, objectId.toString(), objectType);
                List<Tag> tagList = new ArrayList<Tag>();
                tagList.add(tag);

                List<Mapper<UUID, Long>> recordIds = tagSqlDao.getRecordIds(objectId.toString(), objectType);
                Map<UUID, Long> recordIdMap = convertToHistoryMap(recordIds);

                List<EntityHistory<Tag>> entityHistories = new ArrayList<EntityHistory<Tag>>();
                entityHistories.addAll(convertToHistory(tagList, recordIdMap, ChangeType.INSERT));

                Long maxHistoryRecordId = tagSqlDao.getMaxHistoryRecordId();
                tagSqlDao.addHistoryFromTransaction(objectId.toString(), objectType, entityHistories, context);

                // have to fetch history record ids to update audit log
                List<Mapper<Long, Long>> historyRecordIds = tagSqlDao.getHistoryRecordIds(maxHistoryRecordId);
                Map<Long, Long> historyRecordIdMap = convertToAuditMap(historyRecordIds);
                List<EntityAudit> entityAudits = convertToAudits(entityHistories, historyRecordIdMap);
                tagSqlDao.insertAuditFromTransaction(entityAudits, context);

                return null;
            }
        });
    }

    @Override
    public void insertTags(UUID objectId, ObjectType objectType, List<TagDefinition> tagDefinitions, CallContext context) {
        List<Tag> tags = new ArrayList<Tag>();
        for (TagDefinition tagDefinition : tagDefinitions) {
            if (tagDefinition.isControlTag()) {
                ControlTagType controlTagType = ControlTagType.valueOf(tagDefinition.getName());
                tags.add(new DefaultControlTag(controlTagType));
            } else {
                tags.add(new DescriptiveTag(tagDefinition));
            }
        }

        saveEntities(objectId, objectType, tags, context);
    }

    @Override
    public void deleteTag(final UUID objectId, final ObjectType objectType,
                          final TagDefinition tagDefinition, final CallContext context) {
        tagSqlDao.inTransaction(new Transaction<Void, TagSqlDao>() {
            @Override
            public Void inTransaction(final TagSqlDao tagSqlDao, final TransactionStatus status) throws Exception {
                String tagName = tagDefinition.getName();
                Tag tag = tagSqlDao.findTag(tagName, objectId.toString(), objectType);

                if (tag == null) {
                    throw new InvoiceApiException(ErrorCode.TAG_DOES_NOT_EXIST, tagName);
                }

                List<Tag> tagList = new ArrayList<Tag>();
                tagList.add(tag);

                List<Mapper<UUID, Long>> recordIds = tagSqlDao.getRecordIds(objectId.toString(), objectType);
                Map<UUID, Long> recordIdMap = convertToHistoryMap(recordIds);

                tagSqlDao.deleteFromTransaction(objectId.toString(), objectType, tagList, context);

                List<EntityHistory<Tag>> entityHistories = new ArrayList<EntityHistory<Tag>>();
                entityHistories.addAll(convertToHistory(tagList, recordIdMap, ChangeType.DELETE));

                Long maxHistoryRecordId = tagSqlDao.getMaxHistoryRecordId();
                tagSqlDao.addHistoryFromTransaction(objectId.toString(), objectType, entityHistories, context);

                // have to fetch history record ids to update audit log
                List<Mapper<Long, Long>> historyRecordIds = tagSqlDao.getHistoryRecordIds(maxHistoryRecordId);
                Map<Long, Long> historyRecordIdMap = convertToAuditMap(historyRecordIds);
                List<EntityAudit> entityAudits = convertToAudits(entityHistories, historyRecordIdMap);
                tagSqlDao.insertAuditFromTransaction(entityAudits, context);

                return null;
            }
        });
    }

    @Override
    protected TableName getTableName() {
        return TableName.TAG_HISTORY;
    }

    @Override
    protected UpdatableEntityCollectionSqlDao<Tag> transmogrifyDao(Transmogrifier transactionalDao) {
        return transactionalDao.become(TagSqlDao.class);
    }

    @Override
    protected UpdatableEntityCollectionSqlDao<Tag> getSqlDao() {
        return tagSqlDao;
    }

    @Override
    protected String getKey(Tag entity) {
        return entity.getTagDefinitionName();
    }
}
