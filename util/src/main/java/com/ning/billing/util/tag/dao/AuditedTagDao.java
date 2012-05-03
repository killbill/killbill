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
import com.ning.billing.util.audit.dao.AuditSqlDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.dao.AuditedDaoBase;
import com.ning.billing.util.tag.Tag;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class AuditedTagDao extends AuditedDaoBase implements TagDao {
    private final TagSqlDao tagSqlDao;

    @Inject
    public AuditedTagDao(final IDBI dbi) {
        this.tagSqlDao = dbi.onDemand(TagSqlDao.class);
    }

    @Override
    public void saveTags(final UUID objectId, final String objectType,
                         final List<Tag> tags, final CallContext context) {
        saveTagsFromTransaction(tagSqlDao, objectId, objectType, tags, context);
    }

    @Override
    public void saveTagsFromTransaction(final Transmogrifier dao, final UUID objectId, final String objectType,
                                        final List<Tag> tags, final CallContext context) {
        TagSqlDao tagSqlDao = dao.become(TagSqlDao.class);

        // get list of existing tagStore
        List<Tag> existingTags = tagSqlDao.load(objectId.toString(), objectType);

        // sort into tagStore to update (tagsToUpdate), tagStore to add (tagStore), and tagStore to delete (existingTags)
        Iterator<Tag> tagIterator = tags.iterator();
        while (tagIterator.hasNext()) {
            Tag tag = tagIterator.next();

            Iterator<Tag> existingTagIterator = existingTags.iterator();
            while (existingTagIterator.hasNext()) {
                Tag existingTag = existingTagIterator.next();
                if (tag.getTagDefinitionName().equals(existingTag.getTagDefinitionName())) {
                    // if the tagStore match, remove from both lists
                    // in the case of tag, this just means the tag remains associated
                    tagIterator.remove();
                    existingTagIterator.remove();
                }
            }
        }

        tagSqlDao.batchInsertFromTransaction(objectId.toString(), objectType, tags, context);
        tagSqlDao.batchDeleteFromTransaction(objectId.toString(), objectType, existingTags, context);

        List<String> historyIdsForInsert = getIdList(tags.size());
        tagSqlDao.batchInsertHistoryFromTransaction(objectId.toString(), objectType, historyIdsForInsert, tags, ChangeType.INSERT, context);
        List<String> historyIdsForDelete = getIdList(existingTags.size());
        tagSqlDao.batchInsertHistoryFromTransaction(objectId.toString(), objectType, historyIdsForDelete, existingTags, ChangeType.DELETE, context);

        AuditSqlDao auditSqlDao = tagSqlDao.become(AuditSqlDao.class);
        auditSqlDao.insertAuditFromTransaction("tag_history", historyIdsForInsert, ChangeType.INSERT, context);
        auditSqlDao.insertAuditFromTransaction("tag_history", historyIdsForDelete, ChangeType.DELETE, context);
    }

    @Override
    public List<Tag> loadTags(final UUID objectId, final String objectType) {
        return tagSqlDao.load(objectId.toString(), objectType);
    }

    @Override
    public List<Tag> loadTagsFromTransaction(final Transmogrifier dao, final UUID objectId, final String objectType) {
        TagSqlDao tagSqlDao = dao.become(TagSqlDao.class);
        return tagSqlDao.load(objectId.toString(), objectType);
    }

    @Override
    public void addTag(final String tagName, final UUID objectId, final String objectType, final CallContext context) {
        tagSqlDao.inTransaction(new Transaction<Void, TagSqlDao>() {
            @Override
            public Void inTransaction(final TagSqlDao tagSqlDao, final TransactionStatus status) throws Exception {
                String tagId = UUID.randomUUID().toString();
                tagSqlDao.addTagFromTransaction(tagId, tagName, objectId.toString(), objectType, context);

                TagAuditSqlDao auditDao = tagSqlDao.become(TagAuditSqlDao.class);
                auditDao.addTagFromTransaction(tagId, context);

                return null;
            }
        });
    }

    @Override
    public void removeTag(final String tagName, final UUID objectId, final String objectType, final CallContext context) {
        tagSqlDao.inTransaction(new Transaction<Void, TagSqlDao>() {
            @Override
            public Void inTransaction(final TagSqlDao tagSqlDao, final TransactionStatus status) throws Exception {
                Tag tag = tagSqlDao.findTag(tagName, objectId.toString(), objectType);

                if (tag == null) {
                    throw new InvoiceApiException(ErrorCode.TAG_DOES_NOT_EXIST, tagName);
                }

                tagSqlDao.removeTagFromTransaction(tagName, objectId.toString(), objectType, context);

                TagAuditSqlDao auditDao = tagSqlDao.become(TagAuditSqlDao.class);
                auditDao.removeTagFromTransaction(tag.getId().toString(), context);

                return null;
            }
        });
    }
}
