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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.exceptions.TransactionFailedException;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.util.ChangeType;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.dao.AuditedCollectionDaoBase;
import com.ning.billing.util.dao.EntityAudit;
import com.ning.billing.util.dao.EntityHistory;
import com.ning.billing.util.dao.Mapper;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.collection.dao.UpdatableEntityCollectionSqlDao;
import com.ning.billing.util.events.TagInternalEvent;
import com.ning.billing.util.svcsapi.bus.InternalBus;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.DefaultTagDefinition;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.api.user.TagEventBuilder;

import com.google.inject.Inject;

public class AuditedTagDao extends AuditedCollectionDaoBase<Tag, Tag> implements TagDao {

    private static final Logger log = LoggerFactory.getLogger(AuditedTagDao.class);

    private final TagSqlDao tagSqlDao;
    private final TagEventBuilder tagEventBuilder;
    private final InternalBus bus;

    @Inject
    public AuditedTagDao(final IDBI dbi, final TagEventBuilder tagEventBuilder, final InternalBus bus) {
        this.tagEventBuilder = tagEventBuilder;
        this.bus = bus;
        this.tagSqlDao = dbi.onDemand(TagSqlDao.class);
    }

    @Override
    protected Tag getEquivalenceObjectFor(final Tag obj) {
        return obj;
    }

    private TagDefinition getTagDefinitionFromTransaction(final UUID tagDefinitionId, final InternalTenantContext context) throws TagApiException {
        TagDefinition tagDefintion = null;
        for (final ControlTagType t : ControlTagType.values()) {
            if (t.getId().equals(tagDefinitionId)) {
                tagDefintion = new DefaultTagDefinition(t);
                break;
            }
        }
        if (tagDefintion == null) {
            final TagDefinitionSqlDao transTagDefintionSqlDao = tagSqlDao.become(TagDefinitionSqlDao.class);
            tagDefintion = transTagDefintionSqlDao.getById(tagDefinitionId.toString(), context);
        }

        if (tagDefintion == null) {
            throw new TagApiException(ErrorCode.TAG_DEFINITION_DOES_NOT_EXIST, tagDefinitionId);
        }
        return tagDefintion;
    }

    @Override
    public void insertTag(final UUID objectId, final ObjectType objectType, final UUID tagDefinitionId, final InternalCallContext context)
            throws TagApiException {
        tagSqlDao.inTransaction(new Transaction<Void, TagSqlDao>() {
            @Override
            public Void inTransaction(final TagSqlDao transTagSqlDao, final TransactionStatus status) throws Exception {

                final String tagId = UUID.randomUUID().toString();
                final TagDefinition tagDefinition = getTagDefinitionFromTransaction(tagDefinitionId, context);

                // Create the tag
                tagSqlDao.addTagFromTransaction(tagId, tagDefinitionId.toString(), objectId.toString(), objectType, context);

                final Tag tag = tagSqlDao.findTag(tagDefinitionId.toString(), objectId.toString(), objectType, context);
                final List<Tag> tagList = Arrays.asList(tag);

                // Gather the tag ids for this object id
                final List<Mapper<UUID, Long>> recordIds = tagSqlDao.getRecordIds(objectId.toString(), objectType, context);
                final Map<UUID, Long> recordIdMap = convertToHistoryMap(recordIds, objectType);

                // Update the history table
                final List<EntityHistory<Tag>> entityHistories = convertToHistory(tagList, recordIdMap, ChangeType.INSERT);
                final Long maxHistoryRecordId = tagSqlDao.getMaxHistoryRecordId(context);
                tagSqlDao.addHistoryFromTransaction(objectId.toString(), objectType, entityHistories, context);

                // Have to fetch the history record ids to update the audit log
                final List<Mapper<Long, Long>> historyRecordIds = tagSqlDao.getHistoryRecordIds(maxHistoryRecordId, context);
                final Map<Long, Long> historyRecordIdMap = convertToAuditMap(historyRecordIds);
                final List<EntityAudit> entityAudits = convertToAudits(entityHistories, historyRecordIdMap, context);
                tagSqlDao.insertAuditFromTransaction(entityAudits, context);

                // Post an event to the Bus
                final TagInternalEvent tagEvent;
                if (tagDefinition.isControlTag()) {
                    tagEvent = tagEventBuilder.newControlTagCreationEvent(tag.getId(), objectId, objectType, tagDefinition, context);
                } else {
                    tagEvent = tagEventBuilder.newUserTagCreationEvent(tag.getId(), objectId, objectType, tagDefinition, context);
                }
                try {
                    bus.postFromTransaction(tagEvent, tagSqlDao, context);
                } catch (InternalBus.EventBusException e) {
                    log.warn("Failed to post tag creation event for tag " + tag.getId().toString(), e);
                }
                return null;
            }
        });
    }

