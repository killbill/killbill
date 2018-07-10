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

package org.killbill.billing.util.customfield.dao;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
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
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.billing.util.tag.dao.TagSqlDao;
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

public class DefaultCustomFieldDao extends EntityDaoBase<CustomFieldModelDao, CustomField, CustomFieldApiException> implements CustomFieldDao {

    private static final Logger log = LoggerFactory.getLogger(DefaultCustomFieldDao.class);

    private final PersistentBus bus;
    private final AuditDao auditDao;

    @Inject
    public DefaultCustomFieldDao(final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi, final Clock clock, final CacheControllerDispatcher controllerDispatcher,
                                 final NonEntityDao nonEntityDao, final InternalCallContextFactory internalCallContextFactory, final PersistentBus bus, final AuditDao auditDao) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, controllerDispatcher, nonEntityDao, internalCallContextFactory), CustomFieldSqlDao.class);
        this.bus = bus;
        this.auditDao = auditDao;
    }

    @Override
    public List<CustomFieldModelDao> getCustomFieldsForObject(final UUID objectId, final ObjectType objectType, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<CustomFieldModelDao>>() {
            @Override
            public List<CustomFieldModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(CustomFieldSqlDao.class).getCustomFieldsForObject(objectId, objectType, context);
            }
        });
    }

    @Override
    public List<CustomFieldModelDao>
    getCustomFieldsForAccountType(final ObjectType objectType, final InternalTenantContext context) {
        final List<CustomFieldModelDao> allFields = getCustomFieldsForAccount(context);

        return ImmutableList.<CustomFieldModelDao>copyOf(Collections2.filter(allFields, new Predicate<CustomFieldModelDao>() {
            @Override
            public boolean apply(@Nullable final CustomFieldModelDao input) {
                return input.getObjectType() == objectType;
            }
        }));
    }

    @Override
    public List<CustomFieldModelDao> getCustomFieldsForAccount(final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<CustomFieldModelDao>>() {
            @Override
            public List<CustomFieldModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(CustomFieldSqlDao.class).getByAccountRecordId(context);
            }
        });
    }

    @Override
    public void deleteCustomFields(final Iterable<UUID> customFieldIds, final InternalCallContext context) throws CustomFieldApiException {
        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final CustomFieldSqlDao sqlDao = entitySqlDaoWrapperFactory.become(CustomFieldSqlDao.class);

                for (final UUID cur : customFieldIds) {
                    final CustomFieldModelDao customField = sqlDao.getById(cur.toString(), context);
                    if (customField != null) {
                        sqlDao.markTagAsDeleted(cur.toString(), context);
                        postBusEventFromTransaction(customField, customField, ChangeType.DELETE, entitySqlDaoWrapperFactory, context);
                    }
                }
                return null;
            }
        });

    }

    @Override
    public void updateCustomFields(final Iterable<CustomFieldModelDao> customFieldIds, final InternalCallContext context) throws CustomFieldApiException {

        transactionalSqlDao.execute(false, CustomFieldApiException.class, new EntitySqlDaoTransactionWrapper<Void>() {

            private void validateCustomField(final CustomFieldModelDao input, @Nullable CustomFieldModelDao existing) throws CustomFieldApiException {
                if (existing == null) {
                    throw new CustomFieldApiException(ErrorCode.CUSTOM_FIELD_DOES_NOT_EXISTS_FOR_ID, input.getId());
                }
                if (input.getObjectId() != null & !input.getObjectId().equals(existing.getObjectId())) {
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
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final CustomFieldSqlDao sqlDao = entitySqlDaoWrapperFactory.become(CustomFieldSqlDao.class);

                for (final CustomFieldModelDao cur : customFieldIds) {
                    final CustomFieldModelDao customField = sqlDao.getById(cur.getId().toString(), context);

                    validateCustomField(cur, customField);

                    sqlDao.updateValue(cur.getId().toString(), cur.getFieldValue(), context);
                    postBusEventFromTransaction(customField, customField, ChangeType.UPDATE, entitySqlDaoWrapperFactory, context);

                }
                return null;
            }
        });
    }

    @Override
    public List<AuditLogWithHistory> getCustomFieldAuditLogsWithHistoryForId(final UUID customFieldId, final AuditLevel auditLevel, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<AuditLogWithHistory>>() {
            @Override
            public List<AuditLogWithHistory> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) {
                final CustomFieldSqlDao transactional = entitySqlDaoWrapperFactory.become(CustomFieldSqlDao.class);
                return auditDao.getAuditLogsWithHistoryForId(transactional, TableName.CUSTOM_FIELD, customFieldId, auditLevel, context);
            }
        });
    }

    @Override
    protected CustomFieldApiException generateAlreadyExistsException(final CustomFieldModelDao entity, final InternalCallContext context) {
        return new CustomFieldApiException(ErrorCode.CUSTOM_FIELD_ALREADY_EXISTS, entity.getId());
    }

    @Override
    protected boolean checkEntityAlreadyExists(final EntitySqlDao<CustomFieldModelDao, CustomField> transactional, final CustomFieldModelDao entity, final InternalCallContext context) {
        return Iterables.find(transactional.getByAccountRecordId(context),
                              new Predicate<CustomFieldModelDao>() {
                                  @Override
                                  public boolean apply(final CustomFieldModelDao existingCustomField) {
                                      return entity.isSame(existingCustomField);
                                  }
                              },
                              null) != null;
    }

    @Override
    protected void postBusEventFromTransaction(final CustomFieldModelDao customField, final CustomFieldModelDao savedCustomField, final ChangeType changeType,
                                               final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalCallContext context)
            throws BillingExceptionBase {

        BusInternalEvent customFieldEvent = null;
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
        return paginationHelper.getPagination(CustomFieldSqlDao.class,
                                              new PaginationIteratorBuilder<CustomFieldModelDao, CustomField, CustomFieldSqlDao>() {
                                                  @Override
                                                  public Long getCount(final CustomFieldSqlDao customFieldSqlDao, final InternalTenantContext context) {
                                                      return customFieldSqlDao.getSearchCount(searchKey, String.format("%%%s%%", searchKey), context);
                                                  }

                                                  @Override
                                                  public Iterator<CustomFieldModelDao> build(final CustomFieldSqlDao customFieldSqlDao, final Long offset, final Long limit, final Ordering ordering, final InternalTenantContext context) {
                                                      return customFieldSqlDao.search(searchKey, String.format("%%%s%%", searchKey), offset, limit, ordering.toString(), context);
                                                  }
                                              },
                                              offset,
                                              limit,
                                              context);
    }
}
