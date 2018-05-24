/*
 * Copyright 2010-2013 Ning, Inc.
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

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.inject.Named;

import org.killbill.billing.BillingExceptionBase;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.events.TagInternalEvent;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.audit.dao.AuditDao;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationSqlDaoHelper.Ordering;
import org.killbill.billing.util.entity.dao.DefaultPaginationSqlDaoHelper.PaginationIteratorBuilder;
import org.killbill.billing.util.entity.dao.EntityDaoBase;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.killbill.billing.util.tag.api.user.TagEventBuilder;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public class DefaultTagDao extends EntityDaoBase<TagModelDao, Tag, TagApiException> implements TagDao {

    private static final Logger log = LoggerFactory.getLogger(DefaultTagDao.class);

    private final TagEventBuilder tagEventBuilder;
    private final PersistentBus bus;
    private final AuditDao auditDao;

    @Inject
    public DefaultTagDao(final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi, final TagEventBuilder tagEventBuilder, final PersistentBus bus, final Clock clock,
                         final CacheControllerDispatcher controllerDispatcher, final NonEntityDao nonEntityDao, final InternalCallContextFactory internalCallContextFactory, final AuditDao auditDao) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, controllerDispatcher, nonEntityDao, internalCallContextFactory), TagSqlDao.class);
        this.tagEventBuilder = tagEventBuilder;
        this.bus = bus;
        this.auditDao = auditDao;
    }

    @Override
    public List<TagModelDao> getTagsForObject(final UUID objectId, final ObjectType objectType, final boolean includedDeleted, final InternalTenantContext internalTenantContext) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<TagModelDao>>() {
            @Override
            public List<TagModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final TagSqlDao tagSqlDao = entitySqlDaoWrapperFactory.become(TagSqlDao.class);
                if (includedDeleted) {
                    return tagSqlDao.getTagsForObjectIncludedDeleted(objectId, objectType, internalTenantContext);
                } else {
                    return tagSqlDao.getTagsForObject(objectId, objectType, internalTenantContext);
                }
            }
        });
    }

    @Override
    public List<TagModelDao> getTagsForAccountType(final ObjectType objectType, final boolean includedDeleted, final InternalTenantContext internalTenantContext) {
        final List<TagModelDao> allTags = getTagsForAccount(includedDeleted, internalTenantContext);
        return ImmutableList.<TagModelDao>copyOf(Collections2.filter(allTags, new Predicate<TagModelDao>() {
            @Override
            public boolean apply(final TagModelDao input) {
                return input.getObjectType() == objectType;
            }
        }));
    }

    @Override
    public List<TagModelDao> getTagsForAccount(final boolean includedDeleted, final InternalTenantContext internalTenantContext) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<TagModelDao>>() {
            @Override
            public List<TagModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final TagSqlDao tagSqlDao = entitySqlDaoWrapperFactory.become(TagSqlDao.class);
                if (includedDeleted) {
                    return tagSqlDao.getByAccountRecordIdIncludedDeleted(internalTenantContext);
                } else {
                    return tagSqlDao.getByAccountRecordId(internalTenantContext);
                }
            }
        });
    }

    @Override
    public List<AuditLogWithHistory> getTagAuditLogsWithHistoryForId(final UUID tagId, final AuditLevel auditLevel, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<AuditLogWithHistory>>() {
            @Override
            public List<AuditLogWithHistory> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) {
                final TagSqlDao transactional = entitySqlDaoWrapperFactory.become(TagSqlDao.class);
                return auditDao.getAuditLogsWithHistoryForId(transactional, TableName.TAG, tagId, auditLevel, context);
            }
        });
    }

    @Override
    protected void postBusEventFromTransaction(final TagModelDao tag, final TagModelDao savedTag, final ChangeType changeType,
                                               final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalCallContext context)
            throws BillingExceptionBase {

        final TagInternalEvent tagEvent;
        final TagDefinitionModelDao tagDefinition = getTagDefinitionFromTransaction(tag.getTagDefinitionId(), entitySqlDaoWrapperFactory, context);
        final boolean isControlTag = ControlTagType.getTypeFromId(tagDefinition.getId()) != null;
        switch (changeType) {
            case INSERT:
                tagEvent = (isControlTag) ?
                           tagEventBuilder.newControlTagCreationEvent(tag.getId(), tag.getObjectId(), tag.getObjectType(), tagDefinition,
                                                                      context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken()) :
                           tagEventBuilder.newUserTagCreationEvent(tag.getId(), tag.getObjectId(), tag.getObjectType(), tagDefinition,
                                                                   context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());
                break;
            case DELETE:
                tagEvent = (isControlTag) ?
                           tagEventBuilder.newControlTagDeletionEvent(tag.getId(), tag.getObjectId(), tag.getObjectType(), tagDefinition,
                                                                      context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken()) :
                           tagEventBuilder.newUserTagDeletionEvent(tag.getId(), tag.getObjectId(), tag.getObjectType(), tagDefinition,
                                                                   context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());
                break;
            default:
                return;
        }

        try {
            bus.postFromTransaction(tagEvent, entitySqlDaoWrapperFactory.getHandle().getConnection());
        } catch (final PersistentBus.EventBusException e) {
            log.warn("Failed to post tag event for tagId='{}'", tag.getId().toString(), e);
        }
    }

    @Override
    protected boolean checkEntityAlreadyExists(final EntitySqlDao<TagModelDao, Tag> transactional, final TagModelDao entity, final InternalCallContext context) {
        return Iterables.find(transactional.getByAccountRecordId(context),
                              new Predicate<TagModelDao>() {
                                  @Override
                                  public boolean apply(final TagModelDao existingTag) {
                                      return entity.equals(existingTag) || entity.isSame(existingTag);
                                  }
                              },
                              null) != null;
    }

    @Override
    protected TagApiException generateAlreadyExistsException(final TagModelDao entity, final InternalCallContext context) {
        // Print the tag details, not the id here, as we throw this exception when checking if a tag already exists
        // by using the isSame(TagModelDao) method (see above)
        return new TagApiException(ErrorCode.TAG_ALREADY_EXISTS, entity.toString());
    }

    private TagDefinitionModelDao getTagDefinitionFromTransaction(final UUID tagDefinitionId, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext context) throws TagApiException {
        TagDefinitionModelDao tagDefintion = SystemTags.lookup(tagDefinitionId);
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
    public void create(final TagModelDao entity, final InternalCallContext context) throws TagApiException {
        transactionalSqlDao.execute(false, TagApiException.class, getCreateEntitySqlDaoTransactionWrapper(entity, context));
    }

    @Override
    public void deleteTag(final UUID objectId, final ObjectType objectType, final UUID tagDefinitionId, final InternalCallContext context) throws TagApiException {

        transactionalSqlDao.execute(false, TagApiException.class, new EntitySqlDaoTransactionWrapper<Void>() {

            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {

                final TagDefinitionModelDao tagDefinition = getTagDefinitionFromTransaction(tagDefinitionId, entitySqlDaoWrapperFactory, context);
                final TagSqlDao transactional = entitySqlDaoWrapperFactory.become(TagSqlDao.class);
                final List<TagModelDao> tags = transactional.getTagsForObject(objectId, objectType, context);
                TagModelDao tag = null;
                for (final TagModelDao cur : tags) {
                    if (cur.getTagDefinitionId().equals(tagDefinitionId)) {
                        tag = cur;
                        break;
                    }
                }
                if (tag == null) {
                    throw new TagApiException(ErrorCode.TAG_DOES_NOT_EXIST, tagDefinition.getName());
                }
                // Delete the tag
                transactional.markTagAsDeleted(tag.getId().toString(), context);

                postBusEventFromTransaction(tag, tag, ChangeType.DELETE, entitySqlDaoWrapperFactory, context);
                return null;
            }
        });

    }

    @Override
    public Pagination<TagModelDao> searchTags(final String searchKey, final Long offset, final Long limit, final InternalTenantContext context) {
        return paginationHelper.getPagination(TagSqlDao.class,
                                              new PaginationIteratorBuilder<TagModelDao, Tag, TagSqlDao>() {
                                                  @Override
                                                  public Long getCount(final TagSqlDao tagSqlDao, final InternalTenantContext context) {
                                                      return tagSqlDao.getSearchCount(searchKey, String.format("%%%s%%", searchKey), context);
                                                  }

                                                  @Override
                                                  public Iterator<TagModelDao> build(final TagSqlDao tagSqlDao, final Long offset, final Long limit, final Ordering ordering, final InternalTenantContext context) {
                                                      return tagSqlDao.search(searchKey, String.format("%%%s%%", searchKey), offset, limit, ordering.toString(), context);
                                                  }
                                              },
                                              offset,
                                              limit,
                                              context);
    }
}