    @Override
    public void deleteTag(final UUID objectId, final ObjectType objectType, final UUID tagDefinitionId, final InternalCallContext context) throws TagApiException {
        try {
            tagSqlDao.inTransaction(new Transaction<Void, TagSqlDao>() {
                @Override
                public Void inTransaction(final TagSqlDao transTagSqlDao, final TransactionStatus status) throws Exception {

                    final TagDefinition tagDefinition = getTagDefinitionFromTransaction(tagDefinitionId, context);
                    final Tag tag = tagSqlDao.findTag(tagDefinitionId.toString(), objectId.toString(), objectType, context);
                    if (tag == null) {
                        throw new TagApiException(ErrorCode.TAG_DOES_NOT_EXIST, tagDefinition.getName());
                    }

                    final List<Tag> tagList = Arrays.asList(tag);

                    // Before the deletion, gather the tag ids for this object id
                    final List<Mapper<UUID, Long>> recordIds = tagSqlDao.getRecordIds(objectId.toString(), objectType, context);
                    final Map<UUID, Long> recordIdMap = convertToHistoryMap(recordIds, objectType);

                    // Delete the tag
                    tagSqlDao.deleteFromTransaction(objectId.toString(), objectType, tagList, context);

                    // Update the history table
                    final List<EntityHistory<Tag>> entityHistories = convertToHistory(tagList, recordIdMap, ChangeType.DELETE);
                    final Long maxHistoryRecordId = tagSqlDao.getMaxHistoryRecordId(context);
                    tagSqlDao.addHistoryFromTransaction(objectId.toString(), objectType, entityHistories, context);

                    // Have to fetch the history record ids to update the audit log
                    final List<Mapper<Long, Long>> historyRecordIds = tagSqlDao.getHistoryRecordIds(maxHistoryRecordId, context);
                    final Map<Long, Long> historyRecordIdMap = convertToAuditMap(historyRecordIds);
                    final List<EntityAudit> entityAudits = convertToAudits(entityHistories, historyRecordIdMap, context);
                    tagSqlDao.insertAuditFromTransaction(entityAudits, context);

                    // Post an event to the Bus
                    final TagInternalEvent tagEvent;
                    if (tagDefinition.isControlTag()) {
                        tagEvent = tagEventBuilder.newControlTagDeletionEvent(tag.getId(), objectId, objectType, tagDefinition, context);
                    } else {
                        tagEvent = tagEventBuilder.newUserTagDeletionEvent(tag.getId(), objectId, objectType, tagDefinition, context);
                    }
                    try {
                        bus.postFromTransaction(tagEvent, tagSqlDao, context);
                    } catch (InternalBus.EventBusException e) {
                        log.warn("Failed to post tag deletion event for tag " + tag.getId().toString(), e);
                    }
                    return null;
                }
            });
        } catch (TransactionFailedException exception) {

            if (exception.getCause() instanceof TagDefinitionApiException) {
                throw (TagApiException) exception.getCause();
            } else {
                throw exception;
            }
        }
    }

    @Override
    protected TableName getTableName(final InternalTenantContext context) {
        return TableName.TAG_HISTORY;
    }

    @Override
    protected UpdatableEntityCollectionSqlDao<Tag> transmogrifyDao(final Transmogrifier transactionalDao, final InternalTenantContext context) {
        return transactionalDao.become(TagSqlDao.class);
    }

    @Override
    protected UpdatableEntityCollectionSqlDao<Tag> getSqlDao(final InternalTenantContext context) {
        return tagSqlDao;
    }

    @Override
    protected String getKey(final Tag entity, final InternalTenantContext context) {
        return entity.getId().toString();
    }
}
