/*
 * Copyright 2010-2011 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.util.tag.dao;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.inject.Named;

import org.killbill.billing.BillingExceptionBase;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.events.TagDefinitionInternalEvent;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.audit.dao.AuditDao;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.dao.EntityDaoBase;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.billing.util.tag.TagDefinition;
import org.killbill.billing.util.tag.api.user.TagEventBuilder;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.exceptions.TransactionFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterators;
import com.google.inject.Inject;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public class DefaultTagDefinitionDao extends EntityDaoBase<TagDefinitionModelDao, TagDefinition, TagDefinitionApiException> implements TagDefinitionDao {

    private static final Logger log = LoggerFactory.getLogger(DefaultTagDefinitionDao.class);

    private final TagEventBuilder tagEventBuilder;
    private final PersistentBus bus;
    private final AuditDao auditDao;

    @Inject
    public DefaultTagDefinitionDao(final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi, final TagEventBuilder tagEventBuilder, final PersistentBus bus, final Clock clock,
                                   final CacheControllerDispatcher controllerDispatcher, final NonEntityDao nonEntityDao, final InternalCallContextFactory internalCallContextFactory, final AuditDao auditDao) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, controllerDispatcher, nonEntityDao, internalCallContextFactory), TagDefinitionSqlDao.class);
        this.tagEventBuilder = tagEventBuilder;
        this.bus = bus;
        this.auditDao = auditDao;
    }

    @Override
    public List<TagDefinitionModelDao> getTagDefinitions(final boolean includeSystemTags, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<TagDefinitionModelDao>>() {
            @Override
            public List<TagDefinitionModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                // Get user definitions from the database
                final TagDefinitionSqlDao tagDefinitionSqlDao = entitySqlDaoWrapperFactory.become(TagDefinitionSqlDao.class);
                final Iterator<TagDefinitionModelDao> all = tagDefinitionSqlDao.getAll(context);
                final List<TagDefinitionModelDao> definitionList = new LinkedList<TagDefinitionModelDao>();
                Iterators.addAll(definitionList, all);

                // Add invoice tag definitions
                definitionList.addAll(SystemTags.get(includeSystemTags));
                return definitionList;
            }
        });
    }

    @Override
    public TagDefinitionModelDao getByName(final String definitionName, final InternalTenantContext context) throws TagDefinitionApiException {
        return transactionalSqlDao.execute(true, TagDefinitionApiException.class, new EntitySqlDaoTransactionWrapper<TagDefinitionModelDao>() {
            @Override
            public TagDefinitionModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final TagDefinitionModelDao systemTag = SystemTags.lookup(definitionName);
                final TagDefinitionModelDao tag = systemTag != null ? systemTag : entitySqlDaoWrapperFactory.become(TagDefinitionSqlDao.class).getByName(definitionName, context);
                if (tag == null) {
                    throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_DOES_NOT_EXIST, definitionName);
                }
                return tag;
            }
        });
    }

    @Override
    public TagDefinitionModelDao getById(final UUID definitionId, final InternalTenantContext context) throws TagDefinitionApiException  {
        return transactionalSqlDao.execute(true, TagDefinitionApiException.class, new EntitySqlDaoTransactionWrapper<TagDefinitionModelDao>() {
            @Override
            public TagDefinitionModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final TagDefinitionModelDao systemTag = SystemTags.lookup(definitionId);
                final TagDefinitionModelDao tag = systemTag != null ? systemTag : entitySqlDaoWrapperFactory.become(TagDefinitionSqlDao.class).getById(definitionId.toString(), context);
                if (tag == null) {
                    throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_DOES_NOT_EXIST, definitionId);
                }
                return tag;
            }
        });
    }

    @Override
    public List<TagDefinitionModelDao> getByIds(final Collection<UUID> definitionIds, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<TagDefinitionModelDao>>() {
            @Override
            public List<TagDefinitionModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final List<TagDefinitionModelDao> result = new LinkedList<TagDefinitionModelDao>();
                for (final UUID cur : definitionIds) {
                    final TagDefinitionModelDao tagDefinitionModelDao = SystemTags.lookup(cur);
                    if (tagDefinitionModelDao != null) {
                        result.add(tagDefinitionModelDao);
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
    public TagDefinitionModelDao create(final String definitionName, final String description, final String tagDefinitionObjectTypes,
                                        final InternalCallContext context) throws TagDefinitionApiException {
        // Make sure a invoice tag with this name don't already exist
        if (TagModelDaoHelper.isControlTag(definitionName)) {
            throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_CONFLICTS_WITH_CONTROL_TAG, definitionName);
        }

        try {
            return transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<TagDefinitionModelDao>() {
                @Override
                public TagDefinitionModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                    final TagDefinitionSqlDao tagDefinitionSqlDao = entitySqlDaoWrapperFactory.become(TagDefinitionSqlDao.class);

                    // Make sure the tag definition doesn't exist already
                    final TagDefinitionModelDao existingDefinition = tagDefinitionSqlDao.getByName(definitionName, context);
                    if (existingDefinition != null) {
                        throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_ALREADY_EXISTS, definitionName);
                    }

                    // Create it
                    final TagDefinitionModelDao tagDefinition = new TagDefinitionModelDao(context.getCreatedDate(), definitionName, description, tagDefinitionObjectTypes);
                    createAndRefresh(tagDefinitionSqlDao, tagDefinition, context);

                    // Post an event to the bus
                    final boolean isControlTag = TagModelDaoHelper.isControlTag(tagDefinition.getName());
                    final TagDefinitionInternalEvent tagDefinitionEvent;
                    if (isControlTag) {
                        tagDefinitionEvent = tagEventBuilder.newControlTagDefinitionCreationEvent(tagDefinition.getId(), tagDefinition,
                                                                                                  context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());
                    } else {
                        tagDefinitionEvent = tagEventBuilder.newUserTagDefinitionCreationEvent(tagDefinition.getId(), tagDefinition,
                                                                                               context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());
                    }
                    try {
                        bus.postFromTransaction(tagDefinitionEvent, entitySqlDaoWrapperFactory.getHandle().getConnection());
                    } catch (final PersistentBus.EventBusException e) {
                        log.warn("Failed to post tag definition creation event for tagDefinitionId='{}'", tagDefinition.getId(), e);
                    }

                    return tagDefinition;
                }
            });
        } catch (final TransactionFailedException exception) {
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
            transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
                @Override
                public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
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
        } catch (final TransactionFailedException exception) {
            if (exception.getCause() instanceof TagDefinitionApiException) {
                throw (TagDefinitionApiException) exception.getCause();
            } else {
                throw exception;
            }
        }
    }

    @Override
    public List<AuditLogWithHistory> getTagDefinitionAuditLogsWithHistoryForId(final UUID tagDefinitionId, final AuditLevel auditLevel, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<AuditLogWithHistory>>() {
            @Override
            public List<AuditLogWithHistory> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) {
                final TagDefinitionSqlDao transactional = entitySqlDaoWrapperFactory.become(TagDefinitionSqlDao.class);
                return auditDao.getAuditLogsWithHistoryForId(transactional, TableName.TAG_DEFINITIONS, tagDefinitionId, auditLevel, context);
            }
        });
    }

    protected void postBusEventFromTransaction(final TagDefinitionModelDao tagDefinition, final TagDefinitionModelDao savedTagDefinition,
                                               final ChangeType changeType, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalCallContext context)
            throws BillingExceptionBase {

        final TagDefinitionInternalEvent tagDefinitionEvent;
        final boolean isControlTag = TagModelDaoHelper.isControlTag(tagDefinition.getName());
        switch (changeType) {
            case INSERT:
                tagDefinitionEvent = (isControlTag) ?
                                     tagEventBuilder.newControlTagDefinitionCreationEvent(tagDefinition.getId(), tagDefinition,
                                                                                          context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken()) :
                                     tagEventBuilder.newUserTagDefinitionCreationEvent(tagDefinition.getId(), tagDefinition,
                                                                                       context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());

                break;
            case DELETE:
                tagDefinitionEvent = (isControlTag) ?
                                     tagEventBuilder.newControlTagDefinitionDeletionEvent(tagDefinition.getId(), tagDefinition,
                                                                                          context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken()) :
                                     tagEventBuilder.newUserTagDefinitionDeletionEvent(tagDefinition.getId(), tagDefinition,
                                                                                       context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());
                break;
            default:
                return;
        }

        try {
            bus.postFromTransaction(tagDefinitionEvent, entitySqlDaoWrapperFactory.getHandle().getConnection());
        } catch (final PersistentBus.EventBusException e) {
            log.warn("Failed to post tag definition event for tagDefinitionId='{}'", tagDefinition.getId().toString(), e);
        }
    }

    @Override
    protected TagDefinitionApiException generateAlreadyExistsException(final TagDefinitionModelDao entity, final InternalCallContext context) {
        return new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_ALREADY_EXISTS, entity.getId());
    }
}
