/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2024 Equinix, Inc
 * Copyright 2014-2024 The Billing Project, LLC
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

package org.killbill.billing.util.customfield.dao;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

import org.killbill.billing.BillingExceptionBase;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.events.BusInternalEvent;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.audit.dao.AuditDao;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.customfield.api.DefaultCustomFieldCreationEvent;
import org.killbill.billing.util.customfield.api.DefaultCustomFieldDeletionEvent;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationSqlDaoHelper.Ordering;
import org.killbill.billing.util.entity.dao.DefaultPaginationSqlDaoHelper.PaginationIteratorBuilder;
import org.killbill.billing.util.entity.dao.EntityDaoBase;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.billing.util.entity.dao.SearchAttribute;
import org.killbill.billing.util.entity.dao.SearchQuery;
import org.killbill.billing.util.entity.dao.SqlOperator;
import org.killbill.billing.util.optimizer.BusOptimizer;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.killbill.billing.util.entity.dao.SearchQuery.SEARCH_QUERY_MARKER;
import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public class DefaultCustomFieldDao extends EntityDaoBase<CustomFieldModelDao, CustomField, CustomFieldApiException> implements CustomFieldDao {

    private static final Logger log = LoggerFactory.getLogger(DefaultCustomFieldDao.class);

    private final BusOptimizer bus;
    private final AuditDao auditDao;

    @Inject
    public DefaultCustomFieldDao(final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi, final Clock clock, final CacheControllerDispatcher controllerDispatcher,
                                 final NonEntityDao nonEntityDao, final InternalCallContextFactory internalCallContextFactory, final BusOptimizer bus, final AuditDao auditDao) {
        super(nonEntityDao, controllerDispatcher, new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, controllerDispatcher, nonEntityDao, internalCallContextFactory), CustomFieldSqlDao.class);
        this.bus = bus;
        this.auditDao = auditDao;
    }

    @Override
    public List<CustomFieldModelDao> getCustomFieldsForObject(final UUID objectId, final ObjectType objectType, final InternalTenantContext context) {
        List<CustomFieldModelDao> result = transactionalSqlDao
                .execute(true, entitySqlDaoWrapperFactory -> entitySqlDaoWrapperFactory
                        .become(CustomFieldSqlDao.class)
                        .getCustomFieldsForObject(objectId, objectType, context));
        return List.copyOf(result);
    }

    @Override
    public List<CustomFieldModelDao>
    getCustomFieldsForAccountType(final ObjectType objectType, final InternalTenantContext context) {
        final List<CustomFieldModelDao> allFields = getCustomFieldsForAccount(context);
        return allFields.stream()
                .filter(customFieldModelDao -> customFieldModelDao.getObjectType() == objectType)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<CustomFieldModelDao> getCustomFieldsForAccount(final InternalTenantContext context) {
        final List<CustomFieldModelDao> result = transactionalSqlDao
                .execute(true, entitySqlDaoWrapperFactory -> entitySqlDaoWrapperFactory
                        .become(CustomFieldSqlDao.class)
                        .getByAccountRecordId(context));
        return List.copyOf(result);
    }

    @Override
    public void deleteCustomFields(final Iterable<UUID> customFieldIds, final InternalCallContext context) throws CustomFieldApiException {
        transactionalSqlDao.execute(false, entitySqlDaoWrapperFactory -> {
            final CustomFieldSqlDao sqlDao = entitySqlDaoWrapperFactory.become(CustomFieldSqlDao.class);

            for (final UUID cur : customFieldIds) {
                final CustomFieldModelDao customField = sqlDao.getById(cur.toString(), context);
                if (customField != null) {
                    sqlDao.markTagAsDeleted(cur.toString(), context);
                    postBusEventFromTransaction(customField, customField, ChangeType.DELETE, entitySqlDaoWrapperFactory, context);
                }
            }
            return null;
        });
    }

    @Override
    public void updateCustomFields(final Iterable<CustomFieldModelDao> customFieldIds, final InternalCallContext context) throws CustomFieldApiException {
        transactionalSqlDao.execute(false, CustomFieldApiException.class, entitySqlDaoWrapperFactory -> {
            final CustomFieldSqlDao sqlDao = entitySqlDaoWrapperFactory.become(CustomFieldSqlDao.class);

            for (final CustomFieldModelDao cur : customFieldIds) {
                final CustomFieldModelDao customField = sqlDao.getById(cur.getId().toString(), context);

                validateCustomFieldWhenUpdate(cur, customField);

                sqlDao.updateValue(cur.getId().toString(), cur.getFieldValue(), context);
                postBusEventFromTransaction(customField, customField, ChangeType.UPDATE, entitySqlDaoWrapperFactory, context);

            }
            return null;
        });
    }

    private void validateCustomFieldWhenUpdate(final CustomFieldModelDao input, @Nullable CustomFieldModelDao existing) throws CustomFieldApiException {
        if (existing == null) {
            throw new CustomFieldApiException(ErrorCode.CUSTOM_FIELD_DOES_NOT_EXISTS_FOR_ID, input.getId());
        }
        if (input.getObjectId() != null && !input.getObjectId().equals(existing.getObjectId())) {
            throw new CustomFieldApiException(ErrorCode.CUSTOM_FIELD_INVALID_UPDATE, input.getId(), input.getObjectId(), "ObjectId");
        }
        if (input.getObjectType() != null && input.getObjectType() != existing.getObjectType()) {
            throw new CustomFieldApiException(ErrorCode.CUSTOM_FIELD_INVALID_UPDATE, input.getId(), input.getObjectType(), "ObjectType");
        }
        if (input.getFieldName() != null && !input.getFieldName().equals(existing.getFieldName())) {
            throw new CustomFieldApiException(ErrorCode.CUSTOM_FIELD_INVALID_UPDATE, input.getId(), input.getFieldName(), "FieldName");
        }
    }

    @Override
    public List<AuditLogWithHistory> getCustomFieldAuditLogsWithHistoryForId(final UUID customFieldId, final AuditLevel auditLevel, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, entitySqlDaoWrapperFactory -> {
            final CustomFieldSqlDao transactional = entitySqlDaoWrapperFactory.become(CustomFieldSqlDao.class);
            return auditDao.getAuditLogsWithHistoryForId(transactional, TableName.CUSTOM_FIELD, customFieldId, auditLevel, context);
        });
    }

    @Override
    protected CustomFieldApiException generateAlreadyExistsException(final CustomFieldModelDao entity, final InternalCallContext context) {
        return new CustomFieldApiException(ErrorCode.CUSTOM_FIELD_ALREADY_EXISTS, entity.getId());
    }

    @Override
    protected boolean checkEntityAlreadyExists(final EntitySqlDao<CustomFieldModelDao, CustomField> transactional, final CustomFieldModelDao entity, final InternalCallContext context) {
        return transactional.getByAccountRecordId(context).stream().filter(entity::isSame).findFirst().orElse(null) != null;
    }

    @Override
    protected void postBusEventFromTransaction(final CustomFieldModelDao customField,
                                               final CustomFieldModelDao savedCustomField,
                                               final ChangeType changeType,
                                               final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                               final InternalCallContext context) throws BillingExceptionBase {
        final BusInternalEvent customFieldEvent;
        switch (changeType) {
            case INSERT:
                customFieldEvent = new DefaultCustomFieldCreationEvent(customField.getId(), customField.getObjectId(), customField.getObjectType(),
                                                                       context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());
                break;
            case DELETE:
                customFieldEvent = new DefaultCustomFieldDeletionEvent(customField.getId(), customField.getObjectId(), customField.getObjectType(),
                                                                       context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());
                break;
            default:
                return;
        }

        try {
            bus.postFromTransaction(customFieldEvent, entitySqlDaoWrapperFactory.getHandle().getConnection());
        } catch (final PersistentBus.EventBusException e) {
            log.warn("Failed to post tag event for customFieldId='{}'", customField.getId().toString(), e);
        }

    }

    @Override
    public Pagination<CustomFieldModelDao> searchCustomFields(final String searchKey, final Long offset, final Long limit, final InternalTenantContext context) {
        final SearchQuery searchQuery;
        if (searchKey.startsWith(SEARCH_QUERY_MARKER)) {
            searchQuery = new SearchQuery(searchKey,
                                          Set.of("id",
                                                 "object_id",
                                                 "object_type",
                                                 "is_active",
                                                 "field_name",
                                                 "field_value",
                                                 "created_by",
                                                 "created_date",
                                                 "updated_by",
                                                 "updated_date"));
        } else {
            searchQuery = new SearchQuery(SqlOperator.OR);

            final String likeSearchKey = String.format("%%%s%%", searchKey);
            searchQuery.addSearchClause("id", SqlOperator.EQ, searchKey);
            searchQuery.addSearchClause("object_type", SqlOperator.LIKE, likeSearchKey);
            searchQuery.addSearchClause("object_id", SqlOperator.LIKE, likeSearchKey);
            searchQuery.addSearchClause("field_name", SqlOperator.LIKE, likeSearchKey);
            searchQuery.addSearchClause("field_value", SqlOperator.LIKE, likeSearchKey);
        }

        return searchCustomFields(searchQuery, offset, limit, context);
    }

    @Override
    public Pagination<CustomFieldModelDao> searchCustomFields(final String fieldName, final ObjectType objectType, final Long offset, final Long limit, final InternalTenantContext context) {
        final SearchQuery searchQuery = new SearchQuery(SqlOperator.AND);
        searchQuery.addSearchClause("object_type", SqlOperator.EQ, objectType);
        searchQuery.addSearchClause("field_name", SqlOperator.EQ, fieldName);
        return searchCustomFields(searchQuery, offset, limit, context);
    }

    @Override
    public Pagination<CustomFieldModelDao> searchCustomFields(final String fieldName, @Nullable final String fieldValue, final ObjectType objectType, final Long offset, final Long limit, final InternalTenantContext context) {
        final SearchQuery searchQuery = new SearchQuery(SqlOperator.AND);
        searchQuery.addSearchClause("object_type", SqlOperator.EQ, objectType);
        searchQuery.addSearchClause("field_name", SqlOperator.EQ, fieldName);
        searchQuery.addSearchClause("field_value", SqlOperator.EQ, fieldValue);
        return searchCustomFields(searchQuery, offset, limit, context);
    }

    private Pagination<CustomFieldModelDao> searchCustomFields(final SearchQuery searchQuery, final Long offset, final Long limit, final InternalTenantContext context) {
        return paginationHelper.getPagination(CustomFieldSqlDao.class,
                                              new PaginationIteratorBuilder<CustomFieldModelDao, CustomField, CustomFieldSqlDao>() {
                                                  @Override
                                                  public Long getCount(final CustomFieldSqlDao customFieldSqlDao, final InternalTenantContext context) {
                                                      return customFieldSqlDao.getSearchCount(searchQuery.getSearchKeysBindMap(), searchQuery.getSearchAttributes(), searchQuery.getLogicalOperator(), context);
                                                  }

                                                  @Override
                                                  public Iterator<CustomFieldModelDao> build(final CustomFieldSqlDao customFieldSqlDao, final Long offset, final Long limit, final Ordering ordering, final InternalTenantContext context) {
                                                      return customFieldSqlDao.search(searchQuery.getSearchKeysBindMap(), searchQuery.getSearchAttributes(), searchQuery.getLogicalOperator(), offset, limit, ordering.toString(), context);
                                                  }
                                              },
                                              offset,
                                              limit,
                                              context);
    }
}
