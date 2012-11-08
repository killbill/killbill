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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.ObjectType;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import com.ning.billing.util.events.TagInternalEvent;
import com.ning.billing.util.svcsapi.bus.InternalBus;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.DefaultControlTag;
import com.ning.billing.util.tag.DefaultTagDefinition;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.api.user.TagEventBuilder;

import com.google.inject.Inject;

public class AuditedTagDao implements TagDao {

    private static final Logger log = LoggerFactory.getLogger(AuditedTagDao.class);

    private final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;
    private final TagSqlDao tagSqlDao;
    private final TagEventBuilder tagEventBuilder;
    private final InternalBus bus;
    private final Clock clock;

    @Inject
    public AuditedTagDao(final IDBI dbi, final TagEventBuilder tagEventBuilder, final InternalBus bus, final Clock clock) {
        this.clock = clock;
        this.tagEventBuilder = tagEventBuilder;
        this.bus = bus;
        this.tagSqlDao = dbi.onDemand(TagSqlDao.class);
        this.transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi);
    }

    protected Tag getEquivalenceObjectFor(final Tag obj) {
        return obj;
    }

    private TagDefinition getTagDefinitionFromTransaction(final UUID tagDefinitionId, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalTenantContext context) throws TagApiException {
        TagDefinition tagDefintion = null;
        for (final ControlTagType t : ControlTagType.values()) {
            if (t.getId().equals(tagDefinitionId)) {
                tagDefintion = new DefaultTagDefinition(t);
                break;
            }
        }
        if (tagDefintion == null) {
            final TagDefinitionSqlDao transTagDefintionSqlDao = entitySqlDaoWrapperFactory.become(TagDefinitionSqlDao.class);
            tagDefintion = transTagDefintionSqlDao.getById(tagDefinitionId.toString(), context);
        }

        if (tagDefintion == null) {
            throw new TagApiException(ErrorCode.TAG_DEFINITION_DOES_NOT_EXIST, tagDefinitionId);
        }
        return tagDefintion;
    }

    @Override
    public Tag getTagById(final UUID tagId, final InternalTenantContext context) {
        return tagSqlDao.getById(tagId.toString(), context);
    }

    @Override
    public Map<String, Tag> getTags(final UUID objectId, final ObjectType objectType, final InternalTenantContext internalTenantContext) {
        List<Tag> tags = tagSqlDao.getTagsForObject(objectId, objectType, internalTenantContext);
        Map<String, Tag> result = new HashMap<String, Tag>();
        for (Tag cur : tags) {
            result.put(cur.getId().toString(), cur);
        }
        return result;
    }

    @Override
    public void insertTag(final UUID objectId, final ObjectType objectType, final UUID tagDefinitionId, final InternalCallContext context)
            throws TagApiException {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {

                final TagDefinition tagDefinition = getTagDefinitionFromTransaction(tagDefinitionId, entitySqlDaoWrapperFactory, context);

                final Tag tag = tagDefinition.isControlTag() ? new DefaultControlTag(ControlTagType.getTypeFromId(tagDefinition.getId()), objectType, objectId, clock.getUTCNow()) :
                                new DescriptiveTag(tagDefinition.getId(), objectType, objectId, clock.getUTCNow());

                // Create the tag
                entitySqlDaoWrapperFactory.become(TagSqlDao.class).create(tag, context);

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

        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {

            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {

                final TagDefinition tagDefinition = getTagDefinitionFromTransaction(tagDefinitionId, entitySqlDaoWrapperFactory, context);
                final TagSqlDao transactional = entitySqlDaoWrapperFactory.become(TagSqlDao.class);
                final List<Tag> tags = transactional.getTagsForObject(objectId, objectType, context);
                Tag tag = null;
                for (Tag cur : tags) {
                    if (cur.getTagDefinitionId().equals(tagDefinitionId)) {
                        tag = cur;
                        break;
                    }
                }
                if (tag == null) {
                    throw new TagApiException(ErrorCode.TAG_DOES_NOT_EXIST, tagDefinition.getName());
                }

                // Delete the tag
                transactional.deleteById(tag.getId(), context);

                // Post an event to the Bus
                final TagInternalEvent tagEvent;
                if (tagDefinition.isControlTag()) {
                    tagEvent = tagEventBuilder.newControlTagDeletionEvent(tag.getId(), objectId, objectType, tagDefinition, context);
                } else {
                    tagEvent = tagEventBuilder.newUserTagDeletionEvent(tag.getId(), objectId, objectType, tagDefinition, context);
                }
                try {
                    // TODO ETX
                    bus.postFromTransaction(tagEvent, tagSqlDao, context);
                } catch (InternalBus.EventBusException e) {
                    log.warn("Failed to post tag deletion event for tag " + tag.getId().toString(), e);
                }
                return null;
            }
        });

    }
}
