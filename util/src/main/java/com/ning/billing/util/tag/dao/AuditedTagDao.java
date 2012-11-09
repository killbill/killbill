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

import com.ning.billing.BillingExceptionBase;
import com.ning.billing.ErrorCode;
import com.ning.billing.ObjectType;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.entity.dao.EntityDaoBase;
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

public class AuditedTagDao extends EntityDaoBase<Tag, TagApiException> implements TagDao {

    private static final Logger log = LoggerFactory.getLogger(AuditedTagDao.class);

    private final TagSqlDao tagSqlDao;
    private final TagEventBuilder tagEventBuilder;
    private final InternalBus bus;
    private final Clock clock;

    @Inject
    public AuditedTagDao(final IDBI dbi, final TagEventBuilder tagEventBuilder, final InternalBus bus, final Clock clock) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi));
        this.clock = clock;
        this.tagEventBuilder = tagEventBuilder;
        this.bus = bus;
        this.tagSqlDao = dbi.onDemand(TagSqlDao.class);
    }


    @Override
    public List<Tag> getTags(final UUID objectId, final ObjectType objectType, final InternalTenantContext internalTenantContext) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<Tag>>() {
            @Override
            public List<Tag> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(TagSqlDao.class).getTagsForObject(objectId, objectType, internalTenantContext);
            }
        });
    }

    @Override
    protected void postBusEventFromTransaction(Tag tag, Tag savedTag, ChangeType changeType, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalCallContext context)
            throws BillingExceptionBase {

        final TagInternalEvent tagEvent;
        final TagDefinition tagDefinition = getTagDefinitionFromTransaction(tag.getTagDefinitionId(), entitySqlDaoWrapperFactory, context);
        switch(changeType) {
            case INSERT:
                tagEvent = (tagDefinition.isControlTag()) ?
                           tagEventBuilder.newControlTagCreationEvent(tag.getId(), tag.getObjectId(), tag.getObjectType(), tagDefinition, context) :
                           tagEventBuilder.newUserTagCreationEvent(tag.getId(), tag.getObjectId(), tag.getObjectType(), tagDefinition, context);
                break;
            case DELETE:
                tagEvent = (tagDefinition.isControlTag()) ?
                           tagEventBuilder.newControlTagDeletionEvent(tag.getId(), tag.getObjectId(), tag.getObjectType(), tagDefinition, context) :
                           tagEventBuilder.newUserTagDeletionEvent(tag.getId(), tag.getObjectId(), tag.getObjectType(), tagDefinition, context);
                break;
            default:
                return;
        }

        try {
            bus.postFromTransaction(tagEvent, entitySqlDaoWrapperFactory, context);
        } catch (InternalBus.EventBusException e) {
            log.warn("Failed to post tag event for tag " + tag.getId().toString(), e);
        }
    }


    @Override
    protected TagApiException generateAlreadyExistsException(final Tag entity, final InternalCallContext context) {
        return new TagApiException(ErrorCode.TAG_ALREADY_EXISTS, entity.getId());
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

                postBusEventFromTransaction(tag, tag, ChangeType.DELETE, entitySqlDaoWrapperFactory, context);
                return null;
            }
        });

    }
}
