package com.ning.billing.util.tag.dao;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.tag.Tag;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class AuditedTagDao {
    public void saveTags(Transmogrifier dao, UUID objectId, String objectType, List<Tag> tags, CallContext context) {
        TagSqlDao tagSqlDao = dao.become(TagSqlDao.class);

        // get list of existing tags
        List<Tag> existingTags = tagSqlDao.load(objectId.toString(), objectType);

        // sort into tags to update (tagsToUpdate), tags to add (tags), and tags to delete (existingTags)
        Iterator<Tag> tagIterator = tags.iterator();
        while (tagIterator.hasNext()) {
            Tag tag = tagIterator.next();

            Iterator<Tag> existingTagIterator = existingTags.iterator();
            while (existingTagIterator.hasNext()) {
                Tag existingTag = existingTagIterator.next();
                if (tag.getTagDefinitionName().equals(existingTag.getTagDefinitionName())) {
                    // if the tags match, remove from both lists
                    // in the case of tag, this just means the tag remains associated
                    tagIterator.remove();
                    existingTagIterator.remove();
                }
            }
        }

        tagSqlDao.batchInsertFromTransaction(objectId.toString(), objectType, tags, context);
        tagSqlDao.batchDeleteFromTransaction(objectId.toString(), objectType, existingTags, context);

        TagAuditSqlDao auditDao = dao.become(TagAuditSqlDao.class);
        auditDao.batchInsertFromTransaction(objectId.toString(), objectType, tags, context);
        auditDao.batchDeleteFromTransaction(objectId.toString(), objectType, existingTags, context);
    }
}
