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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.exceptions.TransactionFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.BillingExceptionBase;
import com.ning.billing.ErrorCode;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.entity.dao.EntityDaoBase;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import com.ning.billing.util.events.TagDefinitionInternalEvent;
import com.ning.billing.util.svcsapi.bus.InternalBus;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.api.user.TagEventBuilder;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

public class DefaultTagDefinitionDao extends EntityDaoBase<TagDefinitionModelDao, TagDefinition, TagDefinitionApiException> implements TagDefinitionDao {

    private static final Logger log = LoggerFactory.getLogger(DefaultTagDefinitionDao.class);

    private final TagEventBuilder tagEventBuilder;
    private final InternalBus bus;

    @Inject
    public DefaultTagDefinitionDao(final IDBI dbi, final TagEventBuilder tagEventBuilder, final InternalBus bus, final Clock clock,
                                   final CacheControllerDispatcher controllerDispatcher, final NonEntityDao nonEntityDao) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi, clock, controllerDispatcher, nonEntityDao), TagDefinitionSqlDao.class);
        this.tagEventBuilder = tagEventBuilder;
        this.bus = bus;
    }

    @Override
    public List<TagDefinitionModelDao> getTagDefinitions(final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<TagDefinitionModelDao>>() {
            @Override
            public List<TagDefinitionModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                // Get user definitions from the database
                final List<TagDefinitionModelDao> definitionList = new LinkedList<TagDefinitionModelDao>();
                definitionList.addAll(entitySqlDaoWrapperFactory.become(TagDefinitionSqlDao.class).get(context));

                // Add control tag definitions
                for (final ControlTagType controlTag : ControlTagType.values()) {
                    definitionList.add(new TagDefinitionModelDao(controlTag));
                }
                return definitionList;
            }
        });
    }

    @Override
    public TagDefinitionModelDao getByName(final String definitionName, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<TagDefinitionModelDao>() {
            @Override
            public TagDefinitionModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                for (final ControlTagType controlTag : ControlTagType.values()) {
                    if (controlTag.name().equals(definitionName)) {
                        return new TagDefinitionModelDao(controlTag);
                    }
                }
                return entitySqlDaoWrapperFactory.become(TagDefinitionSqlDao.class).getByName(definitionName, context);
            }
        });
    }

    @Override
    public TagDefinitionModelDao getById(final UUID definitionId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<TagDefinitionModelDao>() {
            @Override
            public TagDefinitionModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                for (final ControlTagType controlTag : ControlTagType.values()) {
                    if (controlTag.getId().equals(definitionId)) {
                        return new TagDefinitionModelDao(controlTag);
                    }
                }
                return entitySqlDaoWrapperFactory.become(TagDefinitionSqlDao.class).getById(definitionId.toString(), context);
            }
        });
    }

    @Override
    public List<TagDefinitionModelDao> getByIds(final Collection<UUID> definitionIds, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<TagDefinitionModelDao>>() {
            @Override
            public List<TagDefinitionModelDao> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final List<TagDefinitionModelDao> result = new LinkedList<TagDefinitionModelDao>();
                for (final UUID cur : definitionIds) {
                    for (final ControlTagType controlTag : ControlTagType.values()) {
                        if (controlTag.getId().equals(cur)) {
                            result.add(new TagDefinitionModelDao(controlTag));
                            break;
                        }
                    }
                }
                if (definitionIds.size() > 0) {
                    result.addAll(entitySqlDaoWrapperFactory.become(TagDefinitionSqlDao.class).getByIds(Collections2.transform(definitionIds, new Function<UUID, String>() {
                        @Override
                        public String apply(final UUID input) {
                            return input.toString();
                        }

                    }), context));
                }
                return result;
            }
        });
    }

    @Override
    public TagDefinitionModelDao create(final String definitionName, final String description,
                                        final InternalCallContext context) throws TagDefinitionApiException {
        // Make sure a control tag with this name don't already exist
        if (TagModelDaoHelper.isControlTag(definitionName)) {
            throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_CONFLICTS_WITH_CONTROL_TAG, definitionName);
        }

        try {
            return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<TagDefinitionModelDao>() {
                @Override
                public TagDefinitionModelDao inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                    final TagDefinitionSqlDao tagDefinitionSqlDao = entitySqlDaoWrapperFactory.become(TagDefinitionSqlDao.class);

                    // Make sure the tag definition doesn't exist already
                    final TagDefinitionModelDao existingDefinition = tagDefinitionSqlDao.getByName(definitionName, context);
                    if (existingDefinition != null) {
                        throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_ALREADY_EXISTS, definitionName);
                    }

                    // Create it
                    final TagDefinitionModelDao tagDefinition = new TagDefinitionModelDao(context.getCreatedDate(), definitionName, description);
                    tagDefinitionSqlDao.create(tagDefinition, context);

                    // Post an event to the bus
                    final boolean isControlTag = TagModelDaoHelper.isControlTag(tagDefinition.getName());
                    final TagDefinitionInternalEvent tagDefinitionEvent;
                    if (isControlTag) {
                        tagDefinitionEvent = tagEventBuilder.newControlTagDefinitionCreationEvent(tagDefinition.getId(), tagDefinition, context);
                    } else {
                        tagDefinitionEvent = tagEventBuilder.newUserTagDefinitionCreationEvent(tagDefinition.getId(), tagDefinition, context);
                    }
                    try {
                        bus.postFromTransaction(tagDefinitionEvent, entitySqlDaoWrapperFactory, context);
                    } catch (InternalBus.EventBusException e) {
                        log.warn("Failed to post tag definition creation event for tag " + tagDefinition.getId(), e);
                    }

                    return tagDefinition;
                }
            });
        } catch (TransactionFailedException exception) {
            if (exception.getCause() instanceof TagDefinitionApiException) {
                throw (TagDefinitionApiException) exception.getCause();
            } else {
                throw exception;
            }
        }
    }

    @Override
    public void deleteById(final UUID definitionId, final InternalCallContext context) throws TagDefinitionApiException {
        try {
            transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
                @Override
                public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                    final TagDefinitionSqlDao tagDefinitionSqlDao = entitySqlDaoWrapperFactory.become(TagDefinitionSqlDao.class);

                    // Make sure the tag definition exists
                    final TagDefinitionModelDao tagDefinition = tagDefinitionSqlDao.getById(definitionId.toString(), context);
                    if (tagDefinition == null) {
                        throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_DOES_NOT_EXIST, definitionId);
                    }

                    // Make sure it is not used currently
                    if (tagDefinitionSqlDao.tagDefinitionUsageCount(definitionId.toString(), context) > 0) {
                        throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_IN_USE, definitionId);
                    }

                    // Delete it
                    tagDefinitionSqlDao.markTagDefinitionAsDeleted(definitionId.toString(), context);

                    postBusEventFromTransaction(tagDefinition, tagDefinition, ChangeType.DELETE, entitySqlDaoWrapperFactory, context);
                    return null;
                }
            });
        } catch (TransactionFailedException exception) {
            if (exception.getCause() instanceof TagDefinitionApiException) {
                throw (TagDefinitionApiException) exception.getCause();
            } else {
                throw exception;
            }
        }
    }

    protected void postBusEventFromTransaction(final TagDefinitionModelDao tagDefinition, final TagDefinitionModelDao savedTagDefinition,
                                               final ChangeType changeType, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalCallContext context)
            throws BillingExceptionBase {

        final TagDefinitionInternalEvent tagDefinitionEvent;
        final boolean isControlTag = TagModelDaoHelper.isControlTag(tagDefinition.getName());
        switch (changeType) {
            case INSERT:
                tagDefinitionEvent = (isControlTag) ?
                                     tagEventBuilder.newControlTagDefinitionCreationEvent(tagDefinition.getId(), tagDefinition, context) :
                                     tagEventBuilder.newUserTagDefinitionCreationEvent(tagDefinition.getId(), tagDefinition, context);

                break;
            case DELETE:
                tagDefinitionEvent = (isControlTag) ?
                                     tagEventBuilder.newControlTagDefinitionDeletionEvent(tagDefinition.getId(), tagDefinition, context) :
                                     tagEventBuilder.newUserTagDefinitionDeletionEvent(tagDefinition.getId(), tagDefinition, context);
                break;
            default:
                return;
        }

        try {
            bus.postFromTransaction(tagDefinitionEvent, entitySqlDaoWrapperFactory, context);
        } catch (InternalBus.EventBusException e) {
            log.warn("Failed to post tag definition event for tag " + tagDefinition.getId().toString(), e);
        }
    }

    @Override
    protected TagDefinitionApiException generateAlreadyExistsException(final TagDefinitionModelDao entity, final InternalCallContext context) {
        return new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_ALREADY_EXISTS, entity.getId());
    }
}
